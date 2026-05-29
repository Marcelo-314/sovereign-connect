package com.sovereign.connect.bus.runtime.validation;

import com.sovereign.connect.bus.contract.ScRoutingKey;

import java.util.Locale;
import java.util.Set;

public class RoutingKeyValidationService {
    private static final Set<String> ENDPOINT_SENTINELS = Set.of("", "none", "default");

    public void validate(ScRoutingKey routingKey) {
        if (routingKey == null) {
            throw new IllegalArgumentException("routingKey is required");
        }
        if (routingKey.lane() == null) {
            throw new IllegalArgumentException("lane is required");
        }
        if (isBlank(routingKey.topic())) {
            throw new IllegalArgumentException("topic is required");
        }
        if (isBlank(routingKey.partitionKey())) {
            throw new IllegalArgumentException("partitionKey is required");
        }
        String endpointId = routingKey.endpointId();
        if (endpointId != null) {
            if (ENDPOINT_SENTINELS.contains(endpointId.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("endpointId sentinel is not allowed");
            }
            if (!endpointId.equals(routingKey.partitionKey())) {
                throw new IllegalArgumentException("endpointId must be the partitionKey when present");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
