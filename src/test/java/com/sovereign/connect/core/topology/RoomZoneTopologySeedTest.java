package com.sovereign.connect.core.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.materialization.DeviceDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.DeviceStateFact;
import com.sovereign.connect.core.topology.materialization.EndpointDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.HealthFact;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.materialization.RoomDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.ZoneDiscoveryFact;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.RelationConfidence;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.SpatialRelationSource;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoomZoneTopologySeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void roomDiscoveryFactMaterializesRoomNodeAndAdvancesTopologyVersion() {
        Fixture fixture = emptyFixture("room-add");
        TopologyVersion before = fixture.repository.findCurrentVersion("habitat-013").orElseThrow();

        MaterializationDecision decision = fixture.materializer.materialize("habitat-013", roomFact("Kitchen"));

        assertThat(decision.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_STRUCTURAL_MUTATION);
        assertThat(decision.resultingTopologyVersion()).isPresent().get().isNotEqualTo(before);
        assertThat(fixture.query.findRoom("habitat-013", "room.tuya.kitchen")).isPresent();
    }

    @Test
    void duplicateRoomDiscoveryDoesNotAdvanceTopologyVersion() {
        Fixture fixture = emptyFixture("room-dup");
        MaterializationDecision first = fixture.materializer.materialize("habitat-013", roomFact("Kitchen"));

        MaterializationDecision duplicate = fixture.materializer.materialize("habitat-013", roomFact("Kitchen"));

        assertThat(duplicate.kind()).isEqualTo(MaterializationDecisionKind.REJECT_DUPLICATE);
        assertThat(duplicate.resultingTopologyVersion()).contains(first.resultingTopologyVersion().orElseThrow());
    }

    @Test
    void zoneDiscoveryFactMaterializesZoneNodeInExistingRoom() {
        Fixture fixture = emptyFixture("zone-add");
        fixture.materializer.materialize("habitat-013", roomFact("Kitchen"));

        MaterializationDecision decision = fixture.materializer.materialize("habitat-013", zoneFact("room.tuya.kitchen", "Worktop"));

        assertThat(decision.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_STRUCTURAL_MUTATION);
        assertThat(fixture.query.findZone("habitat-013", "zone.tuya.room.tuya.kitchen.worktop")).isPresent();
        assertThat(fixture.query.findRoom("habitat-013", "room.tuya.kitchen").orElseThrow().zoneIds())
            .contains("zone.tuya.room.tuya.kitchen.worktop");
    }

    @Test
    void zoneDiscoveryWithUnknownRoomRejectedWithoutVersionAdvance() {
        Fixture fixture = emptyFixture("zone-invalid");
        TopologyVersion before = fixture.repository.findCurrentVersion("habitat-013").orElseThrow();

        MaterializationDecision decision = fixture.materializer.materialize("habitat-013", zoneFact("room.missing", "Worktop"));

        assertThat(decision.kind()).isEqualTo(MaterializationDecisionKind.REJECT_INVALID_FACT);
        assertThat(fixture.repository.findCurrentVersion("habitat-013")).contains(before);
    }

    @Test
    void deviceMaterializationCreatesLocatedInRelationsForChosenRoomAndZone() {
        Fixture fixture = placedFixture("device-rel");

        MaterializationDecision decision = fixture.materializer.materialize("habitat-013", deviceFact());

        assertThat(decision.emittedChanges()).extracting(event -> event.changeKinds().iterator().next())
            .contains(TopologyChangeKind.DEVICE_ADDED, TopologyChangeKind.SPATIAL_ASSIGNMENT_CHANGED);
        assertThat(fixture.query.findSpatialRelationsBySubject(
            "habitat-013",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.tuya-device-abc"
        )).hasSize(2);
    }

    @Test
    void endpointMaterializationCreatesLocatedInRelationConsistentWithRoomZone() {
        Fixture fixture = placedFixture("endpoint-rel");
        fixture.materializer.materialize("habitat-013", deviceFact());

        fixture.materializer.materialize("habitat-013", endpointFact());

        assertThat(fixture.query.findSpatialRelationsBySubject(
            "habitat-013",
            TopologySpatialEntityType.ENDPOINT,
            "endpoint.tuya.tuya-device-abc.dp-1"
        )).extracting(relation -> relation.target().id())
            .contains("room.kitchen", "zone.kitchen.worktop");
    }

    @Test
    void resolvePrimaryPlacementReturnsExpectedRelation() {
        Fixture fixture = placedFixture("resolve");
        fixture.materializer.materialize("habitat-013", deviceFact());

        assertThat(fixture.query.resolvePrimaryPlacement(
            "habitat-013",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.tuya-device-abc"
        )).isPresent()
            .get()
            .extracting(TopologySpatialRelation::kind)
            .isEqualTo(TopologySpatialRelationKind.LOCATED_IN);
    }

    @Test
    void findLocatedDevicesReturnsDeviceInRoom() {
        Fixture fixture = placedFixture("located-devices");
        fixture.materializer.materialize("habitat-013", deviceFact());

        assertThat(fixture.query.findLocatedDevices("habitat-013", "room.kitchen"))
            .extracting(device -> device.deviceId())
            .contains("device.tuya.tuya-device-abc");
    }

    @Test
    void findLocatedEndpointsReturnsEndpointInZone() {
        Fixture fixture = placedFixture("located-endpoints");
        fixture.materializer.materialize("habitat-013", deviceFact());
        fixture.materializer.materialize("habitat-013", endpointFact());

        assertThat(fixture.query.findLocatedEndpoints("habitat-013", "zone.kitchen.worktop"))
            .extracting(endpoint -> endpoint.endpointId())
            .contains("endpoint.tuya.tuya-device-abc.dp-1");
    }

    @Test
    void spatialRelationsPersistAcrossRepositoryRecreation() {
        String jdbcUrl = jdbcUrl("recovery");
        Fixture fixture = placedFixtureFromJdbcUrl(jdbcUrl);
        fixture.materializer.materialize("habitat-013", deviceFact());

        H2BaseTopologyRepository recovered = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        CoreSnapshotQueryService recoveredQuery = new CoreSnapshotQueryService(recovered, clock);

        assertThat(recoveredQuery.findSpatialRelationsBySubject(
            "habitat-013",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.tuya-device-abc"
        )).hasSize(2);
    }

    @Test
    void relationGraphAndCompatibilityFieldsRemainConsistent() {
        Fixture fixture = placedFixture("consistent");
        fixture.materializer.materialize("habitat-013", deviceFact());

        TopologySpatialRelation roomRelation = fixture.query.resolvePrimaryPlacement(
            "habitat-013",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.tuya-device-abc"
        ).orElseThrow();

        assertThat(roomRelation.target().id()).isEqualTo("room.kitchen");
        assertThat(fixture.query.findDevice("habitat-013", "device.tuya.tuya-device-abc").orElseThrow().device().roomId())
            .isEqualTo("room.kitchen");
    }

    @Test
    void relationGraphMismatchRejectedNoInconsistentPlacement() {
        Fixture fixture = placedFixture("mismatch");
        fixture.materializer.materialize("habitat-013", deviceFact());
        fixture.service.addRoomWithResult("habitat-013", new RoomNode(
            "room.other",
            "Other",
            List.of(),
            List.of(),
            List.of(),
            new RoomTraits(false, false)
        ));

        assertThatThrownBy(() -> fixture.service.addSpatialRelationWithResult("habitat-013", relation(
            "relation.bad-placement",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.tuya-device-abc",
            TopologySpatialEntityType.ROOM,
            "room.other"
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("disagrees with device roomId");
    }

    @Test
    void missingRelationTargetRejectedNoSilentOrphan() {
        Fixture fixture = placedFixture("missing-target");

        assertThatThrownBy(() -> fixture.service.addSpatialRelationWithResult("habitat-013", relation(
            "relation.missing",
            TopologySpatialEntityType.DEVICE,
            "device.missing",
            TopologySpatialEntityType.ROOM,
            "room.kitchen"
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subject/target does not exist");
    }

    @Test
    void duplicateSpatialRelationRejectedWithoutVersionAdvance() {
        Fixture fixture = placedFixture("duplicate-relation");
        fixture.materializer.materialize("habitat-013", deviceFact());
        TopologyVersion before = fixture.repository.findCurrentVersion("habitat-013").orElseThrow();

        assertThatThrownBy(() -> fixture.service.addSpatialRelationWithResult("habitat-013", relation(
            "relation.located-in.device.device.tuya.tuya-device-abc.room.room.kitchen",
            TopologySpatialEntityType.DEVICE,
            "device.tuya.tuya-device-abc",
            TopologySpatialEntityType.ROOM,
            "room.kitchen"
        ))).isInstanceOf(IllegalArgumentException.class);
        assertThat(fixture.repository.findCurrentVersion("habitat-013")).contains(before);
    }

    @Test
    void stateAndHealthPersistenceFromMu011Unchanged() {
        Fixture fixture = placedFixture("state-health");
        fixture.materializer.materialize("habitat-013", deviceFact());
        fixture.materializer.materialize("habitat-013", endpointFact());
        TopologyVersion before = fixture.repository.findCurrentVersion("habitat-013").orElseThrow();

        fixture.materializer.materialize("habitat-013", new DeviceStateFact(
            UUID.randomUUID(), "adapter-1", "tuya", "tuya-device-abc",
            Map.of("power", "on"), Instant.parse("2026-05-15T12:00:03Z"), 0.98
        ));
        fixture.materializer.materialize("habitat-013", new HealthFact(
            UUID.randomUUID(), "adapter-1", "tuya", "tuya-device-abc", "dp-1",
            HealthStatus.DEGRADED, "intermittent", Instant.parse("2026-05-15T12:00:04Z"), 0.9
        ));

        assertThat(fixture.repository.findCurrentVersion("habitat-013")).contains(before);
        assertThat(fixture.repository.findDeviceState("habitat-013", "device.tuya.tuya-device-abc"))
            .contains(Map.of("power", "on"));
        assertThat(fixture.repository.findEndpointHealth("habitat-013", "endpoint.tuya.tuya-device-abc.dp-1"))
            .map(EndpointHealth::status)
            .contains(HealthStatus.DEGRADED);
    }

    @Test
    void materializerHasNoH2FieldOrConstructorOrImport() {
        assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getDeclaredFields())
            .map(field -> field.getType().getName())
            .toList()).noneMatch(type -> type.contains("H2BaseTopologyRepository"));
        assertThat(Arrays.stream(DefaultTopologyMaterializationService.class.getConstructors())
            .map(this::constructorSurface)
            .toList()).noneMatch(surface -> surface.contains("H2BaseTopologyRepository"));
    }

    @Test
    void structuralSaveRemainsHealthStateFree() {
        Fixture fixture = placedFixture("structural-only");
        HabitatBaseTopology topology = fixture.repository.findByHabitatId("habitat-013").orElseThrow();

        fixture.repository.save(topology);

        assertThat(fixture.repository.findEndpointHealth("habitat-013", "endpoint.missing")).isEmpty();
        assertThat(fixture.repository.findDeviceState("habitat-013", "device.missing")).isEmpty();
    }

    @Test
    void topologyChangedForRoomAdditionCarriesAffectedRoomId() {
        Fixture fixture = emptyFixture("room-event");

        MaterializationDecision decision = fixture.materializer.materialize("habitat-013", roomFact("Kitchen"));

        assertThat(decision.emittedChanges()).singleElement().satisfies(event -> {
            assertThat(event.changeKinds()).contains(TopologyChangeKind.ROOM_ADDED);
            assertThat(event.affectedRoomIds()).contains("room.tuya.kitchen");
        });
    }

    @Test
    void topologyChangedForSpatialAssignmentCarriesAffectedIds() {
        Fixture fixture = placedFixture("spatial-event");

        MaterializationDecision decision = fixture.materializer.materialize("habitat-013", deviceFact());

        assertThat(decision.emittedChanges().stream()
            .filter(event -> event.changeKinds().contains(TopologyChangeKind.SPATIAL_ASSIGNMENT_CHANGED))
            .toList()).anySatisfy(event -> {
                assertThat(event.affectedDeviceIds()).contains("device.tuya.tuya-device-abc");
                assertThat(event.affectedRoomIds()).contains("room.kitchen");
            });
    }

    @Test
    void oldTopologyJsonDeserializesWithEmptySpatialRelations() throws Exception {
        String json = """
            {
              "habitatId":"habitat-old",
              "topologyVersion":{"scope":{"type":"HABITAT","id":"habitat-old"},"value":"1"},
              "rooms":[],
              "zones":[],
              "devices":[],
              "endpoints":[],
              "metadata":{"schemaVersion":"base-topology.seed.v1","lastModified":"2026-05-15T12:00:00Z","source":"SC-C","checksum":null}
            }
            """;

        HabitatBaseTopology topology = mapper().readValue(json, HabitatBaseTopology.class);

        assertThat(topology.spatialRelations()).isEmpty();
    }

    private Fixture emptyFixture(String name) {
        return emptyFixtureFromJdbcUrl(jdbcUrl(name));
    }

    private Fixture emptyFixtureFromJdbcUrl(String jdbcUrl) {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService service = new BaseTopologyService(repository, repository, clock);
        CoreSnapshotQueryService query = new CoreSnapshotQueryService(repository, clock);
        DefaultTopologyMaterializationService materializer = new DefaultTopologyMaterializationService(
            service, repository, adapterInstanceId -> true, clock
        );
        service.createInitialTopology("habitat-013", List.of(), List.of(), List.of(), List.of());
        return new Fixture(repository, service, query, materializer);
    }

    private Fixture placedFixture(String name) {
        return placedFixtureFromJdbcUrl(jdbcUrl(name));
    }

    private Fixture placedFixtureFromJdbcUrl(String jdbcUrl) {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService service = new BaseTopologyService(repository, repository, clock);
        CoreSnapshotQueryService query = new CoreSnapshotQueryService(repository, clock);
        DefaultTopologyMaterializationService materializer = new DefaultTopologyMaterializationService(
            service, repository, adapterInstanceId -> true, clock
        );
        service.createInitialTopology(
            "habitat-013",
            List.of(new RoomNode("room.kitchen", "Kitchen", List.of("zone.kitchen.worktop"), List.of(), List.of(), new RoomTraits(false, false))),
            List.of(new ZoneNode("zone.kitchen.worktop", "Kitchen Worktop", "room.kitchen", List.of(), List.of(), new ZoneTraits(true))),
            List.of(),
            List.of()
        );
        return new Fixture(repository, service, query, materializer);
    }

    private RoomDiscoveryFact roomFact(String name) {
        return new RoomDiscoveryFact(
            UUID.randomUUID(), "adapter-1", "tuya", name, new RoomTraits(false, false),
            Instant.parse("2026-05-15T12:00:01Z"), 0.98, Map.of()
        );
    }

    private ZoneDiscoveryFact zoneFact(String roomId, String name) {
        return new ZoneDiscoveryFact(
            UUID.randomUUID(), "adapter-1", "tuya", roomId, name, new ZoneTraits(true),
            Instant.parse("2026-05-15T12:00:02Z"), 0.98, Map.of()
        );
    }

    private DeviceDiscoveryFact deviceFact() {
        return new DeviceDiscoveryFact(
            UUID.randomUUID(), "adapter-1", "tuya", "tuya-device-abc", "LIGHT",
            "Kitchen Light", "Tuya", "room.kitchen", "zone.kitchen.worktop",
            Instant.parse("2026-05-15T12:00:01Z"), 0.98, Map.of()
        );
    }

    private EndpointDiscoveryFact endpointFact() {
        return new EndpointDiscoveryFact(
            UUID.randomUUID(), "adapter-1", "tuya", "tuya-device-abc", "dp-1", "LIGHT",
            List.of(), Instant.parse("2026-05-15T12:00:02Z"), 0.98, Map.of()
        );
    }

    private TopologySpatialRelation relation(
        String id,
        TopologySpatialEntityType subjectType,
        String subjectId,
        TopologySpatialEntityType targetType,
        String targetId
    ) {
        return new TopologySpatialRelation(
            id,
            TopologySpatialRelationKind.LOCATED_IN,
            new TopologySpatialSubject(subjectType, subjectId),
            new TopologySpatialTarget(targetType, targetId),
            true,
            RelationConfidence.CONFIGURED,
            SpatialRelationSource.MANUAL,
            null,
            Instant.parse("2026-05-15T12:00:00Z"),
            Map.of()
        );
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private String jdbcUrl(String name) {
        String dbPath = tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
    }

    private DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "sa", "");
    }

    private String constructorSurface(Constructor<?> constructor) {
        return Arrays.stream(constructor.getParameterTypes())
            .map(Class::getName)
            .reduce("", (left, right) -> left + " " + right);
    }

    private record Fixture(
        H2BaseTopologyRepository repository,
        BaseTopologyService service,
        CoreSnapshotQueryService query,
        DefaultTopologyMaterializationService materializer
    ) {
    }
}
