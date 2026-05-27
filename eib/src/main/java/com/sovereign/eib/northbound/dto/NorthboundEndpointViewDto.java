package com.sovereign.eib.northbound.dto;

import java.util.List;

public record NorthboundEndpointViewDto(
        String endpointId,
        String deviceId,
        String alias,
        String displayName,
        String kind,
        String roomId,
        String zoneId,
        List<NorthboundCapabilityViewDto> capabilities,
        String providerEndpointId
) {
}
