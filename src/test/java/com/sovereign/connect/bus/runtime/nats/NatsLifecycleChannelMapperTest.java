package com.sovereign.connect.bus.runtime.nats;

import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NatsLifecycleChannelMapperTest {
    private final NatsLifecycleChannelMapper mapper = new NatsLifecycleChannelMapper();

    @Test
    void mapsAnnounceSubjectOnly() {
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");

        assertThat(mapper.announceSubject("habitat-1"))
                .isEqualTo("sc.v1." + habitat + ".lifecycle.announce");
    }

    @Test
    void mapsChallengeSubjectWithoutAdmissionRuntime() {
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");
        String adapter = ScSubjectIdTokenCodec.encodeScid1("adapter.z2m");

        assertThat(mapper.challengeSubject("habitat-1", "adapter.z2m"))
                .isEqualTo("sc.v1." + habitat + ".lifecycle.challenge." + adapter);
    }

    @Test
    void mapsRouteAssignmentSubjectWithoutActiveAuthority() {
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");
        String adapter = ScSubjectIdTokenCodec.encodeScid1("adapter.z2m");

        assertThat(mapper.routeAssignmentSubject("habitat-1", "adapter.z2m"))
                .isEqualTo("sc.v1." + habitat + ".lifecycle.route-assignment." + adapter);
    }
}
