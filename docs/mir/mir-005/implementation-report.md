# Implementation Report - MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001

```text
Document ID:  IMPLEMENTATION-REPORT-MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Title:        Implementation Report - SC-C TemporalActs Seed
Version:      v0.1.0
Status:       Implemented
Date:         2026-05-18
Corpus:       Sovereign Connect
Type:         Execution Package / Implementation Report
Plane:        SC-C
MU:           MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Slot:         MU-005
Profile:      A - Signal-first only
```

## 0. Status

```text
Implementation status: Complete
Execution package status: PASS
```

## 1. Branch and commits

```text
Branch:
  feat/sc-c-temporal-acts-seed

Baseline commit:
  post-MU-015 hardening baseline, 75 tests passing

Implementation commit:
  not created by Codex

Recommended commit message:
  feat(sc-c): add signal-first temporal acts seed
```

## 2. Files changed

### Production files added

```text
src/main/java/com/sovereign/connect/core/temporal/model/TemporalAct.java
src/main/java/com/sovereign/connect/core/temporal/model/TemporalActStatus.java
src/main/java/com/sovereign/connect/core/temporal/model/TemporalActPayload.java
src/main/java/com/sovereign/connect/core/temporal/model/SignalTemporalPayload.java
src/main/java/com/sovereign/connect/core/temporal/model/CreatedByRef.java
src/main/java/com/sovereign/connect/core/temporal/port/TemporalActWritePort.java
src/main/java/com/sovereign/connect/core/temporal/port/TemporalActReadPort.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalActService.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineService.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineRunner.java
src/main/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepository.java
```

### Production files modified

```text
None
```

### Test files added

```text
src/test/java/com/sovereign/connect/core/temporal/TemporalActSeedTest.java
```

### Test files modified

```text
src/test/java/com/sovereign/connect/adapter/persistence/OutboxLedgerStorageSeedTest.java
```

Reason: patched obsolete MU-015 negative assertions so MU-005 can legitimately add TemporalAct types, temporal polling and the temporal_acts table while still forbidding outbox dispatcher/broker surfaces.

### Documentation files modified

```text
docs/mir/mir-005/implementation-report.md
```

## 3. Implementation summary

Implemented Profile A Signal-first TemporalActs:

```text
TemporalAct aggregate
TemporalActStatus exact vocabulary
SignalTemporalPayload only; no ActionTemporalPayload
CreatedByRef opaque value
TemporalActReadPort / TemporalActWritePort
H2TemporalActRepository with temporal_acts table and indexes
TemporalActService create/cancel with ledger entries
TemporalEngineService poll/fire/misfire with transaction boundary
TemporalEngineRunner virtual-thread polling loop
Fire idempotency through conditional UPDATE row count
TimerFired ledger + PENDING outbox append
MISFIRED per-act classification with per-act ledger entry
```

The transaction boundary is coordinated with `TransactionTemplate` over the same H2 `DataSource` used by temporal_acts, ledger and outbox storage. Core temporal services receive `TransactionTemplate`; they do not import SQL, JDBC, H2, JdbcTemplate or DataSource.

## 4. Test result

```text
Command:
  mvn test

Actual:
  Tests run: 97
  Failures: 0
  Errors: 0
  Skipped: 0
  Build: success
```

Baseline:

```text
75 tests, 0 failures, 0 errors, 0 skipped
```

New TemporalActs tests:

```text
TemporalActSeedTest: 22 tests, 0 failures, 0 errors, 0 skipped
```

## 5. Acceptance map result

| Test | Result | Notes |
|---|---|---|
| T-001 createSignalTemporalActPersistsDurably | PASS | File-backed H2 recovery verified. |
| T-002 repositoryDuplicateTemporalActIdRejected | PASS | PK rejection verified. |
| T-003 createTemporalActAppendsCreatedLedgerEntry | PASS | TemporalActCreated ledger entry verified. |
| T-004 createTemporalActAtomicityPreserved | PASS | temporal_acts row + ledger entry verified. |
| T-005 cancelNonTerminalActSucceeds | PASS | CANCELLED + ledger entry verified. |
| T-006 fireThenCancelReturnsAlreadyTerminal | PASS | No cancellation ledger after FIRED. |
| T-007 cancelThenFireDoesNotCreateTimerFired | PASS | No TimerFired ledger/outbox after cancel. |
| T-008 listActiveReturnsOnlyNonTerminalActs | PASS | PENDING only in active list after terminal transitions. |
| T-009 fireTransitionAdvancesStatusToFired | PASS | FIRED, firedAt and terminalAt verified. |
| T-010 fireRequiresDueAtReached | PASS | Future dueAt does not fire. |
| T-011 fireIdempotencyGuardPreventsDoubleFire | PASS | Exactly one fire ledger/outbox. |
| T-012 fireLedgerEntryCreatedAtomically | PASS | TimerFired ledger verified. |
| T-013 fireOutboxEntryCreatedAtomically | PASS | PENDING outbox verified. |
| T-014 notificationTargetRefCarriedInFiringRecord | PASS | Opaque value copied to outbox. |
| T-015 misfiredClassificationOnStartupForOverdueActs | PASS | Per-act MISFIRED ledger entries verified. |
| T-016 misfiredActIsNotRefired | PASS | MISFIRED act creates no TimerFired records. |
| T-017 misfiredActRemainsQueryable | PASS | listMisfired/listTerminal verified. |
| T-018 pollingIntervalIsConfigurable | PASS | Custom interval and default <= 1000 ms verified. |
| T-019 durabilityAfterRepositoryRecreation | PASS | FIRED state + ledger + outbox survive recreation. |
| T-020 domainServicesHaveNoSqlImports | PASS | core.temporal.service scan passed. |
| T-021 virtualThreadRunnerDoesNotUseScheduledAnnotation | PASS | No @Scheduled annotation. |
| T-022 targetlessSignalTimerDoesNotRequireTopologyTarget | PASS | Signal payload has no target fields; sealed permits Signal only. |

## 6. Boundary checks

| Boundary | Result | Notes |
|---|---|---|
| Profile A only | PASS | SignalTemporalPayload only. |
| No ActionTemporalPayload runtime behavior | PASS | Type not implemented. |
| Targetless SignalTemporalPayload supported | PASS | No topology target required. |
| No SC-B / NATS / JetStream | PASS | Production source scan clean. |
| No outbox dispatcher / claim loop / retry loop | PASS | MU-015 outbox scan retained for dispatcher/broker surfaces. |
| Virtual threads used; no primary @Scheduled | PASS | TemporalEngineRunner uses `Thread.ofVirtual()`. |
| Domain services do not import persistence classes | PASS | No SQL/JDBC/DataSource imports in core.temporal.service. |
| MU-015 PENDING-only guard preserved | PASS | Existing OutboxLedgerStorageSeedTest remains green. |
| Existing 75 tests still pass | PASS | Full suite green. |

## 7. Deviations

```text
TemporalActService and TemporalEngineService receive TransactionTemplate directly
instead of DataSource. This preserves the acceptance-map requirement that
core.temporal.service classes remain SQL/JDBC/DataSource-free while still using
an explicit shared transaction boundary.

OutboxLedgerStorageSeedTest was patched to remove obsolete pre-MU-005 assertions
that forbade TemporalAct file names, temporal_acts and temporal polling strings.
The test still forbids outbox dispatcher, read-port, delivery observation,
terminal response, broker and retry/claim surfaces.
```

## 8. Final disposition

```text
PASS
```
