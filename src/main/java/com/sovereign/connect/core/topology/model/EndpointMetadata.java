package com.sovereign.connect.core.topology.model;

import java.util.Map;
import java.util.Objects;

public record EndpointMetadata(
    Map<String, String> attributes
) {

    public EndpointMetadata {
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes is required"));
    }

    public static EndpointMetadata empty() {
        return new EndpointMetadata(Map.of());
    }
}
