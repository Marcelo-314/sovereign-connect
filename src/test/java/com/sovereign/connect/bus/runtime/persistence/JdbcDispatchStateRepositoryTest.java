package com.sovereign.connect.bus.runtime.persistence;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcDispatchStateRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void firstClaimIsPersisted() {
        DataSource dataSource = dataSource("first-claim");
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        UUID dispatchRecordId = UUID.randomUUID();

        DispatchAttempt claimed = repository.claim(dispatchRecordId);

        assertThat(claimed.dispatchRecordId()).isEqualTo(dispatchRecordId);
        assertThat(claimed.attemptNumber()).isEqualTo(1);
        assertThat(claimed.state()).isEqualTo(DispatchState.CLAIMED);
        assertThat(repository.currentAttempt(dispatchRecordId)).contains(claimed);
    }

    @Test
    void currentAttemptSurvivesRepositoryRecreation() {
        DataSource dataSource = dataSource("recreate-current");
        UUID dispatchRecordId = UUID.randomUUID();
        DispatchAttempt claimed = new JdbcDispatchStateRepository(dataSource).claim(dispatchRecordId);

        JdbcDispatchStateRepository recreated = new JdbcDispatchStateRepository(dataSource);

        assertThat(recreated.currentAttempt(dispatchRecordId)).contains(claimed);
    }

    @Test
    void claimedDispatchingDispatchedSurvivesRestart() {
        DataSource dataSource = dataSource("dispatched-restart");
        UUID dispatchRecordId = UUID.randomUUID();
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        DispatchAttempt claimed = repository.claim(dispatchRecordId);
        repository.transition(dispatchRecordId, DispatchState.DISPATCHING);
        DispatchAttempt dispatched = repository.transition(dispatchRecordId, DispatchState.DISPATCHED);

        DispatchAttempt afterRestart = new JdbcDispatchStateRepository(dataSource).currentAttempt(dispatchRecordId).orElseThrow();

        assertThat(afterRestart.attemptId()).isEqualTo(claimed.attemptId());
        assertThat(afterRestart.attemptNumber()).isEqualTo(1);
        assertThat(afterRestart.state()).isEqualTo(DispatchState.DISPATCHED);
        assertThat(afterRestart).isEqualTo(dispatched);
    }

    @Test
    void dispatchingDeliveryFailedSurvivesRestart() {
        DataSource dataSource = dataSource("failed-restart");
        UUID dispatchRecordId = UUID.randomUUID();
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        repository.claim(dispatchRecordId);
        repository.transition(dispatchRecordId, DispatchState.DISPATCHING);
        repository.transition(dispatchRecordId, DispatchState.DELIVERY_FAILED);

        assertThat(new JdbcDispatchStateRepository(dataSource).currentAttempt(dispatchRecordId).orElseThrow().state())
                .isEqualTo(DispatchState.DELIVERY_FAILED);
    }

    @Test
    void retryIncreasesAttemptNumber() {
        DataSource dataSource = dataSource("retry-new-attempt");
        UUID dispatchRecordId = UUID.randomUUID();
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        DispatchAttempt first = failDispatch(repository, dispatchRecordId);

        repository.transition(dispatchRecordId, DispatchState.RETRY_SCHEDULED);
        DispatchAttempt retry = repository.claim(dispatchRecordId);

        assertThat(retry.dispatchRecordId()).isEqualTo(dispatchRecordId);
        assertThat(retry.attemptId()).isNotEqualTo(first.attemptId());
        assertThat(retry.attemptNumber()).isEqualTo(2);
        assertThat(retry.state()).isEqualTo(DispatchState.CLAIMED);
    }

    @Test
    void retryAttemptNumberContinuitySurvivesRepositoryRecreation() {
        DataSource dataSource = dataSource("retry-continuity");
        UUID dispatchRecordId = UUID.randomUUID();
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        failDispatch(repository, dispatchRecordId);
        repository.transition(dispatchRecordId, DispatchState.RETRY_SCHEDULED);

        DispatchAttempt retry = new JdbcDispatchStateRepository(dataSource).claim(dispatchRecordId);

        assertThat(retry.attemptNumber()).isEqualTo(2);
        assertThat(new JdbcDispatchStateRepository(dataSource).currentAttempt(dispatchRecordId)).contains(retry);
    }

    @Test
    void exhaustedIsPersistentAndNotClaimable() {
        DataSource dataSource = dataSource("exhausted-terminal");
        UUID dispatchRecordId = UUID.randomUUID();
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        failDispatch(repository, dispatchRecordId);
        repository.transition(dispatchRecordId, DispatchState.EXHAUSTED);

        JdbcDispatchStateRepository recreated = new JdbcDispatchStateRepository(dataSource);

        assertThat(recreated.currentAttempt(dispatchRecordId).orElseThrow().state()).isEqualTo(DispatchState.EXHAUSTED);
        assertThatThrownBy(() -> recreated.claim(dispatchRecordId)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supersedeCancellationRequiresEvidenceRef() {
        JdbcDispatchStateRepository repository = repository("supersede-evidence");
        UUID dispatchRecordId = UUID.randomUUID();
        repository.claim(dispatchRecordId);

        assertThatThrownBy(() -> repository.transition(dispatchRecordId, DispatchState.CANCELLED_BY_SUPERSEDE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.transitionWithEvidence(dispatchRecordId, DispatchState.CANCELLED_BY_SUPERSEDE, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supersedeCancellationPersistsEvidenceRef() {
        DataSource dataSource = dataSource("supersede-persisted");
        UUID dispatchRecordId = UUID.randomUUID();
        JdbcDispatchStateRepository repository = new JdbcDispatchStateRepository(dataSource);
        repository.claim(dispatchRecordId);
        repository.transitionWithEvidence(dispatchRecordId, DispatchState.CANCELLED_BY_SUPERSEDE, "ledger:suppressed");

        JdbcDispatchStateRepository recreated = new JdbcDispatchStateRepository(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(recreated.currentAttempt(dispatchRecordId).orElseThrow().state())
                .isEqualTo(DispatchState.CANCELLED_BY_SUPERSEDE);
        assertThatThrownBy(() -> recreated.claim(dispatchRecordId)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT supersession_evidence_ref FROM sc_b_dispatch_records WHERE dispatch_record_id = ?",
                String.class,
                dispatchRecordId.toString()
        )).isEqualTo("ledger:suppressed");
    }

    @Test
    void invalidTransitionsAreRejected() {
        JdbcDispatchStateRepository repository = repository("invalid-transitions");
        UUID dispatchRecordId = UUID.randomUUID();

        assertThatThrownBy(() -> repository.claim(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.transition(null, DispatchState.CLAIMED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.transitionWithEvidence(null, DispatchState.CANCELLED_BY_SUPERSEDE, "evidence"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.transition(dispatchRecordId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.transition(dispatchRecordId, DispatchState.DISPATCHED)).isInstanceOf(IllegalStateException.class);
        repository.claim(dispatchRecordId);
        assertThatThrownBy(() -> repository.transition(dispatchRecordId, DispatchState.EXHAUSTED)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownCurrentAttemptReturnsEmpty() {
        assertThat(repository("unknown-current").currentAttempt(UUID.randomUUID())).isEmpty();
    }

    @Test
    void migrationCreatesScBDispatchTablesWithNullableOpaqueSourceRecordId() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource("schema"));

        assertThat(tableExists(jdbc, "sc_b_dispatch_records")).isTrue();
        assertThat(tableExists(jdbc, "sc_b_dispatch_attempts")).isTrue();
        assertThat(jdbc.queryForList("PRAGMA table_info(sc_b_dispatch_records)")).anySatisfy(row -> {
            assertThat(row.get("name")).isEqualTo("source_record_id");
            assertThat(((Number) row.get("notnull")).intValue()).isZero();
        });
    }

    @Test
    void dispatchStateNamesDoNotEncodeSemanticMeaning() {
        assertThat(DispatchState.CLAIMED.name()).doesNotContain("ACCEPT");
        assertThat(DispatchState.DISPATCHED.name()).doesNotContain("SUCCESS");
        assertThat(DispatchState.EXHAUSTED.name()).doesNotContain("FAILURE");
    }

    @Test
    void transitionWithEvidenceRejectsNonSupersedeTargetState() {
        JdbcDispatchStateRepository repository = repository("evidence-target");
        UUID dispatchRecordId = UUID.randomUUID();
        repository.claim(dispatchRecordId);

        assertThatThrownBy(() -> repository.transitionWithEvidence(
                dispatchRecordId,
                DispatchState.DISPATCHING,
                "ledger:unexpected-evidence"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private DispatchAttempt failDispatch(JdbcDispatchStateRepository repository, UUID dispatchRecordId) {
        DispatchAttempt claimed = repository.claim(dispatchRecordId);
        repository.transition(dispatchRecordId, DispatchState.DISPATCHING);
        repository.transition(dispatchRecordId, DispatchState.DELIVERY_FAILED);
        return claimed;
    }

    private JdbcDispatchStateRepository repository(String name) {
        return new JdbcDispatchStateRepository(dataSource(name));
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

    private boolean tableExists(JdbcTemplate jdbc, String tableName) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class,
                tableName
        );
        return count != null && count == 1;
    }
}
