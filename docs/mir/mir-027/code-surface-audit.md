# CSA-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001

## Code Surface Audit — SC-B Dispatch Observation Persistence

```text
Document ID:  CSA-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
Title:        Code Surface Audit — SC-B Dispatch Observation Persistence
Version:      v0.2.0-merged
Status:       Merged / Post-MU-026 / Pre-execution-package / Execution-package-ready
Date:         2026-05-30
Corpus:       Sovereign Connect
Type:         CSA
Plane:        SC-B
MU ID:        MU-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
MU Slot:      MU-027
Track:        SC-B Runtime Dispatch Hardening / H3
Baseline:     sovereign-connect-026.zip (post-MU-026 / Outbox Bridge Seed Validated L4)
Input MIR:    MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v0.2.0-candidate
Tests:        354 / 0 failures / 0 errors / 0 skipped
Result:       Approvable — execution package may be produced
```

---

## 0. Merge note

This merged CSA consolidates:

```text
1. CSA-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v0.1.0-refresh
2. User-provided Post-SDD Code Surface Audit for MU-027 / H3
```

Merge policy:

```text
- User audit is treated as the more precise operational/code-level inventory.
- Previous CSA refresh is retained for scope, exclusions, risks, boundary language and execution sequencing.
- Where the user audit provides exact method names, DDL, repository shape or stop conditions, those are treated as preferred execution-package inputs.
```

---

## 1. Executive verdict

```text
Verdict: Approvable. MIR execution package may be produced.

H3 is the simplest MU in the SC-B runtime hardening track.
The only required new production class is JdbcDispatchObservationRepository.
The only required new resource is V101__sc_b_dispatch_observation_persistence.sql.
All other production surfaces are pre-existing and should remain unchanged unless a deviation is explicitly justified.
```

Recommended next artifact:

```text
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 execution package
```

Recommended branch:

```text
feat/sc-b-mir-027-dispatch-observation-persistence
```

Recommended commit:

```text
feat(sc-b): persist dispatch observations
```

### 1.1 Mandatory action

```text
ACTION-H3-001 — Update ScBusOutboxBridgeArchitectureTest.h2DoesNotAddScBMigrations().

Current assertion expects exactly V100. After H3 adds V101, this assertion breaks.
The test must be updated in the same commit as H3 to allow V100 + V101.
```

### 1.2 Required execution-package notes

```text
NOTE-H3-001 — CANDIDATE_SELECTED remains reserved vocabulary.

RuntimeDispatchService has no emit point for it. The private record(...) helper is
called from 9 explicit points; none is a candidate-selected moment. H3 MUST NOT
invent that runtime lifecycle stage.
```

```text
NOTE-H3-002 — FK from sc_b_dispatch_observations to sc_b_dispatch_records is viable and recommended.

V100 already defines sc_b_dispatch_records(dispatch_record_id PRIMARY KEY), and
RuntimeDispatchService records observations after DispatchStateWritePort has
created/updated dispatch state. The execution package SHOULD require the FK unless
implementation discovers a concrete incompatibility and records it as a deviation.
```

---

## 2. Audit scope

This CSA refresh audits the post-MU-026 code surface for H3 only:

```text
H3 — Dispatch Observation Persistence
```

Included audit targets:

```text
1. DispatchObservationRecord shape.
2. DispatchObservationPort shape.
3. InMemoryDispatchObservationRepository behavior.
4. RuntimeDispatchService observation emit points.
5. Actual observation vocabulary emitted today.
6. CANDIDATE_SELECTED hook presence/absence.
7. V100 migration and FK viability.
8. Existing architecture tests that must change for H3.
9. H1 datasource/test pattern to reuse.
10. Baseline regression reports.
11. Scope risks: NATS, lifecycle, SC-D, ScDeliveryError, SC-C outbox mutation.
```

Excluded from this CSA and from MU-027:

```text
- NATS / JetStream binding design.
- Lifecycle-channel runtime implementation.
- SC-D adapter participation.
- Productive ScdCommand shape.
- Productive ScDeliveryError event-lane emission.
- SC-C OutboxEntryStatus mutation.
- Mirroring dispatch observations into SC-C ledger/outbox.
- Terminal semantic request-state ownership.
- Redesign of RuntimeDispatchService lifecycle.
```

---

## 3. Baseline evidence

### 3.1 Code state observed

The audited service is post-MU-026. Visible top commits:

```text
cbd77f0 docs(sc-b): finalize outbox bridge report hashes
9e8a693 docs(sc-b): record outbox bridge seed evidence
51f3dbd feat(sc-b): bridge sc-c outbox to runtime dispatch
31abfe5 Merge pull request #30 from Marcelo-314/feat/sc-b-mir-025-dispatch-state-persistence
```

The uploaded repository still shows the feature branch as:

```text
feat/sc-b-mir-026-outbox-bridge-seed
```

This is acceptable for H3 audit because H3 is a post-MU-026 refresh over the merged code surface.

### 3.2 Included Surefire reports

Included reports show:

```text
sovereign-connect module:
  354 tests, 0 failures, 0 errors, 0 skipped

EIB module:
  56 tests, 0 failures, 0 errors, 0 skipped
```

Relevant SC-B / dispatch / bridge reports included:

```text
RuntimeDispatchServiceTest:                    8 tests
JdbcDispatchStateRepositoryTest:              14 tests
ScBusHardeningArchitectureTest:                9 tests
ScBusOutboxBridgeArchitectureTest:             8 tests
SQLiteScOutboxDispatchReadPortTest:            5 tests
OutboxEntryDispatchProjectorTest:              9 tests
ScLedgerDispatchCandidateReadAdapterTest:     10 tests
ScLedgerDispatchBridgeRuntimeTest:             5 tests
```

Maven was not re-executed in this audit environment. Evidence is based on source inspection and included Surefire XML reports.

---

## 4. Confirmed H3-relevant surface inventory

### 4.1 DispatchObservationRecord

Confirmed shape:

```java
record DispatchObservationRecord(
    UUID    observationId,
    UUID    dispatchRecordId,
    UUID    attemptId,          // nullable — record() receives attempt.attemptId()
    DispatchState state,
    String  code,               // nullable
    String  sanitizedReason,    // nullable
    Instant observedAt
)
```

CSA interpretation:

```text
The current seven-field record is sufficient for H3 minimum persistence.
All fields map directly to SQL columns.
H3 SHOULD persist this shape as-is.
```

Forward-compatible nullable enrichment columns may be added at the DB layer, but the current Java record cannot populate them without broadening runtime surface. The execution package should prefer the seven-field schema unless there is a concrete reason to add nullable future-proofing columns.

### 4.2 DispatchObservationPort

Confirmed shape:

```java
interface DispatchObservationPort {
    void record(DispatchObservationRecord observation);
    List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId);
}
```

CSA interpretation:

```text
The port is H3-ready.
JdbcDispatchObservationRepository must implement both methods.
The port must not change for MU-027.
```

### 4.3 InMemoryDispatchObservationRepository

Confirmed behavior:

```text
- CopyOnWriteArrayList<DispatchObservationRecord> storage;
- null-check in record(...);
- stream-filter in observationsFor(...);
- no restart visibility.
```

CSA disposition:

```text
InMemoryDispatchObservationRepository must remain unchanged and available for seed/unit-test profiles.
H3 adds a persistent alternative; it must not remove the in-memory adapter.
```

### 4.4 RuntimeDispatchService observation emit points

Confirmed 9 emit points via private `record(DispatchAttempt attempt, String code, String reason)`:

```text
Line  72: CLAIMED                    code=null          reason="technical claim recorded"
Line  74: DISPATCHING                code=null          reason="technical dispatch started"
Line  78: DISPATCHED                 code=null          reason="technical dispatch completed"
Line  81: DELIVERY_FAILED            code="NO_HANDLER" reason=noHandler.sanitizedReason()
Line  84: DELIVERY_FAILED            code=failedOutcome.code() reason=failedOutcome.sanitizedReason()
Line 120: RETRY_SCHEDULED            code=null          reason="technical retry scheduled"
Line 122: CLAIMED                    code=null          reason="technical retry claimed"
Line 128: EXHAUSTED                  code=null          reason="technical delivery exhausted"
Line 138: CANCELLED_BY_SUPERSEDE     code="SUPERSEDED" reason=evidenceRef
```

Important facts:

```text
- No DISPATCH_STARTED / DISPATCHING distinction exists today.
- The service emits DispatchAttempt.state() directly.
- observationId is generated as UUID.randomUUID() inside RuntimeDispatchService.record(...).
- JdbcDispatchObservationRepository.record(...) receives a fully constructed DispatchObservationRecord and must persist the existing observationId.
```

### 4.5 CANDIDATE_SELECTED

Search result:

```text
No production or test hook emits CANDIDATE_SELECTED today.
```

Disposition:

```text
CANDIDATE_SELECTED is reserved vocabulary only in MU-027.
The execution package must not require implementing or testing it.
```

Rationale:

```text
RuntimeDispatchService reads candidates and claims them in the same dispatch operation.
There is no separate candidate-selected lifecycle stage.
Adding one would be a runtime lifecycle redesign, not H3 observation persistence.
```

---

## 5. Persistence and migration surface

### 5.1 Existing migration state

Current migration list:

```text
V1__sc_c_base_schema.sql
V2__sc_c_temporal_engine.sql
V3__sc_c_ledger_outbox_sqlite.sql
V4__sc_c_normalized_topology_persistence.sql
V100__sc_b_dispatch_state_persistence.sql
```

No `V101` exists. H3 creates it.

### 5.2 Existing `sc_b_dispatch_records` surface

`V100__sc_b_dispatch_state_persistence.sql` defines `sc_b_dispatch_records` with:

```text
dispatch_record_id TEXT PRIMARY KEY
source_record_id TEXT NULL
current_attempt_id TEXT NULL
current_attempt_number INTEGER
current_state TEXT
supersession_evidence_ref TEXT NULL
created_at_ms INTEGER
updated_at_ms INTEGER
```

`sc_b_dispatch_attempts` already references `sc_b_dispatch_records(dispatch_record_id)`.

### 5.3 FK decision

CSA decision:

```text
Recommend FK from sc_b_dispatch_observations(dispatch_record_id)
          to sc_b_dispatch_records(dispatch_record_id).
```

Rationale:

```text
- The active test/runtime profile uses one Flyway migration stream.
- V100 creates sc_b_dispatch_records before V101.
- RuntimeDispatchService records observations only after state transitions create/update dispatch records.
- The FK prevents orphan observation rows and strengthens H3 without coupling SC-B to SC-C.
```

Test caveat:

```text
Repository tests that call JdbcDispatchObservationRepository.record(...) directly must create a corresponding dispatch record first, for example through JdbcDispatchStateRepository.claim(dispatchRecordId).
```

---

## 6. H3 implementation surface

### 6.1 New production files

Required:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/
  JdbcDispatchObservationRepository.java

src/main/resources/db/migration/
  V101__sc_b_dispatch_observation_persistence.sql
```

Optional, not recommended for the seed unless justified:

```text
bus/runtime/dispatch/model/DispatchObservationKind.java
bus/runtime/port/DispatchObservationReadPort.java
```

### 6.2 Modified files

Required test/architecture patch:

```text
src/test/java/com/sovereign/connect/bus/ScBusOutboxBridgeArchitectureTest.java
  → update h2DoesNotAddScBMigrations to allow V100 + V101
```

Potential architecture test update:

```text
src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
  → update scBMigrationCreatesOnlyScBTables to inspect every migration whose filename contains sc_b
```

### 6.3 New test files

Recommended:

```text
src/test/java/com/sovereign/connect/bus/runtime/persistence/
  JdbcDispatchObservationRepositoryTest.java

src/test/java/com/sovereign/connect/bus/runtime/dispatch/
  RuntimeDispatchObservationPersistenceTest.java

src/test/java/com/sovereign/connect/bus/
  ScBusObservationPersistenceArchitectureTest.java
```

---

## 7. Recommended migration DDL

File:

```text
src/main/resources/db/migration/V101__sc_b_dispatch_observation_persistence.sql
```

Preferred seed DDL:

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

Optional additional index:

```sql
CREATE INDEX IF NOT EXISTS idx_sc_b_dispatch_observations_attempt
    ON sc_b_dispatch_observations(attempt_id);
```

CSA decision:

```text
The seven-field schema is preferred for MU-027 because it matches DispatchObservationRecord exactly.
```

The richer MIR-compatible columns below may be added as nullable future-proofing if execution chooses, but they are not required for H3 and must remain NULL under the current Java record shape:

```text
source_record_id
observation_kind
correlation_id
lane
logical_topic
```

---

## 8. Reference implementation shape

The execution package may use this implementation shape as paste-ready guidance.

```java
package com.sovereign.connect.bus.runtime.persistence;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchObservationPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcDispatchObservationRepository implements DispatchObservationPort {

    private final JdbcTemplate jdbc;

    public JdbcDispatchObservationRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(
            Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public void record(DispatchObservationRecord observation) {
        Objects.requireNonNull(observation, "observation is required");
        Objects.requireNonNull(observation.observationId(), "observationId is required");
        Objects.requireNonNull(observation.dispatchRecordId(), "dispatchRecordId is required");
        Objects.requireNonNull(observation.state(), "state is required");
        Objects.requireNonNull(observation.observedAt(), "observedAt is required");

        jdbc.update("""
            INSERT INTO sc_b_dispatch_observations
            (observation_id, dispatch_record_id, attempt_id,
             state, code, sanitized_reason, observed_at_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            observation.observationId().toString(),
            observation.dispatchRecordId().toString(),
            observation.attemptId() != null ? observation.attemptId().toString() : null,
            observation.state().name(),
            observation.code(),
            observation.sanitizedReason(),
            observation.observedAt().toEpochMilli()
        );
    }

    @Override
    public List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId) {
        Objects.requireNonNull(dispatchRecordId, "dispatchRecordId is required");
        return List.copyOf(jdbc.query("""
            SELECT observation_id, dispatch_record_id, attempt_id,
                   state, code, sanitized_reason, observed_at_ms
            FROM sc_b_dispatch_observations
            WHERE dispatch_record_id = ?
            ORDER BY observed_at_ms ASC, observation_id ASC
            """,
            (rs, rowNum) -> new DispatchObservationRecord(
                UUID.fromString(rs.getString("observation_id")),
                UUID.fromString(rs.getString("dispatch_record_id")),
                rs.getString("attempt_id") != null
                    ? UUID.fromString(rs.getString("attempt_id")) : null,
                DispatchState.valueOf(rs.getString("state")),
                rs.getString("code"),
                rs.getString("sanitized_reason"),
                Instant.ofEpochMilli(rs.getLong("observed_at_ms"))
            ),
            dispatchRecordId.toString()
        ));
    }
}
```

Boundary requirements:

```text
No core.* imports.
No adapter.* imports.
No integration.scledgerdispatch.* imports.
No broker client imports.
```

---

## 9. Test strategy

### 9.1 H1 datasource pattern to reuse

Use the same raw SQLiteDataSource + Flyway pattern as `JdbcDispatchStateRepositoryTest`:

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

Rules:

```text
- No PerConnectionPragmaDataSource in bus runtime persistence tests.
- No Spring context required.
- Repository tests with FK must create dispatch records before inserting observations.
```

### 9.2 JdbcDispatchObservationRepositoryTest

Expected tests: 11.

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

Additional optional test if not covered by the exact 11 above:

```text
foreignKeyRejectsUnknownDispatchRecordId
```

If added, update the expected test count in the execution package.

### 9.3 RuntimeDispatchObservationPersistenceTest

Expected tests: 8.

Use:

```text
JdbcDispatchStateRepository
JdbcDispatchObservationRepository
InMemoryScBusPort
Manual RuntimeDispatchService wiring
```

Test names:

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

### 9.4 RuntimeDispatchService wiring pattern

`RuntimeDispatchService` constructor requires 7 dependencies:

```java
new RuntimeDispatchService(
    busPort,
    candidateReadPort,
    dispatchStateRepository,
    dispatchObservationRepository,
    new EnvelopeValidationService(),
    new RoutingKeyValidationService(),
    new CorrelationValidationService()
)
```

For H3 runtime tests:

```text
- Use JdbcDispatchStateRepository for state.
- Use JdbcDispatchObservationRepository for observations.
- Use InMemoryScBusPort for dispatch.
- Use either a minimal test DispatchCandidateReadPort or direct dispatch(candidate) calls when sufficient.
- Do not use mocks for DispatchObservationPort; the point is persistent observation visibility.
```

### 9.5 ScBusObservationPersistenceArchitectureTest

Expected tests: 5.

```text
busRuntimePersistenceDoesNotImportCore
coreDoesNotImportBusRuntime
noBrokerDependencyIntroduced
scBObservationMigrationUsesV101
scBObservationMigrationCreatesOnlyScBTables
```

Architecture tests must follow the existing file-walk + `Files.readString` + AssertJ style. Do not introduce ArchUnit.

---

## 10. Mandatory architecture-test updates

### 10.1 ACTION-H3-001 — update `h2DoesNotAddScBMigrations`

Current pre-H3 assertion:

```java
assertThat(scBMigrations).containsExactly("V100__sc_b_dispatch_state_persistence.sql");
```

Required H3-compatible version:

```java
@Test
void h2DoesNotAddScBMigrations() throws Exception {
    try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/db/migration"))) {
        List<String> scBMigrations = paths
            .filter(path -> path.getFileName().toString().contains("sc_b"))
            .map(path -> path.getFileName().toString())
            .sorted()
            .toList();
        assertThat(scBMigrations).containsExactlyInAnyOrder(
            "V100__sc_b_dispatch_state_persistence.sql",
            "V101__sc_b_dispatch_observation_persistence.sql"
        );
    }
}
```

Equivalent acceptable rename:

```text
scBMigrationsContainExpectedHardeningFiles
```

with the same assertion.

### 10.2 Replace obsolete pre-H3 observation-persistence absence assertion

Current pre-H3 test:

```text
ScBusOutboxBridgeArchitectureTest.dispatchObservationPersistenceStillDeferred
```

H3 impact:

```text
This assertion must be removed or replaced.
```

Recommended replacement assertions:

```text
- Dispatch observation persistence exists only under bus.runtime.persistence.
- JdbcDispatchObservationRepository does not import core.*.
- JdbcDispatchObservationRepository does not import integration.scledgerdispatch.*.
- JdbcDispatchObservationRepository does not import adapter.*.
- JdbcDispatchObservationRepository does not import broker APIs.
```

### 10.3 Update SC-B migration architecture guard

Current `ScBusHardeningArchitectureTest.scBMigrationCreatesOnlyScBTables` inspects only V100.

H3 recommendation:

```text
Update it to inspect every migration whose filename contains sc_b.
```

Required behavior:

```text
- V101 must not create/alter SC-C tables.
- V101 must not introduce broker dependencies.
- V101 must not mutate sc_c_outbox_entries.
```

---

## 11. Stop conditions for execution package

```text
STOP-1: mvn -q compile
Expected: BUILD SUCCESS
Verify: no core.** import from bus.**
```

```text
STOP-2: mvn -q test -Dtest="JdbcDispatchObservationRepositoryTest"
Expected: 11 tests, 0 failures
```

```text
STOP-3: mvn -q test -Dtest="RuntimeDispatchObservationPersistenceTest,ScBusObservationPersistenceArchitectureTest,ScBusHardeningArchitectureTest,ScBusOutboxBridgeArchitectureTest"
Expected: all pass, 0 failures
Note: ScBusOutboxBridgeArchitectureTest will only pass after ACTION-H3-001 is applied.
```

```text
STOP-4: mvn -q test
Expected: >= 378 tests, 0 failures, 0 errors, 0 skipped
SC-C baseline: 240 tests still green
```

Test-count rationale:

```text
Current baseline: 354 tests
Expected delta:   +24 tests (11 + 8 + 5)
Expected total:   >= 378 tests
```

If execution adds the optional FK negative test, expected total should be increased accordingly.

---

## 12. Scope boundaries for execution package

H3 must not introduce:

```text
- NATS / JetStream / broker client.
- Lifecycle channel implementation.
- Productive ScdCommand.
- Productive ScDeliveryError emission.
- SC-D adapter execution.
- SC-C OutboxEntryStatus mutation.
- SC-C ledger mirroring of dispatch observations.
- Semantic interpretation of DISPATCHED / EXHAUSTED / DELIVERY_FAILED.
- RuntimeDispatchService lifecycle redesign.
- CANDIDATE_SELECTED emission hook.
```

H3 must preserve:

```text
- DispatchObservationPort as primary port.
- InMemoryDispatchObservationRepository.
- RuntimeDispatchService current emission sequence.
- OutboxEntry != DispatchCandidate != ScEnvelope.
- SC-B technical state remains technical only.
```

---

## 13. Risk assessment

### RISK-027-001 — Pre-H3 architecture tests fail after valid H3 implementation

Severity: high.

Cause:

```text
Existing tests intentionally assert observation persistence is deferred and SC-B migrations are exactly V100.
```

Mitigation:

```text
Execution package must require patching those architecture tests as part of H3.
```

### RISK-027-002 — Implementer expands DispatchObservationRecord prematurely

Severity: medium.

Cause:

```text
MIR schema allows optional future context fields, but current runtime does not carry those fields.
```

Mitigation:

```text
Persist the current seven-field record. Optional DB columns may remain nullable.
Do not redesign RuntimeDispatchService in MU-027.
```

### RISK-027-003 — FK test failures from direct repository use

Severity: medium.

Cause:

```text
Direct record(...) tests may try to insert an observation for a dispatchRecordId not present in sc_b_dispatch_records.
```

Mitigation:

```text
Create dispatch records through JdbcDispatchStateRepository in repository tests.
Add one negative FK test only if test-count expectations are updated.
```

### RISK-027-004 — Observation persistence becomes semantic authority

Severity: high.

Mitigation:

```text
Implementation report and tests must preserve that DISPATCHED is technical delivery only,
DELIVERY_FAILED is technical delivery failure only, and EXHAUSTED is not domain failure.
```

### RISK-027-005 — ScDeliveryError emission sneaks into H3

Severity: medium.

Mitigation:

```text
H3 may prepare persisted fields useful for future ScDeliveryError projection,
but it must not emit productive ScDeliveryError events.
```

---

## 14. CSA target checklist for execution package

```text
CSA-027-T01 — Confirm DispatchObservationRecord is unchanged or any change is explicitly justified.
CSA-027-T02 — Confirm DispatchObservationPort remains the primary port.
CSA-027-T03 — Confirm JdbcDispatchObservationRepository implements DispatchObservationPort.
CSA-027-T04 — Confirm InMemoryDispatchObservationRepository remains present.
CSA-027-T05 — Confirm V101 migration exists and uses V100+ SC-B range.
CSA-027-T06 — Confirm FK to sc_b_dispatch_records is present unless implementation report justifies deviation.
CSA-027-T07 — Confirm obsolete pre-H3 architecture tests are updated.
CSA-027-T08 — Confirm CANDIDATE_SELECTED remains reserved, not implemented.
CSA-027-T09 — Confirm runtime emission sequence is covered by tests.
CSA-027-T10 — Confirm no bus.runtime.persistence import of core.*, adapter.* or integration.scledgerdispatch.*.
CSA-027-T11 — Confirm no NATS/JetStream/broker dependency.
CSA-027-T12 — Confirm no SC-C outbox mutation.
CSA-027-T13 — Confirm no productive ScDeliveryError emission.
CSA-027-T14 — Confirm full regression remains green.
```

---

## 15. Search ledger

```text
DispatchObservationRecord:
  7 fields, all SQL-mappable, observationId generated upstream and preserved as-is.

DispatchObservationPort:
  2 methods — record/observationsFor — port unchanged.

InMemoryDispatchObservationRepository:
  CopyOnWriteArrayList, null-check, stream-filter — unchanged.

RuntimeDispatchService emit points:
  9 confirmed; no CANDIDATE_SELECTED hook.

V100 migration:
  sc_b_dispatch_records exists with dispatch_record_id PK; FK viable.

V101 migration:
  absent pre-H3; greenfield for MU-027.

Architecture tests:
  h2DoesNotAddScBMigrations containsExactly(V100) and will break after V101.
  dispatchObservationPersistenceStillDeferred becomes obsolete after H3.

H1 test datasource pattern:
  raw SQLiteDataSource + Flyway; no PerConnectionPragmaDataSource.

Boundary:
  bus→core = 0; core→bus.runtime = 0; broker dependency = 0.
```

---

## 16. Final recommendation

```text
CSA-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v0.2.0-merged
Status: execution-package-ready
```

Recommended next step:

```text
Open execution package for MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001.
```

Recommended branch:

```text
feat/sc-b-mir-027-dispatch-observation-persistence
```

Recommended commit:

```text
feat(sc-b): persist dispatch observations
```

Final note:

```text
H3 is narrower than NATS work. It closes durable technical observability for the
existing abstract/runtime dispatch path. It does not make SC-B semantic authority,
and it does not make a physical broker the diagnostic source of truth.
```
