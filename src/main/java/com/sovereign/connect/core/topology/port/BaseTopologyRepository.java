package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.util.Optional;

public interface BaseTopologyRepository {

    void save(HabitatBaseTopology topology);

    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);

    Optional<TopologyVersion> findCurrentVersion(String habitatId);
}
