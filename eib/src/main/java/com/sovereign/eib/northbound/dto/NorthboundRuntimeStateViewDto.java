package com.sovereign.eib.northbound.dto;

import java.time.Instant;
import java.util.Map;

public record NorthboundRuntimeStateViewDto(
        String habitatId,
        String subjectId,
        String subjectType,
        Map<String, Object> state,
        Instant readAt
) {
}
