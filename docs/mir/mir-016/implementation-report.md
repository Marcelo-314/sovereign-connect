# implementation-report.md - MU-016 Review Blockers Patch

```text
Document:            implementation-report.md
Version:             v0.2.3-patch
MU:                  MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot:    MU-016
Patch package:        docs/mir/mir-016/patch-001-review-blockers
Branch:              feat/sc-c-temporal-engine-industrial-hardening
Baseline commit:     a76d261
Implementation commit: uncommitted workspace
Executor:            Codex
Date:                2026-05-20
Status:              PASS
```

---

## 1. Patch summary

Focused correction patch for MU-016 review blockers. This is not a new MU and does not expand scope beyond the SC-C temporal engine hardening patch.

---

## 2. Blockers closed

| Blocker | Status | Evidence |
|---|---|---|
| B-01 Recovery completion gate | CLOSED | `TemporalEngineLifecycle` loops recovery until complete, fails closed on incomplete/zero-progress batches. |
| B-02 Atomic request idempotency | CLOSED | `TemporalActApplicationService` wraps idempotency read, domain mutation, classification, and idempotency insert in one transaction. |
| B-03 Unknown payload FAILED ledger + observation | CLOSED | Unknown payloads transition to FAILED, append `TEMPORAL_ACT_FAILED`, and observe as `UNKNOWN`. |
| B-04 Heartbeat TTL + lock loss | CLOSED | Heartbeat sets `expires_at_ms = now + ttl`, returns affected rows, and lock loss stops runner/marks FAILED. |

---

## 3. High-priority items closed or documented

| Item | Status | Evidence / rationale |
|---|---|---|
| H-01 DEGRADED -> RUNNING recovery | CLOSED | `recordPollSuccess()` restores RUNNING from DEGRADED. |
| H-02 tryAcquire TOCTOU / fail-fast evidence | CLOSED | Second lifecycle lock acquisition test proves held lock fails fast. |
| H-03 runtime failure mapping | CLOSED | Application failures now map from `TemporalEngineStatus`. |
| H-04 health counters | CLOSED | Fired, skipped, failed, misfired, and cancelled counters are incremented at transition points. |
| H-05 BLOB ID binding | DOCUMENTED | Option C accepted as deferred debt for this patch. |

---

## 4. Atomic idempotency strategy

`TemporalActService` now exposes transaction-participating create/cancel methods that assert an active Spring transaction. `TemporalActApplicationService` owns the application transaction and only writes idempotency after final result classification.

```text
No accepted TemporalAct creation can commit without final idempotency record.
No idempotency insert failure leaves a committed TemporalAct side effect.
Replay/conflict behavior is stable.
```

## 4.1 Cancel idempotency result classification

```text
First-time Cancelled:
  result_kind: ACCEPTED
  result_code: CANCELLED

First-time AlreadyTerminal:
  result_kind: ACCEPTED
  result_code: ALREADY_TERMINAL

First-time NotFound:
  result_kind: ACCEPTED
  result_code: NOT_FOUND

Failed / inconsistent cancel:
  result_kind: FAILED
  result_code: INTERNAL_FAILURE
```

The idempotency row is written only after final result classification is known. Replay reconstructs stable behavior for `NOT_FOUND`, `ALREADY_TERMINAL`, and `FAILED`.

---

## 5. Recovery completion strategy

```text
loops until complete: yes
fails closed on incomplete recovery: yes
uses maxRecoveryBatchesPerStartup: yes
uses zero-progress guard: yes
```

---

## 6. Lock / heartbeat strategy

```text
heartbeat SQL: UPDATE temporal_engine_locks SET heartbeat_at_ms = ?, expires_at_ms = ?
TTL behavior: expires_at_ms is exactly nowMs + ttlMs
affected row handling: affected != 1 is lock loss
lock loss behavior: stop runner, await stop, mark health FAILED
tryAcquire evidence: second lifecycle lock-held test fails acquisition
```

---

## 7. Unknown payload strategy

```text
FAILED transition: markFailed(..., "unknown payload kind", ...)
TemporalActFailed ledger evidence: SemanticKind.TEMPORAL_ACT_FAILED with idempotency key temporal-act-failed:{id}:unknown-payload
recovery finding evidence: UNKNOWN_PAYLOAD_KIND finding recorded during recovery classification
observation behavior for UNKNOWN payload: payloadKind=UNKNOWN, label=null, signalKind=null
no ActionTemporalPayload implicit activation: confirmed
```

---

## 8. Runtime failure mapping

```text
DISABLED   -> TEMPORAL_ENGINE_DISABLED
RECOVERING -> RECOVERY_NOT_COMPLETED
STARTING   -> TEMPORAL_ENGINE_NOT_READY
STOPPED    -> TEMPORAL_ENGINE_NOT_READY
FAILED     -> INTERNAL_FAILURE
SINGLE_NODE_GUARD_VIOLATED -> not a TemporalEngineStatus; lifecycle throws fail-fast and marks FAILED
```

---

## 9. Health counters

```text
firedTotal: after confirmed fire and outbox append
misfiredTotal: lifecycle recovery result aggregation
cancelledTotal: after successful committed cancel result
failedTotal: unknown payload failure and lifecycle recovery result aggregation
skippedTotal: fire lost-race updated == 0
```

---

## 10. BLOB ID binding

```text
Option C - explicit deferred debt accepted for this patch; no schema/code change.
```

H-05 / Option C: `temporal_act_id` binds as String (UUID text) into BLOB columns. SQLite stores as TEXT via dynamic typing. Intended BLOB(16) representation requires a follow-on patch. L4 claimed with this debt noted.

Affected columns:

```text
temporal_acts.temporal_act_id
temporal_request_idempotency.temporal_act_id
recovery_findings.entity_id
```

---

## 11. Tests

Before patch:

```text
Tests run: 109
Failures: 0
Errors: 0
Skipped: 0
```

After patch:

```text
Tests run: 132
Failures: 0
Errors: 0
Skipped: 0
```

Added tests in `TemporalEngineReviewBlockersTest`:

```text
recoveryDoesNotStartPollingWhenMisfireBatchIncomplete
incompleteRecoveryBatchDoesNotFireOverdueActs
heartbeatExtendsLockToNowPlusTtl
heartbeatDoesNotGrowExpiryExponentially
heartbeatReturnsZeroForLostLock
heartbeatLossStopsRunnerOrMarksFailed
secondLifecycleFailsFastWhenLockHeld
expiredLockCanBeAcquiredBySecondLifecycle
successfulPollAfterFailureRestoresRunningStatus
unknownPayloadFailureAppendsTemporalActFailedLedgerEntry
unknownPayloadFailedActIsObservableByFindById
unknownPayloadFailureCreatesRecoveryFinding
unknownPayloadFailedActIsObservableInListTerminal
createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct
concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct
createReplayReturnsOriginalTemporalActObservation
conflictingCreateFingerprintReturnsIdempotencyConflict
cancelIdempotencyIsAtomicWithCancelLedger
cancelAlreadyTerminalPersistsAcceptedAlreadyTerminal
cancelNotFoundPersistsStableIdempotentOutcome
runtimeFailureMappingDisabled
runtimeFailureMappingRecovering
runtimeFailureMappingStartingStoppedAndFailed
```

Verification command:

```text
mvn test
```

---

## 12. Scope preservation

```text
No ActionTemporalPayload.
No ActionRequest.
No SC-B.
No NATS / JetStream.
No outbox dispatcher.
No View Composer.
No Effective Access Boundary.
No unrelated .idea / mir-001 / mir-002 noise.
```

---

## 13. Final disposition

```text
MU-016 patch result:
  PASS

Candidate L4:
  yes
```
