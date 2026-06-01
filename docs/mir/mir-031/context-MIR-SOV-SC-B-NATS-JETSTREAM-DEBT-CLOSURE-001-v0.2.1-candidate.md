# context — MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001

```text
Document ID:  context-MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001
Version:      v0.2.1-candidate
Status:       Execution context / Candidate for Codex
Date:         2026-06-01
Corpus:       Sovereign Connect
Plane:        SC-B
MIR:          MIR-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.0-candidate
CSA:          CSA-SOV-SC-B-NATS-JETSTREAM-DEBT-CLOSURE-001 v0.2.0-merged
Baseline:     post-MIR-030 / JetStream hardening validated L4
Baseline tests: sovereign-connect 469 / 0 failures / 0 errors / 0 skipped
```

---

## 1. Purpose

Close exactly three retained SC-B NATS / JetStream debts:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior
DEBT-B-NATS-005 — drain behavior
DEBT-B-NATS-009 — incompatible stream conflict behavior
```

This is a debt-closure / industrialization increment. It is not a new SC-D runtime seed.

---

## 2. Scope

### IN

```text
reconnect observability
handler survivability after forceReconnect for the existing timer-fired event handler
drain / close lifecycle for NatsScBusPort
deterministic publish/register rejection after close
incompatible stream conflict detection using StorageType.File vs StorageType.Memory
ensureStream error classification
architecture test for AutoCloseable if NatsScBusPort owns close/drain
implementation-report closure of DEBT-B-NATS-004 / 005 / 009
```

### OUT — hard stop if introduced

```text
Adapter Manifest runtime
manifest-over-NATS transport
route assignment runtime
adapter lifecycle runtime
provider execution
command admission runtime
capability registry runtime
registerCommandHandler real subscription
registerResponseHandler real subscription
SC-C direct NATS publishing
Flyway migrations
```

---

## 3. Current post-MIR-030 production surface

### 3.1 NatsSubjectBuilder

Current shape:

```java
public final class NatsSubjectBuilder {
    public NatsSubjectBuilder() // implicit no-arg constructor

    public String buildTimerFiredEventSubject(String habitatId)
    public String buildTimerFiredEventSubscriptionPattern()
    public String buildCommandSubject(String habitatId, String adapterId, String targetKind, String targetId)
    public String buildResponseSubject(String habitatId, String adapterId, UUID rootCorrelationId)
    public String buildLifecycleAnnounceSubject(String habitatId)
}
```

`ScSubjectIdTokenCodec`, `ScCorrelationTokenCodec` and `ScSubjectTokenValidator` are static utilities used internally by `NatsSubjectBuilder`. Do **not** instantiate them. Do **not** write:

```java
new NatsSubjectBuilder(new ScSubjectIdTokenCodec(), ...)
```

Correct form:

```java
new NatsSubjectBuilder()
```

### 3.2 ScMessageMetadata and ScEventEnvelope

Current constructor shapes:

```java
new ScMessageMetadata(
    UUID messageId,
    Instant emittedAt,
    UUID correlationId,
    UUID causationId,
    String topologyVersion
)

new ScEventEnvelope<>(
    ScMessageMetadata metadata,
    Object payload,
    ScRoutingKey routingKey
)
```

`payloadType` and `payloadSchemaVersion` are **not** fields of `ScMessageMetadata`. They exist in `ScJsonWireEnvelope` only.

Correct timer-fired envelope helper:

```java
private ScEventEnvelope<Object> buildTimerFiredEnvelope(String habitatId) {
    UUID id = UUID.randomUUID();
    ObjectNode payload = new ObjectMapper().createObjectNode().put("timer", "fired");
    return new ScEventEnvelope<>(
        new ScMessageMetadata(id, Instant.now(), id, null, null),
        payload,
        new ScRoutingKey(
            ScBusLane.EVENT,
            habitatId,
            "sc-c.timer-fired",
            habitatId,
            null,
            null,
            null
        )
    );
}
```

### 3.3 NatsScBusPort

Current shape:

```java
public final class NatsScBusPort implements ScBusPort {
    private final Connection connection;
    private final NatsSubjectBuilder subjectBuilder;
    private final ScJsonWireCodec wireCodec;
    private final EnvelopeValidationService envelopeValidationService;
    private final CopyOnWriteArrayList<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();

    public NatsScBusPort(
        Connection connection,
        NatsSubjectBuilder subjectBuilder,
        ScJsonWireCodec wireCodec,
        EnvelopeValidationService envelopeValidationService
    ) { ... }
}
```

Current `registerCommandHandler` and `registerResponseHandler` are validation-only stubs. They must remain stubs in this MIR.

### 3.4 NatsJetStreamStreamApplicator

Current debt-bearing shape:

```java
try {
    return management.addStream(config);
} catch (JetStreamApiException alreadyExistsOrConflict) {
    try {
        return management.updateStream(config);
    } catch (IOException | JetStreamApiException updateFailed) {
        throw new IllegalStateException(
            "JetStream stream is incompatible or cannot be updated: " + definition.name(),
            updateFailed);
    }
}
```

This treats every `JetStreamApiException` from `addStream(...)` as “maybe already exists”. The debt closure must classify the error first.

---

## 4. Verified jnats 2.25.2 API

These invocation forms are the verified target for Codex. If compilation fails, inspect the jnats 2.25.2 jar / javadocs and patch the package; do not guess alternate method names.

### 4.1 Dispatcher

```java
Dispatcher subscribe(String subject);
Dispatcher unsubscribe(String subject);
```

`Dispatcher.drain(Duration)` must not be used. It is not part of the verified `Dispatcher` surface for this package.

Dispatcher cleanup uses the connection:

```java
connection.closeDispatcher(dispatcher);
```

### 4.2 Connection

```java
void addConnectionListener(ConnectionListener listener);
void removeConnectionListener(ConnectionListener listener);
CompletableFuture<Boolean> drain(Duration timeout) throws TimeoutException, InterruptedException;
void closeDispatcher(Dispatcher dispatcher);
void forceReconnect() throws IOException, InterruptedException;
Connection.Status getStatus();
```

Relevant statuses:

```text
CONNECTED, CONNECTING, RECONNECTING, DISCONNECTED, CLOSED, DRAINING_SUBS, DRAINING_PUBS
```

### 4.3 ConnectionListener

Use an anonymous class to avoid lambda ambiguity:

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

Relevant events:

```text
CONNECTED, CLOSED, DISCONNECTED, RECONNECTED, RESUBSCRIBED, DISCOVERED_SERVERS, LAME_DUCK
```

### 4.4 Reconnect-aware test connections

`NatsLocalServer.connection()` is created internally with default options. Reconnect tests must create their own reconnect-aware connection:

```java
Options opts = new Options.Builder()
    .server(natsUri)
    .maxReconnects(-1)
    .reconnectWait(Duration.ofMillis(200))
    .build();
Connection conn = Nats.connect(opts);
```

Attach listener through `NatsScBusPort` construction, or through `Options.Builder.connectionListener(...)` if a future design moves ownership into a factory. For this package, `NatsScBusPort` may call `connection.addConnectionListener(...)` in its constructor.

### 4.5 Stream error classification

Stream already exists error code:

```java
apiEx.getApiErrorCode() == 10058
```

Only this code may enter compare/update path. Other `JetStreamApiException` values must fail explicitly.

---

## 5. Required implementation decisions

### D-DC-001 — Incompatible stream conflict

Use a real non-updateable conflict fixture:

```text
Existing stream: StorageType.File
Canonical stream: StorageType.Memory
```

Do not use subject-pattern changes as conflict source. Subject changes may be updateable.

### D-DC-002 — Close/drain

Preferred implementation:

```java
public final class NatsScBusPort implements ScBusPort, AutoCloseable
```

The close path must:

```text
1. atomically mark the port closed;
2. close registered dispatchers using connection.closeDispatcher(dispatcher);
3. clear local dispatcher registry;
4. call connection.drain(Duration.ofSeconds(10)).get();
5. never call flush() after drain;
6. reject publish/register calls after close.
```

### D-DC-003 — Reconnect/resubscribe evidence

Minimum accepted evidence:

```text
- NatsScBusPort wires a ConnectionListener.
- Reconnect/resubscribe events are observable via test-only package-visible counters.
- The timer-fired event handler receives an event before and after forceReconnect().
```

If forceReconnect cannot produce an observable event under `NatsLocalServer`, this is a harness limitation and must be reported. It is not acceptable to mark closure if no listener is wired.

### D-DC-004 — Handler registration strategy

Do not implement command/response subscriptions. For event handlers, either:

```text
A. rely on jnats auto-resubscription if the before/after forceReconnect test passes; or
B. store event handler registrations and rebuild the dispatcher on reconnect if auto-resubscription fails.
```

The implementation report must document which strategy was used.

---

## 6. Correct test helper patterns

### 6.1 Build NatsScBusPort

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

### 6.2 Publish a timer-fired wire event from a separate connection

Do not publish raw partial JSON. `NatsScBusPort.registerEventHandler(...)` expects a complete `ScJsonWireEnvelope`.

Correct helper:

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

### 6.3 Build timer-fired envelope

```java
private ScEventEnvelope<Object> buildTimerFiredEnvelope(String habitatId) {
    UUID id = UUID.randomUUID();
    ObjectNode payload = new ObjectMapper().createObjectNode().put("timer", "fired");
    return new ScEventEnvelope<>(
        new ScMessageMetadata(id, Instant.now(), id, null, null),
        payload,
        new ScRoutingKey(ScBusLane.EVENT, habitatId, "sc-c.timer-fired", habitatId, null, null, null)
    );
}
```

---

## 7. Hard stops

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
