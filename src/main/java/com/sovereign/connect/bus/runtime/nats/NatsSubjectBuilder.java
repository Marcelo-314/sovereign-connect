package com.sovereign.connect.bus.runtime.nats;

import com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec;
import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec;
import com.sovereign.connect.bus.runtime.serialization.ScSubjectTokenValidator;

import java.util.UUID;

public final class NatsSubjectBuilder {
    private static final String PREFIX = "sc.v1";

    public String buildTimerFiredEventSubject(String habitatId) {
        String habitatRoute = encodedRoute(habitatId);
        return publishSubject(PREFIX + "." + habitatRoute + ".event.timer.sc-c.timer-fired");
    }

    public String buildTimerFiredEventSubscriptionPattern() {
        return PREFIX + ".*.event.timer.sc-c.timer-fired";
    }

    public String buildCommandSubject(String habitatId, String adapterId, String targetKind, String targetId) {
        String habitatRoute = encodedRoute(habitatId);
        String adapterRoute = encodedRoute(adapterId);
        String safeTargetKind = safeLiteral(targetKind, "targetKind");
        String targetRoute = encodedRoute(targetId);
        return publishSubject(PREFIX + "." + habitatRoute + ".command."
                + adapterRoute + "." + safeTargetKind + "." + targetRoute);
    }

    public String buildResponseSubject(String habitatId, String adapterId, UUID rootCorrelationId) {
        String habitatRoute = encodedRoute(habitatId);
        String adapterRoute = encodedRoute(adapterId);
        if (rootCorrelationId == null) {
            throw new IllegalArgumentException("rootCorrelationId is required");
        }
        String correlationToken = ScCorrelationTokenCodec.encode(rootCorrelationId);
        ScSubjectTokenValidator.requireSafeToken(correlationToken);
        return publishSubject(PREFIX + "." + habitatRoute + ".response."
                + adapterRoute + "." + correlationToken);
    }

    public String buildLifecycleAnnounceSubject(String habitatId) {
        String habitatRoute = encodedRoute(habitatId);
        return publishSubject(PREFIX + "." + habitatRoute + ".lifecycle.announce");
    }

    private String encodedRoute(String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank()) {
            throw new IllegalArgumentException("canonicalId is required");
        }
        String route = ScSubjectIdTokenCodec.encodeScid1(canonicalId);
        ScSubjectTokenValidator.requireSafeToken(route);
        return route;
    }

    private String safeLiteral(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        ScSubjectTokenValidator.requireSafeToken(value);
        return value;
    }

    private String publishSubject(String subject) {
        if (subject.contains("*") || subject.contains(">")) {
            throw new IllegalArgumentException("publish subject must not contain wildcards");
        }
        return subject;
    }
}
