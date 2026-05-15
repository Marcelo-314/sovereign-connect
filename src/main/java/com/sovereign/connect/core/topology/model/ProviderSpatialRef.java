package com.sovereign.connect.core.topology.model;

import java.util.Map;

public record ProviderSpatialRef(
    String provider,
    String providerRoomId,
    String providerAreaId,
    String providerZoneId,
    Map<String, String> nativeCoordinates
) {
}
