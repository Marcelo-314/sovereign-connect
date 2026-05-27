package com.sovereign.eib;

import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.dto.NorthboundTopologySnapshotDto;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import com.sovereign.eib.service.EibEffectiveViewService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EibEffectiveViewServiceTest {

    @Test
    void populatesSourceTopologyVersionAndTemporalActsFromNorthboundResponses() {
        EibScNorthboundClient client = mock(EibScNorthboundClient.class);
        EibEffectiveViewService service = new EibEffectiveViewService(
                client,
                new EibCanonicalEnvelopeMapper(),
                new EibEffectiveViewMapper(new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256"))
        );
        when(client.getTopologySnapshot("habitat.alpha")).thenReturn(new ScEnvelope<>("OK",
                new NorthboundTopologySnapshotDto("habitat.alpha", "42", "HABITAT",
                        List.of(), List.of(), List.of(), List.of(), Instant.parse("2026-01-01T00:00:00Z")),
                List.of(), null));
        when(client.listTemporalActs("habitat.alpha", "ACTIVE", 50)).thenReturn(new ScEnvelope<>("OK",
                List.of(new NorthboundTemporalActViewDto("temporal.1", "habitat.alpha", "ACTIVE",
                        Instant.parse("2026-01-02T00:00:00Z"), "SIGNAL", "Wake", "WAKE", "target", "creator",
                        null, null, null, null, null)), List.of(), null));

        var response = service.getEffectiveView("habitat.alpha", ctx());

        assertThat(response.status()).isEqualTo("OK");
        assertThat(response.payload().sourceTopologyVersion()).isEqualTo("42");
        assertThat(response.payload().temporalActs()).hasSize(1);
        assertThat(response.payload().temporalActs().getFirst().effectiveTemporalActRef()).startsWith("eib.temporal.");
    }

    private EibRequestContext ctx() {
        return new EibRequestContext("ctx", "actor", "surface", "en", false, false, Instant.now(), "req");
    }
}
