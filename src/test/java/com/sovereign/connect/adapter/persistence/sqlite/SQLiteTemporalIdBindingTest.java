package com.sovereign.connect.adapter.persistence.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.temporal.application.TemporalRequestIdempotencyRecord;
import com.sovereign.connect.core.temporal.model.CreatedByRef;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.model.TemporalActStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SQLiteTemporalIdBindingTest {

    @Test
    void temporalActIdStoredAsBlob16() {
        DataSource dataSource = migratedDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SQLiteTemporalActRepository repository = temporalActRepository(dataSource);

        repository.insertCreated(temporalAct(UUID.randomUUID().toString()));

        Map<String, Object> row = jdbc.queryForMap(
            """
                SELECT typeof(temporal_act_id) AS type_of,
                       length(temporal_act_id) AS len
                FROM temporal_acts
                WHERE habitat_id = 'habitat-001'
                """
        );
        assertThat(row.get("type_of")).isEqualTo("blob");
        assertThat(((Number) row.get("len")).intValue()).isEqualTo(16);
    }

    @Test
    void temporalRequestIdempotencyTemporalActIdStoredAsBlob16() {
        DataSource dataSource = migratedDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SQLiteTemporalActRepository temporalActRepository = temporalActRepository(dataSource);
        SQLiteTemporalRequestIdempotencyRepository idempotencyRepository =
            new SQLiteTemporalRequestIdempotencyRepository(dataSource);
        String temporalActId = UUID.randomUUID().toString();
        temporalActRepository.insertCreated(temporalAct(temporalActId));

        idempotencyRepository.insert(new TemporalRequestIdempotencyRecord(
            "habitat-001",
            "idem-key-001",
            "CREATE_SIGNAL",
            new byte[32],
            temporalActId,
            "ACCEPTED",
            null,
            "{}"
        ), Instant.now());

        Map<String, Object> row = jdbc.queryForMap(
            """
                SELECT typeof(temporal_act_id) AS type_of,
                       length(temporal_act_id) AS len
                FROM temporal_request_idempotency
                WHERE idempotency_key = 'idem-key-001'
                """
        );
        assertThat(row.get("type_of")).isEqualTo("blob");
        assertThat(((Number) row.get("len")).intValue()).isEqualTo(16);
    }

    @Test
    void temporalActIdRoundTripsToCanonicalString() {
        DataSource dataSource = migratedDataSource();
        SQLiteTemporalActRepository repository = temporalActRepository(dataSource);
        String temporalActId = UUID.randomUUID().toString();
        repository.insertCreated(temporalAct(temporalActId));

        Optional<TemporalAct> found = repository.findById("habitat-001", temporalActId);

        assertThat(found).isPresent();
        assertThat(found.get().temporalActId()).isEqualTo(temporalActId);
    }

    @Test
    void invalidTemporalActIdRejectedBeforePersistence() {
        DataSource dataSource = migratedDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SQLiteTemporalActRepository repository = temporalActRepository(dataSource);

        assertThatThrownBy(() -> repository.insertCreated(temporalAct("not-a-uuid")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a valid UUID");

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM temporal_acts", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void nullTemporalActIdInIdempotencyRecordStoredAsNull() {
        DataSource dataSource = migratedDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SQLiteTemporalRequestIdempotencyRepository repository =
            new SQLiteTemporalRequestIdempotencyRepository(dataSource);

        repository.insert(new TemporalRequestIdempotencyRecord(
            "habitat-001",
            "idem-key-rejected",
            "CREATE_SIGNAL",
            new byte[32],
            null,
            "REJECTED",
            "MISSING_LABEL",
            null
        ), Instant.now());

        byte[] stored = jdbc.queryForObject(
            """
                SELECT temporal_act_id
                FROM temporal_request_idempotency
                WHERE idempotency_key = 'idem-key-rejected'
                """,
            byte[].class
        );
        assertThat(stored).isNull();

        Optional<TemporalRequestIdempotencyRecord> found =
            repository.find("habitat-001", "idem-key-rejected", "CREATE_SIGNAL");
        assertThat(found).isPresent();
        assertThat(found.get().temporalActId()).isNull();
    }

    private TemporalAct temporalAct(String temporalActId) {
        Instant now = Instant.now();
        return new TemporalAct(
            temporalActId,
            "habitat-001",
            TemporalActStatus.PENDING,
            now.plusSeconds(60),
            new SignalTemporalPayload("Timer", "signal"),
            "surface:test",
            new CreatedByRef("creator:test"),
            null,
            now,
            now,
            null,
            null,
            null
        );
    }

    private SQLiteTemporalActRepository temporalActRepository(DataSource dataSource) {
        return new SQLiteTemporalActRepository(
            dataSource,
            new ObjectMapper().findAndRegisterModules(),
            Clock.systemUTC(),
            30
        );
    }

    private DataSource migratedDataSource() {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:target/temporal-blob-id-" + UUID.randomUUID() + ".sqlite");
        DataSource dataSource = new PerConnectionPragmaDataSource(delegate);
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return dataSource;
    }
}
