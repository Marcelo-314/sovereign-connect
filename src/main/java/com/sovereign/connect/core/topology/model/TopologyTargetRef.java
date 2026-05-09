package com.sovereign.connect.core.topology.model;

import java.util.Objects;

public record TopologyTargetRef(
    String deviceId,
    String endpointId,
    String capabilityId
) {

    public TopologyTargetRef {
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(endpointId, "endpointId is required");
        Objects.requireNonNull(capabilityId, "capabilityId is required");
    }
}
