# MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001

## Materialization Increment Record — SC-B Outbox Bridge Seed

```text
Document ID:  MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
Title:        Materialization Increment Record — SC-B Outbox Bridge Seed
Version:      v0.2.0-candidate
Status:       Candidate / approvable for execution package
Date:         2026-05-29
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-B / SC-C integration boundary
Track:        SC-B Runtime Dispatch Hardening / H2
MU ID:        MU-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
MU Slot:      MU-026
```

---

## Changelog v0.2.0-candidate

This version incorporates review corrections before execution-package descent:

```text
1. Makes dispatchRecordId determinism mandatory for a given sourceRecordId / outboxEntryId.
2. Keeps dispatchRecordId distinct from the raw outboxEntryId as a boundary-clarity SHOULD, not as a substitute for determinism.
3. Removes obsolete attempt_count references from H2 read-port and outbox-mutation rules.
4. Reformulates AC-026-034 so TIMER_FIRED_SIGNAL behavior is testable whether mapped to EVENT or left non-dispatchable.
```

## 0. MIR boundary

This MIR authorizes a future implementation attempt for the **H2 Outbox Bridge Seed** after:

```text
H0 / MU-024 — SC-B Abstract Bus Seed                     Validated L4
H1 / MU-025 — SC-B Dispatch State Persistence            Validated L4
```

This MIR does not include an execution prompt, context file, acceptance map, Codex instructions, or implementation package. Those artifacts must be produced separately if this MIR is promoted to candidate and accepted for execution descent.

This MIR is not a NATS / JetStream binding. It is not lifecycle-channel implementation. It is not SC-D adapter implementation. It is not `ScdCommand` shape definition. It is not dispatch observation persistence.

---

## 0.1 Intended validation level

```text
Target validation level: L4
Required evidence: implementation attempt + local regression tests + architecture tests
Expected evidence package: docs/mir/mir-026/
```

---

## 1. Purpose

MU-024 created the abstract SC-B substrate:

```text
ScCommandEnvelope / ScEventEnvelope / ScResponseEnvelope
ScMessageMetadata
ScRoutingKey
ScBusPort
RuntimeDispatchService
DispatchCandidateReadPort
DispatchStateWritePort
DispatchObservationPort
in-memory bus/runtime adapters
validation services
```

MU-025 hardened dispatch state by replacing purely in-memory dispatch state with restart-visible JDBC persistence.

The current missing execution bridge is:

```text
SC-C scledger outbox rows
  → SC-B DispatchCandidate
  → RuntimeDispatchService
  → ScBusPort
```

This MIR opens the H2 increment that projects SC-C-owned outbox records into SC-B technical dispatch candidates without collapsing ownership boundaries.

Canonical purpose:

```text
Make SC-C outbox visible to SC-B runtime dispatch as candidates,
without making SC-B owner of SC-C outbox semantics.
```

---

## 2. Depends on

```text
PDR-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v1.0.0-accepted / Validated L4
SDD-SOV-SC-C-OUTBOX-LEDGER-001 v0.1.1-draft
MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001 v1.0.0-accepted / Validated L4
```

---

## 3. Related

```text
ADR-SOV-SC-SERIALIZATION-001 v0.2.0-candidate
PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001 v0.2.0-candidate
ADR-SOV-SC-BUS-TECH-001 v0.2.2-draft
PDR-SOV-SC-BUS-CONTRACT-001 v0.4.6-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.25-draft
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.19-draft
```

These related artifacts are not implementation dependencies for the seed bridge except where stated explicitly by downstream SDD or execution package.

---

## 4. Baseline state

The baseline after MU-025 is:

```text
SC-B abstract runtime exists.
SC-B dispatch state is restart-visible through JDBC.
SC-C scledger has persisted outbox rows.
SC-B cannot yet read SC-C outbox rows.
No ScOutboxDispatchReadPort exists.
No integration.scledgerdispatch package exists.
No outbox bridge projector exists.
No DispatchCandidate source projection from OutboxEntry exists.
DispatchObservation persistence remains in-memory / deferred to H3.
No NATS / JetStream binding exists.
```

Known code facts from the CSA and MU-025 validation:

```text
OutboxEntry fields:
  outboxEntryId
  ledgerEntryId
  habitatId
  outboundKind
  deliveryLane
  logicalTopic
  semanticPayloadJson
  notificationTargetRef
  idempotencyKey
  status
  createdAt
  updatedAt
  metadataJson

DeliveryLane values:
  SIGNAL, COMMAND, EVENT, RESPONSE

DispatchCandidate fields:
  dispatchRecordId
  sourceRecordId
  lane
  logicalTopic
  partitionKey
  correlationId
  causationId
  messageId

DispatchState persistence:
  sc_b_dispatch_records
  sc_b_dispatch_attempts
  source_record_id column exists and is nullable / opaque
```

---

## 5. Problem statement

SC-C writes semantic outbox entries, but SC-B runtime dispatch cannot consume them.

This means the following system path is still incomplete:

```text
TemporalAct fires
  → SC-C writes ledger/outbox
  → [missing bridge]
  → DispatchCandidate
  → RuntimeDispatchService
  → ScBusPort
  → handler / later SC-D adapter
```

Without H2, the bus runtime is technically sound but still isolated from SC-C production outbox.

The risk is to solve this by collapsing layers, for example:

```text
OutboxEntry used directly as ScEnvelope
SC-B mutating OutboxEntryStatus
bus.runtime importing core.scledger
SC-C importing bus.runtime
bridge pretending to define ScdCommand
SIGNAL mapped to COMMAND because ActionTemporalPayload will exist later
```

This MIR forbids those shortcuts.

---

## 6. Goals

### G-MIR-026-001 — Narrow SC-C outbox read port

Introduce a narrow read port for dispatchable SC-C outbox entries.

### G-MIR-026-002 — Bridge boundary package

Create an explicit integration boundary that is allowed to see both SC-C scledger models and SC-B dispatch/runtime ports.

### G-MIR-026-003 — OutboxEntry → DispatchCandidate projection

Project SC-C outbox rows into SC-B `DispatchCandidate` records without treating the outbox row as an envelope.

### G-MIR-026-004 — Source-record deduplication

Use `OutboxEntry.outboxEntryId` as the candidate `sourceRecordId` and derive a stable `dispatchRecordId` from it so repeated bridge reads do not create unrelated dispatch records.

### G-MIR-026-005 — Dispatch-state-aware candidate filtering

Do not repeatedly emit candidates for outbox rows whose derived dispatch record is already non-claimable or terminal.

### G-MIR-026-006 — SIGNAL lane safety

Handle `DeliveryLane.SIGNAL` explicitly and ensure it never maps to `ScBusLane.COMMAND` in this seed.

### G-MIR-026-007 — Preserve SC-C semantic authority

The bridge must not mutate `OutboxEntryStatus` in the seed and must not interpret terminal semantic success/failure.

---

## 7. Non-goals

This MIR does not authorize:

```text
- NATS / JetStream binding.
- NATS subjects.
- physical serialization.
- SC-D lifecycle channel implementation.
- SC-D adapter implementation.
- ScdCommand production class.
- fact family production classes.
- adapter route assignment.
- hot onboarding.
- dispatch observation persistence.
- mutation of OutboxEntryStatus.
- retry policy beyond existing RuntimeDispatchService state transitions.
- semantic action verification.
- terminal request-state persistence.
- View Composer / SApp / Surface work.
```

---

## 8. Mandatory implementation actions

This MIR consumes the mandatory H2 actions identified by the merged CSA.

```text
ACTION-H-001 — SIGNAL lane handling in bridge projector.
ACTION-H-002 — ScOutboxDispatchReadPort must be created.
ACTION-H-003 — integration.scledgerdispatch package must be created.
ACTION-H-004 — SC-B migrations must not collide with SC-C Flyway V1–V4.
```

For this MIR, ACTION-H-004 is interpreted as:

```text
H2 SHOULD NOT require a new migration.
If execution discovers a necessary migration, it MUST use the SC-B migration space
selected by MU-025, not V5–V99.
```

---

## 9. Design decisions for this MIR

### D-MIR-026-001 — H2 is bridge only

This increment implements the outbox bridge. It must not implement H3 observation persistence or NATS binding.

### D-MIR-026-002 — ScOutboxDispatchReadPort belongs to core.scledger.port

The read port is a SC-C scledger boundary port.

Required location:

```text
com.sovereign.connect.core.scledger.port.ScOutboxDispatchReadPort
```

Minimum shape:

```java
public interface ScOutboxDispatchReadPort {
    List<OutboxEntry> findDispatchableEntries(int limit);
}
```

Rules:

```text
- It returns SC-C OutboxEntry records.
- It does not return DispatchCandidate.
- It does not import bus.*.
- It does not mutate OutboxEntryStatus.
- It is the only new core.* port authorized by this MIR.
```

### D-MIR-026-003 — SQLiteScLedgerOutboxRepository implements the read port

The existing SC-C persistence adapter that writes ledger/outbox rows may implement `ScOutboxDispatchReadPort`.

Expected behavior:

```text
findDispatchableEntries(limit):
  - rejects limit <= 0;
  - reads only OutboxEntryStatus.PENDING rows;
  - orders by createdAt ascending, then outboxEntryId for deterministic order;
  - applies the limit;
  - maps all OutboxEntry fields faithfully;
  - does not update status;
  - does not claim rows.
```

### D-MIR-026-004 — Bridge package is integration.scledgerdispatch

Required package:

```text
com.sovereign.connect.integration.scledgerdispatch
```

This is the only production package allowed to import both:

```text
com.sovereign.connect.core.scledger.*
com.sovereign.connect.bus.*
```

It is a boundary adapter, not a new domain layer.

### D-MIR-026-005 — Bridge implements DispatchCandidateReadPort

The bridge adapter should implement:

```text
com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort
```

Expected class, naming may vary if implementation report maps it clearly:

```text
ScLedgerDispatchCandidateReadAdapter
```

It composes:

```text
ScOutboxDispatchReadPort
OutboxEntryDispatchProjector
DispatchStateWritePort.currentAttempt(...) or equivalent read-only dispatch-state check
```

The bridge may use `DispatchStateWritePort.currentAttempt(...)` for read-only filtering because the existing port already exposes current attempt state. It must not claim or transition dispatch state during candidate read.

### D-MIR-026-006 — OutboxEntry is not DispatchCandidate

The bridge must preserve this invariant:

```text
OutboxEntry != DispatchCandidate != ScEnvelope
```

Definitions:

```text
OutboxEntry:
  SC-C semantic outbox record owned by scledger.

DispatchCandidate:
  SC-B technical dispatch input derived from a source record.

ScEnvelope:
  runtime transport carrier built by RuntimeDispatchService during dispatch.
```

### D-MIR-026-007 — sourceRecordId is outboxEntryId

Projection rule:

```text
DispatchCandidate.sourceRecordId = OutboxEntry.outboxEntryId
```

This reference is opaque. SC-B may use it for deduplication/traceability, but must not use it as semantic authority.

### D-MIR-026-008 — dispatchRecordId is deterministic and bridge-owned

Projection rule:

```text
DispatchCandidate.dispatchRecordId MUST be deterministic for the same sourceRecordId.
Repeated projection of the same OutboxEntry MUST produce the same dispatchRecordId.
Random dispatchRecordId generation is forbidden.
```

Recommended seed implementation:

```java
UUID.nameUUIDFromBytes(("sc-b.dispatch:" + outboxEntryId).getBytes(StandardCharsets.UTF_8))
```

The implementation may choose an equivalent deterministic strategy only if it is documented in the implementation report and covered by tests.

`dispatchRecordId` SHOULD NOT equal the raw `outboxEntryId` unless execution records a deliberate justification. Keeping them distinct better preserves the `OutboxEntry != DispatchCandidate` boundary, but this boundary-clarity recommendation MUST NOT weaken the determinism requirement.

### D-MIR-026-009 — Candidate message IDs are per-dispatch message IDs

`DispatchCandidate.messageId` is the message ID used by `RuntimeDispatchService` to build `ScMessageMetadata`.

Default seed rule:

```text
messageId MAY be generated fresh per projection.
correlationId MUST be stable enough for traceability.
causationId SHOULD reference sourceRecordId or ledgerEntryId when possible.
```

If the execution package chooses deterministic message IDs, it must state why. The MIR does not require deterministic message IDs.

### D-MIR-026-010 — Partition key is habitat-scoped

Default seed partition key:

```text
partitionKey = OutboxEntry.habitatId
```

This satisfies habitat-scoped dispatch ordering without introducing NATS subjects or SC-D route assignment.

### D-MIR-026-011 — Logical topic is preserved

Projection rule:

```text
DispatchCandidate.logicalTopic = OutboxEntry.logicalTopic
```

The bridge may reject or skip blank/null topics, but it must not reinterpret topics as NATS subjects.

### D-MIR-026-012 — DeliveryLane mapping

Default mapping:

```text
DeliveryLane.COMMAND  -> ScBusLane.COMMAND
DeliveryLane.EVENT    -> ScBusLane.EVENT
DeliveryLane.RESPONSE -> ScBusLane.RESPONSE
```

### D-MIR-026-013 — SIGNAL handling is explicit and scoped

`DeliveryLane.SIGNAL` MUST NOT map to `ScBusLane.COMMAND`.

Seed rule:

```text
DeliveryLane.SIGNAL MAY map to ScBusLane.EVENT only for explicitly scoped signal outbox records.
```

For this MIR, the explicitly scoped signal record is:

```text
outboundKind = TIMER_FIRED_SIGNAL
logicalTopic = sc-c.timer-fired
```

All other `SIGNAL` records are non-dispatchable in this seed and must be skipped or rejected by the projector in a test-covered way.

This permits the existing signal-timer outbox flow to reach the abstract event lane without pretending that ActionTemporalPayload can already dispatch to SC-D.

### D-MIR-026-014 — COMMAND is not ScdCommand yet

This MIR may project `DeliveryLane.COMMAND` to `ScBusLane.COMMAND`, but it must not introduce a production `ScdCommand` class.

RuntimeDispatchService currently builds envelopes whose payload surrogate is `DispatchCandidate`. This remains acceptable for the seed unless a later MIR replaces the payload model after SC-D command shape is defined.

### D-MIR-026-015 — Candidate filtering by dispatch state

To avoid repeated dispatch of the same PENDING outbox row, the bridge candidate reader must filter using the derived `dispatchRecordId`.

Allowed candidate states:

```text
No dispatch record exists       -> emit candidate
PENDING                         -> emit candidate if explicitly represented
RETRY_SCHEDULED                 -> emit candidate
```

Non-emittable states:

```text
CLAIMED
DISPATCHING
DISPATCHED
DELIVERY_FAILED
EXHAUSTED
CANCELLED_BY_SUPERSEDE
```

`DELIVERY_FAILED` is not re-emitted until explicit retry scheduling changes the state to `RETRY_SCHEDULED`.

### D-MIR-026-016 — No OutboxEntryStatus mutation in H2

The bridge must not update:

```text
OutboxEntryStatus
updated_at_ms
```

The dispatch state table is the technical delivery state for H2. SC-C outbox remains SC-C semantic record storage.

### D-MIR-026-017 — No physical subject encoding

The bridge must not implement:

```text
NATS subject hierarchy
scid1_ subject token encoding
JSON wire serialization
Protobuf
MessagePack
```

`ADR-SOV-SC-SERIALIZATION-001` remains relevant for later physical binding, not for this in-process bridge seed.

---

## 10. Expected implementation surface

Expected production surface:

```text
src/main/java/com/sovereign/connect/core/scledger/port/
  ScOutboxDispatchReadPort.java                         [new]

src/main/java/com/sovereign/connect/adapter/persistence/sqlite/
  SQLiteScLedgerOutboxRepository.java                   [extend: implements ScOutboxDispatchReadPort]

src/main/java/com/sovereign/connect/integration/scledgerdispatch/
  ScLedgerDispatchCandidateReadAdapter.java             [new]
  OutboxEntryDispatchProjector.java                     [new]
  DispatchRecordIdFactory.java                          [new or equivalent]
  DeliveryLaneToScBusLaneMapper.java                    [new or equivalent]
```

Expected test surface:

```text
src/test/java/com/sovereign/connect/adapter/persistence/sqlite/
  SQLiteScOutboxDispatchReadPortTest.java               [new or equivalent]

src/test/java/com/sovereign/connect/integration/scledgerdispatch/
  OutboxEntryDispatchProjectorTest.java                 [new]
  ScLedgerDispatchCandidateReadAdapterTest.java         [new]
  ScLedgerDispatchBridgeRuntimeTest.java                [new or equivalent]

src/test/java/com/sovereign/connect/bus/
  ScBusOutboxBridgeArchitectureTest.java                [new or equivalent]
```

Names may vary only if the implementation report maps actual classes to acceptance criteria.

---

## 11. Acceptance criteria

### 11.1 Boundary and scope

```text
AC-026-001 — The implementation creates ScOutboxDispatchReadPort in core.scledger.port.
AC-026-002 — The implementation creates integration.scledgerdispatch as the bridge package.
AC-026-003 — bus.** does not import core.**.
AC-026-004 — core.** does not import bus.runtime.**.
AC-026-005 — integration.scledgerdispatch is the only production package importing both scledger and bus runtime/contract surfaces.
AC-026-006 — No NATS / JetStream / broker dependency is introduced.
AC-026-007 — No ScdCommand production class is introduced.
AC-026-008 — DispatchObservation persistence remains deferred.
```

### 11.2 ScOutboxDispatchReadPort

```text
AC-026-009 — findDispatchableEntries(limit) returns only PENDING outbox rows.
AC-026-010 — findDispatchableEntries(limit) rejects non-positive limits.
AC-026-011 — findDispatchableEntries(limit) applies the limit.
AC-026-012 — findDispatchableEntries(limit) returns rows in deterministic createdAt/outboxEntryId order.
AC-026-013 — Mapping from sc_c_outbox_entries to OutboxEntry preserves all fields.
AC-026-014 — The read method does not mutate OutboxEntryStatus or updated_at_ms.
```

### 11.3 Projection

```text
AC-026-015 — OutboxEntry.outboxEntryId maps to DispatchCandidate.sourceRecordId.
AC-026-016 — dispatchRecordId is deterministic for the same outboxEntryId: repeated projection of the same OutboxEntry MUST produce the same dispatchRecordId; random generation is forbidden.
AC-026-017 — dispatchRecordId is distinct from raw outboxEntryId unless the implementation report justifies otherwise; this does not relax AC-026-016 determinism.
AC-026-018 — partitionKey maps to habitatId.
AC-026-019 — logicalTopic maps to OutboxEntry.logicalTopic.
AC-026-020 — correlation/causation/message IDs are non-null where RuntimeDispatchService validation requires them.
AC-026-021 — semanticPayloadJson is not parsed into a domain command type by H2.
```

### 11.4 Lane mapping

```text
AC-026-022 — DeliveryLane.COMMAND maps to ScBusLane.COMMAND.
AC-026-023 — DeliveryLane.EVENT maps to ScBusLane.EVENT.
AC-026-024 — DeliveryLane.RESPONSE maps to ScBusLane.RESPONSE.
AC-026-025 — DeliveryLane.SIGNAL never maps to ScBusLane.COMMAND.
AC-026-026 — TIMER_FIRED_SIGNAL / sc-c.timer-fired SIGNAL maps to ScBusLane.EVENT or is otherwise handled according to the explicit seed rule.
AC-026-027 — Any non-scoped SIGNAL record is skipped/rejected in a test-covered way.
```

### 11.5 Candidate filtering and dispatch state

```text
AC-026-028 — Candidate reader emits a candidate when no dispatch record exists for the derived dispatchRecordId.
AC-026-029 — Candidate reader emits a candidate when dispatch state is RETRY_SCHEDULED.
AC-026-030 — Candidate reader does not emit candidates for CLAIMED, DISPATCHING, DISPATCHED, DELIVERY_FAILED, EXHAUSTED or CANCELLED_BY_SUPERSEDE.
AC-026-031 — Candidate reader does not claim or transition dispatch state during candidate read.
AC-026-032 — Repeated reads after DISPATCHED do not re-emit the same candidate.
```

### 11.6 Runtime bridge behavior

```text
AC-026-033 — A PENDING EVENT outbox row can be projected and dispatched through RuntimeDispatchService to an event handler.
AC-026-034 — The implementation report explicitly records whether TIMER_FIRED_SIGNAL is mapped to ScBusLane.EVENT or left non-dispatchable. If mapped to EVENT, a test verifies the dispatch path. If left non-dispatchable, a test verifies it is skipped/rejected. Both outcomes satisfy this AC only if the ambiguity is resolved explicitly and AC-026-040 is populated.
AC-026-035 — A COMMAND outbox row can be projected to the command lane without creating ScdCommand.
AC-026-036 — No OutboxEntryStatus mutation occurs after dispatch.
AC-026-037 — Dispatch state persistence records technical claim/dispatch state for the derived dispatchRecordId.
```

### 11.7 Evidence and reporting

```text
AC-026-038 — Implementation report records branch, commits and changed files.
AC-026-039 — Implementation report records final test counts for sovereign-connect, SC-C baseline and bus/integration delta.
AC-026-040 — Implementation report records whether SIGNAL was mapped to EVENT for TIMER_FIRED_SIGNAL or left non-dispatchable.
AC-026-041 — Implementation report records retained H3 observation persistence debt.
AC-026-042 — Implementation report confirms no NATS/JetStream, no ScdCommand and no outbox status mutation.
AC-026-043 — Acceptance map links each AC-026 criterion to tests/source/evidence.
```

---

## 12. Expected validation

Minimum expected validation after implementation:

```text
sovereign-connect tests: previous 317 + bridge tests
Expected result: 0 failures, 0 errors, 0 skipped
SC-C/non-bus baseline: 240 tests still green
EIB baseline: 56 tests still green if module tests are run
```

The exact test count is not fixed by this MIR; the execution package should set the expected count after inspecting the final baseline.

---

## 13. Retained debt disposition

### Closed by this MIR if validated

```text
DEBT-B-RD-H-002 — SC-C outbox is not visible to SC-B dispatch runtime.
  Closure condition: ScOutboxDispatchReadPort + integration bridge + candidate projection tests pass.

DEBT-019-005 — Outbox dispatcher / SC-B delivery runtime absent.
  Disposition: partially closed.
  H2 closes the outbox-to-dispatch candidate bridge only.
  Full closure requires H3 observation persistence and later adapter/runtime path.
```

### Retained after this MIR

```text
DEBT-B-RD-H-003 — Dispatch observation persistence absent. Retained for H3.
DEBT-B-RD-H-004 — Lifecycle channel not implemented.
DEBT-B-RD-H-005 — NATS / JetStream physical binding absent.
DEBT-B-RD-H-006 — ScdCommand shape absent.
DEBT-B-RD-H-007 — Fact family types absent.
DEBT-B-RD-H-008 — Adapter-scoped route assignment absent.
DEBT-B-RD-H-009 — Hot onboarding protocol absent.
DEBT-B-RD-H-010 — Terminal request-state integration remains SC-C-owned and not closed by H2.
```

---

## 14. Risks

### RISK-MIR-026-001 — Authority leakage from SC-C outbox to SC-B

Risk:

```text
The bridge may start interpreting OutboxEntry semantics or mutating OutboxEntryStatus.
```

Mitigation:

```text
H2 projects only to DispatchCandidate and does not mutate SC-C outbox status.
```

### RISK-MIR-026-002 — Package boundary collapse

Risk:

```text
bus.runtime imports core.scledger directly.
```

Mitigation:

```text
Only integration.scledgerdispatch may import both surfaces.
Architecture tests must enforce this.
```

### RISK-MIR-026-003 — SIGNAL accidentally becomes COMMAND

Risk:

```text
Existing temporal SIGNAL outbox rows may be mistaken for device commands.
```

Mitigation:

```text
SIGNAL MUST NOT map to COMMAND. TIMER_FIRED_SIGNAL may map to EVENT only by explicit rule.
```

### RISK-MIR-026-004 — Candidate re-emission loop

Risk:

```text
Outbox rows stay PENDING and bridge emits candidates repeatedly after dispatch.
```

Mitigation:

```text
Derived dispatchRecordId + dispatch-state filtering prevents re-emission for non-claimable states.
```

### RISK-MIR-026-005 — Premature SC-D command shape

Risk:

```text
H2 introduces ScdCommand to make command tests feel realistic.
```

Mitigation:

```text
ScdCommand remains deferred to SC-D manifest/command artifacts. H2 may use payload surrogate only.
```

### RISK-MIR-026-006 — Physical binding creep

Risk:

```text
Bridge implements NATS subject/serialization decisions prematurely.
```

Mitigation:

```text
No NATS, no subject encoding, no wire serialization in H2.
```

---

## 15. MIR acceptance checklist

This MIR may be promoted to candidate when:

```text
- MU-024 is recorded as Validated L4.
- MU-025 is recorded as Validated L4.
- CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged is accepted as H2 reference.
- H2 scope excludes H3 observation persistence.
- H2 scope excludes NATS/JetStream.
- H2 scope excludes ScdCommand production shape.
- SIGNAL handling rule is explicit.
- The outbox bridge package boundary is explicit.
- The read port location is explicit.
- No context/prompt is embedded in the MIR.
```

---

## 16. Suggested branch and commit

Suggested branch:

```text
feat/sc-b-mir-026-outbox-bridge-seed
```

Suggested implementation commit:

```text
feat(sc-b): bridge sc-c outbox to dispatch candidates
```

Alternative if the execution package emphasizes timer signal support:

```text
feat(sc-b): project outbox entries into dispatch candidates
```

---

## 17. Final dictum

```text
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v0.1.0-draft is opened for review.

It is not yet candidate.
It does not authorize implementation until promoted and accompanied by an execution package.
```
