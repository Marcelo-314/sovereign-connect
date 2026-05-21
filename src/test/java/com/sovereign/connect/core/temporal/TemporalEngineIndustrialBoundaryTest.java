package com.sovereign.connect.core.temporal;

import com.sovereign.connect.adapter.persistence.sqlite.PerConnectionPragmaDataSource;
import com.sovereign.connect.core.temporal.application.TemporalActApplicationPort;
import com.sovereign.connect.core.temporal.model.TemporalActPayload;
import com.sovereign.connect.core.temporal.observation.TemporalActObservation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemporalEngineIndustrialBoundaryTest {

    @Test
    void h2ExcludedFromSqliteAdapterPath() throws IOException {
        Path sqlitePath = Path.of("src/main/java/com/sovereign/connect/adapter/persistence/sqlite");
        if (!Files.exists(sqlitePath)) return;

        try (Stream<Path> paths = Files.walk(sqlitePath)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertThat(source)
                    .as("SQLite adapter file %s must not import H2", file)
                    .doesNotContain("org.h2");
            }
        }
    }

    @Test
    void domainAndApplicationServicesHaveNoStorageImports() throws IOException {
        List<Path> roots = List.of(
            Path.of("src/main/java/com/sovereign/connect/core/temporal/service"),
            Path.of("src/main/java/com/sovereign/connect/core/temporal/application"),
            Path.of("src/main/java/com/sovereign/connect/core/temporal/observation")
        );
        List<String> forbidden = List.of(
            "import java.sql", "import javax.sql",
            "JdbcTemplate", "DataSource",
            "org.h2", "org.xerial", "org.flywaydb"
        );
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    for (String banned : forbidden) {
                        assertThat(source)
                            .as("Domain service %s must not import %s", file, banned)
                            .doesNotContain(banned);
                    }
                }
            }
        }
    }

    @Test
    void temporalActPayloadPermitsOnlySignalTemporalPayload() {
        Class<?>[] permitted = TemporalActPayload.class.getPermittedSubclasses();
        assertThat(permitted)
            .extracting(Class::getSimpleName)
            .containsExactly("SignalTemporalPayload");
    }

    @Test
    void actionTemporalPayloadDoesNotExist() throws IOException {
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            List<String> actionClasses = paths
                .filter(p -> p.toString().endsWith(".java"))
                .map(p -> p.getFileName().toString())
                .filter(name -> name.contains("ActionTemporalPayload") || name.contains("ActionRequest"))
                .toList();
            assertThat(actionClasses)
                .as("ActionTemporalPayload and ActionRequest must not exist in v1")
                .isEmpty();
        }
    }

    @Test
    void temporalActApplicationPortHasNoReadMethods() {
        List<String> methods = Arrays.stream(TemporalActApplicationPort.class.getMethods())
            .map(Method::getName)
            .toList();
        assertThat(methods).doesNotContain("findById", "listActive", "listTerminal", "listMisfired");
        assertThat(methods).contains("createSignalTemporalAct", "cancelTemporalAct");
    }

    @Test
    void temporalActObservationDoesNotExposeInternals() {
        List<String> fields = Arrays.stream(TemporalActObservation.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
        assertThat(fields).doesNotContain("payload", "remainingMs", "createdByRefObject");
        assertThat(fields).contains("payloadKind", "label", "signalKind",
            "notificationTargetRef", "createdByRef");
    }

    @Test
    void sqliteFlywayMigrationAndPragmasAreAppliedPerConnection() throws Exception {
        DataSource dataSource = sqliteDataSource("target/temporal-industrial-" + UUID.randomUUID() + ".sqlite");
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            assertThat(statement.executeQuery("PRAGMA foreign_keys").getInt(1)).isEqualTo(1);
            assertThat(statement.executeQuery("PRAGMA synchronous").getInt(1)).isEqualTo(2);
            assertThat(statement.executeQuery("PRAGMA busy_timeout").getInt(1)).isEqualTo(5000);
            assertThat(statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='temporal_engine_locks'"
            ).next()).isTrue();
        }

        assertThatThrownBy(() -> insertTemporalActForMissingHabitat(dataSource))
            .hasMessageContaining("FOREIGN KEY");
    }

    private DataSource sqliteDataSource(String path) {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:" + path);
        return new PerConnectionPragmaDataSource(delegate);
    }

    private void insertTemporalActForMissingHabitat(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                """
                    INSERT INTO temporal_acts
                    (habitat_id, temporal_act_id, status, payload_kind, due_at_ms,
                     label, signal_kind, notification_target_ref, created_by_ref,
                     created_at_ms, updated_at_ms)
                    VALUES ('missing-habitat', 'act-1', 'PENDING', 'SIGNAL', 1,
                            'label', 'kind', 'target', 'creator', 1, 1)
                    """
            );
        }
    }
}
