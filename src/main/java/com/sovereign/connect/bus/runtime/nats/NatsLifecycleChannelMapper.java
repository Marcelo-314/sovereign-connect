package com.sovereign.connect.bus.runtime.nats;

import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec;
import com.sovereign.connect.bus.runtime.serialization.ScSubjectTokenValidator;

public final class NatsLifecycleChannelMapper {
    private static final String PREFIX = "sc.v1";

    public String announceSubject(String habitatId) {
        return PREFIX + "." + encodedRoute(habitatId) + ".lifecycle.announce";
    }

    public String challengeSubject(String habitatId, String adapterId) {
        return PREFIX + "." + encodedRoute(habitatId) + ".lifecycle.challenge." + encodedRoute(adapterId);
    }

    public String routeAssignmentSubject(String habitatId, String adapterId) {
        return PREFIX + "." + encodedRoute(habitatId) + ".lifecycle.route-assignment." + encodedRoute(adapterId);
    }

    private String encodedRoute(String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank()) {
            throw new IllegalArgumentException("canonicalId is required");
        }
        String route = ScSubjectIdTokenCodec.encodeScid1(canonicalId);
        ScSubjectTokenValidator.requireSafeToken(route);
        return route;
    }
}
