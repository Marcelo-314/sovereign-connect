# Codex prompt — MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

```text
Document ID:  codex-prompt-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
Version:      v0.2.1-candidate
Status:       Ready for Codex after local review
MIR:          MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.0-candidate
Context:      context-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.1-candidate
```

Read first, in order:

```text
MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001-v0.2.0-candidate.md
CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001-v0.2.0-merged.md
context-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001-v0.2.1-candidate.md
acceptance-map-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001-v0.2.1-candidate.md
```

---

## Mission

Close exactly:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior
DEBT-B-NATS-005 — drain behavior
DEBT-B-NATS-009 — incompatible stream conflict behavior
```

Do not implement Adapter Manifest runtime, route assignment runtime, command/response handler subscriptions, provider execution, SC-C NATS publishing, or Flyway migrations.

---

## Step 0 — Baseline

```bash
git checkout develop
git pull origin develop
git checkout -b feat/sc-b-mir-031-nats-jetstream-debt-closure
mvn -q test
```

Expected baseline: 469 tests, 0 failures, 0 errors, 0 skipped. If different, stop and report.

---

## Step 1 — Close DEBT-B-NATS-009: harden ensureStream error classification

Modify:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsJetStreamStreamApplicator.java
```

Replace the current catch block in `ensureStream()`.

Current debt-bearing behavior:

```java
} catch (JetStreamApiException alreadyExistsOrConflict) {
    try {
        return management.updateStream(config);
    } catch (IOException | JetStreamApiException updateFailed) {
        throw new IllegalStateException(
                "JetStream stream is incompatible or cannot be updated: "
                + definition.name(), updateFailed);
    }
}
```

Replacement:

```java
} catch (JetStreamApiException apiEx) {
    if (apiEx.getApiErrorCode() != 10058) {
        throw new IllegalStateException(
            "JetStream stream application failed with unexpected API error: "
                + definition.name(),
            apiEx
        );
    }
    try {
        StreamInfo existing = management.getStreamInfo(definition.name());
        if (existing.getConfiguration().getStorageType() != config.getStorageType()) {
            throw new IllegalStateException(
                "JetStream stream is incompatible or cannot be updated: "
                    + definition.name()
                    + " — existing storageType: "
                    + existing.getConfiguration().getStorageType()
                    + ", canonical: " + config.getStorageType()
            );
        }
        return management.updateStream(config);
    } catch (IllegalStateException ise) {
        throw ise;
    } catch (IOException | JetStreamApiException updateFailed) {
        throw new IllegalStateException(
            "JetStream stream is incompatible or cannot be updated: "
                + definition.name(),
            updateFailed
        );
    }
}
```

Add to existing `NatsJetStreamStreamApplicatorTest`:

```java
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
                .filter(d -> d.name().equals(NatsStreamConfiguration.SCB_LIFECYCLE_V1))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() ->
            new NatsJetStreamStreamApplicator(jsm).ensureStream(lifecycleDef))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(NatsStreamConfiguration.SCB_LIFECYCLE_V1);
    }
}
```

Do not use subject-pattern changes as conflict source.

---

## Step 2 — Close DEBT-B-NATS-005: drain / close

Modify:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java
```

### 2.1 Class declaration

```java
public final class NatsScBusPort implements ScBusPort, AutoCloseable {
```

### 2.2 Add fields

```java
private final AtomicBoolean closed = new AtomicBoolean(false);
private final AtomicInteger reconnectEvents = new AtomicInteger(0);
private final AtomicInteger resubscribedEvents = new AtomicInteger(0);
```

Required imports:

```java
import io.nats.client.ConnectionListener;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
```

Do not import `NatsScBusPort.EventHandlerRegistration`; no such external import is needed.

### 2.3 Add listener wiring in constructor

After existing field assignments:

```java
connection.addConnectionListener(new ConnectionListener() {
    @Override
    public void connectionEvent(Connection conn, Events type) {
        switch (type) {
            case RECONNECTED  -> reconnectEvents.incrementAndGet();
            case RESUBSCRIBED -> resubscribedEvents.incrementAndGet();
            default           -> { }
        }
    }
});
```

### 2.4 Add `requireOpen()`

```java
private void requireOpen() {
    if (closed.get()) {
        throw new IllegalStateException("NatsScBusPort is closed");
    }
}
```

Call `requireOpen()` at the start of the `try` block in `publishCommand`, `publishEvent`, and `publishResponse`. The existing `catch (RuntimeException ex)` will convert this into a `Failed` outcome.

Call `requireOpen()` at the start of `registerEventHandler`. `registerEventHandler` should throw `IllegalStateException` after close.

Do not implement real command/response handler subscriptions.

### 2.5 Add `close()`

```java
@Override
public void close() {
    if (!closed.compareAndSet(false, true)) {
        return;
    }
    try {
        for (Dispatcher dispatcher : dispatchers) {
            try {
                connection.closeDispatcher(dispatcher);
            } catch (RuntimeException ignored) {
                // best-effort dispatcher cleanup; connection.drain follows
            }
        }
        dispatchers.clear();
        Boolean drained = connection.drain(Duration.ofSeconds(10)).get();
        if (!Boolean.TRUE.equals(drained)) {
            throw new IllegalStateException("NATS connection drain timed out");
        }
    } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while draining NATS connection", ex);
    } catch (Exception ex) {
        throw new IllegalStateException("failed to drain NATS connection", ex);
    }
}
```

Do not call `flush()` inside `close()`. Do not call `flush()` after `connection.drain(...)`.

### 2.6 Add package-visible counters for tests

```java
int getReconnectEventCount() {
    return reconnectEvents.get();
}

int getResubscribedEventCount() {
    return resubscribedEvents.get();
}
```

---

## Step 3 — Tests for drain / close

Create:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPortDrainTest.java
```

Use this helper pattern:

```java
private NatsScBusPort buildPort(Connection conn) {
    return new NatsScBusPort(
        conn,
        new NatsSubjectBuilder(),
        new ScJsonWireCodec(new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)),
        new EnvelopeValidationService()
    );
}
```

Add tests:

```java
@Test
void closeCompletesWithoutThrowing() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        NatsScBusPort port = buildPort(server.connection());
        assertThatCode(port::close).doesNotThrowAnyException();
    }
}

@Test
void publishEventAfterCloseReturnsFailed() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        NatsScBusPort port = buildPort(server.connection());
        port.close();

        DispatchOutcome outcome = port.publishEvent(buildTimerFiredEnvelope("habitat.test.h1"));
        assertThat(outcome).isInstanceOf(DispatchOutcome.Failed.class);
    }
}

@Test
void registerEventHandlerAfterCloseThrowsIllegalStateException() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        NatsScBusPort port = buildPort(server.connection());
        port.close();

        assertThatThrownBy(() -> port.registerEventHandler("sc-c.timer-fired", env -> {}))
            .isInstanceOf(IllegalStateException.class);
    }
}

@Test
void natsScBusPortImplementsAutoCloseable() {
    assertThat(AutoCloseable.class).isAssignableFrom(NatsScBusPort.class);
}
```

Use helpers from the context for `buildTimerFiredEnvelope(...)`.

---

## Step 4 — Close DEBT-B-NATS-004: reconnect / handler survivability

Create:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPortReconnectTest.java
```

Use a reconnect-aware connection. Do not use `server.connection()` as the connection under test.

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

Use `server.connection()` only as a publisher helper connection.

Correct publisher helper — it must send a complete wire envelope, not raw partial JSON:

```java
private void publishTimerFiredWireEvent(Connection publishConn, String habitatId) throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    ScJsonWireCodec codec = new ScJsonWireCodec(mapper);
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
```

Add tests:

```java
@Test
void connectionListenerIsWiredAndReceivesReconnectOrResubscribedEvent() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        Connection conn = buildReconnectAwareConnection(server.connection().getConnectedUrl());
        NatsScBusPort port = buildPort(conn);

        conn.forceReconnect();

        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && port.getReconnectEventCount() + port.getResubscribedEventCount() == 0) {
            Thread.sleep(50);
        }

        assertThat(port.getReconnectEventCount() + port.getResubscribedEventCount())
            .isGreaterThanOrEqualTo(1);

        conn.close();
    }
}

@Test
void timerFiredHandlerReceivesEventBeforeAndAfterForceReconnect() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        Connection conn = buildReconnectAwareConnection(server.connection().getConnectedUrl());
        NatsScBusPort port = buildPort(conn);

        CountDownLatch firstLatch = new CountDownLatch(1);
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger(0);

        port.registerEventHandler("sc-c.timer-fired", env -> {
            if (count.incrementAndGet() == 1) {
                firstLatch.countDown();
            } else {
                secondLatch.countDown();
            }
        });

        publishTimerFiredWireEvent(server.connection(), "habitat.test.h1");
        assertThat(firstLatch.await(5, TimeUnit.SECONDS)).isTrue();

        conn.forceReconnect();
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline
                && port.getReconnectEventCount() + port.getResubscribedEventCount() == 0) {
            Thread.sleep(50);
        }
        assertThat(port.getReconnectEventCount() + port.getResubscribedEventCount())
            .isGreaterThanOrEqualTo(1);

        publishTimerFiredWireEvent(server.connection(), "habitat.test.h1");
        assertThat(secondLatch.await(5, TimeUnit.SECONDS)).isTrue();

        conn.close();
    }
}
```

If these tests cannot produce a reconnect/resubscribed event under `NatsLocalServer`, stop and report. Do not mark DEBT-B-NATS-004 closed without listener and before/after handler evidence.

---

## Step 5 — Architecture test

Modify `ScBusNatsArchitectureTest`:

```java
@Test
void natsScBusPortImplementsAutoCloseable() {
    assertThat(AutoCloseable.class).isAssignableFrom(NatsScBusPort.class);
}
```

Preserve all existing tests unchanged.

---

## Step 6 — Verification

```bash
mvn -q test -Dtest="NatsJetStreamStreamApplicatorTest,NatsScBusPortDrainTest,NatsScBusPortReconnectTest,ScBusNatsArchitectureTest"
mvn -q test
```

Expected after MIR:

```text
>= 480 tests, 0 failures, 0 errors, 0 skipped
```

---

## Hard stops

```text
STOP-1   Dispatcher.drain(Duration) used anywhere.
STOP-2   Subject-pattern change used as incompatible stream conflict fixture.
STOP-3   Connection.addConnectionListener() absent from reconnect path.
STOP-4   DEBT-B-NATS-004 marked closed without listener and before/after handler evidence.
STOP-5   close() calls flush() after connection.drain(...) completes.
STOP-6   drain() treated as semantic success of commands, manifests, or adapter activation.
STOP-7   registerCommandHandler / registerResponseHandler implement real subscriptions.
STOP-8   DispatchStateWritePort or DispatchObservationPort replaced or bypassed.
STOP-9   SC-C imports io.nats.* or bus.runtime.nats.*.
STOP-10  Reconnect test uses only mocked Connection as proof.
STOP-11  Every JetStreamApiException from addStream enters update path without classification.
STOP-12  NatsSubjectBuilder instantiated with codec/validator constructor arguments.
STOP-13  Reconnect event publisher sends raw partial JSON instead of a full ScJsonWireEnvelope.
```
