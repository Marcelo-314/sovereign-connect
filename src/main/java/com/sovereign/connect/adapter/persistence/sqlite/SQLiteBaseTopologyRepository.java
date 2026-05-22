package com.sovereign.connect.adapter.persistence.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
import com.sovereign.connect.core.topology.model.CapabilityKind;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.CapabilityTraits;
import com.sovereign.connect.core.topology.model.DeviceHealth;
import com.sovereign.connect.core.topology.model.DeviceKind;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.DeviceProvider;
import com.sovereign.connect.core.topology.model.DeviceTraits;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointKind;
import com.sovereign.connect.core.topology.model.EndpointMetadata;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.EndpointTraits;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.ProviderDeviceRef;
import com.sovereign.connect.core.topology.model.ProviderEndpointRef;
import com.sovereign.connect.core.topology.model.ProviderSpatialRef;
import com.sovereign.connect.core.topology.model.RelationConfidence;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.SpatialRelationSource;
import com.sovereign.connect.core.topology.model.TopologyMetadata;
import com.sovereign.connect.core.topology.model.TopologyMutationRecord;
import com.sovereign.connect.core.topology.model.TopologySpatialEntityType;
import com.sovereign.connect.core.topology.model.TopologySpatialRelation;
import com.sovereign.connect.core.topology.model.TopologySpatialRelationKind;
import com.sovereign.connect.core.topology.model.TopologySpatialSubject;
import com.sovereign.connect.core.topology.model.TopologySpatialTarget;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.port.CoreSnapshotReadPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SQLiteBaseTopologyRepository implements BaseTopologyRepository, CoreSnapshotReadPort {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Set<TopologyChangeKind>> CHANGE_KIND_SET_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SQLiteBaseTopologyRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        Clock clock
    ) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public void save(HabitatBaseTopology topology) {
        Objects.requireNonNull(topology, "topology is required");
        long nowMs = Instant.now(clock).toEpochMilli();
        String habitatId = topology.habitatId();
        String versionValue = topology.topologyVersion().value();

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                """
                    INSERT OR IGNORE INTO habitats(habitat_id)
                    VALUES (?)
                    """,
                habitatId
            );
            jdbcTemplate.update(
                """
                    INSERT INTO topology_versions (habitat_id, version_value, updated_at_ms)
                    VALUES (?, ?, ?)
                    ON CONFLICT(habitat_id) DO UPDATE SET
                      version_value = excluded.version_value,
                      updated_at_ms = excluded.updated_at_ms
                    """,
                habitatId,
                versionValue,
                nowMs
            );
            jdbcTemplate.update(
                """
                    INSERT INTO topology_metadata
                      (habitat_id, schema_version, last_modified_ms, source, checksum)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(habitat_id) DO UPDATE SET
                      schema_version = excluded.schema_version,
                      last_modified_ms = excluded.last_modified_ms,
                      source = excluded.source,
                      checksum = excluded.checksum
                    """,
                habitatId,
                topology.metadata().schemaVersion(),
                topology.metadata().lastModified().toEpochMilli(),
                topology.metadata().source(),
                topology.metadata().checksum()
            );
            deleteStructuralRows(habitatId);
            insertRooms(topology);
            insertZones(topology);
            insertDevices(topology);
            insertEndpoints(topology);
            insertCapabilities(topology);
            insertSpatialRelations(topology);
            jdbcTemplate.update(
                """
                    INSERT INTO topology_snapshots
                      (habitat_id, topology_version, topology_json, captured_at_ms)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(habitat_id) DO UPDATE SET
                      topology_version = excluded.topology_version,
                      topology_json = excluded.topology_json,
                      captured_at_ms = excluded.captured_at_ms
                    """,
                habitatId,
                versionValue,
                writeJson(topology),
                nowMs
            );
        });
    }

    @Override
    public Optional<HabitatBaseTopology> findByHabitatId(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Optional<TopologyVersion> version = findCurrentVersion(habitatId);
        if (version.isEmpty()) {
            return Optional.empty();
        }
        TopologyMetadata metadata = loadMetadata(habitatId);
        List<RoomRow> roomRows = loadRoomRows(habitatId);
        List<ZoneRow> zoneRows = loadZoneRows(habitatId);
        List<DeviceRow> deviceRows = loadDeviceRows(habitatId);
        List<EndpointRow> endpointRows = loadEndpointRows(habitatId);
        Map<String, List<CapabilityNode>> capsByOwner = loadCapabilitiesGroupedByOwner(habitatId);
        Map<String, EndpointHealth> healthByEndpoint = loadEndpointHealthMap(habitatId);
        List<TopologySpatialRelation> relations = loadSpatialRelations(habitatId);

        List<RoomNode> rooms = roomRows.stream()
            .map(row -> new RoomNode(
                row.roomId(),
                row.roomName(),
                zoneRows.stream().filter(zone -> row.roomId().equals(zone.roomId())).map(ZoneRow::zoneId).toList(),
                deviceRows.stream().filter(device -> row.roomId().equals(device.roomId())).map(DeviceRow::deviceId).toList(),
                endpointRows.stream().filter(endpoint -> row.roomId().equals(endpoint.roomId())).map(EndpointRow::endpointId).toList(),
                row.traits()
            ))
            .toList();
        List<ZoneNode> zones = zoneRows.stream()
            .map(row -> new ZoneNode(
                row.zoneId(),
                row.zoneName(),
                row.roomId(),
                deviceRows.stream().filter(device -> row.zoneId().equals(device.zoneId())).map(DeviceRow::deviceId).toList(),
                endpointRows.stream().filter(endpoint -> row.zoneId().equals(endpoint.zoneId())).map(EndpointRow::endpointId).toList(),
                row.traits()
            ))
            .toList();
        List<DeviceNode> devices = deviceRows.stream()
            .map(row -> assembleDevice(row, endpointRows, capsByOwner))
            .toList();
        EndpointHealth unknownHealth = new EndpointHealth(HealthStatus.UNKNOWN, null, "no durable endpoint health row");
        List<EndpointNode> endpoints = endpointRows.stream()
            .map(row -> assembleEndpoint(row, capsByOwner, healthByEndpoint.getOrDefault(row.endpointId(), unknownHealth)))
            .toList();

        return Optional.of(new HabitatBaseTopology(
            habitatId,
            version.get(),
            rooms,
            zones,
            devices,
            endpoints,
            relations,
            metadata
        ));
    }

    @Override
    public Optional<TopologyVersion> findCurrentVersion(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        return jdbcTemplate.query(
            "SELECT version_value FROM topology_versions WHERE habitat_id = ?",
            (rs, rowNum) -> TopologyVersion.habitatVersion(habitatId, Long.parseLong(rs.getString("version_value"))),
            habitatId
        ).stream().findFirst();
    }

    @Override
    public Optional<BaseTopologySnapshot> findSnapshot(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Optional<HabitatBaseTopology> topology = findByHabitatId(habitatId);
        if (topology.isEmpty()) {
            return Optional.empty();
        }
        long capturedAtMs = jdbcTemplate.query(
            "SELECT captured_at_ms FROM topology_snapshots WHERE habitat_id = ?",
            (rs, rowNum) -> rs.getLong("captured_at_ms"),
            habitatId
        ).stream().findFirst().orElse(Instant.now(clock).toEpochMilli());
        return Optional.of(new BaseTopologySnapshot(
            habitatId,
            topology.get().topologyVersion(),
            topology.get(),
            Instant.ofEpochMilli(capturedAtMs)
        ));
    }

    @Override
    public Optional<HabitatBaseTopology> findTopology(String habitatId) {
        return findByHabitatId(habitatId);
    }

    @Override
    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        return jdbcTemplate.query(
            "SELECT state_json FROM device_states WHERE habitat_id = ? AND device_id = ?",
            (rs, rowNum) -> readJson(rs.getString("state_json"), STATE_TYPE),
            habitatId,
            deviceId
        ).stream().findFirst();
    }

    @Override
    public Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(endpointId, "endpointId is required");
        return jdbcTemplate.query(
            """
                SELECT status, last_seen_at_ms, details
                FROM endpoint_health
                WHERE habitat_id = ? AND endpoint_id = ?
                """,
            (rs, rowNum) -> toEndpointHealth(rs),
            habitatId,
            endpointId
        ).stream().findFirst();
    }

    @Override
    public Optional<RoomNode> findRoom(String habitatId, String roomId) {
        List<RoomRow> rows = loadRoomRows(habitatId, roomId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        RoomRow row = rows.get(0);
        return Optional.of(new RoomNode(
            row.roomId(),
            row.roomName(),
            jdbcTemplate.queryForList("SELECT zone_id FROM zones WHERE habitat_id = ? AND room_id = ? ORDER BY zone_id", String.class, habitatId, roomId),
            jdbcTemplate.queryForList("SELECT device_id FROM devices WHERE habitat_id = ? AND room_id = ? ORDER BY device_id", String.class, habitatId, roomId),
            jdbcTemplate.queryForList("SELECT endpoint_id FROM endpoints WHERE habitat_id = ? AND room_id = ? ORDER BY endpoint_id", String.class, habitatId, roomId),
            row.traits()
        ));
    }

    @Override
    public Optional<ZoneNode> findZone(String habitatId, String zoneId) {
        List<ZoneRow> rows = loadZoneRows(habitatId, zoneId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ZoneRow row = rows.get(0);
        return Optional.of(new ZoneNode(
            row.zoneId(),
            row.zoneName(),
            row.roomId(),
            jdbcTemplate.queryForList("SELECT device_id FROM devices WHERE habitat_id = ? AND zone_id = ? ORDER BY device_id", String.class, habitatId, zoneId),
            jdbcTemplate.queryForList("SELECT endpoint_id FROM endpoints WHERE habitat_id = ? AND zone_id = ? ORDER BY endpoint_id", String.class, habitatId, zoneId),
            row.traits()
        ));
    }

    @Override
    public Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId) {
        return jdbcTemplate.query(
            "SELECT * FROM topology_spatial_relations WHERE habitat_id = ? AND relation_id = ?",
            (rs, rowNum) -> toRelation(rs),
            habitatId,
            relationId
        ).stream().findFirst();
    }

    @Override
    public List<TopologySpatialRelation> findSpatialRelationsBySubject(
        String habitatId,
        TopologySpatialEntityType type,
        String id
    ) {
        return jdbcTemplate.query(
            """
                SELECT * FROM topology_spatial_relations
                WHERE habitat_id = ? AND subject_type = ? AND subject_id = ?
                ORDER BY relation_id
                """,
            (rs, rowNum) -> toRelation(rs),
            habitatId,
            type.name(),
            id
        );
    }

    @Override
    public List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId) {
        Set<String> deviceIds = new LinkedHashSet<>();
        deviceIds.addAll(locatedSubjectIds(habitatId, TopologySpatialEntityType.DEVICE, roomOrZoneId));
        deviceIds.addAll(directlyLocatedDeviceIds(habitatId, roomOrZoneId));
        List<EndpointRow> endpointRows = loadEndpointRows(habitatId);
        Map<String, List<CapabilityNode>> capsByOwner = loadCapabilitiesGroupedByOwner(habitatId);
        return deviceIds.stream()
            .map(deviceId -> loadDeviceRow(habitatId, deviceId))
            .flatMap(Optional::stream)
            .map(row -> assembleDevice(row, endpointRows, capsByOwner))
            .toList();
    }

    @Override
    public List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId) {
        Set<String> endpointIds = new LinkedHashSet<>();
        endpointIds.addAll(locatedSubjectIds(habitatId, TopologySpatialEntityType.ENDPOINT, roomOrZoneId));
        endpointIds.addAll(directlyLocatedEndpointIds(habitatId, roomOrZoneId));
        Map<String, List<CapabilityNode>> capsByOwner = loadCapabilitiesGroupedByOwner(habitatId);
        Map<String, EndpointHealth> healthByEndpoint = loadEndpointHealthMap(habitatId);
        EndpointHealth unknownHealth = new EndpointHealth(HealthStatus.UNKNOWN, null, "no durable endpoint health row");
        return endpointIds.stream()
            .map(endpointId -> loadEndpointRow(habitatId, endpointId))
            .flatMap(Optional::stream)
            .map(row -> assembleEndpoint(row, capsByOwner, healthByEndpoint.getOrDefault(row.endpointId(), unknownHealth)))
            .toList();
    }

    @Override
    public Optional<TopologySpatialRelation> resolvePrimaryPlacement(
        String habitatId,
        TopologySpatialEntityType type,
        String id
    ) {
        return jdbcTemplate.query(
            """
                SELECT * FROM topology_spatial_relations
                WHERE habitat_id = ? AND subject_type = ? AND subject_id = ? AND is_primary = 1
                ORDER BY relation_id
                """,
            (rs, rowNum) -> toRelation(rs),
            habitatId,
            type.name(),
            id
        ).stream().findFirst();
    }

    public void appendMutationRecord(TopologyMutationRecord record) {
        Objects.requireNonNull(record, "record is required");
        jdbcTemplate.update(
            """
                INSERT INTO mutation_records
                  (mutation_id, habitat_id, from_version, to_version, change_kinds_json,
                   affected_device_ids_json, affected_endpoint_ids_json, reason, accepted_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            record.mutationId().toString(),
            record.habitatId(),
            record.fromVersion().value(),
            record.toVersion().value(),
            writeJson(record.changeKinds()),
            writeJson(record.affectedDeviceIds()),
            writeJson(record.affectedEndpointIds()),
            record.reason(),
            record.acceptedAt().toEpochMilli()
        );
    }

    public List<TopologyMutationRecord> findMutationRecords(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        return jdbcTemplate.query(
            """
                SELECT mutation_id, habitat_id, from_version, to_version, change_kinds_json,
                       affected_device_ids_json, affected_endpoint_ids_json, reason, accepted_at_ms
                FROM mutation_records
                WHERE habitat_id = ?
                ORDER BY accepted_at_ms ASC
                """,
            (rs, rowNum) -> toMutationRecord(rs),
            habitatId
        );
    }

    private void deleteStructuralRows(String habitatId) {
        jdbcTemplate.update("DELETE FROM topology_spatial_relations WHERE habitat_id = ?", habitatId);
        jdbcTemplate.update("DELETE FROM capabilities WHERE habitat_id = ?", habitatId);
        jdbcTemplate.update("DELETE FROM endpoints WHERE habitat_id = ?", habitatId);
        jdbcTemplate.update("DELETE FROM devices WHERE habitat_id = ?", habitatId);
        jdbcTemplate.update("DELETE FROM zones WHERE habitat_id = ?", habitatId);
        jdbcTemplate.update("DELETE FROM rooms WHERE habitat_id = ?", habitatId);
    }

    private void insertRooms(HabitatBaseTopology topology) {
        for (RoomNode room : topology.rooms()) {
            jdbcTemplate.update(
                "INSERT INTO rooms (habitat_id, room_id, room_name, traits_json) VALUES (?, ?, ?, ?)",
                topology.habitatId(),
                room.roomId(),
                room.roomName(),
                writeJson(room.traits())
            );
        }
    }

    private void insertZones(HabitatBaseTopology topology) {
        for (ZoneNode zone : topology.zones()) {
            jdbcTemplate.update(
                "INSERT INTO zones (habitat_id, zone_id, zone_name, room_id, traits_json) VALUES (?, ?, ?, ?, ?)",
                topology.habitatId(),
                zone.zoneId(),
                zone.zoneName(),
                zone.roomId(),
                writeJson(zone.traits())
            );
        }
    }

    private void insertDevices(HabitatBaseTopology topology) {
        for (DeviceNode device : topology.devices()) {
            jdbcTemplate.update(
                """
                    INSERT INTO devices
                      (habitat_id, device_id, alias, display_name, room_id, zone_id, kind, provider,
                       traits_json, device_health_status, device_health_last_seen_ms,
                       device_health_details, provider_ref_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                topology.habitatId(),
                device.deviceId(),
                device.alias(),
                device.displayName(),
                device.roomId(),
                device.zoneId(),
                device.kind().name(),
                device.provider().name(),
                writeJson(device.traits()),
                device.health().status().name(),
                device.health().lastSeenAt() == null ? null : device.health().lastSeenAt().toEpochMilli(),
                device.health().details(),
                writeJson(device.providerRef())
            );
        }
    }

    private void insertEndpoints(HabitatBaseTopology topology) {
        for (EndpointNode endpoint : topology.endpoints()) {
            jdbcTemplate.update(
                """
                    INSERT INTO endpoints
                      (habitat_id, endpoint_id, device_id, alias, display_name, kind,
                       room_id, zone_id, traits_json, provider_ref_json, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                topology.habitatId(),
                endpoint.endpointId(),
                endpoint.deviceId(),
                endpoint.alias(),
                endpoint.displayName(),
                endpoint.kind().name(),
                endpoint.roomId(),
                endpoint.zoneId(),
                writeJson(endpoint.traits()),
                writeJson(endpoint.providerRef()),
                writeJson(endpoint.metadata())
            );
            seedEndpointHealthIfAbsent(topology.habitatId(), endpoint);
        }
    }

    private void seedEndpointHealthIfAbsent(String habitatId, EndpointNode endpoint) {
        EndpointHealth health = endpoint.health();
        if (health == null) {
            health = new EndpointHealth(HealthStatus.UNKNOWN, null, "seeded default endpoint health");
        }
        jdbcTemplate.update(
            """
                INSERT OR IGNORE INTO endpoint_health
                  (habitat_id, endpoint_id, status, last_seen_at_ms, details, updated_at_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            habitatId,
            endpoint.endpointId(),
            health.status().name(),
            health.lastSeenAt() == null ? null : health.lastSeenAt().toEpochMilli(),
            health.details(),
            Instant.now(clock).toEpochMilli()
        );
    }

    private void insertCapabilities(HabitatBaseTopology topology) {
        for (DeviceNode device : topology.devices()) {
            for (CapabilityNode capability : device.deviceCapabilities()) {
                insertCapability(topology.habitatId(), "DEVICE", device.deviceId(), capability);
            }
        }
        for (EndpointNode endpoint : topology.endpoints()) {
            for (CapabilityNode capability : endpoint.capabilities()) {
                insertCapability(topology.habitatId(), "ENDPOINT", endpoint.endpointId(), capability);
            }
        }
    }

    private void insertCapability(String habitatId, String ownerKind, String ownerId, CapabilityNode capability) {
        jdbcTemplate.update(
            """
                INSERT INTO capabilities
                  (habitat_id, capability_id, owner_kind, owner_id, name, kind, traits_json)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
            habitatId,
            capability.capabilityId(),
            ownerKind,
            ownerId,
            capability.name(),
            capability.kind().name(),
            writeJson(capability.traits())
        );
    }

    private void insertSpatialRelations(HabitatBaseTopology topology) {
        for (TopologySpatialRelation relation : topology.spatialRelations()) {
            jdbcTemplate.update(
                """
                    INSERT INTO topology_spatial_relations
                      (habitat_id, relation_id, kind, subject_type, subject_id,
                       target_type, target_id, is_primary, confidence, source,
                       provider_spatial_ref_json, observed_at_ms, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                topology.habitatId(),
                relation.relationId(),
                relation.kind().name(),
                relation.subject().type().name(),
                relation.subject().id(),
                relation.target().type().name(),
                relation.target().id(),
                relation.primary() ? 1 : 0,
                relation.confidence().name(),
                relation.source().name(),
                relation.providerRef() == null ? null : writeJson(relation.providerRef()),
                relation.observedAt() == null ? null : relation.observedAt().toEpochMilli(),
                writeJson(relation.metadata() == null ? Map.of() : relation.metadata())
            );
        }
    }

    private TopologyMetadata loadMetadata(String habitatId) {
        return jdbcTemplate.query(
            """
                SELECT schema_version, last_modified_ms, source, checksum
                FROM topology_metadata
                WHERE habitat_id = ?
                """,
            (rs, rowNum) -> new TopologyMetadata(
                rs.getString("schema_version"),
                Instant.ofEpochMilli(rs.getLong("last_modified_ms")),
                rs.getString("source"),
                rs.getString("checksum")
            ),
            habitatId
        ).stream().findFirst().orElse(new TopologyMetadata("base-topology.seed.v1", Instant.now(clock), "SC-C", null));
    }

    private List<RoomRow> loadRoomRows(String habitatId) {
        return loadRoomRows(habitatId, null);
    }

    private List<RoomRow> loadRoomRows(String habitatId, String roomId) {
        String sql = "SELECT room_id, room_name, traits_json FROM rooms WHERE habitat_id = ?"
            + (roomId == null ? " ORDER BY room_id" : " AND room_id = ? ORDER BY room_id");
        Object[] args = roomId == null ? new Object[] {habitatId} : new Object[] {habitatId, roomId};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RoomRow(
            rs.getString("room_id"),
            rs.getString("room_name"),
            readJson(rs.getString("traits_json"), RoomTraits.class)
        ), args);
    }

    private List<ZoneRow> loadZoneRows(String habitatId) {
        return loadZoneRows(habitatId, null);
    }

    private List<ZoneRow> loadZoneRows(String habitatId, String zoneId) {
        String sql = "SELECT zone_id, zone_name, room_id, traits_json FROM zones WHERE habitat_id = ?"
            + (zoneId == null ? " ORDER BY zone_id" : " AND zone_id = ? ORDER BY zone_id");
        Object[] args = zoneId == null ? new Object[] {habitatId} : new Object[] {habitatId, zoneId};
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ZoneRow(
            rs.getString("zone_id"),
            rs.getString("zone_name"),
            rs.getString("room_id"),
            readJson(rs.getString("traits_json"), ZoneTraits.class)
        ), args);
    }

    private List<DeviceRow> loadDeviceRows(String habitatId) {
        return jdbcTemplate.query(
            "SELECT * FROM devices WHERE habitat_id = ? ORDER BY device_id",
            (rs, rowNum) -> toDeviceRow(rs),
            habitatId
        );
    }

    private Optional<DeviceRow> loadDeviceRow(String habitatId, String deviceId) {
        return jdbcTemplate.query(
            "SELECT * FROM devices WHERE habitat_id = ? AND device_id = ?",
            (rs, rowNum) -> toDeviceRow(rs),
            habitatId,
            deviceId
        ).stream().findFirst();
    }

    private List<EndpointRow> loadEndpointRows(String habitatId) {
        return jdbcTemplate.query(
            "SELECT * FROM endpoints WHERE habitat_id = ? ORDER BY endpoint_id",
            (rs, rowNum) -> toEndpointRow(rs),
            habitatId
        );
    }

    private Optional<EndpointRow> loadEndpointRow(String habitatId, String endpointId) {
        return jdbcTemplate.query(
            "SELECT * FROM endpoints WHERE habitat_id = ? AND endpoint_id = ?",
            (rs, rowNum) -> toEndpointRow(rs),
            habitatId,
            endpointId
        ).stream().findFirst();
    }

    private Map<String, List<CapabilityNode>> loadCapabilitiesGroupedByOwner(String habitatId) {
        return jdbcTemplate.query(
            "SELECT * FROM capabilities WHERE habitat_id = ? ORDER BY capability_id",
            rs -> {
                Map<String, List<CapabilityNode>> grouped = new LinkedHashMap<>();
                while (rs.next()) {
                    String key = rs.getString("owner_kind") + ":" + rs.getString("owner_id");
                    CapabilityNode capability = new CapabilityNode(
                        rs.getString("capability_id"),
                        rs.getString("name"),
                        CapabilityKind.valueOf(rs.getString("kind")),
                        readJson(rs.getString("traits_json"), CapabilityTraits.class)
                    );
                    grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(capability);
                }
                return grouped;
            },
            habitatId
        );
    }

    private Map<String, EndpointHealth> loadEndpointHealthMap(String habitatId) {
        return jdbcTemplate.query(
            "SELECT endpoint_id, status, last_seen_at_ms, details FROM endpoint_health WHERE habitat_id = ?",
            rs -> {
                Map<String, EndpointHealth> values = new LinkedHashMap<>();
                while (rs.next()) {
                    values.put(rs.getString("endpoint_id"), toEndpointHealth(rs));
                }
                return values;
            },
            habitatId
        );
    }

    private List<TopologySpatialRelation> loadSpatialRelations(String habitatId) {
        return jdbcTemplate.query(
            "SELECT * FROM topology_spatial_relations WHERE habitat_id = ? ORDER BY relation_id",
            (rs, rowNum) -> toRelation(rs),
            habitatId
        );
    }

    private List<String> locatedSubjectIds(String habitatId, TopologySpatialEntityType subjectType, String roomOrZoneId) {
        return jdbcTemplate.queryForList(
            """
                SELECT subject_id
                FROM topology_spatial_relations
                WHERE habitat_id = ? AND kind = 'LOCATED_IN'
                  AND subject_type = ? AND target_id = ?
                ORDER BY subject_id
                """,
            String.class,
            habitatId,
            subjectType.name(),
            roomOrZoneId
        );
    }

    private List<String> directlyLocatedDeviceIds(String habitatId, String roomOrZoneId) {
        return jdbcTemplate.queryForList(
            """
                SELECT device_id
                FROM devices
                WHERE habitat_id = ?
                  AND (room_id = ? OR zone_id = ?)
                ORDER BY device_id
                """,
            String.class,
            habitatId,
            roomOrZoneId,
            roomOrZoneId
        );
    }

    private List<String> directlyLocatedEndpointIds(String habitatId, String roomOrZoneId) {
        return jdbcTemplate.queryForList(
            """
                SELECT endpoint_id
                FROM endpoints
                WHERE habitat_id = ?
                  AND (room_id = ? OR zone_id = ?)
                ORDER BY endpoint_id
                """,
            String.class,
            habitatId,
            roomOrZoneId,
            roomOrZoneId
        );
    }

    private DeviceNode assembleDevice(
        DeviceRow row,
        List<EndpointRow> endpointRows,
        Map<String, List<CapabilityNode>> capsByOwner
    ) {
        return new DeviceNode(
            row.deviceId(),
            row.alias(),
            row.displayName(),
            row.roomId(),
            row.zoneId(),
            row.kind(),
            row.provider(),
            endpointRows.stream().filter(endpoint -> row.deviceId().equals(endpoint.deviceId())).map(EndpointRow::endpointId).toList(),
            capsByOwner.getOrDefault("DEVICE:" + row.deviceId(), List.of()),
            row.traits(),
            row.health(),
            row.providerRef()
        );
    }

    private EndpointNode assembleEndpoint(
        EndpointRow row,
        Map<String, List<CapabilityNode>> capsByOwner,
        EndpointHealth health
    ) {
        return new EndpointNode(
            row.endpointId(),
            row.deviceId(),
            row.alias(),
            row.displayName(),
            row.kind(),
            row.roomId(),
            row.zoneId(),
            capsByOwner.getOrDefault("ENDPOINT:" + row.endpointId(), List.of()),
            row.traits(),
            health,
            row.providerRef(),
            row.metadata()
        );
    }

    private DeviceRow toDeviceRow(ResultSet rs) throws SQLException {
        long lastSeenAtMs = rs.getLong("device_health_last_seen_ms");
        boolean lastSeenAtWasNull = rs.wasNull();
        return new DeviceRow(
            rs.getString("device_id"),
            rs.getString("alias"),
            rs.getString("display_name"),
            rs.getString("room_id"),
            rs.getString("zone_id"),
            DeviceKind.valueOf(rs.getString("kind")),
            DeviceProvider.valueOf(rs.getString("provider")),
            readJson(rs.getString("traits_json"), DeviceTraits.class),
            new DeviceHealth(
                HealthStatus.valueOf(rs.getString("device_health_status")),
                lastSeenAtWasNull ? null : Instant.ofEpochMilli(lastSeenAtMs),
                rs.getString("device_health_details")
            ),
            readJson(rs.getString("provider_ref_json"), ProviderDeviceRef.class)
        );
    }

    private EndpointRow toEndpointRow(ResultSet rs) throws SQLException {
        return new EndpointRow(
            rs.getString("endpoint_id"),
            rs.getString("device_id"),
            rs.getString("alias"),
            rs.getString("display_name"),
            EndpointKind.valueOf(rs.getString("kind")),
            rs.getString("room_id"),
            rs.getString("zone_id"),
            readJson(rs.getString("traits_json"), EndpointTraits.class),
            readJson(rs.getString("provider_ref_json"), ProviderEndpointRef.class),
            readJson(rs.getString("metadata_json"), EndpointMetadata.class)
        );
    }

    private EndpointHealth toEndpointHealth(ResultSet rs) throws SQLException {
        long lastSeenAtMs = rs.getLong("last_seen_at_ms");
        boolean lastSeenAtWasNull = rs.wasNull();
        return new EndpointHealth(
            HealthStatus.valueOf(rs.getString("status")),
            lastSeenAtWasNull ? null : Instant.ofEpochMilli(lastSeenAtMs),
            rs.getString("details")
        );
    }

    private TopologySpatialRelation toRelation(ResultSet rs) throws SQLException {
        long observedAtMs = rs.getLong("observed_at_ms");
        boolean observedAtWasNull = rs.wasNull();
        String providerRef = rs.getString("provider_spatial_ref_json");
        return new TopologySpatialRelation(
            rs.getString("relation_id"),
            TopologySpatialRelationKind.valueOf(rs.getString("kind")),
            new TopologySpatialSubject(
                TopologySpatialEntityType.valueOf(rs.getString("subject_type")),
                rs.getString("subject_id")
            ),
            new TopologySpatialTarget(
                TopologySpatialEntityType.valueOf(rs.getString("target_type")),
                rs.getString("target_id")
            ),
            rs.getInt("is_primary") == 1,
            RelationConfidence.valueOf(rs.getString("confidence")),
            SpatialRelationSource.valueOf(rs.getString("source")),
            providerRef == null ? null : readJson(providerRef, ProviderSpatialRef.class),
            observedAtWasNull ? null : Instant.ofEpochMilli(observedAtMs),
            readJson(rs.getString("metadata_json"), new TypeReference<Map<String, String>>() {
            })
        );
    }

    private TopologyMutationRecord toMutationRecord(ResultSet rs) throws SQLException {
        String habitatId = rs.getString("habitat_id");
        return new TopologyMutationRecord(
            UUID.fromString(rs.getString("mutation_id")),
            habitatId,
            TopologyVersion.habitatVersion(habitatId, Long.parseLong(rs.getString("from_version"))),
            TopologyVersion.habitatVersion(habitatId, Long.parseLong(rs.getString("to_version"))),
            readJson(rs.getString("change_kinds_json"), CHANGE_KIND_SET_TYPE),
            sorted(readJson(rs.getString("affected_device_ids_json"), STRING_LIST_TYPE)),
            sorted(readJson(rs.getString("affected_endpoint_ids_json"), STRING_LIST_TYPE)),
            rs.getString("reason"),
            Instant.ofEpochMilli(rs.getLong("accepted_at_ms"))
        );
    }

    private List<String> sorted(List<String> values) {
        return values.stream().sorted(Comparator.naturalOrder()).toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize " + value.getClass().getSimpleName(), ex);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize " + type.getSimpleName(), ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize json", ex);
        }
    }

    @SuppressWarnings("unused")
    private Set<TopologyChangeKind> parseLegacyChangeKinds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .map(TopologyChangeKind::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    private record RoomRow(String roomId, String roomName, RoomTraits traits) {
    }

    private record ZoneRow(String zoneId, String zoneName, String roomId, ZoneTraits traits) {
    }

    private record DeviceRow(
        String deviceId,
        String alias,
        String displayName,
        String roomId,
        String zoneId,
        DeviceKind kind,
        DeviceProvider provider,
        DeviceTraits traits,
        DeviceHealth health,
        ProviderDeviceRef providerRef
    ) {
    }

    private record EndpointRow(
        String endpointId,
        String deviceId,
        String alias,
        String displayName,
        EndpointKind kind,
        String roomId,
        String zoneId,
        EndpointTraits traits,
        ProviderEndpointRef providerRef,
        EndpointMetadata metadata
    ) {
    }
}
