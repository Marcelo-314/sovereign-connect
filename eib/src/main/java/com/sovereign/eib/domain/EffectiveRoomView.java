package com.sovereign.eib.domain;

import java.util.List;

public record EffectiveRoomView(
        String effectiveRoomRef,
        String displayName,
        List<String> effectiveDeviceRefs,
        String visibilityStatus,
        List<EibWarning> warnings
) {
}
