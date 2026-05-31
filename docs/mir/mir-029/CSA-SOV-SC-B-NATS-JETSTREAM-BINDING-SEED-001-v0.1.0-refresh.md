# CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

## Pre-MIR Code Surface Audit — SC-B NATS / JetStream Binding Seed

```text
Document ID:  CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Title:        Pre-MIR Code Surface Audit — SC-B NATS / JetStream Binding Seed
Version:      v0.1.0-refresh
Status:       Refresh / Post-MU-028 / Pre-MIR-B
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B / SC-D wire boundary
Scope:        Code-surface audit before MIR-B NATS / JetStream binding seed
Baseline:     sovereign-connect-pre-029.zip
Input SDD:    SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
Input PDR:    PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate
Prior CSA:    CSA-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-merged
Tests:        411 / 0 failures / 0 errors / 0 skipped
EIB tests:    56 / 0 failures / 0 errors / 0 skipped
```

---

# 1. Executive verdict

```text
Verdict: Approvable for MIR-B descent with bounded live-binding scope.

Open next:
  MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

Do not open as:
  full SC-D adapter implementation;
  full lifecycle runtime;
  manifest runtime;
  capability registry runtime;
  semantic command execution;
  terminal request-state integration.
```

MU-028 closed the broker-free wire substrate gap. The codebase now has the required serialization and subject-token primitives for a first physical binding seed:

```text
ScSubjectIdTokenCodec
ScCorrelationTokenCodec
ScSubjectTokenValidator
ScWireEnvelopeKind
ScJsonWireEnvelope
ScPayloadTypeRegistry
ScJsonWireCodec
WireEnvelopeValidator
```

The first NATS MIR should therefore introduce the physical binding as a **ScBusPort implementation over NATS**, not as a redesign of SC-B or SC-D.

The recommended first live end-to-end path is:

```text
SC-C OutboxEntry TIMER_FIRED_SIGNAL
  → ScOutboxDispatchReadPort
  → OutboxEntryDispatchProjector
  → DispatchCandidate(lane=EVENT, logicalTopic=sc-c.timer-fired)
  → RuntimeDispatchService
  → NatsScBusPort
  → NATS subject sc.v1.{habitatRoute}.event.timer.sc-c.timer-fired
  → test subscriber receives SC-JSON-WIRE-v1 envelope
  → dispatch state/observation remain SC-B-owned JDBC records
```

Command and response fixtures may be added in MIR-B, but they should be direct wire/bus fixtures, not claims of productive adapter execution.

---

# 2. Confirmed baseline after MU-028

## 2.1 Validation evidence

Surefire reports in the provided service show:

```text
sovereign-connect:
  411 tests, 0 failures, 0 errors, 0 skipped

EIB:
  56 tests, 0 failures, 0 errors, 0 skipped
```

Relevant MU-028 test subset:

```text
ScSubjectIdTokenCodecTest:           5 tests
ScCorrelationTokenCodecTest:         5 tests
ScSubjectTokenValidatorTest:         3 tests
ScPayloadTypeRegistryTest:           4 tests
ScJsonWireCodecTest:                 6 tests
WireEnvelopeValidatorTest:           6 tests
ScBusSerializationArchitectureTest:  4 tests
```

The patch for opaque payload type validation is present:

```text
WireEnvelopeValidatorTest.acceptsUnknownButSyntacticallyValidPayloadType
payloadType = sc.command.future.v1
```

This confirms the required invariant:

```text
syntactic payloadType validation != registry membership
```

## 2.2 Git/worktree note

The extracted zip contains a noisy worktree with historical/documentation modifications relative to its embedded `origin/develop`. The committed MU-028 surface and Surefire reports are inspectable and coherent, but the MIR-B execution package should still instruct Codex to verify locally:

```powershell
git status --short
git diff --name-status origin/develop...HEAD
git log --oneline --decorate --left-right origin/develop...HEAD
```

This CSA treats the relevant code surface as post-MU-028 because the serialization utilities are present and tested.

---

# 3. Confirmed production surface inventory

## 3.1 Bus contract shapes present

```text
com.sovereign.connect.bus.contract.ScCommandEnvelope
com.sovereign.connect.bus.contract.ScEventEnvelope
com.sovereign.connect.bus.contract.ScResponseEnvelope
com.sovereign.connect.bus.contract.ScMessageMetadata
com.sovereign.connect.bus.contract.ScRoutingKey
com.sovereign.connect.bus.contract.ScBusLane
com.sovereign.connect.bus.contract.ScResponseMetadata
com.sovereign.connect.bus.contract.ScResponseKind
com.sovereign.connect.bus.contract.ScResponseWarning
```

`ScRoutingKey` already has physical-binding-relevant fields:

```text
lane
partitionKey
topic
habitatId
adapterId
deviceId
endpointId
```

However, the current runtime dispatch path does not populate all of them. See §5.

## 3.2 ScBusPort remains the correct extension point

Current surface:

```java
interface ScBusPort {
    DispatchOutcome publishCommand(ScCommandEnvelope<?> envelope);
    DispatchOutcome publishEvent(ScEventEnvelope<?> envelope);
    DispatchOutcome publishResponse(ScResponseEnvelope<?> envelope);
    void registerCommandHandler(String topicOrRoute, ScCommandHandler handler);
    void registerEventHandler(String topicOrRoute, ScEventHandler handler);
    void registerResponseHandler(String topicOrRoute, ScResponseHandler handler);
}
```

MIR-B should implement:

```text
NatsScBusPort implements ScBusPort
```

No change to `ScBusPort` is required for the first seed.

## 3.3 RuntimeDispatchService is replaceable by port injection

`RuntimeDispatchService` receives `ScBusPort` by constructor injection. This remains a clean extension seam:

```text
RuntimeDispatchService
  -> ScBusPort
  -> DispatchCandidateReadPort
  -> DispatchStateWritePort
  -> DispatchObservationPort
  -> EnvelopeValidationService
  -> RoutingKeyValidationService
  -> CorrelationValidationService
```

MIR-B can replace `InMemoryScBusPort` with `NatsScBusPort` without modifying SC-C.

## 3.4 Serialization utilities are present

Confirmed classes:

```text
com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec
com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec
com.sovereign.connect.bus.runtime.serialization.ScSubjectTokenValidator
com.sovereign.connect.bus.runtime.serialization.ScWireEnvelopeKind
com.sovereign.connect.bus.runtime.serialization.ScJsonWireEnvelope
com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry
com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec
com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator
```

Important behavior:

```text
- scid1_ encodes canonical IDs with base64url/no padding.
- scid1_ rejects empty payload tokens.
- correlation subject tokens are UUID hex without hyphens.
- subject-token validator rejects '.', '+', '/', '=', '*' and '>'.
- payloadType grammar is strict: sc.<domain>.<type-name>.v<major>.
- unknown but syntactically valid payloadTypes are accepted/preserved opaquely.
- wire envelope kind/lane mismatch is rejected.
```

## 3.5 NATS dependency remains absent

Confirmed in `pom.xml`:

```text
io.nats:jnats      absent
Testcontainers     absent
NATS classes       absent
NatsScBusPort      absent
NatsSubjectBuilder absent
JetStream config   absent
```

This is the intended pre-MIR-B state.

## 3.6 Dispatch persistence and observation persistence are present

Confirmed migrations:

```text
V100__sc_b_dispatch_state_persistence.sql
V101__sc_b_dispatch_observation_persistence.sql
```

Confirmed runtime persistence classes:

```text
JdbcDispatchStateRepository
JdbcDispatchObservationRepository
```

MIR-B must preserve these as the authoritative technical dispatch state/observation substrate. JetStream must not replace them.

---

# 4. Confirmed architecture tests that MIR-B must update

Current tests intentionally reject broker dependencies:

```text
ScBusArchitectureTest.pomsDoNotIntroducePhysicalBusBindingDependency
ScBusHardeningArchitectureTest.pomsDoNotIntroducePhysicalBusBindingDependencies
ScBusObservationPersistenceArchitectureTest.noBrokerDependencyIntroduced
ScBusOutboxBridgeArchitectureTest.<broker dependency guard>
ScBusSerializationArchitectureTest.serializationPackageDoesNotImportNatsOrTestcontainers
```

MIR-B must update the relevant broker guards **in the same commit that adds `io.nats:jnats`**.

Required policy:

```text
- Allow io.nats:jnats only as the intentional SC-B NATS binding dependency.
- Keep rejecting Vert.x, Redis/Lettuce, gRPC, Kafka, RabbitMQ, MQTT, WebSocket and unrelated broker/client APIs.
- Keep serialization package free of NATS/Testcontainers imports.
- Keep core.** free of bus.runtime and NATS imports.
- Keep bus.runtime.serialization free of NATS imports.
```

Recommended replacement pattern:

```text
pomsAllowOnlyIntentionalNatsBindingDependency
```

The test should not simply remove broker checks. It should become an allowlist/denylist test.

---

# 5. Critical routing gap for MIR-B scoping

`DispatchCandidate` currently carries:

```text
dispatchRecordId
sourceRecordId
lane
logicalTopic
partitionKey
correlationId
causationId
messageId
```

`OutboxEntryDispatchProjector` sets:

```text
logicalTopic = OutboxEntry.logicalTopic
partitionKey = OutboxEntry.habitatId
```

`RuntimeDispatchService.publish(...)` then builds:

```java
new ScRoutingKey(
  candidate.lane(),
  candidate.partitionKey(),
  candidate.logicalTopic(),
  null,
  null,
  null,
  null
)
```

Therefore, the runtime dispatch path currently reaches `ScBusPort` with:

```text
routingKey.habitatId  = null
routingKey.adapterId  = null
routingKey.deviceId   = null
routingKey.endpointId = null
routingKey.partitionKey = habitatId
routingKey.topic = logicalTopic
```

This is not a defect in MU-024..MU-028. It is the correct result of keeping prior seeds broker-neutral and not inventing lifecycle route assignment early.

But it has direct MIR-B consequences:

```text
1. The first RuntimeDispatchService → NATS live E2E should be EVENT / timer-fired.
2. COMMAND adapter-scoped subjects cannot be derived from the runtime candidate path yet.
3. COMMAND/RESPONSE physical subject tests must use manually constructed envelopes/routing keys or explicit route fixtures.
4. MIR-B must not claim real adapter command routing until lifecycle/manifest route assignment provides adapterRoute and target scope.
```

Mandatory MIR-B rule:

```text
MIR-B MUST NOT silently infer adapterRoute/deviceId/endpointId from DispatchCandidate fields that do not exist.
```

Allowed seed strategies:

```text
Strategy A — recommended for MU-029:
  Keep RuntimeDispatchService E2E limited to EVENT/timer-fired.
  Add direct NatsScBusPort tests for COMMAND/RESPONSE using explicit ScRoutingKey fixtures.
  Record adapter-scoped command routing as deferred to lifecycle/manifest route assignment.

Strategy B — not recommended for first MIR-B:
  Extend DispatchCandidate and the outbox bridge to carry adapter/target routing fields.
  This touches H2 surfaces and risks scope creep into lifecycle/manifest before those runtimes exist.
```

This CSA recommends Strategy A.

---

# 6. PDR Command Payload v0.3.0 implications

The approved `PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate` defines:

```text
payloadType = sc.command.scd.v1
payloadSchemaVersion = 1.0

payloadType = sc.response.scd-execution-result.v1
payloadSchemaVersion = 1.0
```

It also adds:

```text
operationKind = INVOKE_CAPABILITY
capabilityRef optional / required for INVOKE_CAPABILITY
capabilityRef validation against accepted adapter manifest at admission time
```

MIR-B implications:

```text
- MIR-B may use ScdCommand / ScdExecutionResult JSON fixtures.
- MIR-B should not implement manifest validation.
- MIR-B should not claim dynamic capability extensibility runtime.
- MIR-B may include an optional INVOKE_CAPABILITY wire fixture only if marked as wire-only.
- MIR-B must not use payloads stub as the only command fixture now that the PDR exists.
```

Recommended command fixture policy:

```text
Use JSON object payloads or test-local records representing ScdCommand v1.
Do not create a product adapter.
Do not make adapter execution result terminal semantic authority.
Do not implement Adapter Manifest or Capability Semantics in MIR-B.
```

Whether MIR-B introduces production Java records named `ScdCommand` / `ScdExecutionResult` should be decided by the MIR. If it does, architecture tests that currently assert no production `ScdCommand.java` must be intentionally updated. This CSA recommends avoiding a production `ScdCommand.java` in MIR-B unless the MIR explicitly scopes SC-D payload implementation as part of the binding seed. JSON fixture payloads are sufficient for a binding seed.

---

# 7. MIR-B recommended scope

## 7.1 Include

```text
NatsScBusPort implements ScBusPort.
NatsSubjectBuilder.
NatsConnectionFactory / NatsClientConfiguration or equivalent minimal config.
NatsDispatchMapper or equivalent envelope-to-subject resolver.
NatsStreamConfiguration seed for SCB_*_V1 streams if JetStream is in first seed.
NatsLifecycleChannelMapper for subject mapping only, not lifecycle runtime.
Use ScJsonWireCodec for payload bytes.
Use WireEnvelopeValidator before publish.
Use ScSubjectIdTokenCodec for canonical ID subject tokens.
Use ScCorrelationTokenCodec for response subject tokens.
Update broker architecture tests to allow only NATS as intentional binding.
Live NATS test substrate using Testcontainers or an explicitly documented local NATS server fixture.
```

## 7.2 Exclude

```text
Full lifecycle runtime.
Adapter admission authority.
Adapter Manifest runtime.
Capability registry runtime.
Real provider adapter.
Productive SC-D fact family shapes.
Semantic retry.
Terminal request-state ownership.
SC-C direct NATS publishing.
SC-C imports of NATS or bus runtime.
Replacing DispatchStateWritePort or DispatchObservationPort with JetStream state.
```

## 7.3 First live tests

Minimum live tests should include:

```text
1. NatsSubjectBuilderTest
   - event timer subject from habitatId/logicalTopic;
   - command subject from explicit routing fixture;
   - response subject from correlation token;
   - rejects unsafe route tokens;
   - uses ScSubjectIdTokenCodec and ScCorrelationTokenCodec.

2. NatsScBusPortEventIntegrationTest
   - local/Testcontainers NATS server;
   - register event subscriber or raw NATS subscriber;
   - publish EVENT envelope through NatsScBusPort;
   - received bytes decode through ScJsonWireCodec;
   - envelopeKind/routingKey.lane validated by WireEnvelopeValidator.

3. RuntimeDispatchToNatsEventIntegrationTest
   - SC-C outbox TIMER_FIRED_SIGNAL row or test candidate;
   - RuntimeDispatchService uses NatsScBusPort;
   - event is received on sc.v1.{habitatRoute}.event.timer.sc-c.timer-fired;
   - DispatchState becomes DISPATCHED;
   - DispatchObservationRecord persists DISPATCHED;
   - no semantic success is inferred.

4. NatsScBusPortCommandWireFixtureTest
   - direct ScCommandEnvelope with payloadType sc.command.scd.v1;
   - payload JSON follows PDR ScdCommand v1 fixture;
   - operationKind SET and optional INVOKE_CAPABILITY fixture are wire-only;
   - no adapter product execution claim.

5. NatsScBusPortResponseWireFixtureTest
   - direct ScResponseEnvelope with payloadType sc.response.scd-execution-result.v1;
   - response subject uses 32 lowercase UUID hex token;
   - ScdExecutionResult.status is adapter evidence only.
```

## 7.4 Expected test delta

Suggested initial expectation:

```text
sovereign-connect baseline: 411 tests
MIR-B expected delta: +15 to +25 tests
Expected full regression: >=426 tests, 0 failures, 0 errors, 0 skipped
EIB reports, if run: 56 tests, 0 failures, 0 errors, 0 skipped
```

Exact count should be fixed by the execution package after the MIR settles.

---

# 8. JetStream scope decision

The SDD is NATS Core + JetStream. MIR-B must decide whether JetStream is included as live runtime behavior or as configuration surface only.

Recommended first seed:

```text
NATS Core live publish/subscribe path: required.
JetStream stream configuration: included if test substrate supports JetStream reliably.
JetStream ack semantics: covered only as broker delivery evidence, never semantic success.
JetStream durability replacing SC-B JDBC state/observations: forbidden.
```

If Testcontainers/local NATS JetStream introduces CI friction, the MIR may split:

```text
MU-029A — NATS Core live binding seed + subject grammar + SC-JSON-WIRE-v1
MU-029B — JetStream stream/consumer hardening seed
```

This CSA does not require that split. It only requires the MIR to make the JetStream seed scope explicit.

---

# 9. Lifecycle-channel scope decision

The lifecycle SDD is accepted, but no lifecycle runtime exists.

MIR-B may include:

```text
NatsLifecycleChannelMapper
logical lifecycle channel -> physical subject mapping tests
route assignment push subject construction
AdapterRouteReady response/event subject construction tests
```

MIR-B must not include:

```text
adapter admission authority
challenge verification
SAVK/trust bundle validation
manifest acceptance
ACTIVE transition authority
route revision runtime
```

The physical mapping may be tested with pure fixtures. Lifecycle execution remains downstream.

---

# 10. Required architecture-test updates for MIR-B

MIR-B must update architecture tests to reflect intentional NATS binding:

```text
ACTION-MIRB-ARCH-001 — Replace no-broker dependency assertions with NATS-only allowlist assertions.
ACTION-MIRB-ARCH-002 — Add ScBusNatsArchitectureTest.
ACTION-MIRB-ARCH-003 — Ensure NATS package does not import core.** or adapter.**.
ACTION-MIRB-ARCH-004 — Ensure serialization package does not import NATS/Testcontainers.
ACTION-MIRB-ARCH-005 — Ensure SC-C production code does not import io.nats, NatsScBusPort or bus.runtime.nats.
ACTION-MIRB-ARCH-006 — Ensure JetStream/NATS ack is never used to transition semantic request state.
```

Suggested new test file:

```text
src/test/java/com/sovereign/connect/bus/nats/ScBusNatsArchitectureTest.java
```

Suggested assertions:

```text
natsPackageDoesNotImportCore
natsPackageDoesNotImportAdapter
natsPackageUsesSerializationPackage
natsSubjectBuilderUsesScid1Codec
natsResponseSubjectsUseCorrelationTokenCodec
noScCCodeImportsNats
pomsAllowOnlyIntentionalNatsBinding
```

---

# 11. Stop conditions for MIR-B execution package

The MIR-B execution package should stop if Codex needs to:

```text
- modify SC-C to publish directly to NATS;
- make JetStream state replace DispatchStateWritePort or DispatchObservationPort;
- infer adapterRoute/deviceId/endpointId from missing DispatchCandidate fields;
- implement Adapter Manifest runtime;
- implement Capability Semantics runtime;
- implement real provider adapter execution;
- make ScdExecutionResult terminal semantic authority;
- add non-NATS broker dependencies;
- import core.** from bus.runtime.nats;
- import NATS/Testcontainers from bus.runtime.serialization;
- require Spring-specific adapter ABI;
- require Java class names as payloadType values.
```

---

# 12. Search ledger

```text
pom.xml:
  io.nats absent.
  Testcontainers absent.
  Jackson + jsr310 present.

bus.contract:
  envelope carrier shapes present.
  ScRoutingKey has habitatId/adapterId/deviceId/endpointId fields.

bus.runtime.dispatch:
  RuntimeDispatchService uses ScBusPort by interface.
  RuntimeDispatchService currently sets ScRoutingKey habitatId/adapterId/deviceId/endpointId to null from DispatchCandidate path.

bus.runtime.serialization:
  MU-028 classes present and tested.
  No NATS/Testcontainers import.

integration.scledgerdispatch:
  TIMER_FIRED_SIGNAL + sc-c.timer-fired maps to EVENT.
  partitionKey carries habitatId.
  no NATS subject encoding.

migrations:
  V100 dispatch state persistence present.
  V101 dispatch observation persistence present.

architecture tests:
  several tests currently reject io.nats and must be intentionally updated by MIR-B.

SC-D payload:
  no production ScdCommand or ScdExecutionResult class exists.
  PDR v0.3.0 defines wire shape and payloadType values.
```

---

# 13. Final recommendation

```text
Open:
  MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.1.0-draft

Recommended MU slot:
  MU-029

Recommended branch:
  feat/sc-b-mir-029-nats-jetstream-binding-seed

Recommended commit:
  feat(sc-b): add nats jetstream binding seed
```

The MIR should explicitly choose the Strategy A scope from §5:

```text
RuntimeDispatchService live E2E: EVENT/timer-fired only.
COMMAND/RESPONSE: direct wire fixtures with explicit routing keys.
Lifecycle: subject mapping fixtures only.
Manifest/capability runtime: deferred.
```

This preserves the architecture:

```text
SC-C owns semantic authority.
SC-B transports, correlates and records technical delivery.
SC-D conforms through public wire protocol.
NATS is a physical binding, not the adapter ABI.
```
