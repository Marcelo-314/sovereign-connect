package com.sovereign.eib.domain;

import java.util.List;

public record EffectiveDeviceView(
        String effectiveDeviceRef,
        String canonicalDeviceId,
        String displayName,
        String roomRef,
        String zoneRef,
        List<EffectiveEndpointView> endpoints,
        List<EffectiveCapabilityAffordance> capabilities,
        String healthSummary,
        String runtimeSummary,
        String visibilityStatus,
        String operabilityStatus,
        List<EibWarning> warnings
) {
}
