# MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001

## Materialization Increment Record — SC-B Dispatch State Persistence

```text
Document ID:  MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
Title:        Materialization Increment Record — SC-B Dispatch State Persistence
Version:      v0.2.0-candidate
Status:       Candidate / Accepted for execution package descent
Date:         2026-05-29
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-B
MU ID:        MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001
MU Slot:      MU-025
Track:        SC-B Runtime Dispatch Hardening / H1
```

---

## 0. MIR boundary

This MIR authorizes review of the first SC-B runtime dispatch hardening increment after `MU-SOV-SC-B-ABSTRACT-BUS-SEED-001`.

It does not include an execution prompt, implementation context, acceptance map or Codex instructions. Those artifacts must be produced separately under the execution package if this MIR is promoted to candidate.

This MIR is not a NATS binding. It is not a JetStream binding. It is not the SC-C outbox bridge. It is not dispatch observation persistence. It is not SC-D lifecycle or adapter onboarding.

This MIR hardens only the technical dispatch state persistence boundary that is currently in-memory after MU-024.

---

## 0.1 Changelog v0.2.0-candidate

Promotes `v0.1.0-draft` to candidate after MIR review.

This version:

1. Clarifies claim semantics: `claim(dispatchRecordId)` creates the first `CLAIMED` attempt when no record exists, explicitly allows claim from `PENDING`, explicitly allows claim from `RETRY_SCHEDULED`, and rejects all other states.
2. Removes the ambiguous conditional “if PENDING is explicitly represented”.
3. Adds optional nullable `sourceRecordId` to the H1 schema recommendation as an opaque future bridge correlation field.
4. Clarifies that `sourceRecordId` may be persisted in H1 but MUST NOT be populated from, read through, or joined to `OutboxEntry` in H1.
5. Preserves H1 scope: dispatch state persistence only; no outbox bridge, no `ScOutboxDispatchReadPort`, no `integration.scledgerdispatch`, no NATS/JetStream.

## 0.2 Changelog v0.1.0-draft

Initial draft.

This version:

1. Opens `MU-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001` as `MU-025`.
2. Descends from `PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate` and `SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate`.
3. Consumes `CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged` as the pre-MIR code surface baseline.
4. Scopes the increment to H1 — dispatch state persistence only.
5. Introduces a persistent `DispatchStateWritePort` adapter for SC-B technical dispatch state.
6. Preserves `RuntimeDispatchService`, `DispatchState`, `DispatchAttempt` and the MU-024 state machine semantics.
7. Preserves `OutboxEntry != DispatchCandidate != ScEnvelope`.
8. Explicitly excludes the SC-C outbox bridge, `ScOutboxDispatchReadPort`, `integration.scledgerdispatch`, dispatch observation persistence and NATS/JetStream.
9. Requires SC-B migration separation from SC-C-owned Flyway `V1`–`V4`.
10. Requires restart-visibility tests for claim, retry, exhaustion and supersede cancellation.

---

## 1. Depends on

```text
Sovereign Connect — Curator & Descent Instructions
PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged
PDR-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
SDD-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v1.0.0-accepted / Validated L4
ADR-SOV-SC-SERIALIZATION-001 v0.2.0-candidate
PDR-SOV-SC-BUS-CONTRACT-001 v0.4.6-draft
PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.24-draft
```

---

## 2. Related / downstream

```text
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001 v0.2.0-candidate
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
TCK-SOV-SC-BUS-CONTRACT-001
```

---

# 3. Purpose

The purpose of this MIR is to replace the in-memory SC-B dispatch state repository with restart-visible, JDBC-backed technical dispatch state persistence.

After MU-024, SC-B has the abstract bus substrate:

```text
ScCommandEnvelope / ScEventEnvelope / ScResponseEnvelope
ScMessageMetadata
ScRoutingKey
ScBusPort
InMemoryScBusPort
RuntimeDispatchService
DispatchState
DispatchOutcome
validation services
```

However, dispatch state still lives in an in-memory `ConcurrentHashMap` implementation. That is acceptable for the abstract seed, but not for an industrial runtime path.

This MIR hardens the following surface:

```text
DispatchStateWritePort
DispatchAttempt lifecycle
claim state
retry scheduling state
exhaustion state
supersede cancellation state
restart visibility of technical dispatch state
```

This MIR does not connect SC-C outbox records to SC-B. That is H2 and belongs to `MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001`.

---

# 4. Baseline state

The pre-MIR CSA records the post-MU-024 baseline as:

```text
sovereign-connect module:
  SC-C / non-bus baseline: 240 tests, 0 failures, 0 errors, 0 skipped
  Bus delta:               54 tests, 0 failures, 0 errors, 0 skipped
  Total:                   294 tests, 0 failures, 0 errors, 0 skipped

EIB module:
  56 tests, 0 failures, 0 errors, 0 skipped
```

Current code properties to preserve:

```text
bus.** imports core.**:           none
core.** imports bus.runtime.**:   none
broker dependency in bus.**:      none
integration.scledgerdispatch:     absent
ScOutboxDispatchReadPort:         absent
bus.runtime.persistence:          absent
```

Current H1-relevant surfaces:

```text
DispatchStateWritePort exists.
InMemoryDispatchStateRepository implements DispatchStateWritePort.
DispatchAttempt is a record with attemptId, dispatchRecordId, attemptNumber, state, claimedAt.
DispatchState contains PENDING, CLAIMED, DISPATCHING, DISPATCHED,
DELIVERY_FAILED, RETRY_SCHEDULED, EXHAUSTED, CANCELLED_BY_SUPERSEDE.
RuntimeDispatchService already depends on DispatchStateWritePort.
```

CSA conclusion:

```text
H1 is MIR-ready.
H2 and H3 are planned but not recommended as the immediate first MIR.
```

---

# 5. Problem statement

`InMemoryDispatchStateRepository` currently records technical dispatch state in process memory.

That means:

```text
- claimed dispatch attempts are lost on restart;
- retry/exhaustion/cancel state is not restart-visible;
- the runtime cannot distinguish post-restart pending vs lost technical state;
- future outbox bridge work would have no durable SC-B dispatch state substrate;
- a future physical broker could be incorrectly treated as the first durable runtime state.
```

The hardening track must prevent that outcome before NATS/JetStream work descends.

---

# 6. Goals

## G-MIR-025-001 — Persistent dispatch state adapter

Create a persistent adapter for `DispatchStateWritePort` under the SC-B runtime persistence boundary.

## G-MIR-025-002 — Preserve existing state machine

Preserve the MU-024 allowed transitions and rejection rules.

## G-MIR-025-003 — Restart-visible current attempt

After repository/service recreation, `currentAttempt(dispatchRecordId)` must return the last technical dispatch state.

## G-MIR-025-004 — Retry attempt continuity

Retry scheduling and re-claim must preserve attempt-number continuity across repository recreation.

## G-MIR-025-005 — Terminal technical states remain terminal

`EXHAUSTED` and `CANCELLED_BY_SUPERSEDE` must remain visible and must not become claimable after restart.

## G-MIR-025-006 — Supersede evidence persistence

Supersede cancellation must require non-blank evidence and persist it in SC-B technical state.

## G-MIR-025-007 — Migration separation

SC-B persistence migrations must not collide with SC-C-owned Flyway `V1`–`V4`.

## G-MIR-025-008 — Boundary preservation

`bus.**` must not import `core.**`; `core.**` must not import `bus.runtime.**`; no broker dependencies may be introduced.

---

# 7. Non-goals

This MIR does not authorize:

```text
- SC-C outbox bridge;
- ScOutboxDispatchReadPort;
- integration.scledgerdispatch package;
- OutboxEntry -> DispatchCandidate projection;
- DispatchCandidate payload/envelope projection hardening;
- dispatch observation persistence;
- ScDeliveryError persistence;
- DeliveryLane -> ScBusLane bridge mapping;
- SIGNAL lane bridge handling;
- lifecycle channel implementation;
- adapter announce/challenge/registration;
- ScdCommand production shape;
- discovery/state/health fact family production shapes;
- NATS Core;
- JetStream;
- NATS subjects;
- serialization implementation;
- physical broker client dependencies;
- mutation of SC-C OutboxEntryStatus;
- semantic retry;
- terminal request state ownership.
```

---

# 8. Design decisions

## D-MIR-025-001 — H1 only

This MIR implements H1 only: dispatch state persistence.

It must not implement H2 outbox bridge or H3 observation persistence.

## D-MIR-025-002 — SC-B owns technical dispatch state

Persistent dispatch state belongs to SC-B and is technical state only.

It must not be interpreted as:

```text
semantic success
semantic failure
adapter acceptance
provider acceptance
device effect verification
SC-C terminal request state
```

## D-MIR-025-003 — Existing DispatchStateWritePort remains the primary port

H1 must implement the existing `DispatchStateWritePort` contract:

```java
DispatchAttempt claim(UUID dispatchRecordId)
DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState)
DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef)
Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId)
```

The MIR does not require changing this port.

If execution discovers that a minimal additive helper is unavoidable, it must be justified in the implementation report and must not import `core.*` into `bus.*`.

## D-MIR-025-004 — H1 persists current dispatch attempt state, not full outbox metadata

The current `DispatchStateWritePort` receives `dispatchRecordId`, not `DispatchCandidate` or `OutboxEntry`.

Therefore H1 is scoped to persist the technical dispatch attempt/current-state surface that the port owns today.

Full durable source metadata such as:

```text
habitatId
sourceRecordKind / outboundKind
ledgerEntryId
semanticPayloadJson
notificationTargetRef
```

belongs to H2 bridge or later durable dispatch-record enrichment, where `OutboxEntry -> DispatchCandidate` projection is actually available.

H1 may create a minimal `sc_b_dispatch_records` table keyed by `dispatchRecordId`, but it must not invent SC-C source metadata.

H1 MAY include a nullable `sourceRecordId` column as an opaque future bridge correlation field. This is allowed only as schema preparation for H2. H1 MUST NOT populate it from `OutboxEntry`, read SC-C outbox rows, join against `core.scledger`, or treat `sourceRecordId` as semantic authority. H2 may later populate it with `OutboxEntry.outboxEntryId` when the bridge is explicitly in scope.

## D-MIR-025-005 — Persistent adapter package

Create a new package:

```text
com.sovereign.connect.bus.runtime.persistence
```

Expected adapter:

```text
JdbcDispatchStateRepository
```

Equivalent naming is allowed if mapped in the implementation report.

## D-MIR-025-006 — JDBC/Flyway profile

Use JDBC/Flyway-compatible persistence consistent with the existing SC-C storage profile.

JPA is not required and should not be introduced by this MIR.

## D-MIR-025-007 — SC-B migration numbering must avoid SC-C collision

SC-C currently owns Flyway `V1`–`V4`.

H1 must use one of the CSA-approved strategies:

```text
Option A — separate SC-B migration location, e.g. db/migration/sc-b/
Option B — single-stream high-number offset, e.g. V100__sc_b_dispatch_state.sql
```

Default for the H1 execution package:

```text
Use Option B unless the active repository already has a dedicated multi-location Flyway strategy.
Reserve V5–V99 for SC-C.
Use V100+ for SC-B seed/hardening migrations.
```

This decision is seed/hardening-scoped and may be revisited by a later storage governance artifact if SC-B becomes a separate deployable/runtime module.

## D-MIR-025-008 — Minimal schema is acceptable for H1

H1 may implement a minimal schema sufficient to persist:

```text
dispatchRecordId
sourceRecordId, nullable and opaque, optional schema-preparation field for H2
attemptId
attemptNumber
state
claimedAt
updatedAt
supersessionEvidenceRef, nullable
```

and attempt history.

`sourceRecordId`, if included, is not an outbox bridge implementation. It is an optional nullable reference slot reserved for the later H2 bridge. H1 must not resolve, validate, derive, query or mutate any SC-C outbox record through this field.

If a broader schema is introduced, it must not force SC-C outbox bridge semantics into H1.

## D-MIR-025-009 — No SC-C outbox mutation

H1 must not read or mutate `OutboxEntry`, `OutboxEntryStatus`, `LedgerEntry` or any `core.scledger` type.

H1 must not introduce `ScOutboxDispatchReadPort`.

## D-MIR-025-010 — In-memory implementation preserved

`InMemoryDispatchStateRepository` may remain for unit tests and seed profiles.

H1 adds a persistent alternative; it does not need to delete the in-memory repository.

## D-MIR-025-011 — Observation persistence remains deferred

`DispatchObservationPort` remains in-memory in this MIR unless tests require an existing in-memory implementation.

Persistent observation storage belongs to H3.

## D-MIR-025-012 — No broker dependency

No NATS, JetStream, Redis, Vert.x, MQTT, Kafka, gRPC or broker client dependency may be added by this MIR.

---

# 9. Expected implementation surface

Expected production surface:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepository.java
src/main/resources/db/migration/V100__sc_b_dispatch_state_persistence.sql
```

Optional production surface, if the implementation chooses separated records:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/DispatchStateRow.java
src/main/java/com/sovereign/connect/bus/runtime/persistence/DispatchAttemptRow.java
```

Expected test surface:

```text
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchStateRepositoryTest.java
src/test/java/com/sovereign/connect/bus/ScBusHardeningArchitectureTest.java
```

Equivalent names are acceptable if mapped to the acceptance criteria in the implementation report.

---

# 10. Required behavior

## 10.1 Claim behavior

`claim(dispatchRecordId)` must:

```text
- reject null dispatchRecordId;
- create the first CLAIMED attempt when no record exists for dispatchRecordId, treating PENDING as implicit initial state;
- allow claim from PENDING explicitly when PENDING is represented as the current persisted state;
- allow claim from RETRY_SCHEDULED;
- reject claim from all other states: CLAIMED, DISPATCHING, DISPATCHED, DELIVERY_FAILED, EXHAUSTED and CANCELLED_BY_SUPERSEDE;
- set attemptNumber = 1 for the first CLAIMED attempt;
- increment attemptNumber when claiming after RETRY_SCHEDULED;
- persist the new current attempt.
```

## 10.2 Transition behavior

`transition(dispatchRecordId, targetState)` must:

```text
- reject null dispatchRecordId;
- reject null targetState;
- reject CANCELLED_BY_SUPERSEDE without evidenceRef;
- preserve MU-024 allowed transitions;
- reject forbidden transitions;
- persist the target state;
- preserve the current attemptId during transition.
```

## 10.3 Supersede cancellation behavior

`transitionWithEvidence(dispatchRecordId, CANCELLED_BY_SUPERSEDE, evidenceRef)` must:

```text
- reject null or blank evidenceRef;
- permit cancellation only from PENDING, CLAIMED or RETRY_SCHEDULED;
- persist CANCELLED_BY_SUPERSEDE;
- persist supersessionEvidenceRef;
- keep the state terminal after restart.
```

## 10.4 Current attempt behavior

`currentAttempt(dispatchRecordId)` must:

```text
- return Optional.empty() for unknown dispatchRecordId;
- return the persisted current attempt for known dispatchRecordId;
- preserve attemptId, dispatchRecordId, attemptNumber, state and claimedAt across repository recreation.
```

## 10.5 Restart behavior

The persistent repository must prove restart visibility by constructing a new repository instance over the same database and observing:

```text
CLAIMED remains CLAIMED;
DISPATCHING remains DISPATCHING;
DELIVERY_FAILED remains DELIVERY_FAILED;
RETRY_SCHEDULED remains RETRY_SCHEDULED;
EXHAUSTED remains EXHAUSTED;
CANCELLED_BY_SUPERSEDE remains CANCELLED_BY_SUPERSEDE;
attemptNumber continuity survives retry after recreation.
```

## 10.6 Migration behavior

The SC-B migration must:

```text
- create only sc_b_* tables;
- not alter SC-C tables;
- not reuse SC-C-owned V1–V4 numbers;
- be covered by an architecture or migration-naming test;
- be idempotent through Flyway semantics, not manual CREATE retry logic.
```

---

# 11. Required tests

At minimum, the implementation must add tests for:

```text
1. first claim is persisted;
2. currentAttempt survives repository recreation;
3. CLAIMED -> DISPATCHING -> DISPATCHED survives restart;
4. DISPATCHING -> DELIVERY_FAILED survives restart;
5. DELIVERY_FAILED -> RETRY_SCHEDULED -> CLAIMED increments attemptNumber;
6. retry attemptNumber continuity survives repository recreation;
7. EXHAUSTED is persistent and not claimable;
8. CANCELLED_BY_SUPERSEDE requires evidenceRef;
9. CANCELLED_BY_SUPERSEDE persists evidenceRef or equivalent diagnostic field;
10. invalid transitions are rejected;
11. unknown currentAttempt returns Optional.empty();
12. SC-B migrations do not collide with SC-C V1–V4;
13. bus.** still does not import core.**;
14. core.** still does not import bus.runtime.**;
15. no broker dependency is introduced.
```

Recommended minimum test delta:

```text
+12 tests
```

The final implementation report must record the actual test delta.

---

# 12. Acceptance criteria

## 12.1 Scope and boundaries

```text
AC-025-001 — MIR implements H1 dispatch state persistence only.
AC-025-002 — MIR does not implement outbox bridge, ScOutboxDispatchReadPort or integration.scledgerdispatch.
AC-025-003 — MIR does not implement dispatch observation persistence.
AC-025-004 — MIR does not introduce NATS, JetStream or broker dependencies.
AC-025-005 — bus.** does not import core.**.
AC-025-006 — core.** does not import bus.runtime.**.
```

## 12.2 Persistent adapter

```text
AC-025-007 — A persistent adapter for DispatchStateWritePort exists.
AC-025-008 — The persistent adapter supports claim(...), transition(...), transitionWithEvidence(...) and currentAttempt(...).
AC-025-009 — InMemoryDispatchStateRepository remains usable for seed/unit-test profiles.
AC-025-010 — Persistent adapter uses JDBC/Flyway-compatible storage without JPA.
```

## 12.3 State machine preservation

```text
AC-025-011 — Allowed MU-024 transitions remain allowed.
AC-025-012 — Forbidden MU-024 transitions remain rejected.
AC-025-013 — CLAIMED does not mean adapter acceptance.
AC-025-014 — DISPATCHED does not mean semantic success.
AC-025-015 — EXHAUSTED does not mean domain failure.
```

## 12.4 Restart visibility

```text
AC-025-016 — currentAttempt survives repository recreation.
AC-025-017 — CLAIMED/DISPATCHING/DELIVERY_FAILED/RETRY_SCHEDULED states are restart-visible.
AC-025-018 — EXHAUSTED remains terminal after restart.
AC-025-019 — CANCELLED_BY_SUPERSEDE remains terminal after restart.
AC-025-020 — Retry attemptNumber continuity survives repository recreation.
```

## 12.5 Supersede and retry evidence

```text
AC-025-021 — Supersede cancellation rejects null or blank evidenceRef.
AC-025-022 — Supersede cancellation persists evidenceRef or equivalent diagnostic field.
AC-025-023 — Retry from DELIVERY_FAILED to RETRY_SCHEDULED to CLAIMED creates a new attempt.
```

## 12.6 Migration and schema

```text
AC-025-024 — SC-B migration creates only sc_b_* tables.
AC-025-025 — SC-B migration does not collide with SC-C Flyway V1–V4.
AC-025-026 — Migration strategy is recorded in the implementation report, including whether nullable `sourceRecordId` was included as H2 schema preparation.
```

## 12.7 Evidence and reporting

```text
AC-025-027 — Implementation report records branch, commit hash and changed files.
AC-025-028 — Implementation report records test command and full test summary.
AC-025-029 — Implementation report maps every AC-025 criterion to tests, source files or rationale.
AC-025-030 — Implementation report preserves retained debts for H2 and H3.
```

---

# 13. Retained debt

The following debts remain explicitly open after H1:

```text
DEBT-BRD-H-001 — SC-C outbox bridge absent.
  Target: MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001.

DEBT-BRD-H-002 — ScOutboxDispatchReadPort absent.
  Target: H2 bridge MIR.

DEBT-BRD-H-003 — integration.scledgerdispatch absent.
  Target: H2 bridge MIR.

DEBT-BRD-H-004 — Dispatch observation persistence absent.
  Target: MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001.

DEBT-BRD-H-005 — SIGNAL lane bridge mapping unresolved for execution.
  Target: H2 bridge MIR; SIGNAL MUST NOT map to COMMAND.

DEBT-BRD-H-006 — Physical broker binding absent.
  Target: SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 after hardening gates.

DEBT-BRD-H-007 — SC-D lifecycle channel not implemented.
  Target: SDD/MIR descent from PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001.
```

---

# 14. Risks

## RISK-MIR-025-001 — Port overreach

The implementation may attempt to redesign `DispatchStateWritePort` or `RuntimeDispatchService` more broadly than H1 requires.

Mitigation:

```text
Preserve the existing port unless a minimal additive helper is unavoidable and documented.
```

## RISK-MIR-025-002 — SC-C coupling

The implementation may import `core.scledger` into `bus.runtime.persistence`.

Mitigation:

```text
Architecture tests must prove bus.** imports no core.**.
```

## RISK-MIR-025-003 — Migration collision

The implementation may introduce `V5__sc_b_*.sql`, colliding with future SC-C schema ownership.

Mitigation:

```text
Use V100+ or a separate SC-B migration location; add migration naming test.
```

## RISK-MIR-025-004 — State/semantic confusion

`DISPATCHED` or `EXHAUSTED` may be described as semantic outcome.

Mitigation:

```text
Tests and implementation report must preserve technical-only interpretation.
```

## RISK-MIR-025-005 — H2 leakage into H1

The implementation may attempt to introduce `ScOutboxDispatchReadPort` or bridge logic early.

Mitigation:

```text
H1 negative scope forbids outbox bridge surfaces.
```

---

# 15. Expected validation

Minimum expected validation:

```text
mvn -q test
```

Expected baseline:

```text
sovereign-connect module:
  previous total: 294 tests, 0 failures, 0 errors, 0 skipped
  expected total: >= 306 tests, 0 failures, 0 errors, 0 skipped

EIB module:
  expected to remain 56 tests, 0 failures, 0 errors, 0 skipped if run
```

If the EIB module is not run because H1 modifies only the `sovereign-connect` module, the implementation report must state this explicitly.

---

# 16. MIR acceptance checklist

This MIR may be promoted to candidate when:

```text
- PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate is accepted.
- SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate is accepted.
- CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged is accepted as the pre-MIR code surface reference.
- H1-only scope is accepted.
- Migration strategy constraints are accepted.
- No prompt/context is embedded in the MIR.
- Branch and commit suggestions are present.
```

---

# 17. Suggested branch and commit

Suggested branch:

```text
feat/sc-b-mir-025-dispatch-state-persistence
```

Suggested implementation commit:

```text
feat(sc-b): persist runtime dispatch state
```

Suggested evidence commit:

```text
docs(sc-b): record dispatch state persistence implementation
```

---

# 18. Final dictum

```text
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v0.1.0-draft
is opened for review.
```

This MIR is the first hardening step after MU-024.

It is intentionally narrower than the full SC-B runtime dispatch hardening track.

It creates restart-visible SC-B technical dispatch state, but it does not yet make the SC-C outbox visible to SC-B.

The qualitative system transition remains H2:

```text
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
```

H1 is necessary because the bridge should not land on an entirely in-memory dispatch state substrate.
