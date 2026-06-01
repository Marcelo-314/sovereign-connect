package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.JetStreamManagement;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NatsJetStreamLifecycleConsumerTest {
    @Test
    void createsDurableLifecycleConsumerOnLiveJetStream() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();

            ConsumerInfo info = new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            assertThat(info).isNotNull();
            assertThat(info.getName()).isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER);
        }
    }

    @Test
    void consumerInfoReportsExpectedDurableNameAndFilterSubject() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
            new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            ConsumerInfo info = jsm.getConsumerInfo(
                    NatsStreamConfiguration.SCB_LIFECYCLE_V1,
                    JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER);

            assertThat(info.getConsumerConfiguration().getFilterSubject())
                    .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT);
            assertThat(info.getConsumerConfiguration().getAckPolicy()).isEqualTo(AckPolicy.Explicit);
            assertThat(info.getConsumerConfiguration().getMaxDeliver())
                    .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_MAX_DELIVER);
            assertThat(info.getConsumerConfiguration().getDeliverPolicy()).isEqualTo(DeliverPolicy.All);
        }
    }

    @Test
    void consumerCreationIsIdempotent() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
            var applicator = new NatsJetStreamConsumerApplicator(jsm);
            applicator.ensureLifecycleDurableConsumer();

            assertThatCode(applicator::ensureLifecycleDurableConsumer).doesNotThrowAnyException();
        }
    }

    @Test
    void consumerAckWaitMatchesSeedConstant() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();

            ConsumerInfo info = new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            assertThat(info.getConsumerConfiguration().getAckWait())
                    .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_ACK_WAIT);
        }
    }

    @Test
    void hardeningConfigurationDeclaresLifecycleDurablePolicy() {
        assertThat(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
                .isEqualTo("sc-b-lifecycle-consumer-v1");
        assertThat(JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT)
                .isEqualTo("sc.v1.*.lifecycle.>");
        assertThat(JetStreamHardeningConfiguration.LIFECYCLE_ACK_WAIT)
                .isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(JetStreamHardeningConfiguration.LIFECYCLE_MAX_DELIVER).isEqualTo(3);
    }
}
