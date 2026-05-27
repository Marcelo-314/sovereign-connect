package com.sovereign.eib.northbound.dto;

import java.time.Instant;
import java.util.List;

public record NorthboundDeviceHealthViewDto(
        String deviceId,
        String status,
        String source,
        int endpointCount,
        Instant readAt,
        List<ScWarningDto> warnings
) {
}
