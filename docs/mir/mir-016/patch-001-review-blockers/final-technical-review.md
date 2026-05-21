# Final Technical Review — MU-016 Temporal Engine Industrial Hardening

```text
Document:            final-technical-review.md
Version:             v0.1.1-final-review
MU:                  MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot:    MU-016
MIR:                 MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
Reviewed ZIP:        sovereign-connect-016.zip
Date:                2026-05-20
Revision:            v0.1.1 adds non-blocking implementation-report requirements and fixes Phase A specificity
Status:              REQUIRES CORRECTIONS — not yet L4 / not industrial-grade
```

---

## Changelog v0.1.1-final-review

This version incorporates the post-merge review note:

```text
- Adds non-blocking implementation-report requirements for:
  I-05 runtime failure mapping precision;
  I-06 health counters actually incremented;
  I-07 BLOB ID binding versus String binding.
- Clarifies that B-02 atomic idempotency must be decided in the patch execution context before Codex implementation.
- Adds Phase D reporting requirements for failure mapping, health counters and BLOB ID handling.
```

---

## 1. Executive verdict

```text
MU-016 implementation: PARTIAL PASS
Industrial closure:    NOT YET
L4 status:             NOT APPROVED
```

The implementation is a substantial improvement over the TemporalActs seed. It introduces the expected industrial-grade scaffolding: Spring lifecycle, SQLite/Flyway, temporal request/application ports, temporal observation ports, request idempotency storage, engine health, recovery observability, single-node guard infrastructure and bounded query/polling surfaces.

However, the implementation still has structural gaps that can violate industrial guarantees:

```text
1. Recovery may start polling before all overdue acts are classified.
2. Create request idempotency is not atomic with the creation side effect.
3. Unknown-payload FAILED transitions are not proven ledgered/audited.
4. Single-node guard heartbeat TTL is incorrect.
5. Observation of quarantined/unknown-payload acts can crash.
```

Therefore, the implementation must remain in **REQUIRES CORRECTIONS** until the blockers below are patched and tested.

---

## 2. Evidence baseline

Reported test evidence from the implementation package:

```text
Tests run:   109
Failures:      0
Errors:        0
Skipped:       0
```

The green test suite is valuable but insufficient for industrial closure because several failure modes are not covered by existing tests:

```text
concurrent idempotent create;
incomplete recovery batch;
second lifecycle / lock contention;
heartbeat lock loss;
unknown payload observation path;
FAILED transition ledgering.
```

Local `mvn test` was not executed in the review environment because Maven was unavailable.

---

## 3. Positive implementation findings

The implementation correctly introduces or preserves:

```text
- TemporalEngineLifecycle implements SmartLifecycle.
- TemporalEngineRunner remains virtual-thread based.
- No @Scheduled is used for engine lifecycle.
- No Thread.sleep is used in the runner.
- SQLite/Flyway migrations exist.
- PerConnectionPragmaDataSource applies PRAGMAs per connection.
- PRAGMA foreign_keys = ON is verified in tests.
- PRAGMA synchronous = FULL is used.
- TemporalActApplicationPort exists and is write/request-only.
- TemporalActObservationPort exists.
- TemporalActObservation does not expose aggregate internals or remainingMs.
- Rejected(domain) and Failed(runtime) result classes are separated.
- TemporalRuntimeFailureCode includes engine lifecycle failures.
- temporal_request_idempotency table exists.
- temporal_engine_locks table exists.
- recovery_runs / recovery_findings exist.
- Signal-only v1 is preserved.
- ActionTemporalPayload and ActionRequest are not introduced.
- SC-B, NATS, dispatcher and View Composer are not introduced.
- Existing seed tests remain green.
```

These are substantial achievements and should be preserved in the patch.

---

## 4. Final blocker list

### B-01 — Recovery batch incompletion must not allow polling

**Severity:** BLOCKER  
**Area:** TemporalEngineLifecycle / recovery gate  
**Industrial invariant violated:** recovery classification must complete before polling/firing.

The implementation calls `classifyMisfires(...)` during `TemporalEngineLifecycle.start()`, but the available evidence does not prove that it handles `TemporalRecoveryResult.complete() == false` correctly.

If recovery classifies only the first bounded batch and then starts the runner, remaining overdue non-terminal acts may still be `PENDING` / `ARMED` with `dueAt <= now`. The polling loop can then fire them as `FIRED` instead of classifying them as `MISFIRED`.

That would violate:

```text
Overdue non-terminal acts after downtime MUST be classified MISFIRED before polling starts.
Recovery MUST complete before engine firing.
Recovery MUST NOT emit TimerFired for missed acts.
```

#### Required fix

`TemporalEngineLifecycle.start()` must implement one of these strategies:

```text
Option A — Complete recovery before start:
  repeat classifyMisfires(...) in bounded batches until result.complete() == true;
  only then acquire/confirm lock, set RUNNING and start runner.

Option B — Fail closed on incomplete recovery:
  if result.complete() == false:
    set health FAILED;
    do not start runner;
    expose RECOVERY_NOT_COMPLETED / TEMPORAL_ENGINE_NOT_READY.
```

The implementation MUST NOT enter `RUNNING` while recovery is incomplete.

#### Required tests

```text
recoveryDoesNotStartPollingWhenMisfireBatchIncomplete
incompleteRecoveryBatchDoesNotFireOverdueActs
lifecycleFailsClosedOrContinuesRecoveryUntilComplete
```

---

### B-02 — Create idempotency is not co-transactional with TemporalAct creation

**Severity:** BLOCKER  
**Area:** TemporalActApplicationService / temporal_request_idempotency  
**Industrial invariant violated:** idempotent request must not create duplicate semantic effects.

The current flow is described as:

```text
1. find idempotency record;
2. create TemporalAct through TemporalActService;
3. read observation;
4. insert temporal_request_idempotency record.
```

The critical issue: TemporalAct creation and idempotency insertion occur in separate operations/transactions.

Crash window:

```text
TX-1 commits TemporalAct + TemporalActCreated ledger.
Process crashes before idempotency record is inserted.
Retry finds no idempotency record.
Retry creates a second TemporalAct with a new ID.
```

This violates industrial request idempotency.

#### Required fix

Create/cancel idempotency must become atomic with the side effect.

Recommended implementation pattern:

```text
1. In one transaction, reserve/claim idempotency record.
2. If existing record found:
   - same fingerprint => replay existing result;
   - different fingerprint => reject IDEMPOTENCY_CONFLICT.
3. If claim succeeds:
   - create/cancel TemporalAct;
   - append required ledger;
   - store final result in idempotency table;
   - commit atomically.
```

Acceptable designs:

```text
A. TemporalActApplicationService owns the transaction and uses lower-level ports.
B. TemporalActService exposes transactional participation hooks.
C. A new orchestration port performs claim + mutation + ledger + result update atomically.
```

The final implementation must prove:

```text
No accepted TemporalAct creation can commit without a corresponding idempotency record.
No idempotency claim can remain permanently accepted without a reconstructible result.
```

#### Required tests

```text
concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct
createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct
createReplayReturnsOriginalTemporalActObservation
conflictingCreateFingerprintReturnsIdempotencyConflict
cancelIdempotencyIsAtomicWithCancelLedger
```

---

### B-03 — Unknown payload FAILED transition must be ledgered/audited and safely observable

**Severity:** BLOCKER  
**Area:** TemporalEngineService / SQLiteTemporalActRepository / TemporalActObservationService  
**Industrial invariant violated:** terminal lifecycle transition requires evidence and query safety.

The implementation handles unknown payloads by returning a `null` sentinel and then calling a failure path. That direction is correct. However, two closure requirements remain insufficiently proven:

```text
1. FAILED transition caused by unknown payload must append TemporalActFailed ledger/audit evidence.
2. Observation of the quarantined FAILED act must not crash.
```

Without a ledger/audit entry, the terminal transition is not industrially traceable. Without observation safety, the northbound observation contract is not robust.

#### Required fix A — ledger/audit

Unknown payload failure must atomically record:

```text
status = FAILED;
terminal_at_ms = now;
terminal_reason = UNKNOWN_PAYLOAD_KIND / diagnostic message;
TemporalActFailed ledger entry;
recovery finding if the failure is encountered during recovery.
```

If `markFailed(...)` currently updates only `temporal_acts`, it must be extended or wrapped so the ledger entry is part of the same transaction.

#### Required fix B — observation safety

`TemporalActObservationService.toObservation(...)` must not cast blindly to `SignalTemporalPayload`.

Safe behavior:

```text
if payload instanceof SignalTemporalPayload:
  payloadKind = SIGNAL;
  label/signalKind from payload.

else if payload == null and status == FAILED:
  payloadKind = UNKNOWN;
  label/signalKind = null or diagnostic-safe values;
  terminalReason preserved.

else:
  fail closed in a typed/diagnostic way, not ClassCastException/NPE.
```

#### Required tests

```text
unknownPayloadFailureAppendsTemporalActFailedLedgerEntry
unknownPayloadFailureCreatesRecoveryFinding
unknownPayloadFailedActIsObservableByFindById
unknownPayloadFailedActIsObservableInListTerminal
unknownPayloadFailedActIsObservableInListMisfiredWhenApplicable
```

---

### B-04 — Single-node guard heartbeat TTL calculation is incorrect

**Severity:** BLOCKER  
**Area:** SQLiteTemporalEngineLockRepository / TemporalEngineLifecycle heartbeat  
**Industrial invariant violated:** single-node guard must remain meaningful under process crash / heartbeat loss.

Current heartbeat SQL was reported as:

```sql
SET heartbeat_at_ms = ?,
    expires_at_ms = expires_at_ms + (expires_at_ms - heartbeat_at_ms)
```

This is incorrect. It grows the lock expiration window instead of setting it to `now + ttlMs`.

Example:

```text
t=0:     expires=30000
t=10000: new expires = 60000, expected 40000
t=20000: new expires = 110000, expected 50000
```

The lock may effectively never expire after repeated heartbeats, undermining failover/recovery semantics.

#### Required fix

Update heartbeat signature and SQL:

```java
void heartbeat(
    String habitatId,
    String storagePartitionRef,
    String engineInstanceId,
    Instant now,
    long ttlMs
);
```

SQL:

```sql
UPDATE temporal_engine_locks
SET heartbeat_at_ms = :nowMs,
    expires_at_ms   = :nowMs + :ttlMs
WHERE habitat_id = :habitatId
  AND storage_partition_ref = :storagePartitionRef
  AND engine_instance_id = :engineInstanceId
  AND status = 'ACTIVE'
```

The update count must be checked:

```text
if affectedRows != 1:
  engine lost lock or no longer owns it;
  stop runner;
  health = FAILED;
  expose SINGLE_NODE_GUARD_VIOLATED or equivalent runtime failure.
```

#### Required tests

```text
heartbeatExtendsLockToNowPlusTtl
heartbeatDoesNotGrowExpiryExponentially
heartbeatLossStopsRunnerOrMarksFailed
secondLifecycleFailsFastWhenLockHeld
```

---

## 5. High-priority non-blocking issues

### H-01 — DEGRADED does not auto-recover to RUNNING

**Severity:** HIGH / simple fix  
**Area:** TemporalEngineHealth

`recordPollFailure(...)` sets status to `DEGRADED`, but `recordPollSuccess()` does not return status to `RUNNING`.

Required fix:

```java
public void recordPollSuccess() {
    Instant now = Instant.now();
    lastPollAt = now;
    lastSuccessfulPollAt = now;
    lastFailure = null;
    if (status == TemporalEngineStatus.DEGRADED) {
        status = TemporalEngineStatus.RUNNING;
    }
}
```

Required test:

```text
successfulPollAfterFailureRestoresRunningStatus
```

---

### H-02 — tryAcquire has TOCTOU risk

**Severity:** HIGH unless deployment contract is explicitly accepted as sufficient  
**Area:** SQLiteTemporalEngineLockRepository

The reported lock acquisition uses `SELECT COUNT` followed by `INSERT OR REPLACE`. This is not an atomic acquisition.

Given the v1 single-node deployment contract, this may be acceptable only if explicitly documented and tested as fail-fast for a second lifecycle under normal deployment conditions.

Recommended hardening:

```text
Use BEGIN IMMEDIATE or an atomic INSERT/UPDATE predicate strategy.
```

Minimum required test:

```text
secondLifecycleFailsFastWhenLockHeld
```

Recommended additional test:

```text
concurrentTryAcquireAllowsOnlyOneWinner
```

---

### H-03 — Runtime failure mapping must be precise in the implementation report

**Severity:** HIGH / implementation-report requirement  
**Area:** TemporalActApplicationService / TemporalEngineHealth / result mapping

The implementation introduced distinct `TemporalRuntimeFailureCode` values. The correction patch must confirm that runtime states map to the correct failure code and are not collapsed into a single coarse fallback such as `RECOVERY_NOT_COMPLETED`.

The implementation report v0.1.1 MUST explicitly document the final mapping, at minimum:

```text
DISABLED   -> TEMPORAL_ENGINE_DISABLED
RECOVERING -> RECOVERY_NOT_COMPLETED
STARTING   -> TEMPORAL_ENGINE_NOT_READY
STOPPED    -> TEMPORAL_ENGINE_NOT_READY
FAILED     -> INTERNAL_FAILURE or SINGLE_NODE_GUARD_VIOLATED when causally known
SINGLE_NODE_GUARD_VIOLATED -> SINGLE_NODE_GUARD_VIOLATED
```

If the implementation uses equivalent internal statuses, the failure classes must remain distinguishable.

---

### H-04 — Health counters must either be incremented or explicitly reduced

**Severity:** HIGH / implementation-report requirement  
**Area:** TemporalEngineHealth / observability

`TemporalEngineHealth` declares counters such as fired, misfired, cancelled, failed and skipped. The correction patch must ensure these counters are either:

```text
A. incremented when the corresponding confirmed transition occurs; or
B. removed/reduced from the public health contract if not supported in v1.
```

Preferred outcome for MU-016 is A.

The implementation report v0.1.1 MUST confirm which counters are supported and where they are incremented.

---

### H-05 — BLOB ID storage versus String binding must be resolved or explicitly justified

**Severity:** HIGH / schema-sensitive implementation-report requirement  
**Area:** SQLite adapters / schema binding / Persistence Schema alignment

The schema declares canonical temporal IDs as `BLOB`, consistent with the production persistence schema direction. If adapters bind Java `String` values directly into `BLOB` columns, SQLite may tolerate the value dynamically, but the implementation would not satisfy the intended physical representation.

The correction patch must choose and document one path:

```text
Option A — preferred:
  implement UUID string <-> byte[16] binding in SQLite adapters.

Option B — schema change:
  change temporal ID columns to TEXT and produce a corresponding SDD-SCHEMA patch.
```

Because Option B changes schema doctrine, it must not be done silently inside the implementation patch. If Option B is chosen, the implementation report must flag the required corpus patch before claiming L4.

The implementation report v0.1.1 MUST explicitly confirm how `temporal_act_id` and idempotency references are bound.

---

## 6. Minor / documented items

### M-01 — topologyVersionAtRegistration not persisted

For Signal-only targetless v1 this is acceptable and should remain documented.

If delayed action execution is introduced later, this becomes a schema and engine blocker.

### M-02 — working tree noise must be excluded

The MU-016 commit must exclude unrelated files:

```text
.idea/*
docs/mir/mir-001/*
docs/mir/mir-002/*
.gitignore changes unless intentional and documented
```

MU-016 commit should include only code/docs directly related to Temporal Engine Industrial Hardening.

### M-03 — Flyway baselineOnMigrate posture

If `baselineOnMigrate(true)` is used, it should be restricted to dev/import profile or explicitly justified. Industrial profile should fail closed on unmanaged non-empty DB unless a migration/import procedure is active.

### M-04 — Jackson compatibility shims

If custom classes under `com.fasterxml.jackson.annotation.*` were added as compatibility shims, they should be treated as temporary technical debt and not as a final industrial solution unless dependency alignment is formally resolved.

---

## 7. Required patch scope

The correction patch should be tightly scoped to MU-016. It MUST NOT introduce:

```text
ActionTemporalPayload;
ActionRequest;
SC-B;
NATS / JetStream;
outbox dispatcher;
View Composer;
Effective Access Boundary;
recurrence;
cron/calendar semantics;
SC-D adapter behavior.
```

Required code areas:

```text
TemporalEngineLifecycle
TemporalEngineHealth
SQLiteTemporalEngineLockRepository
TemporalEngineLockPort
TemporalActApplicationService
TemporalRequestIdempotencyPort / repository
TemporalActService or lower-level transactional orchestration
TemporalEngineService
TemporalActObservationService
SQLiteTemporalActRepository
SQLiteScLedgerOutboxRepository if needed for TemporalActFailed ledger
Tests under src/test/java/com/sovereign/connect/core/temporal/*
```

---

## 8. Required tests for correction patch

Minimum required additions:

```text
1. recoveryDoesNotStartPollingWhenMisfireBatchIncomplete
2. incompleteRecoveryBatchDoesNotFireOverdueActs
3. concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct
4. createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct
5. unknownPayloadFailureAppendsTemporalActFailedLedgerEntry
6. unknownPayloadFailedActIsObservableByFindById
7. unknownPayloadFailedActIsObservableInListTerminal
8. heartbeatExtendsLockToNowPlusTtl
9. heartbeatDoesNotGrowExpiryExponentially
10. heartbeatLossStopsRunnerOrMarksFailed
11. secondLifecycleFailsFastWhenLockHeld
12. successfulPollAfterFailureRestoresRunningStatus
```

Optional but recommended:

```text
13. concurrentTryAcquireAllowsOnlyOneWinner
14. cancelIdempotencyIsAtomicWithCancelLedger
15. createReplayReturnsOriginalTemporalActObservation
16. conflictingCreateFingerprintReturnsIdempotencyConflict
```

---

## 9. Plan of action

### Phase A — Patch only critical correctness

Implement fixes for:

```text
B-01 recovery completion gate;
B-02 atomic request idempotency;
B-03 unknown payload FAILED ledger + observation safety;
B-04 heartbeat TTL + lost-lock handling;
H-01 DEGRADED → RUNNING recovery;
H-02 second lifecycle lock fail-fast test / atomic acquire hardening if feasible.
```

No new architecture.

#### Phase A.1 — Mandatory decision before coding B-02

The patch execution context MUST resolve the atomic idempotency strategy before Codex starts implementation. Codex must not choose freely among strategies.

Recommended strategy for this patch:

```text
Use TemporalActApplicationService as the transactional orchestrator for request-level idempotency.

Inside a single TransactionTemplate transaction:
  1. claim or read temporal_request_idempotency;
  2. compare semantic fingerprint;
  3. if new claim, perform the TemporalAct create/cancel effect;
  4. append required ledger entry;
  5. persist the final idempotency result;
  6. commit as one unit.
```

This may require introducing a lower-level transactional participant method or port so that `TemporalActService` does not open an independent transaction that escapes the request-idempotency transaction.

Allowed alternatives:

```text
A. ApplicationService owns the transaction and invokes lower-level write/ledger ports.
B. TemporalActService exposes transaction-participating internal methods.
C. A dedicated orchestration component performs claim + mutation + ledger + result update atomically.
```

Whichever option is selected, the implementation report must document it and prove that no accepted TemporalAct creation can commit without a corresponding idempotency record.

### Phase B — Add targeted tests

Add the required tests listed in §8.

The patch should not rely only on existing green tests.

### Phase C — Run full suite

Expected result:

```text
mvn test
0 failures
0 errors
0 skipped
```

The number of tests will increase from 109.

### Phase D — Produce implementation-report v0.1.1

The report must include:

```text
- patch summary;
- exact tests added;
- final test count;
- explanation of atomic idempotency strategy;
- explanation of recovery completion strategy;
- explanation of lock/heartbeat strategy;
- evidence that unknown payload FAILED transition is ledgered and observable;
- final runtime failure mapping from engine health/status to TemporalRuntimeFailureCode;
- confirmation that health counters are incremented or intentionally reduced from the contract;
- confirmation of BLOB ID binding strategy, or explicit schema-patch requirement if TEXT is chosen;
- confirmation that no ActionTemporalPayload / SC-B / dispatcher was introduced;
- confirmation that unrelated working tree noise was excluded.
```

### Phase E — Re-review

After the patch ZIP is provided, perform a focused review against this final report.

If all blockers are closed and tests pass, MU-016 can move from:

```text
REQUIRES CORRECTIONS
```

to:

```text
Candidate L4
```

Final L4 should be declared only after reviewing the patch and final implementation report.

---

## 10. Branch and commit recommendation

If correcting on top of the current branch:

```text
Branch:
  fix/sc-c-mir-016-temporal-engine-industrial-hardening

Commit:
  fix(sc-c): harden temporal engine industrial guarantees
```

If the branch remains the existing feature branch, use a second commit:

```text
Commit:
  fix(sc-c): close MU-016 temporal engine review blockers
```

---

## 11. Final disposition

```text
MU-016 implementation state:
  REQUIRES CORRECTIONS

Industrial-grade acceptance:
  BLOCKED

L4 status:
  NOT APPROVED

Next step:
  focused correction patch inside MU-016.
```

The implementation is directionally strong and much closer to industrial-grade than the seed. The remaining issues are not conceptual; they are hardening defects at exactly the places where industrial-grade semantics matter: recovery, idempotency, lock ownership, auditability and safe observation.
