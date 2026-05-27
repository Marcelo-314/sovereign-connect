package com.sovereign.eib;

import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import com.sovereign.eib.service.EibTemporalActProjectionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EibTemporalActProjectionServiceTest {

    private final EibScNorthboundClient client = mock(EibScNorthboundClient.class);
    private final EibEffectiveRefCodec codec =
            new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
    private final EibTemporalActProjectionService service =
            new EibTemporalActProjectionService(
                    client, new EibCanonicalEnvelopeMapper(),
                    new EibEffectiveViewMapper(codec), codec);
    private final EibRequestContext ctx =
            new EibRequestContext("ctx", "actor", "surface", "en",
                    false, false, Instant.now(), "req");

    @Test
    void listReturnsUpstreamUnavailableWhenClientThrows() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var response = service.listEffectiveTemporalActs("habitat.alpha", ctx);

        assertThat(response.status()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(response.error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(response.error().source()).isEqualTo("eib.northbound");
    }

    @Test
    void getReturnsUpstreamUnavailableWhenClientThrows() {
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50))
                .thenThrow(new EibUpstreamUnavailableException("connection refused"));

        var response = service.getEffectiveTemporalAct("habitat.alpha", "eib.temporal.any", ctx);

        assertThat(response.status()).isEqualTo("UPSTREAM_UNAVAILABLE");
        assertThat(response.payload()).isNull();
        assertThat(response.error().code()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }
}
