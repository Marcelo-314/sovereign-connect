package com.sovereign.connect.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.TopologyMutationRecord;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.port.CoreSnapshotReadPort;
import com.sovereign.connect.core.topology.port.EndpointHealthWritePort;
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

public class H2BaseTopologyRepository implements BaseTopologyRepository, CoreSnapshotReadPort, EndpointHealthWritePort {

    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {
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
