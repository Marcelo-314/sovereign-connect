package com.sovereign.connect.core.topology.model;

import com.sovereign.connect.core.topology.event.TopologyChangeKind;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record TopologyMutationResult(
    String habitatId,
    TopologyVersion fromVersion,
    TopologyVersion toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    List<String> affectedRoomIds,
    List<String> affectedZoneIds
) {

    public TopologyMutationResult(
        String habitatId,
        TopologyVersion fromVersion,
        TopologyVersion toVersion,
        Set<TopologyChangeKind> changeKinds,
        List<String> affectedDeviceIds,
        List<String> affectedEndpointIds
    ) {
        this(habitatId, fromVersion, toVersion, changeKinds, affectedDeviceIds, affectedEndpointIds, List.of(), List.of());
    }

    public TopologyMutationResult {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(fromVersion, "fromVersion is required");
        Objects.requireNonNull(toVersion, "toVersion is required");
        changeKinds = Set.copyOf(Objects.requireNonNull(changeKinds, "changeKinds is required"));
        affectedDeviceIds = List.copyOf(Objects.requireNonNull(affectedDeviceIds, "affectedDeviceIds is required"));
        affectedEndpointIds = List.copyOf(Objects.requireNonNull(affectedEndpointIds, "affectedEndpointIds is required"));
        affectedRoomIds = List.copyOf(Objects.requireNonNull(affectedRoomIds, "affectedRoomIds is required"));
        affectedZoneIds = List.copyOf(Objects.requireNonNull(affectedZoneIds, "affectedZoneIds is required"));
    }
}
