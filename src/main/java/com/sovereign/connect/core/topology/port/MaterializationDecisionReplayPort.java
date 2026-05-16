package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.materialization.MaterializationDecision;

import java.util.Optional;
import java.util.UUID;

public interface MaterializationDecisionReplayPort {
    Optional<MaterializationDecision> findDecision(String habitatId, UUID factId);

    void recordDecision(String habitatId, UUID factId, MaterializationDecision decision);
}
