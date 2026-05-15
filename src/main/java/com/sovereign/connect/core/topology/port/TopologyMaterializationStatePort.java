package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;

import java.util.Map;
import java.util.Optional;

public interface TopologyMaterializationStatePort {

    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);

    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state);

    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);
}
