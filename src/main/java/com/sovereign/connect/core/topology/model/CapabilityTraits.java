package com.sovereign.connect.core.topology.model;

public record CapabilityTraits(
    boolean idempotent,
    boolean stateChanging,
    boolean requiresConfirmationByDefault
) {
}
