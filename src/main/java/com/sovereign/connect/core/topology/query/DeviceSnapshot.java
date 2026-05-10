package com.sovereign.connect.core.topology.query;

import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record DeviceSnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    DeviceNode device,
    Map<String, Object> state,
    Instant readAt
) {

    public DeviceSnapshot {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(topologyVersion, "topologyVersion is required");
        Objects.requireNonNull(device, "device is required");
        state = Map.copyOf(Objects.requireNonNull(state, "state is required"));
        Objects.requireNonNull(readAt, "readAt is required");
    }
}
