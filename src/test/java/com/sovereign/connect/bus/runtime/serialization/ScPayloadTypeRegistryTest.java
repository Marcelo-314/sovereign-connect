package com.sovereign.connect.bus.runtime.serialization;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScPayloadTypeRegistryTest {
    @Test
    void acceptsSeedPayloadTypeConstants() {
        assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax(ScPayloadTypeRegistry.COMMAND_STUB_V1)).isTrue();
        assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax(ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1)).isTrue();
        assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax(ScPayloadTypeRegistry.RESPONSE_STUB_V1)).isTrue();
        assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax(ScPayloadTypeRegistry.LIFECYCLE_ADAPTER_ANNOUNCE_V1)).isTrue();
    }

    @Test
    void acceptsUnknownButSyntacticallyValidPayloadType() {
        assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax("sc.command.future-device.v2")).isTrue();
    }

    @Test
    void rejectsJavaClassNamePayloadType() {
        assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax("com.sovereign.connect.SomeCommand")).isFalse();
        assertThatThrownBy(() -> ScPayloadTypeRegistry.requireValidPayloadTypeSyntax("org.example.SomeEvent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedPayloadType() {
        for (String payloadType : new String[]{
                "sc.command.future.version",
                "sc.command..v1",
                "sc.command.future.v",
                "sc.command.future.v1.extra",
                "sc.Command.Future.v1",
                "sc.command.future.v0"
        }) {
            assertThat(ScPayloadTypeRegistry.hasValidPayloadTypeSyntax(payloadType)).isFalse();
        }
    }
}
