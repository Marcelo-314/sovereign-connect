package com.sovereign.connect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.adapter.persistence.sqlite.PerConnectionPragmaDataSource;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteScLedgerOutboxRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalActRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalEngineLockRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalRequestIdempotencyRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTemporalRecoveryObservationRepository;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
import com.sovereign.connect.core.temporal.application.TemporalActApplicationPort;
import com.sovereign.connect.core.temporal.application.TemporalActApplicationService;
import com.sovereign.connect.core.temporal.application.TemporalRequestIdempotencyPort;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.engine.TemporalEngineLifecycle;
import com.sovereign.connect.core.temporal.engine.TemporalEngineLockPort;
import com.sovereign.connect.core.temporal.engine.TemporalEngineProperties;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationPort;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationService;
import com.sovereign.connect.core.temporal.port.TemporalActReadPort;
import com.sovereign.connect.core.temporal.port.TemporalActWritePort;
import com.sovereign.connect.core.temporal.service.TemporalActService;
import com.sovereign.connect.core.temporal.service.TemporalEngineRunner;
import com.sovereign.connect.core.temporal.service.TemporalEngineService;
import com.sovereign.connect.core.temporal.service.TemporalRecoveryObservationPort;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(TemporalEngineProperties.class)
public class TemporalEngineConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String url) {
        createParentDirectory(url);
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl(url);
        return new PerConnectionPragmaDataSource(delegate);
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
    }

    @Bean
    public TransactionTemplate transactionTemplate(DataSource dataSource) {
        return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Bean
    @DependsOn("flyway")
    public SQLiteTemporalActRepository sqliteTemporalActRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        Clock clock,
        TemporalEngineProperties properties
    ) {
        return new SQLiteTemporalActRepository(dataSource, objectMapper, clock, properties.terminalRetentionDays());
    }

    @Bean
    @Primary
    public TemporalActWritePort temporalActWritePort(SQLiteTemporalActRepository repository) {
        return repository;
    }

    @Bean
    @Primary
    public TemporalActReadPort temporalActReadPort(SQLiteTemporalActRepository repository) {
        return repository;
    }

    @Bean
    @DependsOn("flyway")
    public SQLiteScLedgerOutboxRepository sqliteScLedgerOutboxRepository(DataSource dataSource) {
        return new SQLiteScLedgerOutboxRepository(dataSource);
    }

    @Bean
    public ScLedgerWritePort scLedgerWritePort(SQLiteScLedgerOutboxRepository repository) {
        return repository;
    }

    @Bean
    public ScOutboxWritePort scOutboxWritePort(SQLiteScLedgerOutboxRepository repository) {
        return repository;
    }

    @Bean
    @DependsOn("flyway")
    public TemporalRequestIdempotencyPort temporalRequestIdempotencyPort(DataSource dataSource) {
        return new SQLiteTemporalRequestIdempotencyRepository(dataSource);
    }

    @Bean
    @DependsOn("flyway")
    public TemporalEngineLockPort temporalEngineLockPort(DataSource dataSource) {
        return new SQLiteTemporalEngineLockRepository(dataSource);
    }

    @Bean
    @DependsOn("flyway")
    public TemporalRecoveryObservationPort temporalRecoveryObservationPort(DataSource dataSource) {
        return new SQLiteTemporalRecoveryObservationRepository(dataSource);
    }

    @Bean
    public TemporalActService temporalActService(
        @Qualifier("temporalActWritePort") TemporalActWritePort writePort,
        @Qualifier("temporalActReadPort") TemporalActReadPort readPort,
        @Qualifier("scLedgerWritePort") ScLedgerWritePort ledgerPort,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        return new TemporalActService(writePort, readPort, ledgerPort, transactionTemplate, objectMapper, clock);
    }

    @Bean
    public TemporalEngineService temporalEngineService(
        @Qualifier("temporalActWritePort") TemporalActWritePort writePort,
        @Qualifier("temporalActReadPort") TemporalActReadPort readPort,
        @Qualifier("scLedgerWritePort") ScLedgerWritePort ledgerPort,
        @Qualifier("scOutboxWritePort") ScOutboxWritePort outboxPort,
        TransactionTemplate transactionTemplate,
        ObjectMapper objectMapper,
        Clock clock,
        TemporalRecoveryObservationPort recoveryObservationPort,
        TemporalEngineHealth health
    ) {
        return new TemporalEngineService(
            writePort,
            readPort,
            ledgerPort,
            outboxPort,
            transactionTemplate,
            objectMapper,
            clock,
            recoveryObservationPort,
            health
        );
    }

    @Bean
    public TemporalActObservationPort temporalActObservationPort(
        @Qualifier("temporalActReadPort") TemporalActReadPort readPort
    ) {
        return new TemporalActObservationService(readPort);
    }

    @Bean
    public TemporalEngineHealth temporalEngineHealth() {
        return new TemporalEngineHealth();
    }

    @Bean
    public TemporalActApplicationPort temporalActApplicationPort(
        TemporalActService actService,
        TemporalActObservationPort observationPort,
        TemporalRequestIdempotencyPort idempotencyPort,
        TemporalEngineHealth health,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate
    ) {
        return new TemporalActApplicationService(
            actService,
            observationPort,
            idempotencyPort,
            health,
            objectMapper,
            transactionTemplate
        );
    }

    @Bean
    public TemporalEngineRunner temporalEngineRunner(
        TemporalEngineService engineService,
        TemporalEngineProperties properties,
        TemporalEngineHealth health,
        Clock clock
    ) {
        return new TemporalEngineRunner(
            engineService,
            properties.habitatId(),
            clock,
            properties.pollingIntervalMs(),
            properties.maxDueActsPerCycle(),
            properties.failureBackoffMs(),
            properties.maxConsecutiveFailures(),
            health
        );
    }

    @Bean
    public TemporalEngineLifecycle temporalEngineLifecycle(
        TemporalEngineService engineService,
        TemporalEngineRunner runner,
        TemporalEngineProperties properties,
        TemporalEngineHealth health,
        TemporalEngineLockPort lockPort,
        Clock clock
    ) {
        return new TemporalEngineLifecycle(engineService, runner, properties, health, lockPort, clock);
    }

    private void createParentDirectory(String url) {
        if (!url.startsWith("jdbc:sqlite:") || url.equals("jdbc:sqlite::memory:")) {
            return;
        }
        String path = url.substring("jdbc:sqlite:".length());
        Path parent = Path.of(path).toAbsolutePath().getParent();
        if (parent != null) {
            parent.toFile().mkdirs();
        }
    }
}
