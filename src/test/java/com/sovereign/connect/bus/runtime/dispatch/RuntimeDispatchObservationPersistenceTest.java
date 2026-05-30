package com.sovereign.connect.bus.runtime.dispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryScBusPort;
import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchObservationRepository;
import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchStateRepository;
import com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort;
import com.sovereign.connect.bus.runtime.validation.CorrelationValidationService;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import com.sovereign.connect.bus.runtime.validation.RoutingKeyValidationService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDispatchObservationPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void claimEmitsPersistentClaimedObservation() {
        Fixture fixture = fixture("claim");
        DispatchCandidate candidate = candidate(ScBusLane.COMMAND, "topic.command");
        fixture.busPort.registerCommandHandler("topic.command", envelope -> { });

        fixture.dispatchService.dispatch(candidate);

        assertThat(recreatedObservations(fixture, candidate)).extracting(DispatchObservationRecord::state)
                .contains(DispatchState.CLAIMED);
    }

    @Test
    void dispatchEmitsPersistentDispatchingAndDispatchedObservations() {
        Fixture fixture = fixture("dispatch-success");
        DispatchCandidate candidate = candidate(ScBusLane.EVENT, "topic.event");
        fixture.busPort.registerEventHandler("topic.event", envelope -> { });

        DispatchOutcome outcome = fixture.dispatchService.dispatch(candidate);

        assertThat(outcome).isInstanceOf(Dispatched.class);
        assertThat(recreatedObservations(fixture, candidate)).extracting(DispatchObservationRecord::state)
                .containsExactly(DispatchState.CLAIMED, DispatchState.DISPATCHING, DispatchState.DISPATCHED);
    }

    @Test
    void noHandlerEmitsPersistentDeliveryFailedObservation() {
        Fixture fixture = fixture("no-handler");
        DispatchCandidate candidate = candidate(ScBusLane.COMMAND, "topic.command");

        fixture.dispatchService.dispatch(candidate);

        assertThat(recreatedObservations(fixture, candidate)).anySatisfy(observation -> {
            assertThat(observation.state()).isEqualTo(DispatchState.DELIVERY_FAILED);
            assertThat(observation.code()).isEqualTo("NO_HANDLER");
            assertThat(observation.sanitizedReason()).isEqualTo("no command handler registered");
        });
    }

    @Test
    void retryEmitsPersistentRetryScheduledObservation() {
        Fixture fixture = fixture("retry");
        DispatchCandidate candidate = candidate(ScBusLane.COMMAND, "topic.command");
        fixture.dispatchService.dispatch(candidate);

        fixture.dispatchService.scheduleRetry(candidate.dispatchRecordId());

        assertThat(recreatedObservations(fixture, candidate)).extracting(DispatchObservationRecord::state)
                .contains(DispatchState.RETRY_SCHEDULED, DispatchState.CLAIMED);
        assertThat(recreatedObservations(fixture, candidate)).anySatisfy(observation -> {
            assertThat(observation.state()).isEqualTo(DispatchState.RETRY_SCHEDULED);
            assertThat(observation.sanitizedReason()).isEqualTo("technical retry scheduled");
        });
    }

    @Test
    void exhaustionEmitsPersistentExhaustedObservation() {
        Fixture fixture = fixture("exhaust");
        DispatchCandidate candidate = candidate(ScBusLane.COMMAND, "topic.command");
        fixture.dispatchService.dispatch(candidate);

        fixture.dispatchService.exhaust(candidate.dispatchRecordId());

        assertThat(recreatedObservations(fixture, candidate)).anySatisfy(observation -> {
            assertThat(observation.state()).isEqualTo(DispatchState.EXHAUSTED);
            assertThat(observation.sanitizedReason()).isEqualTo("technical delivery exhausted");
        });
    }

    @Test
    void supersedeCancellationEmitsPersistentCancelledObservation() {
        Fixture fixture = fixture("supersede");
        DispatchCandidate candidate = candidate(ScBusLane.COMMAND, "topic.command");
        fixture.stateRepository.claim(candidate.dispatchRecordId());

        fixture.dispatchService.cancelBySupersede(candidate.dispatchRecordId(), "ledger:suppressed");

        assertThat(recreatedObservations(fixture, candidate)).singleElement().satisfies(observation -> {
            assertThat(observation.state()).isEqualTo(DispatchState.CANCELLED_BY_SUPERSEDE);
            assertThat(observation.code()).isEqualTo("SUPERSEDED");
            assertThat(observation.sanitizedReason()).isEqualTo("ledger:suppressed");
        });
    }

    @Test
    void dispatchedObservationIsTechnicalNotSemantic() {
        Fixture fixture = fixture("technical-dispatched");
        DispatchCandidate candidate = candidate(ScBusLane.EVENT, "topic.event");
        fixture.busPort.registerEventHandler("topic.event", envelope -> { });

        fixture.dispatchService.dispatch(candidate);

        assertThat(recreatedObservations(fixture, candidate)).anySatisfy(observation -> {
            assertThat(observation.state()).isEqualTo(DispatchState.DISPATCHED);
            assertThat(observation.sanitizedReason()).isEqualTo("technical dispatch completed");
            assertThat(observation.sanitizedReason()).doesNotContain("semantic");
            assertThat(observation.sanitizedReason()).doesNotContain("success");
        });
    }

    @Test
    void exhaustedObservationIsTechnicalNotSemantic() {
        Fixture fixture = fixture("technical-exhausted");
        DispatchCandidate candidate = candidate(ScBusLane.COMMAND, "topic.command");
        fixture.dispatchService.dispatch(candidate);

        fixture.dispatchService.exhaust(candidate.dispatchRecordId());

        assertThat(recreatedObservations(fixture, candidate)).anySatisfy(observation -> {
            assertThat(observation.state()).isEqualTo(DispatchState.EXHAUSTED);
            assertThat(observation.sanitizedReason()).isEqualTo("technical delivery exhausted");
            assertThat(observation.sanitizedReason()).doesNotContain("provider");
            assertThat(observation.sanitizedReason()).doesNotContain("device");
        });
    }

    private List<DispatchObservationRecord> recreatedObservations(Fixture fixture, DispatchCandidate candidate) {
        return new JdbcDispatchObservationRepository(fixture.dataSource).observationsFor(candidate.dispatchRecordId());
    }

    private Fixture fixture(String name) {
        DataSource dataSource = dataSource(name);
        InMemoryScBusPort busPort = new InMemoryScBusPort();
        JdbcDispatchStateRepository stateRepository = new JdbcDispatchStateRepository(dataSource);
        JdbcDispatchObservationRepository observationRepository = new JdbcDispatchObservationRepository(dataSource);
        DispatchCandidateReadPort candidateReadPort = List::of;
        RuntimeDispatchService dispatchService = new RuntimeDispatchService(
                busPort,
                candidateReadPort,
                stateRepository,
                observationRepository,
                new EnvelopeValidationService(),
                new RoutingKeyValidationService(),
                new CorrelationValidationService()
        );
        return new Fixture(dataSource, busPort, stateRepository, dispatchService);
    }

    private DispatchCandidate candidate(ScBusLane lane, String topic) {
        UUID sourceRecordId = UUID.randomUUID();
        return new DispatchCandidate(
                UUID.randomUUID(),
                sourceRecordId,
                lane,
                topic,
                "habitat-001",
                UUID.randomUUID(),
                sourceRecordId,
                UUID.randomUUID()
        );
    }

    private DataSource dataSource(String name) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(name + ".sqlite").toAbsolutePath());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }

    private record Fixture(
            DataSource dataSource,
            InMemoryScBusPort busPort,
            JdbcDispatchStateRepository stateRepository,
            RuntimeDispatchService dispatchService
    ) {
    }
}
