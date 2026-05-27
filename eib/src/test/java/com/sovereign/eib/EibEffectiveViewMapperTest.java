package com.sovereign.eib;

import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.northbound.dto.NorthboundCapabilityViewDto;
import com.sovereign.eib.northbound.dto.NorthboundDeviceViewDto;
import com.sovereign.eib.northbound.dto.NorthboundEndpointViewDto;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import com.sovereign.eib.service.EibEffectiveViewMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EibEffectiveViewMapperTest {

    private final EibEffectiveViewMapper mapper =
            new EibEffectiveViewMapper(new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256"));

    @Test
    void ordinaryModeDoesNotExposeCanonicalIdsOrProviderIds() {
        var device = new NorthboundDeviceViewDto("device.provider.device-1", "alias", "Lamp",
                "room.1", "zone.1", "LIGHT", "provider", List.of("endpoint.provider.device-1.main"),
                List.of(new NorthboundCapabilityViewDto("capability.provider.device-1.main.onoff", "Power", "ON_OFF")),
                "raw-provider-id");
        var endpoint = new NorthboundEndpointViewDto("endpoint.provider.device-1.main", device.deviceId(),
                "main", "Main", "LIGHT", "room.1", "zone.1", List.of(), "raw-endpoint-id");
        EibRequestContext ctx = new EibRequestContext("ctx", "actor", "surface", "en",
                false, false, Instant.now(), "req");

        var view = mapper.device("habitat.alpha", device, List.of(endpoint), ctx);

        assertThat(view.canonicalDeviceId()).isNull();
        assertThat(view.effectiveDeviceRef()).doesNotContain("provider").doesNotContain("device-1");
        assertThat(view.endpoints()).hasSize(1);
        assertThat(view.endpoints().getFirst().canonicalEndpointId()).isNull();
        assertThat(view.endpoints().getFirst().effectiveEndpointRef()).doesNotContain("raw-endpoint-id");
    }

    @Test
    void diagnosticModeCanExposeCanonicalIdsButStillNeverProviderMetadata() {
        var endpoint = new NorthboundEndpointViewDto("endpoint.provider.device-1.main", "device.provider.device-1",
                "main", "Main", "LIGHT", "room.1", "zone.1", List.of(), "raw-endpoint-id");
        EibRequestContext ctx = new EibRequestContext("ctx", "actor", "surface", "en",
                true, true, Instant.now(), "req");

        var view = mapper.endpoint("habitat.alpha", endpoint, ctx);

        assertThat(view.canonicalEndpointId()).isEqualTo("endpoint.provider.device-1.main");
        assertThat(view.toString()).doesNotContain("raw-endpoint-id");
    }
}
