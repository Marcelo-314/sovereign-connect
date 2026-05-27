package com.sovereign.eib.domain;

public record EffectiveCapabilityAffordance(
        String affordanceRef,
        String capabilityKind,
        String displayLabel,
        String visibilityStatus,
        String operabilityStatus,
        boolean requiresConfirmation,
        String deferredReason
) {
}
