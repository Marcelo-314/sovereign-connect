package com.sovereign.connect.core.topology.model;

import java.util.List;
import java.util.Objects;

public record RoomNode(
    String roomId,
    String roomName,
    List<String> zoneIds,
    List<String> deviceIds,
    List<String> endpointIds,
    RoomTraits traits
) implements TopologyNode {

    public RoomNode {
        Objects.requireNonNull(roomId, "roomId is required");
        Objects.requireNonNull(roomName, "roomName is required");
        zoneIds = List.copyOf(Objects.requireNonNull(zoneIds, "zoneIds is required"));
        deviceIds = List.copyOf(Objects.requireNonNull(deviceIds, "deviceIds is required"));
        endpointIds = List.copyOf(Objects.requireNonNull(endpointIds, "endpointIds is required"));
        Objects.requireNonNull(traits, "traits is required");
    }

    @Override
    public String canonicalId() {
        return roomId;
    }
}
