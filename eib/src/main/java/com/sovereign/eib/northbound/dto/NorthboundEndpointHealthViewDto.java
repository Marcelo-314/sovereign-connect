package com.sovereign.eib.northbound.dto;

import java.time.Instant;

public record NorthboundEndpointHealthViewDto(String endpointId, String status, Instant lastSeenAt, String details) {
}
