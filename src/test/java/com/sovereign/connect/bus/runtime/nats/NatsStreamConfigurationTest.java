package com.sovereign.connect.bus.runtime.nats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsStreamConfigurationTest {
    @Test
    void definesScbStreamNames() {
        assertThat(NatsStreamConfiguration.SCB_COMMANDS_V1).isEqualTo("SCB_COMMANDS_V1");
        assertThat(NatsStreamConfiguration.SCB_EVENTS_V1).isEqualTo("SCB_EVENTS_V1");
        assertThat(NatsStreamConfiguration.SCB_RESPONSES_V1).isEqualTo("SCB_RESPONSES_V1");
        assertThat(NatsStreamConfiguration.SCB_LIFECYCLE_V1).isEqualTo("SCB_LIFECYCLE_V1");
        assertThat(NatsStreamConfiguration.SCB_DLQ_V1).isEqualTo("SCB_DLQ_V1");
    }

    @Test
    void definesSubjectPatternsForEveryStream() {
        assertThat(NatsStreamConfiguration.streamDefinitions())
                .extracting(NatsStreamConfiguration.StreamDefinition::subjects)
                .allSatisfy(subjects -> assertThat(subjects).isNotEmpty());
    }

    @Test
    void commandEventAndResponsePatternsAreSeparated() {
        assertThat(NatsStreamConfiguration.streamDefinitions())
                .anySatisfy(stream -> {
                    assertThat(stream.name()).isEqualTo("SCB_COMMANDS_V1");
                    assertThat(stream.subjects()).containsExactly("sc.v1.*.command.>");
                })
                .anySatisfy(stream -> {
                    assertThat(stream.name()).isEqualTo("SCB_EVENTS_V1");
                    assertThat(stream.subjects()).containsExactly("sc.v1.*.event.>");
                })
                .anySatisfy(stream -> {
                    assertThat(stream.name()).isEqualTo("SCB_RESPONSES_V1");
                    assertThat(stream.subjects()).containsExactly("sc.v1.*.response.>");
                });
    }

    @Test
    void streamDefinitionsAreImmutable() {
        var definitions = NatsStreamConfiguration.streamDefinitions();

        assertThatThrownBy(() -> definitions.add(new NatsStreamConfiguration.StreamDefinition("X", java.util.List.of("x"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidStreamDefinitions() {
        assertThatThrownBy(() -> new NatsStreamConfiguration.StreamDefinition(" ", java.util.List.of("sc.v1.>")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NatsStreamConfiguration.StreamDefinition("SCB_X", java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
