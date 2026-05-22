package com.sovereign.connect.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteBaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteEndpointHealthRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteMaterializationDecisionReplayRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTopologyMaterializationStateRepository;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.materialization.TopologyMaterializationService;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.port.CoreSnapshotReadPort;
import com.sovereign.connect.core.topology.port.EndpointHealthWritePort;
import com.sovereign.connect.core.topology.port.MaterializationDecisionReplayPort;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
public class TopologyPersistenceConfiguration {

    @Bean
    @DependsOn("flyway")
    @Primary
    public SQLiteBaseTopologyRepository sqliteBaseTopologyRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        Clock clock
    ) {
        return new SQLiteBaseTopologyRepository(dataSource, objectMapper, transactionTemplate, clock);
    }

    @Bean
    @DependsOn("flyway")
    @Primary
    public SQLiteEndpointHealthRepository sqliteEndpointHealthRepository(DataSource dataSource, Clock clock) {
        return new SQLiteEndpointHealthRepository(dataSource, clock);
    }

    @Bean
    @DependsOn("flyway")
    @Primary
    public SQLiteTopologyMaterializationStateRepository sqliteTopologyMaterializationStateRepository(
        DataSource dataSource,
        ObjectMapper objectMapper,
        SQLiteBaseTopologyRepository baseRepository,
        SQLiteEndpointHealthRepository healthRepository,
        Clock clock
    ) {
        return new SQLiteTopologyMaterializationStateRepository(
            dataSource,
            objectMapper,
            baseRepository,
            healthRepository,
            clock
        );
    }

    @Bean
    @DependsOn("flyway")
    @Primary
    public SQLiteMaterializationDecisionReplayRepository sqliteMaterializationDecisionReplayRepository(
        DataSource dataSource,
        ObjectMapper objectMapper
    ) {
        return new SQLiteMaterializationDecisionReplayRepository(dataSource, objectMapper);
    }

    @Bean
    public BaseTopologyService baseTopologyService(
        BaseTopologyRepository repository,
        EndpointHealthWritePort healthWritePort,
        Clock clock
    ) {
        return new BaseTopologyService(repository, healthWritePort, clock);
    }

    @Bean
    public CoreSnapshotQueryService coreSnapshotQueryService(CoreSnapshotReadPort readPort, Clock clock) {
        return new CoreSnapshotQueryService(readPort, clock);
    }

    @Bean
    public TopologyMaterializationService topologyMaterializationService(
        BaseTopologyService baseTopologyService,
        TopologyMaterializationStatePort statePort,
        MaterializationDecisionReplayPort replayPort,
        Clock clock
    ) {
        return new DefaultTopologyMaterializationService(
            baseTopologyService,
            statePort,
            adapterInstanceId -> true,
            replayPort,
            clock
        );
    }
}
