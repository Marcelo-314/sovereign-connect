package com.sovereign.connect.core.topology.materialization;

public interface TopologyMaterializationService {

    MaterializationDecision materialize(String habitatId, TopologyFact fact);
}
