package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.contract.scd.ScdCommand;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry;
import com.sovereign.connect.bus.runtime.serialization.ScWireEnvelopeKind;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NatsScBusPortCommandWireFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesScdCommandFixtureToCommandSubject() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var builder = new NatsSubjectBuilder();
            var codec = new ScJsonWireCodec(mapper);
            var subject = builder.buildCommandSubject("habitat-1", "adapter.z2m", "device", "device.z2m.0x001");
            var received = new AtomicReference<Message>();
            var latch = new CountDownLatch(1);
            server.connection().createDispatcher(message -> {
                received.set(message);
                latch.countDown();
            }).subscribe(subject);
            server.connection().flush(Duration.ofSeconds(2));
            var port = new NatsScBusPort(server.connection(), builder, codec, new EnvelopeValidationService());

            var outcome = port.publishCommand(commandEnvelope(scdCommand()));

            assertThat(outcome).isInstanceOf(Dispatched.class);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            var wire = codec.unmarshal(received.get().getData());
            assertThat(wire.envelopeKind()).isEqualTo(ScWireEnvelopeKind.COMMAND);
            assertThat(wire.payloadType()).isEqualTo(ScPayloadTypeRegistry.COMMAND_SCD_V1);
            assertThat(mapper.treeToValue(wire.payload(), ScdCommand.class)).isEqualTo(scdCommand());
        }
    }

    @Test
    void unsupportedCommandPayloadReturnsFailedWithoutPublishing() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var port = new NatsScBusPort(server.connection(), new NatsSubjectBuilder(),
                    new ScJsonWireCodec(mapper), new EnvelopeValidationService());

            var outcome = port.publishCommand(commandEnvelope("raw-command"));

            assertThat(outcome).isInstanceOf(Failed.class);
            assertThat(((Failed) outcome).code()).isEqualTo("UNSUPPORTED_PAYLOAD_TYPE");
        }
    }

    @Test
    void commandFixtureUsesExplicitRoutingValues() {
        var envelope = commandEnvelope(scdCommand());

        assertThat(envelope.routingKey().habitatId()).isEqualTo("habitat-1");
        assertThat(envelope.routingKey().adapterId()).isEqualTo("adapter.z2m");
        assertThat(envelope.routingKey().deviceId()).isEqualTo("device.z2m.0x001");
    }

    private ScdCommand scdCommand() {
        return new ScdCommand("device.z2m.0x001", "endpoint.switch", "SET", null,
                mapper.createObjectNode().put("value", true));
    }

    private <T> ScCommandEnvelope<T> commandEnvelope(T payload) {
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        return new ScCommandEnvelope<>(
                new ScMessageMetadata(id, Instant.parse("2026-05-30T12:00:00Z"), id, null, null),
                payload,
                new ScRoutingKey(ScBusLane.COMMAND, "device.z2m.0x001", "device",
                        "habitat-1", "adapter.z2m", "device.z2m.0x001", null)
        );
    }
}
