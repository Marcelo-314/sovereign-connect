package com.sovereign.connect.bus.runtime.persistence;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchObservationPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class JdbcDispatchObservationRepository implements DispatchObservationPort {
    private final JdbcTemplate jdbc;

    public JdbcDispatchObservationRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public void record(DispatchObservationRecord observation) {
        Objects.requireNonNull(observation, "observation is required");
        Objects.requireNonNull(observation.observationId(), "observationId is required");
        Objects.requireNonNull(observation.dispatchRecordId(), "dispatchRecordId is required");
        Objects.requireNonNull(observation.state(), "state is required");
        Objects.requireNonNull(observation.observedAt(), "observedAt is required");

        jdbc.update(
                """
                INSERT INTO sc_b_dispatch_observations (
                    observation_id, dispatch_record_id, attempt_id,
                    state, code, sanitized_reason, observed_at_ms
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                observation.observationId().toString(),
                observation.dispatchRecordId().toString(),
                observation.attemptId() == null ? null : observation.attemptId().toString(),
                observation.state().name(),
                observation.code(),
                observation.sanitizedReason(),
                observation.observedAt().toEpochMilli()
        );
    }

    @Override
    public List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId) {
        Objects.requireNonNull(dispatchRecordId, "dispatchRecordId is required");
        return List.copyOf(jdbc.query(
                """
                SELECT observation_id, dispatch_record_id, attempt_id,
                       state, code, sanitized_reason, observed_at_ms
                FROM sc_b_dispatch_observations
                WHERE dispatch_record_id = ?
                ORDER BY observed_at_ms ASC, observation_id ASC
                """,
                (rs, rowNum) -> new DispatchObservationRecord(
                        UUID.fromString(rs.getString("observation_id")),
                        UUID.fromString(rs.getString("dispatch_record_id")),
                        rs.getString("attempt_id") == null ? null : UUID.fromString(rs.getString("attempt_id")),
                        DispatchState.valueOf(rs.getString("state")),
                        rs.getString("code"),
                        rs.getString("sanitized_reason"),
                        Instant.ofEpochMilli(rs.getLong("observed_at_ms"))
                ),
                dispatchRecordId.toString()
        ));
    }
}
