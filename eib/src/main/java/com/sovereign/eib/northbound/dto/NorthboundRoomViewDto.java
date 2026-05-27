package com.sovereign.eib.northbound.dto;

import java.util.List;

public record NorthboundRoomViewDto(
        String roomId,
        String roomName,
        List<String> zoneIds,
        List<String> deviceIds,
        List<String> endpointIds
) {
}
