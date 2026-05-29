package com.sovereign.connect.bus.contract;

public record ScCommandEnvelope<TCommand>(
        ScMessageMetadata metadata,
        TCommand payload,
        ScRoutingKey routingKey
) {
}
