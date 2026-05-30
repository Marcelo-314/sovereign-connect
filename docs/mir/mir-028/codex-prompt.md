# Codex Prompt — MU-028 SC-B Serialization Utilities Seed

```text
Version: v0.2.0-candidate
```

Implement `MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001 v0.2.0-candidate`.

Read first:
```text
docs/mir/mir-028/context.md           ← all paste-ready code here
docs/mir/mir-028/acceptance-map.md
docs/mir/mir-028/MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001.md
```

This is NOT the NATS binding MIR. No broker dependency is introduced.

---

## Hard prohibitions

```text
NO io.nats:jnats, Testcontainers, NatsScBusPort, NatsSubjectBuilder
NO lifecycle runtime, productive ScdCommand, SC-D fact-family classes
NO imports of core.**, adapter.**, io.nats.** from bus.runtime.serialization
NO INTERNAL_CONTROL as public wire envelope kind
NO Java class names as payloadType values
```


## Mandatory corrections already incorporated in context.md v0.2.0-candidate

```text
- ScPayloadTypeRegistry MUST validate payloadType with strict grammar:
  ^sc\.[a-z0-9-]+\.[a-z0-9-]+\.v[1-9][0-9]*$
- Unknown payloadTypes are preserved only when syntactically valid.
- decodeScid1("scid1_") MUST be rejected.
- ScSubjectTokenValidator MUST reject NATS wildcards `*` and `>` in concrete subject tokens.
```

---

## Step 1 — Create 8 production classes

All in `src/main/java/com/sovereign/connect/bus/runtime/serialization/`.

Use the **exact paste-ready code from context.md §4**.

Key decisions already made for you:
```text
ObjectMapper:  new ObjectMapper().findAndRegisterModules()
               .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
               — matches existing project pattern (EibConfiguration.java)

JsonNode:      use mapper.valueToTree(payload) to convert objects to JsonNode
               — this preserves opaque payloads correctly
               — do NOT use writeValueAsString() and wrap as string node

scid1_:        "scid1_" + Base64.getUrlEncoder().withoutPadding()
               .encodeToString(canonicalId.getBytes(StandardCharsets.UTF_8))

correlation:   uuid.toString().replace("-", "")   → 32 lowercase hex, no hyphens
               NOT scid1_

ScWireEnvelopeKind: enum { COMMAND, EVENT, RESPONSE }
               — INTERNAL_CONTROL is NOT included in MU-028
```

**STOP-1:** `mvn -q compile` → BUILD SUCCESS.

---

## Step 2 — Create 7 test classes

All in `src/test/java/com/sovereign/connect/bus/runtime/serialization/` except
`ScBusSerializationArchitectureTest` which goes in
`src/test/java/com/sovereign/connect/bus/`.

Use the **exact test helpers and patterns from context.md §5**.

Critical: use `StubPayload` record in `src/test/java` only, never in production.

Use the **exact method names** listed below — acceptance-map references them:

```text
ScSubjectIdTokenCodecTest (5):
  scid1EncodingPrefixesToken
  scid1TokenHasNoProblematicSubjectChars
  scid1RoundtripPreservesOriginalCanonicalId
  scid1EncodeDecodeEncodeIsIdempotent
  malformedScid1TokensAreRejected  ← include `scid1_`

ScCorrelationTokenCodecTest (5):
  correlationIdEncodingRemovesHyphens
  correlationIdEncodingIsLowercaseHex
  correlationIdTokenRoundtripsToOriginalUuid
  invalidCorrelationTokenLengthIsRejected
  nonHexCorrelationTokenCharsAreRejected

ScSubjectTokenValidatorTest (3):
  rejectsProblematicNatsSubjectCharacters  ← include `.`, `+`, `/`, `=`, `*`, `>`
  rejectsBlankSubjectTokens
  acceptsScid1AndCorrelationTokens

ScPayloadTypeRegistryTest (4):
  acceptsSeedPayloadTypeConstants
  acceptsUnknownButSyntacticallyValidPayloadType
  rejectsJavaClassNamePayloadType
  rejectsMalformedPayloadType  ← include malformed shape cases from context.md §4.9

ScJsonWireCodecTest (6):
  commandEnvelopeRoundtripsThroughScJsonWireV1
  eventEnvelopeRoundtripsThroughScJsonWireV1
  responseEnvelopeRoundtripsThroughScJsonWireV1
  serializedEnvelopeContainsKindPayloadTypeAndSchemaVersion
  unknownPayloadTypeIsPreservedOpaquely
  opaqueJsonPayloadIsPreservedThroughRoundtrip

WireEnvelopeValidatorTest (5):
  rejectsMissingPayloadType
  rejectsMissingPayloadSchemaVersion
  rejectsEnvelopeKindLaneMismatch
  acceptsValidCommandEventAndResponseEnvelopes
  rejectsJavaClassNamePayloadType

ScBusSerializationArchitectureTest (4):
  serializationPackageDoesNotImportCore
  serializationPackageDoesNotImportAdapter
  serializationPackageDoesNotImportNatsOrTestcontainers
  payloadTypeConstantsDoNotUseJavaClassNames
```

Use the **exact architecture test from context.md §6** — file-walk + Files.readString
+ AssertJ, same style as all other architecture tests in the project.

**STOP-2:** codec/registry subset → 17 tests, 0 failures.
**STOP-3:** wire/architecture subset → 15 tests, 0 failures.

---

## Step 3 — Full regression

```bash
mvn -q test
```

Expected: **≥ 410 tests, 0 failures, 0 errors, 0 skipped.**
SC-C 240 baseline still green.
Existing bus architecture tests unchanged and still passing.

---

## Step 4 — Update implementation report

`docs/mir/mir-028/implementation-report.md` must confirm:
```text
branch, commit hash, changed files
test count (≥410 total, +32 delta)
no NATS dependency introduced (grep pom.xml)
scid1_ codec is reversible
correlation token codec uses 32 lowercase hex without hyphens
SC-JSON-WIRE-v1 roundtrips COMMAND/EVENT/RESPONSE
unknown but valid payloadType preserved opaquely
retained debts: NATS binding (MU-029), ScdCommand, SC-D fact family
```

Commit:
```bash
git commit -m "feat(sc-b): add serialization utilities seed"
```

---

## Hard stops

```text
- Any production import of core.**, adapter.**, io.nats.** → STOP
- INTERNAL_CONTROL added to ScWireEnvelopeKind → STOP
- Java class name appears as payloadType constant → STOP
- mapper.writeValueAsString(payload) used instead of mapper.valueToTree(payload) → REWRITE
- io.nats or Testcontainers in pom.xml → STOP
- Any existing SC-C, bus or EIB test fails → STOP
```
