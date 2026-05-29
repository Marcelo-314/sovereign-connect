package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteScOutboxDispatchReadPortTest {
    @TempDir
    Path tempDir;

    @Test
    void findDispatchableEntriesReturnsOnlyPendingRows() {
        DataSource dataSource = migratedDataSource("pending-only.sqlite");
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(dataSource);
        OutboxEntry pending = entry(UUID.randomUUID(), Instant.parse("2026-05-29T10:00:00Z"));
        OutboxEntry claimed = entry(UUID.randomUUID(), Instant.parse("2026-05-29T10:01:00Z"));
        appendOutboxEntry(repository, pending);
        appendOutboxEntry(repository, claimed);
        updateStatus(dataSource, claimed.outboxEntryId(), OutboxEntryStatus.CLAIMED);

        List<OutboxEntry> entries = repository.findDispatchableEntries(10);

        assertThat(entries).extracting(OutboxEntry::outboxEntryId).containsExactly(pending.outboxEntryId());
    }

    @Test
    void findDispatchableEntriesRejectsNonPositiveLimit() {
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(migratedDataSource("limit.sqlite"));

        assertThatThrownBy(() -> repository.findDispatchableEntries(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findDispatchableEntries(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findDispatchableEntriesAppliesLimitAndStableOrdering() {
        DataSource dataSource = migratedDataSource("ordering.sqlite");
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(dataSource);
        Instant firstCreatedAt = Instant.parse("2026-05-29T10:00:00Z");
        OutboxEntry later = entry(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), Instant.parse("2026-05-29T10:01:00Z"));
        OutboxEntry firstTie = entry(UUID.fromString("00000000-0000-0000-0000-000000000001"), firstCreatedAt);
        OutboxEntry secondTie = entry(UUID.fromString("00000000-0000-0000-0000-000000000002"), firstCreatedAt);
        appendOutboxEntry(repository, later);
        appendOutboxEntry(repository, secondTie);
        appendOutboxEntry(repository, firstTie);

        List<OutboxEntry> entries = repository.findDispatchableEntries(2);

        assertThat(entries).extracting(OutboxEntry::outboxEntryId)
                .containsExactly(firstTie.outboxEntryId(), secondTie.outboxEntryId());
    }

    @Test
    void findDispatchableEntriesMapsAllOutboxEntryFields() {
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(migratedDataSource("fields.sqlite"));
        OutboxEntry expected = new OutboxEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "habitat-001",
                OutboundKind.COMMAND,
                DeliveryLane.COMMAND,
                "device.command",
                "{\"command\":\"open\"}",
                "target:door",
                "idem-1",
                OutboxEntryStatus.PENDING,
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:01Z"),
                "{\"trace\":\"abc\"}"
        );
        appendOutboxEntry(repository, expected);

        assertThat(repository.findDispatchableEntries(1)).containsExactly(expected);
    }

    @Test
    void findDispatchableEntriesDoesNotMutateOutboxStatusOrUpdatedAt() {
        DataSource dataSource = migratedDataSource("no-mutation.sqlite");
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(dataSource);
        OutboxEntry pending = entry(UUID.randomUUID(), Instant.parse("2026-05-29T10:00:00Z"));
        appendOutboxEntry(repository, pending);

        repository.findDispatchableEntries(10);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sc_c_outbox_entries WHERE outbox_entry_id = ?",
                String.class,
                pending.outboxEntryId().toString()
        )).isEqualTo(OutboxEntryStatus.PENDING.name());
        assertThat(jdbc.queryForObject(
                "SELECT updated_at_ms FROM sc_c_outbox_entries WHERE outbox_entry_id = ?",
                Long.class,
                pending.outboxEntryId().toString()
        )).isEqualTo(pending.updatedAt().toEpochMilli());
    }

    private DataSource migratedDataSource(String name) {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:" + tempDir.resolve(name));
        DataSource ds = new PerConnectionPragmaDataSource(delegate);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        return ds;
    }

    private OutboxEntry entry(UUID outboxEntryId, Instant createdAt) {
        return new OutboxEntry(
                outboxEntryId,
                UUID.randomUUID(),
                "habitat-001",
                OutboundKind.COMMAND,
                DeliveryLane.EVENT,
                "topic.alpha",
                "{\"payload\":true}",
                null,
                "idem-" + outboxEntryId,
                OutboxEntryStatus.PENDING,
                createdAt,
                createdAt.plusMillis(5),
                null
        );
    }

    private void appendOutboxEntry(SQLiteScLedgerOutboxRepository repository, OutboxEntry entry) {
        repository.appendLedgerEntry(new LedgerEntry(
                entry.ledgerEntryId(),
                entry.habitatId(),
                LedgerRecordClass.EVENT_OUTBOX,
                "test-aggregate",
                entry.outboxEntryId().toString(),
                SemanticKind.TIMER_FIRED,
                "application/json",
                "{}",
                "ledger-" + entry.idempotencyKey(),
                entry.createdAt(),
                null
        ));
        repository.appendOutboxEntry(entry);
    }

    private void updateStatus(DataSource dataSource, UUID outboxEntryId, OutboxEntryStatus status) {
        new JdbcTemplate(dataSource).update(
                "UPDATE sc_c_outbox_entries SET status = ? WHERE outbox_entry_id = ?",
                status.name(),
                outboxEntryId.toString()
        );
    }
}
