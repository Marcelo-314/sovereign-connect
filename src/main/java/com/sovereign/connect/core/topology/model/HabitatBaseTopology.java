package com.sovereign.connect.core.topology.model;

import java.util.List;
import java.util.Objects;

public record HabitatBaseTopology(
    String habitatId,
    TopologyVersion topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    TopologyMetadata metadata
) {

    public HabitatBaseTopology {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(topologyVersion, "topologyVersion is required");
        Objects.requireNonNull(metadata, "metadata is required");
        if (!topologyVersion.isScopedToHabitat(habitatId)) {
            throw new IllegalArgumentException("topologyVersion must be scoped to the topology habitatId");
        }
        rooms = List.copyOf(Objects.requireNonNull(rooms, "rooms is required"));
        zones = List.copyOf(Objects.requireNonNull(zones, "zones is required"));
        devices = List.copyOf(Objects.requireNonNull(devices, "devices is required"));
        endpoints = List.copyOf(Objects.requireNonNull(endpoints, "endpoints is required"));
    }

    public long versionValue() {
        return topologyVersion.asLong();
    }
}
