package com.sovereign.connect.core.northbound.topology;

import java.util.List;

public record NorthboundDeviceView(
    String deviceId,
    String alias,
    String displayName,
    String roomId,
    String zoneId,
    String kind,
    String provider,
    List<String> endpointIds,
    List<NorthboundCapabilityView> capabilities,
    String providerDeviceId
) {
    public NorthboundDeviceView {
        endpointIds = List.copyOf(endpointIds);
        capabilities = List.copyOf(capabilities);
    }
}
