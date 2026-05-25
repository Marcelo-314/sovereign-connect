package com.sovereign.connect.core.northbound.topology;

import java.util.List;

public record NorthboundEndpointView(
    String endpointId,
    String deviceId,
    String alias,
    String displayName,
    String kind,
    String roomId,
    String zoneId,
    List<NorthboundCapabilityView> capabilities,
    String providerEndpointId
) {
    public NorthboundEndpointView {
        capabilities = List.copyOf(capabilities);
    }
}
