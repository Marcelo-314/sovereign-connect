package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NatsConnectionFactoryTest {
    @Test
    void createsConnectionToLocalNatsServer() throws Exception {
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
            Connection connection = new NatsConnectionFactory()
                     .create(server.connection().getConnectedUrl())) {
            assertThat(connection.getStatus())
                    .isEqualTo(Connection.Status.CONNECTED);
        }
    }

    @Test
    void canWireConnectionListenerAtCreationTime() throws Exception {
        AtomicInteger observed = new AtomicInteger(0);
        try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
             Connection connection = new NatsConnectionFactory()
                     .create(server.connection().getConnectedUrl(), (conn, event) -> {
                         if (event == ConnectionListener.Events.RECONNECTED
                                 || event == ConnectionListener.Events.RESUBSCRIBED) {
                             observed.incrementAndGet();
                         }
                     })) {
            connection.forceReconnect();
            long deadline = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < deadline && observed.get() == 0) {
                Thread.sleep(50);
            }
            assertThat(observed.get()).isGreaterThanOrEqualTo(1);
        }
    }
}
