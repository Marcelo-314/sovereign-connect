package com.sovereign.connect.core.topology.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TopologyChanged(
    UUID eventId,
    Instant timestamp,
    String habitatId,
    String fromVersion,
    String toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    String reason
) {

    public TopologyChanged {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(timestamp, "timestamp is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(fromVersion, "fromVersion is required");
        Objects.requireNonNull(toVersion, "toVersion is required");
        changeKinds = Set.copyOf(Objects.requireNonNull(changeKinds, "changeKinds is required"));
        affectedDeviceIds = List.copyOf(Objects.requireNonNull(affectedDeviceIds, "affectedDeviceIds is required"));
        affectedEndpointIds = List.copyOf(Objects.requireNonNull(affectedEndpointIds, "affectedEndpointIds is required"));
        Objects.requireNonNull(reason, "reason is required");
    }
}
