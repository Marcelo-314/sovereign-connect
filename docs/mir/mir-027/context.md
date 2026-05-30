# context — MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001

```text
Document ID:  context-MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
Version:      v0.1.0
Status:       Execution Package Context
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
MU Slot:      MU-027
Track:        SC-B Runtime Dispatch Hardening / H3
Scope:        Dispatch Observation Persistence only
Input MIR:    MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v0.2.0-candidate
Input CSA:    CSA-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v0.2.0-merged
```

---

## 1. Operational purpose

Implement H3 of SC-B Runtime Dispatch Hardening:

```text
Persist DispatchObservationRecord through JDBC/Flyway-compatible SC-B storage.
```

This package is intentionally narrow. It must not redesign runtime dispatch, the outbox bridge, lifecycle channels, SC-D adapter execution, serialization runtime, NATS or JetStream.

The implementation must add durable/restart-visible observation persistence while preserving the existing `DispatchObservationPort` contract.

---

## 2. Baseline facts verified post-MU-026

Current validated baseline:

```text
MU-024 — Abstract Bus Seed: Validated L4
MU-025 — Dispatch State Persistence: Validated L4
MU-026 — Outbox Bridge Seed: Validated L4
Current full test baseline: 354 tests, 0 failures, 0 errors, 0 skipped
EIB reports present: 56 tests, 0 failures, 0 errors, 0 skipped
```

Existing H3-relevant production surface:

```text
DispatchObservationRecord exists.
DispatchObservationPort exists.
InMemoryDispatchObservationRepository exists.
RuntimeDispatchService emits observations through DispatchObservationPort.
JdbcDispatchStateRepository exists.
V100__sc_b_dispatch_state_persistence.sql exists.
sc_b_dispatch_records exists with dispatch_record_id primary key.
```

Current `DispatchObservationRecord` shape:

```java
record DispatchObservationRecord(
    UUID observationId,
    UUID dispatchRecordId,
    UUID attemptId,
    DispatchState state,
    String code,
    String sanitizedReason,
    Instant observedAt
)
```

Current `DispatchObservationPort` shape:

```java
interface DispatchObservationPort {
    void record(DispatchObservationRecord observation);
    List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId);
}
```

Port must not change.

---

## 3. Mandatory implementation surface

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchObservationRepository.java
src/main/resources/db/migration/V101__sc_b_dispatch_observation_persistence.sql
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchObservationRepositoryTest.java
src/test/java/com/sovereign/connect/bus/runtime/dispatch/RuntimeDispatchObservationPersistenceTest.java
src/test/java/com/sovereign/connect/bus/ScBusObservationPersistenceArchitectureTest.java
```

Modify:

```text
src/test/java/com/sovereign/connect/bus/ScBusOutboxBridgeArchitectureTest.java
```

Specifically update `h2DoesNotAddScBMigrations()` or rename it, because H3 must add `V101`. The assertion that only `V100` exists becomes obsolete.

---

## 4. Required DDL

Create exactly:

```sql
CREATE TABLE IF NOT EXISTS sc_b_dispatch_observations (
    observation_id         TEXT    PRIMARY KEY,
    dispatch_record_id     TEXT    NOT NULL,
    attempt_id             TEXT    NULL,
    state                  TEXT    NOT NULL,
    code                   TEXT    NULL,
    sanitized_reason       TEXT    NULL,
    observed_at_ms         INTEGER NOT NULL,
    FOREIGN KEY (dispatch_record_id)
        REFERENCES sc_b_dispatch_records(dispatch_record_id),
    CHECK (state IN (
        'PENDING','CLAIMED','DISPATCHING','DISPATCHED',
        'DELIVERY_FAILED','RETRY_SCHEDULED','EXHAUSTED',
        'CANCELLED_BY_SUPERSEDE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_sc_b_dispatch_observations_record
    ON sc_b_dispatch_observations(dispatch_record_id, observed_at_ms);
```

Rationale:

```text
V100 already creates sc_b_dispatch_records(dispatch_record_id PRIMARY KEY).
Therefore the FK from sc_b_dispatch_observations.dispatch_record_id is viable and recommended.
```

Do not add SC-C tables. Do not alter SC-C tables. Do not create non-`sc_b_*` persistence objects.

---

## 5. JdbcDispatchObservationRepository requirements

Implement `DispatchObservationPort`.

Required behavior:

```text
record(observation):
  - rejects null observation
  - rejects null observationId
  - rejects null dispatchRecordId
  - rejects null state
  - rejects null observedAt
  - preserves existing observationId
  - preserves nullable attemptId
  - preserves nullable code
  - preserves nullable sanitizedReason
  - persists observedAt as epoch millis

observationsFor(dispatchRecordId):
  - rejects null dispatchRecordId
  - returns empty immutable/copy list for unknown dispatchRecordId
  - filters by dispatchRecordId
  - orders by observed_at_ms ASC, observation_id ASC
```

Recommended implementation pattern:

```java
public final class JdbcDispatchObservationRepository implements DispatchObservationPort {
    private final JdbcTemplate jdbc;

    public JdbcDispatchObservationRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }
}
```

Do not import `core.*`. Do not import `adapter.*`. Do not use JPA.

---

## 6. RuntimeDispatchService observation emit points

The current runtime has 9 emit points. H3 must persist them through the port, not add new lifecycle semantics.

```text
CLAIMED                  reason="technical claim recorded"
DISPATCHING              reason="technical dispatch started"
DISPATCHED               reason="technical dispatch completed"
DELIVERY_FAILED          code="NO_HANDLER" reason=noHandler.sanitizedReason()
DELIVERY_FAILED          code=failedOutcome.code() reason=failedOutcome.sanitizedReason()
RETRY_SCHEDULED          reason="technical retry scheduled"
CLAIMED                  reason="technical retry claimed"
EXHAUSTED                reason="technical delivery exhausted"
CANCELLED_BY_SUPERSEDE   code="SUPERSEDED" reason=evidenceRef
```

`CANDIDATE_SELECTED` is reserved vocabulary only. There is no current runtime hook for it because candidate read and claim occur within the same `dispatchPending()` flow. Do not introduce a `CANDIDATE_SELECTED` observation in this MU.

---

## 7. Test datasource pattern

Reuse the H1 raw SQLite pattern exactly. Do not use `PerConnectionPragmaDataSource` from adapter test support.

```java
private DataSource dataSource(String name) {
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(name + ".sqlite").toAbsolutePath());
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    return dataSource;
}
```

Tests must run with `V100` and `V101` applied through Flyway.

---

## 8. RuntimeDispatchService wiring pattern for runtime tests

Use manual wiring. Do not use Spring context. Follow the same constructor order used in MU-026:

```text
RuntimeDispatchService(
  ScBusPort busPort,
  DispatchCandidateReadPort candidateReadPort,
  DispatchStateWritePort stateWritePort,
  DispatchObservationPort observationPort,
  EnvelopeValidationService envelopeValidationService,
  RoutingKeyValidationService routingKeyValidationService,
  CorrelationValidationService correlationValidationService
)
```

For H3 runtime persistence tests, use:

```java
var busPort = new InMemoryScBusPort();
var statePort = new JdbcDispatchStateRepository(dataSource);
var observationPort = new JdbcDispatchObservationRepository(dataSource);
var dispatchService = new RuntimeDispatchService(
    busPort,
    candidateReadPort,
    statePort,
    observationPort,
    new EnvelopeValidationService(),
    new RoutingKeyValidationService(),
    new CorrelationValidationService()
);
```

`candidateReadPort` may be a minimal in-test implementation returning deterministic `DispatchCandidate` objects. Use `ScLedgerDispatchCandidateReadAdapter` only if the test explicitly needs outbox-bridge integration. H3 should not re-test all of H2.

---

## 9. Required tests

### 9.1 JdbcDispatchObservationRepositoryTest — 11 tests

```text
recordPersistsObservationWithAllMandatoryFields
recordRejectsNullObservation
recordRejectsNullObservationId
recordRejectsNullDispatchRecordId
recordRejectsNullState
recordRejectsNullObservedAt
observationsForUnknownDispatchRecordIdReturnsEmptyList
observationSurvivesRepositoryRecreation
multipleObservationsReturnedInObservedAtOrder
noHandlerObservationPersistsCodeAndSanitizedReason
deliveryFailedObservationPersistsCodeAndSanitizedReason
```

### 9.2 RuntimeDispatchObservationPersistenceTest — 8 tests

```text
claimEmitsPersistentClaimedObservation
dispatchEmitsPersistentDispatchingAndDispatchedObservations
noHandlerEmitsPersistentDeliveryFailedObservation
retryEmitsPersistentRetryScheduledObservation
exhaustionEmitsPersistentExhaustedObservation
supersedeCancellationEmitsPersistentCancelledObservation
dispatchedObservationIsTechnicalNotSemantic
exhaustedObservationIsTechnicalNotSemantic
```

### 9.3 ScBusObservationPersistenceArchitectureTest — 5 tests

```text
busRuntimePersistenceDoesNotImportCore
coreDoesNotImportBusRuntime
noBrokerDependencyIntroduced
scBObservationMigrationUsesV101
scBObservationMigrationCreatesOnlyScBTables
```

### 9.4 Existing architecture test update

Update `ScBusOutboxBridgeArchitectureTest.h2DoesNotAddScBMigrations()` to accept:

```text
V100__sc_b_dispatch_state_persistence.sql
V101__sc_b_dispatch_observation_persistence.sql
```

Preferred assertion:

```java
assertThat(scBMigrations).containsExactlyInAnyOrder(
    "V100__sc_b_dispatch_state_persistence.sql",
    "V101__sc_b_dispatch_observation_persistence.sql"
);
```

---

## 10. Hard stops

```text
STOP-1: mvn -q compile
  Expected: BUILD SUCCESS.
  Failure means the repository/port wiring is wrong.

STOP-2: mvn -q test -Dtest="JdbcDispatchObservationRepositoryTest"
  Expected: 11 tests, 0 failures, 0 errors.

STOP-3: mvn -q test -Dtest="RuntimeDispatchObservationPersistenceTest,ScBusObservationPersistenceArchitectureTest,ScBusHardeningArchitectureTest,ScBusOutboxBridgeArchitectureTest"
  Expected: all pass, 0 failures, 0 errors.
  Critical: ScBusOutboxBridgeArchitectureTest must be updated for V101.

STOP-4: mvn -q test
  Expected: >= 378 tests, 0 failures, 0 errors, 0 skipped.
  SC-C / non-bus baseline expected: 240 tests still green.
```

If any hard stop fails, do not broaden scope. Diagnose within H3 boundaries.

---

## 11. Strict negative scope

Forbidden:

```text
- modifying DispatchObservationPort method signatures;
- redesigning RuntimeDispatchService lifecycle;
- introducing CANDIDATE_SELECTED emit hooks;
- implementing productive ScDeliveryError event emission;
- adding NATS, JetStream or broker dependencies;
- implementing lifecycle-channel runtime;
- implementing SC-D adapter execution;
- introducing productive ScdCommand or SC-D fact families;
- mutating OutboxEntryStatus or SC-C ledger records;
- mirroring observations into SC-C ledger;
- interpreting DISPATCHED as semantic success;
- interpreting DELIVERY_FAILED or EXHAUSTED as domain/provider failure.
```

---

## 12. Branch and commit

Branch:

```text
feat/sc-b-mir-027-dispatch-observation-persistence
```

Implementation commit:

```text
feat(sc-b): persist dispatch observations
```

Evidence commit:

```text
docs(sc-b): record dispatch observation persistence evidence
```
