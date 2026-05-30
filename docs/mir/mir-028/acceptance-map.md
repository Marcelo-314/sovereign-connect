# Acceptance Map — MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001

```text
Document ID:  acceptance-map-MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
Version:      v0.2.0-candidate
Status:       Execution Package Acceptance Map
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
MU Slot:      MU-028
```

---

## Scope

This acceptance map links `AC-028-*` criteria from `MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001 v0.2.0-candidate` to expected source files, tests and evidence.

---

## AC coverage

| AC | Criterion | Expected evidence |
|---|---|---|
| AC-028-001 | No NATS / JetStream / Testcontainers dependency | `pom.xml`; `ScBusSerializationArchitectureTest.serializationPackageDoesNotImportNatsOrTestcontainers`; existing broker-dependency tests |
| AC-028-002 | No `NatsScBusPort` or NATS package | source tree; implementation report changed files |
| AC-028-003 | Serialization package does not import `core.*` | `ScBusSerializationArchitectureTest.serializationPackageDoesNotImportCore` |
| AC-028-004 | Serialization package does not import `adapter.*` | `ScBusSerializationArchitectureTest.serializationPackageDoesNotImportAdapter` |
| AC-028-005 | Serialization package does not import `io.nats.*` | `ScBusSerializationArchitectureTest.serializationPackageDoesNotImportNatsOrTestcontainers` |
| AC-028-006 | `scid1_` prefix | `ScSubjectIdTokenCodecTest.scid1EncodingPrefixesToken` |
| AC-028-007 | base64url no padding | `ScSubjectIdTokenCodecTest.scid1TokenHasNoProblematicSubjectChars` plus source inspection |
| AC-028-008 | no `.`, `+`, `/`, `=` | `ScSubjectIdTokenCodecTest.scid1TokenHasNoProblematicSubjectChars` |
| AC-028-009 | decode returns original canonical ID | `ScSubjectIdTokenCodecTest.scid1RoundtripPreservesOriginalCanonicalId` |
| AC-028-010 | encode/decode idempotence | `ScSubjectIdTokenCodecTest.scid1EncodeDecodeEncodeIsIdempotent` |
| AC-028-011 | malformed `scid1_` rejected, including empty payload `scid1_` | `ScSubjectIdTokenCodecTest.malformedScid1TokensAreRejected` |
| AC-028-012 | correlation UUID token is 32 lowercase hex, no hyphens | `ScCorrelationTokenCodecTest.correlationIdEncodingRemovesHyphens`; `correlationIdEncodingIsLowercaseHex` |
| AC-028-013 | correlation token decodes to original UUID | `ScCorrelationTokenCodecTest.correlationIdTokenRoundtripsToOriginalUuid` |
| AC-028-014 | invalid correlation length rejected | `ScCorrelationTokenCodecTest.invalidCorrelationTokenLengthIsRejected` |
| AC-028-015 | non-hex chars rejected | `ScCorrelationTokenCodecTest.nonHexCorrelationTokenCharsAreRejected` |
| AC-028-016 | `scid1_` not used for correlation tokens | `ScCorrelationTokenCodecTest.correlationIdEncodingRemovesHyphens`; source inspection |
| AC-028-017 | token validator rejects `.` | `ScSubjectTokenValidatorTest.rejectsProblematicNatsSubjectCharacters` |
| AC-028-018 | token validator rejects `+` | `ScSubjectTokenValidatorTest.rejectsProblematicNatsSubjectCharacters` |
| AC-028-019 | token validator rejects `/` | `ScSubjectTokenValidatorTest.rejectsProblematicNatsSubjectCharacters` |
| AC-028-020 | token validator rejects `=` | `ScSubjectTokenValidatorTest.rejectsProblematicNatsSubjectCharacters` |
| AC-028-021 | validator accepts valid `scid1_` and UUID tokens | `ScSubjectTokenValidatorTest.acceptsScid1AndCorrelationTokens` |
| AC-028-022 | serialized envelope contains `envelopeKind` | `ScJsonWireCodecTest.serializedEnvelopeContainsKindPayloadTypeAndSchemaVersion` |
| AC-028-023 | serialized envelope contains `payloadType` | `ScJsonWireCodecTest.serializedEnvelopeContainsKindPayloadTypeAndSchemaVersion` |
| AC-028-024 | serialized envelope contains `payloadSchemaVersion` | `ScJsonWireCodecTest.serializedEnvelopeContainsKindPayloadTypeAndSchemaVersion` |
| AC-028-025 | serialized envelope contains `metadata` | command/event/response roundtrip tests |
| AC-028-026 | serialized envelope contains `routingKey` | command/event/response roundtrip tests |
| AC-028-027 | response envelope contains `responseMetadata` | `ScJsonWireCodecTest.responseEnvelopeRoundtripsThroughScJsonWireV1` |
| AC-028-028 | Java class names are not payloadType values | `ScPayloadTypeRegistryTest.rejectsJavaClassNamePayloadType`; `ScBusSerializationArchitectureTest.payloadTypeConstantsDoNotUseJavaClassNames` |
| AC-028-029 | command roundtrip | `ScJsonWireCodecTest.commandEnvelopeRoundtripsThroughScJsonWireV1` |
| AC-028-030 | event roundtrip | `ScJsonWireCodecTest.eventEnvelopeRoundtripsThroughScJsonWireV1` |
| AC-028-031 | response roundtrip | `ScJsonWireCodecTest.responseEnvelopeRoundtripsThroughScJsonWireV1` |
| AC-028-032 | opaque JSON payload preserved | `ScJsonWireCodecTest.opaqueJsonPayloadIsPreservedThroughRoundtrip` |
| AC-028-033 | unknown valid payloadType preserved opaquely | `ScPayloadTypeRegistryTest.acceptsUnknownButSyntacticallyValidPayloadType`; `ScJsonWireCodecTest.unknownPayloadTypeIsPreservedOpaquely` |
| AC-028-034 | missing payloadType rejected | `WireEnvelopeValidatorTest.rejectsMissingPayloadType` |
| AC-028-035 | missing payloadSchemaVersion rejected | `WireEnvelopeValidatorTest.rejectsMissingPayloadSchemaVersion` |
| AC-028-036 | kind/lane mismatch rejected | `WireEnvelopeValidatorTest.rejectsEnvelopeKindLaneMismatch` |
| AC-028-037 | valid COMMAND accepted | `WireEnvelopeValidatorTest.acceptsValidCommandEventAndResponseEnvelopes` |
| AC-028-038 | valid EVENT accepted | `WireEnvelopeValidatorTest.acceptsValidCommandEventAndResponseEnvelopes` |
| AC-028-039 | valid RESPONSE accepted | `WireEnvelopeValidatorTest.acceptsValidCommandEventAndResponseEnvelopes` |
| AC-028-040 | implementation report records branch, commits, files | `docs/mir/mir-028/implementation-report.md` |
| AC-028-041 | implementation report records test counts | `docs/mir/mir-028/implementation-report.md` |
| AC-028-042 | implementation report confirms no NATS dependency | `docs/mir/mir-028/implementation-report.md`; architecture tests |
| AC-028-043 | implementation report records retained MIR-B debt | `docs/mir/mir-028/implementation-report.md` |
| AC-028-044 | acceptance map links each AC | this document; final implementation report |

---

## Execution-package hardening notes

```text
The v0.2.0-candidate execution package additionally requires:
- ScSubjectTokenValidator rejects NATS wildcard tokens `*` and `>` in concrete subject tokens.
- ScPayloadTypeRegistry rejects malformed payloadType shapes, not merely Java class names.
These are package-level hardening requirements derived from review and do not add new AC numbers.
```

## Expected validation summary

```text
Baseline before MU-028: 378 tests.
Expected MU-028 delta:  +32 tests.
Expected total:         >=410 tests, 0 failures, 0 errors, 0 skipped.
```

---

## Non-coverage by design

The following are intentionally not covered by MU-028 tests:

```text
NATS live publish / subscribe
JetStream stream configuration
NatsScBusPort
lifecycle route assignment runtime
productive ScdCommand payload shape
SC-D fact family payload schemas
adapter execution
semantic retry
terminal request-state authority
```
