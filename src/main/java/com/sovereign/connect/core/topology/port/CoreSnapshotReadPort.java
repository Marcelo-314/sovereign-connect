package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.util.Map;
import java.util.Optional;

public interface CoreSnapshotReadPort {

    Optional<BaseTopologySnapshot> findSnapshot(String habitatId);

    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    Optional<HabitatBaseTopology> findTopology(String habitatId);

    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);

    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);
}
