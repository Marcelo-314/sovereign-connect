package com.sovereign.connect.core.topology.model;

import java.util.Objects;

public record TopologyVersionScope(
    TopologyVersionScopeType type,
    String id
) {

    public TopologyVersionScope {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(id, "id is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    public static TopologyVersionScope habitat(String habitatId) {
        return new TopologyVersionScope(TopologyVersionScopeType.HABITAT, habitatId);
    }
}
