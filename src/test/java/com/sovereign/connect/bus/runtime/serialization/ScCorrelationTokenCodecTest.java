package com.sovereign.connect.bus.runtime.serialization;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScCorrelationTokenCodecTest {
    @Test
    void correlationIdEncodingRemovesHyphens() {
        assertThat(ScCorrelationTokenCodec.encode(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
                .isEqualTo("550e8400e29b41d4a716446655440000")
                .doesNotContain("-");
    }

    @Test
    void correlationIdEncodingIsLowercaseHex() {
        assertThat(ScCorrelationTokenCodec.encode(UUID.fromString("A50E8400-E29B-41D4-A716-446655440000")))
                .matches("[0-9a-f]{32}");
    }

    @Test
    void correlationIdTokenRoundtripsToOriginalUuid() {
        UUID id = UUID.randomUUID();

        assertThat(ScCorrelationTokenCodec.decode(ScCorrelationTokenCodec.encode(id))).isEqualTo(id);
    }

    @Test
    void invalidCorrelationTokenLengthIsRejected() {
        assertThatThrownBy(() -> ScCorrelationTokenCodec.decode("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonHexCorrelationTokenCharsAreRejected() {
        assertThatThrownBy(() -> ScCorrelationTokenCodec.decode("550e8400e29b41d4a71644665544000g"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScCorrelationTokenCodec.decode("550E8400E29B41D4A716446655440000"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
