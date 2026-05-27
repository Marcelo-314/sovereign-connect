package com.sovereign.eib.northbound.dto;

import java.util.List;

public record NorthboundZoneViewDto(
        String zoneId,
        String zoneName,
        String roomId,
        List<String> deviceIds,
        List<String> endpointIds
) {
}
