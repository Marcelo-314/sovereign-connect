# acceptance-map.md — MU-016 Review Blockers Patch

```text
Package:          execution-package-MU-016-review-blockers-patch
Version:          v0.2.3
MU:               MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot: MU-016
Patch target:     sovereign-connect-016.zip review blockers
```

---

## Acceptance map

| ID | Requirement | Verification |
|---|---|---|
| P-001 | Recovery must complete before polling. | Test `recoveryDoesNotStartPollingWhenMisfireBatchIncomplete`; inspect `TemporalEngineLifecycle.start()`. |
| P-002 | Incomplete recovery batch must not fire overdue acts. | Test `incompleteRecoveryBatchDoesNotFireOverdueActs`. |
| P-003 | Create idempotency atomic with create side effect. | Test `concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct`; implementation transaction review. |
| P-004 | Idempotency insert failure cannot leave untracked TemporalAct. | Test `createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct`. |
| P-005 | Replay same create returns original act. | Test `createReplayReturnsOriginalTemporalActObservation`. |
| P-006 | Conflicting create fingerprint rejects without new act. | Test `conflictingCreateFingerprintReturnsIdempotencyConflict`. |
| P-007 | Cancel idempotency atomic with cancellation ledger. | Test `cancelIdempotencyIsAtomicWithCancelLedger`. |
| P-007a | First-time cancel of already-terminal act persists stable `ACCEPTED / ALREADY_TERMINAL` idempotency result. | Test `cancelAlreadyTerminalPersistsAcceptedAlreadyTerminal`. |
| P-007b | First-time cancel of missing act persists stable idempotent `NOT_FOUND` outcome. | Test `cancelNotFoundIsIdempotentlyReplayable`. |
| P-008 | Unknown payload failure appends TemporalActFailed ledger. | Test `unknownPayloadFailureAppendsTemporalActFailedLedgerEntry`. |
| P-009 | Unknown payload recovery records finding. | Test `unknownPayloadFailureCreatesRecoveryFinding`. |
| P-010 | Unknown payload FAILED act observable by findById. | Test `unknownPayloadFailedActIsObservableByFindById`. |
| P-011 | Unknown payload FAILED act observable in terminal list. | Test `unknownPayloadFailedActIsObservableInListTerminal`. |
| P-012 | Heartbeat sets expiry to now + ttl. | Test `heartbeatExtendsLockToNowPlusTtl`. |
| P-013 | Heartbeat expiry does not grow exponentially. | Test `heartbeatDoesNotGrowExpiryExponentially`. |
| P-014 | Lock loss stops runner or marks failed. | Test `heartbeatLossStopsRunnerOrMarksFailed`. |
| P-015 | Second lifecycle fails fast when lock held. | Test `secondLifecycleFailsFastWhenLockHeld`. |
| P-016 | DEGRADED recovers to RUNNING on successful poll. | Test `successfulPollAfterFailureRestoresRunningStatus`. |
| P-017 | Runtime failure mapping is specific. | Test or implementation-report table mapping engine state to `TemporalRuntimeFailureCode`. |
| P-018 | Health counters are meaningful. | Tests or implementation-report evidence for transition increments; unsupported counters removed/reduced. |
| P-019 | Temporal ID physical binding is explicitly resolved or documented. | Implementation report selects Option A (UUID↔BLOB), Option B (schema TEXT + corpus patch), or Option C (accepted debt for this patch). |
| P-020 | No forbidden scope drift. | Source-level tests: no ActionTemporalPayload, no ActionRequest, no SC-B/NATS/dispatcher/View Composer. |
| P-021 | Full test suite passes. | `mvn test` reports 0 failures / 0 errors / 0 skipped. |
| P-022 | Working tree noise excluded. | Implementation report lists changed files; excludes `.idea`, `mir-001`, `mir-002` unless explicitly justified. |

---

## Minimum required new tests

```text
recoveryDoesNotStartPollingWhenMisfireBatchIncomplete
incompleteRecoveryBatchDoesNotFireOverdueActs
concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct
createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct
createReplayReturnsOriginalTemporalActObservation
conflictingCreateFingerprintReturnsIdempotencyConflict
cancelIdempotencyIsAtomicWithCancelLedger
cancelAlreadyTerminalPersistsAcceptedAlreadyTerminal
cancelNotFoundIsIdempotentlyReplayable
unknownPayloadFailureAppendsTemporalActFailedLedgerEntry
unknownPayloadFailureCreatesRecoveryFinding
unknownPayloadFailedActIsObservableByFindById
unknownPayloadFailedActIsObservableInListTerminal
heartbeatExtendsLockToNowPlusTtl
heartbeatDoesNotGrowExpiryExponentially
heartbeatLossStopsRunnerOrMarksFailed
secondLifecycleFailsFastWhenLockHeld
successfulPollAfterFailureRestoresRunningStatus
```

---

## Candidate L4 condition

MU-016 can be considered Candidate L4 only if:

```text
- P-001 through P-022 plus P-007a and P-007b pass or are explicitly justified in implementation report; P-019 may pass via Option C debt only for this patch;
- all final technical review blockers B-01 through B-04 are closed;
- H-01 through H-05 are either implemented or explicitly documented with accepted rationale;
- full mvn test passes;
- implementation report is complete.
```
