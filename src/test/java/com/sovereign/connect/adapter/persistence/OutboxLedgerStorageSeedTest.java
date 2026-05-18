package com.sovereign.connect.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxLedgerStorageSeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void appendLedgerEntryPersistsDurably() {
        String jdbcUrl = jdbcUrl("ledger-durability");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);

        repository.appendLedgerEntry(ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015001"),
            "habitat-015",
            "act-001",
            SemanticKind.TEMPORAL_ACT_CREATED,
            "temporal-act-created:act-001"
        ));

        new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        List<Map<String, Object>> rows = jdbc(jdbcUrl).queryForList(
            "SELECT habitat_id, semantic_kind FROM sc_c_ledger_entries WHERE habitat_id = ?",
            "habitat-015"
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("HABITAT_ID")).isEqualTo("habitat-015");
            assertThat(row.get("SEMANTIC_KIND")).isEqualTo("TEMPORAL_ACT_CREATED");
        });
    }

    @Test
    void appendOutboxEntryPersistsDurably() {
        String jdbcUrl = jdbcUrl("outbox-durability");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        LedgerEntry ledger = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015002"),
            "habitat-015",
            "act-002",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-002"
        );

        repository.appendLedgerEntry(ledger);
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015102"),
            ledger.ledgerEntryId(),
            "habitat-015",
            "timer-fired-signal:act-002",
            "surface:bedroom-left"
        ));

        new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        List<Map<String, Object>> rows = jdbc(jdbcUrl).queryForList(
            "SELECT status, notification_target_ref FROM sc_c_outbox_entries WHERE habitat_id = ?",
            "habitat-015"
        );

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.get("STATUS")).isEqualTo("PENDING");
            assertThat(row.get("NOTIFICATION_TARGET_REF")).isEqualTo("surface:bedroom-left");
        });
    }

    @Test
    void duplicateLedgerIdempotencyKeyRejected() {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(
            dataSource(jdbcUrl("ledger-duplicate")),
            mapper(),
            clock
        );

        repository.appendLedgerEntry(ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015003"),
            "habitat-015",
            "act-003",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-001"
        ));

        assertThatThrownBy(() -> repository.appendLedgerEntry(ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015004"),
            "habitat-015",
            "act-004",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-001"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateOutboxIdempotencyKeyRejected() {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(
            dataSource(jdbcUrl("outbox-duplicate")),
            mapper(),
            clock
        );
        LedgerEntry ledger = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015005"),
            "habitat-015",
            "act-005",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-005"
        );
        repository.appendLedgerEntry(ledger);
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015105"),
            ledger.ledgerEntryId(),
            "habitat-015",
            "timer-fired-signal:act-001",
            "surface:bedroom-left"
        ));

        assertThatThrownBy(() -> repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015106"),
            ledger.ledgerEntryId(),
            "habitat-015",
            "timer-fired-signal:act-001",
            "surface:bedroom-right"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameIdempotencyKeyAllowedAcrossDifferentHabitats() {
        String jdbcUrl = jdbcUrl("habitat-scoped-idempotency");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        LedgerEntry ledgerA = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015006"),
            "habitat-A",
            "act-006",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-001"
        );
        LedgerEntry ledgerB = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015007"),
            "habitat-B",
            "act-007",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-001"
        );

        repository.appendLedgerEntry(ledgerA);
        repository.appendLedgerEntry(ledgerB);
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015107"),
            ledgerA.ledgerEntryId(),
            "habitat-A",
            "timer-fired-signal:act-001",
            "surface:bedroom-left"
        ));
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015108"),
            ledgerB.ledgerEntryId(),
            "habitat-B",
            "timer-fired-signal:act-001",
            "surface:bedroom-left"
        ));

        assertThat(jdbc(jdbcUrl).queryForObject("SELECT COUNT(*) FROM sc_c_ledger_entries", Integer.class))
            .isEqualTo(2);
        assertThat(jdbc(jdbcUrl).queryForObject("SELECT COUNT(*) FROM sc_c_outbox_entries", Integer.class))
            .isEqualTo(2);
    }

    @Test
    void outboxEntryRequiresExistingLedgerEntry() {
        String jdbcUrl = jdbcUrl("outbox-requires-ledger");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        LedgerEntry ledger = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015008"),
            "habitat-015",
            "act-008",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-008"
        );

        repository.appendLedgerEntry(ledger);
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015109"),
            ledger.ledgerEntryId(),
            "habitat-015",
            "timer-fired-signal:act-008",
            "surface:living-room"
        ));

        assertThat(jdbc(jdbcUrl).queryForObject("SELECT COUNT(*) FROM sc_c_outbox_entries", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void outboxEntryWithoutLedgerReferenceRejected() {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(
            dataSource(jdbcUrl("missing-ledger")),
            mapper(),
            clock
        );

        assertThatThrownBy(() -> repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015110"),
            UUID.fromString("00000000-0000-0000-0000-000000019999"),
            "habitat-015",
            "timer-fired-signal:missing",
            "surface:living-room"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void outboxEntryHabitatMustMatchLedgerHabitat() {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(
            dataSource(jdbcUrl("habitat-mismatch")),
            mapper(),
            clock
        );
        LedgerEntry ledger = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015011"),
            "habitat-A",
            "act-011",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-011"
        );
        repository.appendLedgerEntry(ledger);

        assertThatThrownBy(() -> repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015111"),
            ledger.ledgerEntryId(),
            "habitat-B",
            "timer-fired-signal:act-011",
            "surface:living-room"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notificationTargetRefCarriedOpaquely() {
        String jdbcUrl = jdbcUrl("opaque-target");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        LedgerEntry ledger = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015012"),
            "habitat-015",
            "act-012",
            SemanticKind.TIMER_FIRED,
            "timer-fired:act-012"
        );

        repository.appendLedgerEntry(ledger);
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015112"),
            ledger.ledgerEntryId(),
            "habitat-015",
            "timer-fired-signal:act-012",
            "surface:bedroom-left"
        ));

        assertThat(jdbc(jdbcUrl).queryForObject(
            "SELECT notification_target_ref FROM sc_c_outbox_entries WHERE outbox_entry_id = ?",
            String.class,
            "00000000-0000-0000-0000-000000015112"
        )).isEqualTo("surface:bedroom-left");
    }

    @Test
    void temporalActFiredLikeRecordCanBeStoredByLedgerOutboxSeed() {
        String jdbcUrl = jdbcUrl("temporal-like");
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        LedgerEntry ledger = ledgerEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015013"),
            "habitat-015",
            "act-013",
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:act-013"
        );

        repository.appendLedgerEntry(ledger);
        repository.appendOutboxEntry(outboxEntry(
            UUID.fromString("00000000-0000-0000-0000-000000015113"),
            ledger.ledgerEntryId(),
            "habitat-015",
            "timer-fired-signal:act-013",
            "surface:living-room"
        ));
        new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);

        assertThat(jdbc(jdbcUrl).queryForObject("SELECT COUNT(*) FROM sc_c_ledger_entries", Integer.class))
            .isEqualTo(1);
        assertThat(jdbc(jdbcUrl).queryForObject("SELECT COUNT(*) FROM sc_c_outbox_entries", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void noOutboxDispatcherSurfaceIntroduced() throws IOException {
        List<String> forbidden = List.of(
            "ScLedgerReadPort",
            "ScOutboxReadPort",
            "claimReady",
            "markClaimed",
            "markDispatched",
            "retryLoop",
            "org.nats",
            "io.nats",
            "JetStream",
            "sc_c_delivery_observations",
            "sc_c_terminal_responses",
            "sc_c_outbox_attempts"
        );

        for (String source : productionSources()) {
            assertThat(forbidden).noneMatch(source::contains);
        }
    }

    @Test
    void domainServicesRemainSqlFree() throws IOException {
        List<String> forbidden = List.of(
            "import java.sql",
            "import javax.sql.DataSource",
            "import org.springframework.jdbc",
            "JdbcTemplate",
            "import org.h2"
        );
        List<Path> packageRoots = List.of(
            Path.of("src/main/java/com/sovereign/connect/core/topology/service"),
            Path.of("src/main/java/com/sovereign/connect/core/topology/materialization")
        );

        for (Path root : packageRoots) {
            if (Files.notExists(root)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertThat(forbidden).noneMatch(source::contains);
                }
            }
        }
    }

    @Test
    void nonPendingOutboxAppendRejected() {
        String jdbcUrl = jdbcUrl("non-pending-outbox");
        H2BaseTopologyRepository repository =
            new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);

        UUID actId = UUID.randomUUID();
        UUID ledgerEntryId = UUID.randomUUID();

        LedgerEntry ledger = ledgerEntry(
            ledgerEntryId,
            "habitat-001",
            actId.toString(),
            SemanticKind.TEMPORAL_ACT_FIRED,
            "temporal-act-fired:" + actId
        );

        repository.appendLedgerEntry(ledger);

        OutboxEntry claimed = new OutboxEntry(
            UUID.randomUUID(),
            ledgerEntryId,
            "habitat-001",
            OutboundKind.TIMER_FIRED_SIGNAL,
            DeliveryLane.SIGNAL,
            "sc-c.timer-fired",
            "{\"kind\":\"timer-fired\"}",
            "surface:test",
            "timer-fired-claimed:" + actId,
            OutboxEntryStatus.CLAIMED,
            Instant.parse("2026-05-16T12:00:00Z"),
            Instant.parse("2026-05-16T12:00:00Z"),
            null
        );

        assertThatThrownBy(() -> repository.appendOutboxEntry(claimed))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PENDING");
    }

    private LedgerEntry ledgerEntry(
        UUID ledgerEntryId,
        String habitatId,
        String aggregateId,
        SemanticKind semanticKind,
        String idempotencyKey
    ) {
        return new LedgerEntry(
            ledgerEntryId,
            habitatId,
            semanticKind == SemanticKind.TEMPORAL_ACT_FIRED ? LedgerRecordClass.EVENT_OUTBOX : LedgerRecordClass.LEDGER_ONLY,
            "TEMPORAL_ACT",
            aggregateId,
            semanticKind,
            "application/vnd.sovereign.temporal-act+json",
            "{\"temporalActId\":\"" + aggregateId + "\"}",
            idempotencyKey,
            Instant.parse("2026-05-16T12:00:00Z"),
            null
        );
    }

    private OutboxEntry outboxEntry(
        UUID outboxEntryId,
        UUID ledgerEntryId,
        String habitatId,
        String idempotencyKey,
        String notificationTargetRef
    ) {
        return new OutboxEntry(
            outboxEntryId,
            ledgerEntryId,
            habitatId,
            OutboundKind.TIMER_FIRED_SIGNAL,
            DeliveryLane.SIGNAL,
            "sc-c.timer-fired",
            "{\"kind\":\"timer-fired\"}",
            notificationTargetRef,
            idempotencyKey,
            OutboxEntryStatus.PENDING,
            Instant.parse("2026-05-16T12:00:00Z"),
            Instant.parse("2026-05-16T12:00:00Z"),
            null
        );
    }

    private List<String> productionSources() throws IOException {
        Path root = Path.of("src/main/java");
        if (Files.notExists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    try {
                        return Files.readString(path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("failed to read " + path, ex);
                    }
                })
                .toList();
        }
    }

    private JdbcTemplate jdbc(String jdbcUrl) {
        return new JdbcTemplate(dataSource(jdbcUrl));
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private String jdbcUrl(String name) {
        String dbPath = tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
    }

    private DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "sa", "");
    }
}
