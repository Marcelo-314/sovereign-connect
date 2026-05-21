package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.temporal.engine.TemporalEngineLockPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class SQLiteTemporalEngineLockRepository implements TemporalEngineLockPort {

    private final JdbcTemplate jdbcTemplate;

    public SQLiteTemporalEngineLockRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public boolean tryAcquire(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now, long ttlMs) {
        Long activeCount = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM temporal_engine_locks
                WHERE habitat_id = ?
                  AND storage_partition_ref = ?
                  AND status = 'ACTIVE'
                  AND expires_at_ms > ?
                """,
            Long.class,
            habitatId,
            storagePartitionRef,
            now.toEpochMilli()
        );
        if (activeCount != null && activeCount > 0) {
            return false;
        }
        jdbcTemplate.update(
            """
                INSERT OR REPLACE INTO temporal_engine_locks
                (habitat_id, storage_partition_ref, engine_instance_id,
                 acquired_at_ms, heartbeat_at_ms, expires_at_ms, status, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', NULL)
                """,
            habitatId,
            storagePartitionRef,
            engineInstanceId,
            now.toEpochMilli(),
            now.toEpochMilli(),
            now.toEpochMilli() + ttlMs
        );
        return true;
    }

    @Override
    public int heartbeat(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now, long ttlMs) {
        return jdbcTemplate.update(
            """
                UPDATE temporal_engine_locks
                SET heartbeat_at_ms = ?,
                    expires_at_ms = ?
                WHERE habitat_id = ?
                  AND storage_partition_ref = ?
                  AND engine_instance_id = ?
                  AND status = 'ACTIVE'
                """,
            now.toEpochMilli(),
            now.toEpochMilli() + ttlMs,
            habitatId,
            storagePartitionRef,
            engineInstanceId
        );
    }

    @Override
    public void release(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now) {
        jdbcTemplate.update(
            """
                UPDATE temporal_engine_locks
                SET status = 'RELEASED',
                    expires_at_ms = ?,
                    heartbeat_at_ms = ?
                WHERE habitat_id = ?
                  AND storage_partition_ref = ?
                  AND engine_instance_id = ?
                """,
            now.toEpochMilli(),
            now.toEpochMilli(),
            habitatId,
            storagePartitionRef,
            engineInstanceId
        );
    }

    @Override
    public boolean isHeld(String habitatId, String storagePartitionRef, Instant now) {
        List<Integer> result = jdbcTemplate.query(
            """
                SELECT 1
                FROM temporal_engine_locks
                WHERE habitat_id = ?
                  AND storage_partition_ref = ?
                  AND status = 'ACTIVE'
                  AND expires_at_ms > ?
                LIMIT 1
                """,
            (rs, rowNum) -> 1,
            habitatId,
            storagePartitionRef,
            now.toEpochMilli()
        );
        return !result.isEmpty();
    }
}
