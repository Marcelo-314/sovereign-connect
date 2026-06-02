# Codex Prompt — PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001 v0.1.0

You are working in the `sovereign-connect` repository on the current branch for `MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001`.

## Objective

Apply a micro quality patch that introduces a production `NatsConnectionFactory` for SC-B NATS connection creation.

This patch improves ownership and future wiring quality after `MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001` has validated reconnect/resubscribe, drain and incompatible-stream conflict behavior.

Do not expand scope.

## Scope

IN:

```text
- Add NatsConnectionFactory under bus.runtime.nats.
- Centralize Options construction and Nats.connect(...).
- Preserve reconnect configuration: maxReconnects=-1 and reconnectWait compatible with current tests.
- Allow optional ConnectionListener wiring at connection creation time.
- Refactor reconnect tests/helpers to use NatsConnectionFactory instead of local Options.Builder code.
- Add focused tests for the factory.
- Add architecture guard if useful.
```

OUT:

```text
- Adapter Manifest runtime.
- Manifest-over-NATS transport.
- Route assignment runtime.
- Command/response handler runtime.
- Provider execution.
- Command admission runtime.
- Capability registry runtime.
- SC-C direct NATS publishing.
- Flyway migrations.
- Subject grammar changes.
- JetStream stream definition changes.
```

## Required production class

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactory.java
```

Recommended implementation:

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

If the codebase style prefers static factory methods, keep equivalent behavior and tests. Do not swallow `IOException` or `InterruptedException`.

## NatsScBusPort interaction

Current `NatsScBusPort` already registers a `ConnectionListener` on the injected connection and maintains reconnect/resubscription counters.

Acceptable patch path:

1. Add `NatsConnectionFactory`.
2. Refactor `NatsScBusPortReconnectTest.buildReconnectAwareConnection(...)` to use the factory.
3. Keep `NatsScBusPort` behavior intact unless you can cleanly move listener wiring without breaking tests.
4. If you move listener wiring out of `NatsScBusPort`, ensure there is no double listener registration and all reconnect tests still pass.

Do not weaken reconnect tests.

## Tests

Add:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsConnectionFactoryTest.java
```

Minimum test 1:

```java
@Test
void createsConnectionToLocalNatsServer() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"));
         Connection connection = new NatsConnectionFactory()
                 .create(server.connection().getConnectedUrl())) {
        assertThat(connection.getStatus())
                .isIn(Connection.Status.CONNECTED, Connection.Status.RECONNECTED);
    }
}
```

Minimum test 2:

```java
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
```

If `forceReconnect()` is nondeterministic in the local harness, do not mark the test green by weakening it. Use the existing reconnect test pattern or report a clear limitation in the implementation report.

Update existing reconnect tests:

```java
private Connection buildReconnectAwareConnection(String natsUri) throws Exception {
    return new NatsConnectionFactory().create(natsUri);
}
```

or use a parameterized factory if your final constructor signature differs.

## Architecture test

Add to `ScBusNatsArchitectureTest` if not already implied by package-wide checks:

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

## Hard stops

Stop and report instead of improvising if any of these occurs:

```text
STOP-PATCH-001 — You need to modify SC-C, manifest runtime, route assignment, command admission,
                 provider execution, or Flyway migrations.
STOP-PATCH-002 — Existing reconnect/drain/JetStream debt-closure tests need to be deleted or weakened.
STOP-PATCH-003 — NatsSubjectBuilder is instantiated with constructor args.
                 Correct shape remains new NatsSubjectBuilder().
STOP-PATCH-004 — Reconnect tests publish partial JSON into NatsScBusPort handlers.
                 Handlers require full ScJsonWireEnvelope.
STOP-PATCH-005 — NatsConnectionFactory imports core.**, adapter.**, integration.**, manifest, or dispatch persistence.
STOP-PATCH-006 — IOException/InterruptedException from Nats.connect(...) are swallowed.
```

## Required verification

Run:

```bash
mvn test
```

Expected:

```text
sovereign-connect: >= 483 tests, 0 failures, 0 errors, 0 skipped
EIB: unchanged; if included by repo scripts, must remain 56 / 0 / 0 / 0
```

## Implementation report

Update the implementation report for the current MIR branch with:

```text
PATCH-SOV-SC-B-NATS-CONNECTION-FACTORY-001
- files added/modified;
- final test count;
- whether NatsScBusPort listener ownership remained internal or was moved to factory wiring;
- confirmation no scope expansion;
- confirmation reconnect/drain/debt-closure tests remain green.
```

## Commit

Use:

```text
refactor(sc-b): centralize nats connection creation
```
