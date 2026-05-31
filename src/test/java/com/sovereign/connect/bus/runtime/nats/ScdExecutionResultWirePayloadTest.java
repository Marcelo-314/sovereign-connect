package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.contract.scd.ScdExecutionResult;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry;
import com.sovereign.connect.bus.runtime.serialization.ScWireEnvelopeKind;
import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScdExecutionResultWirePayloadTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ScJsonWireCodec codec = new ScJsonWireCodec(mapper);

    @Test
    void registryExposesScdExecutionResultPayloadType() {
        assertThat(ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1)
                .isEqualTo("sc.response.scd-execution-result.v1");
    }

    @Test
    void executionResultPayloadRoundtripsThroughWireCodec() throws Exception {
        var result = new ScdExecutionResult(
                "ACKED",
                "provider-42",
                mapper.createObjectNode().put("relay", "on"),
                false,
                null
        );

        var wire = codec.toWireEnvelope(responseEnvelope(result),
                ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1, "1.0");
        WireEnvelopeValidator.validate(wire);
        var decoded = codec.unmarshal(codec.marshal(wire));

        assertThat(decoded.envelopeKind()).isEqualTo(ScWireEnvelopeKind.RESPONSE);
        assertThat(decoded.payloadType()).isEqualTo("sc.response.scd-execution-result.v1");
        assertThat(mapper.treeToValue(decoded.payload(), ScdExecutionResult.class)).isEqualTo(result);
    }

    @Test
    void executionResultObservedStateRemainsOpaque() throws Exception {
        var observedState = mapper.createObjectNode();
        observedState.putObject("opaque").put("code", "E42");
        var result = new ScdExecutionResult("FAILED", "provider-99",
                observedState, true, "retry later");

        var decoded = codec.unmarshal(codec.marshal(codec.toWireEnvelope(responseEnvelope(result),
                ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1, "1.0")));

        assertThat(mapper.treeToValue(decoded.payload(), ScdExecutionResult.class)
                .observedState().at("/opaque/code").asText()).isEqualTo("E42");
    }

    @Test
    void executionResultPayloadTypeIsNotJavaClassName() {
        assertThat(ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1).doesNotStartWith("com.");
    }

    private ScResponseEnvelope<ScdExecutionResult> responseEnvelope(ScdExecutionResult result) {
        UUID request = UUID.randomUUID();
        UUID response = UUID.randomUUID();
        return new ScResponseEnvelope<>(
                new ScMessageMetadata(response, Instant.parse("2026-05-30T12:00:00Z"), request, request, null),
                result,
                new ScRoutingKey(ScBusLane.RESPONSE, "device.z2m.0x001", "scd-response",
                        "habitat-1", "adapter.z2m", "device.z2m.0x001", null),
                new ScResponseMetadata(request, response, ScResponseKind.EXECUTION_RESULT, true, false, null, List.of())
        );
    }
}
