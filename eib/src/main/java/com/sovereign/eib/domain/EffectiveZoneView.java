package com.sovereign.eib.domain;

import java.util.List;

public record EffectiveZoneView(
        String effectiveZoneRef,
        String displayName,
        List<String> effectiveDeviceRefs,
        String visibilityStatus,
        List<EibWarning> warnings
) {
}
