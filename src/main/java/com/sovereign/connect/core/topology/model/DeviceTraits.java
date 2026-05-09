package com.sovereign.connect.core.topology.model;

public record DeviceTraits(
    boolean safetyCritical,
    boolean supportsVccFollowUp,
    boolean requiresStableStateReadback
) {
}
