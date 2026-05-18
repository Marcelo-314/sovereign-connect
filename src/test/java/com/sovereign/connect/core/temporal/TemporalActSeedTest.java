package com.sovereign.connect.core.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.H2TemporalActRepository;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import com.sovereign.connect.core.temporal.model.CreatedByRef;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.model.TemporalActPayload;
import com.sovereign.connect.core.temporal.model.TemporalActStatus;
import com.sovereign.connect.core.temporal.port.TemporalActWritePort;
import com.sovereign.connect.core.temporal.service.TemporalActService;
import com.sovereign.connect.core.temporal.service.TemporalEngineRunner;
import com.sovereign.connect.core.temporal.service.TemporalEngineService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalActSeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-18T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void createSignalTemporalActPersistsDurably() {
        String jdbcUrl = jdbcUrl("create-durable");
        Fixture fixture = fixture(jdbcUrl);

        String id = fixture.actService.createSignalTemporalAct(
            "habitat-005",
            signalPayload(),
            Instant.parse("2026-05-18T12:05:00Z"),
            "surface:bedroom-left",
            createdBy()
        );

        Fixture recovered = fixture(jdbcUrl);
        TemporalAct recoveredAct = recovered.repository.findById("habitat-005", id).orElseThrow();
        assertThat(recoveredAct.status()).isEqualTo(TemporalActStatus.PENDING);
        assertThat(recoveredAct.payload()).isInstanceOf(SignalTemporalPayload.class);
        assertThat(recoveredAct.notificationTargetRef()).isEqualTo("surface:bedroom-left");
        assertThat(recoveredAct.createdByRef()).isEqualTo(createdBy());
    }

    @Test
    void repositoryDuplicateTemporalActIdRejected() {
        Fixture fixture = fixture(jdbcUrl("duplicate-id"));
        TemporalAct act = act("fixed-act", "habitat-005", TemporalActStatus.PENDING, Instant.parse("2026-05-18T12:05:00Z"));

        fixture.repository.insertCreated(act);

        assertThatThrownBy(() -> fixture.repository.insertCreated(act))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createTemporalActAppendsCreatedLedgerEntry() {
        Fixture fixture = fixture(jdbcUrl("created-ledger"));

        String id = fixture.actService.createSignalTemporalAct(
            "habitat-005", signalPayload(), Instant.parse("2026-05-18T12:05:00Z"), null, createdBy()
        );

        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-created:" + id)).isEqualTo(1);
    }

    @Test
    void createTemporalActAtomicityPreserved() {
        Fixture fixture = fixture(jdbcUrl("create-atomic"));

        String id = fixture.actService.createSignalTemporalAct(
            "habitat-005", signalPayload(), Instant.parse("2026-05-18T12:05:00Z"), null, createdBy()
        );

        assertThat(fixture.repository.findById("habitat-005", id)).isPresent();
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-created:" + id)).isEqualTo(1);
    }

    @Test
    void cancelNonTerminalActSucceeds() {
        Fixture fixture = fixture(jdbcUrl("cancel"));
        String id = fixture.actService.createSignalTemporalAct(
            "habitat-005", signalPayload(), Instant.parse("2026-05-18T12:05:00Z"), null, createdBy()
        );

        int cancelled = fixture.actService.cancelTemporalAct("habitat-005", id, Instant.parse("2026-05-18T12:01:00Z"));

        TemporalAct act = fixture.repository.findById("habitat-005", id).orElseThrow();
        assertThat(cancelled).isEqualTo(1);
        assertThat(act.status()).isEqualTo(TemporalActStatus.CANCELLED);
        assertThat(act.terminalAt()).isEqualTo(Instant.parse("2026-05-18T12:01:00Z"));
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-cancelled:" + id)).isEqualTo(1);
    }

    @Test
    void fireThenCancelReturnsAlreadyTerminal() {
        Fixture fixture = fixture(jdbcUrl("fire-then-cancel"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));
        int cancelled = fixture.actService.cancelTemporalAct("habitat-005", id, Instant.parse("2026-05-18T12:01:00Z"));

        assertThat(cancelled).isZero();
        assertThat(fixture.repository.findById("habitat-005", id).orElseThrow().status()).isEqualTo(TemporalActStatus.FIRED);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-cancelled:" + id)).isZero();
    }

    @Test
    void cancelThenFireDoesNotCreateTimerFired() {
        Fixture fixture = fixture(jdbcUrl("cancel-then-fire"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.actService.cancelTemporalAct("habitat-005", id, Instant.parse("2026-05-18T11:59:00Z"));
        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(fixture.repository.findById("habitat-005", id).orElseThrow().status()).isEqualTo(TemporalActStatus.CANCELLED);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-fired:" + id)).isZero();
        assertThat(outboxCount(fixture.jdbcUrl, "habitat-005", "timer-fired-signal:" + id)).isZero();
    }

    @Test
    void listActiveReturnsOnlyNonTerminalActs() {
        Fixture fixture = fixture(jdbcUrl("active-only"));
        String pending = fixture.actService.createSignalTemporalAct(
            "habitat-005", signalPayload(), Instant.parse("2026-05-18T12:05:00Z"), null, createdBy()
        );
        String fired = createDueAct(fixture, "habitat-005");
        String cancelled = fixture.actService.createSignalTemporalAct(
            "habitat-005", signalPayload(), Instant.parse("2026-05-18T12:05:00Z"), null, createdBy()
        );
        fixture.engine.fireDueTemporalActOnce("habitat-005", fired, Instant.parse("2026-05-18T12:00:00Z"));
        fixture.actService.cancelTemporalAct("habitat-005", cancelled, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(fixture.repository.listActive("habitat-005"))
            .extracting(TemporalAct::temporalActId)
            .containsExactly(pending);
    }

    @Test
    void fireTransitionAdvancesStatusToFired() {
        Fixture fixture = fixture(jdbcUrl("fire-status"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        TemporalAct act = fixture.repository.findById("habitat-005", id).orElseThrow();
        assertThat(act.status()).isEqualTo(TemporalActStatus.FIRED);
        assertThat(act.firedAt()).isEqualTo(Instant.parse("2026-05-18T12:00:00Z"));
        assertThat(act.terminalAt()).isEqualTo(Instant.parse("2026-05-18T12:00:00Z"));
    }

    @Test
    void fireRequiresDueAtReached() {
        Fixture fixture = fixture(jdbcUrl("not-due"));
        String id = fixture.actService.createSignalTemporalAct(
            "habitat-005", signalPayload(), Instant.parse("2026-05-18T12:05:00Z"), null, createdBy()
        );

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(fixture.repository.findById("habitat-005", id).orElseThrow().status()).isEqualTo(TemporalActStatus.PENDING);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-fired:" + id)).isZero();
        assertThat(outboxCount(fixture.jdbcUrl, "habitat-005", "timer-fired-signal:" + id)).isZero();
    }

    @Test
    void fireIdempotencyGuardPreventsDoubleFire() {
        Fixture fixture = fixture(jdbcUrl("double-fire"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));
        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:01Z"));

        assertThat(fixture.repository.findById("habitat-005", id).orElseThrow().status()).isEqualTo(TemporalActStatus.FIRED);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-fired:" + id)).isEqualTo(1);
        assertThat(outboxCount(fixture.jdbcUrl, "habitat-005", "timer-fired-signal:" + id)).isEqualTo(1);
    }

    @Test
    void fireLedgerEntryCreatedAtomically() {
        Fixture fixture = fixture(jdbcUrl("fire-ledger"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-fired:" + id)).isEqualTo(1);
        assertThat(semanticKind(fixture.jdbcUrl, "temporal-act-fired:" + id)).isEqualTo("TIMER_FIRED");
    }

    @Test
    void fireOutboxEntryCreatedAtomically() {
        Fixture fixture = fixture(jdbcUrl("fire-outbox"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(outboxCount(fixture.jdbcUrl, "habitat-005", "timer-fired-signal:" + id)).isEqualTo(1);
        assertThat(outboxStatus(fixture.jdbcUrl, "timer-fired-signal:" + id)).isEqualTo("PENDING");
    }

    @Test
    void notificationTargetRefCarriedInFiringRecord() {
        Fixture fixture = fixture(jdbcUrl("target-ref"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(notificationTargetRef(fixture.jdbcUrl, "timer-fired-signal:" + id)).isEqualTo("surface:bedroom-left");
    }

    @Test
    void misfiredClassificationOnStartupForOverdueActs() {
        Fixture fixture = fixture(jdbcUrl("misfire"));
        String first = createDueAct(fixture, "habitat-005");
        String second = createDueAct(fixture, "habitat-005");

        fixture.engine.classifyMisfires("habitat-005", Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(fixture.repository.findById("habitat-005", first).orElseThrow().status()).isEqualTo(TemporalActStatus.MISFIRED);
        assertThat(fixture.repository.findById("habitat-005", second).orElseThrow().status()).isEqualTo(TemporalActStatus.MISFIRED);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-misfired:" + first)).isEqualTo(1);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-misfired:" + second)).isEqualTo(1);
    }

    @Test
    void misfiredActIsNotRefired() {
        Fixture fixture = fixture(jdbcUrl("misfire-no-refire"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.classifyMisfires("habitat-005", Instant.parse("2026-05-18T12:00:00Z"));
        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:01:00Z"));

        assertThat(fixture.repository.findById("habitat-005", id).orElseThrow().status()).isEqualTo(TemporalActStatus.MISFIRED);
        assertThat(ledgerCount(fixture.jdbcUrl, "habitat-005", "temporal-act-fired:" + id)).isZero();
        assertThat(outboxCount(fixture.jdbcUrl, "habitat-005", "timer-fired-signal:" + id)).isZero();
    }

    @Test
    void misfiredActRemainsQueryable() {
        Fixture fixture = fixture(jdbcUrl("misfire-query"));
        String id = createDueAct(fixture, "habitat-005");

        fixture.engine.classifyMisfires("habitat-005", Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(fixture.repository.listMisfired("habitat-005")).extracting(TemporalAct::temporalActId).contains(id);
        assertThat(fixture.repository.listTerminal("habitat-005")).extracting(TemporalAct::temporalActId).contains(id);
        assertThat(fixture.repository.listActive("habitat-005")).extracting(TemporalAct::temporalActId).doesNotContain(id);
    }

    @Test
    void pollingIntervalIsConfigurable() {
        Fixture fixture = fixture(jdbcUrl("runner-interval"));

        TemporalEngineRunner custom = new TemporalEngineRunner(fixture.engine, "habitat-005", clock, 250L);
        TemporalEngineRunner defaultRunner = new TemporalEngineRunner(fixture.engine, "habitat-005", clock);

        assertThat(custom.pollingIntervalMs()).isEqualTo(250L);
        assertThat(defaultRunner.pollingIntervalMs()).isLessThanOrEqualTo(1000L);
    }

    @Test
    void durabilityAfterRepositoryRecreation() {
        String jdbcUrl = jdbcUrl("temporal-durability");
        Fixture fixture = fixture(jdbcUrl);
        String id = createDueAct(fixture, "habitat-005");
        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        Fixture recovered = fixture(jdbcUrl);

        assertThat(recovered.repository.findById("habitat-005", id).orElseThrow().status()).isEqualTo(TemporalActStatus.FIRED);
        assertThat(ledgerCount(jdbcUrl, "habitat-005", "temporal-act-fired:" + id)).isEqualTo(1);
        assertThat(outboxStatus(jdbcUrl, "timer-fired-signal:" + id)).isEqualTo("PENDING");
    }

    @Test
    void domainServicesHaveNoSqlImports() throws IOException {
        Path root = Path.of("src/main/java/com/sovereign/connect/core/temporal/service");
        List<String> forbidden = List.of("import java.sql", "import javax.sql", "JdbcTemplate", "DataSource", "org.h2");

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertThat(forbidden).noneMatch(source::contains);
            }
        }
    }

    @Test
    void virtualThreadRunnerDoesNotUseScheduledAnnotation() {
        assertThat(Arrays.stream(TemporalEngineRunner.class.getDeclaredMethods())
            .map(Method::getDeclaredAnnotations)
            .flatMap(Arrays::stream)
            .map(annotation -> annotation.annotationType().getName())
            .toList()).doesNotContain(Scheduled.class.getName());
    }

    @Test
    void targetlessSignalTimerDoesNotRequireTopologyTarget() {
        Fixture fixture = fixture(jdbcUrl("targetless"));
        SignalTemporalPayload payload = new SignalTemporalPayload("Wake up", "alarm");

        String id = fixture.actService.createSignalTemporalAct(
            "habitat-005",
            payload,
            Instant.parse("2026-05-18T12:00:00Z"),
            "surface:bedroom-left",
            createdBy()
        );
        fixture.engine.fireDueTemporalActOnce("habitat-005", id, Instant.parse("2026-05-18T12:00:00Z"));

        assertThat(SignalTemporalPayload.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("label", "signalKind");
        assertThat(TemporalActPayload.class.getPermittedSubclasses())
            .extracting(Class::getSimpleName)
            .containsExactly("SignalTemporalPayload");
        assertThat(notificationTargetRef(fixture.jdbcUrl, "timer-fired-signal:" + id)).isEqualTo("surface:bedroom-left");
    }

    @Test
    void insertCreatedRejectsNonPendingTemporalAct() {
        DataSource dataSource = dataSource(jdbcUrl("non-pending-insert"));
        H2TemporalActRepository repository = new H2TemporalActRepository(dataSource, mapper(), clock);
        Instant now = Instant.now(clock);

        TemporalAct firedAct = new TemporalAct(
            UUID.randomUUID().toString(),
            "habitat-001",
            TemporalActStatus.FIRED,
            now.plusSeconds(60),
            new SignalTemporalPayload("alarm", "timer"),
            null,
            new CreatedByRef("hub-test"),
            null,
            now,
            now,
            now,
            now,
            "fired"
        );

        assertThatThrownBy(() -> repository.insertCreated(firedAct))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PENDING");
    }

    @Test
    void insertCreatedRejectsTerminalMetadata() {
        DataSource dataSource = dataSource(jdbcUrl("terminal-metadata-insert"));
        H2TemporalActRepository repository = new H2TemporalActRepository(dataSource, mapper(), clock);
        Instant now = Instant.now(clock);

        TemporalAct invalidAct = new TemporalAct(
            UUID.randomUUID().toString(),
            "habitat-001",
            TemporalActStatus.PENDING,
            now.plusSeconds(60),
            new SignalTemporalPayload("alarm", "timer"),
            null,
            new CreatedByRef("hub-test"),
            null,
            now,
            now,
            now,
            null,
            null
        );

        assertThatThrownBy(() -> repository.insertCreated(invalidAct))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-terminal");
    }

    @Test
    void temporalEngineRunnerDoesNotUseThreadSleep() throws IOException {
        Path source = Path.of(
            "src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineRunner.java"
        );

        String text = Files.readString(source);

        assertThat(text).doesNotContain("Thread.sleep");
        assertThat(text).doesNotContain("Thread.onSpinWait");
        assertThat(text).doesNotContain("@Scheduled");
        assertThat(text).contains("Thread.ofVirtual");
        assertThat(text).contains("LockSupport.parkNanos");
        assertThat(text).contains("LockSupport.unpark");
        assertThat(text).contains(".join(");
    }

    @Test
    void runnerStopsCooperativelyAndAwaitWaitsForTermination() {
        Fixture fixture = fixture(jdbcUrl("runner-stop"));
        TemporalEngineRunner runner = new TemporalEngineRunner(
            fixture.engine,
            "habitat-001",
            clock,
            5_000L
        );

        runner.start();
        runner.stop();

        assertThat(runner.awaitStopped(Duration.ofSeconds(1))).isTrue();
        assertThat(runner.isRunning()).isFalse();
    }

    private String createDueAct(Fixture fixture, String habitatId) {
        return fixture.actService.createSignalTemporalAct(
            habitatId,
            signalPayload(),
            Instant.parse("2026-05-18T12:00:00Z"),
            "surface:bedroom-left",
            createdBy()
        );
    }

    private TemporalAct act(String id, String habitatId, TemporalActStatus status, Instant dueAt) {
        return new TemporalAct(
            id,
            habitatId,
            status,
            dueAt,
            signalPayload(),
            "surface:bedroom-left",
            createdBy(),
            null,
            Instant.parse("2026-05-18T11:59:00Z"),
            Instant.parse("2026-05-18T11:59:00Z"),
            null,
            null,
            null
        );
    }

    private SignalTemporalPayload signalPayload() {
        return new SignalTemporalPayload("Wake up", "alarm");
    }

    private CreatedByRef createdBy() {
        return new CreatedByRef("creator:test");
    }

    private Fixture fixture(String jdbcUrl) {
        DataSource dataSource = dataSource(jdbcUrl);
        ObjectMapper mapper = mapper();
        H2TemporalActRepository repository = new H2TemporalActRepository(dataSource, mapper, clock);
        H2BaseTopologyRepository ledgerOutboxRepository = new H2BaseTopologyRepository(dataSource, mapper, clock);
        TransactionTemplate txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        TemporalActService actService = new TemporalActService(
            repository,
            repository,
            ledgerOutboxRepository,
            txTemplate,
            mapper,
            clock
        );
        TemporalEngineService engine = new TemporalEngineService(
            repository,
            repository,
            ledgerOutboxRepository,
            ledgerOutboxRepository,
            txTemplate,
            mapper,
            clock
        );
        return new Fixture(jdbcUrl, repository, actService, engine);
    }

    private int ledgerCount(String jdbcUrl, String habitatId, String idempotencyKey) {
        return jdbc(jdbcUrl).queryForObject(
            "SELECT COUNT(*) FROM sc_c_ledger_entries WHERE habitat_id = ? AND idempotency_key = ?",
            Integer.class,
            habitatId,
            idempotencyKey
        );
    }

    private int outboxCount(String jdbcUrl, String habitatId, String idempotencyKey) {
        return jdbc(jdbcUrl).queryForObject(
            "SELECT COUNT(*) FROM sc_c_outbox_entries WHERE habitat_id = ? AND idempotency_key = ?",
            Integer.class,
            habitatId,
            idempotencyKey
        );
    }

    private String semanticKind(String jdbcUrl, String idempotencyKey) {
        return jdbc(jdbcUrl).queryForObject(
            "SELECT semantic_kind FROM sc_c_ledger_entries WHERE idempotency_key = ?",
            String.class,
            idempotencyKey
        );
    }

    private String outboxStatus(String jdbcUrl, String idempotencyKey) {
        return jdbc(jdbcUrl).queryForObject(
            "SELECT status FROM sc_c_outbox_entries WHERE idempotency_key = ?",
            String.class,
            idempotencyKey
        );
    }

    private String notificationTargetRef(String jdbcUrl, String idempotencyKey) {
        return jdbc(jdbcUrl).queryForObject(
            "SELECT notification_target_ref FROM sc_c_outbox_entries WHERE idempotency_key = ?",
            String.class,
            idempotencyKey
        );
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

    private record Fixture(
        String jdbcUrl,
        H2TemporalActRepository repository,
        TemporalActService actService,
        TemporalEngineService engine
    ) {
    }
}
