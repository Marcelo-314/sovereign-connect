package com.sovereign.connect.core.northbound.topology;

import java.time.Instant;
import java.util.List;

public record NorthboundTopologySnapshot(
    String habitatId,
    String topologyVersionValue,
    String topologyVersionScope,
    List<NorthboundRoomView> rooms,
    List<NorthboundZoneView> zones,
    List<NorthboundDeviceView> devices,
    List<NorthboundEndpointView> endpoints,
    Instant readAt
) {
    public NorthboundTopologySnapshot {
        rooms = List.copyOf(rooms);
        zones = List.copyOf(zones);
        devices = List.copyOf(devices);
        endpoints = List.copyOf(endpoints);
    }
}
