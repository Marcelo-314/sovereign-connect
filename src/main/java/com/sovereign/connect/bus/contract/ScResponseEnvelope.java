package com.sovereign.connect.bus.contract;

public record ScResponseEnvelope<TResponse>(
        ScMessageMetadata metadata,
        TResponse payload,
        ScRoutingKey routingKey,
        ScResponseMetadata responseMetadata
) {
}
