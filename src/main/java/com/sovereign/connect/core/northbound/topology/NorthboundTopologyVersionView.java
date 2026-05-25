package com.sovereign.connect.core.northbound.topology;

public record NorthboundTopologyVersionView(
    String habitatId,
    String value,
    String scopeType,
    String scopeId
) {
}
