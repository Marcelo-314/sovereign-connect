package com.sovereign.connect.core.topology.materialization;

import com.sovereign.connect.core.topology.event.TopologyChanged;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MaterializationDecision(
    UUID decisionId,
    UUID causationFactId,
    String habitatId,
    MaterializationDecisionKind kind,
    Optional<TopologyVersion> previousTopologyVersion,
    Optional<TopologyVersion> resultingTopologyVersion,
    List<TopologyChanged> emittedChanges,
    String reason
) {

    public MaterializationDecision {
        Objects.requireNonNull(decisionId, "decisionId is required");
        Objects.requireNonNull(causationFactId, "causationFactId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(kind, "kind is required");
        previousTopologyVersion = Objects.requireNonNull(previousTopologyVersion, "previousTopologyVersion is required");
        resultingTopologyVersion = Objects.requireNonNull(resultingTopologyVersion, "resultingTopologyVersion is required");
        emittedChanges = List.copyOf(Objects.requireNonNull(emittedChanges, "emittedChanges is required"));
        Objects.requireNonNull(reason, "reason is required");
    }
}
