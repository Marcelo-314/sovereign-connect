package com.sovereign.eib.api;

import com.sovereign.eib.config.EibDiagnosticAdminProperties;
import com.sovereign.eib.domain.EffectiveTemporalActView;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.domain.EibResponse;
import com.sovereign.eib.domain.EibTemporalCancellationAdmissionRequest;
import com.sovereign.eib.domain.EibTemporalSignalAdmissionRequest;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import com.sovereign.eib.service.EibTemporalActProjectionService;
import com.sovereign.eib.service.EibTemporalAdmissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/eib/v1/habitats/{habitatId}/temporal-acts")
public class EibTemporalActController {

    private final EibTemporalActProjectionService projectionService;
    private final EibTemporalAdmissionService admissionService;
    private final EibDiagnosticAdminProperties diagnosticAdminProperties;

    public EibTemporalActController(
            EibTemporalActProjectionService projectionService,
            EibTemporalAdmissionService admissionService,
            EibDiagnosticAdminProperties diagnosticAdminProperties
    ) {
        this.projectionService = projectionService;
        this.admissionService = admissionService;
        this.diagnosticAdminProperties = diagnosticAdminProperties;
    }

    @GetMapping
    public EibResponse<List<EffectiveTemporalActView>> list(@PathVariable String habitatId, HttpServletRequest request) {
        return projectionService.listEffectiveTemporalActs(habitatId, ctx(request));
    }

    @GetMapping("/{effectiveTemporalActRef}")
    public EibResponse<EffectiveTemporalActView> get(
            @PathVariable String habitatId,
            @PathVariable String effectiveTemporalActRef,
            HttpServletRequest request
    ) {
        return projectionService.getEffectiveTemporalAct(habitatId, effectiveTemporalActRef, ctx(request));
    }

    @PostMapping("/signal")
    public ResponseEntity<EibResponse<InteractionAdmissionDecision>> signal(
            @PathVariable String habitatId,
            @RequestBody EibTemporalSignalAdmissionRequest body,
            HttpServletRequest request
    ) {
        return admissionResponse(
                admissionService.admitTemporalSignalRequest(habitatId, body, ctx(request)));
    }

    @PostMapping("/{effectiveTemporalActRef}/cancel")
    public ResponseEntity<EibResponse<InteractionAdmissionDecision>> cancel(
            @PathVariable String habitatId,
            @PathVariable String effectiveTemporalActRef,
            @RequestBody EibTemporalCancellationAdmissionRequest body,
            HttpServletRequest request
    ) {
        return admissionResponse(
                admissionService.admitTemporalCancellation(
                        habitatId, effectiveTemporalActRef, body, ctx(request)));
    }

    private ResponseEntity<EibResponse<InteractionAdmissionDecision>> admissionResponse(
            InteractionAdmissionDecision decision) {
        if ("FAILED_UPSTREAM_UNAVAILABLE".equals(decision.status())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new EibResponse<>("UPSTREAM_UNAVAILABLE", decision,
                            decision.warnings(), decision.error(), null));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new EibResponse<>("ACCEPTED", decision,
                        decision.warnings(), decision.error(), null));
    }

    private EibRequestContext ctx(HttpServletRequest request) {
        return EibRequestContext.fromHeaders(request, diagnosticAdminProperties.enabled());
    }
}
