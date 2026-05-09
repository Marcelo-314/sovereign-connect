package com.sovereign.connect.core.topology.model;

import java.util.Objects;

public record TopologyVersion(
    TopologyVersionScope scope,
    String value
) {

    public TopologyVersion {
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(value, "value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static TopologyVersion initialForHabitat(String habitatId) {
        return habitatVersion(habitatId, 1L);
    }

    public static TopologyVersion habitatVersion(String habitatId, long value) {
        if (value < 1L) {
            throw new IllegalArgumentException("topology version must be positive");
        }
        return new TopologyVersion(TopologyVersionScope.habitat(habitatId), Long.toString(value));
    }

    public TopologyVersion next() {
        return new TopologyVersion(scope, Long.toString(asLong() + 1L));
    }

    public long asLong() {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("topology version value is not a monotonic long: " + value, ex);
        }
    }

    public boolean isScopedToHabitat(String habitatId) {
        return scope.type() == TopologyVersionScopeType.HABITAT && scope.id().equals(habitatId);
    }
}
