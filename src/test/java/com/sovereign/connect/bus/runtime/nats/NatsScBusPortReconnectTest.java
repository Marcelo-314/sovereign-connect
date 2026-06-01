package com.sovereign.connect.bus.runtime.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireEnvelope;
import com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NatsScBusPortReconnectTest {
    @Test
    void connectionListenerCountersStartAtZero() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
             Connection conn = buildReconnectAwareConnection(server.connection().getConnectedUrl())) {
            NatsScBusPort port = buildPort(conn);

            assertThat(port.getReconnectEventCount()).isZero();
            assertThat(port.getResubscribedEventCount()).isZero();

            port.close();
        }
    }

    @Test
    void connectionListenerIsWiredAndReceivesReconnectOrResubscribedEvent() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
             Connection conn = buildReconnectAwareConnection(server.connection().getConnectedUrl())) {
            NatsScBusPort port = buildPort(conn);

            conn.forceReconnect();
            waitForReconnectObservation(port);

            assertThat(port.getReconnectEventCount() + port.getResubscribedEventCount())
                    .isGreaterThanOrEqualTo(1);
            port.close();
        }
    }

    @Test
    void timerFiredHandlerReceivesEventBeforeAndAfterForceReconnect() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
             Connection conn = buildReconnectAwareConnection(server.connection().getConnectedUrl())) {
            NatsScBusPort port = buildPort(conn);
            CountDownLatch firstLatch = new CountDownLatch(1);
            CountDownLatch secondLatch = new CountDownLatch(1);
            AtomicInteger count = new AtomicInteger(0);

            port.registerEventHandler("sc-c.timer-fired", envelope -> {
                if (count.incrementAndGet() == 1) {
                    firstLatch.countDown();
                } else {
                    secondLatch.countDown();
                }
            });

            publishTimerFiredWireEvent(server.connection(), "habitat.test.h1");
            assertThat(firstLatch.await(5, TimeUnit.SECONDS)).isTrue();

            conn.forceReconnect();
            waitForReconnectObservation(port);

            publishTimerFiredWireEvent(server.connection(), "habitat.test.h1");
            assertThat(secondLatch.await(5, TimeUnit.SECONDS)).isTrue();
            port.close();
        }
    }

    @Test
    void lifecycleDurableConsumerCanReplayAfterForceReconnect() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
             Connection conn = buildReconnectAwareConnection(server.connection().getConnectedUrl())) {
            NatsScBusPort port = buildPort(conn);
            JetStreamManagement jsm = conn.jetStreamManagement();
            new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
            new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

            conn.forceReconnect();
            waitForReconnectObservation(port);

            JetStream js = conn.jetStream();
            String subject = new NatsLifecycleChannelMapper().announceSubject("habitat.test.h1");
            js.publish(subject, "{\"payloadType\":\"sc.lifecycle.adapter-announce.v1\"}"
                    .getBytes(StandardCharsets.UTF_8));
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
            port.close();
        }
    }

    private Connection buildReconnectAwareConnection(String natsUri) throws Exception {
        return new NatsConnectionFactory().create(natsUri);
    }

    private NatsScBusPort buildPort(Connection conn) {
        return new NatsScBusPort(
                conn,
                new NatsSubjectBuilder(),
                new ScJsonWireCodec(objectMapper()),
                new EnvelopeValidationService()
        );
    }

    private void publishTimerFiredWireEvent(Connection publishConn, String habitatId) throws Exception {
        ScJsonWireCodec codec = new ScJsonWireCodec(objectMapper());
        NatsSubjectBuilder builder = new NatsSubjectBuilder();
        String subject = builder.buildTimerFiredEventSubject(habitatId);
        ScEventEnvelope<Object> envelope = buildTimerFiredEnvelope(habitatId);
        ScJsonWireEnvelope wire = codec.toWireEnvelope(
                envelope,
                ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1,
                "1.0"
        );
        publishConn.publish(subject, codec.marshal(wire));
        publishConn.flush(Duration.ofSeconds(2));
    }

    private ScEventEnvelope<Object> buildTimerFiredEnvelope(String habitatId) {
        UUID id = UUID.randomUUID();
        return new ScEventEnvelope<>(
                new ScMessageMetadata(id, Instant.now(), id, null, null),
                objectMapper().createObjectNode().put("timer", "fired"),
                new ScRoutingKey(ScBusLane.EVENT, habitatId, "sc-c.timer-fired", habitatId, null, null, null)
        );
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private void waitForReconnectObservation(NatsScBusPort port) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && port.getReconnectEventCount() + port.getResubscribedEventCount() == 0) {
            Thread.sleep(50);
        }
    }
}
