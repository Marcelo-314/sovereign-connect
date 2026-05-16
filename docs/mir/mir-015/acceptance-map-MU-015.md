# acceptance-map.md — MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

```text
Document:         acceptance-map.md
MU:               MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Operational Slot: MU-015
Version:          v0.1.1-draft
Status:           Draft execution evidence map
MIR:              MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v0.1.1-draft
CSA:              code-surface-audit-MU-015-v0.1.1-merged
```

---

## 0. Purpose

This document maps MIR acceptance criteria to implementation evidence for MU-015.

MU-015 validates append-only H2-backed SC-C ledger/outbox storage seed. It does not validate dispatcher behavior, TemporalActs lifecycle, terminal responses, command dispatch, SC-B delivery, NATS/JetStream or production SQLite/Flyway migrations.

---

## 1. Acceptance criteria mapping

| AC | Requirement | Expected evidence |
|---:|---|---|
| AC-001 | `ScLedgerWritePort` exists as a write-only SC-C port. | File under `com.sovereign.connect.core.scledger.port`; method `appendLedgerEntry(LedgerEntry entry)`. |
| AC-002 | `ScOutboxWritePort` exists as a write-only SC-C port. | File under `com.sovereign.connect.core.scledger.port`; method `appendOutboxEntry(OutboxEntry entry)`. |
| AC-003 | Package family is `core.scledger`, not `core.outbox` or `core.topology`. | New model/port types are under `com.sovereign.connect.core.scledger.*`. |
| AC-004 | Minimal ledger model exists. | `LedgerEntry` plus `LedgerRecordClass` and `SemanticKind`, or equivalent accepted by implementation report. |
| AC-005 | Minimal outbox model exists. | `OutboxEntry` plus `OutboundKind`, `DeliveryLane`, `OutboxEntryStatus`, or equivalent accepted by implementation report. |
| AC-006 | `H2BaseTopologyRepository` implements the two write ports only as seed-local consolidation. | Implements clause includes `ScLedgerWritePort` and `ScOutboxWritePort`; implementation report records seed-local limitation. |
| AC-007 | `sc_c_ledger_entries` table is created by H2 schema setup. | `createSchema()` DDL and test query prove table exists. |
| AC-008 | `sc_c_outbox_entries` table is created by H2 schema setup. | `createSchema()` DDL and test query prove table exists. |
| AC-009 | Ledger append uses `INSERT`, not `MERGE`. | Code inspection; no `MERGE INTO sc_c_ledger_entries`. |
| AC-010 | Outbox append uses `INSERT`, not `MERGE`. | Code inspection; no `MERGE INTO sc_c_outbox_entries`. |
| AC-011 | Ledger idempotency uniqueness is habitat-scoped. | DDL has `UNIQUE(habitat_id, idempotency_key)` for ledger. |
| AC-012 | Outbox idempotency uniqueness is habitat-scoped. | DDL has `UNIQUE(habitat_id, idempotency_key)` for outbox. |
| AC-013 | Duplicate ledger idempotency key within same habitat is rejected. | Test: `duplicateLedgerIdempotencyKeyRejected`. |
| AC-014 | Duplicate outbox idempotency key within same habitat is rejected. | Test: `duplicateOutboxIdempotencyKeyRejected`. |
| AC-015 | Same idempotency key is allowed across different habitats. | Test: `sameIdempotencyKeyAllowedAcrossDifferentHabitats`. |
| AC-016 | Outbox entry must reference an existing ledger entry. | FK or explicit adapter validation; tests: positive `outboxEntryRequiresExistingLedgerEntry`, negative `outboxEntryWithoutLedgerReferenceRejected`. |
| AC-017 | H2 referential integrity is enabled or explicit reference validation exists. | `SET REFERENTIAL_INTEGRITY TRUE` or adapter check; implementation report names strategy. |
| AC-018 | Outbox entry habitat must match referenced ledger entry habitat. | Composite FK or explicit adapter validation; test: `outboxEntryHabitatMustMatchLedgerHabitat`. |
| AC-019 | Ledger append is recovery-visible. | Test: `appendLedgerEntryPersistsDurably` recreates repository against same file-backed H2 database and verifies row. |
| AC-020 | Outbox append is recovery-visible. | Test: `appendOutboxEntryPersistsDurably` recreates repository against same file-backed H2 database and verifies row. |
| AC-021 | `notificationTargetRef` is carried opaquely. | Test: `notificationTargetRefCarriedOpaquely`; stored value equals provided value exactly. |
| AC-022 | TemporalAct-like records can be stored without implementing TemporalActs. | Test: `temporalActFiredLikeRecordCanBeStoredWithoutTemporalEngine`; no `TemporalAct` aggregate introduced. |
| AC-023 | No read ports are introduced. | No `ScLedgerReadPort`; no `ScOutboxReadPort`; readback tests use JdbcTemplate/direct SQL. |
| AC-024 | No dispatcher, claim loop, polling loop or retry loop is introduced. | Test/static scan: `noDispatcherSurfaceIntroduced`; code inspection. |
| AC-025 | No delivery observations / terminal responses tables are introduced. | No `sc_c_delivery_observations`; no `sc_c_terminal_responses`; no `sc_c_outbox_attempts`. |
| AC-026 | No Temporal Engine / TemporalAct lifecycle is introduced. | No `TemporalAct`, `TemporalEngine`, `temporal_acts` table, due scan or fire transaction code. |
| AC-027 | No ActionRequest / command dispatch path is introduced. | No `ActionRequest`, command dispatcher, COMMAND execution or adapter call path. |
| AC-028 | No SC-B / NATS / JetStream dependency is introduced. | Dependency scan / source scan shows no NATS, JetStream, SC-B runtime import. |
| AC-029 | Core topology services and materializers remain SQL/JDBC-free. | Static scan: `domainServicesRemainSqlFree`. |
| AC-030 | Structural topology persistence remains structural-only. | Existing `PersistenceBoundaryHardeningTest` passes; no ledger/outbox append side-effect from `save(...)`. |
| AC-031 | Appending ledger/outbox entries does not advance `topologyVersion`. | Code inspection and existing topology tests pass; no BaseTopology mutation path invoked. |
| AC-032 | Existing regression suites remain green. | Final `mvn test` succeeds. |
| AC-033 | Implementation report records H2 seed limitation and no production SQLite/Flyway migration. | `implementation-report.md` includes limitations. |
| AC-034 | Implementation report records seed-local repository consolidation. | `implementation-report.md` states `H2BaseTopologyRepository` consolidation is not production doctrine. |

---

## 2. Required tests

| Test | Covers |
|---|---|
| `appendLedgerEntryPersistsDurably` | AC-007, AC-009, AC-019 |
| `appendOutboxEntryPersistsDurably` | AC-008, AC-010, AC-016, AC-020 |
| `duplicateLedgerIdempotencyKeyRejected` | AC-011, AC-013 |
| `duplicateOutboxIdempotencyKeyRejected` | AC-012, AC-014 |
| `sameIdempotencyKeyAllowedAcrossDifferentHabitats` | AC-011, AC-012, AC-015 |
| `outboxEntryRequiresExistingLedgerEntry` | AC-016 positive case |
| `outboxEntryWithoutLedgerReferenceRejected` | AC-016, AC-017 negative case |
| `outboxEntryHabitatMustMatchLedgerHabitat` | AC-018 |
| `notificationTargetRefCarriedOpaquely` | AC-021 |
| `temporalActFiredLikeRecordCanBeStoredWithoutTemporalEngine` | AC-022, AC-026 |
| `noDispatcherSurfaceIntroduced` | AC-023, AC-024, AC-025, AC-026, AC-027, AC-028 |
| `domainServicesRemainSqlFree` | AC-029 |

---

## 3. Existing regression suites that must remain green

```text
BaseTopologyServiceTest
ConcurrencyIdempotencySeedTest
CoreSnapshotQuerySeedTest
PersistenceBoundaryHardeningTest
PersistenceMemorySeedTest
RoomZoneTopologySeedTest
ScCoreKernelHardeningTest
TopologyMaterializationSeedTest
TopologyVersionHardeningTest
```

Baseline reported by the audit:

```text
62 tests
0 failures
0 errors
0 skipped
```

Final test count should be at least 74 after the 12 MU-015 tests are added.

---

## 4. Failure signals

| FS | Failure signal |
|---:|---|
| FS-001 | `ScLedgerPort` is used instead of `ScLedgerWritePort`. |
| FS-002 | New types are placed under `core.topology` or an inconsistent package family. |
| FS-003 | `core.outbox` is used despite the execution package selecting `core.scledger`. |
| FS-004 | Ledger append uses `MERGE` or any overwrite/upsert behavior. |
| FS-005 | Outbox append uses `MERGE` or any overwrite/upsert behavior in MU-015. |
| FS-006 | Idempotency uniqueness is global instead of `(habitat_id, idempotency_key)`. |
| FS-007 | Missing-ledger outbox append succeeds. |
| FS-008 | H2 FK/reference enforcement is neither configured nor validated explicitly. |
| FS-009 | Outbox row can reference a ledger row from a different habitat. |
| FS-010 | A read port is introduced. |
| FS-011 | Dispatcher, claim loop, polling loop, retry worker or delivery status transition API appears. |
| FS-012 | `sc_c_delivery_observations`, `sc_c_terminal_responses` or `sc_c_outbox_attempts` is created. |
| FS-013 | TemporalAct aggregate, Temporal Engine, `temporal_acts` table or fire transaction is introduced. |
| FS-014 | ActionRequest, command dispatch or adapter command execution is introduced. |
| FS-015 | NATS / JetStream / SC-B runtime is introduced. |
| FS-016 | SQL/JDBC/DataSource leaks into core topology services or materialization services. |
| FS-017 | `BaseTopologyRepository.save(...)` appends ledger/outbox rows as side effect. |
| FS-018 | Ledger/outbox append advances `topologyVersion`. |
| FS-019 | `notificationTargetRef` is parsed as user/session/identity/authority/policy. |
| FS-020 | Implementation report claims production SQLite/Flyway migration is implemented by this MU. |
| FS-021 | Existing regression suites fail. |

---

## 5. Evidence expected in implementation-report.md

```text
MIR:
MIR version:
CSA version:
Branch:
Baseline commit:
Implementation commit:
Files added:
Files modified:
Ports added:
Tables added:
Package family: com.sovereign.connect.core.scledger
Adapter integration: H2BaseTopologyRepository seed-local consolidation
Append pattern: INSERT-only
Idempotency uniqueness: UNIQUE(habitat_id, idempotency_key)
Ledger/outbox habitat coherence strategy:
FK/reference enforcement strategy:
Final mvn test result:
Final test count:
New tests added:
Existing regression suites status:
Out-of-scope confirmations:
  - no read ports;
  - no dispatcher;
  - no TemporalAct lifecycle;
  - no terminal responses;
  - no ActionRequest/command dispatch;
  - no SC-B/NATS/JetStream;
  - no production SQLite/Flyway migration.
Known limitations:
  - H2 seed only;
  - no outbox delivery runtime;
  - no Temporal Engine integration yet;
  - H2BaseTopologyRepository consolidation is seed-local and not production modularity.
```
