package com.sovereign.connect.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.sqlite.PerConnectionPragmaDataSource;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteBaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteEndpointHealthRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteMaterializationDecisionReplayRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteScLedgerOutboxRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTopologyMaterializationStateRepository;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class SQLiteTestSupport {

    private SQLiteTestSupport() {
    }

    public static DataSource dataSource(Path tempDir, String name) {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:" + tempDir.resolve(name + ".sqlite").toAbsolutePath());
        DataSource dataSource = new PerConnectionPragmaDataSource(delegate);
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return dataSource;
    }

    public static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    public static TopologyFixture topologyFixture(Path tempDir, String name, Clock clock) {
        return topologyFixture(tempDir, name, clock, adapterInstanceId -> true);
    }

    public static TopologyFixture topologyFixture(
        Path tempDir,
        String name,
        Clock clock,
        Predicate<String> admittedAdapterPredicate
    ) {
        Objects.requireNonNull(clock, "clock is required");
        Objects.requireNonNull(admittedAdapterPredicate, "admittedAdapterPredicate is required");
        DataSource dataSource = dataSource(tempDir, name);
        ObjectMapper mapper = mapper();
        TransactionTemplate txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        SQLiteBaseTopologyRepository repository =
            new SQLiteBaseTopologyRepository(dataSource, mapper, txTemplate, clock);
        SQLiteEndpointHealthRepository healthRepository =
            new SQLiteEndpointHealthRepository(dataSource, clock);
        SQLiteTopologyMaterializationStateRepository stateRepository =
            new SQLiteTopologyMaterializationStateRepository(dataSource, mapper, repository, healthRepository, clock);
        SQLiteMaterializationDecisionReplayRepository replayRepository =
            new SQLiteMaterializationDecisionReplayRepository(dataSource, mapper, clock);
        BaseTopologyService service = new BaseTopologyService(repository, healthRepository, clock);
        CoreSnapshotQueryService query = new CoreSnapshotQueryService(repository, clock);
        DefaultTopologyMaterializationService materializer = new DefaultTopologyMaterializationService(
            service,
            stateRepository,
            admittedAdapterPredicate,
            replayRepository,
            clock
        );
        return new TopologyFixture(
            dataSource,
            repository,
            healthRepository,
            stateRepository,
            replayRepository,
            service,
            query,
            materializer,
            new JdbcTemplate(dataSource)
        );
    }

    public static LedgerFixture ledgerFixture(Path tempDir, String name) {
        DataSource dataSource = dataSource(tempDir, name);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        seedCommonHabitats(jdbc);
        return new LedgerFixture(
            dataSource,
            new SQLiteScLedgerOutboxRepository(dataSource),
            jdbc
        );
    }

    private static void seedCommonHabitats(JdbcTemplate jdbc) {
        for (String habitatId : List.of("habitat-001", "habitat-005", "habitat-015", "habitat-A", "habitat-B")) {
            jdbc.update("INSERT OR IGNORE INTO habitats(habitat_id) VALUES (?)", habitatId);
        }
    }

    public record TopologyFixture(
        DataSource dataSource,
        SQLiteBaseTopologyRepository repository,
        SQLiteEndpointHealthRepository healthRepository,
        SQLiteTopologyMaterializationStateRepository stateRepository,
        SQLiteMaterializationDecisionReplayRepository replayRepository,
        BaseTopologyService service,
        CoreSnapshotQueryService query,
        DefaultTopologyMaterializationService materializer,
        JdbcTemplate jdbc
    ) {
    }

    public record LedgerFixture(
        DataSource dataSource,
        SQLiteScLedgerOutboxRepository repository,
        JdbcTemplate jdbc
    ) {
    }
}
