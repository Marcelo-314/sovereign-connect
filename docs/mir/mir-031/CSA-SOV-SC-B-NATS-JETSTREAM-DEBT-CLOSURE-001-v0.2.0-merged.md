# CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

## Pre-MIR Code Surface Audit — SC-B NATS / JetStream Industrial Debt Closure

```text
Document ID:  CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
Version:      v0.2.0-merged
Status:       Merged / Post-MU-030 / Pre-MIR Debt Closure
Date:         2026-06-01
Corpus:       Sovereign Connect
Plane:        SC-B / NATS JetStream
Baseline:     sovereign-connect-030, post-MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Baseline tests:
  sovereign-connect: 469 tests / 0 failures / 0 errors / 0 skipped
  EIB:               56 tests / 0 failures / 0 errors / 0 skipped
Previous increment: MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Scope:        DEBT-B-NATS-004 / DEBT-B-NATS-005 / DEBT-B-NATS-009
Result:       Approvable — open MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
```

---

## 1. Executive verdict

```text
Verdict: Approvable for MIR descent.

Open next:
  MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

Recommended canonical MU slot:
  MU-031

Downstream re-slot:
  MIR-SOV-SC-D-ADAPTER-MANIFEST-SEED-001 should move to MU-032 if this debt closure
  is executed first. Any prior MU-031 reference to Adapter Manifest Seed is an
  operational alias only after this governance decision.
```

The post-MU-030 codebase completed JetStream hardening for live stream application, durable lifecycle consumer creation, lifecycle retention/replay, ack-boundary preservation and import-level architecture hardening.

The remaining SC-B/NATS debt is now narrow and industrial:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated.
DEBT-B-NATS-005 — drain behavior not validated.
DEBT-B-NATS-009 — incompatible stream conflict behavior not validated.
```

This CSA recommends closing those three debts in one focused MIR without expanding into Adapter Manifest runtime, route assignment runtime, command/response handler runtime, provider execution, SC-D capability admission or SC-C NATS exposure.

---

## 2. Merge notes

This merged CSA combines:

```text
A. CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.1.0-refresh
   - confirmed the retained debts and narrow industrial scope;
   - identified absence of reconnect, drain and conflict tests;
   - recommended closure without Manifest runtime or command/response handler expansion.

B. User CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.1.0-draft
   - confirmed the exact NatsScBusPort structure;
   - identified injected Connection ownership constraints;
   - proposed AutoCloseable/drain direction;
   - supplied StorageType.File vs Memory as verified non-updateable conflict fixture;
   - identified ConnectionListener/Options path for reconnect;
   - added implementation-report requirements and concrete hard stops.
```

The user CSA is materially stronger on code-surface exactness and test design. Its findings are incorporated as normative MIR guidance below.

---

## 3. Confirmed baseline inventory

### 3.1 Existing SC-B NATS runtime classes

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  JetStreamHardeningConfiguration.java
  NatsJetStreamConsumerApplicator.java
  NatsJetStreamStreamApplicator.java
  NatsLifecycleChannelMapper.java
  NatsScBusPort.java
  NatsStreamConfiguration.java
  NatsSubjectBuilder.java
```

Confirmed after MU-030:

```text
NatsJetStreamStreamApplicator
  applies configured streams against a live JetStream server.

NatsJetStreamConsumerApplicator
  creates / updates the durable lifecycle consumer.

JetStreamHardeningConfiguration
  records lifecycle durable consumer name, lifecycle filter subject, ack wait and maxDeliver.

NatsScBusPort
  remains NATS Core publish/subscribe for bus publishing.
  does not own DispatchStateWritePort or DispatchObservationPort.
```

### 3.2 Existing tests relevant to debt closure

```text
NatsJetStreamStreamApplicatorTest:        5 tests
NatsJetStreamLifecycleConsumerTest:       5 tests
NatsJetStreamLifecycleRetentionTest:      4 tests
ScBusNatsArchitectureTest:                8 tests
```

Confirmed gaps:

```text
No reconnect / resubscribe test exists.
No drain behavior test exists.
No incompatible stream conflict test exists.
```

### 3.3 Existing implementation report records the target debts

MU-030 implementation retained:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in JetStream hardening MIR.
DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.
DEBT-B-NATS-009 — incompatible stream conflict behavior not validated in JetStream hardening MIR.
```

---

## 4. Confirmed code-surface gaps

### 4.1 NatsScBusPort exact current structure

```java
public final class NatsScBusPort implements ScBusPort {
    private final Connection connection;
    private final NatsSubjectBuilder subjectBuilder;
    private final ScJsonWireCodec wireCodec;
    private final EnvelopeValidationService envelopeValidationService;
    private final CopyOnWriteArrayList<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
```

Constructor injection:

```text
(Connection, NatsSubjectBuilder, ScJsonWireCodec, EnvelopeValidationService)
```

The `Connection` is injected. It is not constructed inside `NatsScBusPort`. Therefore, reconnect listener wiring cannot be silently added inside `NatsScBusPort` after the fact unless a new construction boundary is introduced.

MIR implication:

```text
ConnectionListener MUST be registered before Connection injection, or a new
NatsConnectionFactory / NatsConnectionManager MUST own Options construction.
```

### 4.2 Dispatcher registry exists but is not a reconnect registry yet

Current registry:

```java
private final CopyOnWriteArrayList<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
```

Currently used only by `registerEventHandler`:

```text
registerEventHandler("sc-c.timer-fired", handler)
  creates NATS Core Dispatcher
  subscribes to sc.v1.*.event.timer.sc-c.timer-fired
  stores dispatcher in dispatchers
```

`registerCommandHandler` and `registerResponseHandler` are validation-only stubs.

Debt closure implication:

```text
DEBT-B-NATS-004 is scoped to the existing event-handler subscription path and
lifecycle durable consumer survivability. Command/response handler runtime remains
out of scope because it depends on route assignment / manifest lifecycle semantics.
```

The MIR must decide and document one reconnect strategy:

```text
Strategy A — drain and recreate dispatchers using a stored handler registration registry.
Strategy B — resubscribe existing dispatchers.
```

Preferred:

```text
Strategy A — drain and recreate.
```

Reason:

```text
It avoids duplicate subscriptions when old dispatcher state is ambiguous after reconnect.
```

### 4.3 Drain behavior is absent

Current state:

```text
No drain method in NatsScBusPort.
No AutoCloseable implementation in NatsScBusPort.
No explicit dispatcher shutdown path.
No publish-after-drain rejection behavior.
No drain timeout test.
```

Current `flush()`:

```java
private void flush() {
    try {
        connection.flush(Duration.ofSeconds(2));
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while flushing NATS connection", ex);
    } catch (TimeoutException ex) {
        throw new IllegalStateException("timed out while flushing NATS connection", ex);
    }
}
```

After drain completes, `flush()` must not be called. The MIR must introduce either a connection-state guard or deterministic failure after drain.

Recommended shape:

```java
public final class NatsScBusPort implements ScBusPort, AutoCloseable {
    public void drain(Duration timeout) { ... }
    public boolean isDrained() { ... }
    @Override public void close() { ... }
}
```

Alternative:

```java
public final class NatsConnectionDrainController {
    public void drain(Duration timeout) { ... }
    public boolean isDrained() { ... }
}
```

The MIR must keep `ScBusPort` unchanged unless an ADR/SDD explicitly authorizes broadening the abstract bus contract.

### 4.4 Incompatible stream conflict behavior remains unclosed

Current `NatsJetStreamStreamApplicator.ensureStream(...)` catch block:

```java
} catch (JetStreamApiException alreadyExistsOrConflict) {
    try {
        return management.updateStream(config);
    } catch (IOException | JetStreamApiException updateFailed) {
        throw new IllegalStateException(
            "JetStream stream is incompatible or cannot be updated: " + definition.name(),
            updateFailed);
    }
}
```

Current problem:

```text
Every JetStreamApiException from addStream(...) is treated as a possible existing-stream case.
The code does not distinguish stream-already-exists from unrelated API errors.
The code does not compare existing stream configuration before update.
The code does not explicitly fail on incompatible stream definitions before mutation.
```

DEBT-B-NATS-009 must be closed with explicit conflict behavior.

Correct behavior:

```text
stream absent
  → addStream(canonicalConfig)

stream exists and is compatible
  → no-op or idempotent ensure

stream exists and is incompatible
  → observable failure; no silent mutation

addStream fails for any reason other than stream already exists
  → fail explicitly; do not attempt update
```

### 4.5 Verified conflict fixture: StorageType.File vs Memory

Subject pattern changes must not be used as conflict source; they may be updateable in nats-server 2.14.1.

Use:

```java
StreamConfiguration incompatible = StreamConfiguration.builder()
    .name(NatsStreamConfiguration.SCB_LIFECYCLE_V1)
    .subjects("sc.v1.*.lifecycle.>")
    .storageType(StorageType.File)
    .build();
jsm.addStream(incompatible);
```

Then call `ensureStream(...)` with canonical `StorageType.Memory`. The test must expect observable failure and no silent mutation.

### 4.6 NatsLocalServer can support debt-closure tests but may need test-only restart support

Existing `NatsLocalServer` exposes:

```java
static NatsLocalServer start(Path cacheDir)
Connection connection()
void close()
```

It does not expose:

```text
stop server without closing client connection
restart server on same port
server URI accessor beyond connection-created state
explicit wait-for-reconnect helper
```

MIR implication:

```text
Reconnect tests may require a new test-only harness capable of killing/restarting the server
while retaining client options and observing reconnect events.
```

This harness must remain test-only and must not become production runtime.

---

## 5. jnats API verification required before execution package

The execution package context MUST verify exact jnats 2.25.2 API forms before giving paste-ready code for:

```text
ConnectionListener
Options.Builder.connectionListener(...)
ConnectionListener.Events.RECONNECTED
ConnectionListener.Events.DISCONNECTED
ConnectionListener.Events.CLOSED
Connection.drain(...) / drain(Duration) / close() actual available forms
Dispatcher.drain(...) / unsubscribe(...) / connection.closeDispatcher(...) available forms if used
JetStreamManagement.getStreamInfo(...)
JetStreamManagement.addStream(...)
JetStreamManagement.updateStream(...)
JetStreamApiException API error-code accessors
```

No Codex prompt should infer these signatures from memory.

---

## 6. Required design decisions for MIR

### D-DC-001 — Reconnect listener ownership

The MIR must choose one approach.

Option A — `NatsConnectionFactory` / `NatsConnectionManager` owns `Options` construction.

```text
Preferred.
Registers ConnectionListener before Connection injection.
Keeps NatsScBusPort constructor clean.
Can notify NatsScBusPort or a lifecycle tracker on RECONNECTED/DISCONNECTED/CLOSED.
```

Option B — constructor-injected reconnect callback.

```text
Simpler but leaks connection lifecycle concern into NatsScBusPort construction.
```

Option C — no production reconnect component, test-only reconnect proof.

```text
Not sufficient to close DEBT-B-NATS-004 unless the implementation report explains how production reconnect behavior is guaranteed.
```

### D-DC-002 — Resubscription strategy

Preferred:

```text
Drain and recreate event dispatchers using a stored event handler registry.
```

Alternative:

```text
Resubscribe existing dispatchers.
```

The selected strategy MUST be recorded in the implementation report.

### D-DC-003 — Drain implementation surface

Preferred:

```text
NatsScBusPort implements AutoCloseable and exposes NATS-specific drain(Duration).
```

Acceptable:

```text
Dedicated NatsConnectionDrainController / lifecycle manager.
```

Forbidden:

```text
Adding drain to ScBusPort without separate design decision.
Treating drain as semantic command completion, manifest acceptance or adapter activation.
```

### D-DC-004 — Stream conflict compatibility comparison

The MIR must decide whether `ensureStream` performs:

```text
A. explicit getStreamInfo + compare before update;
B. addStream, and on already-exists only, compare/update/fail;
C. update attempt with post-failure classification.
```

Preferred:

```text
A or B with explicit already-exists classification.
```

Forbidden:

```text
Catch every JetStreamApiException from addStream and blindly attempt update.
```

---

## 7. Invariants

```text
INV-DC-001  ConnectionListener is registered before the Connection is injected into
            NatsScBusPort, or connection lifecycle is owned by a dedicated factory/manager.

INV-DC-002  On RECONNECTED, previously registered event handlers are resubscribed or
            recreated according to a documented strategy.

INV-DC-003  drain() is transport-level closure. It does not mean semantic completion
            of command execution, manifest acceptance or adapter activation.

INV-DC-004  After close/drain, NatsScBusPort does not publish or subscribe.
            Publish-after-drain fails deterministically.

INV-DC-005  DEBT-B-NATS-009 test uses StorageType.File vs StorageType.Memory as the
            verified non-updateable conflict. Subject-pattern changes MUST NOT be the
            conflict source.

INV-DC-006  DispatchStateWritePort and DispatchObservationPort remain the SC-B
            persistence boundary. JetStream retention does not replace them.

INV-DC-007  registerCommandHandler and registerResponseHandler remain stubs.
            This MIR does not implement command/response subscription runtime.

INV-DC-008  SC-C does not import io.nats.* or bus.runtime.nats.* after this MIR.

INV-DC-009  Reconnect/drain/conflict tests use live NATS / JetStream, not mocked broker.
```

---

## 8. Mandatory actions

```text
ACTION-NATS-DC-001 — Introduce reconnect observability.
  Use jnats ConnectionListener directly or introduce a narrow SC-B connection lifecycle component.
  Reconnect events must be observable in tests.

ACTION-NATS-DC-002 — Validate event subscription survivability / resubscribe behavior.
  Existing timer-fired event handler path is the only handler path in scope.
  Command/response handlers remain stubs and must not be completed in this MIR.

ACTION-NATS-DC-003 — Validate lifecycle durable consumer behavior across reconnect/restart.
  Tests must prove lifecycle retention/replay remains available after broker restart or reconnect.

ACTION-NATS-DC-004 — Add drain behavior.
  Implementation must expose a deterministic drain/close path without changing ScBusPort.

ACTION-NATS-DC-005 — Reject publish after drain.
  NatsScBusPort or its lifecycle manager must fail deterministically after drain.

ACTION-NATS-DC-006 — Close incompatible stream conflict behavior.
  NatsJetStreamStreamApplicator must compare existing stream config before update and fail on incompatible config.

ACTION-NATS-DC-007 — Classify addStream errors.
  Do not treat every JetStreamApiException as “maybe already exists”.
  Only stream-already-exists may enter existing stream comparison/update path.

ACTION-NATS-DC-008 — Add live tests for debt closure.
  Tests must use NatsLocalServer or Testcontainers against real NATS / JetStream.

ACTION-NATS-DC-009 — Preserve SC-B / SC-C / SC-D boundaries.
  No manifest runtime, route assignment runtime, provider execution or SC-C NATS imports.

ACTION-NATS-DC-010 — Add architecture check for AutoCloseable if NatsScBusPort owns drain.
  assertThat(AutoCloseable.class).isAssignableFrom(NatsScBusPort.class).
```

---

## 9. Recommended implementation surface

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  NatsConnectionFactory.java              optional / preferred if owning Options
  NatsConnectionLifecycleTracker.java     optional, if separating listener state
  NatsConnectionDrainController.java      optional, if drain is not placed on NatsScBusPort
  NatsJetStreamStreamApplicator.java      harden ensureStream behavior
  NatsScBusPort.java                      add AutoCloseable/drain/closed state if chosen
```

No new migrations. No new SC-C integration. No new ports in SC-C. No manifest runtime.

Preferred implementation shape:

```text
Keep ScBusPort unchanged.
Add NATS-specific lifecycle/drain behavior to NatsScBusPort or companion component.
Do not broaden the abstract bus contract.
```

---

## 10. Required tests

### 10.1 DEBT-B-NATS-009 — incompatible stream conflict

```text
Target class:
  NatsJetStreamStreamApplicatorTest

Required test:
  incompatibleStorageTypeConflictThrowsIllegalStateException
```

Fixture:

```text
Create SCB_LIFECYCLE_V1 with StorageType.File and same lifecycle subjects.
Call ensureStream() with canonical Memory config.
Expect IllegalStateException containing SCB_LIFECYCLE_V1.
```

Hard rule:

```text
Do not use subject-pattern changes as the conflict source.
```

### 10.2 DEBT-B-NATS-004 — reconnect / resubscribe

```text
Target class:
  NatsScBusPortReconnectTest

Minimum tests:
  reconnectEventIsObserved
  timerFiredEventHandlerReceivesAfterReconnect
  lifecycleDurableConsumerCanReplayAfterReconnect
```

If the local server harness cannot deterministically simulate reconnect:

```text
The MIR execution package must revise the harness or stop.
Do not claim DEBT-B-NATS-004 closed with mocked Connection or weak evidence.
```

### 10.3 DEBT-B-NATS-005 — drain

```text
Target class:
  NatsScBusPortDrainTest

Minimum tests:
  drainClosesConnectionAndDispatchersOrderly
  publishAfterDrainFailsDeterministically
  drainDoesNotReplaceDispatchPersistenceBoundary
```

### 10.4 Architecture tests

If `NatsScBusPort` implements AutoCloseable:

```java
@Test
void natsScBusPortImplementsAutoCloseable() {
    assertThat(AutoCloseable.class).isAssignableFrom(NatsScBusPort.class);
}
```

Keep existing boundary tests:

```text
SC-C must not import io.nats.* or bus.runtime.nats.*.
NATS package must not import core.**, adapter.**, integration.**.
DispatchStateWritePort / DispatchObservationPort must not be replaced.
```

---

## 11. Expected test delta

```text
Baseline:     469 tests / 0 failures
After MIR:    >= 482 tests / 0 failures

NatsJetStreamStreamApplicatorTest addition:   +1 to +2
NatsScBusPortReconnectTest:                   +3 to +5
NatsScBusPortDrainTest:                       +3 to +5
ScBusNatsArchitectureTest additions:          +1 to +2
```

---

## 12. Implementation report requirements

The implementation report MUST include:

```text
- branch and commits;
- files modified/created;
- final test count;
- reconnect listener ownership decision;
- resubscription strategy decision;
- drain surface decision;
- DEBT-B-NATS-004 status: CLOSED;
- DEBT-B-NATS-005 status: CLOSED;
- DEBT-B-NATS-009 status: CLOSED;
- confirmation StorageType.File vs Memory was used as conflict fixture;
- confirmation subject-pattern change was NOT used as conflict fixture;
- confirmation DispatchStateWritePort / DispatchObservationPort not replaced;
- confirmation SC-C does not import io.nats.* or bus.runtime.nats.*;
- confirmation registerCommandHandler / registerResponseHandler remain stubs.
```

Partial closure is not expected for this MIR. If reconnect cannot be tested against a real broker restart/reconnect path, the MIR should stop and revise the test harness rather than claim closure.

---

## 13. Hard stops

```text
STOP-NATS-DC-001 — Reconnect/resubscribe behavior is claimed closed without live broker restart/reconnect test.

STOP-NATS-DC-002 — Command/response handler runtime is implemented inside this MIR.

STOP-NATS-DC-003 — Drain behavior is claimed closed with close() only and no publish-after-drain rejection test.

STOP-NATS-DC-004 — Incompatible stream conflict closure relies on subject-pattern changes as conflict source.

STOP-NATS-DC-005 — NatsJetStreamStreamApplicator continues to treat every JetStreamApiException from addStream as an existing-stream condition.

STOP-NATS-DC-006 — Tests use mocked broker instead of live NATS / JetStream for debt closure.

STOP-NATS-DC-007 — Manifest runtime, route assignment runtime or provider execution enters this MIR.

STOP-NATS-DC-008 — SC-C imports io.nats.* or bus.runtime.nats.*.

STOP-NATS-DC-009 — DispatchStateWritePort / DispatchObservationPort are replaced or bypassed by JetStream retention.

STOP-NATS-DC-010 — drain is treated as semantic completion of commands, manifest acceptance or adapter activation.

STOP-NATS-DC-011 — close()/drain calls flush() after drain completes and connection is closed.
```

---

## 14. Verdict

```text
CSA result:
  Approvable for MIR descent.

Open next:
  MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

Recommended branch:
  feat/sc-b-mir-031-nats-jetstream-debt-closure

Suggested commit:
  feat(sc-b): close nats jetstream connection debt
```

This MIR should close the industrial SC-B connection/broker debt retained after MU-030 and must not expand into SC-D Manifest runtime. Once the debt closure is validated, `MIR-SOV-SC-D-ADAPTER-MANIFEST-SEED-001` can proceed on a more stable SC-B substrate.
