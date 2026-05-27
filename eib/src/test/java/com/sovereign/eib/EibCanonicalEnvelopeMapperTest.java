package com.sovereign.eib;

import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.northbound.dto.ScErrorDto;
import com.sovereign.eib.northbound.dto.ScWarningDto;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EibCanonicalEnvelopeMapperTest {

    private final EibCanonicalEnvelopeMapper mapper = new EibCanonicalEnvelopeMapper();

    @Test
    void preservesKnownStatusesAndErrorWarningSourcesInTrace() {
        List<String> statuses = List.of("OK", "CREATED", "ACCEPTED", "CANCELLED", "NOT_FOUND",
                "INVALID_REQUEST", "INVALID_CANONICAL_ID", "VALIDATION_ERROR", "UNSUPPORTED_PROFILE",
                "DEFERRED_SC_B_REQUIRED", "UNKNOWN_PENDING_NORMALIZATION", "INTERNAL_ERROR");

        for (String status : statuses) {
            var envelope = new ScEnvelope<>(status, null,
                    List.of(new ScWarningDto("WARN", "warn", "sc.source")),
                    new ScErrorDto("ERR", "err", "sc.error"));
            var trace = mapper.toTrace("client", "admission", envelope);

            assertThat(trace.scNorthboundStatus()).isEqualTo(status);
            assertThat(trace.error().source()).isEqualTo("sc.error");
            assertThat(trace.warnings().getFirst().source()).isEqualTo("sc.source");
        }
    }
}
