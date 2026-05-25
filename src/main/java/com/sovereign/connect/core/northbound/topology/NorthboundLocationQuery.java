package com.sovereign.connect.core.northbound.topology;

public record NorthboundLocationQuery(
    String habitatId,
    String roomOrZoneId
) {
}
