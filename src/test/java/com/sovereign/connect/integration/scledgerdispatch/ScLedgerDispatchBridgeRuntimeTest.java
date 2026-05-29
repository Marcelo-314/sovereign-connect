package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.adapter.persistence.sqlite.PerConnectionPragmaDataSource;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteScLedgerOutboxRepository;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.RuntimeDispatchService;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryDispatchObservationRepository;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryScBusPort;
import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchStateRepository;
import com.sovereign.connect.bus.runtime.validation.CorrelationValidationService;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import com.sovereign.connect.bus.runtime.validation.RoutingKeyValidationService;
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ScLedgerDispatchBridgeRuntimeTest {
    @TempDir
    Path tempDir;

    private DataSource scCDataSource;
    private DataSource scBDataSource;
    private JdbcDispatchStateRepository dispatchStateRepo;
    private ScLedgerDispatchCandidateReadAdapter candidateAdapter;
    private InMemoryScBusPort busPort;
    private RuntimeDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        SQLiteDataSource scCDelegate = new SQLiteDataSource();
        scCDelegate.setUrl("jdbc:sqlite:" + tempDir.resolve("sc-c-bridge-test.sqlite"));
        scCDataSource = new PerConnectionPragmaDataSource(scCDelegate);
        Flyway.configure().dataSource(scCDataSource).locations("classpath:db/migration").load().migrate();

        SQLiteDataSource scBDelegate = new SQLiteDataSource();
        scBDelegate.setUrl("jdbc:sqlite:" + tempDir.resolve("sc-b-bridge-test.sqlite"));
        scBDataSource = scBDelegate;
        Flyway.configure().dataSource(scBDataSource).locations("classpath:db/migration").load().migrate();

        var outboxReadPort = new SQLiteScLedgerOutboxRepository(scCDataSource);
        dispatchStateRepo = new JdbcDispatchStateRepository(scBDataSource);

        var factory = new DispatchRecordIdFactory();
        var mapper = new DeliveryLaneToScBusLaneMapper();
        var projector = new OutboxEntryDispatchProjector(factory, mapper);
        candidateAdapter = new ScLedgerDispatchCandidateReadAdapter(
                outboxReadPort, projector, dispatchStateRepo, 10);

        busPort = new InMemoryScBusPort();
        dispatchService = new RuntimeDispatchService(
                busPort,
                candidateAdapter,
                dispatchStateRepo,
                new InMemoryDispatchObservationRepository(),
                new EnvelopeValidationService(),
                new RoutingKeyValidationService(),
                new CorrelationValidationService()
        );
    }

    @Test
    void pendingEventOutboxRowDispatchesThroughRuntimeDispatchServiceToEventHandler() {
        OutboxEntry entry = appendOutboxEntry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event");
        AtomicReference<Object> handled = new AtomicReference<>();
        busPort.registerEventHandler("topic.event", envelope -> handled.set(envelope.payload()));

        List<DispatchOutcome> outcomes = dispatchService.dispatchPending();

        assertThat(outcomes).singleElement().isInstanceOf(Dispatched.class);
        assertThat(handled.get()).isInstanceOf(DispatchCandidate.class);
        assertThat(((DispatchCandidate) handled.get()).sourceRecordId()).isEqualTo(entry.outboxEntryId());
    }

    @Test
    void timerFiredSignalDispatchesThroughRuntimeDispatchServiceToEventHandler() {
        appendOutboxEntry(DeliveryLane.SIGNAL, OutboundKind.TIMER_FIRED_SIGNAL, "sc-c.timer-fired");
        AtomicReference<DispatchCandidate> handled = new AtomicReference<>();
        busPort.registerEventHandler("sc-c.timer-fired", envelope -> handled.set((DispatchCandidate) envelope.payload()));

        assertThat(dispatchService.dispatchPending()).singleElement().isInstanceOf(Dispatched.class);
        assertThat(handled.get().lane()).isEqualTo(ScBusLane.EVENT);
    }

    @Test
    void commandOutboxRowDispatchesToCommandLaneWithoutScdCommand() {
        appendOutboxEntry(DeliveryLane.COMMAND, OutboundKind.COMMAND, "topic.command");
        AtomicReference<DispatchCandidate> handled = new AtomicReference<>();
        busPort.registerCommandHandler("topic.command", envelope -> handled.set((DispatchCandidate) envelope.payload()));

        assertThat(dispatchService.dispatchPending()).singleElement().isInstanceOf(Dispatched.class);

        assertThat(handled.get().lane()).isEqualTo(ScBusLane.COMMAND);
        assertThat(Path.of("src/main/java/com/sovereign/connect/bus/contract/ScdCommand.java")).doesNotExist();
    }

    @Test
    void dispatchDoesNotMutateOutboxStatus() {
        OutboxEntry entry = appendOutboxEntry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event");
        busPort.registerEventHandler("topic.event", envelope -> { });

        dispatchService.dispatchPending();

        JdbcTemplate jdbc = new JdbcTemplate(scCDataSource);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM sc_c_outbox_entries WHERE outbox_entry_id = ?",
                String.class,
                entry.outboxEntryId().toString()
        )).isEqualTo(OutboxEntryStatus.PENDING.name());
        assertThat(jdbc.queryForObject(
                "SELECT updated_at_ms FROM sc_c_outbox_entries WHERE outbox_entry_id = ?",
                Long.class,
                entry.outboxEntryId().toString()
        )).isEqualTo(entry.updatedAt().toEpochMilli());
    }

    @Test
    void dispatchStatePersistenceRecordsTechnicalDispatchForDerivedRecordId() {
        OutboxEntry entry = appendOutboxEntry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event");
        busPort.registerEventHandler("topic.event", envelope -> { });

        dispatchService.dispatchPending();

        UUID dispatchRecordId = new DispatchRecordIdFactory().fromSourceRecordId(entry.outboxEntryId());
        assertThat(dispatchStateRepo.currentAttempt(dispatchRecordId).orElseThrow().state()).isEqualTo(DispatchState.DISPATCHED);
    }

    private OutboxEntry appendOutboxEntry(DeliveryLane lane, OutboundKind kind, String topic) {
        OutboxEntry entry = new OutboxEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "habitat-001",
                kind,
                lane,
                topic,
                "{\"opaque\":true}",
                null,
                "idem-" + UUID.randomUUID(),
                OutboxEntryStatus.PENDING,
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:01Z"),
                null
        );
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(scCDataSource);
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
        return entry;
    }
}
