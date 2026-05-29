# Codex Prompt — MU-025 SC-B Dispatch State Persistence

Implement `MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v0.2.0-candidate` in the `sovereign-connect` Maven module.

Read in this order before writing code:

```text
docs/mir/mir-025/MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001.md
docs/mir/mir-025/context.md
docs/mir/mir-025/acceptance-map.md
docs/mir/mir-025/code-surface-audit.md
```

This is H1 of SC-B Runtime Dispatch Hardening: **dispatch state persistence only**.

---

## Branch

```text
feat/sc-b-mir-025-dispatch-state-persistence
```

Commit:

```text
feat(sc-b): persist runtime dispatch state
```

Evidence commit, if separate:

```text
docs(sc-b): record dispatch state persistence implementation
```

---

## Critical constraints

```text
1. Implement H1 only.
2. Do not implement the outbox bridge.
3. Do not create ScOutboxDispatchReadPort.
4. Do not create integration.scledgerdispatch.
5. Do not read or mutate OutboxEntry, LedgerEntry or OutboxEntryStatus.
6. Do not implement DispatchObservationPort persistence.
7. Do not add NATS, JetStream or any broker dependency.
8. bus.** must not import core.**.
9. core.** must not import bus.runtime.**.
10. Keep InMemoryDispatchStateRepository usable.
```

---

## Step 1 — Create SC-B runtime persistence package

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
```

It must implement:

```text
com.sovereign.connect.bus.runtime.port.DispatchStateWritePort
```

Use `JdbcTemplate` over a constructor-provided `DataSource`, consistent with existing SQLite/JDBC repositories.

Do not use JPA.

STOP-1:

```bash
mvn -q compile
```

---

## Step 2 — Add SC-B migration

Default migration strategy for this MU:

```text
src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql
```

Do not use `V5__...` for SC-B. Reserve `V5` through `V99` for SC-C.

The migration must create only `sc_b_*` tables.

Recommended tables:

```text
sc_b_dispatch_records
sc_b_dispatch_attempts
```

`source_record_id` may be included as nullable opaque future H2 reference, but H1 must not populate it from SC-C outbox.

STOP-2:

```bash
mvn -q test -Dtest=JdbcDispatchStateRepositoryTest
```

---

## Step 3 — Preserve claim semantics exactly

`claim(dispatchRecordId)` must:

```text
- reject null dispatchRecordId;
- create the first CLAIMED attempt when no record exists;
- treat missing row as implicit PENDING;
- allow claim from explicit PENDING;
- allow claim from RETRY_SCHEDULED;
- reject CLAIMED, DISPATCHING, DISPATCHED, DELIVERY_FAILED, EXHAUSTED and CANCELLED_BY_SUPERSEDE;
- set attemptNumber = 1 on first claim;
- increment attemptNumber when claiming after RETRY_SCHEDULED.
```

Do not leave this behavior as an implementation choice.

---

## Step 4 — Preserve transition semantics

Allowed transitions only:

```text
PENDING -> CLAIMED
CLAIMED -> DISPATCHING
DISPATCHING -> DISPATCHED
DISPATCHING -> DELIVERY_FAILED
DELIVERY_FAILED -> RETRY_SCHEDULED
RETRY_SCHEDULED -> CLAIMED
DELIVERY_FAILED -> EXHAUSTED
PENDING -> CANCELLED_BY_SUPERSEDE, with evidence
CLAIMED -> CANCELLED_BY_SUPERSEDE, with evidence
RETRY_SCHEDULED -> CANCELLED_BY_SUPERSEDE, with evidence
```

Forbidden transitions must throw `IllegalStateException` or an equivalent deterministic exception.

`transition(..., CANCELLED_BY_SUPERSEDE)` without evidence must throw `IllegalArgumentException`.

---

## Step 5 — Add persistence tests

Create:

```text
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
```

Cover at least:

```text
- first claim is persisted;
- currentAttempt survives repository recreation;
- CLAIMED -> DISPATCHING -> DISPATCHED survives restart;
- DISPATCHING -> DELIVERY_FAILED survives restart;
- DELIVERY_FAILED -> RETRY_SCHEDULED -> CLAIMED increments attemptNumber;
- retry attemptNumber continuity survives repository recreation;
- EXHAUSTED is persistent and not claimable;
- CANCELLED_BY_SUPERSEDE requires evidenceRef;
- CANCELLED_BY_SUPERSEDE persists evidenceRef or equivalent diagnostic field;
- invalid transitions are rejected;
- unknown currentAttempt returns Optional.empty().
```

Prefer a real in-memory datasource configured with the migration applied, consistent with existing repository tests. Do not mock `JdbcTemplate` for persistence semantics.

---

## Step 6 — Add architecture/hardening tests

Create or extend:

```text
src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
```

Use file-walk + `Files.readString` + AssertJ. Do not add ArchUnit.

Required assertions:

```text
- bus runtime persistence does not import core.*;
- core does not import bus.runtime.*;
- bus does not import broker APIs;
- pom.xml and eib/pom.xml do not introduce physical bus binding dependencies;
- SC-B migrations do not use V1–V9 naming;
- SC-B migration file creates only sc_b_* tables;
- H1 did not introduce ScOutboxDispatchReadPort;
- H1 did not introduce integration.scledgerdispatch.
```

STOP-3:

```bash
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest,ScBusHardeningArchitectureTest"
```

---

## Step 7 — Full regression

Run:

```bash
mvn -q test
```

Expected:

```text
>= 306 tests, 0 failures, 0 errors, 0 skipped
SC-C/non-bus baseline remains green
Bus delta increases by at least 12 tests
```

If EIB is run, it should remain:

```text
56 tests, 0 failures, 0 errors, 0 skipped
```

STOP-4: If any existing SC-C or bus tests fail, stop and report. Do not silence failures by weakening tests.

---

## Step 8 — Update implementation report

Update:

```text
docs/mir/mir-025/implementation-report.md
```

Record:

```text
- branch;
- implementation commit;
- evidence commit, if separate;
- changed files;
- migration file and migration strategy;
- test commands and summaries;
- AC-025-001..030 PASS/FAIL map;
- architecture test results;
- confirmation that no outbox bridge was introduced;
- confirmation that ScOutboxDispatchReadPort remains absent;
- confirmation that integration.scledgerdispatch remains absent;
- retained debts H2/H3.
```

---

## Hard stops

Report instead of improvising if:

```text
- You need to import core.* into bus.**.
- You need to mutate OutboxEntryStatus.
- You need ScOutboxDispatchReadPort for H1.
- You need integration.scledgerdispatch for H1.
- You need a broker dependency.
- You need to change RuntimeDispatchService semantics beyond using a persistent DispatchStateWritePort.
- You need to use V5–V99 for an SC-B migration.
```
