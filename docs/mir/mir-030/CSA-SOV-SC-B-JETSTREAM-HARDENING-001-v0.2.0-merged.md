# CSA-SOV-SC-B-JETSTREAM-HARDENING-001

## Pre-MIR Code Surface Audit — SC-B JetStream Hardening

```text
Document ID:  CSA-SOV-SC-B-JETSTREAM-HARDENING-001
Version:      v0.2.0-merged
Status:       Merged / Post-MU-029 / Pre-MIR-JSH
Date:         2026-06-01
Corpus:       Sovereign Connect
Plane:        SC-B / NATS JetStream
Baseline:     sovereign-connect-pre-30 (post-MU-029, 454 tests / 0 failures)
SDD:          SDD-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate
Inputs:       CSA v0.1.0-refresh + user CSA v0.1.0-draft
Result:       Approvable — JetStream hardening may descend as MIR
```

---

## 1. Executive verdict

```text
Verdict: Approvable for MIR descent.

Open next:
  MIR-SOV-SC-B-JETSTREAM-HARDENING-001

Recommended nature:
  SC-B hardening MIR, not another broker seed and not SC-D lifecycle runtime.
```

MU-029 delivered a valid NATS Core binding seed and explicitly left JetStream in `config-only` disposition. The post-MU-029 codebase now has enough NATS surface to harden JetStream without inventing adapter-manifest or lifecycle runtime semantics.

The hardening target is precise:

```text
Current state:
  NatsStreamConfiguration defines stream names and subject patterns;
  NATS Core publish/subscribe is live-tested;
  NatsLocalServer can start a JetStream-enabled server;
  no live stream application exists;
  no durable consumer exists;
  no consumer policy is validated;
  no reconnect/drain behavior is validated;
  NatsScBusPort still publishes through NATS Core only.

Hardening target:
  apply SCB_*_V1 streams against a live JetStream server;
  create and validate a durable SCB_LIFECYCLE_V1 consumer;
  record explicit consumer policy;
  prove lifecycle retention/replay at broker level;
  preserve JetStream ack as transport-only;
  strengthen NATS architecture tests;
  record reconnect/drain gaps as machine-searchable DEBT IDs if deferred.
```

---

## 2. Mandatory actions for MIR

```text
ACTION-JSH-001 — Add live JetStream stream application component.

  NatsStreamConfiguration exists as config-only constants and StreamDefinition objects.
  No stream application logic exists against a live JetStream server.

  The MIR MUST introduce a JetStreamStreamApplicator, NatsJetStreamConfigurator,
  NatsJetStreamManager, or equivalent component that calls real jnats
  JetStreamManagement APIs against a live broker.

  Required behavior:
    - apply all five SCB_*_V1 streams;
    - use live JetStreamManagement, not mocks;
    - create missing streams;
    - accept already-compatible streams idempotently;
    - report or fail on incompatible existing stream configuration.
```

```text
ACTION-JSH-002 — Apply all five streams against a live JetStream server.

  Required streams:
    SCB_COMMANDS_V1
    SCB_EVENTS_V1
    SCB_RESPONSES_V1
    SCB_LIFECYCLE_V1
    SCB_DLQ_V1

  Constructing NatsStreamConfiguration objects is not sufficient.
  NatsStreamConfigurationTest currently validates object construction only.
  MIR tests MUST prove application against a live JetStream server.
```

```text
ACTION-JSH-003 — Create and validate durable consumer for SCB_LIFECYCLE_V1.

  No durable consumer exists in the NATS runtime package.
  The MIR MUST create at least one durable consumer for SCB_LIFECYCLE_V1 and
  validate it against a live JetStream server.

  Silent degradation to stream-only, ephemeral-only, or NATS Core-only behavior
  is forbidden and triggers STOP-JSH-001.
```

```text
ACTION-JSH-004 — Record explicit consumer policy.

  The MIR MUST record and test the chosen consumer policy:
    - durable name;
    - filter subject;
    - ack policy;
    - ack wait, if applicable;
    - max deliver, if applicable;
    - deliver policy;
    - replay/retention expectation.
```

```text
ACTION-JSH-005 — Decide and document NATS Core vs JetStream publish boundary.

  NatsScBusPort currently publishes exclusively through NATS Core
  connection.publish(). No connection.jetStream() / jetStream.publish() path exists.

  The MIR MUST explicitly decide whether lifecycle messages are retained by:
    A. NATS Core publish to a subject covered by an applied JetStream stream; or
    B. explicit JetStream publish with publish ack.

  Both are acceptable for this hardening if documented and tested.
  In either case, JetStream ack remains transport confirmation only.
```

```text
ACTION-JSH-006 — Add lifecycle retention fixture.

  The MIR MUST add a live JetStream test that publishes at least one lifecycle
  subject retained by SCB_LIFECYCLE_V1 and verifies that the durable consumer can
  receive or replay it.

  If manifest payload classes are absent, use a minimal lifecycle fixture such as:
    payloadType = sc.lifecycle.adapter-announce.v1
  or another lifecycle event accepted by the MIR.

  Do not introduce AdapterManifestProposal runtime in this MIR.
```

```text
ACTION-JSH-007 — Preserve dispatch state/observation authority.

  JetStream retention MUST NOT replace:
    DispatchStateWritePort
    DispatchObservationPort
    JdbcDispatchStateRepository
    JdbcDispatchObservationRepository

  JetStream retention is broker-level transport retention, not SC-B technical
  dispatch persistence and not semantic domain authority.
```

```text
ACTION-JSH-008 — Strengthen architecture tests.

  The current natsPackageUsesSerializationUtilitiesAndWireValidator() test checks
  aggregate source-string presence.

  The MIR SHOULD strengthen this to import-level or file-specific reference checks
  for:
    - NatsScBusPort imports ScJsonWireCodec and WireEnvelopeValidator;
    - NatsSubjectBuilder imports ScSubjectIdTokenCodec and ScCorrelationTokenCodec;
    - bus.runtime.nats does not import core.**, adapter.**, or integration.**;
    - SC-C does not import io.nats.* or bus.runtime.nats.*;
    - JetStream hardening classes do not import dispatch repositories.
```

```text
ACTION-JSH-009 — Record reconnect/drain disposition.

  No reconnect/resubscribe or drain behavior exists in NatsScBusPort.

  The MIR MUST either implement and validate reconnect/drain behavior, or record
  retained debt in the implementation report as:
    DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in JetStream hardening MIR.
    DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.

  Informal notes are not sufficient.
```

```text
ACTION-JSH-010 — Keep scope limited to SC-B JetStream hardening.

  The MIR MUST NOT expand into:
    Adapter Manifest runtime;
    AdapterManifestProposal live transport;
    route assignment runtime;
    adapter lifecycle runtime;
    provider execution;
    command admission runtime;
    TCK executable harness.
```

---

## 3. Confirmed baseline

```text
sovereign-connect: 454 tests, 0 failures, 0 errors, 0 skipped
EIB:               56 tests, 0 failures, 0 errors, 0 skipped
HEAD:              post-MU-029 develop
```

MU-029 confirmed present:

```text
NatsScBusPort              — NATS Core publish/subscribe; no JetStream publish
NatsSubjectBuilder         — subject grammar confirmed
NatsStreamConfiguration    — five stream definitions; config-only
NatsLifecycleChannelMapper — lifecycle subjects confirmed
NatsLocalServer            — NatsServerRunner with JetStream enabled
jnats                      — 2.25.2
testcontainers             — 1.20.6 present in test classpath
jnats-server-runner         — 3.1.0
```

---

## 4. Confirmed code surface inventory

### 4.1 NATS binding package exists

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  NatsScBusPort.java
  NatsSubjectBuilder.java
  NatsStreamConfiguration.java
  NatsLifecycleChannelMapper.java
```

`NatsScBusPort` implements `ScBusPort` and uses:

```text
ScJsonWireCodec
WireEnvelopeValidator
NatsSubjectBuilder
EnvelopeValidationService
io.nats.client.Connection
io.nats.client.Dispatcher
```

This is sufficient base surface for JetStream hardening.

### 4.2 NatsStreamConfiguration is config-only

The stream names and patterns are correctly defined:

```text
SCB_COMMANDS_V1   -> sc.v1.*.command.>
SCB_EVENTS_V1     -> sc.v1.*.event.>
SCB_RESPONSES_V1  -> sc.v1.*.response.>
SCB_LIFECYCLE_V1  -> sc.v1.*.lifecycle.>
SCB_DLQ_V1        -> sc.v1.*.dlq.>
```

`streamDefinitions()` returns immutable stream definition records.

There is no production or test code that calls JetStream management APIs to create or update streams. Current stream tests validate string values and object construction only; they do not interact with a live broker.

### 4.3 jnats JetStreamManagement API is available

`JetStreamManagement` is available through the existing jnats dependency.

Recommended idempotent stream ensure shape:

```java
JetStreamManagement jsm = connection.jetStreamManagement();

io.nats.client.api.StreamConfiguration sc =
    io.nats.client.api.StreamConfiguration.builder()
        .name(streamName)
        .subjects(subjectPatterns)
        .storageType(StorageType.Memory) // seed default; File may be production hardening
        .build();

try {
    jsm.addStream(sc);
} catch (JetStreamApiException e) {
    if (e.getApiErrorCode() == 10058) { // stream already exists
        jsm.updateStream(sc);
    } else {
        throw e;
    }
}
```

The exact exception/error-code handling MUST be verified against jnats 2.25.2 in the execution package. The conceptual requirement is idempotent stream ensure with observable incompatible-stream failure.

### 4.4 NatsScBusPort publishes through NATS Core only

Current publish path:

```java
private void publishWire(ScJsonWireEnvelope wire, String subject) {
    WireEnvelopeValidator.validate(wire);
    connection.publish(subject, wireCodec.marshal(wire));
    flush();
}
```

`connection.publish()` is NATS Core. No `connection.jetStream()` or `jetStream.publish()` path exists in the production NATS package.

The MIR must decide whether lifecycle retention is proven by:

```text
A. Core publish captured by applied JetStream stream because the subject matches SCB_LIFECYCLE_V1; or
B. explicit JetStream publish with publish ack.
```

Both can be valid hardening strategies. The decision must be explicit and tested. JetStream publish ack, if used, remains transport-level only.

### 4.5 registerCommandHandler / registerResponseHandler are stubs

Current behavior validates input but creates no subscription.

```text
registerCommandHandler(...): no subscription created.
registerResponseHandler(...): no subscription created.
```

Command/response durable consumers are downstream of richer adapter/lifecycle/runtime work. This hardening MIR may retain them as deferred consumer debt if not in scope.

### 4.6 registerEventHandler is NATS Core dispatcher only

`registerEventHandler(...)` currently uses a Core dispatcher subscription for timer-fired event patterns.

This is not a durable JetStream consumer. The required durable consumer for `SCB_LIFECYCLE_V1` should be implemented separately and must not be confused with the timer-fired event subscription.

### 4.7 NatsLocalServer can run JetStream-enabled tests

`NatsLocalServer` uses `jnats-server-runner` and starts `nats-server` with JetStream enabled.

```text
NatsLocalServer is acceptable for hardening if tests exercise real JetStream APIs.
Testcontainers remains preferred when Docker is available.
If Docker is unavailable in CI/local execution, NatsLocalServer is acceptable with explicit implementation-report justification.
```

No additional configuration is expected to enable JetStream in `NatsLocalServer`, but the execution package must confirm the exact runner behavior.

### 4.8 No durable consumer exists

No `durable`, `ConsumerConfiguration`, `PushSubscribeOptions`, or `PullSubscribeOptions` usage exists in the NATS runtime package.

The durable lifecycle consumer is fully greenfield for the MIR.

Recommended seed defaults:

```java
ConsumerConfiguration cc = ConsumerConfiguration.builder()
    .durable("sc-b-lifecycle-consumer-v1")
    .filterSubject("sc.v1.*.lifecycle.>")
    .ackPolicy(AckPolicy.Explicit)
    .ackWait(Duration.ofSeconds(30))
    .maxDeliver(3)
    .deliverPolicy(DeliverPolicy.New)
    .build();

jsm.addOrUpdateConsumer("SCB_LIFECYCLE_V1", cc);
```

The MIR may alter seed values if justified, but it must record them explicitly.

### 4.9 RuntimeDispatchService persistence boundaries remain intact

`RuntimeDispatchService` continues to use `DispatchStateWritePort` and `DispatchObservationPort` after NATS publish. `NatsScBusPort` does not reference dispatch state repositories or observation repositories.

This invariant must be preserved. JetStream retention does not replace SC-B dispatch-state or observation persistence.

### 4.10 Reconnect/drain behavior is absent

There is no `ConnectionListener`, reconnect/resubscribe, drain, or drain-validation behavior in `NatsScBusPort`.

If not implemented in this hardening MIR, the implementation report must retain:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in JetStream hardening MIR.
DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.
```

### 4.11 Architecture test gap persists

The current architecture test checks class-name string presence across aggregated source. This is acceptable for seed regression, but not sufficiently robust for hardening.

Hardening replacement pattern:

```java
@Test
void natsScBusPortImportsSerializationUtilities() throws Exception {
    String source = Files.readString(
        Path.of("src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java"));
    assertThat(source).contains(
        "import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec");
    assertThat(source).contains(
        "import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator");
}

@Test
void natsSubjectBuilderImportsEncoders() throws Exception {
    String source = Files.readString(
        Path.of("src/main/java/com/sovereign/connect/bus/runtime/nats/NatsSubjectBuilder.java"));
    assertThat(source).contains(
        "import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec");
    assertThat(source).contains(
        "import com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec");
}
```

The MIR may choose a stronger parser/ArchUnit-like method if available, but file-specific import checks are sufficient for hardening seed scope.

### 4.12 Manifest payload classes are absent

No `AdapterManifestProposal`, `ManifestProposal`, `AcceptedManifestView`, or manifest admission production types exist yet.

Therefore lifecycle stream hardening must not depend on manifest payload classes. Use an existing or minimal lifecycle fixture such as:

```text
payloadType = sc.lifecycle.adapter-announce.v1
```

`ScPayloadTypeRegistry.LIFECYCLE_ADAPTER_ANNOUNCE_V1` is already present and sufficient for a lifecycle retention fixture.

### 4.13 Boundary invariants are clean

Current NATS runtime package imports are consistent with SC-B ownership:

```text
bus.contract.*
bus.runtime.dispatch.*
bus.runtime.port.*
bus.runtime.serialization.*
io.nats.client.*
```

Confirmed exclusions to preserve:

```text
NO core.** imports from bus.runtime.nats
NO adapter.** imports from bus.runtime.nats
NO integration.** imports from bus.runtime.nats
NO SC-C import of io.nats.*
NO SC-C import of bus.runtime.nats.*
```

---

## 5. Recommended MIR implementation surface

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  JetStreamStreamApplicator.java       // idempotent stream ensure against live JSM
  JetStreamConsumerApplicator.java     // durable consumer creation for SCB_LIFECYCLE_V1
  JetStreamHardeningConfiguration.java // seed consumer config constants/policies
  JetStreamConfigurationReport.java    // optional report for tests and evidence
  [NatsScBusPort.java]                 // extend only if JetStream publish is in scope
```

Alternative names are acceptable if responsibility is explicit.

No new persistence, no Flyway migration, no SC-C integration, and no SC-D runtime classes are required.

### 5.1 Minimal API shape

```java
public final class JetStreamStreamApplicator {
    public JetStreamConfigurationReport ensureStreams(Connection connection);
}

public final class JetStreamConsumerApplicator {
    public JetStreamConsumerReport ensureLifecycleConsumer(Connection connection);
}
```

or a combined component:

```java
public final class NatsJetStreamConfigurator {
    public JetStreamHardeningReport ensureAll(Connection connection);
}
```

Report types should expose at least:

```text
stream name
subjects
created / alreadyExisted / updated / conflict
consumer durable name
filter subject
ack policy
ack wait
max deliver
deliver policy
```

---

## 6. Seed invariants

```text
INV-JSH-001  JetStream stream application is idempotent. Repeated application of an
             equivalent stream configuration must not fail.

INV-JSH-002  Incompatible existing stream configuration must be observable as conflict
             or failure. Silent overwrite is not acceptable.

INV-JSH-003  JetStream ack is transport confirmation only. It never equals command
             success, manifest acceptance, adapter activation, or any semantic terminal state.

INV-JSH-004  SCB_LIFECYCLE_V1 durable consumer must be validated against a live
             JetStream server. Ephemeral-only fallback requires STOP report.

INV-JSH-005  DispatchStateWritePort and DispatchObservationPort remain the SC-B
             persistence boundary. JetStream retention does not replace them.

INV-JSH-006  NatsStreamConfiguration stream names and subject patterns are not changed
             by this MIR unless a separate SDD patch authorizes the change.

INV-JSH-007  bus.runtime.nats does not import core.**, adapter.**, or integration.**.

INV-JSH-008  Reconnect/drain gaps must be recorded as DEBT-B-NATS-004 /
             DEBT-B-NATS-005 if not implemented. Informal notes are not accepted.

INV-JSH-009  Hardening must not introduce adapter manifest runtime, route assignment
             runtime, command admission runtime, or provider execution.
```

---

## 7. Required tests

```text
JetStreamStreamApplicatorTest
  appliesAllFiveStreamsToLiveJetStreamServer
  streamApplicationIsIdempotent
  incompatibleExistingStreamIsReportedAsConflictOrFailure
  doesNotChangeStreamNamesOrSubjectPatterns
```

```text
JetStreamConsumerApplicatorTest
  createsDurableLifecycleConsumer
  lifecycleConsumerPolicyIsExplicit
  lifecycleConsumerIsValidatedAgainstLiveJetStreamServer
```

```text
JetStreamLifecycleRetentionTest
  lifecycleSubjectIsRetainedBySCB_LIFECYCLE_V1
  durableLifecycleConsumerReceivesOrReplaysLifecycleMessage
```

```text
JetStreamAckBoundaryTest
  jetStreamPublishAckIsTransportOnly
  jetStreamConsumerAckIsTransportOnly
  jetStreamAckDoesNotCreateSemanticSuccessOrManifestAcceptance
```

```text
ScBusNatsArchitectureHardeningTest
  natsPackageUsesSerializationUtilitiesThroughExplicitImportsOrTypedReferences
  natsPackageDoesNotImportCoreAdapterOrIntegration
  scCAndScDDoNotImportBusRuntimeNats
  jetStreamConfiguratorDoesNotImportDispatchStateOrObservationRepositories
```

If reconnect/drain is implemented:

```text
NatsReconnectDrainHardeningTest
  reconnectResubscribesLifecycleConsumer
  drainCompletesWithoutMessageLossForSeedLifecycleFixture
```

If reconnect/drain is deferred, implementation report MUST record `DEBT-B-NATS-004` and/or `DEBT-B-NATS-005`.

---

## 8. Test substrate decision

```text
Preferred:
  Testcontainers NATS with JetStream enabled, when Docker is available.

Accepted fallback:
  NatsLocalServer using jnats-server-runner with JetStream enabled, provided the
  implementation report justifies Docker unavailability or CI constraint.

Forbidden:
  mocked broker used to satisfy live JetStream acceptance.
```

NatsLocalServer is acceptable for this MIR only if tests exercise real JetStream APIs (`JetStreamManagement`, stream creation/update, consumer creation, retention/consume/replay). Starting a server with JetStream enabled but not applying streams/consumers is not sufficient.

---

## 9. Expected test delta

```text
Pre-MIR-JSH baseline: 454 tests

Expected additions:
  JetStreamStreamApplicatorTest:          ~6-8 tests
  JetStreamConsumerApplicatorTest:        ~4 tests
  JetStreamLifecycleRetentionTest:        ~3 tests
  JetStreamAckBoundaryTest:               ~2-3 tests
  ScBusNatsArchitectureHardeningTest:     ~2-5 tests
  Optional reconnect/drain smoke tests:   ~0-4 tests

Expected total after MIR:
  >= 469 tests, 0 failures, 0 errors, 0 skipped
```

The exact count may vary if reconnect/drain behavior is implemented instead of retained as debt.

---

## 10. Hard stops

```text
STOP-JSH-001  SCB_LIFECYCLE_V1 durable consumer is omitted, silently downgraded
              to ephemeral-only, or replaced by NATS Core-only behavior.

STOP-JSH-002  Stream application tests instantiate configuration objects but do not
              apply them against a live JetStream server.

STOP-JSH-003  JetStream publish ack or consumer ack is treated as command success,
              manifest acceptance, adapter activation, or any other semantic terminal state.

STOP-JSH-004  Reconnect/drain gaps are retained as informal notes instead of
              DEBT-B-NATS-004 / DEBT-B-NATS-005 implementation-report entries.

STOP-JSH-005  Hardening expands into adapter manifest runtime, AdapterManifestProposal
              live transport, route assignment runtime, provider execution,
              command admission runtime, or TCK executable harness.

STOP-JSH-006  NatsStreamConfiguration stream names or subject patterns are modified
              without a separate SDD patch.

STOP-JSH-007  DispatchStateWritePort or DispatchObservationPort is removed, bypassed,
              or replaced by JetStream retention in the dispatch flow.

STOP-JSH-008  Hardening uses a mocked broker to satisfy live JetStream acceptance.

STOP-JSH-009  Implementation adds SC-C imports of io.nats.* or bus.runtime.nats.*.

STOP-JSH-010  Implementation adds bus.runtime.nats imports of core.**, adapter.**,
              or integration.**.

STOP-JSH-011  MIR claims JetStream hardening closes adapter lifecycle, manifest runtime,
              route assignment, command admission, or productive SC-D adapter readiness.
```

---

## 11. Deferred items / retained debt

The following are expected to remain out of scope unless the MIR explicitly includes them:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in JetStream hardening MIR.
DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.
DEBT-B-NATS-006 — command/response durable consumers deferred until command/response runtime needs are specified.
DEBT-B-NATS-007 — full DLQ production policy deferred.
DEBT-B-NATS-008 — Testcontainers CI standardization deferred if NatsLocalServer remains the active fallback.
```

Any retained debt MUST appear in the implementation report as a machine-searchable `DEBT-*` entry.

---

## 12. MIR readiness checklist

Before opening the MIR execution package, verify:

```text
[ ] SDD-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate is accepted.
[ ] CSA-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-merged is used as direct input.
[ ] MIR scope is limited to SC-B JetStream hardening.
[ ] MIR requires live broker evidence for stream application.
[ ] MIR requires durable SCB_LIFECYCLE_V1 consumer.
[ ] MIR forbids mocked broker as acceptance proof.
[ ] MIR preserves JetStream ack as transport-only.
[ ] MIR preserves DispatchState/DispatchObservation ownership.
[ ] MIR records reconnect/drain debt IDs if deferred.
[ ] MIR excludes Adapter Manifest runtime and route assignment runtime.
```

---

## 13. CSA result

```text
CSA result:
  Approvable for MIR descent.

Open next:
  MIR-SOV-SC-B-JETSTREAM-HARDENING-001 v0.1.0-draft

Recommended branch:
  feat/sc-b-mir-030-jetstream-hardening

Recommended commit:
  feat(sc-b): harden jetstream stream and lifecycle consumer setup
```

This MIR should be treated as an industrialization hardening step for SC-B. It must not expand into SC-D adapter lifecycle or manifest runtime.
