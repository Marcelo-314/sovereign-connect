package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeValidationServiceTest {
    private final EnvelopeValidationService service = new EnvelopeValidationService();

    @Test
    void acceptsValidCommandEventAndResponseFamilies() {
        assertThatNoException().isThrownBy(() -> service.validateCommand(ScBusTestSupport.commandEnvelope()));
        assertThatNoException().isThrownBy(() -> service.validateEvent(ScBusTestSupport.eventEnvelope()));
        assertThatNoException().isThrownBy(() -> service.validateResponse(ScBusTestSupport.responseEnvelope()));
    }

    @Test
    void rejectsNullEnvelopePayloadAndMetadata() {
        assertThatThrownBy(() -> service.validateCommand(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateCommand(new ScCommandEnvelope<>(ScBusTestSupport.rootMetadata(), null, ScBusTestSupport.routing(ScBusLane.COMMAND))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateCommand(new ScCommandEnvelope<>(null, new TestCommandPayload("x"), ScBusTestSupport.routing(ScBusLane.COMMAND))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingMessageIdAndCorrelationId() {
        ScMessageMetadata noMessage = new ScMessageMetadata(null, Instant.now(), UUID.randomUUID(), null, null);
        ScMessageMetadata noCorrelation = new ScMessageMetadata(UUID.randomUUID(), Instant.now(), null, null, null);
        assertThatThrownBy(() -> service.validateCommand(new ScCommandEnvelope<>(noMessage, new TestCommandPayload("x"), ScBusTestSupport.routing(ScBusLane.COMMAND))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateCommand(new ScCommandEnvelope<>(noCorrelation, new TestCommandPayload("x"), ScBusTestSupport.routing(ScBusLane.COMMAND))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonRootWithoutCausationId() {
        ScMessageMetadata metadata = new ScMessageMetadata(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), null, null);
        assertThatThrownBy(() -> service.validateCommand(new ScCommandEnvelope<>(metadata, new TestCommandPayload("x"), ScBusTestSupport.routing(ScBusLane.COMMAND))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCommandEventAndResponseLaneFamilyMismatches() {
        assertThatThrownBy(() -> service.validateCommand(new ScCommandEnvelope<>(ScBusTestSupport.rootMetadata(), new TestCommandPayload("x"), ScBusTestSupport.routing(ScBusLane.EVENT))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateEvent(new ScEventEnvelope<>(ScBusTestSupport.rootMetadata(), new TestEventPayload("x"), ScBusTestSupport.routing(ScBusLane.RESPONSE))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validateResponse(new ScResponseEnvelope<>(ScBusTestSupport.childMetadata(), new TestResponsePayload("x"), ScBusTestSupport.routing(ScBusLane.EVENT), ScBusTestSupport.responseEnvelope().responseMetadata())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullResponseMetadata() {
        assertThatThrownBy(() -> service.validateResponse(
                new ScResponseEnvelope<>(
                    ScBusTestSupport.childMetadata(),
                    new TestResponsePayload("ok"),
                    ScBusTestSupport.routing(ScBusLane.RESPONSE),
                    null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullRequestMessageIdInResponseMetadata() {
        var meta = new ScResponseMetadata(
            null, UUID.randomUUID(),
            ScResponseKind.EXECUTION_RESULT, true, false, null, List.of());
        assertThatThrownBy(() -> service.validateResponse(
                new ScResponseEnvelope<>(
                    ScBusTestSupport.childMetadata(),
                    new TestResponsePayload("ok"),
                    ScBusTestSupport.routing(ScBusLane.RESPONSE),
                    meta)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullResponseKindInResponseMetadata() {
        var meta = new ScResponseMetadata(
            UUID.randomUUID(), UUID.randomUUID(),
            null, true, false, null, List.of());
        assertThatThrownBy(() -> service.validateResponse(
                new ScResponseEnvelope<>(
                    ScBusTestSupport.childMetadata(),
                    new TestResponsePayload("ok"),
                    ScBusTestSupport.routing(ScBusLane.RESPONSE),
                    meta)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullWarningsListInResponseMetadata() {
        var meta = new ScResponseMetadata(
            UUID.randomUUID(), UUID.randomUUID(),
            ScResponseKind.EXECUTION_RESULT, true, false, null, null);
        assertThatThrownBy(() -> service.validateResponse(
                new ScResponseEnvelope<>(
                    ScBusTestSupport.childMetadata(),
                    new TestResponsePayload("ok"),
                    ScBusTestSupport.routing(ScBusLane.RESPONSE),
                    meta)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsEmptyWarningsListInResponseMetadata() {
        var meta = new ScResponseMetadata(
            UUID.randomUUID(), UUID.randomUUID(),
            ScResponseKind.EXECUTION_RESULT, true, false, null, List.of());
        assertThatNoException().isThrownBy(() -> service.validateResponse(
                new ScResponseEnvelope<>(
                    ScBusTestSupport.childMetadata(),
                    new TestResponsePayload("ok"),
                    ScBusTestSupport.routing(ScBusLane.RESPONSE),
                    meta)));
    }
}
