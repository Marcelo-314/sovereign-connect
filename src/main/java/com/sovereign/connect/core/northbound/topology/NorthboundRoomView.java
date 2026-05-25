package com.sovereign.connect.core.northbound.topology;

import java.util.List;

public record NorthboundRoomView(
    String roomId,
    String roomName,
    List<String> zoneIds,
    List<String> deviceIds,
    List<String> endpointIds
) {
    public NorthboundRoomView {
        zoneIds = List.copyOf(zoneIds);
        deviceIds = List.copyOf(deviceIds);
        endpointIds = List.copyOf(endpointIds);
    }
}
