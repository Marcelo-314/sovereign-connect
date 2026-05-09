package com.sovereign.connect.core.topology.model;

import java.util.List;
import java.util.Objects;

public record ZoneNode(
    String zoneId,
    String zoneName,
    String roomId,
    List<String> deviceIds,
    List<String> endpointIds,
    ZoneTraits traits
) implements TopologyNode {

    public ZoneNode {
        Objects.requireNonNull(zoneId, "zoneId is required");
        Objects.requireNonNull(zoneName, "zoneName is required");
        Objects.requireNonNull(roomId, "roomId is required");
        deviceIds = List.copyOf(Objects.requireNonNull(deviceIds, "deviceIds is required"));
        endpointIds = List.copyOf(Objects.requireNonNull(endpointIds, "endpointIds is required"));
        Objects.requireNonNull(traits, "traits is required");
    }

    @Override
    public String canonicalId() {
        return zoneId;
    }
}
