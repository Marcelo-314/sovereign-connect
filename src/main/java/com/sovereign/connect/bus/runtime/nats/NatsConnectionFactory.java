package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Nats;
import io.nats.client.Options;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

public final class NatsConnectionFactory {
    private static final int DEFAULT_MAX_RECONNECTS = -1;
    private static final Duration DEFAULT_RECONNECT_WAIT = Duration.ofMillis(200);

    private final int maxReconnects;
    private final Duration reconnectWait;

    public NatsConnectionFactory() {
        this(DEFAULT_MAX_RECONNECTS, DEFAULT_RECONNECT_WAIT);
    }

    public NatsConnectionFactory(int maxReconnects, Duration reconnectWait) {
        this.maxReconnects = maxReconnects;
        this.reconnectWait = Objects.requireNonNull(reconnectWait, "reconnectWait is required");
    }

    public Connection create(String serverUrl) throws IOException, InterruptedException {
        return create(serverUrl, null);
    }

    public Connection create(String serverUrl, ConnectionListener listener)
            throws IOException, InterruptedException {
        Objects.requireNonNull(serverUrl, "serverUrl is required");
        Options.Builder builder = new Options.Builder()
                .server(serverUrl)
                .maxReconnects(maxReconnects)
                .reconnectWait(reconnectWait);
        if (listener != null) {
            builder.connectionListener(listener);
        }
        return Nats.connect(builder.build());
    }
}
