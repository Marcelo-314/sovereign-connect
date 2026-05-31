# Context — MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

```text
Document ID:  context-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Version:      v0.2.3-candidate
Status:       Execution context / candidate
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B / SC-D wire boundary
Scope:        Codex execution context for MU-029 NATS / JetStream Binding Seed
Target MIR:   MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.2.0-candidate
```

---

## 1. Purpose

Implement MU-029 as the first physical SC-B binding over NATS Core + JetStream.

Canonical purpose:

```text
Introduce a physical NATS Core + JetStream binding as an implementation of the
existing ScBusPort interface, using SC-B contracts, MU-028 serialization
utilities, durable dispatch state and durable dispatch observation persistence,
without redefining SC-B or SC-D.
```

This is a bounded binding seed. It is not a productive adapter, not lifecycle admission runtime, not manifest runtime, not capability runtime and not semantic authority transfer to NATS / JetStream.

---

## 2. Baseline before MU-029

Expected clean baseline:

```text
sovereign-connect: 411 tests, 0 failures, 0 errors, 0 skipped
EIB reports:       56 tests, 0 failures, 0 errors, 0 skipped
```

Before coding:

```bash
git status --short
git diff --name-status origin/develop...HEAD
git log --oneline --decorate --left-right origin/develop...HEAD
mvn -q test
```

If the baseline is not clean or tests do not pass, stop and report.

---

## 3. Existing code surface — exact signatures

### 3.1 ScBusPort location and shape

`ScBusPort` is in `bus.runtime.port`, not in `bus.contract`.

```java
package com.sovereign.connect.bus.runtime.port;

public interface ScBusPort {
    DispatchOutcome publishCommand(ScCommandEnvelope<?> envelope);
    DispatchOutcome publishEvent(ScEventEnvelope<?> envelope);
    DispatchOutcome publishResponse(ScResponseEnvelope<?> envelope);

    void registerCommandHandler(String topicOrRoute, ScCommandHandler handler);
    void registerEventHandler(String topicOrRoute, ScEventHandler handler);
    void registerResponseHandler(String topicOrRoute, ScResponseHandler handler);
}
```

Do not change this interface.

### 3.2 Handler signatures

```java
@FunctionalInterface
public interface ScCommandHandler {
    void handle(ScCommandEnvelope<?> envelope);
}

@FunctionalInterface
public interface ScEventHandler {
    void handle(ScEventEnvelope<?> envelope);
}

@FunctionalInterface
public interface ScResponseHandler {
    void handle(ScResponseEnvelope<?> envelope);
}
```

Handlers consume in-process envelope objects. `NatsScBusPort` is responsible for deserializing wire envelopes before invoking them.

### 3.3 RuntimeDispatchService

```java
public RuntimeDispatchService(
    ScBusPort busPort,
    DispatchCandidateReadPort candidateReadPort,
    DispatchStateWritePort stateWritePort,
    DispatchObservationPort observationPort,
    EnvelopeValidationService envelopeValidationService,
    RoutingKeyValidationService routingKeyValidationService,
    CorrelationValidationService correlationValidationService
) { ... }
```

Do not modify this constructor or dispatch lifecycle logic.

### 3.4 Runtime path routing limitation

`RuntimeDispatchService` currently builds `ScRoutingKey` from `DispatchCandidate` with `habitatId`, `adapterId`, `deviceId`, `endpointId` set to `null`. It preserves:

```text
lane         = candidate.lane()
partitionKey = candidate.partitionKey()  // habitatId for outbox bridge candidates
topic        = candidate.logicalTopic()  // e.g. sc-c.timer-fired
```

Therefore:

```text
RuntimeDispatchService live E2E in MU-029 MUST be EVENT / TIMER_FIRED_SIGNAL only.
COMMAND / RESPONSE tests MUST be direct wire fixtures with explicit ScRoutingKey values.
```

`NatsSubjectBuilder` MAY use `routingKey.partitionKey()` as seed habitat source when `routingKey.habitatId()` is null for event/timer-fired runtime dispatch. Document this in the implementation report.

### 3.5 Envelope constructors and metadata

Exact constructors:

```java
new ScMessageMetadata(
    UUID messageId,
    Instant emittedAt,
    UUID correlationId,
    UUID causationId,
    String topologyVersion
)

new ScRoutingKey(
    ScBusLane lane,
    String partitionKey,
    String topic,
    String habitatId,
    String adapterId,
    String deviceId,
    String endpointId
)

new ScEventEnvelope<>(metadata, payload, routingKey)
new ScCommandEnvelope<>(metadata, payload, routingKey)
new ScResponseEnvelope<>(metadata, payload, routingKey, responseMetadata)
```

`payloadType` and `payloadSchemaVersion` are not metadata fields. They exist only in `ScJsonWireEnvelope` and are provided to `ScJsonWireCodec.toWireEnvelope(...)`.

### 3.6 MU-028 serialization utilities — exact usage

The following utilities are static-only and have private constructors:

```java
ScSubjectIdTokenCodec.encodeScid1(String canonicalId)
ScSubjectIdTokenCodec.decodeScid1(String token)

ScCorrelationTokenCodec.encode(UUID id)
ScCorrelationTokenCodec.decode(String token)

ScSubjectTokenValidator.requireSafeToken(String token)
ScSubjectTokenValidator.isSafeToken(String token)

WireEnvelopeValidator.validate(ScJsonWireEnvelope envelope)
```

Do not instantiate these classes:

```java
// FORBIDDEN
new ScSubjectIdTokenCodec();
new ScCorrelationTokenCodec();
new ScSubjectTokenValidator();
new WireEnvelopeValidator();
```

`ScJsonWireCodec` is instantiable and has these overloads:

```java
new ScJsonWireCodec()
new ScJsonWireCodec(ObjectMapper mapper)

byte[] marshal(ScJsonWireEnvelope envelope)
ScJsonWireEnvelope unmarshal(byte[] bytes)

ScJsonWireEnvelope toWireEnvelope(ScCommandEnvelope<?> envelope, String payloadType, String payloadSchemaVersion)
ScJsonWireEnvelope toWireEnvelope(ScEventEnvelope<?> envelope, String payloadType, String payloadSchemaVersion)
ScJsonWireEnvelope toWireEnvelope(ScResponseEnvelope<?> envelope, String payloadType, String payloadSchemaVersion)
```

`NatsScBusPort` MUST call the overload with `payloadType` and `payloadSchemaVersion`.

### 3.7 Payload type registry

Existing constants:

```java
public static final String COMMAND_STUB_V1               = "sc.command.stub.v1";
public static final String EVENT_TIMER_FIRED_V1          = "sc.event.timer-fired.v1";
public static final String RESPONSE_STUB_V1              = "sc.response.stub.v1";
public static final String LIFECYCLE_ADAPTER_ANNOUNCE_V1 = "sc.lifecycle.adapter-announce.v1";
```

Add in MU-029:

```java
public static final String COMMAND_SCD_V1 = "sc.command.scd.v1";
public static final String RESPONSE_SCD_EXECUTION_RESULT_V1 = "sc.response.scd-execution-result.v1";
```

Preserve MU-028 invariant:

```text
syntactic payloadType validation != registry membership
```

Do not convert `ScPayloadTypeRegistry` into a closed allowlist.


### 3.8 DispatchOutcome shapes — verified pre-MU-029

Existing runtime dispatch outcome records are:

```java
public record Dispatched(
    UUID dispatchRecordId,
    UUID attemptId,
    String topic,
    String partitionKey
) implements DispatchOutcome {}

public record Failed(
    UUID dispatchRecordId,
    UUID attemptId,
    String code,
    String sanitizedReason,
    boolean retryable
) implements DispatchOutcome {}

public record NoHandler(
    UUID dispatchRecordId,
    UUID attemptId,
    String topic,
    String sanitizedReason
) implements DispatchOutcome {}
```

`ScBusPort` publish methods do not receive the `DispatchAttempt` created by
`RuntimeDispatchService`. This matches the existing `InMemoryScBusPort` pattern:
the port may return a syntactically valid `Dispatched`/`Failed`/`NoHandler`, and
`RuntimeDispatchService` normalizes the returned outcome to the current
`dispatchRecordId` and `attemptId`.

For `NatsScBusPort`, a successful NATS publish MAY therefore return:

```java
new Dispatched(
    UUID.randomUUID(),
    UUID.randomUUID(),
    envelope.routingKey().topic(),
    envelope.routingKey().partitionKey()
)
```

Do not change `DispatchOutcome` shapes in MU-029.

When returning a seed failure from `NatsScBusPort` for an unsupported payload type, use the verified record shape explicitly. Do not write pseudo-Java such as `Failed("UNSUPPORTED_PAYLOAD_TYPE", ...)`.

Example:

```java
new Failed(
    UUID.randomUUID(),
    UUID.randomUUID(),
    "UNSUPPORTED_PAYLOAD_TYPE",
    "payload type not resolvable for seed",
    false
)
```


---

## 4. Dependencies

### 4.1 Production dependency

Add `io.nats:jnats` intentionally.

Use a current stable `jnats` version. If the package uses `2.20.4`, record that exact version in the implementation report.

```xml
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>${jnats.version}</version>
</dependency>
```

### 4.2 Test dependencies

Default test substrate is Testcontainers NATS:

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

Use `nats:latest` or a pinned NATS image. For JetStream tests, start the server with `--js`.

Local `nats-server` is allowed only as fallback with explicit implementation-report justification.

### 4.3 Atomic dependency / architecture-test rule

The commit that adds `io.nats:jnats` MUST update broker-blocking architecture tests in the same commit.

Required transition:

```text
Before MU-029: reject all broker bindings.
After MU-029: allow io.nats only; continue rejecting Redis/Vert.x/gRPC/Kafka/AMQP/MQTT/etc.
```

`bus.runtime.serialization` MUST remain NATS-free. Do not relax `ScBusSerializationArchitectureTest.serializationPackageDoesNotImportNatsOrTestcontainers`.

---

## 5. Package layout

Expected production files:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  NatsSubjectBuilder.java
  NatsScBusPort.java
  NatsStreamConfiguration.java
  NatsLifecycleChannelMapper.java

src/main/java/com/sovereign/connect/bus/contract/scd/
  ScdCommand.java
  ScdExecutionResult.java
```

Forbidden imports from `com.sovereign.connect.bus.runtime.nats`:

```text
com.sovereign.connect.core.**
com.sovereign.connect.adapter.**
com.sovereign.connect.integration.**
```

Forbidden imports from `com.sovereign.connect.bus.contract.scd`:

```text
com.sovereign.connect.core.**
com.sovereign.connect.adapter.**
io.nats.**
manifest runtime classes
lifecycle runtime classes
provider SDKs
```

---

## 6. Production wire payload reference records

These Java records are reference bindings for public wire payloads. They are not an SDK, not adapter runtime and not semantic authority.

### 6.1 ScdCommand

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

Rules:

```text
targetRef is required.
endpointRef may be null.
operationKind is required.
capabilityRef is required when operationKind = INVOKE_CAPABILITY.
params is an opaque JSON object.
```

### 6.2 ScdExecutionResult

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

`ScdExecutionResult` is provider execution feedback. It is not terminal semantic request-state authority.

---

## 7. Subject grammar for MU-029

### 7.1 Token encoding

Canonical IDs in subject tokens use:

```java
String token = ScSubjectIdTokenCodec.encodeScid1(canonicalId);
ScSubjectTokenValidator.requireSafeToken(token);
```

Correlation IDs in response subject tokens use:

```java
String token = ScCorrelationTokenCodec.encode(correlationId);
ScSubjectTokenValidator.requireSafeToken(token);
```

Never use `scid1_` for correlation tokens.

### 7.2 Publish subjects: no wildcards

Publish subjects MUST NOT contain `*` or `>`.

Expected builder methods:

```java
String buildTimerFiredEventSubject(String habitatId)
String buildCommandSubject(String habitatId, String adapterId, String targetKind, String targetId)
String buildResponseSubject(String habitatId, String adapterId, UUID rootCorrelationId)
String buildLifecycleAnnounceSubject(String habitatId)
```

Seed shapes:

```text
Event timer-fired publish subject:
  sc.v1.{scid1(habitatId)}.event.timer.sc-c.timer-fired

Command fixture subject:
  sc.v1.{scid1(habitatId)}.command.{scid1(adapterId)}.{targetKind}.{scid1(targetId)}

Response fixture subject:
  sc.v1.{scid1(habitatId)}.response.{scid1(adapterId)}.{correlationHex}

Lifecycle announce subject:
  sc.v1.{scid1(habitatId)}.lifecycle.announce
```

`targetKind` is a documented literal token for the fixture, for example `device` or `endpoint`. Validate it as a safe subject token. Do not parse it as topology authority.

### 7.3 Subscription patterns: wildcard allowed only as controlled pattern

The event runtime handler registration receives only `topicOrRoute`, not habitatId. Therefore MU-029 may use a seed wildcard subscription pattern for timer-fired event handlers:

```text
registerEventHandler("sc-c.timer-fired", handler)
  -> subscribe to sc.v1.*.event.timer.sc-c.timer-fired
```

Rules:

```text
Wildcards are allowed only in subscription patterns generated by NatsSubjectBuilder / NatsScBusPort.
Wildcards MUST NOT appear in publish subjects.
Wildcards MUST NOT appear in subject identity tokens.
ScSubjectTokenValidator still rejects wildcards for tokens.
```

This is seed-only until lifecycle route assignment provides explicit habitat/adapter scoped subscriptions.

---

## 8. Payload type resolution in NatsScBusPort

`NatsScBusPort` MUST resolve payload type explicitly before calling `ScJsonWireCodec.toWireEnvelope(...)`.

Seed resolver:

```text
EVENT + routingKey.topic == "sc-c.timer-fired"
  -> ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1

COMMAND + payload instanceof ScdCommand
  -> ScPayloadTypeRegistry.COMMAND_SCD_V1

RESPONSE + payload instanceof ScdExecutionResult
  -> ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1
```

If the payload type cannot be resolved, `NatsScBusPort` must return `Failed`, not publish raw JSON and not guess from Java class names.

Use:

```java
ScJsonWireEnvelope wire = wireCodec.toWireEnvelope(envelope, payloadType, "1.0");
WireEnvelopeValidator.validate(wire);
byte[] bytes = wireCodec.marshal(wire);
connection.publish(subject, bytes);
```

For JetStream publish, publish confirmation is transport evidence only. It is not semantic command success.

---

## 9. NatsScBusPort behavior

`NatsScBusPort` implements `ScBusPort`.

Recommended constructor:

```java
public NatsScBusPort(
    Connection connection,
    NatsSubjectBuilder subjectBuilder,
    ScJsonWireCodec wireCodec,
    EnvelopeValidationService envelopeValidationService
) { ... }
```

Do not inject static utility classes.

On publish:

```text
1. Validate in-process envelope with EnvelopeValidationService.
2. Resolve payloadType and schemaVersion.
3. Convert to ScJsonWireEnvelope using ScJsonWireCodec.toWireEnvelope(..., payloadType, "1.0").
4. Validate wire envelope with WireEnvelopeValidator.validate(...).
5. Build physical subject.
6. Publish bytes to NATS.
7. Return Dispatched on transport publish success.
8. Return Failed on NATS/client/serialization/validation error.
```

For `publishEvent`, if no handler is registered locally, the port may still publish to NATS and return `Dispatched` because NATS is decoupled pub/sub. No local handler must not become `NoHandler` for NATS publish. Handler invocation is tested via NATS subscription.

On register handler:

```text
registerEventHandler("sc-c.timer-fired", handler)
  subscribes to sc.v1.*.event.timer.sc-c.timer-fired.
```

On receive:

```text
1. Unmarshal bytes with ScJsonWireCodec.unmarshal(...).
2. Validate with WireEnvelopeValidator.validate(...).
3. Convert payload JsonNode to reference record or preserve opaque node as appropriate.
4. Construct ScEventEnvelope / ScCommandEnvelope / ScResponseEnvelope using exact constructors.
5. Invoke handler.
```

For MU-029, the only live runtime handler path required is event/timer-fired.

---

## 10. JetStream disposition

MU-029 must not silently downgrade from NATS + JetStream to NATS Core only.

Allowed outcomes:

```text
A. Live JetStream stream configuration applied against a real NATS server with --js.
B. Explicit split: NATS Core live binding in MU-029, JetStream hardening in MU-029B / later MIR.
```

If outcome A is claimed, AC-029-040 must prove:

```text
- Test starts a real NATS server with JetStream enabled.
- Stream configuration is applied through the NATS client against that server.
- The test observes that the stream exists or receives a real stream creation/update acknowledgment.
```

Constructing `NatsStreamConfiguration` objects in Java is not sufficient live JetStream evidence.

In all cases:

```text
JetStream must not replace DispatchStateWritePort.
JetStream must not replace DispatchObservationPort.
JetStream ack is transport confirmation only.
```

---

## 11. Required tests

Expected new/updated tests:

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

Expected count:

```text
pre-MU-029: 411 tests
post-MU-029: >=437 tests, 0 failures, 0 errors, 0 skipped
```

### 11.1 DataSource and Flyway pattern for runtime E2E tests

For `RuntimeDispatchToNatsEventIntegrationTest`, use the SC-B bus persistence
pattern established by MU-025/MU-027:

```java
SQLiteDataSource dataSource = new SQLiteDataSource();
dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("sc-b-nats-runtime-test.sqlite"));
Flyway.configure()
    .dataSource(dataSource)
    .locations("classpath:db/migration")
    .load()
    .migrate();

JdbcDispatchStateRepository stateRepository = new JdbcDispatchStateRepository(dataSource);
JdbcDispatchObservationRepository observationRepository = new JdbcDispatchObservationRepository(dataSource);
```

This test needs SC-B migrations `V100` and `V101` from `classpath:db/migration`.
A raw `SQLiteDataSource` is acceptable for bus persistence tests, matching
`JdbcDispatchStateRepositoryTest`, `JdbcDispatchObservationRepositoryTest` and
`RuntimeDispatchObservationPersistenceTest`.

Use `PerConnectionPragmaDataSource` when exercising SC-C adapter persistence or
SC-C scledger/outbox repositories. Do not mix the SC-C adapter persistence
DataSource pattern into a pure SC-B runtime dispatch persistence test unless the
test explicitly composes the SC-C outbox bridge.

If the runtime E2E composes `ScLedgerDispatchCandidateReadAdapter` and SC-C
outbox rows, keep the existing two-DataSource pattern from
`ScLedgerDispatchBridgeRuntimeTest`:

```text
SC-C scledger datasource: SQLiteDataSource wrapped by PerConnectionPragmaDataSource
SC-B dispatch datasource: raw SQLiteDataSource migrated through Flyway V100/V101
```

---

## 12. Hard stops

```text
STOP-1 — Baseline 411 tests do not pass before changes.
STOP-2 — io.nats added but broker architecture tests not updated in same commit.
STOP-3 — ScBusPort or RuntimeDispatchService constructor changes are required.
STOP-4 — bus.runtime.nats imports core.**, adapter.** or integration.**.
STOP-5 — bus.runtime.serialization imports io.nats or Testcontainers.
STOP-6 — publish path bypasses ScJsonWireCodec / WireEnvelopeValidator.
STOP-7 — COMMAND runtime E2E requires adapterId/deviceId/endpointId from DispatchCandidate.
STOP-8 — JetStream implementation replaces DispatchStateWritePort or DispatchObservationPort.
STOP-9 — live JetStream cannot be made stable; split must be reported explicitly.
STOP-10 — tests require a manually running NATS server without documented fallback.
STOP-11 — RuntimeDispatchToNatsEventIntegrationTest uses a DataSource pattern that prevents Flyway V100/V101 from creating SC-B dispatch tables.
```

---

## 13. Non-goals

Do not implement:

```text
productive adapter runtime
lifecycle admission runtime
Adapter Manifest runtime
Capability Semantics runtime
provider execution
productive SC-D fact families
semantic retry
terminal request-state ownership
SC-C direct NATS publishing
View Composer / SApp / Surfaces
```
