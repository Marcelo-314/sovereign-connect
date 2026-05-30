# MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001

## Materialization Increment Record — SC-B Serialization Utilities Seed

```text
Document ID:  MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
Title:        Materialization Increment Record — SC-B Serialization Utilities Seed
Version:      v0.2.0-candidate
Status:       Candidate
Date:         2026-05-30
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-B
Track:        SC-B NATS Core + JetStream Binding Preparation / MIR-A
MU ID:        MU-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
MU Slot:      MU-028
```


---

## Changelog v0.2.0-candidate

Promotes `v0.1.0-draft` to candidate after review.

This version:

```text
1. Closes OQ-MIR-028-002 with an explicit opaque-passthrough policy for syntactically valid unknown payloadType values.
2. Clarifies that marshalling/unmarshalling must preserve unknown-but-valid payloadType values opaquely and must not require all future SC-D payload schemas to be known by MU-028.
3. Keeps the `ScWireEnvelopeKind` implementation form as an execution-package concern: it may be an enum, constants or equivalent representation, provided wire output remains COMMAND / EVENT / RESPONSE and validation remains explicit.
4. Preserves MIR-A scope: broker-free serialization utilities only; no NATS dependency, no lifecycle runtime and no productive ScdCommand.
```

## 0. MIR boundary

This MIR authorizes a future implementation attempt for the **SC-B Serialization Utilities Seed**.

This is the first descent from:

```text
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
CSA-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-merged
```

The CSA concluded that NATS binding work must be split into two implementation increments:

```text
MIR-A / MU-028 — Serialization utilities seed, no broker dependency.
MIR-B / MU-029 — NATS Core + JetStream binding seed, depends on MU-028.
```

This MIR covers only **MIR-A**.

This MIR does not include an execution prompt, context file, acceptance map, Codex instructions, or implementation package. Those artifacts must be produced separately if this MIR is promoted to candidate and accepted for execution descent.

---

## 1. Purpose

MU-024 created the abstract SC-B runtime dispatch substrate.
MU-025 made dispatch state restart-visible.
MU-026 created the SC-C outbox → SC-B DispatchCandidate bridge.
MU-027 made dispatch observations restart-visible.

The next binding track requires a wire layer that can be tested without a running broker:

```text
ScEnvelope / ScRoutingKey / ScMessageMetadata
  → SC-JSON-WIRE-v1
  → subject-safe tokens
  → payloadType / payloadSchemaVersion discrimination
  → future NATS subject construction and delivery
```

Canonical purpose:

```text
Create deterministic, broker-free serialization and subject-token utilities
required before any live NATS Core + JetStream binding implementation.
```

This MIR is intentionally **not** the NATS binding MIR.

---

## 2. Depends on

```text
ADR-SOV-SC-SERIALIZATION-001 v0.2.0-candidate
NT-SOV-SC-D-WIRE-PROTOCOL-SUFFICIENCY-001 v0.2.0-candidate
SDD-SOV-SC-B-LIFECYCLE-CHANNEL-001 v0.2.0-candidate
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
CSA-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-merged
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v1.0.0-accepted / Validated L4
```

---

## 3. Related

```text
PDR-SOV-SC-BUS-CONTRACT-001 v0.4.6-draft
PDR-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001 v0.2.0-candidate
ADR-SOV-SC-BUS-TECH-001 v0.2.2-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.26-draft
PDR-SOV-SC-C-PRODUCTION-READINESS-001 v0.1.20-draft
```

These related artifacts are not implementation dependencies unless the execution package explicitly uses them.

---

## 4. Baseline state

Confirmed post-MU-027 code surface:

```text
ScCommandEnvelope<T>     record(metadata, payload, routingKey)
ScEventEnvelope<T>       record(metadata, payload, routingKey)
ScResponseEnvelope<T>    record(metadata, payload, routingKey, responseMetadata)
ScMessageMetadata        record(messageId, emittedAt, correlationId, causationId, topologyVersion)
ScRoutingKey             record(lane, partitionKey, topic, habitatId, adapterId, deviceId, endpointId)
ScBusLane                COMMAND, EVENT, RESPONSE, INTERNAL_CONTROL
ScBusPort                publish/register methods
RuntimeDispatchService   depends on ScBusPort by interface
```

Confirmed gaps:

```text
No scid1_ encoder / decoder exists.
No correlationId subject-token codec exists.
No SC-JSON-WIRE-v1 runtime DTO exists.
No wire envelope marshaller / unmarshaller exists.
No payloadType registry exists.
No payloadSchemaVersion validator exists.
No subject-token validator exists.
No NATS dependency exists.
No Testcontainers dependency exists.
Architecture tests still reject broker dependencies.
```

Confirmed available dependencies:

```text
Jackson core / annotations / jsr310 are already present.
```

---

## 5. Problem statement

The NATS SDD defines physical subjects and wire-message behavior, but the codebase cannot yet perform the broker-independent transformations required by that SDD:

```text
canonical ID string
  → scid1_<base64url-no-padding> subject token

UUID correlation ID
  → 32 lowercase hexadecimal chars without hyphens

ScCommandEnvelope / ScEventEnvelope / ScResponseEnvelope
  → SC-JSON-WIRE-v1 envelope

payload object
  → payloadType + payloadSchemaVersion + opaque JSON payload
```

If a NATS binding MIR is opened before these utilities exist, it will conflate:

```text
serialization
subject-token encoding
payload-type discrimination
live broker client behavior
stream / consumer configuration
runtime dispatch integration
```

This MIR prevents that collapse by creating a deterministic, unit-testable wire utilities layer first.

---

## 6. Goals

### G-MIR-028-001 — Subject-safe canonical ID token codec

Implement `scid1_` encode/decode utilities for canonical IDs used in subject tokens.

### G-MIR-028-002 — UUID correlation token codec

Implement response-subject correlation token encoding using 32 lowercase hexadecimal characters without hyphens.

### G-MIR-028-003 — SC-JSON-WIRE-v1 envelope DTO

Introduce a language-neutral wire envelope shape containing:

```text
envelopeKind
payloadType
payloadSchemaVersion
metadata
routingKey
responseMetadata, when applicable
payload
```

### G-MIR-028-004 — Wire marshalling / unmarshalling

Serialize and deserialize command, event and response envelopes to/from JSON without requiring Java class names as payload discriminators.

### G-MIR-028-005 — Payload type registry seed

Create a seed registry or constants surface for stable language-neutral `payloadType` identifiers.

### G-MIR-028-006 — Wire validation

Validate required wire fields and `envelopeKind` ↔ `routingKey.lane` consistency before any physical binding uses the envelope.

### G-MIR-028-007 — Preserve NATS-free execution

Keep this increment broker-free and deterministic.

---

## 7. Non-goals

This MIR does not authorize:

```text
- io.nats:jnats dependency.
- Testcontainers dependency.
- NatsScBusPort.
- NATS subject builder for live publish/subscribe.
- JetStream stream/consumer configuration.
- lifecycle-channel runtime implementation.
- live route assignment push.
- productive ScdCommand shape.
- SC-D fact family production classes.
- productive adapter execution.
- semantic retry.
- terminal request-state ownership.
- changes to RuntimeDispatchService dispatch semantics.
- changes to DispatchStateWritePort or DispatchObservationPort.
- changes to SC-C outbox/ledger semantics.
```

---

## 8. Mandatory implementation actions

This MIR consumes the mandatory CSA actions for MIR-A.

```text
ACTION-NATS-001 — Split MIR-A serialization utilities from MIR-B NATS binding.
  Disposition: this MIR is MIR-A only.

ACTION-NATS-002 — MIR-A must implement scid1_ encoder before any subject token test or binding code.
  Disposition: required by this MIR.

ACTION-NATS-003 — MIR-A must implement SC-JSON-WIRE-v1 envelope serialization before NATS delivery.
  Disposition: required by this MIR.

ACTION-NATS-004 — correlationId and messageId are UUID; hex-without-hyphens encoding for response subject tokens must be implemented in MIR-A serialization utilities.
  Disposition: required by this MIR for correlationId and reusable for messageId where needed.
```

---

## 9. Design decisions for this MIR

### D-MIR-028-001 — MIR-A is broker-free

This MIR must not add any NATS, JetStream, Testcontainers or broker dependency.

Existing architecture tests that reject broker dependencies must remain valid after MU-028.

### D-MIR-028-002 — Package location

Default production package:

```text
com.sovereign.connect.bus.runtime.serialization
```

Alternative package names are acceptable only if the implementation report maps them clearly to this MIR and preserves the same boundaries.

Serialization utilities must not import:

```text
com.sovereign.connect.core.*
com.sovereign.connect.adapter.*
io.nats.*
```

### D-MIR-028-003 — scid1_ canonical ID token codec

Canonical IDs appearing in subject tokens must use:

```text
scid1_<base64url-no-padding(UTF-8 canonical ID bytes)>
```

Rules:

```text
- input is a canonical ID string;
- output starts with scid1_;
- output uses base64url without padding;
- output contains no '.', '+', '/', '=';
- decoder returns the exact original canonical ID string;
- codec must not parse provider or topology semantics from the ID;
- codec must not infer entity kind from token content.
```

This codec is for canonical IDs, not correlation IDs.

### D-MIR-028-004 — UUID subject-token codec

Correlation IDs used in response subject tokens must use:

```text
32 lowercase hexadecimal characters without hyphens
```

Example:

```text
UUID string:            550e8400-e29b-41d4-a716-446655440000
response subject token: 550e8400e29b41d4a716446655440000
```

Rules:

```text
- scid1_ is not used for UUID correlation tokens;
- token must be exactly 32 chars;
- token must be lowercase hex;
- token must decode back to the original UUID;
- invalid length or non-hex chars must be rejected.
```

### D-MIR-028-005 — Subject token validator

Introduce a small validator for physical subject token safety.

Minimum rule:

```text
A subject token MUST NOT contain '.', '+', '/', '='.
```

The validator may also reject blank tokens and whitespace.

The validator must not decide routing semantics.

### D-MIR-028-006 — Wire envelope kind

Introduce a wire-level discriminator:

```text
COMMAND
EVENT
RESPONSE
```

`INTERNAL_CONTROL` is a routing lane, not a public adapter payload envelope kind for this seed unless a later MIR explicitly extends the wire profile.

### D-MIR-028-007 — SC-JSON-WIRE-v1 envelope shape

Required wire envelope fields:

```text
envelopeKind: COMMAND | EVENT | RESPONSE
payloadType: stable language-neutral identifier
payloadSchemaVersion: stable schema version string
metadata: ScMessageMetadata-compatible object
routingKey: ScRoutingKey-compatible object
responseMetadata: present only for RESPONSE envelopes unless explicitly null
payload: opaque JSON value/object
```

The wire envelope must not include Java class names as type discriminators.

### D-MIR-028-008 — Payload type identifiers

Payload type identifiers must be stable, language-neutral strings.

Seed grammar:

```text
sc.<domain>.<type-name>.v<major>
```

Examples:

```text
sc.command.stub.v1
sc.event.timer-fired.v1
sc.response.stub.v1
sc.lifecycle.adapter-announce.v1
```

The registry may be small and seed-scoped, but it must reject Java class-name payload types such as:

```text
com.sovereign.connect.SomeJavaClass
```

### D-MIR-028-009 — Payload schema version

Every wire envelope must carry `payloadSchemaVersion`.

Seed version format may be:

```text
1
1.0
v1
```

The execution package must choose one concrete accepted format. Recommended seed value:

```text
1
```

### D-MIR-028-010 — envelopeKind must match routingKey.lane

Wire validation must reject mismatches:

```text
envelopeKind=COMMAND  requires routingKey.lane=COMMAND
envelopeKind=EVENT    requires routingKey.lane=EVENT
envelopeKind=RESPONSE requires routingKey.lane=RESPONSE
```

Mismatch is an invalid wire envelope, not a heuristic routing ambiguity.

### D-MIR-028-011 — Round-trip behavior preserves opaque payloads

Wire marshalling/unmarshalling must preserve opaque JSON payloads without requiring domain-specific payload classes.

This is necessary for language-neutral adapter conformance and for pre-ScdCommand seed tests.

### D-MIR-028-012 — Existing in-process envelope validation remains distinct

Existing `EnvelopeValidationService` remains responsible for in-process envelope structural validation.

MU-028 adds wire validation; it does not replace the runtime dispatch validation services.

### D-MIR-028-013 — No raw provider payload authority

Serialization utilities may carry payload JSON opaquely, but they must not reinterpret raw provider payloads as canonical domain language.

---

## 10. Expected implementation surface

Expected production surface:

```text
src/main/java/com/sovereign/connect/bus/runtime/serialization/
  ScSubjectIdTokenCodec.java
  ScCorrelationTokenCodec.java
  ScSubjectTokenValidator.java
  ScWireEnvelopeKind.java
  ScJsonWireEnvelope.java
  ScPayloadType.java or ScPayloadTypeRegistry.java
  ScJsonWireCodec.java
  WireEnvelopeValidator.java
```

Names may vary if the implementation report maps actual classes to acceptance criteria.

Expected test surface:

```text
src/test/java/com/sovereign/connect/bus/runtime/serialization/
  ScSubjectIdTokenCodecTest.java
  ScCorrelationTokenCodecTest.java
  ScJsonWireCodecTest.java
  WireEnvelopeValidatorTest.java
  ScPayloadTypeRegistryTest.java

src/test/java/com/sovereign/connect/bus/
  ScBusSerializationArchitectureTest.java
```

---

## 11. Acceptance criteria

### 11.1 Scope and dependencies

```text
AC-028-001 — No NATS / JetStream / Testcontainers dependency is introduced.
AC-028-002 — No NatsScBusPort or NATS package is introduced.
AC-028-003 — Serialization package does not import core.*.
AC-028-004 — Serialization package does not import adapter.*.
AC-028-005 — Serialization package does not import io.nats.* or broker-specific APIs.
```

### 11.2 scid1_ canonical ID token codec

```text
AC-028-006 — scid1_ encoding prefixes encoded canonical IDs with scid1_.
AC-028-007 — scid1_ encoding uses base64url without padding.
AC-028-008 — scid1_ tokens contain no '.', '+', '/', '='.
AC-028-009 — scid1_ decode returns the byte-identical original canonical ID string.
AC-028-010 — scid1_ roundtrip verifies encode(decode(token)) == token.
AC-028-011 — malformed scid1_ tokens are rejected.
```

### 11.3 UUID correlation token codec

```text
AC-028-012 — correlation UUID token encoding returns 32 lowercase hex chars with no hyphens.
AC-028-013 — correlation UUID token decoding returns the original UUID.
AC-028-014 — invalid correlation token length is rejected.
AC-028-015 — non-hex correlation token chars are rejected.
AC-028-016 — scid1_ encoding is not used for correlation tokens.
```

### 11.4 Subject token validation

```text
AC-028-017 — subject token validator rejects tokens containing '.'.
AC-028-018 — subject token validator rejects tokens containing '+'.
AC-028-019 — subject token validator rejects tokens containing '/'.
AC-028-020 — subject token validator rejects tokens containing '='.
AC-028-021 — subject token validator accepts valid scid1_ and UUID correlation tokens.
```

### 11.5 Wire envelope shape

```text
AC-028-022 — serialized wire envelope contains envelopeKind.
AC-028-023 — serialized wire envelope contains payloadType.
AC-028-024 — serialized wire envelope contains payloadSchemaVersion.
AC-028-025 — serialized wire envelope contains metadata.
AC-028-026 — serialized wire envelope contains routingKey.
AC-028-027 — response wire envelope contains responseMetadata.
AC-028-028 — Java class names are not used as payloadType values.
```

### 11.6 Wire roundtrip

```text
AC-028-029 — command envelope roundtrips through SC-JSON-WIRE-v1.
AC-028-030 — event envelope roundtrips through SC-JSON-WIRE-v1.
AC-028-031 — response envelope roundtrips through SC-JSON-WIRE-v1.
AC-028-032 — opaque JSON payload is preserved through roundtrip.
AC-028-033 — unknown but syntactically valid payloadType is preserved opaquely by marshalling/unmarshalling; registry-level semantic acceptance remains a higher-layer concern.
```

### 11.7 Wire validation

```text
AC-028-034 — wire validator rejects missing payloadType.
AC-028-035 — wire validator rejects missing payloadSchemaVersion.
AC-028-036 — wire validator rejects envelopeKind / routingKey.lane mismatch.
AC-028-037 — wire validator accepts valid COMMAND envelope.
AC-028-038 — wire validator accepts valid EVENT envelope.
AC-028-039 — wire validator accepts valid RESPONSE envelope.
```

### 11.8 Reporting

```text
AC-028-040 — Implementation report records branch, commits and changed files.
AC-028-041 — Implementation report records final test counts.
AC-028-042 — Implementation report confirms no NATS dependency was introduced.
AC-028-043 — Implementation report records retained MIR-B NATS binding debt.
AC-028-044 — Acceptance map links each AC-028 criterion to tests/source/evidence.
```

---

## 12. Expected validation

Minimum expected validation after implementation:

```text
sovereign-connect tests: previous 378 + serialization tests
Expected result: 0 failures, 0 errors, 0 skipped
SC-C / non-bus baseline: remains green
EIB reports: remain unaffected if module reports are present
```

Expected test delta:

```text
+15 to +25 tests
```

The exact test count is not fixed by this MIR; the execution package should set the expected count after inspecting the final baseline.

---

## 13. Retained debt disposition

### Closed by this MIR if validated

```text
DEBT-SER-UTIL-001 — scid1_ subject-safe canonical ID codec absent.
DEBT-SER-UTIL-002 — correlation UUID subject-token codec absent.
DEBT-SER-UTIL-003 — SC-JSON-WIRE-v1 runtime envelope utilities absent.
DEBT-SER-UTIL-004 — payloadType / payloadSchemaVersion wire validation absent.
```

### Retained after this MIR

```text
DEBT-B-NATS-001 — NATS Core + JetStream binding absent.
DEBT-B-NATS-002 — NatsScBusPort absent.
DEBT-B-NATS-003 — NATS subject builder absent.
DEBT-B-NATS-004 — JetStream stream/consumer configuration absent.
DEBT-B-LC-RUNTIME-001 — lifecycle-channel runtime implementation absent.
DEBT-SCD-CMD-001 — productive ScdCommand payload shape absent.
DEBT-SCD-FACT-001 — SC-D discovery/state/health fact family schemas absent.
```

---

## 14. Risks

### RISK-MIR-028-001 — Java class name leakage into payloadType

Risk:

```text
The implementation uses Java class names as payload discriminators.
```

Mitigation:

```text
payloadType must be language-neutral and registry-backed or rule-validated.
Architecture/tests must reject class-name payloadType constants.
```

### RISK-MIR-028-002 — scid1_ becomes semantic parser

Risk:

```text
The encoder/decoder starts interpreting device/provider/topology semantics.
```

Mitigation:

```text
The codec treats IDs as opaque UTF-8 strings only.
```

### RISK-MIR-028-003 — Correlation tokens confused with canonical IDs

Risk:

```text
UUID correlation IDs are encoded using scid1_ or treated as canonical IDs.
```

Mitigation:

```text
Use the explicit 32-lowercase-hex UUID token codec for correlation subject tokens.
```

### RISK-MIR-028-004 — Broker dependency creeps into serialization seed

Risk:

```text
NATS APIs or Testcontainers enter MIR-A prematurely.
```

Mitigation:

```text
No NATS dependency, no NATS package, architecture guard remains active.
```

### RISK-MIR-028-005 — Wire validator replaces runtime validation

Risk:

```text
WireEnvelopeValidator is treated as a replacement for EnvelopeValidationService.
```

Mitigation:

```text
MIR declares these as distinct layers: in-process validation vs wire validation.
```

### RISK-MIR-028-006 — Over-specific payload schema before SC-D command PDR

Risk:

```text
Serialization seed accidentally defines productive ScdCommand semantics.
```

Mitigation:

```text
Use stub/opaque payloads and retain PDR-SOV-SC-D-COMMAND-PAYLOAD-001 as downstream.
```

---

## 15. CSA / execution-package notes

The execution package should confirm the current source layout before coding.

Specific checks:

```text
- current package names for ScCommandEnvelope, ScEventEnvelope, ScResponseEnvelope;
- current package names for ScMessageMetadata and ScRoutingKey;
- whether ObjectMapper configuration already exists in any shared test support;
- whether bus architecture tests reject any new package layout;
- whether Java version supports records/sealed patterns used in the codebase;
- whether Jackson JavaTimeModule is already configured in any existing production bean.
```

The execution package must include paste-ready test names and hard stops.

Execution package note on `ScWireEnvelopeKind`:

```text
D-MIR-028-006 requires a wire-level discriminator with values COMMAND / EVENT / RESPONSE.
The execution package may choose either:
  - a Java enum such as ScWireEnvelopeKind; or
  - String constants / registry-backed constants.

The normative requirement is the wire value and validator behavior, not the Java representation.
Codex must not invent additional wire kinds or expose INTERNAL_CONTROL as a public adapter envelope kind in MU-028.
```

---

## 16. Downstream sequence

After MU-028 validation, the expected next artifact is:

```text
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
```

But only if:

```text
- MU-028 validates scid1_, correlation tokens and SC-JSON-WIRE-v1;
- CSA confirms the first NATS binding slice is still coherent;
- PDR-SOV-SC-D-COMMAND-PAYLOAD-001 is either opened before MIR-B or MIR-B explicitly uses a stub payload exception;
- NATS test substrate decision is made: Testcontainers or local nats-server.
```

The downstream NATS MIR must update broker-dependency architecture tests intentionally and must not hide the dependency addition inside serialization work.

---

## 17. MIR acceptance checklist

Before promoting this MIR to candidate:

```text
[ ] Confirm no additional post-CSA code changes alter the serialization target surface.
[ ] Confirm payloadSchemaVersion seed format preference in execution package.
[ ] Confirm package location: bus.runtime.serialization or documented equivalent.
[ ] Confirm expected test delta after CSA refresh if needed.
```

Before accepting implementation as L4:

```text
[ ] Tests pass with no NATS dependency.
[ ] scid1_ roundtrip is verified.
[ ] UUID correlation token roundtrip is verified.
[ ] COMMAND/EVENT/RESPONSE wire envelope roundtrips are verified.
[ ] envelopeKind / routingKey.lane mismatch rejection is verified.
[ ] payloadType does not use Java class names.
[ ] Implementation report records retained NATS binding debt.
```

---

## 18. Open questions

### OQ-MIR-028-001 — payloadSchemaVersion exact seed format

Options:

```text
1
1.0
v1
```

Recommended execution-package decision:

```text
1
```

Rationale:

```text
Major-only evolution is enough for the current seed; breaking payload schema changes create a new payloadType major.
```

### OQ-MIR-028-002 — Opaque passthrough for unknown payloadType

Status: Closed by v0.2.0-candidate.

Decision:

```text
Marshaller/unmarshaller MUST permit syntactically valid unknown payloadType values
and preserve their payloads opaquely.

WireEnvelopeValidator MUST validate:
  - payloadType presence;
  - payloadType grammar;
  - payloadSchemaVersion presence;
  - envelopeKind / routingKey.lane consistency.

WireEnvelopeValidator MUST NOT reject a syntactically valid payloadType solely
because it is absent from the seed registry.

The seed registry MAY expose known constants for canonical test fixtures and MAY
reject Java class-name payloadType strings in registry-specific tests.
Semantic acceptance of a payloadType belongs to higher protocol/runtime layers,
not to the generic marshaller/unmarshaller.
```

Rationale:

```text
Rejecting unknown payloadType values at the serialization layer would make MU-028
dependent on all future SC-D payload schemas and would weaken protocol evolution.
Opaque passthrough keeps serialization language-neutral and forward-compatible
while preserving wire-level grammar validation.
```

---

## 19. Summary verdict

```text
MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001 v0.2.0-candidate
Status: Candidate / ready for execution-package descent
Recommended next step: produce execution package for MU-028.
```
