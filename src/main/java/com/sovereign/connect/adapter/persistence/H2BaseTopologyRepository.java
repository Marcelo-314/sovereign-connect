package com.sovereign.connect.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.event.TopologyChanged;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.TopologySpatialEntityType;
import com.sovereign.connect.core.topology.model.TopologySpatialRelation;
import com.sovereign.connect.core.topology.model.TopologySpatialRelationKind;
import com.sovereign.connect.core.topology.model.TopologyMutationRecord;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.port.CoreSnapshotReadPort;
import com.sovereign.connect.core.topology.port.EndpointHealthWritePort;
import com.sovereign.connect.core.topology.port.MaterializationDecisionReplayPort;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class H2BaseTopologyRepository implements BaseTopologyRepository, CoreSnapshotReadPort, EndpointHealthWritePort, TopologyMaterializationStatePort, MaterializationDecisionReplayPort, ScLedgerWritePort, ScOutboxWritePort {

    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<TopologyChanged>> TOPOLOGY_CHANGED_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public H2BaseTopologyRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
    }

    public H2BaseTopologyRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        createSchema();
    }

    @Override
    public void save(HabitatBaseTopology topology) {
        Objects.requireNonNull(topology, "topology is required");
        String topologyJson = writeJson(topology);
        Instant capturedAt = Instant.now(clock);
        jdbcTemplate.update(
            """
                MERGE INTO topology_snapshots
                (habitat_id, topology_version, topology_json, captured_at)
                KEY (habitat_id)
                VALUES (?, ?, ?, ?)
                """,
            topology.habitatId(),
            topology.topologyVersion().value(),
            topologyJson,
            Timestamp.from(capturedAt)
        );
    }

    @Override
    public Optional<HabitatBaseTopology> findByHabitatId(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        List<HabitatBaseTopology> results = jdbcTemplate.query(
            "SELECT topology_json FROM topology_snapshots WHERE habitat_id = ?",
            (rs, rowNum) -> readJson(rs.getString("topology_json"), HabitatBaseTopology.class),
            habitatId
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<HabitatBaseTopology> findTopology(String habitatId) {
        return findByHabitatId(habitatId);
    }

    @Override
    public Optional<TopologyVersion> findCurrentVersion(String habitatId) {
        return findSnapshot(habitatId).map(BaseTopologySnapshot::topologyVersion);
    }

    public Optional<BaseTopologySnapshot> findSnapshot(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        List<BaseTopologySnapshot> results = jdbcTemplate.query(
            "SELECT habitat_id, topology_json, captured_at FROM topology_snapshots WHERE habitat_id = ?",
            (rs, rowNum) -> toSnapshot(rs),
            habitatId
        );
        return results.stream().findFirst();
    }

    public void appendMutationRecord(TopologyMutationRecord record) {
        Objects.requireNonNull(record, "record is required");
        jdbcTemplate.update(
            """
                INSERT INTO mutation_records
                (mutation_id, habitat_id, from_version, to_version, change_kinds,
                 affected_device_ids, affected_endpoint_ids, reason, accepted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            record.mutationId().toString(),
            record.habitatId(),
            record.fromVersion().value(),
            record.toVersion().value(),
            joinChangeKinds(record.changeKinds()),
            String.join(",", record.affectedDeviceIds()),
            String.join(",", record.affectedEndpointIds()),
            record.reason(),
            Timestamp.from(record.acceptedAt())
        );
    }

    @Override
    public void appendLedgerEntry(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        jdbcTemplate.update(
            """
                INSERT INTO sc_c_ledger_entries
                (ledger_entry_id, habitat_id, record_class, aggregate_type, aggregate_id,
                 semantic_kind, payload_type, payload_json, idempotency_key,
                 recorded_at_ms, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            entry.ledgerEntryId().toString(),
            entry.habitatId(),
            entry.recordClass().name(),
            entry.aggregateType(),
            entry.aggregateId(),
            entry.semanticKind().name(),
            entry.payloadType(),
            entry.payloadJson(),
            entry.idempotencyKey(),
            entry.recordedAt().toEpochMilli(),
            entry.metadataJson()
        );
    }

    @Override
    public void appendOutboxEntry(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        jdbcTemplate.update(
            """
                INSERT INTO sc_c_outbox_entries
                (outbox_entry_id, ledger_entry_id, habitat_id, outbound_kind,
                 delivery_lane, logical_topic, semantic_payload_json,
                 notification_target_ref, idempotency_key, status,
                 attempt_count, created_at_ms, updated_at_ms, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            entry.outboxEntryId().toString(),
            entry.ledgerEntryId().toString(),
            entry.habitatId(),
            entry.outboundKind().name(),
            entry.deliveryLane().name(),
            entry.logicalTopic(),
            entry.semanticPayloadJson(),
            entry.notificationTargetRef(),
            entry.idempotencyKey(),
            entry.status().name(),
            0,
            entry.createdAt().toEpochMilli(),
            entry.updatedAt().toEpochMilli(),
            entry.metadataJson()
        );
    }

    public List<TopologyMutationRecord> findMutationRecords(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        return jdbcTemplate.query(
            """
                SELECT mutation_id, habitat_id, from_version, to_version, change_kinds,
                       affected_device_ids, affected_endpoint_ids, reason, accepted_at
                FROM mutation_records
                WHERE habitat_id = ?
                ORDER BY accepted_at ASC
                """,
            (rs, rowNum) -> toMutationRecord(rs),
            habitatId
        );
    }

    public void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(state, "state is required");
        jdbcTemplate.update(
            """
                MERGE INTO device_states
                (habitat_id, device_id, state_json)
                KEY (habitat_id, device_id)
                VALUES (?, ?, ?)
                """,
            habitatId,
            deviceId,
            writeJson(state)
        );
    }

    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        List<Map<String, Object>> results = jdbcTemplate.query(
            "SELECT state_json FROM device_states WHERE habitat_id = ? AND device_id = ?",
            (rs, rowNum) -> readState(rs.getString("state_json")),
            habitatId,
            deviceId
        );
        return results.stream().findFirst();
    }

    public void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(endpointId, "endpointId is required");
        Objects.requireNonNull(health, "health is required");
        jdbcTemplate.update(
            """
                MERGE INTO endpoint_health
                (habitat_id, endpoint_id, status, last_seen_at, details)
                KEY (habitat_id, endpoint_id)
                VALUES (?, ?, ?, ?, ?)
                """,
            habitatId,
            endpointId,
            health.status().name(),
            health.lastSeenAt() == null ? null : Timestamp.from(health.lastSeenAt()),
            health.details()
        );
    }

    public Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(endpointId, "endpointId is required");
        List<EndpointHealth> results = jdbcTemplate.query(
            "SELECT status, last_seen_at, details FROM endpoint_health WHERE habitat_id = ? AND endpoint_id = ?",
            (rs, rowNum) -> new EndpointHealth(
                HealthStatus.valueOf(rs.getString("status")),
                toInstant(rs.getTimestamp("last_seen_at")),
                rs.getString("details")
            ),
            habitatId,
            endpointId
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<MaterializationDecision> findDecision(String habitatId, UUID factId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(factId, "factId is required");
        List<MaterializationDecision> results = jdbcTemplate.query(
            """
                SELECT decision_id, kind,
                       previous_version_value, resulting_version_value,
                       emitted_changes_json, reason
                FROM materialization_decision_replay
                WHERE habitat_id = ? AND fact_id = ?
                """,
            (rs, rowNum) -> toMaterializationDecision(habitatId, factId, rs),
            habitatId,
            factId.toString()
        );
        return results.stream().findFirst();
    }

    @Override
    public void recordDecision(String habitatId, UUID factId, MaterializationDecision decision) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(decision, "decision is required");
        String emittedChangesJson = writeJson(decision.emittedChanges());
        jdbcTemplate.update(
            """
                MERGE INTO materialization_decision_replay
                (habitat_id, fact_id, decision_id, kind,
                 previous_version_value, resulting_version_value,
                 emitted_changes_json, reason, recorded_at)
                KEY (habitat_id, fact_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            habitatId,
            factId.toString(),
            decision.decisionId().toString(),
            decision.kind().name(),
            decision.previousTopologyVersion().map(TopologyVersion::value).orElse(null),
            decision.resultingTopologyVersion().map(TopologyVersion::value).orElse(null),
            emittedChangesJson,
            decision.reason(),
            Timestamp.from(Instant.now(clock))
        );
    }

    @Override
    public Optional<RoomNode> findRoom(String habitatId, String roomId) {
        Objects.requireNonNull(roomId, "roomId is required");
        return findByHabitatId(habitatId)
            .flatMap(topology -> topology.rooms().stream()
                .filter(room -> room.roomId().equals(roomId))
                .findFirst());
    }

    @Override
    public Optional<ZoneNode> findZone(String habitatId, String zoneId) {
        Objects.requireNonNull(zoneId, "zoneId is required");
        return findByHabitatId(habitatId)
            .flatMap(topology -> topology.zones().stream()
                .filter(zone -> zone.zoneId().equals(zoneId))
                .findFirst());
    }

    @Override
    public Optional<TopologySpatialRelation> findSpatialRelation(String habitatId, String relationId) {
        Objects.requireNonNull(relationId, "relationId is required");
        return findByHabitatId(habitatId)
            .flatMap(topology -> topology.spatialRelations().stream()
                .filter(relation -> relation.relationId().equals(relationId))
                .findFirst());
    }

    @Override
    public List<TopologySpatialRelation> findSpatialRelationsBySubject(
        String habitatId,
        TopologySpatialEntityType type,
        String id
    ) {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(id, "id is required");
        return findByHabitatId(habitatId)
            .map(topology -> topology.spatialRelations().stream()
                .filter(relation -> relation.subject().type() == type)
                .filter(relation -> relation.subject().id().equals(id))
                .toList())
            .orElse(List.of());
    }

    @Override
    public List<DeviceNode> findLocatedDevices(String habitatId, String roomOrZoneId) {
        Objects.requireNonNull(roomOrZoneId, "roomOrZoneId is required");
        return findByHabitatId(habitatId)
            .map(topology -> topology.devices().stream()
                .filter(device -> isDeviceLocatedIn(topology, device, roomOrZoneId))
                .toList())
            .orElse(List.of());
    }

    @Override
    public List<EndpointNode> findLocatedEndpoints(String habitatId, String roomOrZoneId) {
        Objects.requireNonNull(roomOrZoneId, "roomOrZoneId is required");
        return findByHabitatId(habitatId)
            .map(topology -> topology.endpoints().stream()
                .filter(endpoint -> isEndpointLocatedIn(topology, endpoint, roomOrZoneId))
                .toList())
            .orElse(List.of());
    }

    @Override
    public Optional<TopologySpatialRelation> resolvePrimaryPlacement(
        String habitatId,
        TopologySpatialEntityType type,
        String id
    ) {
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(id, "id is required");
        return findByHabitatId(habitatId)
            .flatMap(topology -> topology.spatialRelations().stream()
                .filter(relation -> relation.kind() == TopologySpatialRelationKind.LOCATED_IN)
                .filter(TopologySpatialRelation::primary)
                .filter(relation -> relation.subject().type() == type)
                .filter(relation -> relation.subject().id().equals(id))
                .findFirst());
    }

    private void createSchema() {
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS topology_snapshots (
                    habitat_id VARCHAR(255) PRIMARY KEY,
                    topology_version VARCHAR(255) NOT NULL,
                    topology_json CLOB NOT NULL,
                    captured_at TIMESTAMP NOT NULL
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS mutation_records (
                    mutation_id VARCHAR(255) PRIMARY KEY,
                    habitat_id VARCHAR(255) NOT NULL,
                    from_version VARCHAR(255) NOT NULL,
                    to_version VARCHAR(255) NOT NULL,
                    change_kinds VARCHAR(1024),
                    affected_device_ids VARCHAR(2048),
                    affected_endpoint_ids VARCHAR(2048),
                    reason VARCHAR(1024),
                    accepted_at TIMESTAMP NOT NULL
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS device_states (
                    habitat_id VARCHAR(255) NOT NULL,
                    device_id VARCHAR(255) NOT NULL,
                    state_json CLOB NOT NULL,
                    PRIMARY KEY (habitat_id, device_id)
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS endpoint_health (
                    habitat_id VARCHAR(255) NOT NULL,
                    endpoint_id VARCHAR(255) NOT NULL,
                    status VARCHAR(64) NOT NULL,
                    last_seen_at TIMESTAMP,
                    details VARCHAR(1024),
                    PRIMARY KEY (habitat_id, endpoint_id)
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS materialization_decision_replay (
                    habitat_id              VARCHAR(255)  NOT NULL,
                    fact_id                 VARCHAR(36)   NOT NULL,
                    decision_id             VARCHAR(36)   NOT NULL,
                    kind                    VARCHAR(128)  NOT NULL,
                    previous_version_value  VARCHAR(255),
                    resulting_version_value VARCHAR(255),
                    emitted_changes_json    CLOB          NOT NULL,
                    reason                  VARCHAR(2048) NOT NULL,
                    recorded_at             TIMESTAMP     NOT NULL,
                    PRIMARY KEY (habitat_id, fact_id)
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS sc_c_ledger_entries (
                    ledger_entry_id VARCHAR(36) PRIMARY KEY,
                    habitat_id VARCHAR(255) NOT NULL,
                    record_class VARCHAR(64) NOT NULL,
                    aggregate_type VARCHAR(128) NOT NULL,
                    aggregate_id VARCHAR(255) NOT NULL,
                    semantic_kind VARCHAR(128) NOT NULL,
                    payload_type VARCHAR(255) NOT NULL,
                    payload_json CLOB NOT NULL,
                    idempotency_key VARCHAR(512) NOT NULL,
                    recorded_at_ms BIGINT NOT NULL,
                    metadata_json CLOB,
                    CONSTRAINT uq_sc_c_ledger_entry_habitat
                        UNIQUE (ledger_entry_id, habitat_id),
                    CONSTRAINT uq_sc_c_ledger_idempotency
                        UNIQUE (habitat_id, idempotency_key)
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE INDEX IF NOT EXISTS ix_ledger_habitat_aggregate
                ON sc_c_ledger_entries(habitat_id, aggregate_type, aggregate_id)
                """
        );
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS sc_c_outbox_entries (
                    outbox_entry_id VARCHAR(36) PRIMARY KEY,
                    ledger_entry_id VARCHAR(36) NOT NULL,
                    habitat_id VARCHAR(255) NOT NULL,
                    outbound_kind VARCHAR(128) NOT NULL,
                    delivery_lane VARCHAR(64) NOT NULL,
                    logical_topic VARCHAR(255) NOT NULL,
                    semantic_payload_json CLOB NOT NULL,
                    notification_target_ref VARCHAR(512),
                    idempotency_key VARCHAR(512) NOT NULL,
                    status VARCHAR(64) NOT NULL
                        CHECK(status IN ('PENDING','CLAIMED','DISPATCHED',
                                         'DISPATCH_FAILED','RETRY_WAIT',
                                         'DEAD_LETTERED','SUPPRESSED')),
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    claimed_at_ms BIGINT,
                    claim_expires_at_ms BIGINT,
                    claimed_by VARCHAR(255),
                    expires_at_ms BIGINT,
                    created_at_ms BIGINT NOT NULL,
                    updated_at_ms BIGINT NOT NULL,
                    metadata_json CLOB,
                    CONSTRAINT fk_sc_c_outbox_ledger
                        FOREIGN KEY (ledger_entry_id, habitat_id)
                        REFERENCES sc_c_ledger_entries(ledger_entry_id, habitat_id),
                    CONSTRAINT uq_sc_c_outbox_idempotency
                        UNIQUE (habitat_id, idempotency_key)
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE INDEX IF NOT EXISTS ix_outbox_status_created
                ON sc_c_outbox_entries(habitat_id, status, created_at_ms)
                """
        );
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    private boolean isDeviceLocatedIn(HabitatBaseTopology topology, DeviceNode device, String roomOrZoneId) {
        boolean relationMatch = topology.spatialRelations().stream()
            .filter(relation -> relation.kind() == TopologySpatialRelationKind.LOCATED_IN)
            .filter(TopologySpatialRelation::primary)
            .filter(relation -> relation.subject().type() == TopologySpatialEntityType.DEVICE)
            .filter(relation -> relation.subject().id().equals(device.deviceId()))
            .anyMatch(relation -> relation.target().id().equals(roomOrZoneId));
        return relationMatch || device.roomId().equals(roomOrZoneId) || device.zoneId().equals(roomOrZoneId);
    }

    private boolean isEndpointLocatedIn(HabitatBaseTopology topology, EndpointNode endpoint, String roomOrZoneId) {
        boolean relationMatch = topology.spatialRelations().stream()
            .filter(relation -> relation.kind() == TopologySpatialRelationKind.LOCATED_IN)
            .filter(TopologySpatialRelation::primary)
            .filter(relation -> relation.subject().type() == TopologySpatialEntityType.ENDPOINT)
            .filter(relation -> relation.subject().id().equals(endpoint.endpointId()))
            .anyMatch(relation -> relation.target().id().equals(roomOrZoneId));
        return relationMatch || endpoint.roomId().equals(roomOrZoneId) || endpoint.zoneId().equals(roomOrZoneId);
    }

    private BaseTopologySnapshot toSnapshot(ResultSet rs) throws SQLException {
        HabitatBaseTopology topology = readJson(rs.getString("topology_json"), HabitatBaseTopology.class);
        return new BaseTopologySnapshot(
            rs.getString("habitat_id"),
            topology.topologyVersion(),
            topology,
            rs.getTimestamp("captured_at").toInstant()
        );
    }

    private TopologyMutationRecord toMutationRecord(ResultSet rs) throws SQLException {
        String habitatId = rs.getString("habitat_id");
        return new TopologyMutationRecord(
            UUID.fromString(rs.getString("mutation_id")),
            habitatId,
            TopologyVersion.habitatVersion(habitatId, Long.parseLong(rs.getString("from_version"))),
            TopologyVersion.habitatVersion(habitatId, Long.parseLong(rs.getString("to_version"))),
            parseChangeKinds(rs.getString("change_kinds")),
            parseList(rs.getString("affected_device_ids")),
            parseList(rs.getString("affected_endpoint_ids")),
            rs.getString("reason"),
            rs.getTimestamp("accepted_at").toInstant()
        );
    }

    private MaterializationDecision toMaterializationDecision(String habitatId, UUID factId, ResultSet rs) throws SQLException {
        String previousValue = rs.getString("previous_version_value");
        String resultingValue = rs.getString("resulting_version_value");
        Optional<TopologyVersion> previousVersion = previousValue == null
            ? Optional.empty()
            : Optional.of(TopologyVersion.habitatVersion(habitatId, Long.parseLong(previousValue)));
        Optional<TopologyVersion> resultingVersion = resultingValue == null
            ? Optional.empty()
            : Optional.of(TopologyVersion.habitatVersion(habitatId, Long.parseLong(resultingValue)));
        return new MaterializationDecision(
            UUID.fromString(rs.getString("decision_id")),
            factId,
            habitatId,
            MaterializationDecisionKind.valueOf(rs.getString("kind")),
            previousVersion,
            resultingVersion,
            readJson(rs.getString("emitted_changes_json"), TOPOLOGY_CHANGED_LIST_TYPE),
            rs.getString("reason")
        );
    }

    private String joinChangeKinds(Set<TopologyChangeKind> changeKinds) {
        return changeKinds.stream()
            .map(TopologyChangeKind::name)
            .sorted()
            .collect(Collectors.joining(","));
    }

    private Set<TopologyChangeKind> parseChangeKinds(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
            .filter(part -> !part.isBlank())
            .map(TopologyChangeKind::valueOf)
            .collect(Collectors.toUnmodifiableSet());
    }

    private List<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .filter(part -> !part.isBlank())
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private Map<String, Object> readState(String json) {
        try {
            return objectMapper.readValue(json, STATE_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize device state", ex);
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize " + value.getClass().getSimpleName(), ex);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
