package com.sovereign.eib.northbound.dto;

import java.time.Instant;
import java.util.List;

public record NorthboundTopologySnapshotDto(
        String habitatId,
        String topologyVersionValue,
        String topologyVersionScope,
        List<NorthboundRoomViewDto> rooms,
        List<NorthboundZoneViewDto> zones,
        List<NorthboundDeviceViewDto> devices,
        List<NorthboundEndpointViewDto> endpoints,
        Instant readAt
) {
}
