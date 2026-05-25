package com.sovereign.connect.core.northbound.runtime;

import java.time.Instant;
import java.util.Map;

public record NorthboundRuntimeStateView(
    String habitatId,
    String subjectId,
    String subjectType,
    Map<String, Object> state,
    Instant readAt
) {
    public NorthboundRuntimeStateView {
        state = Map.copyOf(state);
    }
}
