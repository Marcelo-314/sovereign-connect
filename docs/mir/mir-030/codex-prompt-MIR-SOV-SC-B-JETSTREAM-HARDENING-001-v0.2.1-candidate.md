# codex-prompt — MIR-SOV-SC-B-JETSTREAM-HARDENING-001

```text
Document ID:  codex-prompt-MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Version:      v0.2.1-candidate
Status:       Ready for Codex after review
MIR:          MIR-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate
Context:      context-MIR-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.1-candidate
```

You are implementing **MIR-SOV-SC-B-JETSTREAM-HARDENING-001** in the Sovereign Connect repository.

Read first, in order:

```text
MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-candidate.md
CSA-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-merged.md
SDD-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.0-candidate.md
context-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
acceptance-map-MIR-SOV-SC-B-JETSTREAM-HARDENING-001-v0.2.1-candidate.md
```

---

## Mission

Harden the post-MU-029 SC-B NATS/JetStream substrate.

MU-029 left JetStream config-only. This increment must apply the configured streams against a live JetStream server and create a durable consumer for `SCB_LIFECYCLE_V1`.

```text
NatsStreamConfiguration constants
  → live stream application via NatsJetStreamStreamApplicator
  → durable SCB_LIFECYCLE_V1 consumer via NatsJetStreamConsumerApplicator
  → lifecycle retention/replay fixture
  → import-level architecture tests
```

---

## Non-negotiable boundaries

Do not implement:

```text
Adapter Manifest runtime
AdapterManifestProposal live transport
route assignment runtime
adapter lifecycle runtime
provider execution
command admission runtime
SC-C direct NATS publishing
Flyway migrations
```

Do not replace or bypass:

```text
DispatchStateWritePort
DispatchObservationPort
JdbcDispatchStateRepository
JdbcDispatchObservationRepository
```

JetStream retention is broker technical retention. It is **not** command success, manifest acceptance, adapter activation, or semantic authority.

---

## Step 0 — Baseline

```bash
git checkout develop
git pull origin develop
git checkout -b feat/sc-b-mir-030-jetstream-hardening
mvn -q test
```

Expected baseline: `454 tests, 0 failures, 0 errors, 0 skipped`. If this differs materially, stop and report.

---

## Step 1 — Add production classes

Create these files under:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
```

### 1.1 `JetStreamHardeningConfiguration.java`

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

### 1.2 `NatsJetStreamStreamApplicator.java`

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

If any `JetStreamManagement` signature does not compile, STOP-9: inspect the `jnats-2.25.2.jar` before continuing. Do not invent method names.

### 1.3 `NatsJetStreamConsumerApplicator.java`

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

Omitting the durable consumer is STOP-JSH-001.

---

## Step 2 — Stream applicator tests

Create:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsJetStreamStreamApplicatorTest.java
```

Use `NatsLocalServer`:

```java
try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
    JetStreamManagement jsm = server.connection().jetStreamManagement();
    // test body
}
```

Required tests:

```java
@Test
void appliesAllConfiguredStreamsToLiveJetStream() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        List<StreamInfo> infos = new NatsJetStreamStreamApplicator(jsm)
            .ensureAllConfiguredStreams();

        assertThat(infos).hasSize(5);
        assertThat(infos)
            .extracting(info -> info.getConfiguration().getName())
            .containsExactlyInAnyOrder(
                NatsStreamConfiguration.SCB_COMMANDS_V1,
                NatsStreamConfiguration.SCB_EVENTS_V1,
                NatsStreamConfiguration.SCB_RESPONSES_V1,
                NatsStreamConfiguration.SCB_LIFECYCLE_V1,
                NatsStreamConfiguration.SCB_DLQ_V1);
    }
}

@Test
void streamApplicationIsIdempotent() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        var applicator = new NatsJetStreamStreamApplicator(jsm);
        applicator.ensureAllConfiguredStreams();
        assertThatCode(() -> applicator.ensureAllConfiguredStreams())
            .doesNotThrowAnyException();
    }
}

@Test
void streamInfoContainsExpectedSubjectPatterns() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();

        StreamInfo lifecycle = jsm.getStreamInfo(NatsStreamConfiguration.SCB_LIFECYCLE_V1);
        assertThat(lifecycle.getConfiguration().getSubjects())
            .containsExactly("sc.v1.*.lifecycle.>");
    }
}
```

Do not add a fragile incompatible-subjects conflict test. Record `DEBT-B-NATS-009` unless a non-updateable conflict is verified against the actual server/API.

---

## Step 3 — Durable lifecycle consumer tests

Create:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsJetStreamLifecycleConsumerTest.java
```

Required tests:

```java
@Test
void createsDurableLifecycleConsumerOnLiveJetStream() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();

        ConsumerInfo info = new NatsJetStreamConsumerApplicator(jsm)
            .ensureLifecycleDurableConsumer();

        assertThat(info).isNotNull();
        assertThat(info.getName())
            .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER);
    }
}

@Test
void consumerInfoReportsExpectedDurableNameAndFilterSubject() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
        new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

        ConsumerInfo info = jsm.getConsumerInfo(
            NatsStreamConfiguration.SCB_LIFECYCLE_V1,
            JetStreamHardeningConfiguration.LIFECYCLE_DURABLE_CONSUMER);

        assertThat(info.getConsumerConfiguration().getFilterSubject())
            .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_FILTER_SUBJECT);
        assertThat(info.getConsumerConfiguration().getAckPolicy())
            .isEqualTo(AckPolicy.Explicit);
        assertThat(info.getConsumerConfiguration().getMaxDeliver())
            .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_MAX_DELIVER);
        assertThat(info.getConsumerConfiguration().getDeliverPolicy())
            .isEqualTo(DeliverPolicy.All);
    }
}

@Test
void consumerCreationIsIdempotent() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
        var applicator = new NatsJetStreamConsumerApplicator(jsm);
        applicator.ensureLifecycleDurableConsumer();
        assertThatCode(() -> applicator.ensureLifecycleDurableConsumer())
            .doesNotThrowAnyException();
    }
}

@Test
void consumerAckWaitMatchesSeedConstant() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
        ConsumerInfo info = new NatsJetStreamConsumerApplicator(jsm)
            .ensureLifecycleDurableConsumer();

        assertThat(info.getConsumerConfiguration().getAckWait())
            .isEqualTo(JetStreamHardeningConfiguration.LIFECYCLE_ACK_WAIT);
    }
}
```

---

## Step 4 — Retention, replay and ack-boundary tests

Create:

```text
src/test/java/com/sovereign/connect/bus/runtime/nats/NatsJetStreamLifecycleRetentionTest.java
```

Use:

```java
private static final NatsLifecycleChannelMapper LIFECYCLE = new NatsLifecycleChannelMapper();
private static final String HABITAT_ID = "habitat.test.h1";
```

### 4.1 Core publish captured by stream

```java
@Test
void lifecycleSubjectIsRetainedBySCB_LIFECYCLE_V1Stream() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
        new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

        String subject = LIFECYCLE.announceSubject(HABITAT_ID);
        byte[] payload = "{\"payloadType\":\"sc.lifecycle.adapter-announce.v1\"}".getBytes(StandardCharsets.UTF_8);
        server.connection().publish(subject, payload);
        server.connection().flush(Duration.ofSeconds(2));

        StreamInfo info = jsm.getStreamInfo(NatsStreamConfiguration.SCB_LIFECYCLE_V1);
        assertThat(info.getStreamState().getMsgCount()).isGreaterThanOrEqualTo(1L);
    }
}
```

### 4.2 JetStream ack boundary

```java
@Test
void jetStreamPublishAckIsTransportOnlyNotSemanticSuccess() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
        new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

        JetStream js = server.connection().jetStream();
        String subject = LIFECYCLE.announceSubject(HABITAT_ID);
        byte[] payload = "{\"payloadType\":\"sc.lifecycle.adapter-announce.v1\"}".getBytes(StandardCharsets.UTF_8);
        PublishAck ack = js.publish(subject, payload);

        assertThat(ack.getStream()).isEqualTo(NatsStreamConfiguration.SCB_LIFECYCLE_V1);
        // No assertion about manifest acceptance, adapter activation, command success or SC-C semantic authority.
    }
}
```

### 4.3 Durable pull consumer can replay retained message

This test must call `pull(1)` before `nextMessage(...)`.

```java
@Test
void lifecycleDurableConsumerCanPullRetainedMessage() throws Exception {
    try (NatsLocalServer server = NatsLocalServer.start(Path.of("target/nats-server-cache"))) {
        JetStreamManagement jsm = server.connection().jetStreamManagement();
        new NatsJetStreamStreamApplicator(jsm).ensureAllConfiguredStreams();
        new NatsJetStreamConsumerApplicator(jsm).ensureLifecycleDurableConsumer();

        JetStream js = server.connection().jetStream();
        String subject = LIFECYCLE.announceSubject(HABITAT_ID);
        byte[] payload = "{\"payloadType\":\"sc.lifecycle.adapter-announce.v1\"}".getBytes(StandardCharsets.UTF_8);
        js.publish(subject, payload);

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
    }
}
```

STOP-11 if a pull consumer test calls `nextMessage(...)` without first calling `pull(...)`.

---

## Step 5 — Architecture test hardening

Modify:

```text
src/test/java/com/sovereign/connect/bus/ScBusNatsArchitectureTest.java
```

Remove the weak aggregate-string test:

```java
natsPackageUsesSerializationUtilitiesAndWireValidator()
```

Add:

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

`assertNoSourceContains` already exists in `ScBusNatsArchitectureTest`; reuse it.

Do not assert `ScCorrelationTokenCodec` import in `NatsLifecycleChannelMapper`.

---

## Step 6 — Implementation report

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
lifecycle retention/replay test shape
JetStream ack boundary: transport only
DEBT-B-NATS-009 retained unless conflict behavior is validated with a verified non-updateable conflict
DEBT-B-NATS-004 if reconnect/resubscribe is not validated
DEBT-B-NATS-005 if drain is not validated
confirmation DispatchStateWritePort / DispatchObservationPort were not replaced
confirmation SC-C does not import io.nats.* or bus.runtime.nats.*
```

---

## Step 7 — Verification

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

## Hard stops

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
