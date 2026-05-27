package com.sovereign.eib;

import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EibEffectiveRefCodecTest {

    @Test
    void generatesDeterministicOpaqueRefsWithoutCanonicalIdLeakage() {
        EibEffectiveRefCodec codec = new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");

        String first = codec.generateRef("eib.device", "habitat.alpha", "device.provider.raw-001");
        String second = codec.generateRef("eib.device", "habitat.alpha", "device.provider.raw-001");

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("eib.device.");
        assertThat(first).doesNotContain("provider").doesNotContain("raw-001");
    }

    @Test
    void resolvesTemporalRefOnlyFromVisibleActs() {
        EibEffectiveRefCodec codec = new EibEffectiveRefCodec("test-secret-32-chars-for-hmac-256");
        NorthboundTemporalActViewDto visible = temporal("temporal.visible");
        String ref = codec.generateRef("eib.temporal", "habitat.alpha", "temporal.visible");

        assertThat(codec.resolveTemporalRef(ref, "habitat.alpha", List.of(visible))).contains("temporal.visible");
        assertThat(codec.resolveTemporalRef(ref, "habitat.other", List.of(visible))).isEmpty();
    }

    private NorthboundTemporalActViewDto temporal(String id) {
        return new NorthboundTemporalActViewDto(id, "habitat.alpha", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"),
                "SIGNAL", "Wake", "WAKE", "target", "creator", null, null, null, null, null);
    }
}
