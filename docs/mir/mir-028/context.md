# Context — MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001

```text
Version:  v0.2.0-candidate
MIR:      MIR-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001 v0.2.0-candidate
MU:       MU-SOV-SC-B-SERIALIZATION-UTILITIES-SEED-001
Slot:     MU-028
Branch:   feat/sc-b-mir-028-serialization-utilities-seed
Baseline: post-MU-027 · sovereign-connect 378/0/0 · EIB 56/0/0
```

---

## 0. Purpose

Create deterministic, broker-free SC-B serialization and subject-token utilities.
This is not the NATS binding MIR. No broker dependency is introduced.

---

## 1. Confirmed baseline facts

```text
Jackson:           findAndRegisterModules() pattern confirmed in EibConfiguration.java
                   jackson-datatype-jsr310 is in pom via spring-boot-starter
ObjectMapper ref:  new ObjectMapper().findAndRegisterModules()
                              .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
JsonNode:          available via jackson-databind (included transitively via spring-boot-starter-web)

SC-B contract shapes (all present, all unchanged):
  ScCommandEnvelope<T>  record(metadata, payload, routingKey)
  ScEventEnvelope<T>    record(metadata, payload, routingKey)
  ScResponseEnvelope<T> record(metadata, payload, routingKey, responseMetadata)
  ScMessageMetadata     record(messageId:UUID, emittedAt:Instant,
                               correlationId:UUID, causationId:UUID,
                               topologyVersion:String)
  ScRoutingKey          record(lane:ScBusLane, partitionKey:String, topic:String,
                               habitatId:String, adapterId:String,
                               deviceId:String, endpointId:String)
  ScBusLane             COMMAND, EVENT, RESPONSE, INTERNAL_CONTROL
  ScResponseMetadata    record(requestMessageId:UUID, requestId:UUID,
                               responseKind:ScResponseKind, terminal:boolean,
                               retryable:boolean, sanitizedReason:String,
                               warnings:List<ScResponseWarning>)

All gaps confirmed absent (scid1_, correlation codec, wire envelope, payloadType, NATS).
```

---

## 2. Hard boundary

```text
DO NOT add:  io.nats:jnats, Testcontainers, NatsScBusPort, NatsSubjectBuilder,
             lifecycle runtime, productive ScdCommand, SC-D fact-family shapes,
             broker integration tests.

DO NOT import from bus.runtime.serialization:
  com.sovereign.connect.core.*
  com.sovereign.connect.adapter.*
  io.nats.*
  org.testcontainers.*

DO NOT change: RuntimeDispatchService, DispatchStateWritePort,
               DispatchObservationPort, SC-C outbox/ledger semantics.

Existing architecture tests that reject broker dependencies MUST remain green.
```

---

## 3. Production package

```text
src/main/java/com/sovereign/connect/bus/runtime/serialization/
  ScSubjectIdTokenCodec.java
  ScCorrelationTokenCodec.java
  ScSubjectTokenValidator.java
  ScWireEnvelopeKind.java
  ScJsonWireEnvelope.java
  ScPayloadTypeRegistry.java
  ScJsonWireCodec.java
  WireEnvelopeValidator.java
```

---

## 4. Exact class implementations — paste-ready

### 4.1 ScSubjectIdTokenCodec

```java
package com.sovereign.connect.bus.runtime.serialization;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class ScSubjectIdTokenCodec {

    private static final String PREFIX = "scid1_";
    private static final Base64.Encoder ENCODER =
        Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER =
        Base64.getUrlDecoder();

    private ScSubjectIdTokenCodec() {}

    public static String encodeScid1(String canonicalId) {
        Objects.requireNonNull(canonicalId, "canonicalId is required");
        if (canonicalId.isBlank()) {
            throw new IllegalArgumentException("canonicalId must not be blank");
        }
        return PREFIX + ENCODER.encodeToString(
            canonicalId.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeScid1(String token) {
        Objects.requireNonNull(token, "token is required");
        if (!token.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                "token is not a valid scid1_ token: " + token);
        }

        String encoded = token.substring(PREFIX.length());
        if (encoded.isBlank()) {
            throw new IllegalArgumentException("scid1_ token payload must not be blank");
        }

        try {
            String decoded = new String(
                DECODER.decode(encoded),
                StandardCharsets.UTF_8);
            if (decoded.isBlank()) {
                throw new IllegalArgumentException("decoded canonicalId must not be blank");
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "malformed scid1_ token: " + token, e);
        }
    }
}
```

### 4.2 ScCorrelationTokenCodec

```java
package com.sovereign.connect.bus.runtime.serialization;

import java.util.Objects;
import java.util.UUID;

public final class ScCorrelationTokenCodec {

    private ScCorrelationTokenCodec() {}

    /** UUID → 32 lowercase hex chars, no hyphens. scid1_ is NOT used. */
    public static String encode(UUID id) {
        Objects.requireNonNull(id, "id is required");
        return id.toString().replace("-", "");
    }

    /** 32 lowercase hex chars → UUID. */
    public static UUID decode(String token) {
        Objects.requireNonNull(token, "token is required");
        if (token.length() != 32) {
            throw new IllegalArgumentException(
                "correlation token must be 32 chars, got: " + token.length());
        }
        if (!token.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException(
                "correlation token must be lowercase hex: " + token);
        }
        // Re-insert hyphens: 8-4-4-4-12
        String canonical = token.substring(0, 8) + "-"
            + token.substring(8, 12) + "-"
            + token.substring(12, 16) + "-"
            + token.substring(16, 20) + "-"
            + token.substring(20);
        return UUID.fromString(canonical);
    }
}
```

### 4.3 ScSubjectTokenValidator

```java
package com.sovereign.connect.bus.runtime.serialization;

import java.util.Objects;

public final class ScSubjectTokenValidator {

    private static final char[] PROHIBITED = new char[]{'.', '+', '/', '=', '*', '>'};

    private ScSubjectTokenValidator() {}

    public static void requireSafeToken(String token) {
        Objects.requireNonNull(token, "token is required");
        if (token.isBlank()) {
            throw new IllegalArgumentException("subject token must not be blank");
        }
        for (char c : PROHIBITED) {
            if (token.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                    "subject token contains prohibited char '" + c + "': " + token);
            }
        }
    }

    public static boolean isSafeToken(String token) {
        if (token == null || token.isBlank()) return false;
        for (char c : PROHIBITED) {
            if (token.indexOf(c) >= 0) return false;
        }
        return true;
    }
}
```

### 4.4 ScWireEnvelopeKind

```java
package com.sovereign.connect.bus.runtime.serialization;

public enum ScWireEnvelopeKind {
    COMMAND,
    EVENT,
    RESPONSE
    // INTERNAL_CONTROL is a routing lane, not a public adapter wire kind for MU-028
}
```

### 4.5 ScJsonWireEnvelope

```java
package com.sovereign.connect.bus.runtime.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;

public record ScJsonWireEnvelope(
    ScWireEnvelopeKind envelopeKind,
    String payloadType,
    String payloadSchemaVersion,
    ScMessageMetadata metadata,
    ScRoutingKey routingKey,
    ScResponseMetadata responseMetadata,   // null for COMMAND/EVENT
    JsonNode payload
) {}
```

### 4.6 ScPayloadTypeRegistry

```java
package com.sovereign.connect.bus.runtime.serialization;

import java.util.regex.Pattern;

public final class ScPayloadTypeRegistry {

    // Seed constants
    public static final String COMMAND_STUB_V1               = "sc.command.stub.v1";
    public static final String EVENT_TIMER_FIRED_V1          = "sc.event.timer-fired.v1";
    public static final String RESPONSE_STUB_V1              = "sc.response.stub.v1";
    public static final String LIFECYCLE_ADAPTER_ANNOUNCE_V1 = "sc.lifecycle.adapter-announce.v1";

    private static final Pattern PAYLOAD_TYPE_PATTERN =
        Pattern.compile("^sc\\.[a-z0-9-]+\\.[a-z0-9-]+\\.v[1-9][0-9]*$");

    private ScPayloadTypeRegistry() {}

    /**
     * Accepts: sc.<domain>.<type-name>.v<major>
     * Rejects: Java class names and malformed identifiers.
     * Unknown but syntactically valid types are accepted — wire layer is not type authority.
     */
    public static boolean hasValidPayloadTypeSyntax(String payloadType) {
        if (payloadType == null || payloadType.isBlank()) {
            return false;
        }
        if (looksLikeJavaClassName(payloadType)) {
            return false;
        }
        return PAYLOAD_TYPE_PATTERN.matcher(payloadType).matches();
    }

    public static void requireValidPayloadTypeSyntax(String payloadType) {
        if (!hasValidPayloadTypeSyntax(payloadType)) {
            throw new IllegalArgumentException(
                "invalid payloadType: '" + payloadType + "'");
        }
    }

    public static boolean looksLikeJavaClassName(String payloadType) {
        if (payloadType == null) {
            return false;
        }
        return payloadType.startsWith("com.")
            || payloadType.startsWith("org.")
            || payloadType.startsWith("net.")
            || payloadType.startsWith("io.")
            || payloadType.startsWith("java.");
    }
}
```

### 4.7 ScJsonWireCodec

```java
package com.sovereign.connect.bus.runtime.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;

import java.io.IOException;
import java.util.Objects;

public final class ScJsonWireCodec {

    private final ObjectMapper mapper;

    public ScJsonWireCodec() {
        // Match the project pattern: findAndRegisterModules() + disable timestamp
        this.mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // For tests that provide a pre-configured ObjectMapper
    public ScJsonWireCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper is required");
    }

    /** Serialize ScJsonWireEnvelope → JSON bytes. */
    public byte[] marshal(ScJsonWireEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        try {
            return mapper.writeValueAsBytes(envelope);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to marshal wire envelope", e);
        }
    }

    /** Deserialize JSON bytes → ScJsonWireEnvelope. */
    public ScJsonWireEnvelope unmarshal(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes is required");
        try {
            return mapper.readValue(bytes, ScJsonWireEnvelope.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to unmarshal wire envelope", e);
        }
    }

    /** Build wire envelope from ScCommandEnvelope. payload serialized as JsonNode. */
    public <T> ScJsonWireEnvelope toWireEnvelope(
            ScCommandEnvelope<T> envelope,
            String payloadType,
            String payloadSchemaVersion) {
        Objects.requireNonNull(envelope, "envelope is required");
        JsonNode payloadNode = mapper.valueToTree(envelope.payload());
        return new ScJsonWireEnvelope(
            ScWireEnvelopeKind.COMMAND,
            payloadType,
            payloadSchemaVersion,
            envelope.metadata(),
            envelope.routingKey(),
            null,
            payloadNode
        );
    }

    /** Build wire envelope from ScEventEnvelope. */
    public <T> ScJsonWireEnvelope toWireEnvelope(
            ScEventEnvelope<T> envelope,
            String payloadType,
            String payloadSchemaVersion) {
        Objects.requireNonNull(envelope, "envelope is required");
        JsonNode payloadNode = mapper.valueToTree(envelope.payload());
        return new ScJsonWireEnvelope(
            ScWireEnvelopeKind.EVENT,
            payloadType,
            payloadSchemaVersion,
            envelope.metadata(),
            envelope.routingKey(),
            null,
            payloadNode
        );
    }

    /** Build wire envelope from ScResponseEnvelope. */
    public <T> ScJsonWireEnvelope toWireEnvelope(
            ScResponseEnvelope<T> envelope,
            String payloadType,
            String payloadSchemaVersion) {
        Objects.requireNonNull(envelope, "envelope is required");
        JsonNode payloadNode = mapper.valueToTree(envelope.payload());
        return new ScJsonWireEnvelope(
            ScWireEnvelopeKind.RESPONSE,
            payloadType,
            payloadSchemaVersion,
            envelope.metadata(),
            envelope.routingKey(),
            envelope.responseMetadata(),
            payloadNode
        );
    }
}
```

**Key points for Codex:**
- `mapper.valueToTree(payload)` converts any object to `JsonNode` — this is what
  preserves opaque payloads correctly. Do NOT use `mapper.writeValueAsString()` and
  then wrap in a string node.
- `findAndRegisterModules()` picks up `JavaTimeModule` automatically — same as EIB.
- `WRITE_DATES_AS_TIMESTAMPS` disabled so `Instant` serializes as ISO-8601 string.

### 4.8 WireEnvelopeValidator

```java
package com.sovereign.connect.bus.runtime.serialization;

import com.sovereign.connect.bus.contract.ScBusLane;

import java.util.Objects;

public final class WireEnvelopeValidator {

    private WireEnvelopeValidator() {}

    public static void validate(ScJsonWireEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        requirePresent(envelope.envelopeKind(), "envelopeKind");
        requirePresent(envelope.payloadType(), "payloadType");
        requirePresent(envelope.payloadSchemaVersion(), "payloadSchemaVersion");
        requirePresent(envelope.metadata(), "metadata");
        requirePresent(envelope.routingKey(), "routingKey");

        ScPayloadTypeRegistry.requireValidPayloadTypeSyntax(envelope.payloadType());

        requireLaneConsistency(envelope);

        if (envelope.envelopeKind() == ScWireEnvelopeKind.RESPONSE
                && envelope.responseMetadata() == null) {
            throw new IllegalArgumentException(
                "responseMetadata is required for RESPONSE envelopes");
        }
    }

    private static void requireLaneConsistency(ScJsonWireEnvelope envelope) {
        ScBusLane lane = envelope.routingKey().lane();
        ScWireEnvelopeKind kind = envelope.envelopeKind();
        boolean consistent = switch (kind) {
            case COMMAND  -> lane == ScBusLane.COMMAND;
            case EVENT    -> lane == ScBusLane.EVENT;
            case RESPONSE -> lane == ScBusLane.RESPONSE;
        };
        if (!consistent) {
            throw new IllegalArgumentException(
                "envelopeKind " + kind + " is inconsistent with routingKey.lane " + lane);
        }
    }

    private static void requirePresent(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value instanceof String s && s.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
```


### 4.9 Mandatory validator/test refinements included in v0.2.0-candidate

```text
ScSubjectIdTokenCodecTest.malformedScid1TokensAreRejected MUST include `scid1_`.
ScSubjectTokenValidatorTest.rejectsProblematicNatsSubjectCharacters MUST include `*` and `>`.
ScPayloadTypeRegistryTest.rejectsMalformedPayloadType MUST include:
  - sc.command.future.version
  - sc.command..v1
  - sc.command.future.v
  - sc.command.future.v1.extra
  - sc.Command.Future.v1
```

These refinements do not change the expected test count; they strengthen existing tests.

---

## 5. Test helpers — paste-ready

All tests are in `src/test/java/com/sovereign/connect/bus/runtime/serialization/`.
Use JUnit 5 + AssertJ. No datasource, no Flyway, no Spring context needed.

### 5.1 Common test fixtures

```java
// Place as static helpers in a test base class or per-test class:

private static ScMessageMetadata testMetadata() {
    return new ScMessageMetadata(
        UUID.randomUUID(),
        Instant.now(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        null
    );
}

private static ScRoutingKey testCommandRouting() {
    return new ScRoutingKey(
        ScBusLane.COMMAND, "habitat-1", "sc.command.stub",
        "habitat-1", "adapter.z2m", null, null);
}

private static ScRoutingKey testEventRouting() {
    return new ScRoutingKey(
        ScBusLane.EVENT, "habitat-1", "sc.event.timer-fired",
        "habitat-1", null, null, null);
}

private static ScRoutingKey testResponseRouting() {
    return new ScRoutingKey(
        ScBusLane.RESPONSE, "habitat-1", "sc.response.stub",
        "habitat-1", "adapter.z2m", null, null);
}

private static ScResponseMetadata testResponseMetadata() {
    return new ScResponseMetadata(
        UUID.randomUUID(), UUID.randomUUID(),
        ScResponseKind.EXECUTION_RESULT,
        true, false, null, List.of());
}

// Stub payload record — in src/test/java only, NOT in production
record StubPayload(String operation, String target) {}
```

### 5.2 ScJsonWireCodecTest — critical patterns

```java
@Test
void commandEnvelopeRoundtripsThroughScJsonWireV1() {
    var codec = new ScJsonWireCodec();
    var envelope = new ScCommandEnvelope<>(
        testMetadata(), new StubPayload("SET", "device.test"), testCommandRouting());

    var wire = codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1");
    var bytes = codec.marshal(wire);
    var roundtrip = codec.unmarshal(bytes);

    assertThat(roundtrip.envelopeKind()).isEqualTo(ScWireEnvelopeKind.COMMAND);
    assertThat(roundtrip.payloadType()).isEqualTo(ScPayloadTypeRegistry.COMMAND_STUB_V1);
    assertThat(roundtrip.payloadSchemaVersion()).isEqualTo("1");
    assertThat(roundtrip.metadata().messageId())
        .isEqualTo(envelope.metadata().messageId());
    assertThat(roundtrip.payload()).isNotNull();
    // Payload is preserved as JsonNode, not null
}

@Test
void unknownPayloadTypeIsPreservedOpaquely() {
    var codec = new ScJsonWireCodec();
    var envelope = new ScCommandEnvelope<>(
        testMetadata(), Map.of("x", 1), testCommandRouting());

    var wire = codec.toWireEnvelope(envelope, "sc.command.future.v2", "1");
    var bytes = codec.marshal(wire);
    var roundtrip = codec.unmarshal(bytes);

    // Unknown but syntactically valid payloadType is preserved
    assertThat(roundtrip.payloadType()).isEqualTo("sc.command.future.v2");
    assertThat(roundtrip.payload()).isNotNull();
}

@Test
void opaqueJsonPayloadIsPreservedThroughRoundtrip() {
    var codec = new ScJsonWireCodec();
    var payload = Map.of("state", "ON", "brightness", 254);
    var envelope = new ScCommandEnvelope<>(
        testMetadata(), payload, testCommandRouting());

    var wire = codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1");
    var bytes = codec.marshal(wire);
    var roundtrip = codec.unmarshal(bytes);

    // Payload arrives as JsonNode — verify structure not lost
    assertThat(roundtrip.payload().get("state").asText()).isEqualTo("ON");
    assertThat(roundtrip.payload().get("brightness").asInt()).isEqualTo(254);
}
```

---

## 6. Architecture test — paste-ready

```java
package com.sovereign.connect.bus;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class ScBusSerializationArchitectureTest {

    @Test
    void serializationPackageDoesNotImportCore() throws Exception {
        assertThat(readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization"))
            .doesNotContain("com.sovereign.connect.core");
    }

    @Test
    void serializationPackageDoesNotImportAdapter() throws Exception {
        assertThat(readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization"))
            .doesNotContain("com.sovereign.connect.adapter");
    }

    @Test
    void serializationPackageDoesNotImportNatsOrTestcontainers() throws Exception {
        String sources = readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization");
        assertThat(sources).doesNotContain("io.nats");
        assertThat(sources).doesNotContain("org.testcontainers");
    }

    @Test
    void payloadTypeConstantsDoNotUseJavaClassNames() throws Exception {
        String sources = readAll("src/main/java/com/sovereign/connect/bus/runtime/serialization");
        // No class-name-style constant like "com.sovereign.connect.Foo" should appear
        assertThat(sources).doesNotContain("PAYLOAD_TYPE = \"com.");
        assertThat(sources).doesNotContain("PAYLOAD_TYPE = \"org.");
        assertThat(sources).doesNotContain("PAYLOAD_TYPE = \"io.");
    }

    private String readAll(String dir) throws Exception {
        Path root = Path.of(dir);
        if (!Files.exists(root)) return "";
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(p -> p.toString().endsWith(".java"))
                .map(this::read)
                .reduce("", String::concat);
        }
    }

    private String read(Path p) {
        try { return Files.readString(p); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
```

---

## 7. Stop conditions

```text
STOP-1: mvn -q compile → BUILD SUCCESS.
  Verify: grep -r "io.nats" src/main/java/com/sovereign/connect/bus/ | wc -l == 0

STOP-2: mvn -q test -Dtest="ScSubjectIdTokenCodecTest,ScCorrelationTokenCodecTest,
                             ScSubjectTokenValidatorTest,ScPayloadTypeRegistryTest"
  Expected: 17 tests, 0 failures.

STOP-3: mvn -q test -Dtest="ScJsonWireCodecTest,WireEnvelopeValidatorTest,
                             ScBusSerializationArchitectureTest"
  Expected: 15 tests, 0 failures.

STOP-4: mvn -q test
  Expected: ≥410 tests, 0 failures, 0 errors, 0 skipped.
  SC-C baseline 240 tests still green.
  Existing bus architecture tests (ScBusArchitectureTest, ScBusHardeningArchitectureTest,
  ScBusOutboxBridgeArchitectureTest) still pass unchanged.
```

---

## 8. Negative scope

```text
No io.nats:jnats. No Testcontainers. No NatsScBusPort. No NatsSubjectBuilder.
No lifecycle runtime. No productive ScdCommand. No SC-D fact-family classes.
No changes to RuntimeDispatchService, DispatchStateWritePort, DispatchObservationPort.
No INTERNAL_CONTROL as public adapter wire envelope kind.
No Java class names as payloadType values.
```
