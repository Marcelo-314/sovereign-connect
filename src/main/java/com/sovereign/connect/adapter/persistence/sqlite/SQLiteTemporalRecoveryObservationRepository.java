package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.temporal.service.TemporalRecoveryObservationPort;
import com.sovereign.connect.core.temporal.service.TemporalRecoveryResult;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class SQLiteTemporalRecoveryObservationRepository implements TemporalRecoveryObservationPort {

    private final JdbcTemplate jdbcTemplate;

    public SQLiteTemporalRecoveryObservationRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public UUID startRun(String mode, Instant startedAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
                INSERT INTO recovery_runs
                (recovery_run_id, mode, status, started_at_ms, schema_version, storage_profile, summary_json)
                VALUES (?, ?, 'RUNNING', ?, 'temporal-engine.v1', 'sqlite', NULL)
                """,
            id.toString(),
            mode,
            startedAt.toEpochMilli()
        );
        return id;
    }

    @Override
    public void recordFinding(UUID recoveryRunId, String severity, String category, String code, String entityType,
                              String entityId, String message, boolean resolved, Instant createdAt) {
        jdbcTemplate.update(
            """
                INSERT INTO recovery_findings
                (finding_id, recovery_run_id, severity, category, code, entity_type,
                 entity_id, message, resolved, metadata_json, created_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                """,
            UUID.randomUUID().toString(),
            recoveryRunId.toString(),
            severity,
            category,
            code,
            entityType,
            entityId,
            message,
            resolved ? 1 : 0,
            createdAt.toEpochMilli()
        );
    }

    @Override
    public void completeRun(UUID recoveryRunId, String status, TemporalRecoveryResult result, Instant completedAt) {
        jdbcTemplate.update(
            """
                UPDATE recovery_runs
                SET status = ?,
                    completed_at_ms = ?,
                    summary_json = ?
                WHERE recovery_run_id = ?
                """,
            status,
            completedAt.toEpochMilli(),
            "{\"misfiredCount\":" + result.misfiredCount()
                + ",\"failedCount\":" + result.failedCount()
                + ",\"complete\":" + result.complete() + "}",
            recoveryRunId.toString()
        );
    }
}
