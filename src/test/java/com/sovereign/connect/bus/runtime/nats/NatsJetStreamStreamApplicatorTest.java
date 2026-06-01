package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NatsJetStreamStreamApplicatorTest {
    @Test
    void appliesAllConfiguredStreamsToLiveJetStream() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            List<StreamInfo> infos = new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();

            assertThat(infos).hasSize(5);
            assertThat(infos)
                    .extracting(info -> info.getConfiguration().getName())
                    .containsExactlyInAnyOrder(
                            NatsStreamConfiguration.SCB_COMMANDS_V1,
                            NatsStreamConfiguration.SCB_EVENTS_V1,
                            NatsStreamConfiguration.SCB_RESPONSES_V1,
                            NatsStreamConfiguration.SCB_LIFECYCLE_V1,
                            NatsStreamConfiguration.SCB_DLQ_V1);
        }
    }

    @Test
    void streamApplicationIsIdempotent() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            var applicator = new NatsJetStreamStreamApplicator(jsm);
            applicator.ensureAllConfiguredStreams();

            assertThatCode(applicator::ensureAllConfiguredStreams).doesNotThrowAnyException();
        }
    }

    @Test
    void streamInfoContainsExpectedSubjectPatterns() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();

            StreamInfo lifecycle = jsm.getStreamInfo(NatsStreamConfiguration.SCB_LIFECYCLE_V1);

            assertThat(lifecycle.getConfiguration().getSubjects()).containsExactly("sc.v1.*.lifecycle.>");
        }
    }

    @Test
    void configuredStreamNamesRemainUnchanged() {
        assertThat(NatsStreamConfiguration.streamDefinitions())
                .extracting(NatsStreamConfiguration.StreamDefinition::name)
                .containsExactly(
                        NatsStreamConfiguration.SCB_COMMANDS_V1,
                        NatsStreamConfiguration.SCB_EVENTS_V1,
                        NatsStreamConfiguration.SCB_RESPONSES_V1,
                        NatsStreamConfiguration.SCB_LIFECYCLE_V1,
                        NatsStreamConfiguration.SCB_DLQ_V1);
    }

    @Test
    void configuredSubjectPatternsRemainUnchanged() {
        assertThat(NatsStreamConfiguration.streamDefinitions())
                .extracting(NatsStreamConfiguration.StreamDefinition::subjects)
                .containsExactly(
                        List.of("sc.v1.*.command.>"),
                        List.of("sc.v1.*.event.>"),
                        List.of("sc.v1.*.response.>"),
                        List.of("sc.v1.*.lifecycle.>"),
                        List.of("sc.v1.*.dlq.>"));
    }

    @Test
    void incompatibleStorageTypeConflictThrowsIllegalStateException() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            StreamConfiguration incompatible = StreamConfiguration.builder()
                    .name(NatsStreamConfiguration.SCB_LIFECYCLE_V1)
                    .subjects("sc.v1.*.lifecycle.>")
                    .storageType(StorageType.File)
                    .build();
            jsm.addStream(incompatible);

            NatsStreamConfiguration.StreamDefinition lifecycleDef =
                    NatsStreamConfiguration.streamDefinitions().stream()
                            .filter(definition -> definition.name().equals(NatsStreamConfiguration.SCB_LIFECYCLE_V1))
                            .findFirst()
                            .orElseThrow();

            assertThatThrownBy(() -> new NatsJetStreamStreamApplicator(jsm).ensureStream(lifecycleDef))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(NatsStreamConfiguration.SCB_LIFECYCLE_V1);
        }
    }
}
