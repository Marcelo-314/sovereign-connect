package com.sovereign.connect.bus.contract;

public record ScResponseWarning(
        String source,
        String code,
        String sanitizedReason
) {
}
