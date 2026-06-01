# MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

## NATS / JetStream Connection Debt Closure

```text
Document ID:  MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
Version:      v0.2.0-candidate
Status:       Candidate / Ready for review
Date:         2026-06-01
Corpus:       Sovereign Connect
Plane:        SC-B
Layer:        Bus runtime / NATS / JetStream
MU Slot:      MU-031
Input CSA:    CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.0-merged
Baseline:     post-MU-030 / JetStream hardening validated L4
Baseline test count: 469 tests / 0 failures / 0 errors / 0 skipped
```

---

## 1. Executive summary

This MIR closes the remaining SC-B NATS / JetStream industrial connection debts retained after `MIR-SOV-SC-B-JETSTREAM-HARDENING-001`.

Target debts:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior
DEBT-B-NATS-005 — drain behavior
DEBT-B-NATS-009 — incompatible stream conflict behavior
```

The goal is to move the SC-B NATS / JetStream substrate from **validated seed hardening** to **industrial-ready transport substrate for downstream SC-D lifecycle / manifest transport**.

This MIR does **not** implement Adapter Manifest runtime, route assignment runtime, command/response handler runtime, provider execution, or semantic command success.

---

## 2. Normative thesis

```text
SC-B owns transport resilience.
SC-B does not own domain semantics.

Reconnect, resubscribe, drain and JetStream stream-conflict behavior are transport-industrial properties.
They must be closed before SC-D manifest/lifecycle runtime builds live transport on top of SC-B.
```

Industrial SC-B NATS/JetStream means:

```text
- Stream definitions are applied against a real broker.
- Lifecycle durable consumer exists and is validated.
- Reconnect/resubscribe behavior is observable and tested.
- Drain / close behavior is observable and tested.
- Incompatible stream configuration fails observably.
- DispatchStateWritePort / DispatchObservationPort remain the technical durable dispatch authority.
- JetStream ack remains broker transport evidence only.
```

---

## 3. Inputs

This MIR consumes:

```text
SDD-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate
MIR-SOV-SC-B-JETSTREAM-HARDENING-001 v1.0.0-accepted / validated L4
CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.0-merged
```

The CSA confirms:

```text
NatsScBusPort has no reconnect handler.
NatsScBusPort has no drain / close path.
NatsScBusPort receives an injected Connection.
NatsJetStreamStreamApplicator treats any JetStreamApiException from addStream as candidate for updateStream.
StorageType.File → StorageType.Memory is the correct incompatible stream conflict fixture.
registerCommandHandler / registerResponseHandler remain stubs and must stay out of scope.
```

---

## 4. Scope

### 4.1 In scope

```text
- reconnect detection for NATS connection;
- event-handler resubscription / recreation after reconnect;
- lifecycle durable consumer survivability / replay after reconnect;
- deterministic drain / close behavior;
- publish-after-drain rejection or deterministic failure;
- stream-incompatibility detection for JetStream stream application;
- ensureStream error classification;
- AutoCloseable / close-path architecture check if NatsScBusPort owns drain;
- implementation-report closure of DEBT-B-NATS-004 / 005 / 009.
```

### 4.2 Out of scope

```text
- Adapter Manifest runtime;
- AdapterManifestProposal live transport;
- route assignment runtime;
- adapter lifecycle FSM runtime;
- registerCommandHandler real subscription;
- registerResponseHandler real subscription;
- provider execution;
- command admission runtime;
- capability registry runtime;
- semantic retry;
- terminal request-state authority;
- SC-C direct NATS publishing;
- Flyway migrations.
```

---

## 5. Baseline inventory

### 5.1 `NatsScBusPort`

Current structure:

```java
public final class NatsScBusPort implements ScBusPort {
    private final Connection connection;
    private final NatsSubjectBuilder subjectBuilder;
    private final ScJsonWireCodec wireCodec;
    private final EnvelopeValidationService envelopeValidationService;
    private final CopyOnWriteArrayList<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
}
```

`Connection` is injected. Therefore reconnect listener ownership cannot be implicit inside raw `NatsScBusPort` construction unless the construction path is changed or wrapped.

### 5.2 Existing handler state

Current effective subscription path:

```text
registerEventHandler("sc-c.timer-fired", handler)
  → creates NATS Core Dispatcher
  → subscribes to timer-fired event subscription pattern
  → stores dispatcher in dispatchers list
```

Current non-subscription stubs:

```text
registerCommandHandler(...)
registerResponseHandler(...)
```

These stubs must remain stubs in this MIR.

### 5.3 Existing JetStream applicator behavior

Current behavior to harden:

```text
addStream(config)
  catches any JetStreamApiException
  then attempts updateStream(config)
```

Industrial behavior must distinguish:

```text
stream absent       → addStream
stream compatible   → idempotent no-op / update if safe
stream incompatible → observable failure
other API error     → observable failure, not update attempt
```

---

## 6. Design decisions

### D-MIR-DC-001 — Debt-closure scope

This MIR closes only:

```text
DEBT-B-NATS-004
DEBT-B-NATS-005
DEBT-B-NATS-009
```

It must not claim closure of lifecycle runtime, adapter manifest runtime, route assignment runtime, or command/response handler runtime.

---

### D-MIR-DC-002 — Reconnect listener ownership

The implementation MUST choose and report one of the following:

```text
Option A — NatsConnectionFactory / NatsConnectionManager owns Options construction.
Option B — constructor / factory injection provides reconnect callback wiring externally.
```

Preferred option:

```text
Option A — introduce NatsConnectionFactory or NatsConnectionManager.
```

Rationale:

```text
NatsScBusPort receives an injected Connection.
ConnectionListener must be registered before connection creation.
```

The implementation MUST NOT silently pretend to add a `ConnectionListener` after the connection is already constructed unless jnats provides a verified API for it.

---

### D-MIR-DC-003 — Event-handler registry for resubscription

Because `registerEventHandler` is the only real subscription path in scope, reconnect support MUST preserve event-handler registrations.

The implementation SHOULD store registrations separately from dispatchers, for example:

```text
Map<String, ScEventHandler> eventHandlerRegistrations
```

or an equivalent immutable/recorded registration structure.

Dispatcher list alone is not sufficient if reconnect strategy recreates dispatchers.

---

### D-MIR-DC-004 — Resubscription strategy

The implementation MUST document one reconnect strategy:

```text
Strategy A — drain/close old dispatchers and recreate from handler registry.
Strategy B — resubscribe existing dispatchers.
```

Preferred:

```text
Strategy A — recreate from handler registry.
```

Reason:

```text
It avoids duplicate subscriptions and makes replay/resubscription testable.
```

---

### D-MIR-DC-005 — Command/response handlers stay out of scope

`registerCommandHandler` and `registerResponseHandler` MUST remain non-subscription stubs unless a separate lifecycle/route-assignment SDD/MIR opens that scope.

This MIR MUST NOT implement command/response runtime subscription.

---

### D-MIR-DC-006 — Drain / close behavior

If `NatsScBusPort` owns drain behavior, it MUST implement `AutoCloseable` or equivalent observable close lifecycle.

Preferred:

```java
public final class NatsScBusPort implements ScBusPort, AutoCloseable
```

Drain order:

```text
1. Stop accepting new publish/register calls.
2. Drain dispatchers.
3. Clear dispatcher state.
4. Drain connection with timeout.
5. Mark port as closed.
```

After close/drain:

```text
publishCommand / publishEvent / publishResponse MUST fail deterministically.
registerEventHandler MUST fail deterministically.
flush() MUST NOT be called after drain completion.
```

Dispatcher drain API note:

```text
The execution package MUST verify the dispatcher lifecycle API available in
jnats 2.25.2 before providing paste-ready code. If Dispatcher.drain(Duration) or
equivalent is not available, the implementation MUST use the verified
connection.closeDispatcher(dispatcher) / dispatcher close path as the dispatcher
cleanup alternative and MUST document the chosen API in the implementation
report.
```

The required behavior is observable ordered shutdown, not a specific method name.

---

### D-MIR-DC-007 — Drain is transport closure only

Drain does not mean:

```text
command success;
manifest acceptance;
adapter activation;
route readiness;
semantic completion.
```

Drain only means:

```text
SC-B NATS transport is closing / closed in an orderly way.
```

---

### D-MIR-DC-008 — Incompatible stream conflict fixture

The implementation MUST close `DEBT-B-NATS-009` using a verified non-updateable conflict.

Required fixture:

```text
Create existing SCB_LIFECYCLE_V1 stream with StorageType.File.
Canonical hardening config uses StorageType.Memory.
ensureStream(SCB_LIFECYCLE_V1 canonical) MUST fail observably.
```

The test MUST NOT use subject-pattern differences as the conflict source because subject changes may be updateable in NATS server.

---

### D-MIR-DC-009 — Stream API error classification

`NatsJetStreamStreamApplicator.ensureStream(...)` MUST NOT treat every `JetStreamApiException` from `addStream(...)` as “maybe already exists”.

Required behavior:

```text
Only verified stream-already-exists API error may enter compatible/update path.
Any other JetStreamApiException MUST fail explicitly.
```

If the implementation cannot reliably classify the API error code, it MUST inspect existing stream state through JetStreamManagement and compare compatibility before mutation.

---

### D-MIR-DC-010 — Dispatch persistence boundary preserved

This MIR MUST NOT replace or bypass:

```text
DispatchStateWritePort
DispatchObservationPort
```

JetStream retention and drain/reconnect behavior remain transport concerns.

---

### D-MIR-DC-011 — SC-C remains NATS-free

SC-C production code MUST NOT import:

```text
io.nats.*
com.sovereign.connect.bus.runtime.nats.*
```

---

## 7. Required production surface

Expected new/modified surface:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  NatsConnectionFactory.java or NatsConnectionManager.java
  NatsScBusPort.java
  NatsJetStreamStreamApplicator.java
```

Optional support types:

```text
NatsConnectionLifecycleObserver.java
NatsReconnectPolicy.java
NatsDrainPolicy.java
NatsRegisteredEventHandler.java
```

No new migrations. No SC-C package changes.

---

## 8. Required test surface

Minimum test classes:

```text
NatsJetStreamStreamApplicatorTest
NatsScBusPortReconnectTest
NatsScBusPortDrainTest
ScBusNatsArchitectureTest
```

Expected delta:

```text
Baseline: 469 tests
Expected: >= 480 tests
```

Minimum expected breakdown:

```text
DEBT-B-NATS-009 incompatible stream conflict:     +1 to +2 tests
DEBT-B-NATS-004 reconnect / resubscribe:          +3 tests minimum
DEBT-B-NATS-005 drain / close:                    +3 tests minimum
Architecture AutoCloseable / boundary assertion:  +1 test minimum
```

Rationale:

```text
A single reconnect test and a single drain test are not sufficient evidence for
industrial debt closure. The execution package may exceed this count, but it
MUST NOT lower the evidence floor unless the MIR is patched before execution.
```

---

## 9. Acceptance criteria

### Group A — reconnect / resubscribe

```text
AC-DC-001 — Implementation introduces a reconnect-aware connection creation/wiring path.
AC-DC-002 — Reconnect listener ownership is documented in the implementation report.
AC-DC-003 — Event handler registrations survive or are recreated after reconnect.
AC-DC-004 — A real NATS server test verifies handler receives an event after reconnect/resubscribe. PARTIAL is acceptable only when the limitation is demonstrably in the test harness: NatsLocalServer does not expose a verified deterministic restart/reconnect primitive or equivalent broker-restart simulation path. If the implementation does not wire a real ConnectionListener / reconnect path, PARTIAL is not allowed and STOP-DC-003 applies.
AC-DC-005 — registerCommandHandler/registerResponseHandler remain out of scope.
```

### Group B — drain / close

```text
AC-DC-006 — NatsScBusPort implements AutoCloseable or an equivalent observable close path.
AC-DC-007 — Drain order is dispatcher-first then connection.
AC-DC-008 — close/drain is tested against real NATS connection.
AC-DC-009 — publish/register after close fails deterministically.
AC-DC-010 — drain is documented as transport closure only, not semantic success.
```

### Group C — incompatible stream conflict

```text
AC-DC-011 — Conflict test uses StorageType.File vs StorageType.Memory.
AC-DC-012 — Conflict test does not rely on subject-pattern changes.
AC-DC-013 — Incompatible stream config produces observable IllegalStateException or equivalent domain-specific failure.
AC-DC-014 — ensureStream does not classify every addStream JetStreamApiException as existing-stream.
```

### Group D — boundaries

```text
AC-DC-015 — DispatchStateWritePort / DispatchObservationPort remain untouched.
AC-DC-016 — SC-C production code remains free of io.nats.* and bus.runtime.nats.* imports.
AC-DC-017 — No Adapter Manifest runtime, route assignment runtime, provider execution or command admission runtime is introduced.
AC-DC-018 — Architecture test asserts AutoCloseable assignability if NatsScBusPort owns drain.
```

### Group E — debt closure

```text
AC-DC-019 — Implementation report marks DEBT-B-NATS-004 CLOSED or PARTIAL with bounded reason. PARTIAL MUST identify the harness limitation, the attempted evidence path and the follow-up artifact. PARTIAL MUST NOT mask missing implementation wiring.
AC-DC-020 — Implementation report marks DEBT-B-NATS-005 CLOSED.
AC-DC-021 — Implementation report marks DEBT-B-NATS-009 CLOSED.
AC-DC-022 — No retained debt is recorded only as informal prose.
```

---

## 10. Hard stops

```text
STOP-DC-001 — registerCommandHandler or registerResponseHandler implement real subscriptions.
STOP-DC-002 — reconnect test uses mocked Connection instead of real NATS server.
STOP-DC-003 — ConnectionListener is claimed but no verified construction/wiring path exists, or DEBT-B-NATS-004 is marked PARTIAL because implementation wiring is missing rather than because the test harness cannot deterministically simulate reconnect.
STOP-DC-004 — Incompatible stream conflict uses subject-pattern changes as conflict source.
STOP-DC-005 — Any JetStreamApiException from addStream is treated as existing-stream without classification.
STOP-DC-006 — close()/drain calls flush after connection drain completes.
STOP-DC-006A — Execution package or implementation assumes Dispatcher.drain(Duration) without verifying it against jnats 2.25.2, and fails to use a verified closeDispatcher/dispatcher cleanup fallback if unavailable.
STOP-DC-007 — drain is treated as semantic completion.
STOP-DC-008 — DispatchStateWritePort or DispatchObservationPort are bypassed or replaced.
STOP-DC-009 — SC-C imports io.nats.* or bus.runtime.nats.*.
STOP-DC-010 — Manifest runtime, route assignment runtime or provider execution enters this MIR.
```

---

## 11. Risks

```text
RISK-DC-001 — Reconnect simulation may be difficult with current NatsLocalServer.
Mitigation: PARTIAL closure is allowed only if the limitation is in the test harness,
not in the implementation. If ConnectionListener / reconnect wiring is absent, this
is STOP-DC-003, not PARTIAL. The implementation report MUST record the failed or
unavailable deterministic restart/reconnect fixture and the follow-up artifact.

RISK-DC-002 — Drain behavior can be over-specified relative to jnats runtime behavior.
Mitigation: test observable deterministic behavior rather than internal implementation details.

RISK-DC-003 — Incompatible stream field may vary by nats-server version.
Mitigation: use StorageType.File vs Memory, verified by CSA, and report server version in evidence.

RISK-DC-004 — Connection lifecycle wiring may creep into domain semantics.
Mitigation: keep all changes under bus.runtime.nats and tests.
```

---

## 12. Implementation report requirements

The implementation report MUST include:

```text
- branch name and commits;
- final test count;
- files created/modified;
- reconnect listener ownership decision;
- resubscription strategy decision;
- drain / close strategy decision;
- dispatcher lifecycle API used for drain cleanup, verified against jnats 2.25.2;
- exact NATS server version used;
- confirmation StorageType.File vs Memory used for DEBT-B-NATS-009;
- DEBT-B-NATS-004 status; if PARTIAL, the report MUST prove the limitation is harness-only and identify the follow-up artifact;
- DEBT-B-NATS-005 status;
- DEBT-B-NATS-009 status;
- confirmation command/response handler runtime remains out of scope;
- confirmation DispatchState/DispatchObservation not replaced;
- confirmation SC-C remains NATS-free.
```

---

## 13. Promotion checklist

```text
[ ] MIR closes exactly DEBT-B-NATS-004 / 005 / 009.
[ ] MIR does not introduce manifest runtime or route assignment runtime.
[ ] MIR defines reconnect listener ownership.
[ ] MIR defines resubscription strategy.
[ ] MIR defines drain/close behavior.
[ ] MIR uses StorageType.File vs Memory as incompatible stream conflict fixture.
[ ] MIR preserves command/response handler stubs.
[ ] MIR preserves DispatchState/DispatchObservation authority.
[ ] MIR preserves SC-C NATS-free boundary.
[ ] MIR defines expected test delta of >=480 with reconnect/drain/conflict breakdown.
[ ] MIR defines strict conditions for DEBT-B-NATS-004 PARTIAL and does not allow PARTIAL for missing implementation wiring.
[ ] MIR defines dispatcher drain API verification/fallback requirement for jnats 2.25.2.
[ ] MIR defines implementation report debt closure requirements.
```

---

## 14. Downstream after this MIR

If this MIR validates L4 and closes the three target debts, SC-B NATS/JetStream can be treated as sufficiently industrial for:

```text
- Adapter Manifest Seed execution;
- manifest-over-NATS runtime design;
- lifecycle route assignment runtime design;
- first adapter seed planning.
```

It does not by itself close:

```text
DEBT-B-LC-001 — lifecycle-channel runtime absent
DEBT-B-RD-004 — DispatchCandidateReadPort seed in-memory
```

Those remain downstream blockers for productive adapter execution.

---

## 15. Changelog

```text
v0.1.0-draft
  - Initial MIR opened from CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.0-merged.
  - Scope limited to DEBT-B-NATS-004 / 005 / 009.
  - Excludes manifest runtime, route assignment runtime and command/response subscription runtime.

v0.2.0-candidate
  - Tightened DEBT-B-NATS-004 PARTIAL criteria: PARTIAL is allowed only for harness limitation, not missing reconnect implementation.
  - Raised expected test floor from >=476 to >=480 with explicit breakdown.
  - Added dispatcher drain API verification/fallback requirement for jnats 2.25.2.
```
