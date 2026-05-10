package com.sovereign.connect.core.topology.model;

import com.sovereign.connect.core.topology.event.TopologyChangeKind;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TopologyMutationRecord(
    UUID mutationId,
    String habitatId,
    TopologyVersion fromVersion,
    TopologyVersion toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    String reason,
    Instant acceptedAt
) {

    public TopologyMutationRecord {
        Objects.requireNonNull(mutationId, "mutationId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(fromVersion, "fromVersion is required");
        Objects.requireNonNull(toVersion, "toVersion is required");
        changeKinds = Set.copyOf(Objects.requireNonNull(changeKinds, "changeKinds is required"));
        affectedDeviceIds = List.copyOf(Objects.requireNonNull(affectedDeviceIds, "affectedDeviceIds is required"));
        affectedEndpointIds = List.copyOf(Objects.requireNonNull(affectedEndpointIds, "affectedEndpointIds is required"));
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(acceptedAt, "acceptedAt is required");
    }

    public static TopologyMutationRecord fromResult(
        TopologyMutationResult result,
        String reason,
        Instant acceptedAt
    ) {
        return new TopologyMutationRecord(
            UUID.randomUUID(),
            result.habitatId(),
            result.fromVersion(),
            result.toVersion(),
            result.changeKinds(),
            result.affectedDeviceIds(),
            result.affectedEndpointIds(),
            reason,
            acceptedAt
        );
    }
}
