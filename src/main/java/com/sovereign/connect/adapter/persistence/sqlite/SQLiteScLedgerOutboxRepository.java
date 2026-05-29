package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxDispatchReadPort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class SQLiteScLedgerOutboxRepository implements ScLedgerWritePort, ScOutboxWritePort, ScOutboxDispatchReadPort {

    private final JdbcTemplate jdbcTemplate;

    public SQLiteScLedgerOutboxRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public void appendLedgerEntry(LedgerEntry entry) {
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
        if (entry.status() != OutboxEntryStatus.PENDING) {
            throw new IllegalArgumentException("appendOutboxEntry accepts only PENDING outbox entries: " + entry.status());
        }
        jdbcTemplate.update(
            """
                INSERT INTO sc_c_outbox_entries
                (outbox_entry_id, ledger_entry_id, habitat_id, outbound_kind,
                 delivery_lane, logical_topic, semantic_payload_json,
                 notification_target_ref, idempotency_key, status,
                 attempt_count, created_at_ms, updated_at_ms, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
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
            entry.createdAt().toEpochMilli(),
            entry.updatedAt().toEpochMilli(),
            entry.metadataJson()
        );
    }

    @Override
    public List<OutboxEntry> findDispatchableEntries(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query(
            """
                SELECT outbox_entry_id, ledger_entry_id, habitat_id, outbound_kind,
                       delivery_lane, logical_topic, semantic_payload_json,
                       notification_target_ref, idempotency_key, status,
                       created_at_ms, updated_at_ms, metadata_json
                FROM sc_c_outbox_entries
                WHERE status = ?
                ORDER BY created_at_ms ASC, outbox_entry_id ASC
                LIMIT ?
                """,
            (rs, rowNum) -> new OutboxEntry(
                UUID.fromString(rs.getString("outbox_entry_id")),
                UUID.fromString(rs.getString("ledger_entry_id")),
                rs.getString("habitat_id"),
                OutboundKind.valueOf(rs.getString("outbound_kind")),
                DeliveryLane.valueOf(rs.getString("delivery_lane")),
                rs.getString("logical_topic"),
                rs.getString("semantic_payload_json"),
                rs.getString("notification_target_ref"),
                rs.getString("idempotency_key"),
                OutboxEntryStatus.valueOf(rs.getString("status")),
                Instant.ofEpochMilli(rs.getLong("created_at_ms")),
                Instant.ofEpochMilli(rs.getLong("updated_at_ms")),
                rs.getString("metadata_json")
            ),
            OutboxEntryStatus.PENDING.name(),
            limit
        );
    }
}
