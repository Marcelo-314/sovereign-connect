# MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

## Materialization Increment Record — SC-B NATS / JetStream Binding Seed

```text
Document ID:  MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Title:        Materialization Increment Record — SC-B NATS / JetStream Binding Seed
Version:      v0.2.0-candidate
Status:       Candidate
Date:         2026-05-30
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-B / SC-D wire boundary
Track:        SC-B Physical Binding / NATS Core + JetStream Seed
MU ID:        MU-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
MU Slot:      MU-029
```

---

## Changelog v0.2.0-candidate

Candidate promotion after MIR review and CSA merge.

This version:

```text
1. Consumes CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.1.0-draft findings.
2. Makes ScdCommand and ScdExecutionResult production wire payload records in scope for MIR-B.
3. Places ScdCommand / ScdExecutionResult under bus.contract.scd as JVM reference bindings for public wire payload shapes.
4. Requires ScPayloadTypeRegistry constants for sc.command.scd.v1 and sc.response.scd-execution-result.v1.
5. Makes Testcontainers NATS the default test substrate; local nats-server is allowed only with implementation-report justification.
6. Requires io.nats:jnats and the architecture-test allowlist patch to be introduced in the same implementation commit.
7. Clarifies that RuntimeDispatchService live E2E remains EVENT / TIMER_FIRED_SIGNAL only.
8. Clarifies that COMMAND / RESPONSE are direct wire fixtures using production payload records, not productive adapter execution.
9. Requires any live JetStream evidence to configure streams against a real NATS server with JetStream enabled, not merely construct Java config objects.
10. Preserves that NATS/JetStream ack is transport evidence only and never semantic success.
```

---

## Changelog v0.1.0-draft

Initial draft.

This version:

```text
1. Opens MU-029 as the first physical SC-B binding seed after MU-024..MU-028.
2. Authorizes an intentional NATS binding dependency only inside the SC-B NATS binding surface.
3. Preserves SC-B as transport/correlation and rejects any semantic-authority transfer to NATS or JetStream.
4. Selects bounded Strategy A from the pre-MIR CSA: RuntimeDispatchService live E2E is EVENT / TIMER_FIRED_SIGNAL only.
5. Allows COMMAND and RESPONSE coverage as direct wire/bus fixtures with explicit ScRoutingKey values, not productive adapter execution.
6. Allows lifecycle physical subject mapping fixtures only; lifecycle runtime/admission remains downstream.
7. Requires SC-JSON-WIRE-v1 serialization utilities from MU-028 for all NATS payload bytes.
8. Requires ScdCommand / ScdExecutionResult wire fixtures from PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate.
9. Requires architecture-test updates from broker-forbidden to NATS-only intentional allowlist.
10. Keeps Adapter Manifest, Capability Semantics, productive SC-D adapters and lifecycle runtime outside the MIR.
```

---

## 0. MIR boundary

This MIR authorizes a future implementation attempt for the **first NATS Core + JetStream binding seed** over the already validated SC-B runtime substrate.

This MIR does **not** include an execution prompt, context file, acceptance map, Codex instructions, or implementation package. Those artifacts must be produced separately if this MIR is promoted to candidate and accepted for execution descent.

This MIR is not an adapter implementation. It is not a lifecycle runtime. It is not Adapter Manifest runtime. It is not Capability Semantics runtime. It is not a productive `ScdCommand` execution engine. It is the first physical binding seed for SC-B.

Canonical purpose:

```text
Introduce a NATS Core + JetStream physical binding as an implementation of ScBusPort,
using the existing SC-B contracts, serialization utilities, dispatch state persistence
and dispatch observation persistence, without redefining SC-B or SC-D.
```

---

## 1. Depends on

```text
ADR-SOV-SC-SERIALIZATION-001 v0.2.0-candidate
NT-SOV-SC-D-WIRE-PROTOCOL-SUFFICIENCY-001 v0.2.0-candidate
SDD-SOV-SC-B-LIFECYCLE-CHANNEL-001 v0.2.0-candidate
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
CSA-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-merged
CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.1.0-refresh
PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001 v1.0.0-accepted / Validated L4
```

---

## 2. Related / downstream

```text
PDR-SOV-SC-D-ADAPTER-MANIFEST-001
PDR-SOV-SC-D-CAPABILITY-SEMANTICS-001
PDR-SOV-SC-D-PROTOCOL-001
SDD-SOV-SC-D-ADAPTER-MANIFEST-001
SDD-SOV-SC-D-CAPABILITY-REGISTRY-001
TCK-SOV-SC-D-ADAPTER-MANIFEST-001
TCK-SOV-SC-D-CAPABILITY-CONFORMANCE-001
MIR-SOV-SC-B-JETSTREAM-HARDENING-001, if JetStream is split after this seed
MIR-SOV-SC-B-LIFECYCLE-RUNTIME-SEED-001, if lifecycle runtime descends later
MIR-SOV-SC-D-ADAPTER-MANIFEST-SEED-001, deferred until manifest protocol is specified
```

---

## 3. Baseline state

The baseline before MU-029 is:

```text
MU-024 — SC-B Abstract Bus Seed                       Validated L4
MU-025 — Dispatch State Persistence                   Validated L4
MU-026 — SC-C Outbox → SC-B DispatchCandidate Bridge  Validated L4
MU-027 — Dispatch Observation Persistence             Validated L4
MU-028 — Serialization Utilities Seed                 Validated L4
```

Validated runtime/wire substrate now exists:

```text
ScBusPort
ScCommandEnvelope / ScEventEnvelope / ScResponseEnvelope
ScMessageMetadata
ScRoutingKey
RuntimeDispatchService
DispatchCandidateReadPort
DispatchStateWritePort / JdbcDispatchStateRepository
DispatchObservationPort / JdbcDispatchObservationRepository
ScOutboxDispatchReadPort
ScLedgerDispatchCandidateReadAdapter
ScSubjectIdTokenCodec
ScCorrelationTokenCodec
ScSubjectTokenValidator
ScWireEnvelopeKind
ScJsonWireEnvelope
ScPayloadTypeRegistry
ScJsonWireCodec
WireEnvelopeValidator
```

Known pre-MIR-B code surface:

```text
- io.nats:jnats is absent.
- Testcontainers is absent.
- NatsScBusPort is absent.
- NatsSubjectBuilder is absent.
- JetStream config classes are absent.
- Lifecycle runtime is absent.
- Production wire payload records `ScdCommand` and `ScdExecutionResult` are absent and MUST be introduced by MIR-B as protocol reference bindings.
- `ScPayloadTypeRegistry` does not yet expose `COMMAND_SCD_V1` or `RESPONSE_SCD_EXECUTION_RESULT_V1`.
```

Known runtime routing limitation:

```text
RuntimeDispatchService currently builds ScRoutingKey from DispatchCandidate as:
  lane          = candidate.lane()
  partitionKey  = candidate.partitionKey()
  topic         = candidate.logicalTopic()
  habitatId     = null
  adapterId     = null
  deviceId      = null
  endpointId    = null
```

This is acceptable for the first event binding E2E, but it is not sufficient for productive adapter-scoped command routing.

---

## 4. Problem statement

Sovereign Connect now has a broker-free SC-B runtime path and serialization substrate:

```text
SC-C outbox row
  → ScOutboxDispatchReadPort
  → OutboxEntryDispatchProjector
  → DispatchCandidate
  → RuntimeDispatchService
  → ScBusPort
  → in-process handler
```

The missing transition is:

```text
ScBusPort
  → physical NATS Core / JetStream binding
  → language-neutral wire delivery
  → external SC-D adapter participation path, later
```

The architectural risk is to solve this by letting the physical broker become the contract:

```text
NATS subjects become canonical identity.
JetStream ack becomes semantic success.
NATS retention replaces DispatchState / DispatchObservation persistence.
SC-C publishes directly to NATS.
Adapters become NATS/JVM/SDK specific instead of protocol-conformant.
```

This MIR prevents those outcomes.

---

## 5. Goals

### G-MIR-029-001 — Intentional NATS binding dependency

Introduce `io.nats:jnats` as an explicit and narrowly scoped SC-B binding dependency.

### G-MIR-029-002 — NatsScBusPort

Implement a physical `ScBusPort` binding over NATS.

### G-MIR-029-003 — Subject grammar implementation

Implement subject construction for the seed subset of:

```text
EVENT
COMMAND fixture
RESPONSE fixture
Lifecycle mapping fixture
```

### G-MIR-029-004 — SC-JSON-WIRE-v1 delivery

Use MU-028 `ScJsonWireCodec` and `WireEnvelopeValidator` for NATS message bytes.

### G-MIR-029-005 — Runtime E2E event path

Prove one live path through:

```text
SC-C outbox TIMER_FIRED_SIGNAL
  → bridge
  → RuntimeDispatchService
  → NatsScBusPort
  → NATS subscriber receives SC-JSON-WIRE-v1 envelope
```

### G-MIR-029-006 — Technical delivery evidence only

Preserve `DispatchStateWritePort` and `DispatchObservationPort` as the technical dispatch-state/diagnostics substrate.

### G-MIR-029-007 — Command/response wire fixtures

Prove command and response subject/wire behavior with explicit `ScRoutingKey` fixtures and PDR-approved payload shapes.

### G-MIR-029-008 — JetStream seed profile

Introduce a minimal JetStream stream configuration seed without allowing JetStream to replace SC-B-owned dispatch state or observations.

### G-MIR-029-009 — Architecture test transition

Update broker-forbidden architecture tests into intentional NATS allowlist tests.

---

## 6. Non-goals

This MIR does not authorize:

```text
- full lifecycle runtime;
- adapter admission authority;
- Adapter Manifest runtime;
- Capability Semantics runtime;
- productive SC-D adapter implementation;
- provider execution;
- productive fact family shapes;
- SC-D discovery/state/health fact runtime;
- semantic retry;
- terminal request-state ownership;
- SC-C direct NATS publishing;
- SC-C imports of NATS or bus.nats;
- replacing JDBC dispatch state or observations with JetStream state;
- real hot onboarding runtime;
- View Composer / SApp / Surface behavior;
- official adapter SDK;
- TCK implementation.
```

---

## 7. Mandatory implementation actions

```text
ACTION-MIRB-001 — Add io.nats:jnats as intentional SC-B NATS binding dependency.
ACTION-MIRB-002 — Update broker-dependency architecture tests into NATS-only allowlist tests in the SAME COMMIT as ACTION-MIRB-001.
ACTION-MIRB-003 — Add Testcontainers NATS as the default integration test substrate.
ACTION-MIRB-004 — Document any local nats-server fallback explicitly if Testcontainers is not viable.
ACTION-MIRB-005 — Implement NatsScBusPort as ScBusPort.
ACTION-MIRB-006 — Implement NatsSubjectBuilder using ScSubjectIdTokenCodec and ScCorrelationTokenCodec.
ACTION-MIRB-007 — Use ScJsonWireCodec and WireEnvelopeValidator before publish.
ACTION-MIRB-008 — Add RuntimeDispatchService → NATS live EVENT / TIMER_FIRED_SIGNAL test.
ACTION-MIRB-009 — Introduce production wire payload records ScdCommand and ScdExecutionResult under bus.contract.scd.
ACTION-MIRB-010 — Add COMMAND_SCD_V1 and RESPONSE_SCD_EXECUTION_RESULT_V1 to ScPayloadTypeRegistry.
ACTION-MIRB-011 — Add direct COMMAND fixture with payloadType sc.command.scd.v1.
ACTION-MIRB-012 — Add direct RESPONSE fixture with payloadType sc.response.scd-execution-result.v1.
ACTION-MIRB-013 — Add lifecycle subject mapping fixtures only if low-cost and non-runtime.
ACTION-MIRB-014 — Add ScBusNatsArchitectureTest.
ACTION-MIRB-015 — Preserve DispatchStateWritePort and DispatchObservationPort as authoritative technical runtime persistence.
```

Atomicity rule:

```text
io.nats:jnats and the architecture-test allowlist update MUST be committed together.
The project MUST NOT contain an intermediate commit where jnats is added while the
architecture test still forbids NATS, nor a commit that relaxes broker checks before
the intentional dependency is introduced.
```

---


## 8. Design decisions for this MIR

### D-MIR-029-001 — Physical binding implements ScBusPort

`NatsScBusPort` MUST implement the existing `ScBusPort` interface.

The MIR MUST NOT introduce a parallel bus interface for NATS.

### D-MIR-029-002 — NATS is a binding, not the bus doctrine

NATS subjects, streams, acks and consumers are physical binding artifacts. They do not redefine:

```text
ScRoutingKey
ScBusLane
ScMessageMetadata
ScCommandEnvelope
ScEventEnvelope
ScResponseEnvelope
```

### D-MIR-029-003 — SC-C does not publish directly to NATS

The validated dispatch path remains:

```text
SC-C outbox / ledger
  → SC-B bridge
  → DispatchCandidate
  → RuntimeDispatchService
  → ScBusPort
  → NatsScBusPort
```

SC-C production code MUST NOT import `io.nats`, `NatsScBusPort`, or any `bus.nats` class.

### D-MIR-029-004 — Runtime E2E is EVENT / timer-fired only

The first live RuntimeDispatchService E2E path MUST be limited to:

```text
DeliveryLane.SIGNAL / TIMER_FIRED_SIGNAL / sc-c.timer-fired
  → ScBusLane.EVENT
  → NATS event subject
```

This is the only runtime-dispatch E2E path authorized by this MIR.

### D-MIR-029-005 — Command routing is fixture-only in this MIR

COMMAND binding tests MAY use direct `ScCommandEnvelope<?>` fixtures with explicit `ScRoutingKey` values.

COMMAND tests MUST NOT claim productive adapter command routing through the current DispatchCandidate path.

Rationale:

```text
RuntimeDispatchService currently does not receive adapterId/deviceId/endpointId from DispatchCandidate.
Adapter-scoped routing belongs downstream to lifecycle route assignment and manifest/capability artifacts.
```

### D-MIR-029-006 — Response routing is fixture-only in this MIR

RESPONSE binding tests MAY use direct `ScResponseEnvelope<?>` fixtures with explicit `ScRoutingKey` and `ScResponseMetadata` values.

Response subject tokens MUST use `ScCorrelationTokenCodec` and the 32-lowercase-hex UUID representation.

### D-MIR-029-007 — Lifecycle scope is subject mapping only

This MIR MAY implement `NatsLifecycleChannelMapper` or equivalent subject-mapping utilities for lifecycle channels.

It MUST NOT implement:

```text
challenge verification;
SAVK / trust bundle validation;
adapter admission authority;
manifest acceptance;
ACTIVE transition authority;
route revision runtime.
```

### D-MIR-029-008 — Subject grammar uses `sc.v1`

Physical NATS subjects MUST follow the seed grammar selected by the NATS SDD.

Seed examples:

```text
EVENT timer-fired:
  sc.v1.{habitatRoute}.event.timer.sc-c.timer-fired

COMMAND fixture:
  sc.v1.{habitatRoute}.command.{adapterRoute}.{targetRoute}[.{endpointRoute}]

RESPONSE fixture:
  sc.v1.{habitatRoute}.response.{adapterRoute}.{rootCorrelationIdToken}
```

`habitatRoute`, `targetRoute`, and `endpointRoute` MUST use `scid1_` when derived from canonical IDs.

`rootCorrelationIdToken` MUST use the correlation UUID hex-no-hyphens codec, not `scid1_`.

### D-MIR-029-009 — Route tokens are not canonical identity

Subject tokens are physical routing tokens only.

NATS subject parsing MUST NOT become identity reconstruction.

Canonical IDs remain canonical values inside envelopes and SC-C-owned state. Subject-safe tokens exist for routing.

### D-MIR-029-010 — Wire payloads use SC-JSON-WIRE-v1

`NatsScBusPort` MUST serialize envelopes through `ScJsonWireCodec` and MUST validate wire envelopes through `WireEnvelopeValidator` before publish.

`NatsScBusPort` MUST NOT send raw payloads without envelope.

### D-MIR-029-011 — ScdCommand / ScdExecutionResult are production wire reference records

MIR-B MUST introduce production JVM reference records for the public SC-D wire payload shapes:

```text
com.sovereign.connect.bus.contract.scd.ScdCommand
com.sovereign.connect.bus.contract.scd.ScdExecutionResult
```

They are protocol surface reference bindings, not test convenience classes.

COMMAND fixture:

```text
payloadType = sc.command.scd.v1
payloadSchemaVersion = 1.0
payload shape = ScdCommand v1 from PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate
```

RESPONSE fixture:

```text
payloadType = sc.response.scd-execution-result.v1
payloadSchemaVersion = 1.0
payload shape = ScdExecutionResult v1 from PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate
```

These records MUST NOT import SC-C domain classes, adapter implementation classes, lifecycle runtime classes, manifest runtime classes or provider SDKs.

They are allowed because they are JVM bindings for language-neutral wire schemas. They MUST NOT become an adapter SDK requirement or productive adapter runtime.

### D-MIR-029-012 — INVOKE_CAPABILITY is fixture-only if used

This MIR MAY include an optional wire-only fixture using:

```text
operationKind = INVOKE_CAPABILITY
capabilityRef = sc.capability.onoff.v1
```

Such a fixture MUST NOT imply Adapter Manifest runtime, capability registry runtime, or real capability admissibility checks.

### D-MIR-029-013 — JetStream does not replace SC-B persistence

JetStream acks, streams, consumers and retention are technical broker artifacts.

They MUST NOT replace:

```text
DispatchStateWritePort
DispatchObservationPort
JdbcDispatchStateRepository
JdbcDispatchObservationRepository
```

JetStream ack MAY be recorded as technical delivery evidence. It MUST NOT become semantic command success.

### D-MIR-029-014 — JetStream seed scope

This MIR selects the following JetStream seed scope:

```text
- define SCB_*_V1 stream configuration names and subject patterns;
- initialize or validate stream configuration if the NATS test fixture supports JetStream;
- never rely on JetStream as the only source of dispatch state or delivery diagnostics.
```

If live JetStream configuration is claimed, evidence MUST be produced against a running NATS server with JetStream enabled. Constructing `NatsStreamConfiguration` or equivalent Java objects without applying them to a real server is config-only evidence, not live JetStream evidence.

If live JetStream configuration introduces CI friction, execution MUST stop and report whether to split:

```text
MU-029A — NATS Core live binding seed
MU-029B — JetStream stream/consumer hardening seed
```

Silent downgrade from NATS + JetStream MIR to Core-only implementation is forbidden.

### D-MIR-029-015 — Handler registration maps to subscriptions

`registerEventHandler`, `registerCommandHandler`, and `registerResponseHandler` MAY map to NATS subscriptions for explicit topics/routes.

For this MIR, handler registration may be tested for EVENT. COMMAND/RESPONSE subscriptions may remain fixture-level if lifecycle route assignment is not implemented.

### D-MIR-029-016 — DispatchOutcome is technical

`NatsScBusPort` must return `DispatchOutcome` based on technical publish/subscribe/ack behavior.

It MUST NOT infer provider execution or semantic success.

### D-MIR-029-017 — Architecture tests become allowlist, not removal

Tests that currently reject all broker dependencies MUST be replaced with intentional allowlist logic:

```text
Allowed:
  io.nats:jnats
  org.testcontainers, if test fixture uses Testcontainers

Still rejected:
  Vert.x
  Redis / Lettuce
  gRPC
  Kafka
  RabbitMQ
  MQTT
  WebSocket broker APIs
  unrelated broker client APIs
```

### D-MIR-029-018 — Serialization package remains broker-free

`bus.runtime.serialization` MUST NOT import:

```text
io.nats
org.testcontainers
bus.nats
```

---

## 9. Expected implementation surface

Expected production package:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  NatsScBusPort.java
  NatsSubjectBuilder.java
  NatsConnectionFactory.java or NatsClientConfiguration.java
  NatsDispatchMapper.java or equivalent envelope-to-subject resolver
  NatsStreamConfiguration.java
  NatsLifecycleChannelMapper.java, if lifecycle mapping is included
```

Expected test package:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/
  NatsSubjectBuilderTest.java
  NatsScBusPortEventIntegrationTest.java
  RuntimeDispatchToNatsEventIntegrationTest.java
  NatsScBusPortCommandWireFixtureTest.java
  NatsScBusPortResponseWireFixtureTest.java
  NatsLifecycleChannelMapperTest.java, if lifecycle mapping is included
  ScBusNatsArchitectureTest.java
```

Expected dependency changes:

```text
pom.xml:
  add io.nats:jnats
  add org.testcontainers:testcontainers and org.testcontainers:junit-jupiter by default
```

Local `nats-server` fixture is allowed only if the implementation report explains why Testcontainers is not viable.

Names may vary if the implementation report maps actual classes to the acceptance criteria.

---

## 10. Acceptance criteria

### 10.1 Boundary and scope

```text
AC-029-001 — NatsScBusPort implements ScBusPort.
AC-029-002 — SC-C production code does not import io.nats or bus.nats.
AC-029-003 — bus.runtime.serialization does not import io.nats, Testcontainers or bus.nats.
AC-029-004 — NATS dependency is confined to SC-B NATS binding surface and test fixtures.
AC-029-005 — No Adapter Manifest runtime is introduced.
AC-029-006 — No Capability Semantics runtime is introduced.
AC-029-007 — No productive provider adapter is introduced.
AC-029-008 — No productive fact-family runtime is introduced.
```

### 10.2 Dependencies and architecture tests

```text
AC-029-009 — pom.xml intentionally includes io.nats:jnats.
AC-029-010 — Testcontainers NATS is used as the default test substrate, or a local nats-server fallback is explicitly justified in the implementation report.
AC-029-011 — previous no-broker architecture tests are updated to NATS-only allowlist tests in the same commit that adds io.nats:jnats.
AC-029-012 — non-NATS broker dependencies remain rejected.
AC-029-013 — ScBusNatsArchitectureTest exists or equivalent architecture coverage is added.
AC-029-014 — architecture tests verify NATS package does not import core.**, adapter.** or integration.**.
AC-029-015 — architecture tests verify SC-C code does not import NATS.
```

### 10.3 Subject construction

```text
AC-029-016 — NatsSubjectBuilder builds event timer subject sc.v1.{habitatRoute}.event.timer.sc-c.timer-fired.
AC-029-017 — habitatRoute is built using ScSubjectIdTokenCodec.
AC-029-018 — command fixture subject uses explicit habitatRoute, adapterRoute and targetRoute.
AC-029-019 — response fixture subject uses ScCorrelationTokenCodec for rootCorrelationIdToken.
AC-029-020 — unsafe subject tokens are rejected.
AC-029-021 — subject builder does not parse subject tokens back into canonical identity.
```

### 10.4 Serialization and validation

```text
AC-029-022 — NatsScBusPort serializes envelopes with ScJsonWireCodec.
AC-029-023 — NatsScBusPort validates wire envelopes with WireEnvelopeValidator before publish.
AC-029-024 — received message bytes can be decoded as SC-JSON-WIRE-v1.
AC-029-025 — raw payload without ScEnvelope is never published by NatsScBusPort.
AC-029-026 — payloadType values are not Java class names.
```

### 10.5 Runtime EVENT E2E

```text
AC-029-027 — RuntimeDispatchService dispatches TIMER_FIRED_SIGNAL / sc-c.timer-fired through NatsScBusPort.
AC-029-028 — a NATS subscriber receives the event on the expected event subject.
AC-029-029 — received event decodes to envelopeKind=EVENT and routingKey.lane=EVENT.
AC-029-030 — DispatchState records technical dispatched state through existing dispatch state persistence.
AC-029-031 — DispatchObservation persists technical DISPATCHED or equivalent delivery observation through existing observation persistence.
AC-029-032 — no semantic success is inferred from publish/ack.
```

### 10.6 Command / response wire fixtures

```text
AC-029-033 — ScPayloadTypeRegistry exposes COMMAND_SCD_V1 = sc.command.scd.v1.
AC-029-034 — ScdCommand exists as a production wire reference record under bus.contract.scd and the command fixture uses payloadType sc.command.scd.v1 / payloadSchemaVersion 1.0.
AC-029-035 — Optional INVOKE_CAPABILITY fixture, if present, is marked wire-only and does not implement capability runtime.
AC-029-036 — ScPayloadTypeRegistry exposes RESPONSE_SCD_EXECUTION_RESULT_V1 = sc.response.scd-execution-result.v1.
AC-029-037 — ScdExecutionResult exists as a production wire reference record under bus.contract.scd and the response fixture uses payloadType sc.response.scd-execution-result.v1 / payloadSchemaVersion 1.0.
AC-029-038 — ScdExecutionResult is treated as adapter evidence, not terminal semantic authority.
```

### 10.7 JetStream seed behavior

```text
AC-029-039 — NatsStreamConfiguration or equivalent defines SCB_*_V1 stream names and subject patterns.
AC-029-040 — If live JetStream config is implemented, tests start a NATS server with JetStream enabled and show stream initialization/configuration is applied against that server.
AC-029-041 — JetStream ack/retention is never used to replace DispatchStateWritePort.
AC-029-042 — JetStream ack/retention is never used to replace DispatchObservationPort.
AC-029-043 — If live JetStream is not implemented, implementation report records explicit split recommendation rather than silently omitting it.
```

### 10.8 Lifecycle mapping fixtures

```text
AC-029-044 — Lifecycle subject mapping fixtures are included if NatsLifecycleChannelMapper is implemented.
AC-029-045 — Lifecycle tests do not implement adapter admission or ACTIVE authority.
AC-029-046 — Route assignment push subject construction is test-covered if lifecycle mapper is included.
```

### 10.9 Evidence and reporting

```text
AC-029-047 — Implementation report records branch, commits and changed files.
AC-029-048 — Implementation report records final test counts and NATS fixture strategy.
AC-029-049 — Implementation report records JetStream seed disposition: live config, config-only, or split recommendation.
AC-029-050 — Implementation report confirms no SC-C direct NATS publishing.
AC-029-051 — Implementation report confirms retained downstream debts: lifecycle runtime, manifest runtime, capability runtime, productive adapters.
AC-029-052 — Acceptance map links all AC-029 criteria to tests/source/evidence.
```

---

## 11. Expected validation

Baseline before implementation:

```text
sovereign-connect: 411 tests, 0 failures, 0 errors, 0 skipped
EIB reports:       56 tests, 0 failures, 0 errors, 0 skipped
```

Expected delta:

```text
MIR-B expected new tests: +26 or more
Expected full regression: >=437 tests, 0 failures, 0 errors, 0 skipped
```

Minimum targeted test suites should cover:

```text
NatsSubjectBuilderTest
ScdCommandWirePayloadTest
ScdExecutionResultWirePayloadTest
NatsScBusPortEventIntegrationTest
RuntimeDispatchToNatsEventIntegrationTest
NatsScBusPortCommandWireFixtureTest
NatsScBusPortResponseWireFixtureTest
ScBusNatsArchitectureTest
```

The exact count must be fixed by the execution package after code-surface refresh and package finalization.

---

## 12. Retained debt disposition

### Closed by this MIR if validated

```text
DEBT-B-NATS-001 — physical NATS Core + JetStream binding absent.
  Closure condition: NatsScBusPort exists, NATS live event path is validated, and JetStream seed disposition is explicit.
  Closure scope: seed physical binding only.
```

### Partially closed by this MIR if validated

```text
DEBT-B-PHYSICAL-BINDING-001 — SC-B has no physical transport binding.
  Disposition: partially closed for NATS seed binding.
  Full closure requires lifecycle runtime, adapter route assignment and production operational hardening.
```

### Retained after this MIR

```text
DEBT-B-LC-001 — lifecycle-channel runtime absent.
DEBT-SCD-MANIFEST-001 — Adapter Manifest runtime absent.
DEBT-SCD-CAPABILITY-001 — Capability Semantics runtime absent.
DEBT-SCD-FACTS-001 — SC-D fact family production shapes absent.
DEBT-SCD-ADAPTER-001 — productive SC-D adapter absent.
DEBT-SCD-TCK-001 — SC-D conformance/TCK absent.
DEBT-B-ROUTE-001 — adapter-scoped route assignment runtime absent.
DEBT-B-JS-HARDENING-001 — JetStream operational hardening may remain downstream if this MIR only seeds configuration/smoke behavior.
```

---

## 13. Risks

### RISK-MIR-029-001 — NATS becomes the contract

Risk:

```text
Subject grammar or NATS client behavior may start redefining SC-B/SC-D semantics.
```

Mitigation:

```text
NATS is binding only. ScEnvelope, ScRoutingKey, payloadType and lifecycle contracts remain authoritative.
```

### RISK-MIR-029-002 — JetStream replaces SC-B state/observations

Risk:

```text
Acks, consumer state or stream retention may be treated as dispatch state or delivery diagnostics.
```

Mitigation:

```text
DispatchStateWritePort and DispatchObservationPort remain mandatory technical persistence surfaces.
```

### RISK-MIR-029-003 — SC-C publishes directly to NATS

Risk:

```text
SC-C outbox or temporal engine may bypass SC-B runtime and publish to NATS directly.
```

Mitigation:

```text
Architecture tests forbid SC-C imports of NATS and bus.nats.
```

### RISK-MIR-029-004 — Command routing overclaims readiness

Risk:

```text
MIR-B command fixture may be mistaken for productive adapter command routing.
```

Mitigation:

```text
COMMAND/RESPONSE are explicit direct wire fixtures. Runtime E2E is EVENT only.
```

### RISK-MIR-029-005 — Lifecycle mapping becomes lifecycle runtime

Risk:

```text
Subject mapping helpers may expand into admission, ACTIVE authority or manifest acceptance.
```

Mitigation:

```text
Lifecycle scope is mapping fixtures only. Runtime/admission remains downstream.
```

### RISK-MIR-029-006 — Wire payload records become adapter SDK

Risk:

```text
Java fixture classes may become perceived as required adapter ABI.
```

Mitigation:

```text
Wire JSON shape and payloadType are authoritative. Java classes are optional reference/test implementation only.
```

### RISK-MIR-029-007 — Test infrastructure friction hides scope downgrade

Risk:

```text
JetStream/Testcontainers friction may cause a silent Core-only implementation.
```

Mitigation:

```text
Silent downgrade is forbidden. Execution must report split recommendation if JetStream cannot be included safely.
```

### RISK-MIR-029-008 — Broker dependency checks are removed instead of refined

Risk:

```text
Architecture tests may simply delete no-broker checks and allow uncontrolled dependencies.
```

Mitigation:

```text
Tests must become NATS-only allowlist plus denylist for unrelated broker/client APIs.
```

---

## 14. Open questions

### OQ-MIR-029-001 — Test substrate

Resolved for candidate:

```text
Testcontainers NATS is the default MIR-B integration substrate.
A local nats-server fixture is allowed only if the implementation report explains why Testcontainers is not viable.
Mock-only or InMemoryScBusPort-only tests do not satisfy MIR-B live binding evidence.
```

### OQ-MIR-029-002 — JetStream live scope

Should MU-029 require live JetStream stream initialization, or allow config-only with explicit downstream JetStream hardening?

Default recommendation:

```text
Require at least stream configuration object and subject pattern tests.
Attempt live JetStream smoke test if fixture supports it.
Stop and split if it causes CI instability.
```

### OQ-MIR-029-003 — Production ScdCommand Java shape

Resolved for candidate:

```text
ScdCommand and ScdExecutionResult MUST be introduced as production wire payload records in this MIR.
They are protocol surface reference bindings, not test convenience classes.
Placement: com.sovereign.connect.bus.contract.scd.
They MUST NOT import SC-C domain classes, adapter implementation classes, lifecycle runtime classes, manifest runtime classes or provider SDKs.
```

### OQ-MIR-029-004 — Lifecycle mapper inclusion

Should `NatsLifecycleChannelMapper` be included in MU-029 or deferred to lifecycle runtime descent?

Default recommendation:

```text
Include mapping fixtures only if low-cost and clearly non-runtime.
Otherwise defer.
```

---

## 15. MIR acceptance checklist

Before promoting this MIR to candidate, verify:

```text
[ ] Scope states RuntimeDispatchService live E2E = EVENT / TIMER_FIRED_SIGNAL only.
[ ] COMMAND and RESPONSE are direct wire fixtures only.
[ ] NATS is binding, not doctrine.
[ ] JetStream does not replace SC-B JDBC dispatch state/observation persistence.
[ ] PDR-SOV-SC-D-COMMAND-PAYLOAD-001 v0.3.0-candidate is consumed.
[ ] MU-028 serialization utilities are consumed and not duplicated.
[ ] Architecture tests are required to become NATS-only allowlist, not removed.
[ ] Lifecycle runtime is excluded.
[ ] Adapter Manifest and Capability Semantics runtime are excluded.
[ ] Productive adapter execution is excluded.
[ ] Expected validation and evidence requirements are stated.
```

---


Additional candidate checks applied in v0.2.0:

```text
- ScdCommand / ScdExecutionResult are in scope as production wire reference records.
- Registry constants for sc.command.scd.v1 and sc.response.scd-execution-result.v1 are mandatory.
- Testcontainers NATS is the default substrate; fallback requires written justification.
- jnats dependency and architecture-test allowlist patch are atomic.
- Live JetStream evidence requires a real JetStream-enabled server; config-object construction alone is not enough.
```

---

## 16. Recommended downstream sequence

```text
1. Promote this MIR to v0.2.0-candidate after review.
2. Produce execution package for MU-029.
3. Implement on branch: feat/sc-b-mir-029-nats-jetstream-binding-seed.
4. Validate full regression and NATS integration tests.
5. If JetStream is config-only or split, open MIR-SOV-SC-B-JETSTREAM-HARDENING-001.
6. Open PDR-SOV-SC-D-ADAPTER-MANIFEST-001 before first productive adapter.
7. Open PDR-SOV-SC-D-CAPABILITY-SEMANTICS-001 before industrial dynamic capability claims.
```

---

## 17. Recommended branch and commit

```text
Branch:
  feat/sc-b-mir-029-nats-jetstream-binding-seed

Commit:
  feat(sc-b): add nats jetstream binding seed
```

---

## 18. Boundary invariant

```text
SC-C owns semantic authority.
SC-B transports, correlates and records technical delivery.
SC-D conforms through public wire protocol.
NATS is a physical binding, not the adapter ABI.
JetStream durability does not become semantic authority.
```
