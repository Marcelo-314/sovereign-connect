package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.RuntimeDispatchService;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchObservationRepository;
import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchStateRepository;
import com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScWireEnvelopeKind;
import com.sovereign.connect.bus.runtime.validation.CorrelationValidationService;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import com.sovereign.connect.bus.runtime.validation.RoutingKeyValidationService;
import io.nats.client.Message;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDispatchToNatsEventIntegrationTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void runtimeDispatchPublishesTimerFiredEventToNatsAndRecordsDispatchState() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var dataSource = dataSource("runtime-dispatch-nats");
            var stateRepository = new JdbcDispatchStateRepository(dataSource);
            var observationRepository = new JdbcDispatchObservationRepository(dataSource);
            var codec = new ScJsonWireCodec(mapper);
            var builder = new NatsSubjectBuilder();
            var port = new NatsScBusPort(server.connection(), builder, codec, new EnvelopeValidationService());
            var service = dispatchService(port, stateRepository, observationRepository);
            var candidate = candidate();
            var subject = builder.buildTimerFiredEventSubject("habitat-runtime");
            var received = new AtomicReference<Message>();
            var latch = new CountDownLatch(1);
            server.connection().createDispatcher(message -> {
                received.set(message);
                latch.countDown();
            }).subscribe(subject);
            server.connection().flush(Duration.ofSeconds(2));

            var outcome = service.dispatch(candidate);

            assertThat(outcome).isInstanceOf(Dispatched.class);
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(stateRepository.currentAttempt(candidate.dispatchRecordId()).orElseThrow().state())
                    .isEqualTo(DispatchState.DISPATCHED);
            assertThat(observationRepository.observationsFor(candidate.dispatchRecordId()))
                    .extracting(DispatchObservationRecord::state)
                    .containsExactly(DispatchState.CLAIMED, DispatchState.DISPATCHING, DispatchState.DISPATCHED);
            var wire = codec.unmarshal(received.get().getData());
            assertThat(wire.envelopeKind()).isEqualTo(ScWireEnvelopeKind.EVENT);
            assertThat(wire.routingKey().lane()).isEqualTo(ScBusLane.EVENT);
        }
    }

    @Test
    void runtimeDispatchDoesNotInferSemanticSuccessFromNatsPublish() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            var dataSource = dataSource("runtime-dispatch-technical");
            var stateRepository = new JdbcDispatchStateRepository(dataSource);
            var observationRepository = new JdbcDispatchObservationRepository(dataSource);
            var port = new NatsScBusPort(server.connection(), new NatsSubjectBuilder(),
                    new ScJsonWireCodec(mapper), new EnvelopeValidationService());
            var candidate = candidate();

            dispatchService(port, stateRepository, observationRepository).dispatch(candidate);

            assertThat(observationRepository.observationsFor(candidate.dispatchRecordId()))
                    .anySatisfy(observation -> {
                        assertThat(observation.state()).isEqualTo(DispatchState.DISPATCHED);
                        assertThat(observation.sanitizedReason()).isEqualTo("technical dispatch completed");
                    });
        }
    }

    @Test
    void runtimeDispatchUsesEventLaneOnlyForLiveNatsPath() throws Exception {
        var candidate = candidate();

        assertThat(candidate.lane()).isEqualTo(ScBusLane.EVENT);
        assertThat(candidate.logicalTopic()).isEqualTo("sc-c.timer-fired");
    }

    private RuntimeDispatchService dispatchService(
            NatsScBusPort port,
            JdbcDispatchStateRepository stateRepository,
            JdbcDispatchObservationRepository observationRepository
    ) {
        DispatchCandidateReadPort candidateReadPort = List::of;
        return new RuntimeDispatchService(
                port,
                candidateReadPort,
                stateRepository,
                observationRepository,
                new EnvelopeValidationService(),
                new RoutingKeyValidationService(),
                new CorrelationValidationService()
        );
    }

    private DispatchCandidate candidate() {
        UUID sourceRecordId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        return new DispatchCandidate(
                UUID.randomUUID(),
                sourceRecordId,
                ScBusLane.EVENT,
                "sc-c.timer-fired",
                "habitat-runtime",
                messageId,
                null,
                messageId
        );
    }

    private SQLiteDataSource dataSource(String name) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve(name + ".sqlite").toAbsolutePath());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }
}
