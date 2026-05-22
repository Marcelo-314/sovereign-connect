package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.port.EndpointHealthWritePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SQLiteEndpointHealthRepository implements EndpointHealthWritePort {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SQLiteEndpointHealthRepository(DataSource dataSource, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(endpointId, "endpointId is required");
        Objects.requireNonNull(health, "health is required");
        jdbcTemplate.update(
            """
                INSERT INTO endpoint_health
                  (habitat_id, endpoint_id, status, last_seen_at_ms, details, updated_at_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(habitat_id, endpoint_id) DO UPDATE SET
                  status = excluded.status,
                  last_seen_at_ms = excluded.last_seen_at_ms,
                  details = excluded.details,
                  updated_at_ms = excluded.updated_at_ms
                """,
            habitatId,
            endpointId,
            health.status().name(),
            health.lastSeenAt() == null ? null : health.lastSeenAt().toEpochMilli(),
            health.details(),
            Instant.now(clock).toEpochMilli()
        );
    }

    public Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(endpointId, "endpointId is required");
        List<EndpointHealth> results = jdbcTemplate.query(
            """
                SELECT status, last_seen_at_ms, details
                FROM endpoint_health
                WHERE habitat_id = ? AND endpoint_id = ?
                """,
            (rs, rowNum) -> toEndpointHealth(rs),
            habitatId,
            endpointId
        );
        return results.stream().findFirst();
    }

    public java.util.Map<String, EndpointHealth> findEndpointHealthByHabitat(String habitatId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        return jdbcTemplate.query(
            """
                SELECT endpoint_id, status, last_seen_at_ms, details
                FROM endpoint_health
                WHERE habitat_id = ?
                ORDER BY endpoint_id
                """,
            rs -> {
                java.util.Map<String, EndpointHealth> values = new java.util.LinkedHashMap<>();
                while (rs.next()) {
                    values.put(rs.getString("endpoint_id"), toEndpointHealth(rs));
                }
                return values;
            },
            habitatId
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
}
