package com.sovereign.connect.bus.runtime.serialization;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScJsonWireCodecTest {
    @Test
    void commandEnvelopeRoundtripsThroughScJsonWireV1() {
        var codec = new ScJsonWireCodec();
        var envelope = new ScCommandEnvelope<>(
                testMetadata(), new StubPayload("SET", "device.test"), testCommandRouting());

        var wire = codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1");
        var roundtrip = codec.unmarshal(codec.marshal(wire));

        assertThat(roundtrip.envelopeKind()).isEqualTo(ScWireEnvelopeKind.COMMAND);
        assertThat(roundtrip.payloadType()).isEqualTo(ScPayloadTypeRegistry.COMMAND_STUB_V1);
        assertThat(roundtrip.payloadSchemaVersion()).isEqualTo("1");
        assertThat(roundtrip.metadata().messageId()).isEqualTo(envelope.metadata().messageId());
        assertThat(roundtrip.payload().get("operation").asText()).isEqualTo("SET");
    }

    @Test
    void eventEnvelopeRoundtripsThroughScJsonWireV1() {
        var codec = new ScJsonWireCodec();
        var envelope = new ScEventEnvelope<>(
                testMetadata(), new StubPayload("FIRED", "timer.test"), testEventRouting());

        var roundtrip = codec.unmarshal(codec.marshal(
                codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1, "1")));

        assertThat(roundtrip.envelopeKind()).isEqualTo(ScWireEnvelopeKind.EVENT);
        assertThat(roundtrip.routingKey().lane()).isEqualTo(ScBusLane.EVENT);
        assertThat(roundtrip.payload().get("target").asText()).isEqualTo("timer.test");
    }

    @Test
    void responseEnvelopeRoundtripsThroughScJsonWireV1() {
        var codec = new ScJsonWireCodec();
        var responseMetadata = testResponseMetadata();
        var envelope = new ScResponseEnvelope<>(
                testMetadata(), new StubPayload("ACK", "device.test"), testResponseRouting(), responseMetadata);

        var roundtrip = codec.unmarshal(codec.marshal(
                codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.RESPONSE_STUB_V1, "1")));

        assertThat(roundtrip.envelopeKind()).isEqualTo(ScWireEnvelopeKind.RESPONSE);
        assertThat(roundtrip.responseMetadata()).isEqualTo(responseMetadata);
        assertThat(roundtrip.payload().get("operation").asText()).isEqualTo("ACK");
    }

    @Test
    void serializedEnvelopeContainsKindPayloadTypeAndSchemaVersion() {
        var codec = new ScJsonWireCodec();
        var envelope = new ScCommandEnvelope<>(
                testMetadata(), new StubPayload("SET", "device.test"), testCommandRouting());

        String json = new String(codec.marshal(codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1")));

        assertThat(json).contains("\"envelopeKind\":\"COMMAND\"");
        assertThat(json).contains("\"payloadType\":\"sc.command.stub.v1\"");
        assertThat(json).contains("\"payloadSchemaVersion\":\"1\"");
        assertThat(json).contains("\"metadata\"");
        assertThat(json).contains("\"routingKey\"");
    }

    @Test
    void unknownPayloadTypeIsPreservedOpaquely() {
        var codec = new ScJsonWireCodec();
        var envelope = new ScCommandEnvelope<>(
                testMetadata(), Map.of("x", 1), testCommandRouting());

        var roundtrip = codec.unmarshal(codec.marshal(
                codec.toWireEnvelope(envelope, "sc.command.future.v2", "1")));

        assertThat(roundtrip.payloadType()).isEqualTo("sc.command.future.v2");
        assertThat(roundtrip.payload().get("x").asInt()).isEqualTo(1);
    }

    @Test
    void opaqueJsonPayloadIsPreservedThroughRoundtrip() {
        var codec = new ScJsonWireCodec();
        var payload = Map.of("state", "ON", "brightness", 254);
        var envelope = new ScCommandEnvelope<>(
                testMetadata(), payload, testCommandRouting());

        var roundtrip = codec.unmarshal(codec.marshal(
                codec.toWireEnvelope(envelope, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1")));

        assertThat(roundtrip.payload().get("state").asText()).isEqualTo("ON");
        assertThat(roundtrip.payload().get("brightness").asInt()).isEqualTo(254);
    }

    private static ScMessageMetadata testMetadata() {
        return new ScMessageMetadata(
                UUID.randomUUID(),
                Instant.parse("2026-05-30T10:00:00Z"),
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

    record StubPayload(String operation, String target) {}
}
