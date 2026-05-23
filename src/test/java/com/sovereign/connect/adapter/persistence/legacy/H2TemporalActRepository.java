package com.sovereign.connect.adapter.persistence.legacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.temporal.model.CreatedByRef;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.model.TemporalActPayload;
import com.sovereign.connect.core.temporal.model.TemporalActStatus;
import com.sovereign.connect.core.temporal.port.TemporalActReadPort;
import com.sovereign.connect.core.temporal.port.TemporalActWritePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class H2TemporalActRepository implements TemporalActWritePort, TemporalActReadPort {

    private static final System.Logger LOGGER = System.getLogger(H2TemporalActRepository.class.getName());

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public H2TemporalActRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
    }

    public H2TemporalActRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        createSchema();
    }

    @Override
    public void insertCreated(TemporalAct act) {
        Objects.requireNonNull(act, "act is required");
        if (act.status() != TemporalActStatus.PENDING) {
            throw new IllegalArgumentException(
                "insertCreated accepts only PENDING TemporalActs in MU-005 seed: " + act.status()
            );
        }
        if (act.firedAt() != null || act.terminalAt() != null || act.terminalReason() != null) {
            throw new IllegalArgumentException(
                "insertCreated accepts only non-terminal newly-created TemporalActs; "
                    + "firedAt/terminalAt/terminalReason must be null"
            );
        }
        jdbcTemplate.update(
            """
                INSERT INTO temporal_acts
                (temporal_act_id, habitat_id, status, due_at_ms, payload_type, payload_json,
                 notification_target_ref, created_by_ref_json, topology_version_at_registration,
                 created_at_ms, updated_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            act.temporalActId(),
            act.habitatId(),
            act.status().name(),
            act.dueAt().toEpochMilli(),
            act.payload().getClass().getSimpleName(),
            writeJson(act.payload()),
            act.notificationTargetRef(),
            writeJson(act.createdByRef()),
            act.topologyVersionAtRegistration(),
            act.createdAt().toEpochMilli(),
            act.updatedAt().toEpochMilli()
        );
    }

    @Override
    public int cancelIfNonTerminal(String habitatId, String temporalActId, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return jdbcTemplate.update(
            """
                UPDATE temporal_acts
                SET status = 'CANCELLED',
                    terminal_at_ms = ?,
                    updated_at_ms = ?,
                    terminal_reason = 'cancelled'
                WHERE temporal_act_id = ?
                  AND habitat_id = ?
                  AND status IN ('PENDING','ARMED')
                """,
            now.toEpochMilli(),
            now.toEpochMilli(),
            temporalActId,
            habitatId
        );
    }

    @Override
    public int markFiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return jdbcTemplate.update(
            """
                UPDATE temporal_acts
                SET status = 'FIRED',
                    fired_at_ms = ?,
                    terminal_at_ms = ?,
                    updated_at_ms = ?
                WHERE temporal_act_id = ?
                  AND habitat_id = ?
                  AND status IN ('PENDING','ARMED')
                  AND due_at_ms <= ?
                """,
            now.toEpochMilli(),
            now.toEpochMilli(),
            now.toEpochMilli(),
            temporalActId,
            habitatId,
            now.toEpochMilli()
        );
    }

    @Override
    public int markMisfiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant cutoff, Instant now) {
        Objects.requireNonNull(cutoff, "cutoff is required");
        Objects.requireNonNull(now, "now is required");
        return jdbcTemplate.update(
            """
                UPDATE temporal_acts
                SET status = 'MISFIRED',
                    terminal_at_ms = ?,
                    updated_at_ms = ?,
                    terminal_reason = 'misfired during recovery'
                WHERE temporal_act_id = ?
                  AND habitat_id = ?
                  AND status IN ('PENDING','ARMED')
                  AND due_at_ms <= ?
                """,
            now.toEpochMilli(),
            now.toEpochMilli(),
            temporalActId,
            habitatId,
            cutoff.toEpochMilli()
        );
    }

    @Override
    public int markFailed(String habitatId, String temporalActId, String reason, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return jdbcTemplate.update(
            """
                UPDATE temporal_acts
                SET status = 'FAILED',
                    terminal_at_ms = ?,
                    updated_at_ms = ?,
                    terminal_reason = ?
                WHERE temporal_act_id = ?
                  AND habitat_id = ?
                  AND status IN ('PENDING','ARMED')
                """,
            now.toEpochMilli(),
            now.toEpochMilli(),
            reason,
            temporalActId,
            habitatId
        );
    }

    @Override
    public Optional<TemporalAct> findById(String habitatId, String temporalActId) {
        List<TemporalAct> results = jdbcTemplate.query(
            """
                SELECT temporal_act_id, habitat_id, status, due_at_ms, payload_type, payload_json,
                       notification_target_ref, created_by_ref_json, topology_version_at_registration,
                       created_at_ms, updated_at_ms, fired_at_ms, terminal_at_ms, terminal_reason
                FROM temporal_acts
                WHERE habitat_id = ? AND temporal_act_id = ?
                """,
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId,
            temporalActId
        );
        return results.stream().findFirst();
    }

    @Override
    public List<TemporalAct> listActive(String habitatId) {
        return queryByStatusSet(habitatId, "status IN ('PENDING','ARMED')");
    }

    @Override
    public List<TemporalAct> findDue(String habitatId, Instant now) {
        return findDue(habitatId, now, Integer.MAX_VALUE);
    }

    @Override
    public List<TemporalAct> findDue(String habitatId, Instant now, int maxRows) {
        Objects.requireNonNull(now, "now is required");
        return jdbcTemplate.query(
            """
                SELECT temporal_act_id, habitat_id, status, due_at_ms, payload_type, payload_json,
                       notification_target_ref, created_by_ref_json, topology_version_at_registration,
                       created_at_ms, updated_at_ms, fired_at_ms, terminal_at_ms, terminal_reason
                FROM temporal_acts
                WHERE habitat_id = ?
                  AND status IN ('PENDING','ARMED')
                  AND due_at_ms <= ?
                ORDER BY due_at_ms ASC
                LIMIT ?
                """,
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId,
            now.toEpochMilli(),
            maxRows
        );
    }

    @Override
    public List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff) {
        return findNonTerminalDueBefore(habitatId, cutoff, Integer.MAX_VALUE);
    }

    @Override
    public List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff, int maxRows) {
        Objects.requireNonNull(cutoff, "cutoff is required");
        return jdbcTemplate.query(
            """
                SELECT temporal_act_id, habitat_id, status, due_at_ms, payload_type, payload_json,
                       notification_target_ref, created_by_ref_json, topology_version_at_registration,
                       created_at_ms, updated_at_ms, fired_at_ms, terminal_at_ms, terminal_reason
                FROM temporal_acts
                WHERE habitat_id = ?
                  AND status IN ('PENDING','ARMED')
                  AND due_at_ms <= ?
                ORDER BY due_at_ms ASC
                LIMIT ?
                """,
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId,
            cutoff.toEpochMilli(),
            maxRows
        );
    }

    @Override
    public List<TemporalAct> listTerminal(String habitatId) {
        return queryByStatusSet(habitatId, "status IN ('FIRED','CANCELLED','EXPIRED','MISFIRED','FAILED')");
    }

    @Override
    public List<TemporalAct> listTerminal(String habitatId, int maxResults) {
        return queryByStatusSet(habitatId, "status IN ('FIRED','CANCELLED','EXPIRED','MISFIRED','FAILED')", maxResults);
    }

    @Override
    public List<TemporalAct> listMisfired(String habitatId) {
        return queryByStatusSet(habitatId, "status = 'MISFIRED'");
    }

    @Override
    public List<TemporalAct> listMisfired(String habitatId, int maxResults) {
        return queryByStatusSet(habitatId, "status = 'MISFIRED'", maxResults);
    }

    private void createSchema() {
        jdbcTemplate.execute(
            """
                CREATE TABLE IF NOT EXISTS temporal_acts (
                    temporal_act_id         VARCHAR(36)   PRIMARY KEY,
                    habitat_id              VARCHAR(255)  NOT NULL,
                    status                  VARCHAR(64)   NOT NULL
                                            CHECK(status IN ('PENDING','ARMED','FIRED',
                                                             'CANCELLED','EXPIRED','MISFIRED','FAILED')),
                    due_at_ms               BIGINT        NOT NULL,
                    payload_type            VARCHAR(255)  NOT NULL,
                    payload_json            CLOB          NOT NULL,
                    notification_target_ref VARCHAR(512),
                    created_by_ref_json     CLOB          NOT NULL,
                    topology_version_at_registration VARCHAR(255),
                    created_at_ms           BIGINT        NOT NULL,
                    updated_at_ms           BIGINT        NOT NULL,
                    fired_at_ms             BIGINT,
                    terminal_at_ms          BIGINT,
                    terminal_reason         VARCHAR(2048)
                )
                """
        );
        jdbcTemplate.execute(
            """
                CREATE INDEX IF NOT EXISTS ix_temporal_acts_due_status
                ON temporal_acts(habitat_id, status, due_at_ms)
                """
        );
        jdbcTemplate.execute(
            """
                CREATE INDEX IF NOT EXISTS ix_temporal_acts_status
                ON temporal_acts(habitat_id, status)
                """
        );
    }

    private List<TemporalAct> queryByStatusSet(String habitatId, String statusPredicate) {
        return queryByStatusSet(habitatId, statusPredicate, Integer.MAX_VALUE);
    }

    private List<TemporalAct> queryByStatusSet(String habitatId, String statusPredicate, int maxRows) {
        return jdbcTemplate.query(
            """
                SELECT temporal_act_id, habitat_id, status, due_at_ms, payload_type, payload_json,
                       notification_target_ref, created_by_ref_json, topology_version_at_registration,
                       created_at_ms, updated_at_ms, fired_at_ms, terminal_at_ms, terminal_reason
                FROM temporal_acts
                WHERE habitat_id = ? AND
                """ + statusPredicate + "\nORDER BY due_at_ms ASC LIMIT ?",
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId,
            maxRows
        );
    }

    private TemporalAct toTemporalAct(ResultSet rs) throws SQLException {
        return new TemporalAct(
            rs.getString("temporal_act_id"),
            rs.getString("habitat_id"),
            TemporalActStatus.valueOf(rs.getString("status")),
            Instant.ofEpochMilli(rs.getLong("due_at_ms")),
            readPayload(rs.getString("payload_type"), rs.getString("payload_json")),
            rs.getString("notification_target_ref"),
            readJson(rs.getString("created_by_ref_json"), CreatedByRef.class),
            rs.getString("topology_version_at_registration"),
            Instant.ofEpochMilli(rs.getLong("created_at_ms")),
            Instant.ofEpochMilli(rs.getLong("updated_at_ms")),
            nullableInstant(rs, "fired_at_ms"),
            nullableInstant(rs, "terminal_at_ms"),
            rs.getString("terminal_reason")
        );
    }

    private TemporalActPayload readPayload(String payloadType, String payloadJson) {
        return switch (payloadType) {
            case "SIGNAL" -> readJson(payloadJson, SignalTemporalPayload.class);
            case "SignalTemporalPayload" -> readJson(payloadJson, SignalTemporalPayload.class);
            default -> {
                LOGGER.log(System.Logger.Level.WARNING, "Unknown payload kind {0} - act will be quarantined", payloadType);
                yield null;
            }
        };
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(value);
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
}
