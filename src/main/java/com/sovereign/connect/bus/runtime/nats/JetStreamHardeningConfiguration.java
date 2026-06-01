package com.sovereign.connect.bus.runtime.nats;

import java.time.Duration;

public final class JetStreamHardeningConfiguration {
    public static final String LIFECYCLE_DURABLE_CONSUMER = "sc-b-lifecycle-consumer-v1";
    public static final String LIFECYCLE_FILTER_SUBJECT = "sc.v1.*.lifecycle.>";
    public static final Duration LIFECYCLE_ACK_WAIT = Duration.ofSeconds(30);
    public static final int LIFECYCLE_MAX_DELIVER = 3;

    private JetStreamHardeningConfiguration() {}
}
