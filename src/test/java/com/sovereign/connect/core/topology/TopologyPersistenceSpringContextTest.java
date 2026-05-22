package com.sovereign.connect.core.topology;

import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.InMemoryBaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteBaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteEndpointHealthRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteMaterializationDecisionReplayRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTopologyMaterializationStateRepository;
import com.sovereign.connect.core.topology.materialization.TopologyMaterializationService;
import com.sovereign.connect.core.topology.port.BaseTopologyRepository;
import com.sovereign.connect.core.topology.port.CoreSnapshotReadPort;
import com.sovereign.connect.core.topology.port.EndpointHealthWritePort;
import com.sovereign.connect.core.topology.port.MaterializationDecisionReplayPort;
import com.sovereign.connect.core.topology.port.TopologyMaterializationStatePort;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TopologyPersistenceSpringContextTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
            "jdbc:sqlite:target/topology-context-" + UUID.randomUUID() + ".sqlite");
        registry.add("sovereign.temporal.engine.habitat-id", () -> "habitat-001");
        registry.add("sovereign.temporal.engine.polling-interval-ms", () -> "10000");
        registry.add("sovereign.temporal.engine.single-node-guard-ttl-ms", () -> "30000");
        registry.add("sovereign.temporal.engine.shutdown-timeout-ms", () -> "1000");
    }

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    BaseTopologyRepository baseTopologyRepository;

    @Autowired
    CoreSnapshotReadPort coreSnapshotReadPort;

    @Autowired
    EndpointHealthWritePort endpointHealthWritePort;

    @Autowired
    TopologyMaterializationStatePort topologyMaterializationStatePort;

    @Autowired
    MaterializationDecisionReplayPort materializationDecisionReplayPort;

    @Autowired
    BaseTopologyService baseTopologyService;

    @Autowired
    CoreSnapshotQueryService coreSnapshotQueryService;

    @Autowired
    TopologyMaterializationService topologyMaterializationService;

    @Test
    void springContextWiresTopologyPortsToSQLiteRepositories() {
        assertThat(baseTopologyRepository).isInstanceOf(SQLiteBaseTopologyRepository.class);
        assertThat(coreSnapshotReadPort).isInstanceOf(SQLiteBaseTopologyRepository.class);
        assertThat(endpointHealthWritePort).isInstanceOf(SQLiteEndpointHealthRepository.class);
        assertThat(topologyMaterializationStatePort).isInstanceOf(SQLiteTopologyMaterializationStateRepository.class);
        assertThat(materializationDecisionReplayPort).isInstanceOf(SQLiteMaterializationDecisionReplayRepository.class);
        assertThat(baseTopologyService).isNotNull();
        assertThat(coreSnapshotQueryService).isNotNull();
        assertThat(topologyMaterializationService).isNotNull();
    }

    @Test
    void h2BaseTopologyRepositoryIsNotSelectedInProductionContext() {
        assertThat(baseTopologyRepository).isNotInstanceOf(H2BaseTopologyRepository.class);
        assertThat(coreSnapshotReadPort).isNotInstanceOf(H2BaseTopologyRepository.class);
        assertThat(endpointHealthWritePort).isNotInstanceOf(H2BaseTopologyRepository.class);
        assertThat(topologyMaterializationStatePort).isNotInstanceOf(H2BaseTopologyRepository.class);
        assertThat(materializationDecisionReplayPort).isNotInstanceOf(H2BaseTopologyRepository.class);
    }

    @Test
    void inMemoryBaseTopologyRepositoryIsNotProductionDefault() {
        assertThat(baseTopologyRepository).isNotInstanceOf(InMemoryBaseTopologyRepository.class);
        assertThat(applicationContext.getBeansOfType(InMemoryBaseTopologyRepository.class)).isEmpty();
    }
}
