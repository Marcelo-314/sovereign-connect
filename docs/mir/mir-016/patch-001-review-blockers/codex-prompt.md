# codex-prompt.md — MU-016 patch-001-review-blockers

```text
Package:   execution-package-MU-016-review-blockers-patch
Version:   v0.2.3
```

You are working in the `sovereign-connect` repository on branch
`fix/sc-c-mir-016-temporal-engine-industrial-hardening`.

Read `context.md` completely before modifying any file. Every method signature, every SQL
statement, and every wiring decision in `context.md` is binding. Sections 2–5 show exact
current code. Sections 6–13 show exact target code and binding design decisions; §14 lists required test scenarios.

---

## Absolute constraints

Do NOT introduce:

```
ActionTemporalPayload / ActionRequest
SC-B / NATS / JetStream / outbox dispatcher
View Composer / Effective Access Boundary
SC-D adapter, recurrence, cron, calendar semantics
@Scheduled for engine loop or heartbeat
Thread.sleep in runner or heartbeat
```

Do NOT modify:

```
H2TemporalActRepository (seed legacy — preserve exactly)
TemporalActSeedTest     (all 26 tests must remain green)
mir-001/*, mir-002/*, .idea/*
```

---

## Phase 0 — Baseline

```
mvn test
```

Expected: 109 tests, 0 failures. If baseline fails, stop and report.

---

## Phase 1 — H-01: DEGRADED auto-recovery (one line, do this first — zero risk)

In `TemporalEngineHealth.recordPollSuccess()`, add after `lastFailure = null`:

```java
if (status == TemporalEngineStatus.DEGRADED) {
    status = TemporalEngineStatus.RUNNING;
}
```

Add test `successfulPollAfterFailureRestoresRunningStatus` (see `context.md §14 T-7`).

Run `mvn test`. Must stay at 109+ / 0 failures.

---

## Phase 2 — B-04: Heartbeat TTL fix

**2a. TemporalEngineLockPort** — change `heartbeat` return type from `void` to `int` and
add `long ttlMs` parameter (see `context.md §9.1`).

**2b. SQLiteTemporalEngineLockRepository.heartbeat** — replace the expiry formula with
`expires_at_ms = nowMs + ttlMs` (see `context.md §9.2`). Return the `jdbcTemplate.update` count.

**2c. TemporalEngineLifecycle.startHeartbeat()** — pass `properties.singleNodeGuardTtlMs()`
to `heartbeat(...)` and handle `affected != 1` as lock-loss: stop runner, set health FAILED
(see `context.md §9.3`).

Add tests `heartbeatExtendsLockToNowPlusTtl`, `heartbeatDoesNotGrowExpiryExponentially`,
`heartbeatLossStopsRunnerOrMarksFailed`, `secondLifecycleFailsFastWhenLockHeld`
(see `context.md §14 T-3 through T-6`).

Run `mvn test`. Must pass.

---

## Phase 3 — B-03: Unknown payload ledger + observation safety

**3a. TemporalEngineService.failUnknownPayload** — add `ledgerPort.appendLedgerEntry(...)` in
the same transaction as `markFailed`. Use `SemanticKind.TEMPORAL_ACT_FAILED` and idempotency
key `"temporal-act-failed:" + temporalActId + ":unknown-payload"` (see `context.md §8.1`).

**3b. TemporalActObservationService.toObservation** — replace the blind cast with null-safe
pattern matching (see `context.md §8.2`). Unknown payload acts return `payloadKind="UNKNOWN"`,
`label=null`, `signalKind=null`. No NPE or ClassCastException may escape.

Add tests `unknownPayloadFailureAppendsTemporalActFailedLedgerEntry`,
`unknownPayloadFailureCreatesRecoveryFinding`,
`unknownPayloadFailedActIsObservableByFindById`, and `unknownPayloadFailedActIsObservableInListTerminal`
(see `context.md §14 T-8, T-9`).

Run `mvn test`. Must pass.

---

## Phase 4 — B-02: Atomic request idempotency

This is the most complex phase. Read `context.md §6` completely before starting.

**4a. TemporalActService** — add public transaction-participating methods `createSignalTemporalActInExistingTransaction` and
`cancelTemporalActInExistingTransaction` that execute domain logic WITHOUT their own `txTemplate`.
They must be public because service/application are in different Java packages, and must assert an active Spring transaction via `TransactionSynchronizationManager.isActualTransactionActive()` (see
`context.md §6.1`). The existing public `createSignalTemporalAct` and `cancelTemporalAct`
methods MUST be preserved — they delegate to the InTx methods wrapped in `txTemplate`.

**4b. TemporalActApplicationService** — add `TransactionTemplate txTemplate` as 6th constructor
parameter. Rewrite create and cancel to execute inside a single `txTemplate.execute(...)` that
encloses: idempotency read → InTx domain call → final result classification → idempotency insert (see `context.md §6.3–§6.5`). For cancel, do **not** insert the idempotency record before classifying `Cancelled`, `AlreadyTerminal`, `NotFound`, or `Failed`. `NotFound` is a stable idempotent outcome and must be persisted with `result_kind=ACCEPTED`, `result_code=NOT_FOUND`.

Add `toObservationDirect(TemporalAct)` helper to avoid N+1 read after in-transaction create
(see `context.md §6.4`).

Handle concurrent idempotency race (DataIntegrityViolationException) by re-reading the
idempotency row and returning replay or conflict — the act + ledger must roll back (see
`context.md §6.3` race handling note).

**4c. TemporalEngineConfiguration** — inject `transactionTemplate` into `TemporalActApplicationService`.

Add tests `concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct`,
`createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct`,
`createReplayReturnsOriginalTemporalActObservation`,
`conflictingCreateFingerprintReturnsIdempotencyConflict`,
`cancelIdempotencyIsAtomicWithCancelLedger`,
`cancelAlreadyTerminalPersistsAcceptedAlreadyTerminal`,
`cancelNotFoundIsIdempotentlyReplayable`
(see `context.md §14 T-10`).

Run `mvn test`. Verify all 26 TemporalActSeedTest tests still pass.

---

## Phase 5 — B-01: Recovery completion loop

**5a. TemporalEngineProperties** — add `int maxRecoveryBatchesPerStartup` field (see
`context.md §7`).

**5b. application.yml** — add `max-recovery-batches-per-startup: 50`.

**5c. TemporalEngineLifecycle.start()** — replace single `classifyMisfires` call with the
loop that continues until `result.complete() == true`, fails closed on zero-progress or
max-batches exceeded (see `context.md §7`).

Add tests `recoveryDoesNotStartPollingWhenMisfireBatchIncomplete`,
`incompleteRecoveryBatchDoesNotFireOverdueActs`
(see `context.md §14 T-1, T-2`).

Run `mvn test`. Must pass.

---

## Phase 6 — H-03: Precise failure mapping

Replace `notReady()` in `TemporalActApplicationService` with `toRuntimeFailure(health.status())`
using the switch statement from `context.md §11`. `TemporalEngineHealth.status()` is already
public.

No new tests required; the runtime failure mapping tests in Phase 4 implicitly exercise this.
Optionally add `runtimeFailureMappingUsesSpecificCodes` test.

---

## Phase 7 — H-04: Health counter increments

Pass `TemporalEngineHealth` to `TemporalEngineService` via a nullable 9th constructor parameter
(see `context.md §12`). The existing 7- and 8-arg constructors pass `null` for health — backward
compatible. Update `TemporalEngineConfiguration.temporalEngineService(...)` to pass the health bean.

Add `health.incrementFired()` after confirmed fire, `health.incrementSkipped()` on lost race
(`updated == 0` in polling), `health.incrementFailed(1)` after `failUnknownPayload`, and `health.incrementCancelled()` after a successful committed cancel result in `TemporalActApplicationService`.

Optionally add `healthCountersIncrementOnTransitions` test.

---

## Phase 8 — H-05: Document BLOB ID debt

Do not change schema or adapter code. Select Option C — explicit debt for this patch — and add this to the implementation report:

```
H-05 / Option C: temporal_act_id binds as String (UUID text) into BLOB columns.
SQLite stores as TEXT via dynamic typing. Intended BLOB(16) representation
requires a follow-on patch. L4 claimed with this debt noted.
```

---

## Phase 9 — Full suite verification

```
mvn test
```

Expected: all original 109 tests pass + new tests. Total ≥ 125. 0 failures. 0 errors.

---

## Final output

Produce `docs/mir/mir-016/implementation-report.md` using `implementation-report-template.md` as the required structure.
Include exact test count, list of new tests, strategy decisions for B-01/B-02/B-03/B-04,
H-05 debt note, and confirmation no forbidden scope was introduced.
