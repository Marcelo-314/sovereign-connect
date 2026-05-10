package com.sovereign.connect.core.topology.query;

import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.time.Instant;
import java.util.Objects;

public record EndpointSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    EndpointNode endpoint,
    EndpointHealth health,
    Instant readAt
) {

    public EndpointSnapshot {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(topologyVersion, "topologyVersion is required");
        Objects.requireNonNull(endpoint, "endpoint is required");
        Objects.requireNonNull(health, "health is required");
        Objects.requireNonNull(readAt, "readAt is required");
    }
}
