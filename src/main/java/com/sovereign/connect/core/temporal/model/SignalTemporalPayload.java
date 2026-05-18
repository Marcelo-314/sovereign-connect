package com.sovereign.connect.core.temporal.model;

import java.util.Objects;

public record SignalTemporalPayload(
    String label,
    String signalKind
) implements TemporalActPayload {

    public SignalTemporalPayload {
        Objects.requireNonNull(label, "label is required");
        Objects.requireNonNull(signalKind, "signalKind is required");
    }
}
