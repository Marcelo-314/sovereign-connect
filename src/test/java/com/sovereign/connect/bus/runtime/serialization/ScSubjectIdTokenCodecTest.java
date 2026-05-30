package com.sovereign.connect.bus.runtime.serialization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScSubjectIdTokenCodecTest {
    @Test
    void scid1EncodingPrefixesToken() {
        assertThat(ScSubjectIdTokenCodec.encodeScid1("device.light.kitchen")).startsWith("scid1_");
    }

    @Test
    void scid1TokenHasNoProblematicSubjectChars() {
        String token = ScSubjectIdTokenCodec.encodeScid1("adapter/zigbee=device.light.kitchen");

        assertThat(token).doesNotContain(".", "+", "/", "=");
    }

    @Test
    void scid1RoundtripPreservesOriginalCanonicalId() {
        String canonicalId = "endpoint.tuya.light-kitchen.switch";

        assertThat(ScSubjectIdTokenCodec.decodeScid1(ScSubjectIdTokenCodec.encodeScid1(canonicalId)))
                .isEqualTo(canonicalId);
    }

    @Test
    void scid1EncodeDecodeEncodeIsIdempotent() {
        String token = ScSubjectIdTokenCodec.encodeScid1("device.light.kitchen");

        assertThat(ScSubjectIdTokenCodec.encodeScid1(ScSubjectIdTokenCodec.decodeScid1(token))).isEqualTo(token);
    }

    @Test
    void malformedScid1TokensAreRejected() {
        assertThatThrownBy(() -> ScSubjectIdTokenCodec.decodeScid1("device.light"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScSubjectIdTokenCodec.decodeScid1("scid1_"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScSubjectIdTokenCodec.decodeScid1("scid1_@@@@"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
