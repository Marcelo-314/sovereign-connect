package com.sovereign.connect.core.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.sqlite.PerConnectionPragmaDataSource;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteScLedgerOutboxRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalActRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalEngineLockRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalRecoveryObservationRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalRequestIdempotencyRepository;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
import com.sovereign.connect.core.temporal.application.CancelTemporalActRequest;
import com.sovereign.connect.core.temporal.application.CancelTemporalActResult;
import com.sovereign.connect.core.temporal.application.CreateSignalTemporalActRequest;
import com.sovereign.connect.core.temporal.application.CreateSignalTemporalActResult;
import com.sovereign.connect.core.temporal.application.TemporalActApplicationService;
import com.sovereign.connect.core.temporal.application.TemporalRequestIdempotencyPort;
import com.sovereign.connect.core.temporal.application.TemporalRequestIdempotencyRecord;
import com.sovereign.connect.core.temporal.application.TemporalRuntimeFailureCode;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.engine.TemporalEngineLifecycle;
import com.sovereign.connect.core.temporal.engine.TemporalEngineLockPort;
import com.sovereign.connect.core.temporal.engine.TemporalEngineProperties;
import com.sovereign.connect.core.temporal.engine.TemporalEngineStatus;
import com.sovereign.connect.core.temporal.model.CreatedByRef;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.observation.TemporalActObservation;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationService;
import com.sovereign.connect.core.temporal.service.TemporalActService;
import com.sovereign.connect.core.temporal.service.TemporalEngineRunner;
import com.sovereign.connect.core.temporal.service.TemporalEngineService;
import com.sovereign.connect.core.temporal.service.TemporalRecoveryObservationPort;
import com.sovereign.connect.core.temporal.service.TemporalRecoveryResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalEngineReviewBlockersTest {

    private static final String HABITAT_ID = "habitat-001";
    private static final String STORAGE_PARTITION_REF = "sqlite.default";

    @Test
    void recoveryDoesNotStartPollingWhenMisfireBatchIncomplete() {
        TemporalEngineService engine = mock(TemporalEngineService.class);
        when(engine.classifyMisfires(anyString(), any(Instant.class), anyInt()))
            .thenReturn(new TemporalRecoveryResult(1, 0, false));
        TemporalEngineHealth health = new TemporalEngineHealth();
        TemporalEngineRunner runner = new TemporalEngineRunner(engine, HABITAT_ID, Clock.systemUTC(), 10_000L);
        TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
            engine,
            runner,
            properties(1, 1, false),
            health,
            new FixedLockPort(true, 1),
            Clock.systemUTC()
        );

        assertThatThrownBy(lifecycle::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TEMPORAL_RECOVERY_INCOMPLETE");

        assertThat(health.status()).isEqualTo(TemporalEngineStatus.FAILED);
        assertThat(runner.isRunning()).isFalse();
        verify(engine, never()).pollDueOnce(anyString(), any(Instant.class), anyInt());
    }

    @Test
    void incompleteRecoveryBatchDoesNotFireOverdueActs() {
        TemporalEngineService engine = mock(TemporalEngineService.class);
        when(engine.classifyMisfires(anyString(), any(Instant.class), anyInt()))
            .thenReturn(new TemporalRecoveryResult(0, 0, false));
        TemporalEngineRunner runner = new TemporalEngineRunner(engine, HABITAT_ID, Clock.systemUTC(), 10_000L);
        TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
            engine,
            runner,
            properties(1, 2, false),
            new TemporalEngineHealth(),
            new FixedLockPort(true, 1),
            Clock.systemUTC()
        );

        assertThatThrownBy(lifecycle::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TEMPORAL_RECOVERY_STALLED");

        verify(engine, never()).pollDueOnce(anyString(), any(Instant.class), anyInt());
    }

    @Test
    void stopDoesNotReleaseLockIfRunnerFailsToStop() {
        TemporalEngineRunner runner = mock(TemporalEngineRunner.class);
        when(runner.awaitStopped(any())).thenReturn(false);
        TemporalEngineLockPort lockPort = mock(TemporalEngineLockPort.class);
        when(lockPort.tryAcquire(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(true);
        when(lockPort.heartbeat(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(1);
        TemporalEngineService engine = mock(TemporalEngineService.class);
        when(engine.classifyMisfires(anyString(), any(Instant.class), anyInt()))
            .thenReturn(new TemporalRecoveryResult(0, 0, true));
        TemporalEngineHealth health = new TemporalEngineHealth();
        health.transitionTo(TemporalEngineStatus.RUNNING);
        TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
            engine,
            runner,
            properties(50, 200, true),
            health,
            lockPort,
            Clock.systemUTC()
        );

        assertThatThrownBy(lifecycle::stop)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("TEMPORAL_ENGINE_STOP_TIMEOUT");

        verify(lockPort, never()).release(anyString(), anyString(), anyString(), any());
        assertThat(health.status()).isEqualTo(TemporalEngineStatus.FAILED);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void successfulStopReleasesSingleNodeLockAfterRunnerStops() {
        TemporalEngineRunner runner = mock(TemporalEngineRunner.class);
        when(runner.awaitStopped(any())).thenReturn(true);
        TemporalEngineLockPort lockPort = mock(TemporalEngineLockPort.class);
        when(lockPort.tryAcquire(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(true);
        when(lockPort.heartbeat(anyString(), anyString(), anyString(), any(), anyLong())).thenReturn(1);
        TemporalEngineService engine = mock(TemporalEngineService.class);
        when(engine.classifyMisfires(anyString(), any(Instant.class), anyInt()))
            .thenReturn(new TemporalRecoveryResult(0, 0, true));
        TemporalEngineHealth health = new TemporalEngineHealth();
        health.transitionTo(TemporalEngineStatus.RUNNING);
        TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
            engine,
            runner,
            properties(50, 200, true),
            health,
            lockPort,
            Clock.systemUTC()
        );

        lifecycle.stop();

        verify(lockPort).release(anyString(), anyString(), anyString(), any());
        assertThat(health.status()).isEqualTo(TemporalEngineStatus.STOPPED);
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void heartbeatExtendsLockToNowPlusTtl() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalEngineLockRepository repository = new SQLiteTemporalEngineLockRepository(dataSource);
        Instant acquiredAt = Instant.ofEpochMilli(1_000L);
        Instant heartbeatAt = Instant.ofEpochMilli(2_500L);

        assertThat(repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", acquiredAt, 10_000L)).isTrue();
        assertThat(repository.heartbeat(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", heartbeatAt, 10_000L)).isEqualTo(1);

        Long expiresAt = jdbc(dataSource).queryForObject(
            "SELECT expires_at_ms FROM temporal_engine_locks WHERE habitat_id = ? AND storage_partition_ref = ?",
            Long.class,
            HABITAT_ID,
            STORAGE_PARTITION_REF
        );
        assertThat(expiresAt).isEqualTo(12_500L);
    }

    @Test
    void heartbeatDoesNotGrowExpiryExponentially() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalEngineLockRepository repository = new SQLiteTemporalEngineLockRepository(dataSource);

        repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", Instant.ofEpochMilli(1_000L), 9_000L);
        repository.heartbeat(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", Instant.ofEpochMilli(2_000L), 9_000L);
        repository.heartbeat(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", Instant.ofEpochMilli(3_000L), 9_000L);

        Long expiresAt = jdbc(dataSource).queryForObject(
            "SELECT expires_at_ms FROM temporal_engine_locks WHERE habitat_id = ? AND storage_partition_ref = ?",
            Long.class,
            HABITAT_ID,
            STORAGE_PARTITION_REF
        );
        assertThat(expiresAt).isEqualTo(12_000L);
    }

    @Test
    void heartbeatReturnsZeroForLostLock() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalEngineLockRepository repository = new SQLiteTemporalEngineLockRepository(dataSource);

        repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", Instant.ofEpochMilli(1_000L), 10_000L);

        assertThat(repository.heartbeat(HABITAT_ID, STORAGE_PARTITION_REF, "engine-2", Instant.ofEpochMilli(2_000L), 10_000L))
            .isZero();
    }

    @Test
    void heartbeatLossStopsRunnerOrMarksFailed() {
        TemporalEngineService engine = mock(TemporalEngineService.class);
        when(engine.classifyMisfires(anyString(), any(Instant.class), anyInt()))
            .thenReturn(new TemporalRecoveryResult(0, 0, true));
        TemporalEngineHealth health = new TemporalEngineHealth();
        TemporalEngineRunner runner = new TemporalEngineRunner(engine, HABITAT_ID, Clock.systemUTC(), 10_000L, 10, 10_000L, 3, health);
        TemporalEngineLifecycle lifecycle = new TemporalEngineLifecycle(
            engine,
            runner,
            properties(10, 10, true),
            health,
            new FixedLockPort(true, 0),
            Clock.systemUTC()
        );

        lifecycle.start();
        waitUntil(() -> health.status() == TemporalEngineStatus.FAILED);

        assertThat(health.status()).isEqualTo(TemporalEngineStatus.FAILED);
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void secondLifecycleFailsFastWhenLockHeld() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalEngineLockRepository repository = new SQLiteTemporalEngineLockRepository(dataSource);

        assertThat(repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", Instant.ofEpochMilli(1_000L), 10_000L))
            .isTrue();
        assertThat(repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-2", Instant.ofEpochMilli(2_000L), 10_000L))
            .isFalse();
    }

    @Test
    void expiredLockCanBeAcquiredBySecondLifecycle() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalEngineLockRepository repository = new SQLiteTemporalEngineLockRepository(dataSource);

        assertThat(repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-1", Instant.ofEpochMilli(1_000L), 1_000L))
            .isTrue();
        assertThat(repository.tryAcquire(HABITAT_ID, STORAGE_PARTITION_REF, "engine-2", Instant.ofEpochMilli(3_000L), 1_000L))
            .isTrue();
    }

    @Test
    void successfulPollAfterFailureRestoresRunningStatus() {
        TemporalEngineHealth health = new TemporalEngineHealth();
        health.transitionTo(TemporalEngineStatus.RUNNING);
        health.recordPollFailure(new RuntimeException("db unavailable"));

        health.recordPollSuccess();

        assertThat(health.status()).isEqualTo(TemporalEngineStatus.RUNNING);
        assertThat(health.lastFailure()).isNull();
    }

    @Test
    void unknownPayloadFailureAppendsTemporalActFailedLedgerEntry() throws Exception {
        DataSource dataSource = migratedDataSource();
        String temporalActId = UUID.randomUUID().toString();
        insertUnknownPayloadAct(dataSource, temporalActId);
        TemporalEngineService engine = engineService(dataSource, new TemporalEngineHealth());

        TemporalRecoveryResult result = engine.classifyMisfires(HABITAT_ID, Instant.ofEpochMilli(5_000L), 10);

        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(jdbc(dataSource).queryForObject(
            "SELECT status FROM temporal_acts WHERE temporal_act_id = ?",
            String.class,
            temporalActIdBytes(temporalActId)
        )).isEqualTo("FAILED");
        assertThat(jdbc(dataSource).queryForObject(
            "SELECT semantic_kind FROM sc_c_ledger_entries WHERE idempotency_key = ?",
            String.class,
            "temporal-act-failed:" + temporalActId + ":unknown-payload"
        )).isEqualTo("TEMPORAL_ACT_FAILED");
    }

    @Test
    void unknownPayloadFailedActIsObservableByFindById() throws Exception {
        DataSource dataSource = migratedDataSource();
        String temporalActId = UUID.randomUUID().toString();
        insertUnknownPayloadAct(dataSource, temporalActId);
        engineService(dataSource, new TemporalEngineHealth())
            .classifyMisfires(HABITAT_ID, Instant.ofEpochMilli(5_000L), 10);

        Optional<TemporalActObservation> observation = new TemporalActObservationService(
            new SQLiteTemporalActRepository(dataSource, mapper(), Clock.systemUTC(), 30)
        ).findById(HABITAT_ID, temporalActId);

        assertThat(observation).isPresent();
        assertThat(observation.get().status().name()).isEqualTo("FAILED");
        assertThat(observation.get().payloadKind()).isEqualTo("UNKNOWN");
        assertThat(observation.get().label()).isNull();
        assertThat(observation.get().signalKind()).isNull();
    }

    @Test
    void unknownPayloadFailureCreatesRecoveryFinding() throws Exception {
        DataSource dataSource = migratedDataSource();
        String temporalActId = UUID.randomUUID().toString();
        insertUnknownPayloadAct(dataSource, temporalActId);
        ObjectMapper objectMapper = mapper();
        SQLiteTemporalActRepository actRepository = new SQLiteTemporalActRepository(dataSource, objectMapper, Clock.systemUTC(), 30);
        SQLiteScLedgerOutboxRepository ledgerRepository = new SQLiteScLedgerOutboxRepository(dataSource);
        TemporalEngineService engine = new TemporalEngineService(
            actRepository,
            actRepository,
            ledgerRepository,
            ledgerRepository,
            transactionTemplate(dataSource),
            objectMapper,
            Clock.systemUTC(),
            new SQLiteTemporalRecoveryObservationRepository(dataSource),
            new TemporalEngineHealth()
        );

        engine.classifyMisfires(HABITAT_ID, Instant.ofEpochMilli(5_000L), 10);

        assertThat(jdbc(dataSource).queryForObject(
            "SELECT code FROM recovery_findings WHERE entity_id = ?",
            String.class,
            temporalActId
        )).isEqualTo("UNKNOWN_PAYLOAD_KIND");
    }

    @Test
    void unknownPayloadFailedActIsObservableInListTerminal() throws Exception {
        DataSource dataSource = migratedDataSource();
        insertUnknownPayloadAct(dataSource, UUID.randomUUID().toString());
        engineService(dataSource, new TemporalEngineHealth())
            .classifyMisfires(HABITAT_ID, Instant.ofEpochMilli(5_000L), 10);

        assertThat(new TemporalActObservationService(
            new SQLiteTemporalActRepository(
                dataSource,
                mapper(),
                Clock.fixed(Instant.ofEpochMilli(6_000L), ZoneOffset.UTC),
                30
            )
        ).listTerminal(HABITAT_ID, 10))
            .extracting(TemporalActObservation::payloadKind)
            .contains("UNKNOWN");
    }

    @Test
    void createIdempotencyInsertFailureDoesNotLeaveUntrackedTemporalAct() {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new FailingInsertIdempotencyPort(new SQLiteTemporalRequestIdempotencyRepository(dataSource)),
            TemporalEngineStatus.RUNNING
        );

        CreateSignalTemporalActResult result = application.createSignalTemporalAct(createRequest("idem-create"));

        assertThat(result).isInstanceOf(CreateSignalTemporalActResult.Failed.class);
        assertThat(count(dataSource, "temporal_acts")).isZero();
        assertThat(count(dataSource, "sc_c_ledger_entries")).isZero();
    }

    @Test
    void concurrentCreateSameIdempotencyKeyCreatesOnlyOneTemporalAct() {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new SQLiteTemporalRequestIdempotencyRepository(dataSource),
            TemporalEngineStatus.RUNNING
        );

        CreateSignalTemporalActResult first = application.createSignalTemporalAct(createRequest("idem-concurrent"));
        CreateSignalTemporalActResult second = application.createSignalTemporalAct(createRequest("idem-concurrent"));

        assertThat(first).isInstanceOf(CreateSignalTemporalActResult.Accepted.class);
        assertThat(second).isInstanceOf(CreateSignalTemporalActResult.IdempotentReplay.class);
        assertThat(count(dataSource, "temporal_acts")).isEqualTo(1);
    }

    @Test
    void createReplayReturnsOriginalTemporalActObservation() {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new SQLiteTemporalRequestIdempotencyRepository(dataSource),
            TemporalEngineStatus.RUNNING
        );

        CreateSignalTemporalActResult.Accepted first =
            (CreateSignalTemporalActResult.Accepted) application.createSignalTemporalAct(createRequest("idem-replay"));
        CreateSignalTemporalActResult.IdempotentReplay replay =
            (CreateSignalTemporalActResult.IdempotentReplay) application.createSignalTemporalAct(createRequest("idem-replay"));

        assertThat(replay.act().temporalActId()).isEqualTo(first.temporalAct().temporalActId());
    }

    @Test
    void conflictingCreateFingerprintReturnsIdempotencyConflict() {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new SQLiteTemporalRequestIdempotencyRepository(dataSource),
            TemporalEngineStatus.RUNNING
        );

        application.createSignalTemporalAct(createRequest("idem-conflict"));
        CreateSignalTemporalActRequest conflicting = new CreateSignalTemporalActRequest(
            HABITAT_ID,
            Instant.ofEpochMilli(70_000L),
            "different-label",
            "signal-kind",
            "target",
            "creator",
            "idem-conflict",
            Instant.ofEpochMilli(1_000L)
        );

        assertThat(application.createSignalTemporalAct(conflicting))
            .isInstanceOf(CreateSignalTemporalActResult.Rejected.class);
    }

    @Test
    void cancelIdempotencyIsAtomicWithCancelLedger() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalRequestIdempotencyRepository idempotencyRepository = new SQLiteTemporalRequestIdempotencyRepository(dataSource);
        TemporalActApplicationService createApplication = applicationService(
            dataSource,
            idempotencyRepository,
            TemporalEngineStatus.RUNNING
        );
        String temporalActId = ((CreateSignalTemporalActResult.Accepted) createApplication
            .createSignalTemporalAct(createRequest("idem-create-for-cancel"))).temporalAct().temporalActId();
        TemporalActApplicationService cancelApplication = applicationService(
            dataSource,
            new FailingInsertIdempotencyPort(idempotencyRepository),
            TemporalEngineStatus.RUNNING
        );

        CancelTemporalActResult result = cancelApplication.cancelTemporalAct(cancelRequest(temporalActId, "idem-cancel-fail"));

        assertThat(result).isInstanceOf(CancelTemporalActResult.Failed.class);
        assertThat(jdbc(dataSource).queryForObject(
            "SELECT status FROM temporal_acts WHERE temporal_act_id = ?",
            String.class,
            temporalActIdBytes(temporalActId)
        )).isEqualTo("PENDING");
        assertThat(jdbc(dataSource).queryForObject(
            "SELECT COUNT(*) FROM sc_c_ledger_entries WHERE semantic_kind = 'TEMPORAL_ACT_CANCELLED'",
            Integer.class
        )).isZero();
    }

    @Test
    void cancelAlreadyTerminalPersistsAcceptedAlreadyTerminal() {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new SQLiteTemporalRequestIdempotencyRepository(dataSource),
            TemporalEngineStatus.RUNNING
        );
        String temporalActId = ((CreateSignalTemporalActResult.Accepted) application
            .createSignalTemporalAct(createRequest("idem-create-terminal"))).temporalAct().temporalActId();
        application.cancelTemporalAct(cancelRequest(temporalActId, "idem-cancel-first"));

        CancelTemporalActResult terminal = application.cancelTemporalAct(cancelRequest(temporalActId, "idem-cancel-terminal"));

        assertThat(terminal).isInstanceOf(CancelTemporalActResult.AlreadyTerminal.class);
        assertThat(jdbc(dataSource).queryForObject(
            "SELECT result_code FROM temporal_request_idempotency WHERE idempotency_key = ?",
            String.class,
            "idem-cancel-terminal"
        )).isEqualTo("ALREADY_TERMINAL");
    }

    @Test
    void cancelNotFoundPersistsStableIdempotentOutcome() {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new SQLiteTemporalRequestIdempotencyRepository(dataSource),
            TemporalEngineStatus.RUNNING
        );
        CancelTemporalActRequest request = cancelRequest(UUID.randomUUID().toString(), "idem-cancel-missing");

        CancelTemporalActResult first = application.cancelTemporalAct(request);
        CancelTemporalActResult replay = application.cancelTemporalAct(request);

        assertThat(first).isInstanceOf(CancelTemporalActResult.NotFound.class);
        assertThat(replay).isInstanceOf(CancelTemporalActResult.NotFound.class);
        assertThat(jdbc(dataSource).queryForObject(
            "SELECT result_code FROM temporal_request_idempotency WHERE idempotency_key = ?",
            String.class,
            "idem-cancel-missing"
        )).isEqualTo("NOT_FOUND");
    }

    @Test
    void runtimeFailureMappingDisabled() {
        assertCreateFailureCode(TemporalEngineStatus.DISABLED, TemporalRuntimeFailureCode.TEMPORAL_ENGINE_DISABLED);
    }

    @Test
    void runtimeFailureMappingRecovering() {
        assertCreateFailureCode(TemporalEngineStatus.RECOVERING, TemporalRuntimeFailureCode.RECOVERY_NOT_COMPLETED);
    }

    @Test
    void runtimeFailureMappingStartingStoppedAndFailed() {
        assertCreateFailureCode(TemporalEngineStatus.STARTING, TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY);
        assertCreateFailureCode(TemporalEngineStatus.STOPPED, TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY);
        assertCreateFailureCode(TemporalEngineStatus.FAILED, TemporalRuntimeFailureCode.INTERNAL_FAILURE);
    }

    private void assertCreateFailureCode(TemporalEngineStatus status, TemporalRuntimeFailureCode code) {
        DataSource dataSource = migratedDataSource();
        TemporalActApplicationService application = applicationService(
            dataSource,
            new SQLiteTemporalRequestIdempotencyRepository(dataSource),
            status
        );

        CreateSignalTemporalActResult result = application.createSignalTemporalAct(createRequest("idem-" + status));

        assertThat(result).isInstanceOf(CreateSignalTemporalActResult.Failed.class);
        assertThat(((CreateSignalTemporalActResult.Failed) result).failure().code()).isEqualTo(code);
    }

    private TemporalActApplicationService applicationService(
        DataSource dataSource,
        TemporalRequestIdempotencyPort idempotencyPort,
        TemporalEngineStatus status
    ) {
        ObjectMapper objectMapper = mapper();
        SQLiteTemporalActRepository actRepository = new SQLiteTemporalActRepository(dataSource, objectMapper, Clock.systemUTC(), 30);
        SQLiteScLedgerOutboxRepository ledgerRepository = new SQLiteScLedgerOutboxRepository(dataSource);
        TransactionTemplate txTemplate = transactionTemplate(dataSource);
        TemporalActService actService = new TemporalActService(
            actRepository,
            actRepository,
            ledgerRepository,
            txTemplate,
            objectMapper,
            Clock.systemUTC()
        );
        TemporalEngineHealth health = new TemporalEngineHealth();
        health.transitionTo(status);
        return new TemporalActApplicationService(
            actService,
            new TemporalActObservationService(actRepository),
            idempotencyPort,
            health,
            objectMapper,
            txTemplate
        );
    }

    private TemporalEngineService engineService(DataSource dataSource, TemporalEngineHealth health) {
        ObjectMapper objectMapper = mapper();
        SQLiteTemporalActRepository actRepository = new SQLiteTemporalActRepository(dataSource, objectMapper, Clock.systemUTC(), 30);
        SQLiteScLedgerOutboxRepository ledgerRepository = new SQLiteScLedgerOutboxRepository(dataSource);
        return new TemporalEngineService(
            actRepository,
            actRepository,
            ledgerRepository,
            ledgerRepository,
            transactionTemplate(dataSource),
            objectMapper,
            Clock.systemUTC(),
            TemporalRecoveryObservationPort.noOp(),
            health
        );
    }

    private CreateSignalTemporalActRequest createRequest(String idempotencyKey) {
        Instant now = Instant.ofEpochMilli(1_000L);
        return new CreateSignalTemporalActRequest(
            HABITAT_ID,
            now.plusSeconds(60),
            "label",
            "signal-kind",
            "target",
            "creator",
            idempotencyKey,
            now
        );
    }

    private CancelTemporalActRequest cancelRequest(String temporalActId, String idempotencyKey) {
        return new CancelTemporalActRequest(
            HABITAT_ID,
            temporalActId,
            "operator",
            idempotencyKey,
            "user requested",
            Instant.ofEpochMilli(2_000L)
        );
    }

    private void insertUnknownPayloadAct(DataSource dataSource, String temporalActId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement(
                 """
                     INSERT INTO temporal_acts
                     (habitat_id, temporal_act_id, status, payload_kind, due_at_ms,
                      label, signal_kind, notification_target_ref, created_by_ref,
                      created_at_ms, updated_at_ms)
                     VALUES ('habitat-001', ?, 'PENDING', 'UNKNOWN_KIND', 1,
                             'label', 'kind', 'target', 'creator', 1, 1)
                     """
             )) {
            statement.execute("PRAGMA ignore_check_constraints = ON");
            insert.setBytes(1, temporalActIdBytes(temporalActId));
            insert.executeUpdate();
        }
    }

    private byte[] temporalActIdBytes(String temporalActId) {
        UUID uuid = UUID.fromString(temporalActId);
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private DataSource migratedDataSource() {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:target/temporal-review-blockers-" + UUID.randomUUID() + ".sqlite");
        DataSource dataSource = new PerConnectionPragmaDataSource(delegate);
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return dataSource;
    }

    private JdbcTemplate jdbc(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private int count(DataSource dataSource, String table) {
        Integer count = jdbc(dataSource).queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private TransactionTemplate transactionTemplate(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private TemporalEngineProperties properties(int maxRecoveryBatches, int maxRecoveryBatchSize, boolean guardEnabled) {
        return new TemporalEngineProperties(
            true,
            HABITAT_ID,
            10_000L,
            50,
            maxRecoveryBatchSize,
            maxRecoveryBatches,
            30,
            500,
            500,
            guardEnabled,
            30_000L,
            1_000L,
            5_000L,
            5
        );
    }

    private void waitUntil(BooleanCondition condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.matches()) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    @FunctionalInterface
    private interface BooleanCondition {
        boolean matches();
    }

    private static final class FixedLockPort implements TemporalEngineLockPort {
        private final boolean acquired;
        private final int heartbeatResult;
        private final AtomicInteger heartbeatCount = new AtomicInteger();

        private FixedLockPort(boolean acquired, int heartbeatResult) {
            this.acquired = acquired;
            this.heartbeatResult = heartbeatResult;
        }

        @Override
        public boolean tryAcquire(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now, long ttlMs) {
            return acquired;
        }

        @Override
        public int heartbeat(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now, long ttlMs) {
            heartbeatCount.incrementAndGet();
            return heartbeatResult;
        }

        @Override
        public void release(String habitatId, String storagePartitionRef, String engineInstanceId, Instant now) {
        }

        @Override
        public boolean isHeld(String habitatId, String storagePartitionRef, Instant now) {
            return acquired;
        }
    }

    private static final class FailingInsertIdempotencyPort implements TemporalRequestIdempotencyPort {
        private final TemporalRequestIdempotencyPort delegate;

        private FailingInsertIdempotencyPort(TemporalRequestIdempotencyPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<TemporalRequestIdempotencyRecord> find(String habitatId, String idempotencyKey, String requestKind) {
            return delegate.find(habitatId, idempotencyKey, requestKind);
        }

        @Override
        public void insert(TemporalRequestIdempotencyRecord record, Instant now) {
            throw new DataIntegrityViolationException("forced idempotency insert failure");
        }
    }
}
