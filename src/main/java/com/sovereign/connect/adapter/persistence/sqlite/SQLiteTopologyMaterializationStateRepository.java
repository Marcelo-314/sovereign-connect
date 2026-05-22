package com.sovereign.connect.adapter.persistence.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class SQLiteTopologyMaterializationStateRepository implements TopologyMaterializationStatePort {

    private static final TypeReference<Map<String, Object>> STATE_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SQLiteBaseTopologyRepository baseRepository;
    private final SQLiteEndpointHealthRepository healthRepository;
    private final Clock clock;

    public SQLiteTopologyMaterializationStateRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        SQLiteBaseTopologyRepository baseRepository,
        SQLiteEndpointHealthRepository healthRepository,
        Clock clock
    ) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.baseRepository = Objects.requireNonNull(baseRepository, "baseRepository is required");
        this.healthRepository = Objects.requireNonNull(healthRepository, "healthRepository is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public Optional<HabitatBaseTopology> findByHabitatId(String habitatId) {
        return baseRepository.findByHabitatId(habitatId);
    }

    @Override
    public Optional<TopologyVersion> findCurrentVersion(String habitatId) {
        return baseRepository.findCurrentVersion(habitatId);
    }

    @Override
    public void saveDeviceState(String habitatId, String deviceId, Map<String, Object> state) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(state, "state is required");
        jdbcTemplate.update(
            """
                INSERT INTO device_states (habitat_id, device_id, state_json, updated_at_ms)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(habitat_id, device_id) DO UPDATE SET
                  state_json = excluded.state_json,
                  updated_at_ms = excluded.updated_at_ms
                """,
            habitatId,
            deviceId,
            writeJson(state),
            Instant.now(clock).toEpochMilli()
        );
    }

    @Override
    public void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health) {
        healthRepository.saveEndpointHealth(habitatId, endpointId, health);
    }

    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        return jdbcTemplate.query(
            """
                SELECT state_json
                FROM device_states
                WHERE habitat_id = ? AND device_id = ?
                """,
            (rs, rowNum) -> readJson(rs.getString("state_json"), STATE_TYPE),
            habitatId,
            deviceId
        ).stream().findFirst();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize device state", ex);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize device state", ex);
        }
    }
}
