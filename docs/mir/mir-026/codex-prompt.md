# Codex Prompt — MU-026 SC-B Outbox Bridge Seed

```text
Version: v0.2.0-candidate
```

Implement `MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v0.2.0-candidate`.

Read first:
```text
docs/mir/mir-026/context.md           ← all exact code here
docs/mir/mir-026/acceptance-map.md
docs/mir/mir-026/MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001.md
docs/mir/mir-026/code-surface-audit.md
```

---

## Hard rule

```text
H2 only — outbox bridge.
bus.** MUST NOT import core.**
core.** MUST NOT import bus.runtime.**
integration.scledgerdispatch is the ONLY production package that may import both.
SIGNAL MUST NEVER map to ScBusLane.COMMAND.
Do NOT add attempt_count to the SELECT in findDispatchableEntries.
Do NOT parse semanticPayloadJson.
Do NOT mutate OutboxEntryStatus.
Do NOT add new SC-B migrations — if needed STOP and report.
```

---

## Step 1 — ScOutboxDispatchReadPort

Create `src/main/java/com/sovereign/connect/core/scledger/port/ScOutboxDispatchReadPort.java`
using **exact code from context.md §3**.

---

## Step 2 — Extend SQLiteScLedgerOutboxRepository

Modify `src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteScLedgerOutboxRepository.java`.

Add `ScOutboxDispatchReadPort` to the implements list.
Add `findDispatchableEntries(int limit)` using **exact SQL from context.md §4**.

CRITICAL: The SELECT must include 13 columns. Do NOT include `attempt_count` —
it exists in the table but NOT in the OutboxEntry Java record. Copying the INSERT
column list is wrong because INSERT includes `attempt_count`.

**STOP-1:** `mvn -q compile` → BUILD SUCCESS.

---

## Step 3 — Create integration.scledgerdispatch package

Create `src/main/java/com/sovereign/connect/integration/scledgerdispatch/` with
four classes using **exact code from context.md §§5–8**:

```text
DispatchRecordIdFactory.java        — deterministic UUID from sourceRecordId
DeliveryLaneToScBusLaneMapper.java  — COMMAND/EVENT/RESPONSE direct; SIGNAL scoped
OutboxEntryDispatchProjector.java   — OutboxEntry → Optional<DispatchCandidate>
ScLedgerDispatchCandidateReadAdapter.java — implements DispatchCandidateReadPort
```

Key rules:
```text
dispatchRecordId = UUID.nameUUIDFromBytes("sc-b.dispatch:" + outboxEntryId)
                 → deterministic, NEVER random
sourceRecordId = outboxEntryId
partitionKey   = habitatId
logicalTopic   = OutboxEntry.logicalTopic (preserved as-is)
causationId    = ledgerEntryId
SIGNAL + outboundKind=TIMER_FIRED_SIGNAL + logicalTopic="sc-c.timer-fired" → EVENT
All other SIGNAL → Optional.empty() (non-dispatchable)
semanticPayloadJson is NOT accessed in projection
```

---

## Step 4 — Add tests

Create the 5 test classes using **exact method names and wiring from context.md §§10–9**.

For `ScLedgerDispatchBridgeRuntimeTest`: use the **exact @BeforeEach wiring from
context.md §9.2** — two separate datasources (SC-C with PerConnectionPragmaDataSource,
SC-B with plain SQLiteDataSource), manual construction of all 7 RuntimeDispatchService
dependencies.

For `SQLiteScOutboxDispatchReadPortTest`: this test is in `adapter.persistence.sqlite`
so it CAN use `PerConnectionPragmaDataSource`.

For `OutboxEntryDispatchProjectorTest` and `ScLedgerDispatchCandidateReadAdapterTest`:
pure unit tests — no datasource, mock `DispatchStateWritePort` with Mockito.

For `ScBusOutboxBridgeArchitectureTest`: file-walk + `Files.readString` + AssertJ,
same style as `ScBusArchitectureTest`. No ArchUnit.

**STOP-2:** `mvn -q test -Dtest="SQLiteScOutboxDispatchReadPortTest"` → 5 tests, 0 failures.
**STOP-3:** `mvn -q test -Dtest="OutboxEntryDispatchProjectorTest,ScLedgerDispatchCandidateReadAdapterTest"` → 19 tests, 0 failures.
**STOP-4:** `mvn -q test -Dtest="ScLedgerDispatchBridgeRuntimeTest,ScBusOutboxBridgeArchitectureTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest"` → all pass.

---

## Step 5 — Full regression

```bash
mvn -q test
```

Expected: **≥ 354 tests, 0 failures, 0 errors, 0 skipped.**
SC-C baseline: 240 tests still green.

---

## Step 6 — Update implementation report

`docs/mir/mir-026/implementation-report.md` must include:
```text
branch, commit hash, changed files
final test counts (AC-026-001..043 PASS/FAIL map)
SIGNAL decision: TIMER_FIRED_SIGNAL/sc-c.timer-fired → ScBusLane.EVENT
dispatchRecordId strategy: UUID.nameUUIDFromBytes("sc-b.dispatch:" + outboxEntryId)
confirmations: no NATS, no ScdCommand, no OutboxEntryStatus mutation,
               no new SC-B migration, H3 deferred
```

Commit:
```bash
git commit -m "feat(sc-b): bridge sc-c outbox to runtime dispatch"
```

---

## Hard stops

```text
- attempt_count appears in the SELECT → STOP (remove it)
- bus.** needs to import core.** → STOP
- core.** needs to import bus.runtime.** → STOP
- SIGNAL maps to COMMAND anywhere → STOP
- semanticPayloadJson is parsed into a domain type → STOP
- OutboxEntryStatus is mutated → STOP
- New SC-B migration is needed → STOP and report
- ScdCommand production class created → STOP
- dispatchRecordId is generated randomly → STOP (must be deterministic)
- ScLedgerDispatchBridgeRuntimeTest uses Spring @SpringBootTest → do not;
  use the manual wiring from context.md §9.2
```
