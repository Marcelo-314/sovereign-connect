# CSA-SOV-SC-B-NATS-CORE-JETSTREAM-001

## Pre-MIR Code Surface Audit — SC-B NATS Core + JetStream Binding

```text
Document ID:  CSA-SOV-SC-B-NATS-CORE-JETSTREAM-001
Title:        Code Surface Audit — SC-B NATS Core + JetStream Binding
Version:      v0.2.0-merged
Status:       Merged / Post-SDD / Pre-MIR
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
Scope:        Code surface audit before NATS binding MIR
Baseline:     sovereign-connect-027.zip (post-MU-027)
Input SDD:    SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
Tests:        sovereign-connect 378 / 0 failures / 0 errors / 0 skipped
              EIB reports 56 / 0 failures / 0 errors / 0 skipped
Result:       Approvable for descent with mandatory split MIR recommendation
```

---

# 0. Merge note

This merged CSA consolidates:

```text
1. The initial CSA findings from direct inspection of sovereign-connect-027.zip.
2. The reviewer-provided CSA findings and mandatory MIR split actions.
3. The NATS SDD v0.2.0-candidate downstream rule that serialization utilities should precede any MIR requiring a running NATS server.
```

The merged position is stricter than a generic “NATS-ready” conclusion:

```text
The current codebase is ready for the first NATS-binding descent only as
serialization / wire utilities.

It is not yet ready for live NATS binding as the first MIR.
```

---

# 1. Executive verdict and MIR recommendation

```text
Verdict: Approvable for MIR descent with split MIR recommendation.
```

Mandatory actions:

```text
ACTION-NATS-001 — Split MIR-A (serialization utilities seed) from MIR-B (NATS binding seed).

ACTION-NATS-002 — MIR-A must implement scid1_ encoder / decoder before any subject-token
                  test or NATS binding code.

ACTION-NATS-003 — MIR-A must implement SC-JSON-WIRE-v1 envelope serialization before NATS
                  delivery.

ACTION-NATS-004 — MIR-A must implement correlationId / messageId subject-token encoding
                  for response subjects using 32 lowercase hexadecimal characters without
                  hyphens, per R-RESP-SUBJ-001.
```

Recommended MIR split:

```text
MIR-A: MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
  Basis: MU-028
  Scope:
    - scid1_ encoder / decoder
    - SC-JSON-WIRE-v1 envelope marshaller / unmarshaller
    - correlationId subject token encoder (hex-no-hyphens)
    - payloadType registry seed
    - wire envelope validator
    - deterministic unit / fixture tests
    - zero NATS dependency
    - no running broker

MIR-B: MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
  Basis: MU-029
  Depends on: MU-028 / MIR-A
  Scope:
    - io.nats:jnats dependency
    - NatsScBusPort implements ScBusPort
    - NATS physical subject construction
    - stream / consumer configuration seed
    - lifecycle subject mapping seed
    - route assignment push delivery seed, if scoped
    - dispatch state / observation interaction
    - live NATS tests through Testcontainers, local nats-server, or equivalent
```

Do not open yet:

```text
MIR-SOV-SC-B-NATS-CORE-JETSTREAM-BINDING-SEED-001
```

Open first:

```text
MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
```

---

# 2. Why split descent is mandatory

The SDD correctly permits the CSA to decide whether the NATS binding descent should be split. The code surface strongly supports a split.

## 2.1 No NATS dependency exists

Confirmed absent from the current codebase:

```text
io.nats:jnats
Testcontainers NATS dependency
NatsScBusPort
NatsSubjectBuilder
Nats stream / consumer configuration code
```

Existing architecture tests intentionally reject broker dependencies. This is correct and must remain true for MIR-A.

## 2.2 No scid1_ encoder exists

There is no production implementation of:

```text
scid1_<base64url-no-padding>
```

The only ID codec-like surface found is storage-specific and must not be reused as bus/wire doctrine:

```text
com.sovereign.connect.adapter.persistence.sqlite.SQLiteCanonicalIdCodec
```

That class is adapter-owned, package-specific and storage-oriented. It is not a language-neutral subject-token codec.

## 2.3 No SC-JSON-WIRE-v1 envelope runtime exists

Internal bus records exist, but the public wire envelope does not.

Internal carrier records:

```text
ScCommandEnvelope<T>(metadata, payload, routingKey)
ScEventEnvelope<T>(metadata, payload, routingKey)
ScResponseEnvelope<T>(metadata, payload, routingKey, responseMetadata)
```

Wire fields still absent from production runtime:

```text
envelopeKind
payloadType
payloadSchemaVersion
```

These fields belong to the public wire layer. They should not be added by distorting the internal Java records unless a later contract explicitly decides that the internal shape and wire shape are identical.

## 2.4 No payload type registry exists

No production code validates stable, language-neutral payload identifiers such as:

```text
sc.<domain>.<type-name>.v<major>
```

No registry exists for payloadType fixtures or schema versions.

This blocks reliable NATS delivery because a non-JVM adapter must use `payloadType` and `payloadSchemaVersion`, not Java class names, to interpret the payload family.

## 2.5 Current runtime cannot build full adapter-scoped NATS subjects by itself

`RuntimeDispatchService` currently receives a `DispatchCandidate` whose available fields are:

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

The current dispatch path constructs / forwards routing information sufficient for abstract dispatch, but not enough for a full adapter-scoped physical NATS subject without additional route-resolution state.

Current physical routing gaps:

```text
adapterId may be absent from the candidate-derived route;
deviceId may be absent;
endpointId may be absent;
lifecycle route assignment registry is not implemented;
productive ScdCommand routing shape is not implemented.
```

This is not a defect in MU-024 through MU-027. It is the expected result of keeping the abstract runtime broker-neutral.

A live NATS MIR must not silently invent these fields.

---

# 3. Confirmed code surface inventory

## 3.1 SC-B contract shapes — present

Confirmed contract surface:

```text
ScCommandEnvelope<T>     record(metadata, payload, routingKey)
ScEventEnvelope<T>       record(metadata, payload, routingKey)
ScResponseEnvelope<T>    record(metadata, payload, routingKey, responseMetadata)
ScMessageMetadata        record(messageId: UUID,
                                emittedAt: Instant,
                                correlationId: UUID,
                                causationId: UUID,
                                topologyVersion: String)
ScRoutingKey             record(lane: ScBusLane,
                                partitionKey: String,
                                topic: String,
                                habitatId: String,
                                adapterId: String,
                                deviceId: String,
                                endpointId: String)
ScBusLane                enum { COMMAND, EVENT, RESPONSE, INTERNAL_CONTROL }
ScResponseMetadata       record(requestMessageId: UUID,
                                requestId: UUID,
                                responseKind: ScResponseKind,
                                terminal: boolean,
                                retryable: boolean,
                                sanitizedReason: String,
                                warnings: List<ScResponseWarning>)
```

These shapes are sufficient for MIR-A serialization work.

Interpretation:

```text
ScRoutingKey.topic      = logical topic, e.g. sc-c.timer-fired.
ScRoutingKey.habitatId  = canonical habitat ID string, not subject-token encoded.
NATS subject grammar    = later physical binding over this routing information.
```

## 3.2 ScBusPort — clean extension point

Current port shape:

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

This remains the correct physical-binding extension point.

A later `NatsScBusPort` may implement this interface, but the interface itself does not need to change for MIR-A.

## 3.3 RuntimeDispatchService — clean extension through ports

`RuntimeDispatchService` holds `ScBusPort` by interface injection. Replacing `InMemoryScBusPort` with a future `NatsScBusPort` should be a wiring decision.

The dispatch state lifecycle is already owned by the service:

```text
CLAIMED -> DISPATCHING -> DISPATCHED
DELIVERY_FAILED -> RETRY_SCHEDULED -> CLAIMED
DELIVERY_FAILED -> EXHAUSTED
CANCELLED_BY_SUPERSEDE
```

The physical NATS port must not take over semantic or technical state authority from:

```text
DispatchStateWritePort
DispatchObservationPort
RuntimeDispatchService
```

## 3.4 Runtime hardening baseline — complete

Confirmed validated hardening surfaces:

```text
MU-024 — Abstract SC-B runtime dispatch seed
MU-025 — JDBC-backed dispatch state persistence
MU-026 — SC-C outbox -> SC-B DispatchCandidate bridge
MU-027 — JDBC-backed dispatch observation persistence
```

Representative production surfaces:

```text
JdbcDispatchStateRepository
JdbcDispatchObservationRepository
V100__sc_b_dispatch_state_persistence.sql
V101__sc_b_dispatch_observation_persistence.sql
ScLedgerDispatchCandidateReadAdapter
OutboxEntryDispatchProjector
DeliveryLaneToScBusLaneMapper
DispatchRecordIdFactory
```

This confirms:

```text
GATE-B-RD-HARDENING is satisfied from runtime / diagnostics perspective.
```

It does not imply that live NATS binding can skip serialization utilities or route-resolution design.

## 3.5 Serialization — complete gap

Existing dependencies:

```text
Jackson core / annotations / jsr310 are present.
```

Confirmed gaps:

```text
No bus wire envelope serializer / deserializer.
No ObjectMapper or JsonNode usage in bus.** production code.
No scid1_ encoder.
No payloadType registry.
No SC-JSON-WIRE-v1 envelope DTO/value object.
No wire-envelope validator.
```

MIR-A must create these before MIR-B.

## 3.6 scid1_ encoder — complete gap

Required SDD / ADR behavior:

```text
scid1_<base64url-no-padding>
```

Canonical seed implementation pattern:

```java
public static String encodeScid1(String canonicalId) {
    return "scid1_" + Base64.getUrlEncoder().withoutPadding()
        .encodeToString(canonicalId.getBytes(StandardCharsets.UTF_8));
}
```

MIR-A must implement and test both encode and decode.

## 3.7 correlationId subject token codec — complete gap

Per `R-RESP-SUBJ-001`, response subject correlation tokens use:

```text
UUID -> 32 lowercase hex chars without hyphens
```

Example:

```text
550e8400-e29b-41d4-a716-446655440000
→ 550e8400e29b41d4a716446655440000
```

Rules:

```text
scid1_ is not used for correlation tokens.
correlation tokens are not canonical ID tokens.
wrong length, non-hex chars and hyphenated tokens must be rejected for subject-token decoding.
```

## 3.8 DispatchCandidate fields relevant to subject construction

Relevant current fields:

```text
logicalTopic  = e.g. sc-c.timer-fired
partitionKey  = habitatId from OutboxEntryDispatchProjector
lane          = ScBusLane.EVENT / COMMAND / RESPONSE
correlationId = UUID
messageId     = UUID
```

The NATS subject builder must eventually transform this kind of information into physical subjects.

Example future event route:

```text
logicalTopic "sc-c.timer-fired"
→ sc.v1.{scid1(habitatId)}.event.timer.sc-c.timer-fired
```

This transformation belongs to MIR-B or a later physical route-resolution slice, not MIR-A.

## 3.9 Architecture test surface

Existing architecture tests include no-broker guards:

```text
ScBusArchitectureTest
ScBusHardeningArchitectureTest
ScBusObservationPersistenceArchitectureTest
ScBusOutboxBridgeArchitectureTest
```

Known implication:

```text
MIR-A must leave these no-broker guards green.
MIR-B must revise them narrowly, not delete them.
```

When NATS is intentionally introduced, the rules should become scoped allowlist rules:

```text
Allowed:
  io.nats.* only under com.sovereign.connect.bus.binding.nats or equivalent.

Forbidden:
  io.nats.* under core.*
  io.nats.* under bus.contract
  io.nats.* under bus.runtime.dispatch model
  io.nats.* under integration.scledgerdispatch
  io.nats.* under EIB
```

## 3.10 Existing validation services — reusable but insufficient for wire

`EnvelopeValidationService` validates internal envelope structure and lane/family compatibility.

It does not yet validate:

```text
payloadType presence / grammar;
payloadSchemaVersion presence;
wire envelopeKind;
wire envelopeKind vs routingKey.lane mismatch;
subject token validity;
correlation subject-token encoding.
```

MIR-A should introduce wire-level validation without replacing `EnvelopeValidationService`.

---

# 4. MIR-A scope — Serialization Utilities Seed

## 4.1 Canonical MIR-A identity

```text
MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
MU slot: MU-028
```

## 4.2 Purpose

```text
Implement the deterministic, language-neutral wire/subject utility layer required
before any physical NATS binding can be implemented safely.
```

## 4.3 Non-goals

MIR-A must not implement:

```text
NATS dependency
NatsScBusPort
NATS stream/consumer configuration
lifecycle runtime
productive ScdCommand
SC-D fact-family production payloads
adapter SDK
physical broker tests
Testcontainers
```

## 4.4 Recommended production packages

Preferred:

```text
src/main/java/com/sovereign/connect/bus/serialization/
```

Acceptable alternative:

```text
src/main/java/com/sovereign/connect/bus/wire/
```

The package must not import:

```text
core.*
adapter.*
integration.scledgerdispatch.*
io.nats.*
```

## 4.5 Recommended production classes

```text
ScSubjectIdTokenCodec.java          — scid1_ encode/decode
ScCorrelationTokenCodec.java        — UUID <-> 32 lowercase hex no-hyphens
ScWireEnvelopeKind.java             — COMMAND / EVENT / RESPONSE
ScJsonWireEnvelope.java             — public SC-JSON-WIRE-v1 envelope value object
ScPayloadType.java                  — stable payloadType value object or validator
ScPayloadTypeRegistry.java          — minimal seed registry / test fixture registry
ScJsonWireCodec.java                — ObjectMapper-based SC-JSON-WIRE-v1 codec
WireEnvelopeValidator.java          — wire-level validation
ScSubjectTokenValidator.java        — if not merged into codecs
```

Names may vary if the implementation report maps them explicitly.

---

# 5. MIR-A detailed requirements

## 5.1 ScSubjectIdTokenCodec

Required behavior:

```text
encode(rawCanonicalId) -> scid1_<base64url-no-padding>
decode(scid1Token) -> rawCanonicalId
encode(decode(token)) == token
subject token contains no '.', '+', '/', '='
raw canonical ID roundtrip is byte-identical under UTF-8
reject null IDs
reject blank IDs
reject tokens without scid1_ prefix
reject padded base64 tokens
reject tokens containing forbidden NATS token characters
```

This codec is for canonical ID subject tokens only.

## 5.2 ScCorrelationTokenCodec

Required behavior:

```text
UUID -> 32 lowercase hex chars without hyphens
32 lowercase hex chars -> UUID
reject hyphenated subject-token form
reject non-hex characters
reject wrong length
scid1_ is not used for correlation tokens
```

## 5.3 ScJsonWireEnvelope

Minimum fields:

```text
envelopeKind              COMMAND | EVENT | RESPONSE
payloadType               stable language-neutral type identifier
payloadSchemaVersion      major schema version, seed integer or string
metadata                  ScMessageMetadata-compatible shape
routingKey                ScRoutingKey-compatible shape
payload                   JSON object / JsonNode / Object
responseMetadata          required for RESPONSE, absent/null otherwise
```

Rules:

```text
envelopeKind must match routingKey.lane.
payloadType must not be a Java class name.
payloadSchemaVersion must be present.
responseMetadata must be required for RESPONSE.
command/event/response family mismatch must be rejected before NATS binding.
payload is opaque to SC-B routing decisions except payloadType/schema validation.
```

Example SC-JSON-WIRE-v1 envelope:

```json
{
  "envelopeKind": "COMMAND",
  "payloadType": "sc.command.stub.v1",
  "payloadSchemaVersion": "1",
  "metadata": {
    "messageId": "550e8400-e29b-41d4-a716-446655440000",
    "emittedAt": "2026-05-30T20:00:00Z",
    "correlationId": "550e8400-e29b-41d4-a716-446655440000",
    "causationId": null,
    "topologyVersion": null
  },
  "routingKey": {
    "lane": "COMMAND",
    "partitionKey": "habitat-1",
    "topic": "sc-c.command.device.set",
    "habitatId": "habitat-1",
    "adapterId": "adapter.z2m",
    "deviceId": "device.z2m.0x001",
    "endpointId": null
  },
  "payload": {
    "stubOperation": "TEST",
    "stubTarget": "test-device"
  }
}
```

## 5.4 ScPayloadTypeRegistry

Minimum behavior:

```text
accept stable language-neutral payload types, e.g. sc.test.command.v1;
reject fully qualified Java class names, e.g. com.sovereign.connect...;
reject blank / null payload types;
reject missing schema version;
allow opaque test payload fixtures without introducing productive ScdCommand.
```

The seed registry may be static and test-focused.

It must not require productive SC-D payload schemas.

## 5.5 ScJsonWireCodec

Minimum behavior:

```text
serialize ScJsonWireEnvelope to JSON;
deserialize JSON back to ScJsonWireEnvelope;
validate envelopeKind / routingKey.lane match;
validate payloadType / payloadSchemaVersion;
preserve UUID metadata values;
preserve responseMetadata;
roundtrip payload JSON object without requiring Java domain class binding;
reject missing responseMetadata for RESPONSE;
reject Java class-name payloadType;
```

Jackson may be used because it is already in the dependency graph.

---

# 6. MIR-A test surface

## 6.1 Recommended test files

```text
src/test/java/com/sovereign/connect/bus/serialization/
  ScSubjectIdTokenCodecTest.java
  ScCorrelationTokenCodecTest.java
  ScPayloadTypeRegistryTest.java
  ScJsonWireEnvelopeTest.java
  ScJsonWireCodecTest.java

src/test/java/com/sovereign/connect/bus/
  ScBusSerializationArchitectureTest.java
```

## 6.2 Minimum test methods

### ScSubjectIdTokenCodecTest

```text
scid1EncodingIsReversible
scid1TokenHasNoProblematicChars
scid1TokenHasNoPadding
rejectsTokenWithoutScid1Prefix
rejectsTokenWithForbiddenSubjectCharacters
```

### ScCorrelationTokenCodecTest

```text
correlationIdToHexNoHyphens
correlationIdHexIsExactly32Chars
correlationIdHexIsLowercase
hexEncodingRoundTripsToUuid
rejectsHyphenatedCorrelationToken
rejectsWrongLengthCorrelationToken
rejectsNonHexCorrelationToken
```

### ScPayloadTypeRegistryTest

```text
acceptsStableLanguageNeutralPayloadType
rejectsNullPayloadType
rejectsBlankPayloadType
rejectsJavaClassNamePayloadType
rejectsPayloadTypeWithoutMajorVersion
```

### ScJsonWireEnvelopeTest / ScJsonWireCodecTest

```text
commandEnvelopeRoundTrip
eventEnvelopeRoundTrip
responseEnvelopeRoundTrip
envelopeKindIsPresentInJson
payloadTypeIsPresentInJson
payloadSchemaVersionIsPresentInJson
unknownPayloadTypeIsPreservedOpaquely
javaClassNamesAreNotPayloadTypes
rejectsMismatchEnvelopeKindAndLane
responseEnvelopeRequiresResponseMetadata
payloadJsonRemainsOpaque
```

### ScBusSerializationArchitectureTest

```text
serializationPackageDoesNotImportCore
serializationPackageDoesNotImportAdapter
serializationPackageDoesNotImportIntegrationScledgerdispatch
serializationPackageDoesNotImportNats
noBrokerDependencyIntroducedBySerializationSeed
noJavaClassNameAppearsInPayloadTypeConstants
noProductiveScdCommandIntroduced
```

## 6.3 Expected test delta

Recommended target:

```text
+20 to +30 unit / architecture tests
```

Minimum acceptable target if tests are consolidated:

```text
+15 tests
```

The execution package should set the exact expected number after finalizing class and method names.

---

# 7. MIR-A stop conditions

```text
STOP-A1: mvn -q compile
  Expected: PASS.

STOP-A2: mvn -q test -Dtest="ScSubjectIdTokenCodecTest,ScCorrelationTokenCodecTest,ScPayloadTypeRegistryTest,ScJsonWireCodecTest,ScBusSerializationArchitectureTest"
  Expected: all pass.

STOP-A3: mvn -q test
  Expected: >= 398 tests, 0 failures, 0 errors, 0 skipped.
  Baseline: 378 sovereign-connect root tests from MU-027 evidence.

STOP-A4: POM / dependency guard
  No io.nats / jetstream dependency in root pom or EIB pom.

STOP-A5: Production class guard
  No productive ScdCommand, fact-family production classes, NatsScBusPort or io.nats imports.

STOP-A6: Wire sufficiency guard
  No Java class names used as payloadType constants.
```

The exact full-test minimum may be adjusted by the execution package if the MIR deliberately chooses the consolidated minimum test suite.

---

# 8. MIR-B scope — later NATS binding seed

## 8.1 Canonical MIR-B identity

```text
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
MU slot: MU-029
Depends on: MU-028 / MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
```

## 8.2 Dependency addition

MIR-B may add:

```xml
<dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <version>2.x.x</version>
</dependency>
```

The exact version must be chosen during the MIR-B execution package or implementation preparation.

Because this is current / dependency-sensitive, execution should verify the latest stable `jnats` version at that time.

## 8.3 Test substrate for MIR-B

Recommended:

```text
Testcontainers NATS
```

Dependencies:

```text
org.testcontainers:testcontainers
org.testcontainers:junit-jupiter
NATS official container image
```

Fallback if Testcontainers introduces CI friction:

```text
nats-server binary test fixture launched with ProcessBuilder
```

Contract-only / mock-only tests are insufficient for MIR-B because the architecture must verify actual NATS subjects and message delivery behavior.

## 8.4 New production classes for MIR-B

Recommended package:

```text
com.sovereign.connect.bus.binding.nats
```

Recommended classes:

```text
NatsScBusPort.java
NatsSubjectBuilder.java
NatsStreamConfiguration.java
NatsLifecycleChannelMapper.java
NatsRouteAssignmentAdapter.java
```

## 8.5 Minimum end-to-end MIR-B test

The seed MIR-B test should demonstrate the full physical path:

```text
1. SC-C writes OutboxEntry (TIMER_FIRED_SIGNAL).
2. ScOutboxDispatchReadPort reads it.
3. OutboxEntryDispatchProjector projects it to DispatchCandidate (lane=EVENT).
4. RuntimeDispatchService dispatches via NatsScBusPort.
5. NatsScBusPort publishes to sc.v1.{scid1(habitatId)}.event.timer.sc-c.timer-fired.
6. Test subscriber receives the message from the NATS subject.
7. Dispatch observation records DISPATCHED.
8. No semantic interpretation is performed.
```

## 8.6 MIR-B architecture tests

`ScBusArchitectureTest.pomsDoNotIntroducePhysicalBusBindingDependency` and related guards must be updated in MIR-B, not MIR-A.

Correct update strategy:

```text
Do not delete no-broker tests.
Convert them into scoped allowlist tests.
```

Required constraints:

```text
io.nats.* allowed only under com.sovereign.connect.bus.binding.nats or equivalent.
No NATS imports under core.*.
No NATS imports under bus.contract.
No NATS imports under bus.runtime.dispatch model.
No NATS imports under integration.scledgerdispatch.
No NATS imports under EIB.
Other broker APIs remain forbidden.
```

Additional MIR-B architecture tests:

```text
natsPackageDoesNotImportCore
natsPackageDoesNotImportAdapter
natsSubjectBuilderUsesScid1EncoderNotRawIds
natsPackageDoesNotImportRedisVertxGrpc
brokerAckIsNotSemanticSuccess
jetStreamDoesNotReplaceDispatchStateOrObservationPorts
```

---

# 9. MIR-B prerequisites and unresolved design surfaces

## 9.1 Route resolution for physical subjects

Current abstract runtime does not yet provide enough adapter-scoped physical route information for the full NATS subject grammar.

A future `NatsScBusPort` needs a rule for constructing subjects from some combination of:

```text
routingKey.topic
routingKey.partitionKey
route assignment state
adapter route alias
candidate source metadata
lifecycle route assignment payload
```

Recommended MIR-B options:

```text
Option B1 — topic-as-physical-subject seed:
  Treat routingKey.topic as already-resolved physical subject in the first NATS seed.
  Narrow and testable, but less representative of full route assignment.

Option B2 — route resolver seed:
  Add NatsSubjectResolver / RouteAssignmentRegistry consumed by NatsScBusPort.
  Better matches lifecycle SDD, but may require lifecycle implementation work.

Option B3 — event-only NATS seed:
  Bind only scoped event dispatch first, e.g. TIMER_FIRED_SIGNAL -> EVENT.
  Avoids productive command semantics, but still requires MIR-A.
```

CSA position:

```text
Do not decide B1/B2/B3 in this CSA.
Complete MIR-A first, then reassess MIR-B scope.
```

## 9.2 Productive command payload fixture

Productive `ScdCommand` remains intentionally absent.

Therefore MIR-B should either:

```text
1. use opaque payload fixtures only; or
2. wait for PDR-SOV-SC-D-COMMAND-PAYLOAD-001.
```

If MIR-B uses opaque payload fixtures, the implementation report must record an explicit deferred-payload exception.

## 9.3 Lifecycle runtime surface

The lifecycle SDD is accepted as design baseline, but no runtime lifecycle implementation currently exists.

Therefore MIR-B must not claim:

```text
real adapter admission;
ACTIVE state enforcement;
route revocation enforcement;
challenge/response trust flow;
adapter trust bundle verification;
full route assignment registry.
```

A seed MIR-B may scope lifecycle to:

```text
announce + challenge + route-assignment push demonstration
```

only if the MIR explicitly declares which states are in scope and which are deferred.

---

# 10. Lifecycle channel implementation note

The lifecycle channel design requires future payload types such as:

```text
sc.lifecycle.adapter-announce.v1
sc.lifecycle.adapter-challenge.v1
sc.lifecycle.adapter-registration-response.v1
sc.lifecycle.adapter-decision.v1
sc.lifecycle.adapter-route-assignment.v1
sc.lifecycle.adapter-route-ready.v1
sc.lifecycle.adapter-activation.v1
sc.lifecycle.adapter-revocation.v1
```

These are not implemented in code yet.

The NATS binding must not define SC-C admission authority. SC-C owns operational admission.

For MIR-B, lifecycle behavior must be narrowed or deferred unless a lifecycle runtime MIR precedes it.

---

# 11. PDR-SOV-SC-D-COMMAND-PAYLOAD-001 dependency

Per the NATS SDD, productive command payload fixtures require a command-payload artifact.

Because that artifact does not exist yet:

```text
MIR-A:
  no productive command fixture needed;
  pure serialization tests may use opaque fixtures.

MIR-B:
  either use opaque JSON stub payloads, or wait for PDR-SOV-SC-D-COMMAND-PAYLOAD-001.
```

Allowed opaque MIR-B fixture if explicitly documented:

```json
{
  "payloadType": "sc.command.stub.v1",
  "payload": {
    "stubOperation": "TEST",
    "stubTarget": "test-device"
  }
}
```

---

# 12. Search ledger

```text
Baseline zip:
  sovereign-connect-027.zip

Baseline branch inside zip:
  feat/sc-b-mir-027-dispatch-observation-persistence

Latest visible commits:
  f9a9c64 feat(sc-b): persist dispatch observations
  00acf1e docs(sc-b): record dispatch observation persistence evidence
  c8429c8 docs(sc-b): finalize dispatch observation report hashes

Surefire root module:
  47 XML reports, 378 tests, 0 failures, 0 errors, 0 skipped

Surefire EIB module:
  13 XML reports, 56 tests, 0 failures, 0 errors, 0 skipped

bus.contract.*:
  9 shapes, all clean, no serialization utilities

ScBusPort:
  6 methods, clean interface, no NATS

RuntimeDispatchService:
  7-dependency constructor, all by interface, clean extension point

bus.serialization / bus.wire:
  absent — greenfield for MIR-A

scid1_ encoder:
  absent — must be in MIR-A

correlation subject token codec:
  absent — must be in MIR-A

SC-JSON-WIRE-v1 runtime envelope:
  absent — must be in MIR-A

payloadType registry:
  absent — must be in MIR-A

NATS dependency:
  absent — must remain absent in MIR-A; introduced only in MIR-B

Testcontainers:
  absent — MIR-B only if selected

NATS subject resolver:
  absent — MIR-B or later route-resolution slice

NATS live ScBusPort:
  absent — MIR-B

lifecycle runtime implementation:
  absent — not MIR-A; MIR-B only if explicitly scoped

productive ScdCommand:
  absent by design

fact family production payloads:
  absent by design

Architecture test pom/no-broker guards:
  present; unchanged for MIR-A; scoped rewrite required for MIR-B

DispatchCandidate.logicalTopic:
  carries OutboxEntry.logicalTopic

DispatchCandidate.partitionKey:
  carries habitatId, not scid1_-encoded

ScRoutingKey.habitatId:
  canonical string, not encoded

correlationId:
  UUID type — hex-no-hyphens for response subjects

OutboxEntryDispatchProjector:
  projects habitatId as partitionKey — correct
```

---

# 13. Final recommendation

```text
CSA result: split descent is required.
```

Open first:

```text
MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001 v0.1.0-draft
```

Do not open yet:

```text
MIR-SOV-SC-B-NATS-CORE-JETSTREAM-BINDING-SEED-001
```

Rationale:

```text
The current codebase is architecturally ready for wire utilities, not yet for live NATS.
Serialization / scid1_ / correlation-token utilities are deterministic, broker-free,
language-neutral and directly required by the SDD.

A live NATS binding would otherwise have to introduce serialization, token encoding,
subject resolution, route assignment assumptions and broker dependency in one step.
That would be too large and would risk turning NATS into an accidental protocol authority.
```

Suggested branch for MIR-A implementation later:

```text
feat/sc-b-mir-028-serialization-utilities-seed
```

Suggested commit later:

```text
feat(sc-b): add serialization utilities seed
```

