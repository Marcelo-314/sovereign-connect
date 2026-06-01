package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import io.nats.client.Connection;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsScBusPortDrainTest {
    @Test
    void closeCompletesWithoutThrowing() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            NatsScBusPort port = buildPort(server.connection());

            assertThatCode(port::close).doesNotThrowAnyException();
        }
    }

    @Test
    void closeIsIdempotent() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            NatsScBusPort port = buildPort(server.connection());

            port.close();

            assertThatCode(port::close).doesNotThrowAnyException();
        }
    }

    @Test
    void publishEventAfterCloseReturnsFailed() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            NatsScBusPort port = buildPort(server.connection());
            port.close();

            DispatchOutcome outcome = port.publishEvent(buildTimerFiredEnvelope("habitat.test.h1"));

            assertThat(outcome).isInstanceOf(Failed.class);
        }
    }

    @Test
    void registerEventHandlerAfterCloseThrowsIllegalStateException() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            NatsScBusPort port = buildPort(server.connection());
            port.close();

            assertThatThrownBy(() -> port.registerEventHandler("sc-c.timer-fired", envelope -> { }))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void commandAndResponseRegistrationAfterCloseThrowIllegalStateException() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            NatsScBusPort port = buildPort(server.connection());
            port.close();

            assertThatThrownBy(() -> port.registerCommandHandler("topic.command", envelope -> { }))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> port.registerResponseHandler("topic.response", envelope -> { }))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void natsScBusPortImplementsAutoCloseable() {
        assertThat(AutoCloseable.class).isAssignableFrom(NatsScBusPort.class);
    }

    private NatsScBusPort buildPort(Connection conn) {
        return new NatsScBusPort(
                conn,
                new NatsSubjectBuilder(),
                new ScJsonWireCodec(new ObjectMapper().findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)),
                new EnvelopeValidationService()
        );
    }

    private ScEventEnvelope<Object> buildTimerFiredEnvelope(String habitatId) {
        UUID id = UUID.randomUUID();
        return new ScEventEnvelope<>(
                new ScMessageMetadata(id, Instant.now(), id, null, null),
                new ObjectMapper().createObjectNode().put("timer", "fired"),
                new ScRoutingKey(ScBusLane.EVENT, habitatId, "sc-c.timer-fired", habitatId, null, null, null)
        );
    }
}
