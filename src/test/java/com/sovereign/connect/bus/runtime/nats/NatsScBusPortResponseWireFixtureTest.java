package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.contract.scd.ScdExecutionResult;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NatsScBusPortResponseWireFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UUID requestId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void publishesScdExecutionResultToResponseSubject() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var builder = new NatsSubjectBuilder();
            var codec = new ScJsonWireCodec(mapper);
            var subject = builder.buildResponseSubject("habitat-1", "adapter.z2m", requestId);
            var received = new AtomicReference<Message>();
            var latch = new CountDownLatch(1);
            server.connection().createDispatcher(message -> {
                received.set(message);
                latch.countDown();
            }).subscribe(subject);
            server.connection().flush(Duration.ofSeconds(2));
            var port = new NatsScBusPort(server.connection(), builder, codec, new EnvelopeValidationService());

            var outcome = port.publishResponse(responseEnvelope(executionResult()));

            assertThat(outcome).isInstanceOf(Dispatched.class);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            var wire = codec.unmarshal(received.get().getData());
            assertThat(wire.envelopeKind()).isEqualTo(ScWireEnvelopeKind.RESPONSE);
            assertThat(wire.payloadType()).isEqualTo(ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1);
            assertThat(mapper.treeToValue(wire.payload(), ScdExecutionResult.class)).isEqualTo(executionResult());
        }
    }

    @Test
    void unsupportedResponsePayloadReturnsFailedWithoutPublishing() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var port = new NatsScBusPort(server.connection(), new NatsSubjectBuilder(),
                    new ScJsonWireCodec(mapper), new EnvelopeValidationService());

            var outcome = port.publishResponse(responseEnvelope("raw-response"));

            assertThat(outcome).isInstanceOf(Failed.class);
            assertThat(((Failed) outcome).code()).isEqualTo("UNSUPPORTED_PAYLOAD_TYPE");
        }
    }

    @Test
    void responseFixtureUsesCorrelationIdAsSubjectTokenSource() {
        var subject = new NatsSubjectBuilder().buildResponseSubject("habitat-1", "adapter.z2m", requestId);

        assertThat(subject).endsWith("123e4567e89b12d3a456426614174000");
        assertThat(subject).doesNotContain("scid1_123e4567");
    }

    private ScdExecutionResult executionResult() {
        return new ScdExecutionResult("ACKED", "provider-42",
                mapper.createObjectNode().put("relay", "on"), false, null);
    }

    private <T> ScResponseEnvelope<T> responseEnvelope(T payload) {
        UUID responseId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");
        return new ScResponseEnvelope<>(
                new ScMessageMetadata(responseId, Instant.parse("2026-05-30T12:00:00Z"), requestId, requestId, null),
                payload,
                new ScRoutingKey(ScBusLane.RESPONSE, "device.z2m.0x001", "scd-response",
                        "habitat-1", "adapter.z2m", "device.z2m.0x001", null),
                new ScResponseMetadata(requestId, responseId, ScResponseKind.EXECUTION_RESULT, true, false, null, List.of())
        );
    }
}
