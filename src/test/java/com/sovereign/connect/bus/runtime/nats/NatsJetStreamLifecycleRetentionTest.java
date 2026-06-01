package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.PublishAck;
import io.nats.client.api.StreamInfo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NatsJetStreamLifecycleRetentionTest {
    private static final NatsLifecycleChannelMapper LIFECYCLE = new NatsLifecycleChannelMapper();
    private static final String HABITAT_ID = "habitat.test.h1";
    private static final byte[] PAYLOAD =
            "{\"payloadType\":\"sc.lifecycle.adapter-announce.v1\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void lifecycleSubjectIsRetainedBySCB_LIFECYCLE_V1Stream() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
            new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            String subject = LIFECYCLE.announceSubject(HABITAT_ID);
            server.connection().publish(subject, PAYLOAD);
            server.connection().flush(Duration.ofSeconds(2));

            StreamInfo info = jsm.getStreamInfo(NatsStreamConfiguration.SCB_LIFECYCLE_V1);

            assertThat(info.getStreamState().getMsgCount()).isGreaterThanOrEqualTo(1L);
        }
    }

    @Test
    void jetStreamPublishAckIsTransportOnlyNotSemanticSuccess() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
            new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            JetStream js = server.connection().jetStream();
            String subject = LIFECYCLE.announceSubject(HABITAT_ID);
            PublishAck ack = js.publish(subject, PAYLOAD);

            assertThat(ack.getStream()).isEqualTo(NatsStreamConfiguration.SCB_LIFECYCLE_V1);
        }
    }

    @Test
    void lifecycleDurableConsumerCanPullRetainedMessage() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
            JetStreamManagement jsm = server.connection().jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
            new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            JetStream js = server.connection().jetStream();
            String subject = LIFECYCLE.announceSubject(HABITAT_ID);
            js.publish(subject, PAYLOAD);

            JetStreamSubscription sub = js.subscribe(
                    JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT,
                    PullSubscribeOptions.bind(
                            NatsStreamConfiguration.SCB_LIFECYCLE_V1,
                            JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER));

            sub.pull(1);
            Message msg = sub.nextMessage(Duration.ofSeconds(5));

            assertThat(msg).isNotNull();
            assertThat(msg.getSubject()).isEqualTo(subject);
            msg.ack();
        }
    }

    @Test
    void lifecycleMapperProducesConcreteSubjectForConfiguredLifecycleStream() {
        String subject = LIFECYCLE.announceSubject(HABITAT_ID);

        assertThat(subject).startsWith("sc.v1.");
        assertThat(subject).contains(".lifecycle.announce");
        assertThat(subject).doesNotContain("*", ">");
    }
}
