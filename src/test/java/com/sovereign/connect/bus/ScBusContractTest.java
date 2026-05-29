package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScResponseWarning;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ScBusContractTest {
    @Test
    void metadataFieldsKeepNormativeOrder() {
        assertThat(recordFields(ScMessageMetadata.class))
                .containsExactly("messageId", "emittedAt", "correlationId", "causationId", "topologyVersion");
    }

    @Test
    void routingKeyFieldsKeepNormativeOrder() {
        assertThat(recordFields(ScRoutingKey.class))
                .containsExactly("lane", "partitionKey", "topic", "habitatId", "adapterId", "deviceId", "endpointId");
    }

    @Test
    void envelopeFieldsKeepNormativeOrder() {
        assertThat(recordFields(ScCommandEnvelope.class)).containsExactly("metadata", "payload", "routingKey");
        assertThat(recordFields(ScEventEnvelope.class)).containsExactly("metadata", "payload", "routingKey");
        assertThat(recordFields(ScResponseEnvelope.class)).containsExactly("metadata", "payload", "routingKey", "responseMetadata");
    }

    @Test
    void responseMetadataFieldsKeepNormativeOrder() {
        assertThat(recordFields(ScResponseWarning.class)).containsExactly("source", "code", "sanitizedReason");
        assertThat(recordFields(ScResponseMetadata.class))
                .containsExactly("requestMessageId", "requestId", "responseKind", "terminal", "retryable", "sanitizedReason", "warnings");
    }

    @Test
    void laneAndResponseKindValuesAreComplete() {
        assertThat(ScBusLane.values()).containsExactly(ScBusLane.COMMAND, ScBusLane.EVENT, ScBusLane.RESPONSE, ScBusLane.INTERNAL_CONTROL);
        assertThat(ScResponseKind.values()).extracting(Enum::name).containsExactly(
                "VALIDATION_FAILURE", "EXECUTION_PROGRESS", "EXECUTION_RESULT", "READ_RESULT", "REFRESH_RESULT",
                "ADMISSION_PROGRESS", "ADMISSION_RESULT", "LIFECYCLE_PROGRESS", "LIFECYCLE_RESULT", "IDEMPOTENCY_STATUS"
        );
    }

    @Test
    void recordsExposeConstructorValues() {
        ScCommandEnvelope<TestCommandPayload> envelope = ScBusTestSupport.commandEnvelope();
        assertThat(envelope.payload().value()).isEqualTo("on");
        assertThat(envelope.routingKey().habitatId()).isEqualTo("habitat-1");
    }

    private static java.util.List<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
