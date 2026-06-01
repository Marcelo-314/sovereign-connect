# context — MIR-SOV-SC-B-JETSTREAM-HARDENING-001

```text
Document ID:  context-MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Version:      v0.2.1-candidate
Status:       Execution package context / Ready for Codex after review
Date:         2026-06-01
Corpus:       Sovereign Connect
Plane:        SC-B
Scope:        JetStream hardening after MU-029
MIR:          MIR-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate
CSA:          CSA-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-merged
Baseline:     sovereign-connect-pre-30 / post-MU-029 / 454 tests green
```

---

## 0. Read order for Codex

Read these files in this order:

1. `MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-candidate.md`
2. `CSA-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-merged.md`
3. `SDD-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-candidate.md`
4. this `context` file
5. `codex-prompt-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md`
6. `acceptance-map-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md`

This package is **SC-B JetStream hardening**, not a new SC-D feature.

---

## 1. Goal

MU-029 left JetStream in config-only disposition. Five stream names and subject patterns exist as Java constants, but no stream is applied to a live JetStream server and no durable consumer exists.

This MIR closes that gap:

```text
NatsStreamConfiguration constants
  → live JetStream stream application
  → durable SCB_LIFECYCLE_V1 consumer
  → lifecycle retention/replay fixture
  → JetStream ack boundary tests
  → import-level architecture hardening
  → explicit reconnect/drain/conflict debt IDs if deferred
```

This MIR is SC-B hardening. It is not Adapter Manifest runtime, not route assignment, not adapter lifecycle runtime.

---

## 2. Hard boundary

### In scope

```text
NatsJetStreamStreamApplicator or equivalent.
NatsJetStreamConsumerApplicator or equivalent.
JetStreamHardeningConfiguration or equivalent.
Live ensure/apply of all five SCB_*_V1 streams.
Idempotent stream ensure.
DEBT-B-NATS-009 for incompatible-stream conflict behavior, unless validated with verified non-mutable stream settings.
Durable consumer for SCB_LIFECYCLE_V1.
Lifecycle retention/replay fixture.
JetStream ack is transport-only assertion.
Import-level architecture tests.
DEBT-B-NATS-004 / DEBT-B-NATS-005 if reconnect/drain remain deferred.
```

### Out of scope

```text
Adapter Manifest runtime.
AdapterManifestProposal live transport.
Route assignment runtime.
Adapter lifecycle runtime.
Provider execution.
Command admission runtime.
Capability registry runtime.
TCK executable harness.
SC-C direct NATS publishing.
Flyway migrations.
Replacing DispatchStateWritePort / DispatchObservationPort with JetStream retention.
```

---

## 3. Exact current code surface

### 3.1 Existing production NATS package

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
  NatsScBusPort.java
  NatsSubjectBuilder.java
  NatsStreamConfiguration.java
  NatsLifecycleChannelMapper.java
```

### 3.2 `NatsStreamConfiguration` — exact current shape

```java
public final class NatsStreamConfiguration {
    public static final String SCB_COMMANDS_V1  = "SCB_COMMANDS_V1";
    public static final String SCB_EVENTS_V1    = "SCB_EVENTS_V1";
    public static final String SCB_RESPONSES_V1 = "SCB_RESPONSES_V1";
    public static final String SCB_LIFECYCLE_V1 = "SCB_LIFECYCLE_V1";
    public static final String SCB_DLQ_V1       = "SCB_DLQ_V1";

    private NatsStreamConfiguration() {}

    public static List<StreamDefinition> streamDefinitions() {
        return List.of(
            new StreamDefinition(SCB_COMMANDS_V1,  List.of("sc.v1.*.command.>")),
            new StreamDefinition(SCB_EVENTS_V1,    List.of("sc.v1.*.event.>")),
            new StreamDefinition(SCB_RESPONSES_V1, List.of("sc.v1.*.response.>")),
            new StreamDefinition(SCB_LIFECYCLE_V1, List.of("sc.v1.*.lifecycle.>")),
            new StreamDefinition(SCB_DLQ_V1,       List.of("sc.v1.*.dlq.>"))
        );
    }

    public record StreamDefinition(String name, List<String> subjects) {
        public StreamDefinition {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
            if (subjects == null || subjects.isEmpty()) throw new IllegalArgumentException("subjects are required");
            subjects = List.copyOf(subjects);
        }
    }
}
```

Do not rename stream constants or subject patterns in this MIR.

### 3.3 `NatsScBusPort` — current publish path

All three publish methods currently call NATS Core publish:

```java
private void publishWire(ScJsonWireEnvelope wire, String subject) {
    WireEnvelopeValidator.validate(wire);
    connection.publish(subject, wireCodec.marshal(wire));
    flush();
}
```

This MIR does **not** require converting `NatsScBusPort` to `JetStream.publish()`. The lifecycle retention fixture may use Core publish captured by a configured stream and a separate explicit `JetStream.publish()` test for ack boundary. Record the choice in the implementation report.

### 3.4 Existing imports relevant to architecture tests

`NatsScBusPort.java` must import:

```java
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator;
```

`NatsSubjectBuilder.java` must import:

```java
import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec;
import com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec;
```

`NatsLifecycleChannelMapper.java` imports `ScSubjectIdTokenCodec` and `ScSubjectTokenValidator`. It does **not** import `ScCorrelationTokenCodec`. Do not write an architecture test expecting `NatsLifecycleChannelMapper` to import `ScCorrelationTokenCodec`.

### 3.5 Existing weak architecture test to replace

Current `ScBusNatsArchitectureTest.natsPackageUsesSerializationUtilitiesAndWireValidator()` aggregates all NATS source files and checks string presence. That is seed-grade only.

This MIR must replace it with file-specific import assertions.

### 3.6 `NatsLocalServer` pattern

Use the existing post-MU-029 pattern:

```java
try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
    JetStreamManagement jsm = server.connection().jetStreamManagement();
    // test body
}
```

`NatsLocalServer` starts `nats-server` with JetStream enabled. Use `Path.of("target/nats-server-cache")` in all new tests.

---

## 4. jnats 2.25.2 verified invocation targets

These invocation forms are the target for Codex. If compilation fails, inspect the `jnats-2.25.2.jar` / Javadocs and patch the package; do not guess alternate method names.

### 4.1 JetStream management

```java
JetStreamManagement jsm = connection.jetStreamManagement();
```

### 4.2 Stream configuration

```java
StreamConfiguration config = StreamConfiguration.builder()
    .name(definition.name())
    .subjects(definition.subjects())
    .storageType(StorageType.Memory)
    .build();
```

### 4.3 Stream application

```java
StreamInfo created = jsm.addStream(config);
StreamInfo updated = jsm.updateStream(config);
```

Use the add-then-update pattern for idempotency. If `addStream` throws because the stream already exists, attempt `updateStream(config)`. If `updateStream` also fails, wrap as `IllegalStateException` with the stream name.

### 4.4 Consumer configuration and creation

```java
ConsumerConfiguration consumer = ConsumerConfiguration.builder()
    .durable(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
    .name(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
    .filterSubject(JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT)
    .ackPolicy(AckPolicy.Explicit)
    .ackWait(JetStreamHardeningConfiguration.LIFECYCLE_ACK_WAIT)
    .maxDeliver(JetStreamHardeningConfiguration.LIFECYCLE_MAX_DELIVER)
    .deliverPolicy(DeliverPolicy.All)
    .build();

ConsumerInfo info = jsm.addOrUpdateConsumer(
    NatsStreamConfiguration.SCB_LIFECYCLE_V1,
    consumer);
```

Use `addOrUpdateConsumer`, not `addConsumer`.

### 4.5 Consumer inspection

```java
ConsumerInfo info = jsm.getConsumerInfo(
    NatsStreamConfiguration.SCB_LIFECYCLE_V1,
    JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER);

info.getName();
info.getConsumerConfiguration().getFilterSubject();
info.getConsumerConfiguration().getAckPolicy();
info.getConsumerConfiguration().getAckWait();
info.getConsumerConfiguration().getMaxDeliver();
info.getConsumerConfiguration().getDeliverPolicy();
```

### 4.6 JetStream publish ack

```java
JetStream js = connection.jetStream();
PublishAck ack = js.publish(subject, payloadBytes);
ack.getStream();
```

`PublishAck` is broker transport confirmation only. It is not command success, manifest acceptance, adapter activation, or SC-C semantic authority.

### 4.7 Pull subscription replay rule

For pull subscriptions, `nextMessage(...)` consumes from the local client buffer. A pull request must be issued first.

Correct pattern:

```java
JetStreamSubscription sub = js.subscribe(
    JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT,
    PullSubscribeOptions.bind(
        NatsStreamConfiguration.SCB_LIFECYCLE_V1,
        JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER));

sub.pull(1);
Message msg = sub.nextMessage(Duration.ofSeconds(5));
assertThat(msg).isNotNull();
msg.ack();
```

Do not write a replay/consume test that calls `nextMessage(...)` without first calling `pull(...)`.

---

## 5. Required production surface

### 5.1 `JetStreamHardeningConfiguration.java`

```java
package com.sovereign.connect.bus.runtime.nats;

import java.time.Duration;

public final class JetStreamHardeningConfiguration {
    public static final String   LIFECYCLE_DURABLE_CONSUMER = "sc-b-lifecycle-consumer-v1";
    public static final String   LIFECYCLE_FILTER_SUBJECT   = "sc.v1.*.lifecycle.>";
    public static final Duration LIFECYCLE_ACK_WAIT         = Duration.ofSeconds(30);
    public static final int      LIFECYCLE_MAX_DELIVER      = 3;

    private JetStreamHardeningConfiguration() {}
}
```

### 5.2 `NatsJetStreamStreamApplicator.java`

```java
package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NatsJetStreamStreamApplicator {

    private final JetStreamManagement management;

    public NatsJetStreamStreamApplicator(JetStreamManagement management) {
        this.management = Objects.requireNonNull(management, "management is required");
    }

    public List<StreamInfo> ensureAllConfiguredStreams() throws IOException, JetStreamApiException {
        List<StreamInfo> infos = new ArrayList<>();
        for (NatsStreamConfiguration.StreamDefinition definition
                : NatsStreamConfiguration.streamDefinitions()) {
            infos.add(ensureStream(definition));
        }
        return List.copyOf(infos);
    }

    public StreamInfo ensureStream(NatsStreamConfiguration.StreamDefinition definition)
            throws IOException, JetStreamApiException {
        Objects.requireNonNull(definition, "definition is required");
        StreamConfiguration config = StreamConfiguration.builder()
                .name(definition.name())
                .subjects(definition.subjects())
                .storageType(StorageType.Memory)
                .build();
        try {
            return management.addStream(config);
        } catch (JetStreamApiException alreadyExistsOrConflict) {
            try {
                return management.updateStream(config);
            } catch (IOException | JetStreamApiException updateFailed) {
                throw new IllegalStateException(
                        "JetStream stream is incompatible or cannot be updated: "
                        + definition.name(), updateFailed);
            }
        }
    }
}
```

### 5.3 `NatsJetStreamConsumerApplicator.java`

```java
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

    public ConsumerInfo ensureLifecycleDurableConsumer()
            throws IOException, JetStreamApiException {
        ConsumerConfiguration config = ConsumerConfiguration.builder()
                .durable(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
                .name(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER)
                .filterSubject(JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT)
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(JetStreamHardeningConfiguration.LIFECYCLE_ACK_WAIT)
                .maxDeliver(JetStreamHardeningConfiguration.LIFECYCLE_MAX_DELIVER)
                .deliverPolicy(DeliverPolicy.All)
                .build();
        return management.addOrUpdateConsumer(
                NatsStreamConfiguration.SCB_LIFECYCLE_V1, config);
    }
}
```

---

## 6. Test obligations

### 6.1 Stream application tests

Create `NatsJetStreamStreamApplicatorTest` with at least:

```text
appliesAllConfiguredStreamsToLiveJetStream
streamApplicationIsIdempotent
streamInfoContainsExpectedSubjectPatterns
```

All tests must use `NatsLocalServer` and a live `JetStreamManagement`.

### 6.2 Consumer tests

Create `NatsJetStreamLifecycleConsumerTest` with at least:

```text
createsDurableLifecycleConsumerOnLiveJetStream
consumerInfoReportsExpectedDurableNameAndFilterSubject
consumerCreationIsIdempotent
consumerAckWaitMatchesSeedConstant
```

The durable lifecycle consumer is mandatory. Omission or ephemeral-only downgrade is STOP-JSH-001.

### 6.3 Lifecycle retention/replay tests

Create `NatsJetStreamLifecycleRetentionTest` with at least:

```text
lifecycleSubjectIsRetainedBySCB_LIFECYCLE_V1Stream
jetStreamPublishAckIsTransportOnlyNotSemanticSuccess
lifecycleDurableConsumerCanPullRetainedMessage
```

The pull test must call `sub.pull(1)` before `sub.nextMessage(...)`.

Use `NatsLifecycleChannelMapper.announceSubject(habitatId)` to build a lifecycle subject. Use a minimal payload such as:

```json
{"payloadType":"sc.lifecycle.adapter-announce.v1"}
```

No Adapter Manifest runtime is allowed.

### 6.4 Incompatible stream conflict behavior

Do **not** include a paste-ready test that assumes changing stream subjects is incompatible. That behavior may be updateable and can produce a fragile test.

For this execution package, retain:

```text
DEBT-B-NATS-009 — incompatible stream conflict behavior not validated in JetStream hardening MIR.
```

If the implementation chooses to validate conflict behavior anyway, it must first verify a truly non-updateable conflict against `jnats 2.25.2` and `nats-server v2.14.1`. Otherwise, record `DEBT-B-NATS-009`.

### 6.5 Architecture tests

Modify `ScBusNatsArchitectureTest`:

Remove the aggregate string-presence test:

```java
natsPackageUsesSerializationUtilitiesAndWireValidator()
```

Add file-specific import tests:

```java
@Test
void natsScBusPortImportsScJsonWireCodecAndWireEnvelopeValidator() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java"));
    assertThat(source)
        .contains("import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec");
    assertThat(source)
        .contains("import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator");
}

@Test
void natsSubjectBuilderImportsScSubjectIdTokenCodecAndScCorrelationTokenCodec() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/com/sovereign/connect/bus/runtime/nats/NatsSubjectBuilder.java"));
    assertThat(source)
        .contains("import com.sovereign.connect.bus.runtime.serialization.ScSubjectIdTokenCodec");
    assertThat(source)
        .contains("import com.sovereign.connect.bus.runtime.serialization.ScCorrelationTokenCodec");
}

@Test
void jetStreamHardeningClassesDoNotImportDispatchPersistencePorts() throws Exception {
    assertNoSourceContains(
        Path.of("src/main/java/com/sovereign/connect/bus/runtime/nats"),
        List.of(
            "import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort",
            "import com.sovereign.connect.bus.runtime.port.DispatchObservationPort",
            "import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchStateRepository",
            "import com.sovereign.connect.bus.runtime.persistence.JdbcDispatchObservationRepository"
        )
    );
}
```

Do not assert `ScCorrelationTokenCodec` import in `NatsLifecycleChannelMapper`.

---

## 7. Required implementation report entries

Create or update:

```text
docs/mir/mir-030/implementation-report.md
```

Record:

```text
branch and commits
files created/modified
final test count
stream application evidence
SCB_LIFECYCLE_V1 durable consumer policy
the lifecycle retention/replay test shape
JetStream ack boundary: transport only
DEBT-B-NATS-009 retained unless conflict behavior is validated
DEBT-B-NATS-004 if reconnect/resubscribe is not validated
DEBT-B-NATS-005 if drain is not validated
confirmation DispatchStateWritePort / DispatchObservationPort were not replaced
confirmation SC-C does not import io.nats.* or bus.runtime.nats.*
```

---

## 8. Expected verification

Targeted tests:

```bash
mvn -q test -Dtest="NatsJetStreamStreamApplicatorTest,NatsJetStreamLifecycleConsumerTest,NatsJetStreamLifecycleRetentionTest,ScBusNatsArchitectureTest"
```

Full regression:

```bash
mvn -q test
```

Expected result:

```text
sovereign-connect: >=469 tests, 0 failures, 0 errors, 0 skipped
EIB: 56 tests, 0 failures, 0 errors, 0 skipped
```

---

## 9. Hard stops

```text
STOP-1  Streams applied only to Java objects, not to a live JetStream server.
STOP-2  SCB_LIFECYCLE_V1 durable consumer omitted or downgraded to ephemeral.
STOP-3  JetStream ack used as semantic success, manifest acceptance, adapter activation or command completion.
STOP-4  Reconnect/drain gaps retained without DEBT-B-NATS-004 / DEBT-B-NATS-005.
STOP-5  Implementation expands into Adapter Manifest runtime, route assignment or provider execution.
STOP-6  Stream names or subject patterns modified.
STOP-7  DispatchStateWritePort / DispatchObservationPort removed or bypassed.
STOP-8  Broker mocked to satisfy live hardening tests.
STOP-9  jnats method names invented instead of using context §4 invocation targets.
STOP-10 Incompatible stream conflict behavior neither validated nor recorded as DEBT-B-NATS-009.
STOP-11 Pull consumer replay test calls nextMessage(...) without first calling pull(...).
```
