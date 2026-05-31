package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry;
import com.sovereign.connect.bus.runtime.serialization.ScWireEnvelopeKind;
import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator;
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

class NatsScBusPortEventIntegrationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesTimerFiredEventAsWireEnvelopeOnExpectedSubject() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var codec = new ScJsonWireCodec(mapper);
            var builder = new NatsSubjectBuilder();
            var port = new NatsScBusPort(server.connection(), builder, codec, new EnvelopeValidationService());
            var subject = builder.buildTimerFiredEventSubject("habitat-1");
            var received = new AtomicReference<Message>();
            var latch = new CountDownLatch(1);
            var dispatcher = server.connection().createDispatcher(message -> {
                received.set(message);
                latch.countDown();
            });
            dispatcher.subscribe(subject);
            server.connection().flush(Duration.ofSeconds(2));

            var outcome = port.publishEvent(eventEnvelope("habitat-1", null));

            assertThat(outcome).isInstanceOf(Dispatched.class);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            var wire = codec.unmarshal(received.get().getData());
            WireEnvelopeValidator.validate(wire);
            assertThat(wire.envelopeKind()).isEqualTo(ScWireEnvelopeKind.EVENT);
            assertThat(wire.payloadType()).isEqualTo(ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1);
            assertThat(wire.routingKey().lane()).isEqualTo(ScBusLane.EVENT);
        }
    }

    @Test
    void registeredTimerFiredHandlerReceivesOpaquePayload() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var port = new NatsScBusPort(server.connection(), new NatsSubjectBuilder(),
                    new ScJsonWireCodec(mapper), new EnvelopeValidationService());
            var receivedPayload = new AtomicReference<Object>();
            var latch = new CountDownLatch(1);
            port.registerEventHandler("sc-c.timer-fired", envelope -> {
                receivedPayload.set(envelope.payload());
                latch.countDown();
            });

            port.publishEvent(eventEnvelope("habitat-1", "habitat-1"));

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(receivedPayload.get()).hasToString("{\"timer\":\"fired\"}");
        }
    }

    @Test
    void eventPublishUsesPartitionKeyAsHabitatFallback() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var builder = new NatsSubjectBuilder();
            var expectedSubject = builder.buildTimerFiredEventSubject("habitat-from-partition");
            var latch = new CountDownLatch(1);
            server.connection().createDispatcher(message -> latch.countDown()).subscribe(expectedSubject);
            server.connection().flush(Duration.ofSeconds(2));
            var port = new NatsScBusPort(server.connection(), builder,
                    new ScJsonWireCodec(mapper), new EnvelopeValidationService());

            var outcome = port.publishEvent(eventEnvelope("habitat-from-partition", null));

            assertThat(outcome).isInstanceOf(Dispatched.class);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ScEventEnvelope<Object> eventEnvelope(String partitionKey, String habitatId) {
        UUID id = UUID.randomUUID();
        return new ScEventEnvelope<>(
                new ScMessageMetadata(id, Instant.parse("2026-05-30T12:00:00Z"), id, null, null),
                mapper.createObjectNode().put("timer", "fired"),
                new ScRoutingKey(ScBusLane.EVENT, partitionKey, "sc-c.timer-fired", habitatId, null, null, null)
        );
    }
}
