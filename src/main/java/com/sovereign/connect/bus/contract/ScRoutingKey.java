package com.sovereign.connect.bus.contract;

public record ScRoutingKey(
        ScBusLane lane,
        String partitionKey,
        String topic,
        String habitatId,
        String adapterId,
        String deviceId,
        String endpointId
) {
}
