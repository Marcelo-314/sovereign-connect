package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.runtime.NorthboundDiagnosticsView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRecoveryStatusView;
import com.sovereign.connect.core.northbound.runtime.NorthboundTemporalRuntimeStatusView;
import com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnProperty(prefix = "sc.northbound.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/sc/v1/habitats/{habitatId}")
public class ScNorthboundTemporalHttpController {

    private final ScCoreNorthboundFacade facade;

    public ScNorthboundTemporalHttpController(ScCoreNorthboundFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/temporal/runtime-status")
    public ResponseEntity<ScNorthboundResponse<NorthboundTemporalRuntimeStatusView>> getTemporalRuntimeStatus(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getTemporalRuntimeStatus(habitatId));
    }

    @GetMapping("/recovery/status")
    public ResponseEntity<ScNorthboundResponse<NorthboundRecoveryStatusView>> getRecoveryStatus(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getRecoveryStatus(habitatId));
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<ScNorthboundResponse<NorthboundDiagnosticsView>> getNorthboundDiagnostics(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getNorthboundDiagnostics(habitatId));
    }

    @GetMapping("/temporal-acts/{temporalActId}")
    public ResponseEntity<ScNorthboundResponse<NorthboundTemporalActView>> getTemporalAct(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("temporalActId") String temporalActId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getTemporalAct(habitatId, temporalActId));
    }

    @PostMapping("/temporal-acts")
    public ResponseEntity<ScNorthboundResponse<NorthboundTemporalActView>> createSignalTemporalAct(
        @PathVariable("habitatId") String habitatId,
        @RequestBody NorthboundCreateSignalTemporalActRequest request
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.createSignalTemporalAct(habitatId, request));
    }

    @PostMapping("/temporal-acts/{temporalActId}/cancel")
    public ResponseEntity<ScNorthboundResponse<NorthboundTemporalActView>> cancelTemporalAct(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("temporalActId") String temporalActId,
        @RequestBody ScNorthboundCancelTemporalActHttpBody body
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(
            facade.cancelTemporalAct(habitatId,
                new NorthboundCancelTemporalActRequest(
                    temporalActId,
                    body.requestedByRef(),
                    body.idempotencyKey(),
                    body.reason()
                )));
    }

    @GetMapping("/temporal-acts")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundTemporalActView>>> listTemporalActs(
        @PathVariable("habitatId") String habitatId,
        @RequestParam(name = "mode", required = false) String mode,
        @RequestParam(name = "maxResults", required = false) Integer maxResults
    ) {
        NorthboundTemporalActFilter.Mode resolvedMode = null;
        if (mode != null) {
            try {
                resolvedMode = NorthboundTemporalActFilter.Mode.valueOf(mode.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ScNorthboundHttpResponseMapper.toResponseEntity(
                    ScNorthboundResponse.invalidRequest("INVALID_MODE",
                        "unsupported mode value: " + mode));
            }
        }
        return ScNorthboundHttpResponseMapper.toResponseEntity(
            facade.listTemporalActs(habitatId,
                new NorthboundTemporalActFilter(resolvedMode, maxResults)));
    }
}
