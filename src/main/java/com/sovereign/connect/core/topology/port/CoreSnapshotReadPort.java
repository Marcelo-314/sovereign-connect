package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.TopologySpatialEntityType;
import com.sovereign.connect.core.topology.model.TopologySpatialRelation;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CoreSnapshotReadPort {

    Optional<BaseTopologySnapshot> findSnapshot(String habitatId);

    Optional<TopologyVersion> findCurrentVersion(String habitatId);

    Optional<HabitatBaseTopology> findTopology(String habitatId);

    Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);

    Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId);

    Optional<RoomNode> findRoom(String habitatId, String roomId);

    Optional<ZoneNode> findZone(String habitatId, String zoneId);

    Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId);

    List<TopologySpatialRelation> findSpatialRelationsBySubject(
        String habitatId,
        TopologySpatialEntityType type,
        String id
    );

    List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId);

    List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId);

    Optional<TopologySpatialRelation> resolvePrimaryPlacement(
        String habitatId,
        TopologySpatialEntityType type,
        String id
    );
}
