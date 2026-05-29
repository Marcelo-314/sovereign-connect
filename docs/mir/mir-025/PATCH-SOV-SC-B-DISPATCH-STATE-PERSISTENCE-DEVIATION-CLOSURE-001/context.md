# Execution Context — PATCH-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-DEVIATION-CLOSURE-001

```text
Version:  v0.1.0
MIR:      MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v0.2.0-candidate
MU:       MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
Slot:     MU-025
Track:    SC-B Runtime Dispatch Hardening / H1 patch closure
Branch:   feat/sc-b-mir-025-dispatch-state-persistence
Baseline: post-MU-025 implementation review
Review:   VALIDATION-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v0.1.0-review
Scope:    close deviations before marking MU-025 Validated L4
```

---

## 0. Purpose

This patch closes the remaining MU-025 validation deviations.

It is **not** a new MIR and **not** a new branch. It applies on the existing MU-025 branch.

Patch goals:

```text
1. Restrict transitionWithEvidence(...) to CANCELLED_BY_SUPERSEDE only.
2. Add regression coverage for non-supersede target rejection.
3. Align test method names with the v0.2.0 execution package references.
4. Remove or avoid bus test dependency on adapter-owned test support / PerConnectionPragmaDataSource.
5. Preserve full H1-only scope.
```

---

## 1. Baseline facts from validation

Validated implementation baseline:

```text
JdbcDispatchStateRepository exists.
V100__sc_b_dispatch_state_persistence.sql exists.
sc_b_dispatch_records and sc_b_dispatch_attempts exist.
source_record_id is nullable.
H1 scope boundaries are preserved.
No outbox bridge exists.
No ScOutboxDispatchReadPort exists.
No integration.scledgerdispatch exists.
No broker dependency exists.
Full test evidence: sovereign-connect 315 tests, 0 failures, 0 errors, 0 skipped.
EIB evidence: 56 tests, 0 failures, 0 errors, 0 skipped.
```

Validation blocker:

```text
transitionWithEvidence(dispatchRecordId, targetState, evidenceRef)
currently permits targetState values other than CANCELLED_BY_SUPERSEDE.
```

Required contract:

```text
transitionWithEvidence(...) is only valid for:
  PENDING|CLAIMED|RETRY_SCHEDULED -> CANCELLED_BY_SUPERSEDE

Any targetState other than CANCELLED_BY_SUPERSEDE MUST be rejected with IllegalArgumentException.
```

---

## 2. Hard boundaries

Allowed files to change:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java      [only if needed]
docs/mir/mir-025/implementation-report.md
```

Forbidden changes:

```text
Do not change DispatchStateWritePort.
Do not change RuntimeDispatchService.
Do not change DispatchAttempt.
Do not change DispatchState enum.
Do not change InMemoryDispatchStateRepository unless a test name reference forces no production change.
Do not change V100 migration unless a harmless comment/report sync is needed.
Do not create ScOutboxDispatchReadPort.
Do not create integration.scledgerdispatch.
Do not import OutboxEntry / OutboxEntryStatus / LedgerEntry.
Do not add DispatchObservationPort persistence.
Do not add DeliveryLane -> ScBusLane bridge mapping.
Do not handle SIGNAL lane.
Do not add NATS / JetStream / Redis / Vert.x / gRPC / MQTT / Kafka.
Do not introduce JPA / Hibernate / ORM.
Do not create a new branch or a new MIR.
```

Architecture invariants:

```text
bus.** MUST NOT import core.**
bus.** MUST NOT import adapter.**
core.** MUST NOT import bus.runtime.**
bus.** MUST NOT import physical broker APIs
```

---

## 3. Patch 1 — transitionWithEvidence target guard

In `JdbcDispatchStateRepository.transitionWithEvidence(...)`, add this guard before delegating to any generic transition logic:

```java
if (targetState != DispatchState.CANCELLED_BY_SUPERSEDE) {
    throw new IllegalArgumentException(
        "transitionWithEvidence is only allowed for CANCELLED_BY_SUPERSEDE"
    );
}
```

Required behavior:

```text
null dispatchRecordId -> IllegalArgumentException
null targetState -> IllegalArgumentException
blank/null evidenceRef -> IllegalArgumentException
targetState != CANCELLED_BY_SUPERSEDE -> IllegalArgumentException
CANCELLED_BY_SUPERSEDE from PENDING / CLAIMED / RETRY_SCHEDULED -> allowed
CANCELLED_BY_SUPERSEDE from any other state -> IllegalStateException
```

Do not weaken the existing evidence requirement.

---

## 4. Patch 2 — regression test for target guard

Add this test to `JdbcDispatchStateRepositoryTest`:

```java
@Test
void transitionWithEvidenceRejectsNonSupersedeTargetState() {
    JdbcDispatchStateRepository repository = repo();
    UUID dispatchRecordId = UUID.randomUUID();
    repository.claim(dispatchRecordId);

    assertThatThrownBy(() -> repository.transitionWithEvidence(
            dispatchRecordId,
            DispatchState.DISPATCHING,
            "ledger:unexpected-evidence"
    )).isInstanceOf(IllegalArgumentException.class);
}
```

If the existing repository helper requires a named repo or datasource, adapt only the setup call. Do not change the semantic assertions.

Expected post-patch test count:

```text
JdbcDispatchStateRepositoryTest: 14 tests
Full sovereign-connect module: >= 316 tests
```

---

## 5. Patch 3 — test method names alignment

The execution package required exact method names for acceptance-map traceability.

Verify that `JdbcDispatchStateRepositoryTest` contains these method names exactly:

```text
firstClaimIsPersisted
currentAttemptSurvivesRepositoryRecreation
claimedDispatchingDispatchedSurvivesRestart
dispatchingDeliveryFailedSurvivesRestart
retryIncreasesAttemptNumber
retryAttemptNumberContinuitySurvivesRepositoryRecreation
exhaustedIsPersistentAndNotClaimable
supersedeCancellationRequiresEvidenceRef
supersedeCancellationPersistsEvidenceRef
invalidTransitionsAreRejected
unknownCurrentAttemptReturnsEmpty
dispatchStateNamesDoNotEncodeSemanticMeaning
transitionWithEvidenceRejectsNonSupersedeTargetState
```

If the implementation currently uses equivalent but differently named tests, rename them rather than duplicating coverage.

If an additional valid test already exists, preserve it. Do not delete useful coverage solely to match the old 12-test expectation.

---

## 6. Patch 4 — datasource/test-support deviation closure

The original execution package forbade bus tests from using `PerConnectionPragmaDataSource` because it belongs to `adapter.persistence.sqlite`.

Required final state:

```text
src/test/java/com/sovereign/connect/bus/** MUST NOT import com.sovereign.connect.adapter.**
src/test/java/com/sovereign/connect/bus/** MUST NOT directly import or instantiate PerConnectionPragmaDataSource
```

Preferred test setup:

```java
@TempDir
Path tempDir;

private DataSource dataSource;

@BeforeEach
void setUp() {
    org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
    ds.setUrl("jdbc:sqlite:" + tempDir.resolve("sc-b-dispatch-test.sqlite"));
    org.flywaydb.core.Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    this.dataSource = ds;
}
```

If a shared `SQLiteTestSupport` helper is retained, it must not live under `adapter.*` and must not leak adapter-owned infrastructure into `bus/**` tests. The simplest fix is to inline the raw `SQLiteDataSource` setup above in the bus test.

Add or preserve an architecture test assertion that scans `src/test/java/com/sovereign/connect/bus` and rejects:

```text
com.sovereign.connect.adapter
PerConnectionPragmaDataSource
```

---

## 7. Validation commands

Run:

```bash
mvn -q compile
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest"
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest"
mvn -q test
```

Expected:

```text
JdbcDispatchStateRepositoryTest: 14 tests, 0 failures, 0 errors, 0 skipped
Full sovereign-connect: >= 316 tests, 0 failures, 0 errors, 0 skipped
EIB baseline remains green if run separately or recorded from prior report
```

If any SC-C or existing bus test fails, stop and report. Do not silence failures.

---

## 8. Implementation report update

Update `docs/mir/mir-025/implementation-report.md` with:

```text
Patch commit hash
Files changed
transitionWithEvidence target guard result
New test transitionWithEvidenceRejectsNonSupersedeTargetState
Final JdbcDispatchStateRepositoryTest test count
Datasource/test-support deviation closure
Architecture test confirmation: no bus test imports adapter.* / PerConnectionPragmaDataSource
Full regression summary
Statement: H1 scope preserved
Statement: ScOutboxDispatchReadPort remains absent
Statement: integration.scledgerdispatch remains absent
Statement: no OutboxEntry / OutboxEntryStatus mutation
Statement: no broker dependency
Final recommended status: MU-025 Validated L4
```

---

## 9. Commit

Suggested commit:

```bash
git commit -m "fix(sc-b): close dispatch state persistence deviations"
```
