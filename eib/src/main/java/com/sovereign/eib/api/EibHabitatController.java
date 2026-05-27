package com.sovereign.eib.api;

import com.sovereign.eib.config.EibDiagnosticAdminProperties;
import com.sovereign.eib.domain.EffectiveDeviceView;
import com.sovereign.eib.domain.EffectiveDiagnosticsView;
import com.sovereign.eib.domain.EffectiveEndpointView;
import com.sovereign.eib.domain.EffectiveHabitatView;
import com.sovereign.eib.domain.EibError;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibResponse;
import com.sovereign.eib.northbound.EibScNorthboundClient;
import com.sovereign.eib.northbound.EibUpstreamUnavailableException;
import com.sovereign.eib.northbound.dto.NorthboundDiagnosticsViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;
import com.sovereign.eib.service.EibCanonicalEnvelopeMapper;
import com.sovereign.eib.service.EibEffectiveViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/eib/v1/habitats/{habitatId}")
public class EibHabitatController {

    private final EibEffectiveViewService effectiveViewService;
    private final EibScNorthboundClient client;
    private final EibCanonicalEnvelopeMapper envelopeMapper;
    private final EibDiagnosticAdminProperties diagnosticAdminProperties;

    public EibHabitatController(
            EibEffectiveViewService effectiveViewService,
            EibScNorthboundClient client,
            EibCanonicalEnvelopeMapper envelopeMapper,
            EibDiagnosticAdminProperties diagnosticAdminProperties
    ) {
        this.effectiveViewService = effectiveViewService;
        this.client = client;
        this.envelopeMapper = envelopeMapper;
        this.diagnosticAdminProperties = diagnosticAdminProperties;
    }

    @GetMapping("/effective-view")
    public EibResponse<EffectiveHabitatView> effectiveView(@PathVariable String habitatId, HttpServletRequest request) {
        return effectiveViewService.getEffectiveView(habitatId, ctx(request));
    }

    @GetMapping("/devices")
    public EibResponse<List<EffectiveDeviceView>> devices(@PathVariable String habitatId, HttpServletRequest request) {
        EibResponse<EffectiveHabitatView> response = effectiveView(habitatId, request);
        return new EibResponse<>(response.status(), response.payload() == null ? List.of() : response.payload().devices(),
                response.warnings(), response.error(), response.canonicalTrace());
    }

    @GetMapping("/devices/{effectiveDeviceRef}")
    public EibResponse<EffectiveDeviceView> device(
            @PathVariable String habitatId,
            @PathVariable String effectiveDeviceRef,
            HttpServletRequest request
    ) {
        EibResponse<List<EffectiveDeviceView>> devices = devices(habitatId, request);
        if (!"OK".equals(devices.status())) {
            return new EibResponse<>(devices.status(), null,
                    devices.warnings(), devices.error(), devices.canonicalTrace());
        }
        return devices.payload().stream()
                .filter(device -> device.effectiveDeviceRef().equals(effectiveDeviceRef))
                .findFirst()
                .map(device -> new EibResponse<>("OK", device, devices.warnings(), null, null))
                .orElseGet(() -> new EibResponse<>("NOT_FOUND", null, List.of(),
                        new EibError("NOT_FOUND", "device not visible", "eib.device"), null));
    }

    @GetMapping("/endpoints")
    public EibResponse<List<EffectiveEndpointView>> endpoints(@PathVariable String habitatId, HttpServletRequest request) {
        EibResponse<EffectiveHabitatView> response = effectiveView(habitatId, request);
        return new EibResponse<>(response.status(), response.payload() == null ? List.of() : response.payload().endpoints(),
                response.warnings(), response.error(), response.canonicalTrace());
    }

    @GetMapping("/endpoints/{effectiveEndpointRef}")
    public EibResponse<EffectiveEndpointView> endpoint(
            @PathVariable String habitatId,
            @PathVariable String effectiveEndpointRef,
            HttpServletRequest request
    ) {
        EibResponse<List<EffectiveEndpointView>> endpoints = endpoints(habitatId, request);
        if (!"OK".equals(endpoints.status())) {
            return new EibResponse<>(endpoints.status(), null,
                    endpoints.warnings(), endpoints.error(), endpoints.canonicalTrace());
        }
        return endpoints.payload().stream()
                .filter(endpoint -> endpoint.effectiveEndpointRef().equals(effectiveEndpointRef))
                .findFirst()
                .map(endpoint -> new EibResponse<>("OK", endpoint, endpoints.warnings(), null, null))
                .orElseGet(() -> new EibResponse<>("NOT_FOUND", null, List.of(),
                        new EibError("NOT_FOUND", "endpoint not visible", "eib.endpoint"), null));
    }

    @GetMapping("/diagnostics")
    public EibResponse<EffectiveDiagnosticsView> diagnostics(@PathVariable String habitatId) {
        ScEnvelope<NorthboundDiagnosticsViewDto> envelope;
        try {
            envelope = client.getDiagnostics(habitatId);
        } catch (EibUpstreamUnavailableException e) {
            return new EibResponse<>("UPSTREAM_UNAVAILABLE", null, List.of(),
                    new EibError("UPSTREAM_UNAVAILABLE", e.getMessage(), "eib.northbound"), null);
        }
        if (!envelopeMapper.isSuccess(envelope) || envelope.payload() == null) {
            return new EibResponse<>(envelopeMapper.extractScStatus(envelope), null,
                    envelopeMapper.mapWarnings(envelope.warnings()), envelopeMapper.mapError(envelope.error()), null);
        }
        NorthboundDiagnosticsViewDto diag = envelope.payload();
        EffectiveDiagnosticsView view = new EffectiveDiagnosticsView(
                "eib.habitat." + habitatId,
                diag.topologyVersion(),
                diag.migrationReadiness() == null ? "UNKNOWN" : diag.migrationReadiness().status(),
                diag.migrationReadiness() == null ? null : diag.migrationReadiness().source(),
                diag.temporalEngineStatus() == null ? "UNKNOWN" : diag.temporalEngineStatus().engineStatus(),
                diag.readAt(),
                envelopeMapper.mapWarnings(diag.warnings())
        );
        return new EibResponse<>("OK", view, envelopeMapper.mapWarnings(envelope.warnings()), null, null);
    }

    private EibRequestContext ctx(HttpServletRequest request) {
        return EibRequestContext.fromHeaders(request, diagnosticAdminProperties.enabled());
    }
}
