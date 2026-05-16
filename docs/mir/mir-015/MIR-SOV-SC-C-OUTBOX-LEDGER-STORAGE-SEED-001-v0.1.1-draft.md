# MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

## SC-C Outbox / Ledger Storage Seed

```text
Document ID: MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Title:       SC-C Outbox / Ledger Storage Seed
Version:     v0.1.1-draft
Status:      Draft / CSA Integrated / Pending Decision Confirmation
Date:        2026-05-16
Corpus:      Sovereign Connect
Type:        MIR
Plane:       SC-C
Materialization Unit: MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Suggested operational slot: MU-015
Acceptance level target: L4
Scope:       H2-backed append-only SC-C ledger/outbox storage seed, write ports and regression tests.
```

---

## Changelog v0.1.1-draft

Patch release after Code Surface Audit merge.

This version:

1. Integrates `code-surface-audit-MU-015-v0.1.1-merged.md` as the governing implementation-readiness input.
2. Confirms the implementation surface as `Non-Greenfield — bounded persistence surface`.
3. Narrows MU-015 to an append-only storage seed: two H2 tables, two write ports, minimal records/enums and L4 persistence tests.
4. Accepts `H2BaseTopologyRepository` as the seed-local adapter integration point, while explicitly forbidding reading that as production modularity doctrine.
5. Renames the ledger port target to `ScLedgerWritePort` to prevent accidental read/query/claim expansion.
6. Requires `ScOutboxWritePort` for outbox append only.
7. Fixes seed idempotency uniqueness to `UNIQUE(habitat_id, idempotency_key)`, not global `UNIQUE(idempotency_key)`.
8. Removes partial-index requirements from the MIR; the seed uses normal H2-compatible indexes.
9. Prohibits ledger/outbox read ports; test readback may use `JdbcTemplate` / direct SQL only.
10. Keeps dispatcher, claim loop, status transition management, delivery observations, terminal responses, TemporalActs, ActionRequest, command dispatch, SC-B and NATS outside scope.

---

## Changelog v0.1.0-draft

Initial MIR draft.

This version:

```text
- Opens MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001.
- Assigns suggested operational slot MU-015.
- Defines H2-backed append-only ledger/outbox storage seed scope.
- Requires Code Surface Audit before execution package authorization.
- Keeps dispatcher, SC-B, NATS, Temporal Engine, ActionRequest, command dispatch and terminal responses out of scope.
- Sets acceptance level target to L4.
```

---

## 0. Purpose

This MIR opens the implementation descent for `MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001`.

The purpose of this MU is to materialize the minimum SC-C-owned append-only storage substrate required before the `TemporalActs` seed can implement Profile A firing safely.

Canonical thesis:

```text
Temporal Engine MUST NOT invent an engine-local firing log.
TemporalAct lifecycle records MUST append through SC-C ledger/outbox storage ports.
SC-C owns semantic records.
SC-B, NATS, JetStream and any physical broker remain outside this MU.
```

This MU is deliberately small. It does not implement an outbox dispatcher. It does not implement claim semantics. It does not implement the Temporal Engine. It only creates the durable append substrate and write ports that later TemporalActs code can call atomically with lifecycle state transitions.

---

## 1. Materialization Unit

```text
MU ID:     MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Title:     SC-C Outbox / Ledger Storage Seed
Type:      Kernel / Persistence / Outbox-Ledger Seed
Plane:     SC-C
Status:    MIR Drafted / CSA Integrated
Target:    L4
```

### 1.1 Primary invariant validated

```text
SC-C can append semantic ledger entries and dispatchable outbox entries durably behind SC-C write ports without implementing dispatch, broker binding, TemporalAct lifecycle or command execution.
```

### 1.2 Failure value

This MU detects whether the SC-C seed codebase can accept a ledger/outbox append substrate without:

```text
- contaminating domain/materialization services with JDBC or SQL;
- turning outbox into SC-B;
- implementing dispatcher behavior prematurely;
- duplicating an engine-local firing log in TemporalActs;
- overloading the outbox seed with terminal response / request-state concerns;
- reintroducing Session / Identity / Authority / Policy / Projection metadata into SC-C persistence;
- expanding into ActionRequest, Profile B TemporalActs or command dispatch.
```

---

## 2. Artifact Bundle

### 2.1 Normative inputs

```text
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.7-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.13-draft
SYNC-SOV-SC-MU-BACKLOG-001 v0.1.14-draft

SDD-SOV-SC-C-OUTBOX-LEDGER-001 v0.1.1-draft
SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.1-draft
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001 v0.1.1-draft
SDD-SOV-SC-C-RECOVERY-001 v0.1.1-draft

ADR-SOV-SC-C-STORAGE-TECH-001 v0.1.2-draft
NT-SOV-SC-C-LOCAL-FIRST-STORAGE-PROFILE-001 v0.1.0-draft
PDR-SOV-SC-MU-MIR-GOVERNANCE-001 v0.1.5-draft
```

### 2.2 Implementation-readiness input

This MIR consumes the merged Code Surface Audit:

```text
docs/mir/mir-015/code-surface-audit.md
Source version: code-surface-audit-MU-015-v0.1.1-merged.md
```

The audit resolves the previous provisional ambiguity around adapter placement and scope size.

### 2.3 Required filesystem package

```text
docs/mir/mir-015/
  MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001.md
  code-surface-audit.md
  decision-confirmation.md
  context.md
  acceptance-map.md
  codex-prompt.md
  implementation-report.md
```

The MIR MUST NOT inline `context.md`, `acceptance-map.md` or `codex-prompt.md`.

---

## 3. Readiness Assessment

### 3.1 Readiness status

```text
Readiness: MIR may proceed to decision confirmation.
Implementation: NOT authorized until DEC-015-001 through DEC-015-011 are confirmed and the execution package is authored.
Surface: Non-Greenfield — bounded persistence surface.
```

### 3.2 Why this MU is now needed

`SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.1-draft` introduces this MU before the TemporalActs seed because Profile A firing requires ledger/outbox append storage and must not create an engine-local event log.

The minimum storage seed is:

```text
H2 table: sc_c_ledger_entries
H2 table: sc_c_outbox_entries
Port: ScLedgerWritePort
  appendLedgerEntry(...)
Port: ScOutboxWritePort
  appendOutboxEntry(...)
Adapter:
  H2BaseTopologyRepository as seed-local convenience, unless implementation discovers concrete blockers.
```

### 3.3 Code surface classification

```text
Surface category: Non-Greenfield — bounded persistence surface.
```

Rationale:

```text
The MU adds new ports and tables but must integrate with the existing H2/JdbcTemplate persistence seed, schema bootstrap, repository constructor pattern and test harness.
```

The merged audit confirms that the change is additive and should not require existing constructor call-site changes.

---

## 4. Decision Confirmation Required

The following decisions MUST be confirmed before `context.md`, `acceptance-map.md` and `codex-prompt.md` are authorized.

### DEC-015-001 — Seed-local adapter consolidation

```text
H2BaseTopologyRepository MAY implement ScLedgerWritePort and ScOutboxWritePort for MU-015.
```

This is accepted only as a seed-local convenience.

It MUST NOT be interpreted as the production modularity target.

A future production persistence implementation MAY split ledger/outbox into a dedicated adapter or module.

### DEC-015-002 — Ledger write port name

```text
Use ScLedgerWritePort.
Do not use ScLedgerPort for this MU.
```

Rationale:

```text
The seed is append-only. The port name must not invite read, query, claim or dispatcher behavior.
```

### DEC-015-003 — Outbox write port name

```text
Use ScOutboxWritePort.
```

The port exposes append-only behavior.

### DEC-015-004 — Package placement

Preferred package family:

```text
com.sovereign.connect.core.scledger.model
com.sovereign.connect.core.scledger.port
```

Rationale:

```text
Ledger/outbox serves all of SC-C, not only topology.
```

### DEC-015-005 — Append operation pattern

```text
Ledger append: INSERT only.
Outbox append: INSERT only.
```

`MERGE` is not used for ledger/outbox append in this MU.

`MERGE` remains a valid existing pattern for replay/upsert cases such as `materialization_decision_replay`, but this MU records append-only facts and dispatch intents.

### DEC-015-006 — Idempotency-key uniqueness scope

```text
Use UNIQUE(habitat_id, idempotency_key) for sc_c_ledger_entries.
Use UNIQUE(habitat_id, idempotency_key) for sc_c_outbox_entries.
```

Do not use global `UNIQUE(idempotency_key)` unless a later production schema decision explicitly makes the idempotency key globally unique across habitats.

### DEC-015-007 — No read ports

```text
Do not introduce ScLedgerReadPort.
Do not introduce ScOutboxReadPort.
```

Readback for L4 tests MAY use `JdbcTemplate` / direct SQL against seed H2 tables.

### DEC-015-008 — No dispatcher / claim semantics

```text
No dispatcher.
No claim loop.
No polling loop.
No status transition API.
No retry scheduler.
```

The seed only writes initial outbox records, normally `PENDING`.

### DEC-015-009 — No partial H2 index requirement

Do not require H2 partial indexes.

Use normal H2-compatible indexes such as:

```sql
CREATE INDEX IF NOT EXISTS ix_outbox_status_created
ON sc_c_outbox_entries(habitat_id, status, created_at_ms);
```

### DEC-015-010 — Delivery observations and terminal responses out of scope

```text
sc_c_delivery_observations: out of scope.
sc_c_terminal_responses: out of scope.
```

### DEC-015-011 — TemporalAct / Action / command path out of scope

```text
No temporal_acts table.
No TemporalAct aggregate.
No Temporal Engine.
No ActionRequest.
No command dispatch.
No SC-B / NATS / JetStream.
```

---

## 5. Implementation Scope

### 5.1 Positive scope

This MU may implement:

```text
1. Ledger write model:
   - LedgerEntry record or equivalent;
   - SemanticKind enum or equivalent;
   - ScLedgerWritePort with appendLedgerEntry(...).

2. Outbox write model:
   - OutboxEntry record or equivalent;
   - OutboundKind enum or equivalent;
   - DeliveryLane enum or equivalent;
   - OutboxEntryStatus enum or equivalent;
   - ScOutboxWritePort with appendOutboxEntry(...).

3. H2 seed tables:
   - sc_c_ledger_entries;
   - sc_c_outbox_entries.

4. H2/JdbcTemplate adapter implementation:
   - H2BaseTopologyRepository implements ScLedgerWritePort and ScOutboxWritePort for seed-local convenience;
   - createSchema() creates the two tables and required normal indexes;
   - append methods use INSERT.

5. Recovery-visible persistence tests:
   - append row;
   - recreate repository/adapter;
   - verify row still exists.

6. Integrity tests:
   - duplicate ledger idempotency key rejected per habitat;
   - duplicate outbox idempotency key rejected per habitat;
   - outbox entry referencing missing ledger entry rejected;
   - notificationTargetRef carried opaquely.

7. Architecture regression tests:
   - no SQL/JDBC import in domain/materialization services;
   - no SC-B / NATS / JetStream dependencies;
   - no outbox dispatcher, read port or claim loop.
```

### 5.2 Minimal ledger table seed shape

The seed table is H2-compatible and intentionally smaller than the full production schema.

Minimum required logical fields:

```text
ledger_entry_id
habitat_id
aggregate_type
aggregate_id
semantic_kind
payload_json
idempotency_key
recorded_at_ms
```

Recommended H2 seed shape:

```sql
CREATE TABLE IF NOT EXISTS sc_c_ledger_entries (
    ledger_entry_id VARCHAR(36) PRIMARY KEY,
    habitat_id VARCHAR(255) NOT NULL,
    aggregate_type VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    semantic_kind VARCHAR(128) NOT NULL,
    payload_json CLOB NOT NULL,
    idempotency_key VARCHAR(512) NOT NULL,
    recorded_at_ms BIGINT NOT NULL,
    CONSTRAINT uq_ledger_habitat_idempotency UNIQUE (habitat_id, idempotency_key)
);
```

Required index:

```sql
CREATE INDEX IF NOT EXISTS ix_ledger_habitat_aggregate
ON sc_c_ledger_entries(habitat_id, aggregate_type, aggregate_id);
```

Rules:

```text
- ledger_entry_id is SC-C-owned record identity.
- semantic_kind names SC-C semantic record, not physical topic.
- payload_json stores semantic payload or reconstruction data as JSON text.
- idempotency_key is mandatory in this seed to support TemporalAct firing/cancellation/misfire uniqueness later.
- metadata_json, record_class, payload_hash, causation/correlation and richer production fields MAY be deferred unless implementation adds them without expanding behavior.
```

### 5.3 Minimal outbox table seed shape

Minimum required logical fields:

```text
outbox_entry_id
ledger_entry_id
habitat_id
outbound_kind
delivery_lane
logical_topic
semantic_payload_json
notification_target_ref
idempotency_key
status
created_at_ms
updated_at_ms
```

Recommended H2 seed shape:

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
        CHECK(status IN ('PENDING','CLAIMED','DISPATCHED','DISPATCH_FAILED','RETRY_WAIT','DEAD_LETTERED','SUPPRESSED')),
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    CONSTRAINT uq_outbox_habitat_idempotency UNIQUE (habitat_id, idempotency_key),
    CONSTRAINT fk_outbox_ledger FOREIGN KEY (ledger_entry_id)
        REFERENCES sc_c_ledger_entries(ledger_entry_id)
);
```

Required index:

```sql
CREATE INDEX IF NOT EXISTS ix_outbox_status_created
ON sc_c_outbox_entries(habitat_id, status, created_at_ms);
```

Rules:

```text
- Every outbox entry MUST reference an existing ledger entry.
- Appended outbox entries default to PENDING unless a test fixture explicitly sets another valid status.
- notification_target_ref is opaque and nullable.
- claim_expires_at_ms, expires_at_ms, attempt_count and dispatch-management columns MAY be deferred because this MU has no dispatcher, claim loop or TTL behavior.
- If implementation adds dispatch-management columns for closer SDD alignment, it MUST NOT implement their behavior in this MU.
```

### 5.4 Minimal TemporalActs readiness support

This MU must be sufficient for a later Profile A TemporalActs seed to persist:

```text
TemporalActCreated      -> ledger entry
TemporalActCancelled    -> ledger entry
TemporalActFired        -> ledger entry + outbox entry
TemporalActMisfired     -> ledger entry
TemporalActFailed       -> ledger entry, if failure occurs after eligibility
```

This MU does not implement TemporalActs themselves.

---

## 6. Negative Scope

This MU MUST NOT implement:

```text
- outbox dispatcher;
- claim loop;
- polling loop;
- retry scheduler;
- delivery worker;
- delivery observation storage;
- dispatcher status updates;
- ScLedgerReadPort;
- ScOutboxReadPort;
- sc_c_delivery_observations table;
- sc_c_terminal_responses table;
- sc_c_outbox_attempts table;
- SC-B runtime;
- NATS / JetStream;
- physical broker subject mapping;
- transport acknowledgement handling;
- SC-D adapter delivery;
- Temporal Engine;
- temporal_acts table;
- TemporalAct aggregate or lifecycle;
- TemporalAct firing loop;
- ActionRequest;
- ActionTemporalPayload execution;
- command dispatch;
- terminal response records/table;
- generic request-state;
- generic command/request idempotency;
- Profile B TemporalActs;
- Projection / Hub / Session / Identity / Authority / Policy / Surface state;
- Flyway migrations;
- production SQLite implementation.
```

---

## 7. Required Invariants

### INV-015-001 — SC-C semantic ownership

```text
Ledger entries and outbox entries are SC-C-owned persistence records.
SC-B and physical brokers are not semantic authority.
```

### INV-015-002 — Append-only seed

```text
This MU implements append storage only.
It MUST NOT implement dispatch, claim, retry, polling, delivery loops or status transition workflows.
```

### INV-015-003 — Port boundary preservation

```text
Domain services and materialization services MUST NOT import H2, JDBC, SQL, DataSource, JdbcTemplate or Flyway classes.
```

### INV-015-004 — Ledger/outbox separation

```text
A ledger entry records semantic evidence.
An outbox entry records dispatch intent.
The outbox entry MUST NOT become the source of semantic truth.
```

### INV-015-005 — Referential outbox integrity

```text
Every appended outbox entry MUST reference an existing ledger entry.
```

### INV-015-006 — Dispatcher exclusion

```text
No ScOutboxReadPort.claimReadyEntries(...), dispatcher, retry loop, background worker, transport adapter, SC-B client or NATS client may be introduced by this MU.
```

### INV-015-007 — Boundary metadata exclusion

```text
No ledger/outbox metadata may require Session, Identity, Authority, Policy, Projection, Hub conversation or Surface-specific ownership semantics.
```

### INV-015-008 — TemporalActs compatibility without TemporalActs implementation

```text
The storage shape must support future TemporalAct lifecycle ledger/outbox records without implementing TemporalAct aggregate, scheduler or firing loop.
```

### INV-015-009 — Seed H2 is not production storage doctrine

```text
H2 implementation validates the seed.
Production storage remains governed by SQLite/WAL/Flyway SDD/ADR artifacts.
H2BaseTopologyRepository implementing ledger/outbox write ports is seed-local convenience, not final production modularity.
```

### INV-015-010 — Existing tests must keep passing

```text
All previously validated SC-C seed tests MUST keep passing.
```

### INV-015-011 — Per-habitat idempotency uniqueness

```text
Ledger and outbox idempotency keys are unique within habitat scope in this seed.
```

### INV-015-012 — No read surface

```text
This MU exposes no ledger/outbox read port.
Readback exists only in tests through direct SQL/JdbcTemplate.
```

---

## 8. Minimal Acceptance Criteria

### AC-015-001 — Ledger write port introduced

`ScLedgerWritePort` exists and exposes append-only ledger behavior.

### AC-015-002 — Outbox write port introduced

`ScOutboxWritePort` exists and exposes append-only outbox behavior.

### AC-015-003 — Ledger table created

The H2 seed persistence adapter creates `sc_c_ledger_entries` or a directly equivalent seed table.

### AC-015-004 — Outbox table created

The H2 seed persistence adapter creates `sc_c_outbox_entries` or a directly equivalent seed table.

### AC-015-005 — Ledger append persists

Appending a ledger entry persists it durably.

### AC-015-006 — Outbox append persists

Appending an outbox entry persists it durably.

### AC-015-007 — Outbox references ledger

Appending an outbox entry for an unknown ledger entry is rejected or prevented by persistence integrity.

### AC-015-008 — Recovery-visible ledger readback

A ledger entry remains observable after repository/adapter recreation.

### AC-015-009 — Recovery-visible outbox readback

An outbox entry remains observable after repository/adapter recreation.

### AC-015-010 — Duplicate ledger semantic idempotency rejected

Appending two ledger entries with the same `(habitatId, idempotencyKey)` is rejected by persistence integrity.

### AC-015-011 — Duplicate outbox semantic idempotency rejected

Appending two outbox entries with the same `(habitatId, idempotencyKey)` is rejected by persistence integrity.

### AC-015-012 — Habitat-scoped duplicate allowance tested or documented

If implementation can test it cleanly, the same `idempotencyKey` MAY be accepted for different `habitatId` values. If not tested, implementation report MUST state that the constraint is declared by schema and not behaviorally exercised.

### AC-015-013 — Notification target ref is opaque

`notificationTargetRef` is stored and recovered exactly as provided, without parsing into user, session, authority, policy or surface semantics.

### AC-015-014 — No read ports

No `ScLedgerReadPort`, `ScOutboxReadPort`, claim port, dispatcher query port or polling API is introduced.

### AC-015-015 — No dispatcher

No dispatcher, claim loop, polling loop, transport worker or SC-B delivery code is added.

### AC-015-016 — No SC-B / NATS dependencies

Production code added by this MU has no dependency on SC-B runtime, NATS, JetStream or physical broker bindings.

### AC-015-017 — No TemporalAct implementation

No TemporalAct aggregate, scheduler, firing loop, cancellation service or MISFIRED recovery logic is implemented.

### AC-015-018 — No ActionRequest / command dispatch

No `ActionRequest`, command dispatch, adapter execution or Profile B TemporalActs path is implemented.

### AC-015-019 — No terminal response table

`sc_c_terminal_responses` or equivalent terminal response persistence is not implemented in this MU.

### AC-015-020 — No delivery observations table

`sc_c_delivery_observations` is not implemented in this MU.

### AC-015-021 — Domain services remain persistence-agnostic

No domain/materialization service imports JDBC/H2/SQL classes.

### AC-015-022 — Existing test suite remains green

All pre-existing tests pass.

### AC-015-023 — TemporalAct future record support

Tests or fixtures demonstrate that the ledger/outbox seed can store draft records with semantic kinds equivalent to `TemporalActCreated` and `TemporalActFired` without implementing the Temporal Engine.

### AC-015-024 — H2 seed status explicit

Implementation report explicitly states that this is H2 seed storage and not the production SQLite migration implementation.

### AC-015-025 — Code Surface Audit constraints applied

`context.md`, `acceptance-map.md` and `codex-prompt.md` incorporate the approved Code Surface Audit findings before implementation.

---

## 9. Required New Tests

The execution package SHOULD introduce a focused test class:

```text
OutboxLedgerStorageSeedTest
```

Required tests:

```text
appendLedgerEntryPersistsDurably
appendOutboxEntryPersistsDurably
duplicateLedgerIdempotencyKeyRejected
duplicateOutboxIdempotencyKeyRejected
outboxEntryHasValidLedgerReference
outboxEntryWithMissingLedgerReferenceRejected
notificationTargetRefCarriedOpaquely
noReadPortsOrDispatcherIntroduced
scBAndNatsRemainAbsent
existingSuiteRemainsGreen
```

Allowed readback strategy:

```text
Direct JdbcTemplate / SQL in tests.
No production read port.
```

---

## 10. Failure Signals

### FS-015-001 — Dispatcher creep

Implementation introduces a dispatcher, claim loop, retry loop, background worker, polling loop or dispatch status transition workflow.

### FS-015-002 — SC-B / broker contamination

Implementation introduces SC-B runtime, NATS, JetStream, broker subjects or transport delivery code.

### FS-015-003 — Engine-local log pattern

Implementation creates a Temporal Engine or timer-specific private firing log instead of general SC-C ledger/outbox append storage.

### FS-015-004 — Domain SQL leakage

Domain services or materializers import JDBC/H2/SQL/DataSource/JdbcTemplate classes.

### FS-015-005 — Terminal response expansion

Implementation adds terminal response records/table or generic request-state despite negative scope.

### FS-015-006 — Action / command expansion

Implementation adds `ActionRequest`, command dispatch, adapter execution or Profile B behavior.

### FS-015-007 — Outbox without ledger

Implementation allows outbox entries that do not reference a committed ledger entry.

### FS-015-008 — Semantic authority inversion

Implementation treats outbox status or broker delivery state as the semantic truth that an event happened.

### FS-015-009 — Boundary metadata leak

Ledger/outbox metadata requires Session, Identity, Authority, Policy, Projection, Hub conversation or Surface state.

### FS-015-010 — Existing regression breakage

Previously passing MU-001 through MU-014 tests fail without documented and accepted reason.

### FS-015-011 — Production-storage claim creep

Implementation claims to implement production SQLite/Flyway migrations instead of H2 seed storage.

### FS-015-012 — Universal repository doctrine

Implementation or report claims that `H2BaseTopologyRepository` as ledger/outbox adapter establishes the target production persistence modularity.

### FS-015-013 — Global idempotency-key uniqueness

Implementation uses global `UNIQUE(idempotency_key)` where a habitat-scoped uniqueness constraint is required, unless a patch is explicitly approved.

### FS-015-014 — Read surface expansion

Implementation introduces ledger/outbox read ports or dispatcher query APIs.

---

## 11. Upstream Patch Protocol

If Code Surface Audit, decision confirmation or implementation reveals that this MIR cannot be implemented safely within this scope, the implementation MUST stop and report one of:

```text
PATCH-MIR-015:
  MIR scope, ACs or failure signals require patch.

PATCH-SDD-OUTBOX-LEDGER:
  SDD-SOV-SC-C-OUTBOX-LEDGER-001 requires clarification.

PATCH-SDD-TEMPORAL-ENGINE:
  SDD-SOV-SC-C-TEMPORAL-ENGINE-001 requires clarification.

PATCH-GOVERNANCE:
  INDEX / SYNC / Production Readiness ordering requires correction.

OPEN-NEW-MU:
  Required work exceeds this MU and must become a separate MU.
```

Implementation MUST NOT silently broaden scope to force completion.

---

## 12. Code Surface Audit Disposition

The merged audit is accepted as the operative surface analysis for this MIR version.

Audit summary:

```text
Surface category:
  Non-Greenfield — bounded persistence surface.

Existing surface:
  H2BaseTopologyRepository currently owns H2 schema bootstrap patterns and implements multiple SC-C persistence/read/write ports.

Implementation strategy:
  Extend H2BaseTopologyRepository for MU-015 as seed-local convenience.
  Do not interpret this as production modularity doctrine.

No call-site disruption expected:
  Existing constructor signatures should remain unchanged.
```

The execution package MUST incorporate this audit into:

```text
context.md
acceptance-map.md
codex-prompt.md
implementation-report.md
```

---

## 13. Codex Prompt Reference

The executable implementation prompt is not included in this MIR.

Expected future references:

```text
Code Surface Audit:
  docs/mir/mir-015/code-surface-audit.md

Decision Confirmation:
  docs/mir/mir-015/decision-confirmation.md

Context:
  docs/mir/mir-015/context.md

Acceptance Map:
  docs/mir/mir-015/acceptance-map.md

Codex Prompt:
  docs/mir/mir-015/codex-prompt.md

Implementation Report:
  docs/mir/mir-015/implementation-report.md
```

Executive package MUST NOT be produced before DEC-015-001 through DEC-015-011 are confirmed.

---

## 14. Suggested Branch

```text
feat/sc-c-outbox-ledger-storage-seed
```

---

## 15. Suggested Commit

```text
feat(sc-c): add outbox ledger append storage seed
```

---

## 16. Post-Implementation Report Requirements

The implementation report MUST include:

```text
- baseline commit;
- implementation commit;
- files changed;
- tables added;
- ports/types added;
- tests added;
- full mvn test result;
- acceptance-map PASS/FAIL table;
- confirmation that no dispatcher was implemented;
- confirmation that no read ports were introduced;
- confirmation that no SC-B/NATS/JetStream dependency was added;
- confirmation that no TemporalAct engine, temporal_acts table or ActionRequest code was added;
- confirmation that H2BaseTopologyRepository usage is seed-local convenience;
- notes on any deviations from MIR seed table shape;
- any deferred items discovered during implementation.
```
