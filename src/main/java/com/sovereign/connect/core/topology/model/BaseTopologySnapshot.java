package com.sovereign.connect.core.topology.model;

import java.time.Instant;
import java.util.Objects;

public record BaseTopologySnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Instant capturedAt
) {

    public BaseTopologySnapshot {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(topologyVersion, "topologyVersion is required");
        Objects.requireNonNull(topology, "topology is required");
        Objects.requireNonNull(capturedAt, "capturedAt is required");
        if (!habitatId.equals(topology.habitatId())) {
            throw new IllegalArgumentException("snapshot habitatId must match topology habitatId");
        }
        if (!topologyVersion.equals(topology.topologyVersion())) {
            throw new IllegalArgumentException("snapshot topologyVersion must match topology topologyVersion");
        }
    }
}
