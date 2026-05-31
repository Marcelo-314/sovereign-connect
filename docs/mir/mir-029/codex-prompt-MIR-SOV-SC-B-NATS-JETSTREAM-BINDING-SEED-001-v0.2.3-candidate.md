# Codex Prompt — MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

```text
Document ID:  codex-prompt-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Version:      v0.2.3-candidate
Status:       Execution prompt / candidate
Date:         2026-05-30
Target MIR:   MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.2.0-candidate
Target branch: feat/sc-b-mir-029-nats-jetstream-binding-seed
```

---

## Mission

Implement MU-029: SC-B NATS / JetStream Binding Seed.

This is the first physical NATS binding for the existing SC-B `ScBusPort`. It must use MU-028 serialization utilities and preserve all SC-C / SC-B / SC-D boundaries.

Read first, in order:

```text
context-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.0-candidate.md
CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.1.0-refresh.md
CSA-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.1.0-user-draft.md
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001-v0.2.0-candidate.md
PDR-SOV-SC-D-COMMAND-PAYLOAD-001-v0.3.0-candidate.md
acceptance-map-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001-v0.2.3-candidate.md
```

---

## Step 0 — Baseline

```bash
git checkout develop
git pull origin develop
git checkout -b feat/sc-b-mir-029-nats-jetstream-binding-seed
mvn -q test
```

Expected baseline:

```text
411 tests, 0 failures, 0 errors, 0 skipped
```

If baseline fails, stop.

---

## Step 1 — Add NATS dependencies and update architecture tests atomically

Add production dependency:

```xml
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>${jnats.version}</version>
</dependency>
```

Add test dependencies:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Update all broker-blocking architecture tests in the same commit. Allow `io.nats` only. Continue rejecting other brokers/transports.

Do not relax the serialization package test: `bus.runtime.serialization` must remain NATS-free.

Run:

```bash
mvn -q compile
mvn -q test -Dtest="ScBusArchitectureTest,ScBusHardeningArchitectureTest,ScBusObservationPersistenceArchitectureTest,ScBusOutboxBridgeArchitectureTest,ScBusSerializationArchitectureTest"
```

---

## Step 2 — Add SCD wire reference records

Create:

```text
src/main/java/com/sovereign/connect/bus/contract/scd/ScdCommand.java
src/main/java/com/sovereign/connect/bus/contract/scd/ScdExecutionResult.java
```

Use exactly:

```java
package com.sovereign.connect.bus.contract.scd;

import com.fasterxml.jackson.databind.JsonNode;

public record ScdCommand(
    String targetRef,
    String endpointRef,
    String operationKind,
    String capabilityRef,
    JsonNode params
) {}
```

```java
package com.sovereign.connect.bus.contract.scd;

import com.fasterxml.jackson.databind.JsonNode;

public record ScdExecutionResult(
    String status,
    String providerCorrelationRef,
    JsonNode observedState,
    boolean retryable,
    String sanitizedReason
) {}
```

These records are protocol surface reference bindings. They are not an SDK and not adapter runtime.

Add tests for JSON roundtrip with `ScJsonWireCodec` using:

```text
sc.command.scd.v1
sc.response.scd-execution-result.v1
```

---

## Step 3 — Patch ScPayloadTypeRegistry

Add:

```java
public static final String COMMAND_SCD_V1 = "sc.command.scd.v1";
public static final String RESPONSE_SCD_EXECUTION_RESULT_V1 = "sc.response.scd-execution-result.v1";
```

Do not make the registry a closed allowlist.

---

## Step 4 — Implement NatsSubjectBuilder

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsSubjectBuilder.java
```

Important: MU-028 codecs are static utility classes. Do not instantiate them.

Use:

```java
String habitatRoute = ScSubjectIdTokenCodec.encodeScid1(habitatId);
ScSubjectTokenValidator.requireSafeToken(habitatRoute);

String adapterRoute = ScSubjectIdTokenCodec.encodeScid1(adapterId);
ScSubjectTokenValidator.requireSafeToken(adapterRoute);

String correlationToken = ScCorrelationTokenCodec.encode(correlationId);
ScSubjectTokenValidator.requireSafeToken(correlationToken);
```

Required methods:

```java
public String buildTimerFiredEventSubject(String habitatId)
public String buildTimerFiredEventSubscriptionPattern()
public String buildCommandSubject(String habitatId, String adapterId, String targetKind, String targetId)
public String buildResponseSubject(String habitatId, String adapterId, UUID rootCorrelationId)
public String buildLifecycleAnnounceSubject(String habitatId)
```

Expected shapes:

```text
buildTimerFiredEventSubject("habitat-1")
  -> sc.v1.{scid1(habitat-1)}.event.timer.sc-c.timer-fired

buildTimerFiredEventSubscriptionPattern()
  -> sc.v1.*.event.timer.sc-c.timer-fired

buildCommandSubject("habitat-1", "adapter.z2m", "device", "device.z2m.0x001")
  -> sc.v1.{scid1(habitat-1)}.command.{scid1(adapter.z2m)}.device.{scid1(device.z2m.0x001)}

buildResponseSubject("habitat-1", "adapter.z2m", correlationId)
  -> sc.v1.{scid1(habitat-1)}.response.{scid1(adapter.z2m)}.{correlationHex}

buildLifecycleAnnounceSubject("habitat-1")
  -> sc.v1.{scid1(habitat-1)}.lifecycle.announce
```

Rules:

```text
No wildcards in publish subjects.
Wildcard is allowed only in buildTimerFiredEventSubscriptionPattern().
Never use scid1_ for correlation IDs.
Never parse subject tokens into canonical IDs.
```

---

## Step 5 — Implement NatsScBusPort

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java
```

Recommended constructor:

```java
public NatsScBusPort(
    Connection connection,
    NatsSubjectBuilder subjectBuilder,
    ScJsonWireCodec wireCodec,
    EnvelopeValidationService envelopeValidationService
) { ... }
```

Do not inject or instantiate:

```text
ScSubjectIdTokenCodec
ScCorrelationTokenCodec
ScSubjectTokenValidator
WireEnvelopeValidator
```

They are static utilities.

### Publish event

For runtime E2E, support `sc-c.timer-fired`:

```java
envelopeValidationService.validateEvent(envelope);
String payloadType = ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1;
ScJsonWireEnvelope wire = wireCodec.toWireEnvelope(envelope, payloadType, "1.0");
WireEnvelopeValidator.validate(wire);
byte[] bytes = wireCodec.marshal(wire);
String habitatId = envelope.routingKey().habitatId() != null
    ? envelope.routingKey().habitatId()
    : envelope.routingKey().partitionKey();
String subject = subjectBuilder.buildTimerFiredEventSubject(habitatId);
connection.publish(subject, bytes);
// Existing production record shape verified pre-MU-029:
// Dispatched(UUID dispatchRecordId, UUID attemptId, String topic, String partitionKey).
// RuntimeDispatchService normalizes these IDs to the active dispatch attempt.
return new Dispatched(
        UUID.randomUUID(),
        UUID.randomUUID(),
        envelope.routingKey().topic(),
        envelope.routingKey().partitionKey());
```

Do not treat absence of a local handler as `NoHandler` for NATS pub/sub.

### Publish command fixture

Only direct wire fixture is required. Resolve payload type only for `ScdCommand`:

```text
payload instanceof ScdCommand -> COMMAND_SCD_V1
else -> new Failed(
    UUID.randomUUID(),
    UUID.randomUUID(),
    "UNSUPPORTED_PAYLOAD_TYPE",
    "payload type not resolvable for seed",
    false
)
```

Use explicit `ScRoutingKey` with non-null habitatId and adapterId in tests. If this branch needs to return a `Failed`, use the exact verified shape from context §3.8. Do not use ellipsis or pseudo-constructor syntax in Java code.

### Publish response fixture

Only direct wire fixture is required. Resolve payload type only for `ScdExecutionResult`:

```text
payload instanceof ScdExecutionResult -> RESPONSE_SCD_EXECUTION_RESULT_V1
else -> new Failed(
    UUID.randomUUID(),
    UUID.randomUUID(),
    "UNSUPPORTED_PAYLOAD_TYPE",
    "payload type not resolvable for seed",
    false
)
```

If this branch needs to return a `Failed`, use the exact verified shape from context §3.8. Do not use ellipsis or pseudo-constructor syntax in Java code.

Use response subject:

```text
sc.v1.{scid1(habitatId)}.response.{scid1(adapterId)}.{correlationHex}
```

### Register event handler

For MU-029:

```java
registerEventHandler("sc-c.timer-fired", handler)
```

must subscribe to:

```text
sc.v1.*.event.timer.sc-c.timer-fired
```

On message receipt:

```text
1. unmarshal bytes with ScJsonWireCodec.unmarshal(...)
2. WireEnvelopeValidator.validate(...)
3. construct ScEventEnvelope(metadata, payload, routingKey)
4. invoke handler.handle(envelope)
```

It is acceptable for timer-fired handler payload to remain a JsonNode / opaque object in the event E2E. Do not use ScdCommand as a timer-fired event payload.

---

## Step 6 — JetStream seed disposition

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsStreamConfiguration.java
```

Define stream names/patterns such as:

```text
SCB_COMMANDS_V1
SCB_EVENTS_V1
SCB_RESPONSES_V1
SCB_LIFECYCLE_V1
SCB_DLQ_V1
```

If live JetStream is implemented, test against a real NATS server with `--js` and verify stream creation/update against that server.

If live JetStream is unstable, report explicit split:

```text
MU-029: NATS Core live binding seed.
MU-029B / later MIR: JetStream hardening seed.
```

Do not silently omit JetStream.

---

## Step 7 — Lifecycle subject mapper fixture

Create only mapping fixture if included:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsLifecycleChannelMapper.java
```

Allowed:

```text
announce subject mapping
challenge subject mapping
route-assignment subject mapping
```

Forbidden:

```text
adapter admission runtime
ACTIVE authority
SAVK verification
manifest runtime
```

---

## Step 8 — Tests

Minimum tests:

```text
NatsSubjectBuilderTest
ScdCommandWirePayloadTest
ScdExecutionResultWirePayloadTest
NatsScBusPortEventIntegrationTest
RuntimeDispatchToNatsEventIntegrationTest
NatsScBusPortCommandWireFixtureTest
NatsScBusPortResponseWireFixtureTest
NatsStreamConfigurationTest
ScBusNatsArchitectureTest
```

Use Testcontainers NATS unless a fallback is explicitly justified.

Run targeted:

```bash
mvn -q test -Dtest="NatsSubjectBuilderTest,ScdCommandWirePayloadTest,ScdExecutionResultWirePayloadTest,ScBusNatsArchitectureTest"
mvn -q test -Dtest="NatsScBusPortEventIntegrationTest,RuntimeDispatchToNatsEventIntegrationTest,NatsScBusPortCommandWireFixtureTest,NatsScBusPortResponseWireFixtureTest,NatsStreamConfigurationTest"
```

Run full:

```bash
mvn -q test
```

Expected:

```text
>=437 tests, 0 failures, 0 errors, 0 skipped
```

---

## Step 9 — Implementation report

Create/update:

```text
docs/mir/mir-029/implementation-report.md
```

Must record:

```text
branch
commits
changed files
final test counts
NATS fixture strategy
JetStream disposition
whether JetStream was live-tested or split
confirmation that SC-C does not publish directly to NATS
confirmation that no adapter runtime / lifecycle admission / manifest runtime was implemented
retained debts
```

---

## Hard stops

Stop and report if:

```text
STOP-1 — baseline test suite fails before coding.
STOP-2 — ScBusPort or RuntimeDispatchService constructor needs changes.
STOP-3 — bus.runtime.nats needs core.**, adapter.** or integration.** imports.
STOP-4 — bus.runtime.serialization needs io.nats or Testcontainers.
STOP-5 — command runtime E2E requires adapterId/deviceId/endpointId from DispatchCandidate.
STOP-6 — publish path bypasses ScJsonWireCodec or WireEnvelopeValidator.
STOP-7 — JetStream replaces DispatchStateWritePort or DispatchObservationPort.
STOP-8 — NATS tests require manually running NATS without documented fallback.
STOP-9 — JetStream is silently omitted without explicit split disposition.
```

---

## Commit suggestion

Primary commit:

```bash
git commit -m "feat(sc-b): add nats jetstream binding seed"
```

Documentation/evidence commit if needed:

```bash
git commit -m "docs(sc-b): record nats jetstream binding evidence"
```
