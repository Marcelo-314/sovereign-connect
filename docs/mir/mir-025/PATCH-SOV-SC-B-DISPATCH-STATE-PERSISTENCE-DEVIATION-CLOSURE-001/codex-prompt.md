# Codex Prompt — PATCH-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-DEVIATION-CLOSURE-001

Apply this patch on the existing MU-025 branch:

```text
feat/sc-b-mir-025-dispatch-state-persistence
```

Do not create a new branch. Do not open a new MIR. This patch closes the deviations found in:

```text
VALIDATION-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001-v0.1.0-review.md
```

Read first:

```text
docs/mir/mir-025/context.md
docs/mir/mir-025/acceptance-map.md
docs/mir/mir-025/MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001.md
docs/mir/mir-025/code-surface-audit.md
docs/mir/mir-025/implementation-report.md
```

Then apply the patch instructions from:

```text
docs/mir/mir-025/patches/PATCH-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-DEVIATION-CLOSURE-001/context.md
```

---

## Objective

Close MU-025 validation deviations before marking the MU as Validated L4.

Required fixes:

```text
1. transitionWithEvidence(...) must reject targetState != CANCELLED_BY_SUPERSEDE.
2. Add regression test transitionWithEvidenceRejectsNonSupersedeTargetState.
3. Align JdbcDispatchStateRepositoryTest method names with package references.
4. Remove/avoid bus test dependency on adapter-owned PerConnectionPragmaDataSource/test support.
```

---

## Hard scope

H1 patch only.

Do not implement:

```text
outbox bridge
ScOutboxDispatchReadPort
integration.scledgerdispatch
DispatchObservationPort persistence
DeliveryLane -> ScBusLane bridge mapping
SIGNAL handling
lifecycle channel
NATS / JetStream / broker binding
```

Do not change:

```text
DispatchStateWritePort
RuntimeDispatchService
DispatchAttempt
DispatchState enum
InMemoryDispatchStateRepository
```

---

## Step 1 — Guard transitionWithEvidence

File:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
```

Add a strict guard so `transitionWithEvidence(...)` accepts only:

```text
targetState == DispatchState.CANCELLED_BY_SUPERSEDE
```

Any other target state must throw `IllegalArgumentException`.

Do not weaken existing evidence validation. Blank/null evidence is still invalid.

---

## Step 2 — Add regression test

File:

```text
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
```

Add:

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

Adapt only the repository helper call if needed. Preserve the assertion.

---

## Step 3 — Align test method names

Ensure `JdbcDispatchStateRepositoryTest` includes these exact method names:

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

Rename equivalent tests if necessary. Do not delete useful extra coverage.

---

## Step 4 — Close datasource/test-support deviation

Bus tests must not import adapter-owned infrastructure.

Required final state:

```text
src/test/java/com/sovereign/connect/bus/** contains no import of com.sovereign.connect.adapter
src/test/java/com/sovereign/connect/bus/** contains no direct reference to PerConnectionPragmaDataSource
```

Preferred setup: use `org.sqlite.SQLiteDataSource` directly in `JdbcDispatchStateRepositoryTest`.

If needed, update `ScBusHardeningArchitectureTest` to assert the test-source boundary.

---

## STOP-1 — Compile

```bash
mvn -q compile
```

Expected: success.

---

## STOP-2 — Targeted repository tests

```bash
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest"
```

Expected:

```text
14 tests, 0 failures, 0 errors, 0 skipped
```

---

## STOP-3 — Architecture and bus hardening tests

```bash
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest"
```

Expected: all pass, 0 failures.

---

## STOP-4 — Full regression

```bash
mvn -q test
```

Expected:

```text
>= 316 tests, 0 failures, 0 errors, 0 skipped
```

If any SC-C or existing bus test fails, stop and report. Do not silence.

---

## Step 5 — Update implementation report

Update:

```text
docs/mir/mir-025/implementation-report.md
```

Record:

```text
patch commit hash
files changed
new regression test
final test counts
datasource/test-support deviation closure
architecture boundary confirmation
retained H2/H3 debts
final recommended status: Validated L4
```

---

## Commit

```bash
git commit -m "fix(sc-b): close dispatch state persistence deviations"
```
