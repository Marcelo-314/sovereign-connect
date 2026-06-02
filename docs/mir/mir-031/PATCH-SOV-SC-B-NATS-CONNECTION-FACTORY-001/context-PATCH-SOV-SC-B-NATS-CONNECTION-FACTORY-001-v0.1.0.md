# PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001 — Context

```text
Document ID:  context-PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001
Version:      v0.1.0
Status:       Micro execution context / Ready for Codex
Corpus:       Sovereign Connect
Plane:        SC-B / NATS runtime
Baseline:     post-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 implementation
Expected baseline: 481 tests / 0 failures / 0 errors / 0 skipped
Patch type:   Refactor / quality hardening
```

## 1. Purpose

Introduce a small production `NatsConnectionFactory` to centralize NATS `Connection` creation and reconnect-policy wiring.

This is a micro-patch after `MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001` was technically validated L4. It does not change the public SC-B contract, NATS subject grammar, dispatch semantics, JetStream stream configuration, or lifecycle/manifest runtime scope.

## 2. Current verified surface

Current `NatsScBusPort`:

```java
public final class NatsScBusPort implements ScBusPort, AutoCloseable {
    private final Connection connection;
    private final NatsSubjectBuilder subjectBuilder;
    private final ScJsonWireCodec wireCodec;
    private final EnvelopeValidationService envelopeValidationService;
    private final CopyOnWriteArrayList<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger reconnectEvents = new AtomicInteger(0);
    private final AtomicInteger resubscribedEvents = new AtomicInteger(0);

    public NatsScBusPort(Connection connection, NatsSubjectBuilder subjectBuilder,
                         ScJsonWireCodec wireCodec,
                         EnvelopeValidationService envelopeValidationService) {
        ...
        connection.addConnectionListener(new ConnectionListener() {
            @Override
            public void connectionEvent(Connection conn, Events type) {
                switch (type) {
                    case RECONNECTED -> reconnectEvents.incrementAndGet();
                    case RESUBSCRIBED -> resubscribedEvents.incrementAndGet();
                    default -> { }
                }
            }
        });
    }
}
```

Current reconnect test helper manually creates options:

```java
private Connection buildReconnectAwareConnection(String natsUri) throws Exception {
    Options opts = new Options.Builder()
            .server(natsUri)
            .maxReconnects(-1)
            .reconnectWait(Duration.ofMillis(200))
            .build();
    return Nats.connect(opts);
}
```

Current subject builder shape:

```java
new NatsSubjectBuilder()
```

Do not instantiate codec/validator utilities in `NatsSubjectBuilder` constructor.

## 3. Design decision for this micro-patch

Introduce:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactory.java
```

Recommended implementation surface:

```java
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
```

This factory is intentionally small. It centralizes connection creation and reconnect policy. It does not own publishing, subscriptions, dispatch state, observations, manifest runtime, or semantic success.

## 4. NatsScBusPort listener handling

Two implementation paths are acceptable. Prefer Path A if low-cost.

### Path A — cleaner ownership, preferred

Move the connection-listener callback logic into a package-private method and let `NatsConnectionFactory` wire the listener before connection creation in tests/future wiring.

Suggested refactor:

```java
void onConnectionEvent(ConnectionListener.Events type) {
    switch (type) {
        case RECONNECTED -> reconnectEvents.incrementAndGet();
        case RESUBSCRIBED -> resubscribedEvents.incrementAndGet();
        default -> { }
    }
}
```

Use an `AtomicReference<NatsScBusPort>` in test/future wiring where the listener must exist before the port:

```java
AtomicReference<NatsScBusPort> portRef = new AtomicReference<>();
Connection conn = new NatsConnectionFactory().create(serverUrl, (connection, event) -> {
    NatsScBusPort port = portRef.get();
    if (port != null) {
        port.onConnectionEvent(event);
    }
});
NatsScBusPort port = buildPort(conn);
portRef.set(port);
```

If this path is chosen, avoid double listener registration. Either remove the listener registration from the constructor or add a package-private constructor/factory path that does not register an internal listener.

### Path B — minimal safe patch, acceptable

Keep the current `NatsScBusPort` constructor listener registration, introduce `NatsConnectionFactory`, and refactor reconnect tests/helpers to use the factory for `Options`/`Nats.connect(...)` construction.

This is acceptable because it centralizes connection creation without changing port behavior. The implementation report must state that listener ownership remains in `NatsScBusPort` for now, while `NatsConnectionFactory` is the future production entry point for connection creation.

## 5. Required tests

Add:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactoryTest.java
```

Minimum tests:

```java
@Test
void createsConnectionToLocalNatsServer() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
         Connection connection = new NatsConnectionFactory()
                 .create(server.connection().getConnectedUrl())) {
        assertThat(connection.getStatus()).isIn(
            Connection.Status.CONNECTED,
            Connection.Status.RECONNECTED
        );
    }
}
```

If Path A or a listener-aware test is feasible:

```java
@Test
void wiresConnectionListenerAtCreationTime() throws Exception {
    AtomicInteger observed = new AtomicInteger(0);
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
         Connection connection = new NatsConnectionFactory(Duration-or-default-as-implemented)
                 .create(server.connection().getConnectedUrl(), (conn, event) -> {
                     if (event == ConnectionListener.Events.RECONNECTED
                         || event == ConnectionListener.Events.RESUBSCRIBED) {
                         observed.incrementAndGet();
                     }
                 })) {
        connection.forceReconnect();
        // wait up to 5s for observed > 0
        assertThat(observed.get()).isGreaterThanOrEqualTo(1);
    }
}
```

Adapt constructor call to the final `NatsConnectionFactory` signature.

Update existing reconnect tests to use `NatsConnectionFactory` instead of manually building `Options`.

## 6. Architecture tests

Extend `ScBusNatsArchitectureTest` with a factory boundary assertion if not already covered by package-wide checks:

```java
@Test
void natsConnectionFactoryDoesNotImportCoreAdapterOrIntegration() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactory.java"));
    assertThat(source).doesNotContain("import com.sovereign.connect.core.");
    assertThat(source).doesNotContain("import com.sovereign.connect.adapter.");
    assertThat(source).doesNotContain("import com.sovereign.connect.integration.");
}
```

The existing package-wide test may already cover this. Still add a targeted assertion if it is cheap.

## 7. Hard stops

```text
STOP-PATCH-001  Patch introduces manifest runtime, route assignment, command/response subscriptions,
                provider execution, command admission runtime, SC-C imports of NATS, or new Flyway migration.

STOP-PATCH-002  NatsSubjectBuilder is instantiated with codec/validator constructor args.
                The correct shape is new NatsSubjectBuilder().

STOP-PATCH-003  Reconnect event tests send raw partial JSON to NatsScBusPort handlers.
                Any event entering NatsScBusPort must be a complete ScJsonWireEnvelope.

STOP-PATCH-004  Existing reconnect/drain/JetStream debt-closure tests are weakened or deleted.

STOP-PATCH-005  NatsConnectionFactory imports core.**, adapter.**, integration.**, dispatch persistence,
                manifest, lifecycle runtime, or provider classes.

STOP-PATCH-006  Factory swallows IOException / InterruptedException from Nats.connect(...).
                Exceptions must remain observable.
```

## 8. Expected regression

Baseline after MIR-031:

```text
sovereign-connect: 481 tests, 0 failures, 0 errors, 0 skipped
EIB: 56 tests, 0 failures, 0 errors, 0 skipped
```

Expected after patch:

```text
sovereign-connect: >= 483 tests, 0 failures, 0 errors, 0 skipped
EIB: 56 tests, 0 failures, 0 errors, 0 skipped
```

## 9. Commit

```text
refactor(sc-b): centralize nats connection creation
```
