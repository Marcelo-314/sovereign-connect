package com.sovereign.connect.core.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository;
import com.sovereign.connect.core.topology.event.TopologyChangeKind;
import com.sovereign.connect.core.topology.event.TopologyChanged;
import com.sovereign.connect.core.topology.materialization.CapabilityDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService;
import com.sovereign.connect.core.topology.materialization.DeviceDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.DeviceStateFact;
import com.sovereign.connect.core.topology.materialization.EndpointDiscoveryFact;
import com.sovereign.connect.core.topology.materialization.HealthFact;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.TargetValidationResult;
import com.sovereign.connect.core.topology.model.TopologyTargetRef;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.query.CoreSnapshot;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
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

class TopologyMaterializationSeedTest {

    @TempDir
    private Path tempDir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-11T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void materializesTopologyFactsThroughBaseTopologyServiceAndDurableRepository() {
        String jdbcUrl = jdbcUrl();
        H2BaseTopologyRepository repository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService mutationService = new BaseTopologyService(repository, clock);
        CoreSnapshotQueryService queryService = new CoreSnapshotQueryService(repository, clock);
        DefaultTopologyMaterializationService materializationService = new DefaultTopologyMaterializationService(
            mutationService,
            repository,
            adapterInstanceId -> true,
            repository,
            clock
        );

        mutationService.createInitialTopology(
            "habitat-007",
            List.of(room()),
            List.of(zone()),
            List.of(),
            List.of()
        );

        DeviceDiscoveryFact deviceFact = deviceFact("adapter-1");
        EndpointDiscoveryFact endpointFact = endpointFact("adapter-1");
        CapabilityDiscoveryFact capabilityFact = capabilityFact("adapter-1");
        String canonicalDeviceId = materializationService.canonicalDeviceId("tuya", "tuya-device-abc");
        String canonicalEndpointId = materializationService.canonicalEndpointId("tuya", "tuya-device-abc", "dp-1");
        String canonicalCapabilityId = materializationService.canonicalCapabilityId("tuya", "tuya-device-abc", "dp-1", "switch");

        MaterializationDecision deviceDecision = materializationService.materialize("habitat-007", deviceFact);
        assertStructuralDecision(deviceDecision, TopologyChangeKind.DEVICE_ADDED, canonicalDeviceId, null);
        assertThat(canonicalDeviceId).isNotEqualTo(deviceFact.providerDeviceId());
        DeviceNode materializedDevice = queryService
            .findDevice("habitat-007", canonicalDeviceId)
            .orElseThrow()
            .device();
        assertThat(materializedDevice.providerRef().providerDeviceId())
            .isEqualTo("tuya-device-abc");
        assertThat(materializedDevice.providerRef().providerDeviceId())
            .isNotEqualTo(materializedDevice.deviceId());

        MaterializationDecision duplicateDevice = materializationService.materialize("habitat-007", deviceFactWithId(
            UUID.fromString("00000000-0000-0000-0000-000000000101"),
            "adapter-1"
        ));
        assertRejectedWithoutMutation(duplicateDevice, MaterializationDecisionKind.REJECT_DUPLICATE, deviceDecision.resultingTopologyVersion().orElseThrow(), queryService);

        MaterializationDecision conflictingProviderBinding = materializationService.materialize(
            "habitat-007",
            new DeviceDiscoveryFact(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                "adapter-1",
                "tuya",
                "tuya-device-abc",
                "LIGHT",
                "Conflicting Name",
                "Other Manufacturer",
                "room.kitchen",
                "zone.kitchen.worktop",
                Instant.parse("2026-05-11T12:00:03Z"),
                0.80,
                Map.of("manufacturer", "other")
            )
        );
        assertRejectedWithoutMutation(conflictingProviderBinding, MaterializationDecisionKind.REJECT_DUPLICATE, deviceDecision.resultingTopologyVersion().orElseThrow(), queryService);

        MaterializationDecision endpointDecision = materializationService.materialize("habitat-007", endpointFact);
        assertStructuralDecision(endpointDecision, TopologyChangeKind.ENDPOINT_ADDED, canonicalDeviceId, canonicalEndpointId);
        assertThat(canonicalEndpointId).isNotEqualTo(endpointFact.providerEndpointId());
        EndpointNode materializedEndpoint = queryService
            .findEndpoint("habitat-007", canonicalEndpointId)
            .orElseThrow()
            .endpoint();
        assertThat(materializedEndpoint.providerRef().providerEndpointId())
            .isEqualTo("dp-1");
        assertThat(materializedEndpoint.providerRef().providerEndpointId())
            .isNotEqualTo(materializedEndpoint.endpointId());

        MaterializationDecision duplicateEndpoint = materializationService.materialize("habitat-007", endpointFactWithId(
            UUID.fromString("00000000-0000-0000-0000-000000000103"),
            "adapter-1"
        ));
        assertRejectedWithoutMutation(duplicateEndpoint, MaterializationDecisionKind.REJECT_DUPLICATE, endpointDecision.resultingTopologyVersion().orElseThrow(), queryService);

        MaterializationDecision capabilityDecision = materializationService.materialize("habitat-007", capabilityFact);
        assertStructuralDecision(capabilityDecision, TopologyChangeKind.CAPABILITY_ADDED, canonicalDeviceId, canonicalEndpointId);
        assertThat(canonicalCapabilityId).isNotEqualTo(capabilityFact.providerCapabilityKey());

        MaterializationDecision duplicateCapability = materializationService.materialize("habitat-007", capabilityFactWithId(
            UUID.fromString("00000000-0000-0000-0000-000000000104"),
            "adapter-1"
        ));
        assertRejectedWithoutMutation(duplicateCapability, MaterializationDecisionKind.REJECT_DUPLICATE, capabilityDecision.resultingTopologyVersion().orElseThrow(), queryService);

        TopologyVersion versionBeforeState = queryService.findCurrentTopologyVersion("habitat-007").orElseThrow();
        MaterializationDecision stateDecision = materializationService.materialize(
            "habitat-007",
            new DeviceStateFact(
                UUID.fromString("00000000-0000-0000-0000-000000000004"),
                "adapter-1",
                "tuya",
                "tuya-device-abc",
                Map.of("power", "on", "level", 81),
                Instant.parse("2026-05-11T12:00:04Z"),
                0.99
            )
        );
        assertThat(stateDecision.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_NON_STRUCTURAL_STATE);
        assertThat(stateDecision.emittedChanges()).isEmpty();
        assertThat(queryService.findCurrentTopologyVersion("habitat-007")).contains(versionBeforeState);
        assertThat(repository.findDeviceState("habitat-007", canonicalDeviceId)).contains(Map.of("power", "on", "level", 81));

        TopologyVersion versionBeforeHealth = queryService.findCurrentTopologyVersion("habitat-007").orElseThrow();
        MaterializationDecision healthDecision = materializationService.materialize(
            "habitat-007",
            new HealthFact(
                UUID.fromString("00000000-0000-0000-0000-000000000005"),
                "adapter-1",
                "tuya",
                "tuya-device-abc",
                "dp-1",
                HealthStatus.DEGRADED,
                "provider reported intermittent link",
                Instant.parse("2026-05-11T12:00:05Z"),
                0.91
            )
        );
        assertThat(healthDecision.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_HEALTH_UPDATE);
        assertThat(healthDecision.emittedChanges()).isEmpty();
        assertThat(queryService.findCurrentTopologyVersion("habitat-007")).contains(versionBeforeHealth);
        assertThat(repository.findEndpointHealth("habitat-007", canonicalEndpointId))
            .contains(new EndpointHealth(HealthStatus.DEGRADED, Instant.parse("2026-05-11T12:00:05Z"), "provider reported intermittent link"));

        MaterializationDecision invalidHealth = materializationService.materialize(
            "habitat-007",
            new HealthFact(
                UUID.fromString("00000000-0000-0000-0000-000000000006"),
                "adapter-1",
                "tuya",
                "tuya-device-abc",
                null,
                HealthStatus.OFFLINE,
                "device-level health unsupported",
                Instant.parse("2026-05-11T12:00:06Z"),
                0.88
            )
        );
        assertRejectedWithoutMutation(invalidHealth, MaterializationDecisionKind.REJECT_INVALID_FACT, versionBeforeHealth, queryService);

        DefaultTopologyMaterializationService deniedMaterializer = new DefaultTopologyMaterializationService(
            mutationService,
            repository,
            adapterInstanceId -> false,
            repository,
            clock
        );
        MaterializationDecision denied = deniedMaterializer.materialize(
            "habitat-007",
            new DeviceDiscoveryFact(
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                "adapter-denied",
                "tuya",
                "tuya-device-denied",
                "LIGHT",
                "Denied Device",
                "Tuya",
                "room.kitchen",
                "zone.kitchen.worktop",
                Instant.parse("2026-05-11T12:00:07Z"),
                0.95,
                Map.of()
            )
        );
        assertRejectedWithoutMutation(denied, MaterializationDecisionKind.REJECT_UNAUTHORIZED_ADAPTER, versionBeforeHealth, queryService);

        MaterializationDecision missingHabitat = materializationService.materialize("missing-habitat", deviceFact);
        assertThat(missingHabitat.kind()).isEqualTo(MaterializationDecisionKind.REJECT_INVALID_FACT);
        assertThat(missingHabitat.reason()).isEqualTo("habitat not initialized");
        assertThat(missingHabitat.emittedChanges()).isEmpty();

        repository = null;
        mutationService = null;
        queryService = null;
        materializationService = null;

        H2BaseTopologyRepository recoveredRepository = new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);
        BaseTopologyService recoveredMutationService = new BaseTopologyService(recoveredRepository, clock);
        CoreSnapshotQueryService recoveredQueryService = new CoreSnapshotQueryService(recoveredRepository, clock);
        CoreSnapshot recovered = recoveredQueryService.findCurrentSnapshot("habitat-007").orElseThrow();

        assertThat(recovered.topology().devices()).anyMatch(device -> device.deviceId().equals(canonicalDeviceId));
        assertThat(recovered.topology().endpoints()).anyMatch(endpoint -> endpoint.endpointId().equals(canonicalEndpointId));
        assertThat(recovered.topology().endpoints().stream()
            .filter(endpoint -> endpoint.endpointId().equals(canonicalEndpointId))
            .findFirst()
            .orElseThrow()
            .capabilities())
            .extracting(CapabilityNode::capabilityId)
            .contains(canonicalCapabilityId);
        assertThat(recovered.deviceStates()).containsKey(canonicalDeviceId);
        assertThat(recovered.endpointHealth()).containsKey(canonicalEndpointId);
        assertThat(recovered.topologyVersion()).isEqualTo(capabilityDecision.resultingTopologyVersion().orElseThrow());
        assertThat(recoveredQueryService.findCurrentTopologyVersion("habitat-007")).contains(recovered.topologyVersion());
        assertThat(recoveredQueryService.findCurrentTopologyVersion("habitat-007")).contains(versionBeforeHealth);
        assertThat(recoveredMutationService.validateTarget(
            "habitat-007",
            new TopologyTargetRef(canonicalDeviceId, canonicalEndpointId, canonicalCapabilityId),
            recovered.topologyVersion()
        )).isEqualTo(TargetValidationResult.VALID);
    }

    @Test
    void endpointHealthSurvivesSubsequentStructuralMutation() {
        String jdbcUrl = jdbcUrl();

        H2BaseTopologyRepository repository =
            new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);

        BaseTopologyService mutationService =
            new BaseTopologyService(repository, clock);

        CoreSnapshotQueryService queryService =
            new CoreSnapshotQueryService(repository, clock);

        DefaultTopologyMaterializationService materializer =
            new DefaultTopologyMaterializationService(
                mutationService,
                repository,
                id -> true,
                repository,
                clock
            );

        mutationService.createInitialTopology(
            "habitat-overwrite",
            List.of(room()),
            List.of(zone()),
            List.of(),
            List.of()
        );

        materializer.materialize("habitat-overwrite", deviceFact("adapter-1"));
        materializer.materialize("habitat-overwrite", endpointFact("adapter-1"));

        String canonicalEndpointId =
            materializer.canonicalEndpointId("tuya", "tuya-device-abc", "dp-1");

        Instant observedAt = Instant.parse("2026-05-11T12:00:00Z");

        materializer.materialize(
            "habitat-overwrite",
            new HealthFact(
                UUID.randomUUID(),
                "adapter-1",
                "tuya",
                "tuya-device-abc",
                "dp-1",
                HealthStatus.DEGRADED,
                "intermittent link",
                observedAt,
                0.9
            )
        );

        assertThat(repository.findEndpointHealth("habitat-overwrite", canonicalEndpointId))
            .map(EndpointHealth::status)
            .contains(HealthStatus.DEGRADED);

        assertThat(repository.findEndpointHealth("habitat-overwrite", canonicalEndpointId))
            .map(EndpointHealth::lastSeenAt)
            .contains(observedAt);

        materializer.materialize("habitat-overwrite", capabilityFact("adapter-1"));

        assertThat(repository.findEndpointHealth("habitat-overwrite", canonicalEndpointId))
            .map(EndpointHealth::status)
            .as("endpoint health must survive a subsequent structural save")
            .contains(HealthStatus.DEGRADED);

        assertThat(repository.findEndpointHealth("habitat-overwrite", canonicalEndpointId))
            .map(EndpointHealth::lastSeenAt)
            .as("lastSeenAt must preserve the provider observation time")
            .contains(observedAt);

        repository = null;
        mutationService = null;
        queryService = null;
        materializer = null;

        H2BaseTopologyRepository newRepo =
            new H2BaseTopologyRepository(dataSource(jdbcUrl), mapper(), clock);

        CoreSnapshotQueryService recoveredQueryService =
            new CoreSnapshotQueryService(newRepo, clock);

        assertThat(recoveredQueryService.findEndpointHealth("habitat-overwrite", canonicalEndpointId))
            .map(EndpointHealth::status)
            .contains(HealthStatus.DEGRADED);

        assertThat(recoveredQueryService.findEndpointHealth("habitat-overwrite", canonicalEndpointId))
            .map(EndpointHealth::lastSeenAt)
            .contains(observedAt);
    }

    private void assertStructuralDecision(
        MaterializationDecision decision,
        TopologyChangeKind changeKind,
        String deviceId,
        String endpointId
    ) {
        assertThat(decision.kind()).isEqualTo(MaterializationDecisionKind.ACCEPT_STRUCTURAL_MUTATION);
        assertThat(decision.resultingTopologyVersion()).isPresent();
        assertThat(decision.previousTopologyVersion()).isPresent();
        assertThat(decision.resultingTopologyVersion()).isNotEqualTo(decision.previousTopologyVersion());
        assertThat(decision.emittedChanges()).isNotEmpty();
        TopologyChanged event = decision.emittedChanges().stream()
            .filter(candidate -> candidate.changeKinds().contains(changeKind))
            .findFirst()
            .orElseThrow();
        assertThat(decision.resultingTopologyVersion().orElseThrow().value())
            .isEqualTo(decision.emittedChanges().getLast().toVersion());
        assertThat(event.changeKinds()).contains(changeKind);
        assertThat(event.fromVersion()).isNotEqualTo(event.toVersion());
        assertThat(event.affectedDeviceIds()).contains(deviceId);
        if (endpointId != null) {
            assertThat(event.affectedEndpointIds()).contains(endpointId);
        }
    }

    private void assertRejectedWithoutMutation(
        MaterializationDecision decision,
        MaterializationDecisionKind kind,
        TopologyVersion expectedVersion,
        CoreSnapshotQueryService queryService
    ) {
        assertThat(decision.kind()).isEqualTo(kind);
        assertThat(decision.emittedChanges()).isEmpty();
        assertThat(queryService.findCurrentTopologyVersion("habitat-007")).contains(expectedVersion);
    }

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private String jdbcUrl() {
        String dbPath = tempDir.resolve("sc-materialization").toAbsolutePath().toString().replace('\\', '/');
        return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
    }

    private DataSource dataSource(String jdbcUrl) {
        return new DriverManagerDataSource(jdbcUrl, "sa", "");
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

    private DeviceDiscoveryFact deviceFact(String adapterInstanceId) {
        return deviceFactWithId(UUID.fromString("00000000-0000-0000-0000-000000000001"), adapterInstanceId);
    }

    private DeviceDiscoveryFact deviceFactWithId(UUID factId, String adapterInstanceId) {
        return new DeviceDiscoveryFact(
            factId,
            adapterInstanceId,
            "tuya",
            "tuya-device-abc",
            "LIGHT",
            "Kitchen Worktop Light",
            "Tuya",
            "room.kitchen",
            "zone.kitchen.worktop",
            Instant.parse("2026-05-11T12:00:01Z"),
            0.98,
            Map.of("manufacturer", "tuya", "providerRevision", "rev-1")
        );
    }

    private EndpointDiscoveryFact endpointFact(String adapterInstanceId) {
        return endpointFactWithId(UUID.fromString("00000000-0000-0000-0000-000000000002"), adapterInstanceId);
    }

    private EndpointDiscoveryFact endpointFactWithId(UUID factId, String adapterInstanceId) {
        return new EndpointDiscoveryFact(
            factId,
            adapterInstanceId,
            "tuya",
            "tuya-device-abc",
            "dp-1",
            "LIGHT",
            List.of(),
            Instant.parse("2026-05-11T12:00:02Z"),
            0.98,
            Map.of("dpCode", "switch_led", "providerRevision", "rev-1")
        );
    }

    private CapabilityDiscoveryFact capabilityFact(String adapterInstanceId) {
        return capabilityFactWithId(UUID.fromString("00000000-0000-0000-0000-000000000003"), adapterInstanceId);
    }

    private CapabilityDiscoveryFact capabilityFactWithId(UUID factId, String adapterInstanceId) {
        return new CapabilityDiscoveryFact(
            factId,
            adapterInstanceId,
            "tuya",
            "tuya-device-abc",
            "dp-1",
            "switch",
            "BINARY_SWITCH",
            Instant.parse("2026-05-11T12:00:03Z"),
            0.98,
            Map.of("dpCode", "switch_led")
        );
    }
}
