# codex-prompt.md — MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001

```text
Document: codex-prompt.md
Version:  v0.2.1
MU:       MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
```

---

You are working in the `sovereign-connect` repository.

Execution package path: `docs/mir/mir-016/`.
Operational slot: `MU-016`.

Read `context.md` and `acceptance-map.md` completely before writing a single line of code.
They are binding. Every signature, every invariant, every boundary test in `context.md` is
exact — use them as written.

---

## Absolute non-scope

Do NOT introduce under any circumstances:

```
ActionTemporalPayload / ActionRequest
SC-B / NATS / JetStream / outbox dispatcher / claim loop
View Composer / Effective Access Boundary
Session / Identity / Authority / Policy / Surface rendering
Recurrence / cron / calendar / late notification after downtime
```

If you find yourself about to create any of these to handle an edge case, stop and report
instead. Unknown payload kinds do not require `ActionTemporalPayload` — they require
fail-closed diagnostic behavior (null sentinel + `markFailed` transition).

---

## Phase 0 — Baseline verification (do this first, do not skip)

```
mvn test
```

Expected: 101 tests, 0 failures, 0 errors, 0 skipped.

If baseline fails before any changes, stop and report. Do not proceed.

---

## Phase 1 — Dependencies, migrations, SQLite configuration

**1a. pom.xml** — add SQLite JDBC and Flyway SQLite support (see `context.md §4.1`).
Do not remove the H2 test dependency.

**1b. Flyway migration `V2__sc_c_temporal_engine.sql`** under
`src/main/resources/db/migration/` — use the exact DDL from `context.md §4.3`.

Note on the `habitats` foreign key: if `habitats` does not exist in a prior migration,
add it or remove the FK and document the decision. Do not fail the migration silently.

**1c. `application.yml`** — add `sovereign.temporal.engine.*` properties and
`spring.flyway.*` configuration (see `context.md §3.8`).

**1d. SQLite connection wrapper** — implement per-connection PRAGMAs:
`journal_mode=WAL`, `foreign_keys=ON`, `synchronous=FULL`, `busy_timeout=5000`.
`foreign_keys` is connection-local in SQLite — setting it once is insufficient.

Run `mvn test` after this phase. If tests fail, stop and report.

---

## Phase 2 — SQLite temporal repository

Create `SQLiteTemporalActRepository` in:

```
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/
```

It must implement both `TemporalActWritePort` AND `TemporalActReadPort` — same as
`H2TemporalActRepository` does today.

Critical column differences from H2 (see `context.md §2.6`):
- H2 uses `payload_type` + `created_by_ref_json` (CLOB) — SQLite uses `payload_kind` + `created_by_ref` (TEXT)
- `insertCreated` must extract `label` and `signal_kind` from `SignalTemporalPayload` and store them as columns
- `notification_target_ref` is NOT NULL in the industrial schema — reject null at the adapter level

Add `markFailed(String habitatId, String temporalActId, String reason, Instant now)` to
`TemporalActWritePort` and implement in both repositories.

Replace the throw in `readPayload` with a null sentinel (see `context.md §2.6`):

```java
default -> {
    log.warn("Unknown payload kind '{}' — act will be quarantined", payloadKind);
    yield null;   // caller transitions act to FAILED
}
```

The engine's `pollDueOnce` and `classifyMisfires` must check for null payload and call
`writePort.markFailed(...)` in their own transaction rather than propagating the exception.

Add bounded query methods. Add a `LIMIT` clause to `findDue` and `findNonTerminalDueBefore`,
taking a `maxRows` int parameter:

```java
List<TemporalAct> findDue(String habitatId, Instant now, int maxRows);
List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff, int maxRows);
```

Update `TemporalActReadPort` to add these bounded overloads alongside the existing ones (additive,
no removal). `H2TemporalActRepository` must also implement the new bounded overloads.

Do not import `org.h2` anywhere under `adapter/persistence/sqlite`.
Do not import `JdbcTemplate`, `DataSource`, `java.sql`, or `javax.sql` in domain services.

Run `mvn test`. All 101 existing tests must still pass.

---

## Phase 3 — Observation contract

Create in `core.temporal.observation`:

```
TemporalActObservation     (record — see exact fields in context.md §3.1)
TemporalActObservationPort (interface — see exact signature in context.md §3.2)
```

`listTerminal` and `listMisfired` on the port take an explicit `int maxResults` parameter.

Create `TemporalActObservationService` that implements `TemporalActObservationPort` by
delegating to `TemporalActReadPort` and mapping `TemporalAct` → `TemporalActObservation`:

```java
// Mapping: TemporalAct -> TemporalActObservation
private TemporalActObservation toObservation(TemporalAct act) {
    SignalTemporalPayload payload = (SignalTemporalPayload) act.payload();
    return new TemporalActObservation(
        act.temporalActId(),
        act.habitatId(),
        act.status(),
        act.dueAt(),
        "SIGNAL",
        payload.label(),
        payload.signalKind(),
        act.notificationTargetRef(),
        act.createdByRef().ref(),    // opaque string from CreatedByRef
        act.createdAt(),
        act.updatedAt(),
        act.firedAt(),
        act.terminalAt(),
        act.terminalReason()
    );
}
```

`TemporalActObservation` must NOT expose `remainingMs`, raw `payload_json`, or `TemporalAct`
aggregate directly.

Run `mvn test`.

---

## Phase 4 — Request contract and idempotency

Create in `core.temporal.application`:

```
CreateSignalTemporalActRequest   (record — exact fields in context.md §3.4)
CancelTemporalActRequest         (record — exact fields in context.md §3.4)
CreateSignalTemporalActResult    (sealed — exact variants in context.md §3.5)
CancelTemporalActResult          (sealed — exact variants in context.md §3.5)
TemporalRequestRejection         (record + enum — exact codes in context.md §3.5)
TemporalRuntimeFailure           (record + enum — exact codes in context.md §3.5)
TemporalActApplicationPort       (interface — see context.md §3.3)
```

**Do not collapse `Rejected(...)` and `Failed(...)`.**
Validation failures → `Rejected`. Storage/engine/infra failures → `Failed`.

Implement `TemporalActApplicationService` that implements `TemporalActApplicationPort`:

- Step 1: validate all fields (see rejection table in `context.md §7`).
- Step 2: check `health.isReady()` — return `Failed(RECOVERY_NOT_COMPLETED)` if not ready.
- Step 3: idempotency check against `temporal_request_idempotency`.
- Step 4: if new, execute the create/cancel transaction.
- Step 5: write idempotency record atomically with the act operation.

Create `SQLiteTemporalRequestIdempotencyRepository` in `adapter/persistence/sqlite/`.

Semantic fingerprint construction — see `context.md §5`. Do not include `requestedAt` in the
fingerprint. Use `SHA-256` hash stored as `BLOB`.

`requestedAt` is required in the canonical DTO but must not be part of the conflict key.

Run `mvn test`.

---

## Phase 5 — Spring lifecycle, recovery gate, configuration

Create in `core.temporal.engine`:

```
TemporalEngineStatus      (enum — STOPPED/STARTING/RECOVERING/RUNNING/DEGRADED/FAILED/DISABLED)
TemporalEngineHealth      (class — see context.md §3.7)
TemporalEngineProperties  (@ConfigurationProperties — see context.md §3.8)
TemporalEngineLifecycle   (SmartLifecycle — see context.md §3.6)
```

`TemporalEngineLifecycle.start()` must:

1. Set status RECOVERING.
2. Call `engineService.classifyMisfires(habitatId, now, maxRecoveryBatchSize)`.
3. Set status RUNNING.
4. Call `runner.start()`.

`classifyMisfires` must use the bounded overload added in Phase 2.

`getPhase()` must return `Integer.MAX_VALUE - 100` so the engine starts after all other SC-C
topology beans (which start at lower phases).

`TemporalActApplicationService` must receive `TemporalEngineHealth` via constructor injection
and check `health.isReady()` before processing write requests.

`TemporalEngineRunner.runLoop()` must:
- Track consecutive failure count.
- On failure, use `failureBackoffMs` if `consecutiveFailures >= maxConsecutiveFailures`.
- Reset counter on success.
- Call `health.recordPollSuccess()` / `health.recordPollFailure(ex)` on each cycle.

Add a `@Configuration` class or register `@Beans` in `SovereignConnectApplication` to wire
the full temporal stack: properties → health → repository → idempotency store → services →
lifecycle.

Run `mvn test`. All 101 tests must pass. New Spring context tests are additive.

---

## Phase 6 — Single-node guard

Implement `SQLiteTemporalEngineLockRepository` in `adapter/persistence/sqlite/`.

Using the `temporal_engine_locks` table from the migration, implement:

```java
boolean tryAcquire(String habitatId, String storagePartitionRef, String engineInstanceId,
                   Instant now, long ttlMs);
void heartbeat(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now);
void release(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now);
boolean isHeld(String habitatId, String storagePartitionRef, Instant now);
```

Acquire uses an INSERT-or-REPLACE with an expiry check: if a lock row exists and
`expires_at_ms > now`, the acquire fails.

Wire the guard into `TemporalEngineLifecycle.start()` before `runner.start()`:

```java
boolean acquired = lockRepository.tryAcquire(habitatId, storageRef, instanceId, now, ttlMs);
if (!acquired) {
    health.transitionTo(TemporalEngineStatus.FAILED);
    throw new IllegalStateException("SINGLE_NODE_GUARD_VIOLATED: another engine holds the lock");
}
```

Start a heartbeat task using a lifecycle-owned virtual thread or equivalent executor controlled by `TemporalEngineLifecycle` to refresh `heartbeat_at_ms` every `ttlMs / 3`. Do not use `@Scheduled` for this MU; the existing no-`@Scheduled` discipline must remain unambiguous.

Run `mvn test`.

---

## Phase 7 — Bounded terminal retention, observability tests

`listTerminal(habitatId, maxResults)` and `listMisfired(habitatId, maxResults)` in the SQLite
adapter must use:

```sql
SELECT ... FROM temporal_acts
WHERE habitat_id = ? AND status IN (...)
  AND terminal_at_ms >= ?           -- retention window: now - (terminalRetentionDays * 86400000)
ORDER BY terminal_at_ms DESC
LIMIT ?
```

Pass `maxResults` as the `LIMIT` parameter.

Add `recovery_runs` and `recovery_findings` support to `classifyMisfires` (or a dedicated
`TemporalActRecoveryService`): write a `recovery_runs` row at the start and update it with
the result; write `recovery_findings` rows for each misfired act or quarantined act.

Run `mvn test`.

---

## Phase 8 — Boundary tests and regression hardening

Add a new test class `TemporalEngineIndustrialBoundaryTest` with the exact tests from
`context.md §9`:

- `h2ExcludedFromSqliteAdapterPath` (T-005)
- `domainAndApplicationServicesHaveNoStorageImports` (T-031)
- `temporalActPayloadPermitsOnlySignalTemporalPayload` (T-029)
- `actionTemporalPayloadDoesNotExist` (T-029)
- `temporalActApplicationPortHasNoReadMethods` (T-028)
- `temporalActObservationDoesNotExposeInternals` (T-009)

Add Spring context test verifying `TemporalEngineLifecycle` bean is present and wired correctly
(T-002, T-032).

Add recovery gate test: create an overdue act, start context with `TemporalEngineLifecycle`,
verify act becomes MISFIRED before polling fires it (T-003).

Run full `mvn test`. All tests (original 101 + new industrial tests) must pass with 0 failures.

---

## Final output

After all phases pass, produce `implementation-report.md` with:

```
branch name
baseline commit (a76d261)
files changed per phase
test summary: total / failures
Flyway migrations created
SQLite DataSource / PRAGMA setup summary
Recovery gate behavior (classifyMisfires wiring)
Single-node guard: mechanism chosen, evidence summary
Idempotency: table name, fingerprint fields
Unknown payload: handling strategy
Observability: status states implemented
Known deferrals (if any, with explicit rationale)
Confirmation: T-001 through T-033 pass or explicitly documented deviation
```
