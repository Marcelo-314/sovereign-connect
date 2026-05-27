package com.sovereign.eib.domain;

import java.util.List;

public record EffectiveEndpointView(
        String effectiveEndpointRef,
        String canonicalEndpointId,
        String displayName,
        List<EffectiveCapabilityAffordance> capabilities,
        String healthSummary,
        String runtimeSummary,
        String visibilityStatus,
        String operabilityStatus,
        List<EibWarning> warnings
) {
}
