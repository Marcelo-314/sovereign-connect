package com.sovereign.connect.adapter.persistence.sqlite;

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

public class SQLiteTemporalActRepository implements TemporalActWritePort, TemporalActReadPort {

    private static final System.Logger LOGGER = System.getLogger(SQLiteTemporalActRepository.class.getName());
    private static final long DAY_MS = 86_400_000L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int terminalRetentionDays;

    public SQLiteTemporalActRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC(), 30);
    }

    public SQLiteTemporalActRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        Clock clock,
        int terminalRetentionDays
    ) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.terminalRetentionDays = terminalRetentionDays;
    }

    @Override
    public void insertCreated(TemporalAct act) {
        Objects.requireNonNull(act, "act is required");
        if (act.status() != TemporalActStatus.PENDING) {
            throw new IllegalArgumentException("insertCreated accepts only PENDING TemporalActs: " + act.status());
        }
        if (act.notificationTargetRef() == null || act.notificationTargetRef().isBlank()) {
            throw new IllegalArgumentException("notificationTargetRef is required");
        }
        if (!(act.payload() instanceof SignalTemporalPayload payload)) {
            throw new IllegalArgumentException("only SignalTemporalPayload is supported in v1");
        }
        jdbcTemplate.update(
            """
                INSERT INTO temporal_acts
                (habitat_id, temporal_act_id, status, payload_kind, due_at_ms,
                 label, signal_kind, notification_target_ref, created_by_ref,
                 requested_at_ms, created_at_ms, updated_at_ms, signal_payload_json)
                VALUES (?, ?, ?, 'SIGNAL', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            act.habitatId(),
            SQLiteCanonicalIdCodec.toBlob16(act.temporalActId()),
            act.status().name(),
            act.dueAt().toEpochMilli(),
            payload.label(),
            payload.signalKind(),
            act.notificationTargetRef(),
            act.createdByRef().value(),
            null,
            act.createdAt().toEpochMilli(),
            act.updatedAt().toEpochMilli(),
            writeJson(payload)
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
            SQLiteCanonicalIdCodec.toBlob16(temporalActId),
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
            SQLiteCanonicalIdCodec.toBlob16(temporalActId),
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
            SQLiteCanonicalIdCodec.toBlob16(temporalActId),
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
            SQLiteCanonicalIdCodec.toBlob16(temporalActId),
            habitatId
        );
    }

    @Override
    public Optional<TemporalAct> findById(String habitatId, String temporalActId) {
        List<TemporalAct> results = jdbcTemplate.query(selectBase()
                + " WHERE habitat_id = ? AND temporal_act_id = ?",
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId,
            SQLiteCanonicalIdCodec.toBlob16(temporalActId)
        );
        return results.stream().findFirst();
    }

    @Override
    public List<TemporalAct> listActive(String habitatId) {
        return jdbcTemplate.query(selectBase()
                + " WHERE habitat_id = ? AND status IN ('PENDING','ARMED') ORDER BY due_at_ms ASC",
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId
        );
    }

    @Override
    public List<TemporalAct> findDue(String habitatId, Instant now) {
        return findDue(habitatId, now, Integer.MAX_VALUE);
    }

    @Override
    public List<TemporalAct> findDue(String habitatId, Instant now, int maxRows) {
        return jdbcTemplate.query(selectBase()
                + """
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
        return jdbcTemplate.query(selectBase()
                + """
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
        return listTerminal(habitatId, Integer.MAX_VALUE);
    }

    @Override
    public List<TemporalAct> listTerminal(String habitatId, int maxResults) {
        return terminalQuery(habitatId, "status IN ('FIRED','CANCELLED','MISFIRED','FAILED','EXPIRED')", maxResults);
    }

    @Override
    public List<TemporalAct> listMisfired(String habitatId) {
        return listMisfired(habitatId, Integer.MAX_VALUE);
    }

    @Override
    public List<TemporalAct> listMisfired(String habitatId, int maxResults) {
        return terminalQuery(habitatId, "status = 'MISFIRED'", maxResults);
    }

    private List<TemporalAct> terminalQuery(String habitatId, String predicate, int maxResults) {
        long retentionStartMs = Instant.now(clock).toEpochMilli() - terminalRetentionDays * DAY_MS;
        return jdbcTemplate.query(selectBase()
                + " WHERE habitat_id = ? AND " + predicate
                + " AND terminal_at_ms >= ? ORDER BY terminal_at_ms DESC LIMIT ?",
            (rs, rowNum) -> toTemporalAct(rs),
            habitatId,
            retentionStartMs,
            maxResults
        );
    }

    private String selectBase() {
        return """
            SELECT habitat_id, temporal_act_id, status, payload_kind, due_at_ms,
                   label, signal_kind, notification_target_ref, created_by_ref,
                   requested_at_ms, created_at_ms, updated_at_ms, fired_at_ms,
                   terminal_at_ms, terminal_reason, signal_payload_json
            FROM temporal_acts
            """;
    }

    private TemporalAct toTemporalAct(ResultSet rs) throws SQLException {
        return new TemporalAct(
            SQLiteCanonicalIdCodec.fromBlob16(rs.getBytes("temporal_act_id")),
            rs.getString("habitat_id"),
            TemporalActStatus.valueOf(rs.getString("status")),
            Instant.ofEpochMilli(rs.getLong("due_at_ms")),
            readPayload(rs.getString("payload_kind"), rs.getString("signal_payload_json"), rs),
            rs.getString("notification_target_ref"),
            new CreatedByRef(rs.getString("created_by_ref")),
            null,
            Instant.ofEpochMilli(rs.getLong("created_at_ms")),
            Instant.ofEpochMilli(rs.getLong("updated_at_ms")),
            nullableInstant(rs, "fired_at_ms"),
            nullableInstant(rs, "terminal_at_ms"),
            rs.getString("terminal_reason")
        );
    }

    private TemporalActPayload readPayload(String payloadKind, String payloadJson, ResultSet rs) throws SQLException {
        return switch (payloadKind) {
            case "SIGNAL" -> payloadJson == null || payloadJson.isBlank()
                ? new SignalTemporalPayload(rs.getString("label"), rs.getString("signal_kind"))
                : readJson(payloadJson, SignalTemporalPayload.class);
            default -> {
                LOGGER.log(System.Logger.Level.WARNING, "Unknown payload kind {0} - act will be quarantined", payloadKind);
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
