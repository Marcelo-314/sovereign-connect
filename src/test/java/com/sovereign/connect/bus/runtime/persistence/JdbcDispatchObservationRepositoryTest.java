package com.sovereign.connect.bus.runtime.persistence;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDispatchObservationRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void recordPersistsObservationWithAllMandatoryFields() {
        DataSource dataSource = dataSource("mandatory-fields");
        DispatchAttempt attempt = createDispatchRecord(dataSource);
        JdbcDispatchObservationRepository repository = new JdbcDispatchObservationRepository(dataSource);
        DispatchObservationRecord observation = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.CLAIMED,
                null,
                "technical claim recorded",
                Instant.parse("2026-05-30T10:00:00Z")
        );

        repository.record(observation);

        assertThat(repository.observationsFor(attempt.dispatchRecordId())).containsExactly(observation);
    }

    @Test
    void recordRejectsNullObservation() {
        assertThatThrownBy(() -> repository("null-observation").record(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordRejectsNullObservationId() {
        DataSource dataSource = dataSource("null-observation-id");
        DispatchAttempt attempt = createDispatchRecord(dataSource);

        assertThatThrownBy(() -> new JdbcDispatchObservationRepository(dataSource).record(new DispatchObservationRecord(
                null,
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.CLAIMED,
                null,
                "technical claim recorded",
                Instant.parse("2026-05-30T10:00:00Z")
        ))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordRejectsNullDispatchRecordId() {
        assertThatThrownBy(() -> repository("null-dispatch-record-id").record(new DispatchObservationRecord(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                DispatchState.CLAIMED,
                null,
                "technical claim recorded",
                Instant.parse("2026-05-30T10:00:00Z")
        ))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordRejectsNullState() {
        DataSource dataSource = dataSource("null-state");
        DispatchAttempt attempt = createDispatchRecord(dataSource);

        assertThatThrownBy(() -> new JdbcDispatchObservationRepository(dataSource).record(new DispatchObservationRecord(
                UUID.randomUUID(),
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                null,
                null,
                "technical claim recorded",
                Instant.parse("2026-05-30T10:00:00Z")
        ))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordRejectsNullObservedAt() {
        DataSource dataSource = dataSource("null-observed-at");
        DispatchAttempt attempt = createDispatchRecord(dataSource);

        assertThatThrownBy(() -> new JdbcDispatchObservationRepository(dataSource).record(new DispatchObservationRecord(
                UUID.randomUUID(),
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.CLAIMED,
                null,
                "technical claim recorded",
                null
        ))).isInstanceOf(NullPointerException.class);
    }

    @Test
    void observationsForUnknownDispatchRecordIdReturnsEmptyList() {
        assertThat(repository("unknown").observationsFor(UUID.randomUUID())).isEmpty();
    }

    @Test
    void observationSurvivesRepositoryRecreation() {
        DataSource dataSource = dataSource("survives-recreate");
        DispatchAttempt attempt = createDispatchRecord(dataSource);
        DispatchObservationRecord observation = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.CLAIMED,
                null,
                "technical claim recorded",
                Instant.parse("2026-05-30T10:00:00Z")
        );
        new JdbcDispatchObservationRepository(dataSource).record(observation);

        JdbcDispatchObservationRepository recreated = new JdbcDispatchObservationRepository(dataSource);

        assertThat(recreated.observationsFor(attempt.dispatchRecordId())).containsExactly(observation);
    }

    @Test
    void multipleObservationsReturnedInObservedAtOrder() {
        DataSource dataSource = dataSource("ordered");
        DispatchAttempt attempt = createDispatchRecord(dataSource);
        JdbcDispatchObservationRepository repository = new JdbcDispatchObservationRepository(dataSource);
        DispatchObservationRecord third = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.DISPATCHED,
                null,
                "technical dispatch completed",
                Instant.parse("2026-05-30T10:00:02Z")
        );
        DispatchObservationRecord first = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.CLAIMED,
                null,
                "technical claim recorded",
                Instant.parse("2026-05-30T10:00:00Z")
        );
        DispatchObservationRecord second = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.DISPATCHING,
                null,
                "technical dispatch started",
                Instant.parse("2026-05-30T10:00:01Z")
        );
        repository.record(third);
        repository.record(first);
        repository.record(second);

        assertThat(repository.observationsFor(attempt.dispatchRecordId())).containsExactly(first, second, third);
    }

    @Test
    void noHandlerObservationPersistsCodeAndSanitizedReason() {
        DataSource dataSource = dataSource("no-handler");
        DispatchAttempt attempt = createDispatchRecord(dataSource);
        JdbcDispatchObservationRepository repository = new JdbcDispatchObservationRepository(dataSource);
        DispatchObservationRecord observation = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.DELIVERY_FAILED,
                "NO_HANDLER",
                "no command handler registered",
                Instant.parse("2026-05-30T10:00:00Z")
        );

        repository.record(observation);

        assertThat(repository.observationsFor(attempt.dispatchRecordId())).singleElement().satisfies(record -> {
            assertThat(record.code()).isEqualTo("NO_HANDLER");
            assertThat(record.sanitizedReason()).isEqualTo("no command handler registered");
        });
    }

    @Test
    void deliveryFailedObservationPersistsCodeAndSanitizedReason() {
        DataSource dataSource = dataSource("delivery-failed");
        DispatchAttempt attempt = createDispatchRecord(dataSource);
        JdbcDispatchObservationRepository repository = new JdbcDispatchObservationRepository(dataSource);
        DispatchObservationRecord observation = observation(
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                DispatchState.DELIVERY_FAILED,
                "SC_B_DISPATCH_FAILED",
                "handler failed",
                Instant.parse("2026-05-30T10:00:00Z")
        );

        repository.record(observation);

        assertThat(repository.observationsFor(attempt.dispatchRecordId())).singleElement().satisfies(record -> {
            assertThat(record.code()).isEqualTo("SC_B_DISPATCH_FAILED");
            assertThat(record.sanitizedReason()).isEqualTo("handler failed");
        });
    }

    private JdbcDispatchObservationRepository repository(String name) {
        return new JdbcDispatchObservationRepository(dataSource(name));
    }

    private DispatchAttempt createDispatchRecord(DataSource dataSource) {
        return new JdbcDispatchStateRepository(dataSource).claim(UUID.randomUUID());
    }

    private DispatchObservationRecord observation(
            UUID dispatchRecordId,
            UUID attemptId,
            DispatchState state,
            String code,
            String reason,
            Instant observedAt
    ) {
        return new DispatchObservationRecord(
                UUID.randomUUID(),
                dispatchRecordId,
                attemptId,
                state,
                code,
                reason,
                observedAt
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
}
