package com.sovereign.connect.core.northbound;

import com.sovereign.connect.core.northbound.runtime.NorthboundDeviceHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundDiagnosticsView;
import com.sovereign.connect.core.northbound.runtime.NorthboundEndpointHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundMigrationReadinessView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRecoveryStatusView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRuntimeStateView;
import com.sovereign.connect.core.northbound.runtime.NorthboundTemporalRuntimeStatusView;
import com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import com.sovereign.connect.core.northbound.topology.NorthboundDeviceView;
import com.sovereign.connect.core.northbound.topology.NorthboundEndpointView;
import com.sovereign.connect.core.northbound.topology.NorthboundRoomView;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologyVersionView;
import com.sovereign.connect.core.northbound.topology.NorthboundZoneView;
import com.sovereign.connect.core.temporal.application.CancelTemporalActRequest;
import com.sovereign.connect.core.temporal.application.CancelTemporalActResult;
import com.sovereign.connect.core.temporal.application.CreateSignalTemporalActRequest;
import com.sovereign.connect.core.temporal.application.CreateSignalTemporalActResult;
import com.sovereign.connect.core.temporal.application.TemporalActApplicationPort;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationPort;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.HealthStatus;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.query.CoreSnapshotQueryService;
import com.sovereign.connect.core.topology.query.DeviceSnapshot;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DefaultScCoreNorthboundFacade implements ScCoreNorthboundFacade {

    private static final int DEFAULT_MAX_RESULTS = 50;
    private static final int MAX_RESULTS_CAP = 500;

    private final CoreSnapshotQueryService queryService;
    private final TemporalActApplicationPort temporalActApplicationPort;
    private final TemporalActObservationPort temporalActObservationPort;
    private final TemporalEngineHealth engineHealth;
    private final Clock clock;

    public DefaultScCoreNorthboundFacade(
        CoreSnapshotQueryService queryService,
        TemporalActApplicationPort temporalActApplicationPort,
        TemporalActObservationPort temporalActObservationPort,
        TemporalEngineHealth engineHealth,
        Clock clock
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService is required");
        this.temporalActApplicationPort = Objects.requireNonNull(
            temporalActApplicationPort,
            "temporalActApplicationPort is required"
        );
        this.temporalActObservationPort = Objects.requireNonNull(
            temporalActObservationPort,
            "temporalActObservationPort is required"
        );
        this.engineHealth = Objects.requireNonNull(engineHealth, "engineHealth is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public ScNorthboundResponse<NorthboundTopologySnapshot> getTopologySnapshot(String habitatId) {
        if (isBlank(habitatId)) {
            return ScNorthboundResponse.invalidRequest("INVALID_HABITAT_ID", "habitatId is required");
        }
        return queryService.findCurrentSnapshot(habitatId)
            .map(NorthboundMapper::toTopologySnapshot)
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TOPOLOGY_NOT_FOUND", "topology not found"));
    }

    @Override
    public ScNorthboundResponse<NorthboundTopologyVersionView> getTopologyVersion(String habitatId) {
        if (isBlank(habitatId)) {
            return ScNorthboundResponse.invalidRequest("INVALID_HABITAT_ID", "habitatId is required");
        }
        return queryService.findCurrentTopologyVersion(habitatId)
            .map(version -> NorthboundMapper.toTopologyVersionView(habitatId, version))
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TOPOLOGY_VERSION_NOT_FOUND", "topology version not found"));
    }

    @Override
    public ScNorthboundResponse<NorthboundDeviceView> getDevice(String habitatId, String deviceId) {
        if (!isCanonical(deviceId, "device.")) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_DEVICE_ID", "deviceId must be canonical");
        }
        return queryService.findDevice(habitatId, deviceId)
            .map(NorthboundMapper::toDeviceView)
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("DEVICE_NOT_FOUND", "device not found"));
    }

    @Override
    public ScNorthboundResponse<NorthboundEndpointView> getEndpoint(String habitatId, String endpointId) {
        if (!isCanonical(endpointId, "endpoint.")) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_ENDPOINT_ID", "endpointId must be canonical");
        }
        return queryService.findEndpoint(habitatId, endpointId)
            .map(NorthboundMapper::toEndpointView)
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("ENDPOINT_NOT_FOUND", "endpoint not found"));
    }

    @Override
    public ScNorthboundResponse<List<NorthboundRoomView>> listRooms(String habitatId) {
        return queryService.findCurrentSnapshot(habitatId)
            .map(snapshot -> snapshot.topology().rooms().stream().map(NorthboundMapper::toRoomView).toList())
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TOPOLOGY_NOT_FOUND", "topology not found"));
    }

    @Override
    public ScNorthboundResponse<List<NorthboundZoneView>> listZones(String habitatId) {
        return queryService.findCurrentSnapshot(habitatId)
            .map(snapshot -> snapshot.topology().zones().stream().map(NorthboundMapper::toZoneView).toList())
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TOPOLOGY_NOT_FOUND", "topology not found"));
    }

    @Override
    public ScNorthboundResponse<List<NorthboundDeviceView>> listDevices(String habitatId) {
        return queryService.findCurrentSnapshot(habitatId)
            .map(snapshot -> snapshot.topology().devices().stream().map(NorthboundMapper::toDeviceView).toList())
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TOPOLOGY_NOT_FOUND", "topology not found"));
    }

    @Override
    public ScNorthboundResponse<List<NorthboundEndpointView>> listEndpoints(String habitatId) {
        return queryService.findCurrentSnapshot(habitatId)
            .map(snapshot -> snapshot.topology().endpoints().stream().map(NorthboundMapper::toEndpointView).toList())
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TOPOLOGY_NOT_FOUND", "topology not found"));
    }

    @Override
    public ScNorthboundResponse<List<NorthboundDeviceView>> listDevicesLocatedIn(String habitatId, String roomOrZoneId) {
        if (!isCanonicalLocation(roomOrZoneId)) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_LOCATION_ID", "roomOrZoneId must be canonical");
        }
        return ScNorthboundResponse.ok(
            queryService.findLocatedDevices(habitatId, roomOrZoneId).stream()
                .map(NorthboundMapper::toDeviceView)
                .toList()
        );
    }

    @Override
    public ScNorthboundResponse<List<NorthboundEndpointView>> listEndpointsLocatedIn(
        String habitatId,
        String roomOrZoneId
    ) {
        if (!isCanonicalLocation(roomOrZoneId)) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_LOCATION_ID", "roomOrZoneId must be canonical");
        }
        return ScNorthboundResponse.ok(
            queryService.findLocatedEndpoints(habitatId, roomOrZoneId).stream()
                .map(NorthboundMapper::toEndpointView)
                .toList()
        );
    }

    @Override
    public ScNorthboundResponse<NorthboundEndpointHealthView> getEndpointHealth(String habitatId, String endpointId) {
        if (!isCanonical(endpointId, "endpoint.")) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_ENDPOINT_ID", "endpointId must be canonical");
        }
        return queryService.findEndpointHealth(habitatId, endpointId)
            .map(health -> NorthboundMapper.toEndpointHealthView(endpointId, health))
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("ENDPOINT_HEALTH_NOT_FOUND", "endpoint health not found"));
    }

    @Override
    public ScNorthboundResponse<NorthboundDeviceHealthView> getDeviceHealth(String habitatId, String deviceId) {
        if (!isCanonical(deviceId, "device.")) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_DEVICE_ID", "deviceId must be canonical");
        }
        return queryService.findDevice(habitatId, deviceId)
            .map(deviceSnapshot -> deriveDeviceHealth(habitatId, deviceId, deviceSnapshot))
            .orElseGet(() -> ScNorthboundResponse.notFound("DEVICE_NOT_FOUND", "Device not found: " + deviceId));
    }

    @Override
    public ScNorthboundResponse<NorthboundDiagnosticsView> getNorthboundDiagnostics(String habitatId) {
        String topologyVersion = queryService.findCurrentTopologyVersion(habitatId)
            .map(TopologyVersion::value)
            .orElse("UNKNOWN");
        NorthboundTemporalRuntimeStatusView temporalStatus =
            NorthboundMapper.toTemporalRuntimeStatusView(habitatId, engineHealth);
        NorthboundMigrationReadinessView migrationReadiness = new NorthboundMigrationReadinessView(
            "UNKNOWN_PENDING_NORMALIZATION",
            "migration.readiness",
            "No migration readiness read port exists; deferred to future MU."
        );
        ScNorthboundWarning migrationWarning = new ScNorthboundWarning(
            "MIGRATION_READINESS_PENDING_NORMALIZATION",
            "Migration readiness is not yet exposed through a Northbound-safe port.",
            "migration.readiness"
        );

        return ScNorthboundResponse.ok(
            new NorthboundDiagnosticsView(
                habitatId,
                topologyVersion,
                temporalStatus,
                migrationReadiness,
                Instant.now(clock),
                List.of(migrationWarning)
            )
        );
    }

    @Override
    public ScNorthboundResponse<NorthboundRuntimeStateView> getDeviceRuntimeState(String habitatId, String deviceId) {
        if (!isCanonical(deviceId, "device.")) {
            return ScNorthboundResponse.invalidCanonicalId("INVALID_DEVICE_ID", "deviceId must be canonical");
        }
        return queryService.findDeviceState(habitatId, deviceId)
            .map(state -> NorthboundMapper.toDeviceRuntimeStateView(habitatId, deviceId, state, Instant.now(clock)))
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("DEVICE_STATE_NOT_FOUND", "device runtime state not found"));
    }

    @Override
    public ScNorthboundResponse<NorthboundRuntimeStateView> getEndpointRuntimeState(String habitatId, String endpointId) {
        return ScNorthboundResponse.unsupportedProfile(
            "ENDPOINT_RUNTIME_STATE_UNSUPPORTED",
            "durable endpoint runtime state is not available in MU-019"
        );
    }

    @Override
    public ScNorthboundResponse<NorthboundRecoveryStatusView> getRecoveryStatus(String habitatId) {
        return ScNorthboundResponse.unsupportedProfile(
            "RECOVERY_STATUS_UNSUPPORTED",
            "explicit recovery read model is not available in MU-019"
        );
    }

    @Override
    public ScNorthboundResponse<NorthboundTemporalRuntimeStatusView> getTemporalRuntimeStatus(String habitatId) {
        return ScNorthboundResponse.ok(NorthboundMapper.toTemporalRuntimeStatusView(habitatId, engineHealth));
    }

    @Override
    public ScNorthboundResponse<NorthboundTemporalActView> createSignalTemporalAct(
        String habitatId,
        NorthboundCreateSignalTemporalActRequest request
    ) {
        ScNorthboundResponse<NorthboundTemporalActView> validation = validateCreateRequest(habitatId, request);
        if (validation != null) {
            return validation;
        }
        CreateSignalTemporalActRequest applicationRequest = new CreateSignalTemporalActRequest(
            habitatId,
            request.dueAt(),
            request.label(),
            request.signalKind(),
            request.notificationTargetRef(),
            request.createdByRef(),
            request.idempotencyKey(),
            Instant.now(clock)
        );
        return mapCreateResult(temporalActApplicationPort.createSignalTemporalAct(applicationRequest));
    }

    @Override
    public ScNorthboundResponse<NorthboundTemporalActView> cancelTemporalAct(
        String habitatId,
        NorthboundCancelTemporalActRequest request
    ) {
        ScNorthboundResponse<NorthboundTemporalActView> validation = validateCancelRequest(habitatId, request);
        if (validation != null) {
            return validation;
        }
        CancelTemporalActRequest applicationRequest = new CancelTemporalActRequest(
            habitatId,
            request.temporalActId(),
            request.requestedByRef(),
            request.idempotencyKey(),
            request.reason(),
            Instant.now(clock)
        );
        return mapCancelResult(temporalActApplicationPort.cancelTemporalAct(applicationRequest));
    }

    @Override
    public ScNorthboundResponse<NorthboundTemporalActView> getTemporalAct(String habitatId, String temporalActId) {
        if (isBlank(temporalActId)) {
            return ScNorthboundResponse.invalidRequest("INVALID_TEMPORAL_ACT_ID", "temporalActId is required");
        }
        return temporalActObservationPort.findById(habitatId, temporalActId)
            .map(NorthboundMapper::toTemporalActView)
            .map(ScNorthboundResponse::ok)
            .orElseGet(() -> ScNorthboundResponse.notFound("TEMPORAL_ACT_NOT_FOUND", "temporal act not found"));
    }

    @Override
    public ScNorthboundResponse<List<NorthboundTemporalActView>> listTemporalActs(
        String habitatId,
        NorthboundTemporalActFilter filter
    ) {
        NorthboundTemporalActFilter.Mode mode = filter == null || filter.mode() == null
            ? NorthboundTemporalActFilter.Mode.ACTIVE
            : filter.mode();
        int maxResults = normalizeMaxResults(filter == null ? null : filter.maxResults());
        List<NorthboundTemporalActView> acts = switch (mode) {
            case ACTIVE -> NorthboundMapper.toTemporalActViews(temporalActObservationPort.listActive(habitatId));
            case TERMINAL -> NorthboundMapper.toTemporalActViews(
                temporalActObservationPort.listTerminal(habitatId, maxResults)
            );
            case MISFIRED -> NorthboundMapper.toTemporalActViews(
                temporalActObservationPort.listMisfired(habitatId, maxResults)
            );
        };
        return ScNorthboundResponse.ok(acts);
    }

    private ScNorthboundResponse<NorthboundTemporalActView> mapCreateResult(CreateSignalTemporalActResult result) {
        return switch (result) {
            case CreateSignalTemporalActResult.Accepted accepted -> ScNorthboundResponse.accepted(
                NorthboundMapper.toTemporalActView(accepted.temporalAct())
            );
            case CreateSignalTemporalActResult.IdempotentReplay replay -> ScNorthboundResponse.of(
                ScNorthboundStatus.ACCEPTED,
                NorthboundMapper.toTemporalActView(replay.act()),
                List.of(new ScNorthboundWarning(
                    "IDEMPOTENT_REPLAY",
                    "Request already processed",
                    "temporal.application"
                )),
                null
            );
            case CreateSignalTemporalActResult.Rejected rejected -> ScNorthboundResponse.invalidRequest(
                "REJECTED",
                rejected.rejection().code().name() + ": " + rejected.rejection().message()
            );
            case CreateSignalTemporalActResult.Failed failed -> ScNorthboundResponse.internalError(
                "TEMPORAL_ENGINE_FAILURE",
                failed.failure().code().name() + ": " + failed.failure().message()
            );
        };
    }

    private ScNorthboundResponse<NorthboundTemporalActView> mapCancelResult(CancelTemporalActResult result) {
        return switch (result) {
            case CancelTemporalActResult.Cancelled cancelled -> ScNorthboundResponse.cancelled(
                NorthboundMapper.toTemporalActView(cancelled.temporalAct())
            );
            case CancelTemporalActResult.AlreadyTerminal terminal -> ScNorthboundResponse.of(
                ScNorthboundStatus.OK,
                NorthboundMapper.toTemporalActView(terminal.act()),
                List.of(new ScNorthboundWarning(
                    "ALREADY_TERMINAL",
                    "Act was already in terminal state",
                    "temporal.application"
                )),
                null
            );
            case CancelTemporalActResult.NotFound notFound -> ScNorthboundResponse.notFound(
                "TEMPORAL_ACT_NOT_FOUND",
                "temporal act not found: " + notFound.id()
            );
            case CancelTemporalActResult.IdempotentReplay replay -> ScNorthboundResponse.of(
                ScNorthboundStatus.CANCELLED,
                NorthboundMapper.toTemporalActView(replay.act()),
                List.of(new ScNorthboundWarning(
                    "IDEMPOTENT_REPLAY",
                    "Cancel already recorded",
                    "temporal.application"
                )),
                null
            );
            case CancelTemporalActResult.Rejected rejected -> ScNorthboundResponse.invalidRequest(
                "REJECTED",
                rejected.rejection().code().name() + ": " + rejected.rejection().message()
            );
            case CancelTemporalActResult.Failed failed -> ScNorthboundResponse.internalError(
                "TEMPORAL_ENGINE_FAILURE",
                failed.failure().code().name() + ": " + failed.failure().message()
            );
        };
    }

    private ScNorthboundResponse<NorthboundTemporalActView> validateCreateRequest(
        String habitatId,
        NorthboundCreateSignalTemporalActRequest request
    ) {
        if (isBlank(habitatId)) {
            return ScNorthboundResponse.invalidRequest("INVALID_HABITAT_ID", "habitatId is required");
        }
        if (request == null) {
            return ScNorthboundResponse.invalidRequest("INVALID_REQUEST", "request is required");
        }
        if (request.dueAt() == null) {
            return ScNorthboundResponse.invalidRequest("INVALID_DUE_AT", "dueAt is required");
        }
        if (!request.dueAt().isAfter(Instant.now(clock))) {
            return ScNorthboundResponse.validationError(
                "INVALID_DUE_AT",
                "dueAt must be a future Instant"
            );
        }
        if (isBlank(request.signalKind())) {
            return ScNorthboundResponse.invalidRequest("INVALID_SIGNAL_KIND", "signalKind is required");
        }
        if (isBlank(request.createdByRef())) {
            return ScNorthboundResponse.invalidRequest("INVALID_CREATED_BY", "createdByRef is required");
        }
        if (isBlank(request.idempotencyKey())) {
            return ScNorthboundResponse.invalidRequest("INVALID_IDEMPOTENCY_KEY", "idempotencyKey is required");
        }
        return null;
    }

    private ScNorthboundResponse<NorthboundTemporalActView> validateCancelRequest(
        String habitatId,
        NorthboundCancelTemporalActRequest request
    ) {
        if (isBlank(habitatId)) {
            return ScNorthboundResponse.invalidRequest("INVALID_HABITAT_ID", "habitatId is required");
        }
        if (request == null) {
            return ScNorthboundResponse.invalidRequest("INVALID_REQUEST", "request is required");
        }
        if (isBlank(request.temporalActId())) {
            return ScNorthboundResponse.invalidRequest("INVALID_TEMPORAL_ACT_ID", "temporalActId is required");
        }
        if (isBlank(request.requestedByRef())) {
            return ScNorthboundResponse.invalidRequest("INVALID_REQUESTED_BY", "requestedByRef is required");
        }
        if (isBlank(request.idempotencyKey())) {
            return ScNorthboundResponse.invalidRequest("INVALID_IDEMPOTENCY_KEY", "idempotencyKey is required");
        }
        return null;
    }

    private int normalizeMaxResults(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_MAX_RESULTS;
        }
        return Math.min(requested, MAX_RESULTS_CAP);
    }

    private ScNorthboundResponse<NorthboundDeviceHealthView> deriveDeviceHealth(
        String habitatId,
        String deviceId,
        DeviceSnapshot deviceSnapshot
    ) {
        List<String> endpointIds = deviceSnapshot.device().endpointIds();
        int endpointCount = endpointIds.size();
        Instant readAt = Instant.now(clock);
        if (endpointIds.isEmpty()) {
            return ScNorthboundResponse.of(
                ScNorthboundStatus.UNKNOWN_PENDING_NORMALIZATION,
                new NorthboundDeviceHealthView(
                    deviceId,
                    "UNKNOWN_PENDING_NORMALIZATION",
                    "UNKNOWN_PENDING_NORMALIZATION",
                    0,
                    readAt,
                    List.of()
                ),
                List.of(),
                null
            );
        }

        HealthStatus derived = HealthStatus.HEALTHY;
        for (String endpointId : endpointIds) {
            HealthStatus endpointStatus = queryService.findEndpointHealth(habitatId, endpointId)
                .map(EndpointHealth::status)
                .orElse(HealthStatus.UNKNOWN);
            if (endpointStatus == HealthStatus.OFFLINE) {
                derived = HealthStatus.OFFLINE;
                break;
            }
            if (endpointStatus == HealthStatus.DEGRADED || endpointStatus == HealthStatus.UNKNOWN) {
                derived = HealthStatus.DEGRADED;
            }
        }

        return ScNorthboundResponse.ok(new NorthboundDeviceHealthView(
            deviceId,
            derived.name(),
            "DERIVED_FROM_ENDPOINTS",
            endpointCount,
            readAt,
            List.of()
        ));
    }

    private boolean isCanonicalLocation(String id) {
        return isCanonical(id, "room.") || isCanonical(id, "zone.");
    }

    private boolean isCanonical(String id, String prefix) {
        return !isBlank(id) && id.startsWith(prefix);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
