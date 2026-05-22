package com.sovereign.connect.adapter.persistence.sqlite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.materialization.DeviceStateFact;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.model.CapabilityKind;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.CapabilityTraits;
import com.sovereign.connect.core.topology.model.DeviceHealth;
import com.sovereign.connect.core.topology.model.DeviceKind;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.DeviceProvider;
import com.sovereign.connect.core.topology.model.DeviceTraits;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointKind;
import com.sovereign.connect.core.topology.model.EndpointMetadata;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.EndpointTraits;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.ProviderDeviceRef;
import com.sovereign.connect.core.topology.model.ProviderEndpointRef;
import com.sovereign.connect.core.topology.model.RelationConfidence;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.SpatialRelationSource;
import com.sovereign.connect.core.topology.model.TopologyMetadata;
import com.sovereign.connect.core.topology.model.TopologyMutationRecord;
import com.sovereign.connect.core.topology.model.TopologySpatialEntityType;
import com.sovereign.connect.core.topology.model.TopologySpatialRelation;
import com.sovereign.connect.core.topology.model.TopologySpatialRelationKind;
import com.sovereign.connect.core.topology.model.TopologySpatialSubject;
import com.sovereign.connect.core.topology.model.TopologySpatialTarget;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SQLiteTopologyPersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteNormalizedTopologyFlywayMigrationCreatesRequiredTables() {
        JdbcTemplate jdbc = new JdbcTemplate(migratedDataSource("migration.sqlite"));

        List<String> tables = jdbc.queryForList(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            String.class
        );

        assertThat(tables).contains(
            "topology_versions",
            "topology_metadata",
            "rooms",
            "zones",
            "devices",
            "endpoints",
            "capabilities",
            "topology_spatial_relations",
            "topology_snapshots",
            "mutation_records",
            "device_states",
            "endpoint_health",
            "materialization_decision_replay"
        );
    }

    @Test
    void baseTopologyRowsPersistAndHydrateThroughSQLite() {
        Fixture fixture = fixture("roundtrip.sqlite");
        HabitatBaseTopology topology = buildSeedTopology();

        fixture.baseRepository.save(topology);

        assertRowCount(fixture.jdbc, "rooms", 1);
        assertRowCount(fixture.jdbc, "zones", 1);
        assertRowCount(fixture.jdbc, "devices", 1);
        assertRowCount(fixture.jdbc, "endpoints", 1);
        assertRowCount(fixture.jdbc, "capabilities", 1);
        assertRowCount(fixture.jdbc, "topology_spatial_relations", 1);
        assertRowCount(fixture.jdbc, "topology_snapshots", 1);

        HabitatBaseTopology loaded = fixture.baseRepository.findByHabitatId("habitat-001").orElseThrow();
        assertThat(loaded.rooms()).extracting(RoomNode::roomId).containsExactly("room.kitchen");
        assertThat(loaded.zones()).extracting(ZoneNode::zoneId).containsExactly("zone.kitchen.worktop");
        assertThat(loaded.devices()).extracting(DeviceNode::deviceId).containsExactly("device.tuya.light-1");
        assertThat(loaded.endpoints()).extracting(EndpointNode::endpointId).containsExactly("endpoint.tuya.light-1.switch");
        assertThat(loaded.endpoints().get(0).capabilities()).extracting(CapabilityNode::capabilityId)
            .containsExactly("capability.tuya.light-1.switch.power");
        assertThat(loaded.spatialRelations()).extracting(TopologySpatialRelation::relationId)
            .containsExactly("relation.located-in.device.device.tuya.light-1.room.room.kitchen");
    }

    @Test
    void topologyJsonIsCompatibilitySnapshotNotAuthority() {
        Fixture fixture = fixture("json-compat.sqlite");
        fixture.baseRepository.save(buildSeedTopology());

        fixture.jdbc.update("UPDATE topology_snapshots SET topology_json = 'CORRUPTED' WHERE habitat_id = 'habitat-001'");

        assertThat(fixture.baseRepository.findByHabitatId("habitat-001")).isPresent();
        assertThat(fixture.baseRepository.findRoom("habitat-001", "room.kitchen")).isPresent();
    }

    @Test
    void ctiRoomZoneLocatedInQueriesReadFromNormalizedTables() {
        Fixture fixture = fixture("cti.sqlite");
        fixture.baseRepository.save(buildSeedTopology());
        fixture.jdbc.update("UPDATE topology_snapshots SET topology_json = 'CORRUPTED' WHERE habitat_id = 'habitat-001'");

        assertThat(fixture.baseRepository.findRoom("habitat-001", "room.kitchen").orElseThrow().zoneIds())
            .containsExactly("zone.kitchen.worktop");
        assertThat(fixture.baseRepository.findZone("habitat-001", "zone.kitchen.worktop").orElseThrow().deviceIds())
            .containsExactly("device.tuya.light-1");
        assertThat(fixture.baseRepository.findSpatialRelation(
            "habitat-001",
            "relation.located-in.device.device.tuya.light-1.room.room.kitchen"
        )).isPresent();
        assertThat(fixture.baseRepository.findSpatialRelationsBySubject(
            "habitat-001",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.light-1"
        )).hasSize(1);
        assertThat(fixture.baseRepository.resolvePrimaryPlacement(
            "habitat-001",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.light-1"
        )).isPresent();
        assertThat(fixture.baseRepository.findLocatedDevices("habitat-001", "room.kitchen"))
            .extracting(DeviceNode::deviceId)
            .containsExactly("device.tuya.light-1");
    }

    @Test
    void coreSnapshotQueryReadsTopologyFromNormalizedSQLite() {
        Fixture fixture = fixture("core-snapshot.sqlite");
        fixture.baseRepository.save(buildSeedTopology());
        fixture.stateRepository.saveDeviceState("habitat-001", "device.tuya.light-1", Map.of("power", "on"));
        fixture.healthRepository.saveEndpointHealth(
            "habitat-001",
            "endpoint.tuya.light-1.switch",
            new EndpointHealth(HealthStatus.HEALTHY, Instant.parse("2026-05-22T12:00:00Z"), "ok")
        );
        fixture.jdbc.update("UPDATE topology_snapshots SET topology_json = 'CORRUPTED' WHERE habitat_id = 'habitat-001'");

        CoreSnapshotQueryService queryService = new CoreSnapshotQueryService(fixture.baseRepository, Clock.systemUTC());

        assertThat(queryService.findCurrentSnapshot("habitat-001").orElseThrow().topology().devices())
            .extracting(DeviceNode::deviceId)
            .containsExactly("device.tuya.light-1");
        assertThat(queryService.findDevice("habitat-001", "device.tuya.light-1").orElseThrow().state())
            .containsEntry("power", "on");
        assertThat(queryService.findEndpoint("habitat-001", "endpoint.tuya.light-1.switch").orElseThrow().health().status())
            .isEqualTo(HealthStatus.HEALTHY);
    }

    @Test
    void structuralSaveDoesNotOverwriteEndpointHealthOrDeviceStateInSQLite() {
        Fixture fixture = fixture("state-health.sqlite");
        HabitatBaseTopology topology = buildSeedTopology();
        fixture.baseRepository.save(topology);
        EndpointHealth durableHealth = new EndpointHealth(
            HealthStatus.OFFLINE,
            Instant.parse("2026-05-22T12:01:00Z"),
            "durable health"
        );
        fixture.healthRepository.saveEndpointHealth("habitat-001", "endpoint.tuya.light-1.switch", durableHealth);
        fixture.stateRepository.saveDeviceState("habitat-001", "device.tuya.light-1", Map.of("level", 42));

        fixture.baseRepository.save(topology);

        assertThat(fixture.baseRepository.findEndpointHealth("habitat-001", "endpoint.tuya.light-1.switch"))
            .contains(durableHealth);
        assertThat(fixture.baseRepository.findDeviceState("habitat-001", "device.tuya.light-1").orElseThrow())
            .containsEntry("level", 42);
        assertThat(fixture.baseRepository.findByHabitatId("habitat-001").orElseThrow().endpoints().get(0).health())
            .isEqualTo(durableHealth);
    }

    @Test
    void materializationDecisionReplayAndDuplicateFactUseSQLite() {
        Fixture fixture = fixture("materialization.sqlite");
        fixture.baseRepository.save(buildSeedTopology());
        BaseTopologyService baseService = new BaseTopologyService(
            fixture.baseRepository,
            fixture.healthRepository,
            Clock.systemUTC()
        );
        DefaultTopologyMaterializationService service = new DefaultTopologyMaterializationService(
            baseService,
            fixture.stateRepository,
            adapterInstanceId -> true,
            fixture.replayRepository,
            Clock.systemUTC()
        );
        UUID factId = UUID.randomUUID();
        DeviceStateFact fact = new DeviceStateFact(
            factId,
            "adapter-1",
            "tuya",
            "light-1",
            Map.of("power", "on"),
            Instant.parse("2026-05-22T12:02:00Z"),
            1.0
        );

        MaterializationDecision first = service.materialize("habitat-001", fact);
        MaterializationDecision second = service.materialize("habitat-001", fact);

        assertThat(first.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_NON_STRUCTURAL_STATE);
        assertThat(second.decisionId()).isEqualTo(first.decisionId());
        assertRowCount(fixture.jdbc, "materialization_decision_replay", 1);
        assertThat(fixture.stateRepository.findDeviceState("habitat-001", "device.tuya.light-1").orElseThrow())
            .containsEntry("power", "on");
    }

    @Test
    void mutationRecordsPersistAndRecoverThroughSQLite() {
        Fixture fixture = fixture("mutations.sqlite");
        fixture.baseRepository.save(buildSeedTopology());
        TopologyMutationRecord record = new TopologyMutationRecord(
            UUID.randomUUID(),
            "habitat-001",
            TopologyVersion.habitatVersion("habitat-001", 1),
            TopologyVersion.habitatVersion("habitat-001", 2),
            Set.of(TopologyChangeKind.DEVICE_ADDED),
            List.of("device.tuya.light-1"),
            List.of(),
            "test mutation",
            Instant.parse("2026-05-22T12:03:00Z")
        );

        fixture.baseRepository.appendMutationRecord(record);

        assertThat(fixture.baseRepository.findMutationRecords("habitat-001")).containsExactly(record);
    }

    @Test
    void noTopologyRepositoryImplementsLedgerOrOutboxPorts() {
        Fixture fixture = fixture("no-ledger.sqlite");

        assertThat(fixture.baseRepository).isNotInstanceOf(com.sovereign.connect.core.scledger.port.ScLedgerWritePort.class);
        assertThat(fixture.baseRepository).isNotInstanceOf(com.sovereign.connect.core.scledger.port.ScOutboxWritePort.class);
        assertThat(fixture.healthRepository).isNotInstanceOf(com.sovereign.connect.core.scledger.port.ScLedgerWritePort.class);
        assertThat(fixture.stateRepository).isNotInstanceOf(com.sovereign.connect.core.scledger.port.ScOutboxWritePort.class);
    }

    @Test
    void sqliteScLedgerOutboxRepositoryPersistsLedgerAndOutbox() {
        DataSource dataSource = migratedDataSource("ledger-outbox.sqlite");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(dataSource);
        UUID ledgerEntryId = UUID.randomUUID();
        Instant now = Instant.parse("2026-05-22T12:04:00Z");

        repository.appendLedgerEntry(new LedgerEntry(
            ledgerEntryId,
            "habitat-001",
            LedgerRecordClass.EVENT_OUTBOX,
            "TemporalAct",
            "act-1",
            SemanticKind.TEMPORAL_ACT_CREATED,
            "application/json",
            "{}",
            "ledger-idem",
            now,
            "{}"
        ));
        repository.appendOutboxEntry(new OutboxEntry(
            UUID.randomUUID(),
            ledgerEntryId,
            "habitat-001",
            OutboundKind.TIMER_FIRED_SIGNAL,
            DeliveryLane.SIGNAL,
            "topic",
            "{}",
            "surface:test",
            "outbox-idem",
            OutboxEntryStatus.PENDING,
            now,
            now,
            "{}"
        ));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sc_c_ledger_entries", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sc_c_outbox_entries", Integer.class)).isEqualTo(1);
    }

    private Fixture fixture(String name) {
        DataSource dataSource = migratedDataSource(name);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Clock clock = Clock.systemUTC();
        SQLiteBaseTopologyRepository baseRepository = new SQLiteBaseTopologyRepository(
            dataSource,
            objectMapper,
            new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
            clock
        );
        SQLiteEndpointHealthRepository healthRepository = new SQLiteEndpointHealthRepository(dataSource, clock);
        SQLiteTopologyMaterializationStateRepository stateRepository =
            new SQLiteTopologyMaterializationStateRepository(dataSource, objectMapper, baseRepository, healthRepository, clock);
        SQLiteMaterializationDecisionReplayRepository replayRepository =
            new SQLiteMaterializationDecisionReplayRepository(dataSource, objectMapper);
        return new Fixture(
            dataSource,
            new JdbcTemplate(dataSource),
            baseRepository,
            healthRepository,
            stateRepository,
            replayRepository
        );
    }

    private DataSource migratedDataSource(String name) {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:" + tempDir.resolve(name));
        DataSource dataSource = new PerConnectionPragmaDataSource(delegate);
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        return dataSource;
    }

    private HabitatBaseTopology buildSeedTopology() {
        Instant now = Instant.parse("2026-05-22T12:00:00Z");
        CapabilityNode endpointCap = new CapabilityNode(
            "capability.tuya.light-1.switch.power",
            "Power",
            CapabilityKind.TOGGLE,
            new CapabilityTraits(true, true, false)
        );
        RoomNode room = new RoomNode(
            "room.kitchen",
            "Kitchen",
            List.of("zone.kitchen.worktop"),
            List.of("device.tuya.light-1"),
            List.of("endpoint.tuya.light-1.switch"),
            new RoomTraits(false, false)
        );
        ZoneNode zone = new ZoneNode(
            "zone.kitchen.worktop",
            "Kitchen Worktop",
            "room.kitchen",
            List.of("device.tuya.light-1"),
            List.of("endpoint.tuya.light-1.switch"),
            new ZoneTraits(true)
        );
        DeviceNode device = new DeviceNode(
            "device.tuya.light-1",
            "Kitchen Light",
            "Kitchen Light",
            "room.kitchen",
            "zone.kitchen.worktop",
            DeviceKind.LIGHT,
            DeviceProvider.TUYA,
            List.of("endpoint.tuya.light-1.switch"),
            List.of(),
            new DeviceTraits(false, true, true),
            new DeviceHealth(HealthStatus.HEALTHY, now, null),
            new ProviderDeviceRef("tuya", "light-1", Map.of())
        );
        EndpointNode endpoint = new EndpointNode(
            "endpoint.tuya.light-1.switch",
            "device.tuya.light-1",
            "Switch",
            "Switch",
            EndpointKind.SWITCH_CHANNEL,
            "room.kitchen",
            "zone.kitchen.worktop",
            List.of(endpointCap),
            new EndpointTraits(true, true, true, true, false, false),
            new EndpointHealth(HealthStatus.UNKNOWN, null, "structural stale health"),
            new ProviderEndpointRef("tuya", "light-1", "switch", Map.of()),
            EndpointMetadata.empty()
        );
        TopologySpatialRelation relation = new TopologySpatialRelation(
            "relation.located-in.device.device.tuya.light-1.room.room.kitchen",
            TopologySpatialRelationKind.LOCATED_IN,
            new TopologySpatialSubject(TopologySpatialEntityType.DEVICE, "device.tuya.light-1"),
            new TopologySpatialTarget(TopologySpatialEntityType.ROOM, "room.kitchen"),
            true,
            RelationConfidence.CONFIGURED,
            SpatialRelationSource.MANUAL,
            null,
            now,
            Map.of()
        );
        return new HabitatBaseTopology(
            "habitat-001",
            TopologyVersion.habitatVersion("habitat-001", 1),
            List.of(room),
            List.of(zone),
            List.of(device),
            List.of(endpoint),
            List.of(relation),
            new TopologyMetadata("base-topology.seed.v1", now, "SC-C", null)
        );
    }

    private void assertRowCount(JdbcTemplate jdbc, String table, int expected) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).isEqualTo(expected);
    }

    private record Fixture(
        DataSource dataSource,
        JdbcTemplate jdbc,
        SQLiteBaseTopologyRepository baseRepository,
        SQLiteEndpointHealthRepository healthRepository,
        SQLiteTopologyMaterializationStateRepository stateRepository,
        SQLiteMaterializationDecisionReplayRepository replayRepository
    ) {
    }
}
