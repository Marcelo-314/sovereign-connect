# Context — PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001

```text
Document ID:  context-PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001
Version:      v0.1.1
Status:       Execution context / patch
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
Scope:        MU-028 post-validation micro-patch
Branch:       feat/sc-b-mir-028-serialization-utilities-seed
```

## 1. Baseline

MU-028 has been validated technically as L4 with:

```text
sovereign-connect: 410 tests, 0 failures, 0 errors, 0 skipped
EIB: 56 tests, 0 failures, 0 errors, 0 skipped
```

Implemented MU-028 surface includes:

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

The implementation correctly preserves the MU-028 scope:

```text
No io.nats:jnats
No Testcontainers
No NatsScBusPort
No NatsSubjectBuilder
No JetStream config
No lifecycle runtime
No productive ScdCommand
No SC-D fact-family production classes
```

## 2. Patch rationale

The validation report identified one non-blocking but cheap-to-fix evidence gap:

```text
The implementation report says WireEnvelopeValidatorTest preserves syntactically valid unknown payloadTypes.
The product behavior is correct because validation uses grammar, not a closed registry.
ScJsonWireCodecTest already covers opaque roundtrip.
However, WireEnvelopeValidatorTest itself does not include an explicit accepts-unknown-valid-payloadType test.
```

This patch closes that gap before accepting MU-028.

## 3. Required change

Add one explicit test to:

```text
src/test/java/com/sovereign/connect/bus/runtime/serialization/WireEnvelopeValidatorTest.java
```

Required test method name:

```text
acceptsUnknownButSyntacticallyValidPayloadType
```

Required behavior:

```text
Given a valid ScJsonWireEnvelope with payloadType = "sc.command.future.v1"
When WireEnvelopeValidator validates it
Then validation succeeds
And no registry membership check is required
```

This test must prove the distinction:

```text
syntactic payloadType validation != registry membership
```

## 4. Forbidden changes

Do not change production code unless the current production behavior rejects unknown-but-valid payloadTypes.

Do not add:

```text
io.nats:jnats
Testcontainers
NatsScBusPort
NatsSubjectBuilder
JetStream config
lifecycle runtime
productive ScdCommand
SC-D fact-family production classes
```

Do not convert `ScPayloadTypeRegistry` into a closed allowlist.

Do not make `WireEnvelopeValidator` reject payloadTypes solely because they are absent from the seed registry.

## 5. Expected verification

```text
STOP-1: mvn -q compile
STOP-2: mvn -q test -Dtest="WireEnvelopeValidatorTest"
STOP-3: mvn -q test -Dtest="ScJsonWireCodecTest,WireEnvelopeValidatorTest,ScBusSerializationArchitectureTest"
STOP-4: mvn -q test
```

Expected final full regression:

```text
>= 411 tests, 0 failures, 0 errors, 0 skipped
```

## 6. Implementation report update

Update:

```text
docs/mir/mir-028/implementation-report.md
```

The report must record:

```text
- micro-patch added WireEnvelopeValidatorTest.acceptsUnknownButSyntacticallyValidPayloadType;
- full test count after patch;
- no production scope expansion;
- no NATS dependency introduced;
- opaque valid payloadType behavior is directly tested both at codec and validator levels.
```


## v0.1.1 clarification — exact validator test

The patch test MUST use a payload type that is syntactically valid but absent from the seed registry. Use this exact payload value:

```text
sc.command.future.v1
```

Canonical test shape:

```java
@Test
void acceptsUnknownButSyntacticallyValidPayloadType() {
    // "sc.command.future.v1" is not in ScPayloadTypeRegistry seed constants
    // but satisfies the grammar sc.<domain>.<type>.v<major>.
    var envelope = buildValidCommandEnvelope("sc.command.future.v1");

    assertThatNoException()
        .isThrownBy(() -> WireEnvelopeValidator.validate(envelope));
}
```

If the existing helper has a different name, adapt only the helper call. Do not replace `sc.command.future.v1` with a registry constant.
