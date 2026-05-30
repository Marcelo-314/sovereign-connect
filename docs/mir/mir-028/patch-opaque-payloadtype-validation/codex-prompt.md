# Codex Prompt — PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001

```text
Document ID:  codex-prompt-PATCH-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-OPAQUE-PAYLOADTYPE-001
Version:      v0.1.1
Status:       Codex prompt / patch
Date:         2026-05-30
Corpus:       Sovereign Connect
Plane:        SC-B
Scope:        MU-028 micro-patch before final acceptance
Branch:       feat/sc-b-mir-028-serialization-utilities-seed
```

## Task

Apply a minimal MU-028 evidence-alignment patch.

The implementation is already technically valid. Do not redesign serialization utilities. Add one explicit validator-level test proving that unknown but syntactically valid `payloadType` values are accepted by `WireEnvelopeValidator`.

## Required change

Modify:

```text
src/test/java/com/sovereign/connect/bus/runtime/serialization/WireEnvelopeValidatorTest.java
```

Add this exact test method, adapting only the helper name if the existing file uses a different valid-command-envelope builder:

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

Use the existing construction/helpers in `WireEnvelopeValidatorTest`. Do not introduce a new test style if the file already has helper methods for valid command/event/response wire envelopes. If the current helper is named differently, keep the exact payload type `"sc.command.future.v1"` and the exact assertion semantics: validation must not throw.

This payload type is intentionally outside the seed constants. Do not replace it with `ScPayloadTypeRegistry.COMMAND_STUB_V1` or any other registry constant, because that would not test opaque passthrough.


Required AssertJ import if not already present:

```java
import static org.assertj.core.api.Assertions.assertThatNoException;
```

The test must validate the normative rule:

```text
Unknown but syntactically valid payloadType values are preserved opaquely.
Wire-level validation checks grammar and envelope consistency.
Registry-level semantic acceptance belongs to higher layers.
```

## Production code rule

Do not change production code unless this new test fails because `WireEnvelopeValidator` incorrectly rejects unknown but syntactically valid payloadTypes.

If production code must be changed, the only allowed change is to make `WireEnvelopeValidator` call syntax validation instead of closed registry membership.

Do not convert `ScPayloadTypeRegistry` into a closed allowlist.

## Forbidden changes

Do not add or modify anything related to:

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

Do not add NATS dependencies.
Do not add broker tests.
Do not change `ScBusPort`.
Do not change `RuntimeDispatchService`.
Do not change dispatch state, outbox bridge, or observation persistence code.

## Documentation update

Update:

```text
docs/mir/mir-028/implementation-report.md
```

Record:

```text
- Added WireEnvelopeValidatorTest.acceptsUnknownButSyntacticallyValidPayloadType.
- Final test counts.
- No scope expansion.
- No NATS dependency.
```

## Verification

Run:

```bash
mvn -q compile
mvn -q test -Dtest="WireEnvelopeValidatorTest"
mvn -q test -Dtest="ScJsonWireCodecTest,WireEnvelopeValidatorTest,ScBusSerializationArchitectureTest"
mvn -q test
```

Expected:

```text
full regression >= 411 tests, 0 failures, 0 errors, 0 skipped
```

## Suggested commit

```text
fix(sc-b): cover opaque payload type validation
```
