# CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001

## Post-SDD Code Surface Audit — SC-B Runtime Dispatch Hardening

```text
Document ID:  CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001
Title:        Post-SDD Code Surface Audit — SC-B Runtime Dispatch Hardening
Version:      v0.2.0-merged
Status:       Merged / Post-SDD / Pre-MIR / H1 MIR-ready
Date:         2026-05-29
Corpus:       Sovereign Connect
Type:         CSA
Plane:        SC-B
Scope:        Code surface audit for the SC-B runtime dispatch hardening track
Baseline:     sovereign-connect-024-patch.zip, post-MU-024 response-metadata patch
Input SDD:    SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
Prior CSA:    CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.1.0-merged
Merge input:  User post-SDD CSA analysis, 2026-05-29
```

---

## Changelog v0.2.0-merged

This version merges the previous CSA with the user's code-inspection audit.

It:

1. Preserves the verdict that `MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001` is the next MIR-ready descent.
2. Adds the four mandatory actions from the user audit.
3. Resolves `OQ-SDD-BRD-H-002`: no `ScOutboxDispatchReadPort` exists after MU-024.
4. Resolves `OQ-SDD-BRD-H-003`: `SIGNAL` is a `DeliveryLane`, not a `ScBusLane`; the bridge projector must decide explicit mapping and MUST NOT map `SIGNAL` to `COMMAND`.
5. Refines the Flyway/migration conclusion: SC-B migrations MUST NOT collide with SC-C-owned `V1`–`V4`; a separate SC-B location or explicit prefix/high-number stream must be selected before the first SC-B persistence MIR executes.
6. Corrects test accounting to the post-patch baseline: `SC-C 240 + Bus 54 = 294` for the `sovereign-connect` module; `EIB 56` remains green.
7. Records that H2 and H3 are authorized as part of the hardening track but not recommended as the immediate first MIR.
8. Keeps NATS/JetStream and physical binding out of scope.

---

## 0. Purpose

This CSA audits the post-MU-024 implementation surface against:

```text
PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate / Validated L4
```

The audit determines whether the next implementation descent should open:

```text
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
```

and whether any of those can safely be combined.

---

## 1. Executive verdict

```text
Verdict: Approvable to open MIR descent for the hardening track.
Immediate next MIR: MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001.
H1 status: MIR-ready.
H2 status: planned / not recommended as immediate first MIR.
H3 status: planned / not recommended as immediate first MIR.
```

The code surface is clean after MU-024:

```text
bus.** imports core.**:           none confirmed
core.** imports bus.runtime.**:   none confirmed
broker dependency in bus.**:      none confirmed
integration.scledgerdispatch:     absent; must be created by H2
ScOutboxDispatchReadPort:         absent; must be introduced by H2
bus runtime persistence package:  absent; must be introduced by H1
```

Validation evidence available in the supplied archive:

```text
sovereign-connect module:
  SC-C / non-bus baseline: 240 tests, 0 failures, 0 errors, 0 skipped
  Bus delta:               54 tests, 0 failures, 0 errors, 0 skipped
  Total:                   294 tests, 0 failures, 0 errors, 0 skipped

EIB module:
  56 tests, 0 failures, 0 errors, 0 skipped
```

Maven was not re-executed in the audit environment when unavailable; this CSA relies on supplied Surefire reports plus code inspection.

---

## 2. Completeness level

```text
CSA-C3 — Change-impact-complete
```

All SDD §13 CSA targets were inspected or reconciled from the two CSA inputs.

---

## 3. Full surface inventory

### 3.1 Bus package tree

Current package tree:

```text
com.sovereign.connect.bus.contract
com.sovereign.connect.bus.runtime.dispatch
com.sovereign.connect.bus.runtime.dispatch.model
com.sovereign.connect.bus.runtime.port
com.sovereign.connect.bus.runtime.inmemory
com.sovereign.connect.bus.runtime.validation
```

Absent packages:

```text
com.sovereign.connect.bus.runtime.persistence
com.sovereign.connect.integration.scledgerdispatch
```

Implication:

```text
H1 creates SC-B runtime persistence.
H2 creates the integration bridge boundary.
```

---

### 3.2 RuntimeDispatchService API

Confirmed public surface:

```java
List<DispatchOutcome> dispatchPending()
DispatchOutcome dispatch(DispatchCandidate candidate)
DispatchAttempt scheduleRetry(UUID dispatchRecordId)
DispatchAttempt exhaust(UUID dispatchRecordId)
DispatchAttempt cancelBySupersede(UUID dispatchRecordId, String evidenceRef)
```

The dispatcher currently depends on abstract ports and in-memory seed implementations. It is suitable for H1 persistence replacement.

Current dispatch switch is over `ScBusLane`, not over `DeliveryLane`.

Observed `ScBusLane` values:

```text
COMMAND
EVENT
RESPONSE
INTERNAL_CONTROL
```

Observed switch behavior:

```java
case COMMAND  -> publishCommand(...)
case EVENT    -> publishEvent(...)
case RESPONSE -> publishResponse(...)
case INTERNAL_CONTROL -> new Failed(..., "UNSUPPORTED_LANE", ..., false)
```

`SIGNAL` is absent because `SIGNAL` belongs to `DeliveryLane`, not `ScBusLane`. This is not a dispatcher defect. The future bridge projector must map or reject `DeliveryLane.SIGNAL` before producing a `DispatchCandidate`.

---

### 3.3 DispatchCandidate shape

Confirmed shape:

```java
record DispatchCandidate(
    UUID dispatchRecordId,
    UUID sourceRecordId,
    ScBusLane lane,
    String logicalTopic,
    String partitionKey,
    UUID correlationId,
    UUID causationId,
    UUID messageId
)
```

Interpretation:

```text
DispatchCandidate is a transient SC-B projection carrier.
It is not OutboxEntry.
It is not ScEnvelope.
It is not the durable dispatch record.
```

The bridge should populate `sourceRecordId` from `OutboxEntry.outboxEntryId`.

Fields likely needed by durable H1/H2/H3 records but absent from `DispatchCandidate`:

```text
habitatId
sourceRecordKind / outboundKind
ledgerEntryId
createdAt / updatedAt
nextRetryAt
exhaustedAt
lastErrorCode
lastSanitizedReason
```

These fields should appear in persistent record classes or bridge projection records, not necessarily in `DispatchCandidate` itself.

---

### 3.4 DispatchStateWritePort

Confirmed port:

```java
DispatchAttempt claim(UUID dispatchRecordId)
DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState)
DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef)
Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId)
```

H1 can replace the in-memory implementation with a persistent adapter without changing the port.

---

### 3.5 DispatchCandidateReadPort

Confirmed port:

```java
List<DispatchCandidate> pendingCandidates()
```

This is sufficient for seed dispatch reading. H2 will implement this port from scledger through an integration adapter, likely backed by a new narrow SC-C read port.

---

### 3.6 DispatchObservationPort

Confirmed port:

```java
void record(DispatchObservationRecord observation)
List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId)
```

H3 can replace the in-memory observation repository with a persistent adapter.

Potential H3 gap:

```text
DispatchObservationRecord may need sourceRecordId, habitatId, lane and correlation context before production-grade observation persistence.
```

This should be decided in H3 MIR/SDD continuation or in H2 if bridge context is required.

---

### 3.7 InMemoryScBusPort

Confirmed properties:

```text
- synchronous;
- no executor;
- no async runtime;
- no broker;
- validates envelope/lane/family;
- dispatches to handlers by topic;
- returns Dispatched, NoHandler or Failed;
- wraps handler exceptions as Failed.
```

No NATS/JetStream leakage detected.

---

### 3.8 DeliveryLane and usages

Confirmed enum:

```java
enum DeliveryLane { SIGNAL, COMMAND, EVENT, RESPONSE }
```

Production usages:

```text
TemporalEngineService.java — DeliveryLane.SIGNAL for outbox write
OutboxEntry.java           — field only
```

No exhaustive production switch over `DeliveryLane` exists.

Implication:

```text
The first exhaustive DeliveryLane switch will likely be introduced by H2 bridge mapping.
That switch MUST handle all four values explicitly.
```

Critical bridge rule:

```text
DeliveryLane.SIGNAL MUST NOT map to ScBusLane.COMMAND in the outbox bridge seed.
```

---

### 3.9 scledger model

Confirmed `OutboxEntry` fields:

```java
UUID outboxEntryId
UUID ledgerEntryId
String habitatId
OutboundKind outboundKind
DeliveryLane deliveryLane
String logicalTopic
String semanticPayloadJson
String notificationTargetRef
String idempotencyKey
OutboxEntryStatus status
Instant createdAt
Instant updatedAt
String metadataJson
```

Confirmed `OutboxEntryStatus`:

```text
PENDING
CLAIMED
DISPATCHED
DISPATCH_FAILED
RETRY_WAIT
DEAD_LETTERED
SUPPRESSED
```

Confirmed `LedgerRecordClass`:

```text
LEDGER_ONLY
EVENT_OUTBOX
COMMAND_OUTBOX
RESPONSE_OUTBOX
DIAGNOSTIC_OUTBOX
DELIVERY_OBSERVATION
```

Important invariant:

```text
OutboxEntryStatus != DispatchState.
LedgerRecordClass DELIVERY_OBSERVATION anticipates observations but does not implement SC-B observation persistence by itself.
```

---

### 3.10 scledger outbox read port

No suitable outbox read port exists.

Confirmed existing ports:

```text
ScLedgerWritePort
ScOutboxWritePort
```

Both are write-only.

Implication:

```text
H2 must introduce ScOutboxDispatchReadPort under core.scledger.port.
```

This is a SC-C scledger boundary port. It must not be placed under `bus.*` or `integration.*`.

---

### 3.11 OutboxEntry codec helpers

No codec helper exists for `OutboxEntry.semanticPayloadJson`.

Current state:

```text
semanticPayloadJson is raw JSON String.
No ObjectMapper/helper exists in scledger model.
No bus envelope exists inside OutboxEntry.
```

Implication:

```text
H2 must not pretend semanticPayloadJson is already ScEnvelope.
The bridge may parse only what its explicit projection profile allows.
```

---

### 3.12 Persistence framework and migrations

Confirmed dependencies:

```text
spring-boot-starter-jdbc
sqlite-jdbc
flyway-core
flyway-database-nc-sqlite
h2
```

Existing migrations:

```text
src/main/resources/db/migration/V1__sc_c_base_schema.sql
src/main/resources/db/migration/V2__sc_c_temporal_engine.sql
src/main/resources/db/migration/V3__sc_c_ledger_outbox_sqlite.sql
src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql
```

All `V1`–`V4` are SC-C-owned.

Merged CSA conclusion:

```text
SC-B migrations MUST NOT collide with SC-C Flyway version ownership.
```

Preferred strategies:

```text
Option A — separate SC-B migration location:
  src/main/resources/db/migration/sc-b/
    SB1__create_sc_b_dispatch_records.sql
    SB2__create_sc_b_dispatch_attempts.sql
    SB3__create_sc_b_dispatch_observations.sql

Option B — single Flyway stream with explicit high-number SC-B offset:
  V100__sc_b_dispatch_records.sql
  V101__sc_b_dispatch_attempts.sql
  V102__sc_b_dispatch_observations.sql
```

The MIR must choose one strategy and explain Flyway configuration/test impact. Do not use `V5` for SC-B unless governance explicitly reserves a shared global migration sequence, because `V5` is the natural next SC-C migration slot.

---

### 3.13 Architecture test style

Confirmed style:

```text
file-walk + Files.readString + AssertJ
```

No ArchUnit dependency is present or required.

H1/H2/H3 should follow this style.

---

### 3.14 Module layout and integration boundary

No `integration.*` package exists.

H2 should create:

```text
com.sovereign.connect.integration.scledgerdispatch
```

Allowed H2 integration imports:

```text
com.sovereign.connect.core.scledger.model.*
com.sovereign.connect.core.scledger.port.*
com.sovereign.connect.bus.contract.*
com.sovereign.connect.bus.runtime.dispatch.*
com.sovereign.connect.bus.runtime.dispatch.model.*
com.sovereign.connect.bus.runtime.port.*
```

Forbidden H2 integration imports:

```text
com.sovereign.connect.core.topology.*
com.sovereign.connect.core.temporal.*
com.sovereign.connect.adapter.*
NATS / JetStream / broker APIs
```

---

### 3.15 Boundary violations

Confirmed:

```text
bus.** imports core.**:           none
core.** imports bus.runtime.**:   none
broker imports/dependencies:      none
```

The bridge package will be the only permitted boundary adapter that imports both narrow scledger surfaces and bus runtime surfaces.

---

## 4. Mandatory actions

### ACTION-H-001 — SIGNAL lane handling in bridge projector

The future bridge `DeliveryLane -> ScBusLane` mapping must explicitly handle `DeliveryLane.SIGNAL`.

Per `R-BRIDGE-H-SIGNAL-001`:

```text
DeliveryLane.SIGNAL MUST NOT map to ScBusLane.COMMAND.
```

Recommended bridge seed behavior:

```text
SIGNAL -> EVENT only for explicitly scoped seed signal handlers;
otherwise SIGNAL -> non-dispatchable / skipped / observed as unsupported.
```

The MIR must make this an explicit decision and add tests.

Required test theme:

```text
signalLaneIsNotRoutedAsCommand()
```

The test must prove that `DeliveryLane.SIGNAL` does not produce `ScCommandEnvelope` and does not dispatch to `ScBusLane.COMMAND`.

---

### ACTION-H-002 — ScOutboxDispatchReadPort must be created

No outbox read port exists.

H2 must introduce:

```java
package com.sovereign.connect.core.scledger.port;

public interface ScOutboxDispatchReadPort {
    List<OutboxEntry> findDispatchableEntries(int limit);
}
```

Rules:

```text
- belongs in core.scledger.port;
- is a narrow SC-C-owned scledger boundary port;
- must not be placed in bus.* or integration.*;
- must only expose dispatchable outbox records needed by bridge projection;
- should query PENDING entries with eligible delivery lanes;
- must not mutate OutboxEntryStatus in the first bridge seed unless a later MIR explicitly scopes claim/writeback.
```

Default placement timing:

```text
Introduce in H2, not H1.
```

---

### ACTION-H-003 — integration.scledgerdispatch package must be created

H2 must create:

```text
com.sovereign.connect.integration.scledgerdispatch
```

Purpose:

```text
Host the bridge adapter that reads SC-C scledger outbox records through a narrow read port and projects them into SC-B DispatchCandidate records.
```

This package is the only layer allowed to import both:

```text
core.scledger.*
bus.runtime.*
```

Required architecture test theme:

```text
integrationPackageIsOnlyLayerThatImportsBothScledgerAndBusRuntime()
```

---

### ACTION-H-004 — SC-B migrations must not collide with SC-C Flyway V1–V4

H1 introduces the first SC-B persistence tables. Therefore H1 must decide the SC-B migration strategy.

Allowed strategies:

```text
A. Separate SC-B migration location and prefix.
B. Single stream with high-number SC-B offset.
```

Recommended default:

```text
Option A if project configuration accepts a second Flyway location cleanly.
Option B if avoiding Flyway configuration changes is safer for the seed.
```

Rejected by this CSA:

```text
Unqualified V5__sc_b_*.sql in the shared SC-C migration stream.
```

Required architecture/test theme:

```text
scBMigrationsDoNotCollideWithScCVersionNumbers()
```

---

## 5. OQ resolutions

### OQ-SDD-BRD-H-002 — Outbox read port existence

Question:

```text
Does a suitable outbox read port already exist after MU-024?
```

Resolution:

```text
No.
```

Only `ScLedgerWritePort` and `ScOutboxWritePort` exist, and both are write-only.

Disposition:

```text
H2 must introduce ScOutboxDispatchReadPort in core.scledger.port.
```

---

### OQ-SDD-BRD-H-003 — SIGNAL dispatchability

Question:

```text
Should DeliveryLane.SIGNAL project to ScBusLane.EVENT, or remain non-dispatchable until a signal-specific rule is formalized?
```

Resolution:

```text
The current dispatcher switch is over ScBusLane, not DeliveryLane.
SIGNAL is not dispatchable by RuntimeDispatchService directly.
The bridge projector must explicitly decide SIGNAL mapping.
```

Recommended seed decision:

```text
SIGNAL -> EVENT only for explicitly scoped seed signal handlers;
SIGNAL -> non-dispatchable by default;
SIGNAL -> COMMAND forbidden.
```

---

## 6. H1/H2/H3 sequencing decision

### 6.1 H1 — Dispatch State Persistence

```text
Status: MIR-ready
Recommended next artifact: MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
```

Why H1 is ready:

```text
- DispatchStateWritePort exists.
- InMemoryDispatchStateRepository isolates current gap.
- DispatchState machine is implemented and tested.
- No SC-C imports are required.
- JDBC/SQLite/Flyway infrastructure exists.
- Architecture test style exists.
```

Recommended H1 implementation surface:

```text
com.sovereign.connect.bus.runtime.persistence
```

or:

```text
com.sovereign.connect.bus.runtime.dispatch.persistence
```

Do not place H1 persistence under:

```text
core.*
integration.scledgerdispatch
```

because H1 is pure SC-B technical dispatch state.

Recommended H1 acceptance themes:

```text
- persistent DispatchRecord equivalent exists;
- persistent DispatchAttemptRecord equivalent exists;
- claim/retry/exhaustion state survives repository/service recreation;
- restart-visible states are queryable;
- no OutboxEntryStatus mutation;
- no core imports into bus.**;
- no broker dependency;
- SC-B migration strategy does not collide with SC-C migrations;
- full regression remains green.
```

---

### 6.2 H2 — Outbox Bridge Seed

```text
Status: planned / not recommended as immediate first MIR
Recommended after: H1 validated, unless governance explicitly accepts concurrent read-only bridge descent
```

Reasons H2 is not the immediate first MIR:

```text
- No scledger outbox read port exists.
- integration.scledgerdispatch package does not exist.
- RuntimeDispatchService currently uses DispatchCandidate as seed payload surrogate.
- semanticPayloadJson is raw JSON with no codec helper.
- SIGNAL mapping requires explicit MIR decision.
```

Required H2 decisions before execution package:

```text
1. Introduce ScOutboxDispatchReadPort.
2. Define OutboxEntry -> DispatchCandidate / BridgeProjectionRecord mapping.
3. Define payload carrier strategy without inventing productive ScdCommand.
4. Enforce SIGNAL -> EVENT only under explicit seed scope or mark SIGNAL non-dispatchable.
5. Prohibit SIGNAL -> COMMAND.
6. Deduplicate by sourceRecordId without mutating OutboxEntryStatus.
7. Place bridge in integration.scledgerdispatch.
```

---

### 6.3 H3 — Dispatch Observation Persistence

```text
Status: planned / not recommended as immediate first MIR
Recommended after: H1; may run before or after H2 depending on observation-field scope
```

Reasons:

```text
- DispatchObservationPort exists.
- In-memory observation repository can be replaced.
- But observation fields may need bridge context such as sourceRecordId, habitatId and lane.
```

Safe H3 scope if opened after H1 but before H2:

```text
Persist purely technical dispatch observations keyed by dispatchRecordId and attemptId.
Do not claim bridge/sourceRecord observability until H2 introduces bridge projection.
```

---

## 7. Blocking / non-blocking issues

### BLOCKER-CSA-BRD-H-001 — H2 requires runtime payload strategy

`RuntimeDispatchService` currently builds envelopes whose payload is `DispatchCandidate`.

Disposition:

```text
Blocks H2 MIR.
Does not block H1 MIR.
```

Required resolution before H2:

```text
Define a payload carrier / envelope factory / bridge projection profile.
```

Rules:

```text
- Do not introduce productive ScdCommand.
- Do not treat OutboxEntry.semanticPayloadJson as ScEnvelope.
- Do not place payload semantics in bus.runtime persistence.
```

---

### BLOCKER-CSA-BRD-H-002 — H2 requires scledger outbox read surface

Only write ports exist for scledger/outbox.

Disposition:

```text
Blocks H2 MIR.
Does not block H1 MIR.
```

Required resolution before H2:

```text
Add narrow ScOutboxDispatchReadPort in SC-C scledger boundary.
```

---

### OBS-CSA-BRD-H-001 — Archive / branch hygiene

If the supplied archive does not exactly match the pushed PR tip, refresh the zip before execution package descent.

Disposition:

```text
Non-blocking for CSA.
MIR execution should start from a clean branch state.
```

Recommended check before MIR package generation:

```bash
git status --short
git rev-parse HEAD
git log --oneline -5
```

---

## 8. Surface inventory matrix

| Surface | SDD expectation | Current state | Action |
|---|---|---|---|
| `bus.runtime.persistence` package | New in H1 | Absent | Create in H1 |
| `JdbcDispatchStateRepository` | New in H1 | Absent | Create in H1 |
| `ScOutboxDispatchReadPort` | New in H2 | Absent | ACTION-H-002 |
| `integration.scledgerdispatch` | New in H2 | Absent | ACTION-H-003 |
| `OutboxEntryDispatchProjector` | New in H2 | Absent | Create in H2 |
| `JdbcDispatchCandidateReadPort` / bridge read adapter | New in H2 | Absent | Create in H2 |
| `JdbcDispatchObservationRepository` | New in H3 | Absent | Create in H3 |
| SC-B Flyway migrations | Separate from SC-C | Absent | ACTION-H-004 |
| SIGNAL lane bridge rule | Explicit in H2 | No bridge yet | ACTION-H-001 |
| Architecture tests, H1 | Required | Absent | Add in H1 |
| Architecture tests, H2 bridge | Required | Absent | Add in H2 |
| `bus.**` imports `core.**` | Forbidden | None | Preserve |
| `core.**` imports `bus.runtime.**` | Forbidden | None | Preserve |
| Broker dependency | Forbidden | None | Preserve |
| `DispatchOutcome` sealed | Required | Present | Preserve |
| DeliveryLane switch safety | No existing exhaustive switch | Confirmed | First bridge switch must be explicit |

---

## 9. Required architecture tests for next MIRs

### 9.1 H1 architecture tests

H1 must prove:

```text
bus runtime persistence imports no core.*
bus runtime persistence imports no NATS/JetStream/broker APIs
SC-B migrations do not collide with SC-C-owned migration versions
DispatchState persistence does not mutate OutboxEntryStatus
```

### 9.2 H2 architecture tests

H2 must prove:

```text
core.** does not import bus.runtime.**
bus.** does not import core.**
integration.scledgerdispatch is the only package importing both narrow scledger and bus runtime surfaces
integration.scledgerdispatch does not import core.topology or core.temporal
SIGNAL does not route to COMMAND
```

### 9.3 H3 architecture tests

H3 must prove:

```text
observation persistence remains SC-B technical observation
no broker dependency appears
observation records do not become SC-C semantic terminal state
```

---

## 10. Search ledger

```text
RuntimeDispatchService.java
  - switch: COMMAND/EVENT/RESPONSE/INTERNAL_CONTROL only
  - switch is over ScBusLane, not DeliveryLane
  - SIGNAL absent because SIGNAL is not a ScBusLane
  - boundary: imports bus.contract.*, bus.runtime.* only

core.scledger.port
  - ScLedgerWritePort: write-only
  - ScOutboxWritePort: write-only
  - ScOutboxDispatchReadPort: absent

core.scledger.model
  - OutboxEntry: source payload carrier, raw semanticPayloadJson String
  - OutboxEntryStatus: distinct from DispatchState
  - DeliveryLane: SIGNAL, COMMAND, EVENT, RESPONSE
  - LedgerRecordClass: includes RESPONSE_OUTBOX and DELIVERY_OBSERVATION

bus.runtime.dispatch.model
  - DispatchCandidate: transient projection carrier
  - DispatchOutcome: sealed, permits Dispatched, Failed, NoHandler
  - Dispatched: dispatchRecordId, attemptId, topic, partitionKey
  - Failed: dispatchRecordId, attemptId, code, sanitizedReason, retryable
  - NoHandler: dispatchRecordId, attemptId, topic, sanitizedReason

Flyway
  - V1–V4 all SC-C-owned
  - no SC-B migration files
  - migration strategy must be selected before H1 implementation

Architecture
  - bus -> core imports: none
  - core -> bus.runtime imports: none
  - broker imports: none
  - integration package: absent
```

---

## 11. Recommended next artifact

```text
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
```

Recommended branch:

```text
feat/sc-b-mir-025-dispatch-state-persistence
```

Recommended commit:

```text
feat(sc-b): persist runtime dispatch state
```

Minimum MIR scope:

```text
- persistent DispatchRecord / DispatchAttemptRecord or equivalent;
- JDBC/SQLite adapter for DispatchStateWritePort;
- migration strategy selected without SC-C collision;
- restart visibility tests;
- claim/retry/exhaustion persistence tests;
- architecture tests preserving bus/core/broker boundaries;
- no outbox bridge;
- no observation persistence beyond what H1 requires;
- no NATS/JetStream;
- no mutation of OutboxEntryStatus.
```

---

## 12. Final dictum

```text
CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged
is accepted as the post-SDD / pre-MIR code surface audit for SC-B runtime dispatch hardening.

The hardening track may open MIR descent.

The immediate next MIR is:
  MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001.

H2 and H3 remain valid downstream hardening MUs but should not be collapsed into H1.

NATS / JetStream binding remains blocked until runtime dispatch hardening and serialization/lifecycle gates are satisfied.
```
