package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.contract.scd.ScdCommand;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry;
import com.sovereign.connect.bus.runtime.serialization.ScWireEnvelopeKind;
import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScdCommandWirePayloadTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ScJsonWireCodec codec = new ScJsonWireCodec(mapper);

    @Test
    void registryExposesScdCommandPayloadType() {
        assertThat(ScPayloadTypeRegistry.COMMAND_SCD_V1).isEqualTo("sc.command.scd.v1");
    }

    @Test
    void commandPayloadRoundtripsThroughWireCodec() throws Exception {
        var command = new ScdCommand(
                "device.z2m.0x001",
                "endpoint.switch",
                "INVOKE_CAPABILITY",
                "sc.capability.onoff.v1",
                mapper.createObjectNode().put("value", true)
        );

        var wire = codec.toWireEnvelope(commandEnvelope(command), ScPayloadTypeRegistry.COMMAND_SCD_V1, "1.0");
        WireEnvelopeValidator.validate(wire);
        var decoded = codec.unmarshal(codec.marshal(wire));

        assertThat(decoded.envelopeKind()).isEqualTo(ScWireEnvelopeKind.COMMAND);
        assertThat(decoded.payloadType()).isEqualTo("sc.command.scd.v1");
        assertThat(mapper.treeToValue(decoded.payload(), ScdCommand.class)).isEqualTo(command);
    }

    @Test
    void commandPayloadKeepsOpaqueParams() throws Exception {
        var params = mapper.createObjectNode();
        params.putObject("vendor").put("raw", 7);
        var command = new ScdCommand("target", null, "SET", null, params);

        var decoded = codec.unmarshal(codec.marshal(
                codec.toWireEnvelope(commandEnvelope(command), ScPayloadTypeRegistry.COMMAND_SCD_V1, "1.0")));

        assertThat(mapper.treeToValue(decoded.payload(), ScdCommand.class).params().at("/vendor/raw").asInt())
                .isEqualTo(7);
    }

    @Test
    void commandPayloadTypeIsNotJavaClassName() {
        assertThat(ScPayloadTypeRegistry.COMMAND_SCD_V1).doesNotStartWith("com.");
    }

    private ScCommandEnvelope<ScdCommand> commandEnvelope(ScdCommand command) {
        return new ScCommandEnvelope<>(metadata(), command,
                new ScRoutingKey(ScBusLane.COMMAND, "device.z2m.0x001", "device",
                        "habitat-1", "adapter.z2m", "device.z2m.0x001", null));
    }

    private ScMessageMetadata metadata() {
        UUID id = UUID.randomUUID();
        return new ScMessageMetadata(id, Instant.parse("2026-05-30T12:00:00Z"), id, null, null);
    }
}
