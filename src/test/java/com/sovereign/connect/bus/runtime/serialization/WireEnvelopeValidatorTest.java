package com.sovereign.connect.bus.runtime.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WireEnvelopeValidatorTest {
    @Test
    void rejectsMissingPayloadType() {
        assertThatThrownBy(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.COMMAND, null, "1", routing(ScBusLane.COMMAND), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingPayloadSchemaVersion() {
        assertThatThrownBy(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.COMMAND, ScPayloadTypeRegistry.COMMAND_STUB_V1, " ", routing(ScBusLane.COMMAND), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEnvelopeKindLaneMismatch() {
        assertThatThrownBy(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.COMMAND, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1", routing(ScBusLane.EVENT), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsValidCommandEventAndResponseEnvelopes() {
        assertThatCode(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.COMMAND, ScPayloadTypeRegistry.COMMAND_STUB_V1, "1", routing(ScBusLane.COMMAND), null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.EVENT, ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1, "1", routing(ScBusLane.EVENT), null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.RESPONSE, ScPayloadTypeRegistry.RESPONSE_STUB_V1, "1", routing(ScBusLane.RESPONSE), responseMetadata())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsJavaClassNamePayloadType() {
        assertThatThrownBy(() -> WireEnvelopeValidator.validate(envelope(
                ScWireEnvelopeKind.COMMAND, "com.sovereign.connect.Command", "1", routing(ScBusLane.COMMAND), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ScJsonWireEnvelope envelope(
            ScWireEnvelopeKind kind,
            String payloadType,
            String payloadSchemaVersion,
            ScRoutingKey routingKey,
            ScResponseMetadata responseMetadata
    ) {
        return new ScJsonWireEnvelope(
                kind,
                payloadType,
                payloadSchemaVersion,
                metadata(),
                routingKey,
                responseMetadata,
                new ObjectMapper().createObjectNode().put("ok", true)
        );
    }

    private ScMessageMetadata metadata() {
        return new ScMessageMetadata(
                UUID.randomUUID(),
                Instant.parse("2026-05-30T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null
        );
    }

    private ScRoutingKey routing(ScBusLane lane) {
        return new ScRoutingKey(lane, "habitat-1", "topic", "habitat-1", null, null, null);
    }

    private ScResponseMetadata responseMetadata() {
        return new ScResponseMetadata(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ScResponseKind.EXECUTION_RESULT,
                true,
                false,
                null,
                List.of()
        );
    }
}
