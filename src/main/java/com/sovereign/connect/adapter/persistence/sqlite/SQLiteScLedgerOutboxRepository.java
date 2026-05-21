package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

public class SQLiteScLedgerOutboxRepository implements ScLedgerWritePort, ScOutboxWritePort {

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
}
