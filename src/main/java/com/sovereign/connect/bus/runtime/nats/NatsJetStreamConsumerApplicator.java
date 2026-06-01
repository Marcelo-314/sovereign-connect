package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;

import java.io.IOException;
import java.util.Objects;

public final class NatsJetStreamConsumerApplicator {
    private final JetStreamManagement management;

    public NatsJetStreamConsumerApplicator(JetStreamManagement management) {
        this.management = Objects.requireNonNull(management, "management is required");
    }

    public ConsumerInfo ensureLifecycleDurableConsumer() throws IOException, JetStreamApiException {
        ConsumerConfiguration config = ConsumerConfiguration.builder()
                .durable(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
                .name(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
                .filterSubject(JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT)
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(JetStreamHardeningConfiguration.LIFECYCLE_ACK_WAIT)
                .maxDeliver(JetStreamHardeningConfiguration.LIFECYCLE_MAX_DELIVER)
                .deliverPolicy(DeliverPolicy.All)
                .build();
        return management.addOrUpdateConsumer(NatsStreamConfiguration.SCB_LIFECYCLE_V1, config);
    }
}
