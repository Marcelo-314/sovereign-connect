package com.sovereign.connect.bus.runtime.nats;

import com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec;
import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsSubjectBuilderTest {
    private final NatsSubjectBuilder builder = new NatsSubjectBuilder();

    @Test
    void buildsTimerFiredEventSubjectWithEncodedHabitat() {
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");

        assertThat(builder.buildTimerFiredEventSubject("habitat-1"))
                .isEqualTo("sc.v1." + habitat + ".event.timer.sc-c.timer-fired");
    }

    @Test
    void buildsTimerFiredSubscriptionPatternWithOnlyAllowedWildcard() {
        assertThat(builder.buildTimerFiredEventSubscriptionPattern())
                .isEqualTo("sc.v1.*.event.timer.sc-c.timer-fired");
    }

    @Test
    void buildsCommandSubjectFromExplicitRoutes() {
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");
        String adapter = ScSubjectIdTokenCodec.encodeScid1("adapter.z2m");
        String target = ScSubjectIdTokenCodec.encodeScid1("device.z2m.0x001");

        assertThat(builder.buildCommandSubject("habitat-1", "adapter.z2m", "device", "device.z2m.0x001"))
                .isEqualTo("sc.v1." + habitat + ".command." + adapter + ".device." + target);
    }

    @Test
    void buildsResponseSubjectWithCorrelationHex() {
        UUID correlation = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");
        String adapter = ScSubjectIdTokenCodec.encodeScid1("adapter.z2m");

        assertThat(builder.buildResponseSubject("habitat-1", "adapter.z2m", correlation))
                .isEqualTo("sc.v1." + habitat + ".response." + adapter + "."
                        + ScCorrelationTokenCodec.encode(correlation));
    }

    @Test
    void buildsLifecycleAnnounceSubject() {
        String habitat = ScSubjectIdTokenCodec.encodeScid1("habitat-1");

        assertThat(builder.buildLifecycleAnnounceSubject("habitat-1"))
                .isEqualTo("sc.v1." + habitat + ".lifecycle.announce");
    }

    @Test
    void rejectsUnsafeLiteralTargetKind() {
        assertThatThrownBy(() -> builder.buildCommandSubject("habitat-1", "adapter.z2m", "dev*ice", "device-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankCanonicalIds() {
        assertThatThrownBy(() -> builder.buildTimerFiredEventSubject(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishSubjectsNeverContainWildcards() {
        assertThat(builder.buildTimerFiredEventSubject("habitat-1")).doesNotContain("*", ">");
        assertThat(builder.buildCommandSubject("habitat-1", "adapter.z2m", "device", "device.z2m.0x001"))
                .doesNotContain("*", ">");
    }
}
