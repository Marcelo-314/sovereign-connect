package com.sovereign.connect.core.northbound;

import com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter;
import com.sovereign.connect.core.temporal.application.CancelTemporalActRequest;
import com.sovereign.connect.core.temporal.application.CancelTemporalActResult;
import com.sovereign.connect.core.temporal.application.CreateSignalTemporalActRequest;
import com.sovereign.connect.core.temporal.application.CreateSignalTemporalActResult;
import com.sovereign.connect.core.temporal.application.TemporalActApplicationPort;
import com.sovereign.connect.core.temporal.application.TemporalRequestRejection;
import com.sovereign.connect.core.temporal.application.TemporalRequestRejectionCode;
import com.sovereign.connect.core.temporal.application.TemporalRuntimeFailure;
import com.sovereign.connect.core.temporal.application.TemporalRuntimeFailureCode;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.engine.TemporalEngineStatus;
import com.sovereign.connect.core.temporal.model.TemporalActStatus;
import com.sovereign.connect.core.temporal.observation.TemporalActObservation;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationPort;
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
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.RoomTraits;
import com.sovereign.connect.core.topology.model.TopologyMetadata;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.model.ZoneTraits;
import com.sovereign.connect.core.topology.query.CoreSnapshot;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.query.DeviceSnapshot;
import com.sovereign.connect.core.topology.query.EndpointSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class NorthboundFacadeBehavioralTest {

    private static final String HABITAT = "habitat-001";
    private static final String DEVICE_ID = "device.tuya.light-1";
    private static final String ENDPOINT_ID = "endpoint.tuya.light-1.main";
    private static final String SECOND_ENDPOINT_ID = "endpoint.tuya.light-1.aux";
    private static final Instant NOW = Instant.parse("2026-05-25T12:00:00Z");

    private final CoreSnapshotQueryService queryService = mock(CoreSnapshotQueryService.class);
    private final TemporalActApplicationPort applicationPort = mock(TemporalActApplicationPort.class);
    private final TemporalActObservationPort observationPort = mock(TemporalActObservationPort.class);
    private final TemporalEngineHealth engineHealth = new TemporalEngineHealth();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ScCoreNorthboundFacade facade = new DefaultScCoreNorthboundFacade(
        queryService,
        applicationPort,
        observationPort,
        engineHealth,
        clock
    );

    @Test
    void getTopologySnapshotReturnsCanonicalTopologyWithTopologyVersion() {
        when(queryService.findCurrentSnapshot(HABITAT)).thenReturn(Optional.of(snapshot()));

        var response = facade.getTopologySnapshot(HABITAT);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().habitatId()).isEqualTo(HABITAT);
        assertThat(response.payload().topologyVersionValue()).isEqualTo("7");
        assertThat(response.payload().rooms()).hasSize(1);
        assertThat(response.payload().devices()).extracting("deviceId").containsExactly(DEVICE_ID);
        assertThat(response.payload().endpoints()).extracting("endpointId").containsExactly(ENDPOINT_ID);
    }

    @Test
    void getTopologyVersionReturnsCurrentTopologyVersion() {
        when(queryService.findCurrentTopologyVersion(HABITAT))
            .thenReturn(Optional.of(TopologyVersion.habitatVersion(HABITAT, 7)));

        var response = facade.getTopologyVersion(HABITAT);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().value()).isEqualTo("7");
        assertThat(response.payload().scopeId()).isEqualTo(HABITAT);
    }

    @Test
    void getDeviceAndEndpointResolveCanonicalIdsOnly() {
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot()));
        when(queryService.findEndpoint(HABITAT, ENDPOINT_ID)).thenReturn(Optional.of(endpointSnapshot()));

        assertThat(facade.getDevice(HABITAT, DEVICE_ID).payload().deviceId()).isEqualTo(DEVICE_ID);
        assertThat(facade.getEndpoint(HABITAT, ENDPOINT_ID).payload().endpointId()).isEqualTo(ENDPOINT_ID);

        var nativeDevice = facade.getDevice(HABITAT, "native-light-1");
        var nativeEndpoint = facade.getEndpoint(HABITAT, "main");

        assertThat(nativeDevice.status()).isEqualTo(ScNorthboundStatus.INVALID_CANONICAL_ID);
        assertThat(nativeEndpoint.status()).isEqualTo(ScNorthboundStatus.INVALID_CANONICAL_ID);
        verify(queryService, never()).findDevice(HABITAT, "native-light-1");
        verify(queryService, never()).findEndpoint(HABITAT, "main");
    }

    @Test
    void listMethodsExposeBaseTopologyOnlyFromCurrentSnapshot() {
        when(queryService.findCurrentSnapshot(HABITAT)).thenReturn(Optional.of(snapshot()));

        assertThat(facade.listRooms(HABITAT).payload()).hasSize(1);
        assertThat(facade.listZones(HABITAT).payload()).hasSize(1);
        assertThat(facade.listDevices(HABITAT).payload()).hasSize(1);
        assertThat(facade.listEndpoints(HABITAT).payload()).hasSize(1);

        verify(queryService, times(4)).findCurrentSnapshot(HABITAT);
    }

    @Test
    void locatedQueriesUseCanonicalLocationQueryPath() {
        DeviceNode device = device();
        EndpointNode endpoint = endpoint();
        when(queryService.findLocatedDevices(HABITAT, "room.living")).thenReturn(List.of(device));
        when(queryService.findLocatedEndpoints(HABITAT, "room.living")).thenReturn(List.of(endpoint));

        assertThat(facade.listDevicesLocatedIn(HABITAT, "room.living").payload())
            .extracting("deviceId")
            .containsExactly(DEVICE_ID);
        assertThat(facade.listEndpointsLocatedIn(HABITAT, "room.living").payload())
            .extracting("endpointId")
            .containsExactly(ENDPOINT_ID);

        assertThat(facade.listDevicesLocatedIn(HABITAT, "provider-room").status())
            .isEqualTo(ScNorthboundStatus.INVALID_CANONICAL_ID);
    }

    @Test
    void healthAndRuntimeMethodsUseAllowedReadSurfaces() {
        EndpointHealth endpointHealth = new EndpointHealth(HealthStatus.DEGRADED, NOW, "durable");
        when(queryService.findEndpointHealth(HABITAT, ENDPOINT_ID)).thenReturn(Optional.of(endpointHealth));
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot()));
        when(queryService.findDeviceState(HABITAT, DEVICE_ID)).thenReturn(Optional.of(Map.of("power", true)));

        var endpointHealthResponse = facade.getEndpointHealth(HABITAT, ENDPOINT_ID);
        var deviceHealthResponse = facade.getDeviceHealth(HABITAT, DEVICE_ID);
        var deviceStateResponse = facade.getDeviceRuntimeState(HABITAT, DEVICE_ID);
        var endpointStateResponse = facade.getEndpointRuntimeState(HABITAT, ENDPOINT_ID);
        var recoveryResponse = facade.getRecoveryStatus(HABITAT);

        assertThat(endpointHealthResponse.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(endpointHealthResponse.payload().status()).isEqualTo("DEGRADED");
        assertThat(deviceHealthResponse.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(deviceHealthResponse.payload().status()).isEqualTo("DEGRADED");
        assertThat(deviceHealthResponse.payload().source()).isEqualTo("DERIVED_FROM_ENDPOINTS");
        assertThat(deviceStateResponse.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(deviceStateResponse.payload().state()).containsEntry("power", true);
        assertThat(endpointStateResponse.status()).isEqualTo(ScNorthboundStatus.UNSUPPORTED_PROFILE);
        assertThat(recoveryResponse.status()).isEqualTo(ScNorthboundStatus.UNSUPPORTED_PROFILE);

        verify(queryService, times(2)).findEndpointHealth(HABITAT, ENDPOINT_ID);
        verify(queryService).findDevice(HABITAT, DEVICE_ID);
        verify(queryService).findDeviceState(HABITAT, DEVICE_ID);
    }

    @Test
    void deviceHealthDerivedHealthyWhenAllEndpointsHealthy() {
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot()));
        when(queryService.findEndpointHealth(HABITAT, ENDPOINT_ID))
            .thenReturn(Optional.of(new EndpointHealth(HealthStatus.HEALTHY, NOW, "ok")));

        var response = facade.getDeviceHealth(HABITAT, DEVICE_ID);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().status()).isEqualTo("HEALTHY");
        assertThat(response.payload().source()).isEqualTo("DERIVED_FROM_ENDPOINTS");
        assertThat(response.payload().endpointCount()).isEqualTo(1);
        assertThat(response.payload().readAt()).isEqualTo(NOW);
        verify(queryService).findEndpointHealth(HABITAT, ENDPOINT_ID);
    }

    @Test
    void deviceHealthDerivedOfflineWhenAnyEndpointOffline() {
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot(List.of(
            ENDPOINT_ID,
            SECOND_ENDPOINT_ID
        ))));
        when(queryService.findEndpointHealth(HABITAT, ENDPOINT_ID))
            .thenReturn(Optional.of(new EndpointHealth(HealthStatus.HEALTHY, NOW, "ok")));
        when(queryService.findEndpointHealth(HABITAT, SECOND_ENDPOINT_ID))
            .thenReturn(Optional.of(new EndpointHealth(HealthStatus.OFFLINE, NOW, "offline")));

        var response = facade.getDeviceHealth(HABITAT, DEVICE_ID);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().status()).isEqualTo("OFFLINE");
        assertThat(response.payload().source()).isEqualTo("DERIVED_FROM_ENDPOINTS");
        assertThat(response.payload().endpointCount()).isEqualTo(2);
        verify(queryService).findEndpointHealth(HABITAT, ENDPOINT_ID);
        verify(queryService).findEndpointHealth(HABITAT, SECOND_ENDPOINT_ID);
    }

    @Test
    void deviceHealthDerivedDegradedWhenEndpointDegradedUnknownOrMissing() {
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot(List.of(
            ENDPOINT_ID,
            SECOND_ENDPOINT_ID
        ))));
        when(queryService.findEndpointHealth(HABITAT, ENDPOINT_ID))
            .thenReturn(Optional.of(new EndpointHealth(HealthStatus.UNKNOWN, NOW, "unknown")));
        when(queryService.findEndpointHealth(HABITAT, SECOND_ENDPOINT_ID)).thenReturn(Optional.empty());

        var response = facade.getDeviceHealth(HABITAT, DEVICE_ID);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().status()).isEqualTo("DEGRADED");
        assertThat(response.payload().source()).isEqualTo("DERIVED_FROM_ENDPOINTS");
        assertThat(response.payload().endpointCount()).isEqualTo(2);
    }

    @Test
    void deviceHealthEmptyEndpointSetReturnsUnknownPendingNormalization() {
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.of(deviceSnapshot(List.of())));

        var response = facade.getDeviceHealth(HABITAT, DEVICE_ID);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION);
        assertThat(response.payload().status()).isEqualTo("UNKNOWN_PENDING_NORMALIZATION");
        assertThat(response.payload().source()).isEqualTo("UNKNOWN_PENDING_NORMALIZATION");
        assertThat(response.payload().endpointCount()).isZero();
        assertThat(response.payload().readAt()).isEqualTo(NOW);
        verify(queryService, never()).findEndpointHealth(any(), any());
    }

    @Test
    void diagnosticsContainsMandatoryFields() {
        engineHealth.transitionTo(TemporalEngineStatus.RUNNING);
        when(queryService.findCurrentTopologyVersion(HABITAT))
            .thenReturn(Optional.of(TopologyVersion.habitatVersion(HABITAT, 7)));

        var response = facade.getNorthboundDiagnostics(HABITAT);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().habitatId()).isEqualTo(HABITAT);
        assertThat(response.payload().topologyVersion()).isEqualTo("7");
        assertThat(response.payload().temporalEngineStatus().engineStatus()).isEqualTo("RUNNING");
        assertThat(response.payload().migrationReadiness().status()).isEqualTo("UNKNOWN_PENDING_NORMALIZATION");
        assertThat(response.payload().migrationReadiness().source()).isEqualTo("migration.readiness");
        assertThat(response.payload().readAt()).isEqualTo(NOW);
        assertThat(response.payload().warnings())
            .anySatisfy(warning -> {
                assertThat(warning.code()).isEqualTo("MIGRATION_READINESS_PENDING_NORMALIZATION");
                assertThat(warning.source()).isEqualTo("migration.readiness");
            });
    }

    @Test
    void temporalRuntimeStatusMapsTemporalEngineHealth() {
        engineHealth.transitionTo(TemporalEngineStatus.RUNNING);
        engineHealth.incrementFired();
        engineHealth.incrementMisfired(2);
        engineHealth.incrementCancelled();
        engineHealth.incrementFailed(3);
        engineHealth.incrementSkipped();

        var response = facade.getTemporalRuntimeStatus(HABITAT);

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(response.payload().engineStatus()).isEqualTo("RUNNING");
        assertThat(response.payload().isReady()).isTrue();
        assertThat(response.payload().firedTotal()).isEqualTo(1);
        assertThat(response.payload().misfiredTotal()).isEqualTo(2);
        assertThat(response.payload().cancelledTotal()).isEqualTo(1);
        assertThat(response.payload().failedTotal()).isEqualTo(3);
        assertThat(response.payload().skippedTotal()).isEqualTo(1);
    }

    @Test
    void createSignalTemporalActMapsAllResultVariantsCorrectly() {
        TemporalActObservation observation = observation(TemporalActStatus.PENDING);
        NorthboundCreateSignalTemporalActRequest request = validCreateRequest();

        when(applicationPort.createSignalTemporalAct(any()))
            .thenReturn(new CreateSignalTemporalActResult.Accepted(observation));
        var accepted = facade.createSignalTemporalAct(HABITAT, request);
        assertThat(accepted.status()).isEqualTo(ScNorthboundStatus.ACCEPTED);
        assertThat(accepted.payload().temporalActId()).isEqualTo("act-001");

        ArgumentCaptor<CreateSignalTemporalActRequest> createCaptor =
            ArgumentCaptor.forClass(CreateSignalTemporalActRequest.class);
        verify(applicationPort).createSignalTemporalAct(createCaptor.capture());
        assertThat(createCaptor.getValue().habitatId()).isEqualTo(HABITAT);
        assertThat(createCaptor.getValue().requestedAt()).isEqualTo(NOW);

        when(applicationPort.createSignalTemporalAct(any()))
            .thenReturn(new CreateSignalTemporalActResult.IdempotentReplay(observation));
        var replay = facade.createSignalTemporalAct(HABITAT, request);
        assertThat(replay.status()).isEqualTo(ScNorthboundStatus.ACCEPTED);
        assertThat(replay.warnings()).extracting("code").contains("IDEMPOTENT_REPLAY");
        assertThat(replay.warnings()).extracting("source").contains("temporal.application");

        when(applicationPort.createSignalTemporalAct(any()))
            .thenReturn(new CreateSignalTemporalActResult.Rejected(new TemporalRequestRejection(
                TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT,
                "conflict"
            )));
        var rejected = facade.createSignalTemporalAct(HABITAT, request);
        assertThat(rejected.status()).isEqualTo(ScNorthboundStatus.INVALID_REQUEST);

        when(applicationPort.createSignalTemporalAct(any()))
            .thenReturn(new CreateSignalTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                "failed"
            )));
        var failed = facade.createSignalTemporalAct(HABITAT, request);
        assertThat(failed.status()).isEqualTo(ScNorthboundStatus.INTERNAL_ERROR);
    }

    @Test
    void cancelTemporalActMapsAllResultVariantsCorrectly() {
        TemporalActObservation observation = observation(TemporalActStatus.CANCELLED);
        NorthboundCancelTemporalActRequest request = validCancelRequest();

        when(applicationPort.cancelTemporalAct(any()))
            .thenReturn(new CancelTemporalActResult.Cancelled(observation));
        assertThat(facade.cancelTemporalAct(HABITAT, request).status()).isEqualTo(ScNorthboundStatus.CANCELLED);

        ArgumentCaptor<CancelTemporalActRequest> cancelCaptor = ArgumentCaptor.forClass(CancelTemporalActRequest.class);
        verify(applicationPort).cancelTemporalAct(cancelCaptor.capture());
        assertThat(cancelCaptor.getValue().temporalActId()).isEqualTo("act-001");
        assertThat(cancelCaptor.getValue().requestedAt()).isEqualTo(NOW);

        when(applicationPort.cancelTemporalAct(any()))
            .thenReturn(new CancelTemporalActResult.AlreadyTerminal(observation));
        var terminal = facade.cancelTemporalAct(HABITAT, request);
        assertThat(terminal.status()).isEqualTo(ScNorthboundStatus.OK);
        assertThat(terminal.warnings()).extracting("code").contains("ALREADY_TERMINAL");

        when(applicationPort.cancelTemporalAct(any()))
            .thenReturn(new CancelTemporalActResult.NotFound(HABITAT, "act-404"));
        assertThat(facade.cancelTemporalAct(HABITAT, request).status()).isEqualTo(ScNorthboundStatus.NOT_FOUND);

        when(applicationPort.cancelTemporalAct(any()))
            .thenReturn(new CancelTemporalActResult.IdempotentReplay(observation));
        var replay = facade.cancelTemporalAct(HABITAT, request);
        assertThat(replay.status()).isEqualTo(ScNorthboundStatus.CANCELLED);
        assertThat(replay.warnings()).extracting("code").contains("IDEMPOTENT_REPLAY");

        when(applicationPort.cancelTemporalAct(any()))
            .thenReturn(new CancelTemporalActResult.Rejected(new TemporalRequestRejection(
                TemporalRequestRejectionCode.MISSING_REQUESTED_BY_REF,
                "missing"
            )));
        assertThat(facade.cancelTemporalAct(HABITAT, request).status()).isEqualTo(ScNorthboundStatus.INVALID_REQUEST);

        when(applicationPort.cancelTemporalAct(any()))
            .thenReturn(new CancelTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                "failed"
            )));
        assertThat(facade.cancelTemporalAct(HABITAT, request).status()).isEqualTo(ScNorthboundStatus.INTERNAL_ERROR);
    }

    @Test
    void temporalObservationMethodsDelegateToObservationPort() {
        TemporalActObservation active = observation(TemporalActStatus.ARMED);
        TemporalActObservation terminal = observation(TemporalActStatus.CANCELLED);
        TemporalActObservation misfired = observation(TemporalActStatus.MISFIRED);
        when(observationPort.findById(HABITAT, "act-001")).thenReturn(Optional.of(active));
        when(observationPort.listActive(HABITAT)).thenReturn(List.of(active));
        when(observationPort.listTerminal(HABITAT, 25)).thenReturn(List.of(terminal));
        when(observationPort.listMisfired(HABITAT, 50)).thenReturn(List.of(misfired));

        assertThat(facade.getTemporalAct(HABITAT, "act-001").payload().status()).isEqualTo("ARMED");
        assertThat(facade.listTemporalActs(HABITAT, null).payload()).extracting("status").containsExactly("ARMED");
        assertThat(facade.listTemporalActs(
            HABITAT,
            new NorthboundTemporalActFilter(NorthboundTemporalActFilter.Mode.TERMINAL, 25)
        ).payload()).extracting("status").containsExactly("CANCELLED");
        assertThat(facade.listTemporalActs(
            HABITAT,
            new NorthboundTemporalActFilter(NorthboundTemporalActFilter.Mode.MISFIRED, -1)
        ).payload()).extracting("status").containsExactly("MISFIRED");

        verify(observationPort).listActive(HABITAT);
        verify(observationPort).listTerminal(HABITAT, 25);
        verify(observationPort).listMisfired(HABITAT, 50);
    }

    @Test
    void commonNegativeCasesReturnNorthboundStatuses() {
        assertThat(facade.getTopologySnapshot(" ").status()).isEqualTo(ScNorthboundStatus.INVALID_REQUEST);
        assertThat(facade.createSignalTemporalAct(HABITAT, null).status())
            .isEqualTo(ScNorthboundStatus.INVALID_REQUEST);
        assertThat(facade.createSignalTemporalAct(HABITAT, new NorthboundCreateSignalTemporalActRequest(
            NOW.plusSeconds(60),
            "label",
            "REMINDER",
            "notify.user",
            "user-1",
            " "
        )).status()).isEqualTo(ScNorthboundStatus.INVALID_REQUEST);
        assertThat(facade.cancelTemporalAct(HABITAT, null).status()).isEqualTo(ScNorthboundStatus.INVALID_REQUEST);
    }

    @Test
    void createSignalTemporalActWithPastDueAtReturnsValidationError() {
        var response = facade.createSignalTemporalAct(HABITAT, new NorthboundCreateSignalTemporalActRequest(
            NOW.minusSeconds(3600),
            "label",
            "REMINDER",
            "notify.user",
            "user-1",
            "idem-past"
        ));

        assertThat(response.status()).isEqualTo(ScNorthboundStatus.VALIDATION_ERROR);
        assertThat(response.payload()).isNull();
        assertThat(response.error().code()).isEqualTo("INVALID_DUE_AT");
        assertThat(response.error().source()).isEqualTo("northbound.validation");
        verify(applicationPort, never()).createSignalTemporalAct(any());
    }

    @Test
    void errorAndWarningSourcesArePopulated() {
        when(queryService.findDevice(HABITAT, DEVICE_ID)).thenReturn(Optional.empty());
        TemporalActObservation observation = observation(TemporalActStatus.PENDING);
        when(applicationPort.createSignalTemporalAct(any()))
            .thenReturn(new CreateSignalTemporalActResult.IdempotentReplay(observation));

        var notFound = facade.getDevice(HABITAT, DEVICE_ID);
        var unsupported = facade.getEndpointRuntimeState(HABITAT, ENDPOINT_ID);
        var replay = facade.createSignalTemporalAct(HABITAT, validCreateRequest());

        assertThat(notFound.error().source()).isEqualTo("northbound.query");
        assertThat(unsupported.error().source()).isEqualTo("northbound.unsupported_profile");
        assertThat(replay.warnings()).extracting("source").contains("temporal.application");
    }

    private CoreSnapshot snapshot() {
        HabitatBaseTopology topology = topology();
        return new CoreSnapshot(
            HABITAT,
            topology.topologyVersion(),
            topology,
            Map.of(DEVICE_ID, Map.of("power", true)),
            Map.of(ENDPOINT_ID, new EndpointHealth(HealthStatus.HEALTHY, NOW, "ok")),
            NOW
        );
    }

    private DeviceSnapshot deviceSnapshot() {
        return deviceSnapshot(List.of(ENDPOINT_ID));
    }

    private DeviceSnapshot deviceSnapshot(List<String> endpointIds) {
        return new DeviceSnapshot(
            HABITAT,
            TopologyVersion.habitatVersion(HABITAT, 7),
            device(endpointIds),
            Map.of(),
            NOW
        );
    }

    private EndpointSnapshot endpointSnapshot() {
        return new EndpointSnapshot(
            HABITAT,
            TopologyVersion.habitatVersion(HABITAT, 7),
            endpoint(),
            new EndpointHealth(HealthStatus.HEALTHY, NOW, "ok"),
            NOW
        );
    }

    private HabitatBaseTopology topology() {
        return new HabitatBaseTopology(
            HABITAT,
            TopologyVersion.habitatVersion(HABITAT, 7),
            List.of(room()),
            List.of(zone()),
            List.of(device()),
            List.of(endpoint()),
            List.of(),
            new TopologyMetadata("v1", NOW, "test", "checksum")
        );
    }

    private RoomNode room() {
        return new RoomNode(
            "room.living",
            "Living",
            List.of("zone.living.main"),
            List.of(DEVICE_ID),
            List.of(ENDPOINT_ID),
            new RoomTraits(false, false)
        );
    }

    private ZoneNode zone() {
        return new ZoneNode(
            "zone.living.main",
            "Main",
            "room.living",
            List.of(DEVICE_ID),
            List.of(ENDPOINT_ID),
            new ZoneTraits(false)
        );
    }

    private DeviceNode device() {
        return device(List.of(ENDPOINT_ID));
    }

    private DeviceNode device(List<String> endpointIds) {
        return new DeviceNode(
            DEVICE_ID,
            "light-1",
            "Living Light",
            "room.living",
            "zone.living.main",
            DeviceKind.LIGHT,
            DeviceProvider.TUYA,
            endpointIds,
            List.of(capability("capability.tuya.light-1.power", "Power", CapabilityKind.BINARY_SWITCH)),
            new DeviceTraits(false, false, false),
            new DeviceHealth(HealthStatus.UNKNOWN, null, null),
            new ProviderDeviceRef("tuya", "native-light-1", Map.of())
        );
    }

    private EndpointNode endpoint() {
        return new EndpointNode(
            ENDPOINT_ID,
            DEVICE_ID,
            "main",
            "Main Light",
            EndpointKind.LIGHT,
            "room.living",
            "zone.living.main",
            List.of(capability("capability.tuya.light-1.main.power", "Power", CapabilityKind.BINARY_SWITCH)),
            new EndpointTraits(true, true, true, true, false, false),
            new EndpointHealth(HealthStatus.HEALTHY, NOW, "aggregate"),
            new ProviderEndpointRef("tuya", "native-light-1", "main", Map.of()),
            EndpointMetadata.empty()
        );
    }

    private CapabilityNode capability(String id, String name, CapabilityKind kind) {
        return new CapabilityNode(id, name, kind, new CapabilityTraits(true, true, false));
    }

    private NorthboundCreateSignalTemporalActRequest validCreateRequest() {
        return new NorthboundCreateSignalTemporalActRequest(
            NOW.plusSeconds(60),
            "Check oven",
            "REMINDER",
            "notify.user",
            "user-1",
            "idem-create"
        );
    }

    private NorthboundCancelTemporalActRequest validCancelRequest() {
        return new NorthboundCancelTemporalActRequest("act-001", "user-1", "idem-cancel", "not needed");
    }

    private TemporalActObservation observation(TemporalActStatus status) {
        return new TemporalActObservation(
            "act-001",
            HABITAT,
            status,
            NOW.plusSeconds(60),
            "SIGNAL",
            "Check oven",
            "REMINDER",
            "notify.user",
            "user-1",
            NOW,
            NOW,
            null,
            status == TemporalActStatus.CANCELLED ? NOW : null,
            status == TemporalActStatus.CANCELLED ? "cancelled" : null
        );
    }
}
