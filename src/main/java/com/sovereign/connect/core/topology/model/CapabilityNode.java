package com.sovereign.connect.core.topology.model;

import java.util.Objects;

public record CapabilityNode(
    String capabilityId,
    String name,
    CapabilityKind kind,
    CapabilityTraits traits
) implements TopologyNode {

    public CapabilityNode {
        Objects.requireNonNull(capabilityId, "capabilityId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(kind, "kind is required");
        Objects.requireNonNull(traits, "traits is required");
    }

    @Override
    public String canonicalId() {
        return capabilityId;
    }
}
