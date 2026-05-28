package com.sovereign.eib.api;

import com.sovereign.eib.domain.EibResponse;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class EibHttpResponseMapper {

    private EibHttpResponseMapper() {
    }

    public static ResponseEntity<EibResponse<InteractionAdmissionDecision>>
    admissionResponse(InteractionAdmissionDecision decision) {

        return switch (decision.status()) {
            case "ADMITTED" ->
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(new EibResponse<>("ADMITTED", decision,
                                    decision.warnings(), decision.error(), null));

            case "COMPLETED" ->
                    ResponseEntity.ok(new EibResponse<>("COMPLETED", decision,
                            decision.warnings(), decision.error(), null));

            case "REJECTED_NOT_VISIBLE" ->
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new EibResponse<>("NOT_VISIBLE", decision,
                                    decision.warnings(), decision.error(), null));

            case "REJECTED_INVALID_REQUEST" ->
                    ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new EibResponse<>("INVALID_REQUEST", decision,
                                    decision.warnings(), decision.error(), null));

            case "DEFERRED_SC_B_REQUIRED" ->
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(new EibResponse<>("DEFERRED", decision,
                                    decision.warnings(), decision.error(), null));

            case "DEFERRED_PENDING_NORMALIZATION" ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(new EibResponse<>("PENDING_NORMALIZATION", decision,
                                    decision.warnings(), decision.error(), null));

            case "DEFERRED_UNSUPPORTED_PROFILE" ->
                    ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                            .body(new EibResponse<>("UNSUPPORTED", decision,
                                    decision.warnings(), decision.error(), null));

            case "FAILED_UPSTREAM_UNAVAILABLE" ->
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(new EibResponse<>("UPSTREAM_UNAVAILABLE", decision,
                                    decision.warnings(), decision.error(), null));

            default ->
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new EibResponse<>("FAILED", decision,
                                    decision.warnings(), decision.error(), null));
        };
    }
}
