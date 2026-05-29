package com.sovereign.connect.bus.contract;

public record ScEventEnvelope<TEvent>(
        ScMessageMetadata metadata,
        TEvent payload,
        ScRoutingKey routingKey
) {
}
