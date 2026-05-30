package com.sovereign.connect.bus.runtime.serialization;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScSubjectTokenValidatorTest {
    @Test
    void rejectsProblematicNatsSubjectCharacters() {
        for (String token : new String[]{"a.b", "a+b", "a/b", "a=b", "a*b", "a>b"}) {
            assertThatThrownBy(() -> ScSubjectTokenValidator.requireSafeToken(token))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsBlankSubjectTokens() {
        assertThatThrownBy(() -> ScSubjectTokenValidator.requireSafeToken(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsScid1AndCorrelationTokens() {
        assertThatCode(() -> ScSubjectTokenValidator.requireSafeToken(ScSubjectIdTokenCodec.encodeScid1("device.light")))
                .doesNotThrowAnyException();
        assertThatCode(() -> ScSubjectTokenValidator.requireSafeToken(ScCorrelationTokenCodec.encode(UUID.randomUUID())))
                .doesNotThrowAnyException();
    }
}
