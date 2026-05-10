package com.sovereign.connect.core.topology.query;

import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record CoreSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Map<String, Map<String, Object>> deviceStates,
    Map<String, EndpointHealth> endpointHealth,
    Instant readAt
) {

    public CoreSnapshot {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(topologyVersion, "topologyVersion is required");
        Objects.requireNonNull(topology, "topology is required");
        deviceStates = Map.copyOf(Objects.requireNonNull(deviceStates, "deviceStates is required"));
        endpointHealth = Map.copyOf(Objects.requireNonNull(endpointHealth, "endpointHealth is required"));
        Objects.requireNonNull(readAt, "readAt is required");
        if (!habitatId.equals(topology.habitatId())) {
            throw new IllegalArgumentException("snapshot habitatId must match topology habitatId");
        }
        if (!topologyVersion.equals(topology.topologyVersion())) {
            throw new IllegalArgumentException("snapshot topologyVersion must match topology topologyVersion");
        }
    }
}
