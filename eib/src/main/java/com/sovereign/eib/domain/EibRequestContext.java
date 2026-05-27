package com.sovereign.eib.domain;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record EibRequestContext(
        String contextRef,
        String actorRef,
        String surfaceRef,
        String locale,
        boolean diagnosticRequested,
        boolean diagnosticAdminAuthorized,
        Instant requestedAt,
        String clientRequestRef
) {

    public static EibRequestContext fromHeaders(HttpServletRequest request, boolean diagnosticAdminEnabled) {
        boolean diagnosticRequested = Boolean.parseBoolean(request.getHeader("X-EIB-Diagnostic"));
        return new EibRequestContext(
                Optional.ofNullable(request.getHeader("X-EIB-Context-Ref")).orElse("ctx." + UUID.randomUUID()),
                Optional.ofNullable(request.getHeader("X-EIB-Actor-Ref")).orElse("actor.anonymous"),
                Optional.ofNullable(request.getHeader("X-EIB-Surface-Ref")).orElse("surface.unknown"),
                Optional.ofNullable(request.getHeader("Accept-Language")).orElse("en"),
                diagnosticRequested,
                diagnosticRequested && diagnosticAdminEnabled,
                Instant.now(),
                Optional.ofNullable(request.getHeader("X-Request-Id")).orElse(UUID.randomUUID().toString())
        );
    }
}
