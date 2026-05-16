package com.sovereign.connect.core.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.materialization.DeviceDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.EndpointDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.materialization.RoomDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.ZoneDiscoveryFact;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.TargetValidationResult;
import com.sovereign.connect.core.topology.model.TopologyTargetRef;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.service.BaseTopologyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIdempotencySeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-16T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void sameFactIdReplaysStoredDecisionWithoutVersionAdvance() {
        String jdbcUrl = jdbcUrl("same-fact-replay");
        Fixture fixture = placedFixture(jdbcUrl, adapter -> true);
        DeviceDiscoveryFact fact = deviceFact(UUID.fromString("00000000-0000-0000-0000-000000001001"));

        MaterializationDecision first = fixture.materializer.materialize("habitat-014", fact);
        TopologyVersion afterFirst = fixture.repository.findCurrentVersion("habitat-014").orElseThrow();
        int firstServiceEvents = fixture.service.emittedEvents().size();

        Fixture recovered = placedFixtureWithoutInitializing(jdbcUrl, adapter -> true);
        MaterializationDecision replayed = recovered.materializer.materialize("habitat-014", fact);

        assertThat(replayed.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_STRUCTURAL_MUTATION);
        assertThat(replayed.causationFactId()).isEqualTo(fact.factId());
        assertThat(replayed.decisionId()).isEqualTo(first.decisionId());
        assertThat(replayed.emittedChanges()).isEqualTo(first.emittedChanges());
        assertThat(recovered.repository.findCurrentVersion("habitat-014")).contains(afterFirst);
        assertThat(recovered.service.emittedEvents()).isEmpty();
        assertThat(fixture.service.emittedEvents()).hasSize(firstServiceEvents);
    }

    @Test
    void replaySurvivesRepositoryAndServiceRecreation() {
        String jdbcUrl = jdbcUrl("replay-survives");
        Fixture fixture = placedFixture(jdbcUrl, adapter -> true);
        RoomDiscoveryFact fact = roomFact(UUID.fromString("00000000-0000-0000-0000-000000001002"), "Pantry");

        MaterializationDecision first = fixture.materializer.materialize("habitat-014", fact);
        Fixture recovered = placedFixtureWithoutInitializing(jdbcUrl, adapter -> true);
        MaterializationDecision replayed = recovered.materializer.materialize("habitat-014", fact);

        assertThat(replayed).isEqualTo(first);
        assertThat(recovered.repository.findCurrentVersion("habitat-014"))
            .contains(first.resultingTopologyVersion().orElseThrow());
        assertThat(recovered.service.emittedEvents()).isEmpty();
    }

    @Test
    void sameCanonicalDeviceDifferentFactIdRejectedAsDuplicate() {
        Fixture fixture = placedFixture(jdbcUrl("device-duplicate"), adapter -> true);
        MaterializationDecision first = fixture.materializer.materialize(
            "habitat-014",
            deviceFact(UUID.fromString("00000000-0000-0000-0000-000000001003"))
        );
        TopologyVersion afterFirst = first.resultingTopologyVersion().orElseThrow();

        MaterializationDecision duplicate = fixture.materializer.materialize(
            "habitat-014",
            deviceFact(UUID.fromString("00000000-0000-0000-0000-000000001004"))
        );

        assertThat(duplicate.kind()).isEqualTo(MaterializationDecisionKind.REJECT_DUPLICATE);
        assertThat(duplicate.causationFactId()).isNotEqualTo(first.causationFactId());
        assertThat(duplicate.resultingTopologyVersion()).contains(afterFirst);
        assertThat(fixture.repository.findCurrentVersion("habitat-014")).contains(afterFirst);
    }

    @Test
    void sameCanonicalRoomDifferentFactIdRejectedAsDuplicate() {
        Fixture fixture = emptyFixture(jdbcUrl("room-duplicate"), adapter -> true);
        MaterializationDecision first = fixture.materializer.materialize(
            "habitat-014",
            roomFact(UUID.fromString("00000000-0000-0000-0000-000000001005"), "Kitchen")
        );
        TopologyVersion afterFirst = first.resultingTopologyVersion().orElseThrow();

        MaterializationDecision duplicate = fixture.materializer.materialize(
            "habitat-014",
            roomFact(UUID.fromString("00000000-0000-0000-0000-000000001006"), "Kitchen")
        );

        assertThat(duplicate.kind()).isEqualTo(MaterializationDecisionKind.REJECT_DUPLICATE);
        assertThat(duplicate.causationFactId()).isNotEqualTo(first.causationFactId());
        assertThat(duplicate.resultingTopologyVersion()).contains(afterFirst);
        assertThat(fixture.repository.findCurrentVersion("habitat-014")).contains(afterFirst);
    }

    @Test
    void sameCanonicalZoneDifferentFactIdRejectedAsDuplicate() {
        Fixture fixture = placedFixture(jdbcUrl("zone-duplicate"), adapter -> true);
        MaterializationDecision first = fixture.materializer.materialize(
            "habitat-014",
            zoneFact(UUID.fromString("00000000-0000-0000-0000-000000001007"), "room.kitchen", "Worktop")
        );
        TopologyVersion afterFirst = first.resultingTopologyVersion().orElseThrow();

        MaterializationDecision duplicate = fixture.materializer.materialize(
            "habitat-014",
            zoneFact(UUID.fromString("00000000-0000-0000-0000-000000001008"), "room.kitchen", "Worktop")
        );

        assertThat(duplicate.kind()).isEqualTo(MaterializationDecisionKind.REJECT_DUPLICATE);
        assertThat(duplicate.causationFactId()).isNotEqualTo(first.causationFactId());
        assertThat(duplicate.resultingTopologyVersion()).contains(afterFirst);
        assertThat(fixture.repository.findCurrentVersion("habitat-014")).contains(afterFirst);
    }

    @Test
    void rejectedDecisionIsReplayable() {
        String jdbcUrl = jdbcUrl("rejected-replay");
        Fixture denied = placedFixture(jdbcUrl, adapter -> false);
        TopologyVersion before = denied.repository.findCurrentVersion("habitat-014").orElseThrow();
        DeviceDiscoveryFact fact = deviceFact(UUID.fromString("00000000-0000-0000-0000-000000001009"));

        MaterializationDecision rejected = denied.materializer.materialize("habitat-014", fact);
        Fixture admittedAfterRecreation = placedFixtureWithoutInitializing(jdbcUrl, adapter -> true);
        MaterializationDecision replayed = admittedAfterRecreation.materializer.materialize("habitat-014", fact);

        assertThat(rejected.kind()).isEqualTo(MaterializationDecisionKind.REJECT_UNAUTHORIZED_ADAPTER);
        assertThat(replayed.kind()).isEqualTo(MaterializationDecisionKind.REJECT_UNAUTHORIZED_ADAPTER);
        assertThat(replayed.decisionId()).isEqualTo(rejected.decisionId());
        assertThat(admittedAfterRecreation.repository.findCurrentVersion("habitat-014")).contains(before);
        assertThat(admittedAfterRecreation.service.emittedEvents()).isEmpty();
    }

    @Test
    void validAfterRevalidationRemainsNonRejection() {
        Fixture fixture = placedFixture(jdbcUrl("valid-after-revalidation"), adapter -> true);
        fixture.materializer.materialize(
            "habitat-014",
            deviceFact(UUID.fromString("00000000-0000-0000-0000-000000001010"))
        );
        MaterializationDecision endpoint = fixture.materializer.materialize(
            "habitat-014",
            endpointFact(UUID.fromString("00000000-0000-0000-0000-000000001011"), "dp-1", "switch")
        );
        TopologyVersion originalTargetVersion = endpoint.resultingTopologyVersion().orElseThrow();
        fixture.materializer.materialize(
            "habitat-014",
            roomFact(UUID.fromString("00000000-0000-0000-0000-000000001012"), "Pantry")
        );

        assertThat(fixture.service.validateTarget(
            "habitat-014",
            new TopologyTargetRef(
                "device.tuya.tuya-device-abc",
                "endpoint.tuya.tuya-device-abc.dp-1",
                "capability.tuya.tuya-device-abc.dp-1.switch"
            ),
            originalTargetVersion
        )).isEqualTo(TargetValidationResult.VALID_AFTER_REVALIDATION);
    }

    @Test
    void wrongHabitatScopeReturnsConflict() {
        Fixture fixture = placedFixture(jdbcUrl("wrong-scope"), adapter -> true);
        fixture.materializer.materialize(
            "habitat-014",
            deviceFact(UUID.fromString("00000000-0000-0000-0000-000000001013"))
        );
        fixture.materializer.materialize(
            "habitat-014",
            endpointFact(UUID.fromString("00000000-0000-0000-0000-000000001014"), "dp-1", "switch")
        );

        assertThat(fixture.service.validateTarget(
            "habitat-014",
            new TopologyTargetRef(
                "device.tuya.tuya-device-abc",
                "endpoint.tuya.tuya-device-abc.dp-1",
                "capability.tuya.tuya-device-abc.dp-1.switch"
            ),
            TopologyVersion.habitatVersion("habitat-other", 1L)
        )).isEqualTo(TargetValidationResult.TOPOLOGY_VERSION_CONFLICT);
    }

    private Fixture emptyFixture(String jdbcUrl, java.util.function.Predicate<String> admittedAdapterPredicate) {
        Fixture fixture = placedFixtureWithoutInitializing(jdbcUrl, admittedAdapterPredicate);
        fixture.service.createInitialTopology("habitat-014", List.of(), List.of(), List.of(), List.of());
        return fixture;
    }

    private Fixture placedFixture(String jdbcUrl, java.util.function.Predicate<String> admittedAdapterPredicate) {
        Fixture fixture = placedFixtureWithoutInitializing(jdbcUrl, admittedAdapterPredicate);
        fixture.service.createInitialTopology(
            "habitat-014",
            List.of(room()),
            List.of(zone()),
            List.of(),
            List.of()
        );
        return fixture;
    }

    private Fixture placedFixtureWithoutInitializing(String jdbcUrl, java.util.function.Predicate<String> admittedAdapterPredicate) {
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService service = new BaseTopologyService(repository, repository, clock);
        DefaultTopologyMaterializationService materializer = new DefaultTopologyMaterializationService(
            service,
            repository,
            admittedAdapterPredicate,
            repository,
            clock
        );
        return new Fixture(repository, service, materializer);
    }

    private RoomNode room() {
        return new RoomNode(
            "room.kitchen",
            "Kitchen",
            List.of("zone.kitchen.worktop"),
            List.of(),
            List.of(),
            new RoomTraits(false, false)
        );
    }

    private ZoneNode zone() {
        return new ZoneNode(
            "zone.kitchen.worktop",
            "Kitchen Worktop",
            "room.kitchen",
            List.of(),
            List.of(),
            new ZoneTraits(true)
        );
    }

    private DeviceDiscoveryFact deviceFact(UUID factId) {
        return new DeviceDiscoveryFact(
            factId,
            "adapter-1",
            "tuya",
            "tuya-device-abc",
            "LIGHT",
            "Kitchen Worktop Light",
            "Tuya",
            "room.kitchen",
            "zone.kitchen.worktop",
            Instant.parse("2026-05-16T12:00:01Z"),
            0.98,
            Map.of("manufacturer", "tuya")
        );
    }

    private EndpointDiscoveryFact endpointFact(UUID factId, String providerEndpointId, String capabilityHint) {
        return new EndpointDiscoveryFact(
            factId,
            "adapter-1",
            "tuya",
            "tuya-device-abc",
            providerEndpointId,
            "LIGHT",
            List.of(capabilityHint),
            Instant.parse("2026-05-16T12:00:02Z"),
            0.98,
            Map.of("dpCode", "switch_led")
        );
    }

    private RoomDiscoveryFact roomFact(UUID factId, String name) {
        return new RoomDiscoveryFact(
            factId,
            "adapter-1",
            "tuya",
            name,
            new RoomTraits(false, false),
            Instant.parse("2026-05-16T12:00:03Z"),
            0.98,
            Map.of()
        );
    }

    private ZoneDiscoveryFact zoneFact(UUID factId, String roomId, String name) {
        return new ZoneDiscoveryFact(
            factId,
            "adapter-1",
            "tuya",
            roomId,
            name,
            new ZoneTraits(true),
            Instant.parse("2026-05-16T12:00:04Z"),
            0.98,
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

    private record Fixture(
        H2BaseTopologyRepository repository,
        BaseTopologyService service,
        DefaultTopologyMaterializationService materializer
    ) {
    }
}
