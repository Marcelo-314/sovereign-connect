package com.sovereign.eib.domain;

import java.time.Instant;
import java.util.List;

public record EffectiveHabitatView(
        String habitatRef,
        String sourceTopologyVersion,
        Instant generatedAt,
        List<EffectiveRoomView> rooms,
        List<EffectiveZoneView> zones,
        List<EffectiveDeviceView> devices,
        List<EffectiveEndpointView> endpoints,
        List<EffectiveTemporalActView> temporalActs,
        EffectiveDiagnosticsSummary diagnostics,
        List<EibWarning> warnings
) {
}
