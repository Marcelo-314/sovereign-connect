# Code Surface Audit — MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

```text
Audit ID:            CSA-MU-015-MERGED
Version:             v0.1.1-merged
MU:                  MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
MIR:                 MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v0.1.0-draft
Operational Slot:    MU-015
Repository:          sovereign-connect
Inspected ZIP:       sovereign-connect-014.zip
Inspected branch:    feat/sc-c-concurrency-idempotency-seed
Inspected HEAD:      82611868ee692e68b708571459d03afef2730a74
HEAD summary:        8261186 feat(sc-c): add materialization decision replay seed
Baseline:            post-MU-014 implementation state
SDD references:      SDD-SOV-SC-C-OUTBOX-LEDGER-001 v0.1.1-draft
                     SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.1-draft
Audit inputs:        CSA-MU-015 v0.1.0
                     CSA-MU-OL-SEED v0.1.0 architect audit
Audit date:          2026-05-16
Surface category:    Non-Greenfield — bounded persistence surface
Audit status:        Merged / ready for decision confirmation
```

---

## 0. Executive dictamen

MU-015 is implementable with a narrow, append-only storage scope.

The merged position is:

```text
Implement H2-backed append-only SC-C ledger/outbox storage seed.
Use write-only ports.
Do not implement dispatcher, claim loop, retry loop, delivery observations,
terminal responses, TemporalActs, ActionRequest, command dispatch, SC-B, NATS or JetStream.
```

The architect audit is adopted as the primary operational baseline because it is closer to the immediate Codex execution path and better identifies the existing `mutation_records` and `materialization_decision_replay` patterns.

The previous audit contributes two architectural guardrails:

```text
1. H2BaseTopologyRepository is already broad; using it for ledger/outbox is acceptable only as seed-local convenience.
2. The execution package must prevent read/claim/dispatcher creep and avoid treating H2 seed tables as production SQLite closure.
```

Final merged implementation stance:

```text
For MU-015 seed:
  H2BaseTopologyRepository MAY implement ScLedgerWritePort and ScOutboxWritePort
  to minimize wiring and follow the current createSchema() seed pattern.

Governance constraint:
  This is seed-local consolidation only.
  It MUST NOT be read as the production modularity target.
  Future production persistence may split ledger/outbox into a dedicated adapter/module.
```

---

## 1. Purpose

This audit determines the exact implementation scope for `MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001` before the TemporalActs seed MU opens.

It answers:

```text
- what storage substrate exists today;
- what outbox/ledger pieces are absent;
- what minimum seed surface is required by the Temporal Engine SDD;
- what must remain out of scope;
- which implementation strategy is safest for Codex descent.
```

---

## 2. Audit evidence inspected

### 2.1 Repository state

```text
Branch: feat/sc-c-concurrency-idempotency-seed
HEAD:   82611868ee692e68b708571459d03afef2730a74
Short:  8261186 feat(sc-c): add materialization decision replay seed
```

Included Surefire reports show:

```text
Tests run: 62
Failures: 0
Errors: 0
Skipped: 0
```

The audit environment could not run Maven directly:

```text
mvn: command not found
```

### 2.2 Existing H2 persistence surface

`H2BaseTopologyRepository` currently implements five interfaces:

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository,
               CoreSnapshotReadPort,
               EndpointHealthWritePort,
               TopologyMaterializationStatePort,
               MaterializationDecisionReplayPort
```

Current H2 seed tables:

```text
topology_snapshots
mutation_records
device_states
endpoint_health
materialization_decision_replay
```

Confirmed absent:

```text
sc_c_ledger_entries
sc_c_outbox_entries
temporal_acts
ScLedger*
ScOutbox*
TemporalAct
ActionRequest
NATS
JetStream
```

### 2.3 Existing append / replay patterns

`mutation_records` is the closest existing analog to `sc_c_ledger_entries`:

```text
Pattern: append-only INSERT.
Semantics: immutable evidence, not overwriteable state.
```

`materialization_decision_replay` is the closest existing analog for idempotent replay/upsert mechanics:

```text
Pattern: MERGE INTO ... KEY(habitat_id, fact_id).
Semantics: replay cache / idempotency store.
```

Merged conclusion:

```text
sc_c_ledger_entries MUST use INSERT, not MERGE.
sc_c_outbox_entries append in MU-015 MUST also use INSERT.
Future dispatcher status updates may use UPDATE/MERGE later, but that is out of scope.
```

---

## 3. Canonical scope consumed

The Temporal Engine SDD introduces `MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001` before the TemporalActs seed.

Minimum required support:

```text
TemporalActCreated   -> ledger entry
TemporalActCancelled -> ledger entry, future outbox optional by profile
TemporalActFired     -> ledger entry + outbox entry
TemporalActMisfired  -> ledger entry
TemporalActFailed    -> ledger entry, if failure occurs after eligibility
```

The Outbox/Ledger SDD establishes the semantic split:

```text
sc_c_ledger_entries:
  semantic SC-C evidence / reconstruction record.

sc_c_outbox_entries:
  durable dispatch intent derived from a semantic record.

SC-B / physical broker:
  transport only; not semantic authority.
```

---

## 4. Surface category

```text
Surface category:
  Non-Greenfield — bounded persistence surface.
```

Reason:

```text
The ledger/outbox domain types and tables are new, but the implementation integrates
with existing H2/JdbcTemplate seed persistence patterns and must preserve existing
SC-C topology/materialization tests and repository conventions.
```

A Code Surface Audit is required by MIR governance before authorizing the Executive Package.

---

## 5. Affected code surface

### 5.1 Existing adapter

Primary affected file:

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
```

Potential changes:

```text
- add sc_c_ledger_entries table in createSchema();
- add sc_c_outbox_entries table in createSchema();
- implement ScLedgerWritePort;
- implement ScOutboxWritePort;
- add appendLedgerEntry(...);
- add appendOutboxEntry(...).
```

Merged decision:

```text
Use H2BaseTopologyRepository for MU-015 seed unless implementation reveals friction.
This avoids new constructor/wiring complexity and matches the current H2 seed pattern.
```

Guardrail:

```text
This does not become the production persistence modularity target.
The implementation report MUST state that this is seed-local consolidation.
```

### 5.2 New package surface

Do not place ledger/outbox model types under `core.topology`.

Preferred package:

```text
src/main/java/com/sovereign/connect/core/outbox/model/
src/main/java/com/sovereign/connect/core/outbox/port/
```

Acceptable alternative if the codebase prefers ledger naming:

```text
src/main/java/com/sovereign/connect/core/scledger/model/
src/main/java/com/sovereign/connect/core/scledger/port/
```

Execution package should choose one and use it consistently.

### 5.3 Service/materializer surface

Files that MUST NOT be modified for MU-015:

```text
src/main/java/com/sovereign/connect/core/topology/materialization/DefaultTopologyMaterializationService.java
src/main/java/com/sovereign/connect/core/topology/service/BaseTopologyService.java
```

Reason:

```text
MU-015 is storage substrate only.
It does not integrate ledger/outbox into materialization, topology mutation, TemporalActs or command execution yet.
```

### 5.4 Existing tests that must remain green

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

---

## 6. Required new surface

### 6.1 New tables

```text
sc_c_ledger_entries
sc_c_outbox_entries
```

### 6.2 New ports

```java
public interface ScLedgerWritePort {
    void appendLedgerEntry(LedgerEntry entry);
}

public interface ScOutboxWritePort {
    void appendOutboxEntry(OutboxEntry entry);
}
```

Port naming decision:

```text
Use WritePort, not generic Port.
```

Rationale:

```text
The MU is append-only storage.
Read, claim, dispatch and retry APIs are explicitly out of scope.
```

### 6.3 New minimal model types

Recommended names may vary, but the semantic roles must exist:

```text
LedgerEntry
OutboxEntry
SemanticKind
OutboundKind
DeliveryLane
OutboxEntryStatus
```

Optional but useful if aligning tightly to the Outbox/Ledger SDD:

```text
LedgerRecordClass
```

Minimum `LedgerEntry` shape:

```java
public record LedgerEntry(
    UUID ledgerEntryId,
    String habitatId,
    String recordClass,        // e.g. LEDGER_ONLY, EVENT_OUTBOX, COMMAND_OUTBOX
    String aggregateType,      // e.g. TEMPORAL_ACT
    String aggregateId,        // string form for seed polymorphism
    SemanticKind semanticKind,
    String payloadType,
    String payloadJson,
    String idempotencyKey,
    Instant recordedAt
) {}
```

Minimum `OutboxEntry` shape:

```java
public record OutboxEntry(
    UUID outboxEntryId,
    UUID ledgerEntryId,
    String habitatId,
    OutboundKind outboundKind,
    DeliveryLane deliveryLane,
    String logicalTopic,
    String semanticPayloadJson,
    String notificationTargetRef, // nullable / opaque
    String idempotencyKey,
    OutboxEntryStatus status,
    Instant createdAt,
    Instant updatedAt
) {}
```

The implementation may include additional nullable SDD-aligned fields such as:

```text
claim_expires_at_ms
expires_at_ms
attempt_count
claimed_at_ms
claimed_by
metadata_json
routing_key_json
partition_key
```

But it MUST NOT implement behavior around them.

---

## 7. H2 schema — merged seed shape

### 7.1 sc_c_ledger_entries

Recommended H2-compatible seed table:

```sql
CREATE TABLE IF NOT EXISTS sc_c_ledger_entries (
    ledger_entry_id VARCHAR(36) PRIMARY KEY,
    habitat_id VARCHAR(255) NOT NULL,
    record_class VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    semantic_kind VARCHAR(128) NOT NULL,
    payload_type VARCHAR(255) NOT NULL,
    payload_json CLOB NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    metadata_json CLOB,
    CONSTRAINT uq_sc_c_ledger_idempotency
      UNIQUE (habitat_id, idempotency_key)
);
```

Recommended index:

```sql
CREATE INDEX IF NOT EXISTS ix_ledger_habitat_aggregate
ON sc_c_ledger_entries(habitat_id, aggregate_type, aggregate_id);
```

Rationale for uniqueness:

```text
Use UNIQUE(habitat_id, idempotency_key), not global UNIQUE(idempotency_key),
to preserve habitat scoping and avoid accidental cross-habitat coupling.
```

### 7.2 sc_c_outbox_entries

Recommended H2-compatible seed table:

```sql
CREATE TABLE IF NOT EXISTS sc_c_outbox_entries (
    outbox_entry_id VARCHAR(36) PRIMARY KEY,
    ledger_entry_id VARCHAR(36) NOT NULL,
    habitat_id VARCHAR(255) NOT NULL,
    outbound_kind VARCHAR(128) NOT NULL,
    delivery_lane VARCHAR(64) NOT NULL,
    logical_topic VARCHAR(255) NOT NULL,
    semantic_payload_json CLOB NOT NULL,
    notification_target_ref VARCHAR(512),
    idempotency_key VARCHAR(512) NOT NULL,
    status VARCHAR(64) NOT NULL
      CHECK(status IN ('PENDING','CLAIMED','DISPATCHED',
                       'DISPATCH_FAILED','RETRY_WAIT',
                       'DEAD_LETTERED','SUPPRESSED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claimed_at_ms BIGINT,
    claim_expires_at_ms BIGINT,
    claimed_by VARCHAR(255),
    expires_at_ms BIGINT,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    metadata_json CLOB,
    CONSTRAINT fk_sc_c_outbox_ledger
      FOREIGN KEY (ledger_entry_id) REFERENCES sc_c_ledger_entries(ledger_entry_id),
    CONSTRAINT uq_sc_c_outbox_idempotency
      UNIQUE (habitat_id, idempotency_key)
);
```

Recommended index:

```sql
CREATE INDEX IF NOT EXISTS ix_outbox_status_created
ON sc_c_outbox_entries(habitat_id, status, created_at_ms);
```

Do not require a partial H2 index for the seed. A normal status/created index is sufficient and avoids H2-version friction.

### 7.3 Timestamp convention

Use epoch milliseconds (`BIGINT`) for this seed table to remain closer to production schema conventions and the existing SDD style.

If implementation friction appears, H2 `TIMESTAMP` is acceptable only if the mapping is isolated in the adapter and does not leak into the domain model.

---

## 8. Append semantics

### 8.1 Ledger append

```text
appendLedgerEntry(entry): INSERT into sc_c_ledger_entries.
```

Rules:

```text
- no MERGE;
- duplicate ledger_entry_id is rejected;
- duplicate (habitat_id, idempotency_key) is rejected;
- no update of existing ledger entry;
- no delete path.
```

### 8.2 Outbox append

```text
appendOutboxEntry(entry): INSERT into sc_c_outbox_entries.
```

Rules:

```text
- ledger_entry_id MUST reference an existing ledger entry;
- initial status MUST be PENDING unless an explicit future profile says otherwise;
- duplicate outbox_entry_id is rejected;
- duplicate (habitat_id, idempotency_key) is rejected;
- no status transition methods in MU-015;
- no claim/retry/dead-letter behavior in MU-015.
```

### 8.3 Idempotency keys

Recommended TemporalAct seed key formats:

```text
temporal-act-created:{temporalActId}
temporal-act-cancelled:{temporalActId}
temporal-act-fired:{temporalActId}
temporal-act-misfired:{temporalActId}
temporal-act-failed:{temporalActId}
```

For MU-015 tests, these are plain strings; no `TemporalAct` aggregate is introduced.

---

## 9. Existing invariants to preserve

### INV-015-001 — SC-C domain services stay persistence-agnostic

No JDBC/H2/SQL/DataSource imports may be added to core services or materializers.

### INV-015-002 — H2 seed storage is not production SQLite closure

The implementation report MUST state that MU-015 is an H2 seed storage MU only.

### INV-015-003 — Structural topology save remains structural-only

Ledger/outbox append MUST NOT be a side effect of `BaseTopologyRepository.save(...)`.

### INV-015-004 — topologyVersion does not advance

Appending ledger/outbox entries MUST NOT mutate Base Topology or advance `topologyVersion`.

### INV-015-005 — Outbox is not dispatcher

Outbox status columns may exist, but no claim/read/retry loop may be implemented.

### INV-015-006 — SC-B remains transport-only and absent

No SC-B runtime, NATS, JetStream, broker subjects or physical binding may be introduced.

### INV-015-007 — TemporalActs remain absent

No `TemporalAct`, scheduler, due scan, fire transaction or MISFIRED recovery logic is implemented in MU-015.

### INV-015-008 — notificationTargetRef is opaque

`notificationTargetRef` may be stored and returned in tests exactly as provided, but it MUST NOT be resolved into user/session/identity/authority/policy semantics.

---

## 10. Compatibility constraints

The execution package MUST preserve:

```text
1. No constructor changes to DefaultTopologyMaterializationService.
2. No constructor changes to BaseTopologyService.
3. No materializer integration with ledger/outbox.
4. No modifications to Room/Zone/LOCATED_IN validation.
5. No modifications to MaterializationDecisionReplayPort or materialization_decision_replay semantics.
6. Existing 62-test suite remains green.
7. No dependencies beyond existing Spring JDBC / Jackson / H2 test stack.
8. No SC-B, SC-D, NATS, JetStream, ActionRequest or TemporalAct packages.
```

---

## 11. Integration risks and mitigations

### RISK-015-001 — Repository bloat

Extending `H2BaseTopologyRepository` increases class breadth.

Mitigation:

```text
Allow it only as seed-local consolidation.
Implementation report must record that production modularity may split ledger/outbox later.
No additional read/claim/dispatcher behavior may be added to the class.
```

### RISK-015-002 — Dispatcher creep

Status/claim/expiry columns can tempt implementation of dispatcher logic.

Mitigation:

```text
Store columns only. No methods for claim, dispatch, retry, stale claim repair or TTL handling.
```

### RISK-015-003 — Read port creep

Recovery-visible tests may tempt creation of read ports.

Mitigation:

```text
No ScLedgerReadPort.
No ScOutboxReadPort.
Readback in tests via JdbcTemplate/direct SQL.
```

### RISK-015-004 — Terminal response drift

Outbox/Ledger SDD includes terminal responses, but this MU does not.

Mitigation:

```text
sc_c_terminal_responses remains out of scope.
```

### RISK-015-005 — Production storage claim creep

H2 seed tables may be mistaken for production SQLite migrations.

Mitigation:

```text
Implementation report must explicitly say production SQLite/Flyway migration is not implemented.
```

### RISK-015-006 — Metadata boundary leak

Generic JSON fields may become a carrier for Session/Identity/Authority/Policy/Projection.

Mitigation:

```text
No such fields are required by model constructors or tests.
If metadata validation is implemented, reject forbidden top-level keys.
```

---

## 12. Required tests

### T-015-001 — appendLedgerEntryPersistsDurably

Append a `TEMPORAL_ACT_CREATED`-like ledger entry. Recreate repository against the same file-backed H2 DB. Verify row exists via JdbcTemplate/direct SQL.

### T-015-002 — appendOutboxEntryPersistsDurably

Append a ledger entry, then append an outbox entry with `PENDING` status. Recreate repository and verify row exists.

### T-015-003 — duplicateLedgerIdempotencyKeyRejected

Append a ledger entry with `idempotency_key = temporal-act-fired:act-001`. Attempt a second append with the same `(habitat_id, idempotency_key)`. Assert a persistence conflict / integrity violation.

### T-015-004 — duplicateOutboxIdempotencyKeyRejected

Append a second outbox entry with the same `(habitat_id, idempotency_key)`. Assert conflict / integrity violation.

### T-015-005 — outboxEntryRequiresExistingLedgerEntry

Attempt to append an outbox entry with an unknown `ledger_entry_id`. Assert FK rejection or explicit validation failure.

### T-015-006 — notificationTargetRefCarriedOpaquely

Append an outbox entry with `notificationTargetRef = surface:bedroom-left`. Verify it is stored exactly as provided and not parsed.

### T-015-007 — temporalActFiredLikeRecordCanBeStoredWithoutTemporalEngine

Store a `TEMPORAL_ACT_FIRED`-like ledger entry and outbox entry without introducing a `TemporalAct` aggregate or engine.

### T-015-008 — noDispatcherSurfaceIntroduced

Static/reflection/string scan asserts no production class contains dispatcher/claim/retry/polling constructs such as:

```text
Dispatcher
claimReady
markClaimed
retryLoop
pollingLoop
Nats
JetStream
```

### T-015-009 — domainServicesRemainSqlFree

Static/reflection/string scan asserts no JDBC/H2/SQL imports in:

```text
core/topology/service
core/topology/materialization
```

### T-015-010 — existingRegressionSuitesRemainGreen

All existing 62 tests must remain green.

---

## 13. Decisions to confirm before execution package

### DEC-015-001 — Adapter placement

```text
Decision:
  Use H2BaseTopologyRepository for MU-015 seed implementation.

Constraint:
  This is seed-local consolidation only.
  It MUST NOT be interpreted as production persistence modularity.

Deferred note:
  A future production adapter may split ledger/outbox into a dedicated persistence adapter/module.
```

### DEC-015-002 — Port names

```text
Decision:
  Use ScLedgerWritePort and ScOutboxWritePort.

Rationale:
  WritePort makes append-only scope explicit and avoids implying query/claim behavior.
```

### DEC-015-003 — Package placement

```text
Decision:
  Use com.sovereign.connect.core.outbox.model and com.sovereign.connect.core.outbox.port.

Rationale:
  Ledger/outbox is SC-C runtime infrastructure, not topology model.
```

### DEC-015-004 — Readback strategy

```text
Decision:
  Do not introduce read/claim ports.
  Use test-local JdbcTemplate/direct SQL for readback.

Rationale:
  Recovery-visible readback is test evidence, not dispatcher/query contract.
```

### DEC-015-005 — Append semantics

```text
Decision:
  Append is INSERT-only.
  MERGE is forbidden for ledger/outbox append.

Rationale:
  Ledger/outbox rows are evidence and dispatch intent; silent overwrite would corrupt semantic history.
```

### DEC-015-006 — Idempotency key scope

```text
Decision:
  Use UNIQUE(habitat_id, idempotency_key) for both ledger and outbox seed tables.

Rationale:
  Preserves habitat scoping and supports future TemporalAct fire idempotency.
```

### DEC-015-007 — Delivery observations

```text
Decision:
  sc_c_delivery_observations remains out of scope.

Rationale:
  MU-015 has no delivery attempts or dispatcher.
```

### DEC-015-008 — Terminal responses

```text
Decision:
  sc_c_terminal_responses remains out of scope.

Rationale:
  Terminal response persistence belongs to command/request boundary or Profile B work.
```

### DEC-015-009 — TemporalAct aggregate

```text
Decision:
  No TemporalAct aggregate, repository, query port or service is introduced.

Rationale:
  MU-015 supports future TemporalAct records but does not implement TemporalActs.
```

### DEC-015-010 — Index strategy

```text
Decision:
  Use normal H2 indexes, not partial indexes, for the seed.

Rationale:
  Dispatcher performance is out of scope; avoid H2-version friction.
```

---

## 14. Refactor gate

```text
Refactor decision:
  OPTION A — no significant refactor required.
```

The MU is additive if it:

```text
- adds model/port types;
- adds two H2 tables;
- adds two append methods;
- adds focused storage tests;
- preserves existing service/materializer constructors and behavior.
```

If implementation reveals that extending `H2BaseTopologyRepository` creates excessive friction, the fallback is:

```text
H2ScOutboxLedgerRepository sibling adapter using the same DataSource/JdbcTemplate/ObjectMapper/Clock pattern.
```

Switching to the sibling adapter does not require changing MIR scope, but it should be recorded in the implementation report.

---

## 15. Corpus feedback triggers

No blocking contradiction was found between the corpus and the code surface.

Governance hygiene note:

```text
Some uploaded governance documents were older than the latest generated baseline.
This does not block MU-015 because MIR-015 and Temporal Engine SDD v0.1.1 explicitly introduce the MU.
The next governance update should register MU-015 after MU-014 closure and before TemporalActs seed.
```

---

## 16. Executive package implications

`context.md`, `acceptance-map.md` and `codex-prompt.md` must state:

```text
- implement append-only H2-backed ledger/outbox storage;
- use ScLedgerWritePort and ScOutboxWritePort;
- H2BaseTopologyRepository may implement the write ports as seed-local consolidation;
- append with INSERT, not MERGE;
- use UNIQUE(habitat_id, idempotency_key);
- no dispatcher;
- no read/claim ports;
- no status transitions beyond initial PENDING;
- no delivery observations table;
- no terminal responses table;
- no TemporalAct code;
- no ActionRequest code;
- no SC-B/NATS/JetStream;
- readback tests use JdbcTemplate/direct SQL;
- all existing 62 tests must remain green.
```

---

## 17. Audit verdict

```text
MU-015 surface:       Non-Greenfield — bounded persistence surface.
Implementation risk:  Low.
Scope clarity:        High after merged decisions.
Main risk:            dispatcher/read-port creep or repository-bloat doctrine.
Blockers:             none after DEC-015 confirmation.
Next step:            decision-confirmation.md, then context.md + acceptance-map.md + codex-prompt.md.
```

