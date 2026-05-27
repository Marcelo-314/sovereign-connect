package com.sovereign.eib.northbound.dto;

import java.util.List;

public record NorthboundDeviceViewDto(
        String deviceId,
        String alias,
        String displayName,
        String roomId,
        String zoneId,
        String kind,
        String provider,
        List<String> endpointIds,
        List<NorthboundCapabilityViewDto> capabilities,
        String providerDeviceId
) {
}
