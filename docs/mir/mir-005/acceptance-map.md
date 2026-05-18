# Acceptance Map — MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001

```text
Document ID:  ACCEPTANCE-MAP-MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Title:        Acceptance Map — SC-C TemporalActs Seed
Version:      v0.1.0
Status:       Approved for Codex Prompt
Date:         2026-05-18
Corpus:       Sovereign Connect
Type:         Execution Package / Acceptance Map
Plane:        SC-C
MU:           MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Slot:         MU-005
```

---

## 0. Purpose

This document maps MIR acceptance criteria to executable test expectations for `MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001`.

The implementation is accepted only if:

```text
Existing tests: 75 remain passing.
New tests:      at least T-001 through T-022 pass.
Total tests:    >= 95.
Failures:       0.
Errors:         0.
Skipped:        0.
```

---

## 1. Test suite expectation

Recommended test class names:

```text
TemporalActSeedTest
TemporalEngineSeedTest
TemporalActBoundaryTest
```

A single test class is acceptable if all mapped tests are present and readable.

Tests MUST use deterministic one-shot methods. They MUST NOT depend on real `Thread.sleep` timing.

---

## 2. Acceptance criteria map

| AC | Requirement | Required evidence |
|---|---|---|
| AC-005-001 | `TemporalAct` aggregate exists in `core.temporal.model`. | Compile-time type + creation/persistence tests. |
| AC-005-002 | `TemporalActStatus` exact vocabulary exists. | Enum assertion / status CHECK persistence tests. |
| AC-005-003 | `SignalTemporalPayload` exists and supports targetless timers. | T-001, T-022. |
| AC-005-004 | `ActionTemporalPayload` runtime behavior is not implemented. | Boundary assertion / absence check. |
| AC-005-005 | `CreatedByRef` exists and is stored opaquely. | T-001 or create/readback assertion. |
| AC-005-006 | `temporal_acts` table exists with status CHECK constraint. | T-001, invalid status or schema assertion if practical. |
| AC-005-007 | `temporal_acts` indexes exist for due/status queries. | Schema assertion. |
| AC-005-008 | `TemporalActReadPort` and `TemporalActWritePort` exist. | Compile-time + service wiring. |
| AC-005-009 | Write port does not expose arbitrary status mutation. | Reflection/source boundary assertion. |
| AC-005-010 | Public create API assigns `temporalActId` in SC-C. | T-001; no caller-supplied id in service create API. |
| AC-005-011 | Create commits `temporal_acts` row and `TemporalActCreated` ledger entry atomically. | T-003, T-004. |
| AC-005-012 | Cancel terminalizes non-terminal acts and appends `TemporalActCancelled` ledger entry atomically. | T-005. |
| AC-005-013 | Fire uses conditional UPDATE with status guard and `due_at_ms <= now`. | T-010, T-011. |
| AC-005-014 | Fire update count 0 creates no ledger/outbox entry. | T-007, T-011. |
| AC-005-015 | Fire update count 1 creates `TemporalActFired` / `TimerFired` ledger entry. | T-009, T-012. |
| AC-005-016 | Fire update count 1 creates `TimerFired` outbox entry with PENDING status. | T-013. |
| AC-005-017 | `notificationTargetRef` is copied opaquely into firing record/outbox entry. | T-014. |
| AC-005-018 | Fire idempotency prevents double semantic fire. | T-011. |
| AC-005-019 | MISFIRED classification marks overdue non-terminal acts terminal. | T-015. |
| AC-005-020 | MISFIRED acts remain queryable but are not active. | T-016, T-017. |
| AC-005-021 | Virtual-thread runner exists and does not use `@Scheduled` as primary mechanism. | T-021. |
| AC-005-022 | Polling interval is configurable with seed default <= 1 second. | T-018. |
| AC-005-023 | Deterministic one-shot engine methods exist for tests. | T-009 through T-017. |
| AC-005-024 | Tests do not rely on `Thread.sleep` for correctness. | Code review + deterministic test structure. |
| AC-005-025 | SC-C exposes `dueAt` but not `remainingMs`/countdown ticks as canonical state. | Type/field assertion. |
| AC-005-026 | Services do not import SQL/JdbcTemplate/DataSource directly. | T-020. |
| AC-005-027 | Existing 75 tests still pass. | Full `mvn test`. |
| AC-005-028 | All T-001 through T-022 tests pass. | Surefire report. |
| AC-005-029 | MISFIRED classification cannot block startup indefinitely. | T-015 and implementation review; batch policy documented. |

---

## 3. Required tests

### T-001 — createSignalTemporalActPersistsDurably

Expected:

```text
Creating a SignalTemporalPayload TemporalAct persists a temporal_acts row with status PENDING.
A repository/service recreated against the same database can read it back.
No deviceId, endpointId, capabilityId or TopologyTargetRef is required.
```

Maps:

```text
AC-005-001, AC-005-003, AC-005-005, AC-005-006, AC-005-010
```

---

### T-002 — repositoryDuplicateTemporalActIdRejected

Expected:

```text
Repository-level insertion of the same temporalActId twice is rejected by persistence.
This is repository-level only; public service create API must assign ids internally.
```

Maps:

```text
AC-005-006, AC-005-010
```

---

### T-003 — createTemporalActAppendsCreatedLedgerEntry

Expected:

```text
Create appends a TemporalActCreated ledger entry.
Idempotency key is stable for the created act.
```

Maps:

```text
AC-005-011
```

---

### T-004 — createTemporalActAtomicityPreserved

Expected:

```text
Creation does not leave a temporal_acts row without its required TemporalActCreated ledger entry.
If direct fault injection is not available, assert implementation uses one TransactionTemplate boundary and one DataSource.
```

Maps:

```text
AC-005-011
```

---

### T-005 — cancelNonTerminalActSucceeds

Expected:

```text
Cancelling PENDING or ARMED act sets CANCELLED, sets terminalAt, updates updatedAt and appends TemporalActCancelled ledger entry.
```

Maps:

```text
AC-005-012
```

---

### T-006 — fireThenCancelReturnsAlreadyTerminal

Expected:

```text
If fire wins first, subsequent cancel does not create cancellation semantic effect.
Act remains FIRED.
```

Maps:

```text
AC-005-012, AC-005-014, AC-005-018
```

---

### T-007 — cancelThenFireDoesNotCreateTimerFired

Expected:

```text
If cancel wins first, subsequent fire attempt updates zero rows and creates no TimerFired ledger or outbox entry.
```

Maps:

```text
AC-005-014, AC-005-018
```

---

### T-008 — listActiveReturnsOnlyNonTerminalActs

Expected:

```text
listActive returns PENDING and ARMED only.
It does not return FIRED, CANCELLED, EXPIRED, MISFIRED or FAILED.
```

Maps:

```text
AC-005-020
```

---

### T-009 — fireTransitionAdvancesStatusToFired

Expected:

```text
fireDueTemporalActOnce for due non-terminal act transitions to FIRED and sets firedAt/terminalAt.
```

Maps:

```text
AC-005-013, AC-005-015
```

---

### T-010 — fireRequiresDueAtReached

Expected:

```text
An act with dueAt in the future cannot fire.
No ledger or outbox entry is created.
```

Maps:

```text
AC-005-013, AC-005-014
```

---

### T-011 — fireIdempotencyGuardPreventsDoubleFire

Expected:

```text
Calling fire twice for the same act produces exactly one semantic fire, one firing ledger record and one TimerFired outbox record.
```

Maps:

```text
AC-005-013, AC-005-014, AC-005-018
```

---

### T-012 — fireLedgerEntryCreatedAtomically

Expected:

```text
A successful fire creates TemporalActFired / TimerFired ledger entry in the same transaction as FIRED transition.
```

Maps:

```text
AC-005-015
```

---

### T-013 — fireOutboxEntryCreatedAtomically

Expected:

```text
A successful fire creates TimerFired outbox entry with OutboxEntryStatus.PENDING.
No non-PENDING append is attempted.
```

Maps:

```text
AC-005-016
```

---

### T-014 — notificationTargetRefCarriedInFiringRecord

Expected:

```text
notificationTargetRef from TemporalAct is copied opaquely into firing ledger/outbox payload or metadata.
It is not interpreted as user/session/policy.
```

Maps:

```text
AC-005-017
```

---

### T-015 — misfiredClassificationOnStartupForOverdueActs

Expected:

```text
classifyMisfires marks overdue PENDING/ARMED acts as MISFIRED and appends TemporalActMisfired ledger entry.
Classification can process all overdue acts in one batch for seed.
```

Maps:

```text
AC-005-019, AC-005-029
```

---

### T-016 — misfiredActIsNotRefired

Expected:

```text
A MISFIRED act cannot be fired by pollDueOnce or fireDueTemporalActOnce.
No TimerFired ledger/outbox is created for MISFIRED act.
```

Maps:

```text
AC-005-020, AC-005-018
```

---

### T-017 — misfiredActRemainsQueryable

Expected:

```text
A MISFIRED act is queryable through listMisfired/listTerminal but not through listActive.
```

Maps:

```text
AC-005-020
```

---

### T-018 — pollingIntervalIsConfigurable

Expected:

```text
TemporalEngineRunner accepts configurable polling interval.
Default Profile A interval is <= 1 second.
```

Maps:

```text
AC-005-022
```

---

### T-019 — durabilityAfterRepositoryRecreation

Expected:

```text
TemporalActs and terminal states survive repository/service recreation against the same database.
```

Maps:

```text
AC-005-001, AC-005-019
```

---

### T-020 — domainServicesHaveNoSqlImports

Expected:

```text
core.temporal.service classes do not import JdbcTemplate, DataSource, SQL or H2 types.
```

Maps:

```text
AC-005-026
```

---

### T-021 — virtualThreadRunnerDoesNotUseScheduledAnnotation

Expected:

```text
TemporalEngineRunner uses virtual threads or virtual-thread-backed executor.
No primary firing method is annotated with @Scheduled.
```

Maps:

```text
AC-005-021
```

---

### T-022 — targetlessSignalTimerDoesNotRequireTopologyTarget

Expected:

```text
A SignalTemporalPayload timer can be created and fired without deviceId, endpointId, capabilityId, TopologyTargetRef or validateTarget(...).
```

Maps:

```text
AC-005-003, AC-005-004
```

---

## 4. Required full-suite result

Expected final result:

```text
mvn test
Tests run: >= 95
Failures: 0
Errors: 0
Skipped: 0
```

The implementation report must include:

```text
baseline test count
new test count
total test count
names of TemporalActs test classes
any deviation from expected tests
```
