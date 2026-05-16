# implementation-report.md - MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

```text
MIR: MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
MIR version: v0.1.1-draft
CSA version: code-surface-audit-MU-015-v0.1.1-merged
Branch: feat/sc-c-outbox-ledger-storage-seed
Baseline commit: 8261186 feat(sc-c): add materialization decision replay seed
Implementation commit: not created by Codex
Package family: com.sovereign.connect.core.scledger
Final mvn test result: 74 tests, 0 failures, 0 errors, 0 skipped
```

## Files Added

```text
src/main/java/com/sovereign/connect/core/scledger/model/LedgerEntry.java
src/main/java/com/sovereign/connect/core/scledger/model/OutboxEntry.java
src/main/java/com/sovereign/connect/core/scledger/model/LedgerRecordClass.java
src/main/java/com/sovereign/connect/core/scledger/model/SemanticKind.java
src/main/java/com/sovereign/connect/core/scledger/model/OutboundKind.java
src/main/java/com/sovereign/connect/core/scledger/model/DeliveryLane.java
src/main/java/com/sovereign/connect/core/scledger/model/OutboxEntryStatus.java
src/main/java/com/sovereign/connect/core/scledger/port/ScLedgerWritePort.java
src/main/java/com/sovereign/connect/core/scledger/port/ScOutboxWritePort.java
src/test/java/com/sovereign/connect/adapter/persistence/OutboxLedgerStorageSeedTest.java
docs/mir/mir-015/implementation-report.md
```

## Files Modified

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
```

## Ports And Tables

Ports added:

```text
ScLedgerWritePort
ScOutboxWritePort
```

Tables added:

```text
sc_c_ledger_entries
sc_c_outbox_entries
```

Indexes added:

```text
ix_ledger_habitat_aggregate
ix_outbox_status_created
```

## Implementation Summary

`H2BaseTopologyRepository` now implements `ScLedgerWritePort` and
`ScOutboxWritePort` as MU-015 seed-local consolidation. This is not a production
persistence modularity decision; a future production adapter may split ledger/outbox
storage into a dedicated module.

Ledger and outbox appends use `INSERT` only. No `MERGE`, overwrite, read port,
claim API, dispatcher API or status transition behavior was introduced.

Idempotency uniqueness is habitat-scoped through `UNIQUE(habitat_id, idempotency_key)`
on both tables.

Ledger/outbox habitat coherence and required ledger reference are enforced through
the composite FK:

```text
FOREIGN KEY (ledger_entry_id, habitat_id)
REFERENCES sc_c_ledger_entries(ledger_entry_id, habitat_id)
```

The schema setup executes `SET REFERENTIAL_INTEGRITY TRUE` after table/index creation.

## New Tests Added

```text
OutboxLedgerStorageSeedTest#appendLedgerEntryPersistsDurably
OutboxLedgerStorageSeedTest#appendOutboxEntryPersistsDurably
OutboxLedgerStorageSeedTest#duplicateLedgerIdempotencyKeyRejected
OutboxLedgerStorageSeedTest#duplicateOutboxIdempotencyKeyRejected
OutboxLedgerStorageSeedTest#sameIdempotencyKeyAllowedAcrossDifferentHabitats
OutboxLedgerStorageSeedTest#outboxEntryRequiresExistingLedgerEntry
OutboxLedgerStorageSeedTest#outboxEntryWithoutLedgerReferenceRejected
OutboxLedgerStorageSeedTest#outboxEntryHabitatMustMatchLedgerHabitat
OutboxLedgerStorageSeedTest#notificationTargetRefCarriedOpaquely
OutboxLedgerStorageSeedTest#temporalActFiredLikeRecordCanBeStoredWithoutTemporalEngine
OutboxLedgerStorageSeedTest#noDispatcherSurfaceIntroduced
OutboxLedgerStorageSeedTest#domainServicesRemainSqlFree
```

## Validation

```text
mvn -q -DskipTests compile: success after Phase 1
mvn -q -DskipTests compile: success after Phase 2
mvn test before new tests: 62 tests, 0 failures, 0 errors, 0 skipped
mvn test final: 74 tests, 0 failures, 0 errors, 0 skipped
```

Existing regression suites remained green.

## Acceptance Map

```text
AC-001 PASS - ScLedgerWritePort exists.
AC-002 PASS - ScOutboxWritePort exists.
AC-003 PASS - Package family is core.scledger.
AC-004 PASS - LedgerEntry and ledger enums exist.
AC-005 PASS - OutboxEntry and outbox enums exist.
AC-006 PASS - H2BaseTopologyRepository implements write ports as seed-local consolidation.
AC-007 PASS - sc_c_ledger_entries is created.
AC-008 PASS - sc_c_outbox_entries is created.
AC-009 PASS - Ledger append uses INSERT.
AC-010 PASS - Outbox append uses INSERT.
AC-011 PASS - Ledger idempotency uniqueness is habitat-scoped.
AC-012 PASS - Outbox idempotency uniqueness is habitat-scoped.
AC-013 PASS - Duplicate ledger idempotency key is rejected.
AC-014 PASS - Duplicate outbox idempotency key is rejected.
AC-015 PASS - Same idempotency key allowed across habitats.
AC-016 PASS - Outbox must reference existing ledger.
AC-017 PASS - FK enforcement enabled.
AC-018 PASS - Outbox habitat must match ledger habitat.
AC-019 PASS - Ledger append is recovery-visible.
AC-020 PASS - Outbox append is recovery-visible.
AC-021 PASS - notificationTargetRef is stored opaquely.
AC-022 PASS - TemporalAct-like records can be stored without TemporalAct engine.
AC-023 PASS - No read ports introduced.
AC-024 PASS - No dispatcher/claim/poll/retry loop introduced.
AC-025 PASS - No delivery observations, terminal responses or outbox attempts table.
AC-026 PASS - No Temporal Engine or temporal_acts table.
AC-027 PASS - No ActionRequest or command dispatch path.
AC-028 PASS - No SC-B/NATS/JetStream dependency.
AC-029 PASS - Core topology services/materializers remain SQL/JDBC-free.
AC-030 PASS - Structural topology persistence remains structural-only.
AC-031 PASS - Ledger/outbox append does not advance topologyVersion.
AC-032 PASS - Existing regression suites remain green.
AC-033 PASS - H2 seed limitation recorded.
AC-034 PASS - Repository consolidation limitation recorded.
```

## Out Of Scope Confirmations

```text
No ScLedgerReadPort.
No ScOutboxReadPort.
No dispatcher.
No claim loop.
No polling loop.
No retry loop.
No delivery observations table.
No terminal responses table.
No TemporalAct lifecycle or temporal_acts table.
No ActionRequest or command dispatch.
No SC-B, NATS or JetStream dependency.
No production SQLite or Flyway migration.
```

## Known Limitations

This is H2 seed storage only. It does not implement the production SQLite/Flyway
schema migration profile.

The outbox table stores append-created dispatch intent only. MU-015 does not claim,
dispatch, retry, suppress, dead-letter or observe delivery.

TemporalAct lifecycle integration is deferred to a future MU. MU-015 only proves
that TemporalAct-like semantic records and timer-fired signal intent can be stored
without introducing a Temporal Engine.
