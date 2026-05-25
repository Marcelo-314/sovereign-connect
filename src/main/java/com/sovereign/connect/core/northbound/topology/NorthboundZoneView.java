package com.sovereign.connect.core.northbound.topology;

import java.util.List;

public record NorthboundZoneView(
    String zoneId,
    String zoneName,
    String roomId,
    List<String> deviceIds,
    List<String> endpointIds
) {
    public NorthboundZoneView {
        deviceIds = List.copyOf(deviceIds);
        endpointIds = List.copyOf(endpointIds);
    }
}
