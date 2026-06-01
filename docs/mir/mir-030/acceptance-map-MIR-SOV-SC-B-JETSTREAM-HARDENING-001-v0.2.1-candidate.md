# acceptance-map — MIR-SOV-SC-B-JETSTREAM-HARDENING-001

```text
Document ID: acceptance-map-MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Version:     v0.2.1-candidate
Status:      Execution package acceptance map
MIR:         MIR-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate
```

---

## 1. MIR acceptance groups

### G1 — Stream application

```text
AC-JSH-001  All five SCB_*_V1 streams are applied against live JetStream.
AC-JSH-002  Stream application is idempotent.
AC-JSH-003  Stream names and subject patterns remain unchanged from NatsStreamConfiguration.
```

Evidence:

```text
NatsJetStreamStreamApplicatorTest.appliesAllConfiguredStreamsToLiveJetStream
NatsJetStreamStreamApplicatorTest.streamApplicationIsIdempotent
NatsJetStreamStreamApplicatorTest.streamInfoContainsExpectedSubjectPatterns
```

### G2 — Durable lifecycle consumer

```text
AC-JSH-004  SCB_LIFECYCLE_V1 durable consumer is created against live JetStream.
AC-JSH-005  Consumer policy is explicit: durable/name/filter/ackPolicy/ackWait/maxDeliver/deliverPolicy.
AC-JSH-006  Consumer creation is idempotent.
```

Evidence:

```text
NatsJetStreamLifecycleConsumerTest.createsDurableLifecycleConsumerOnLiveJetStream
NatsJetStreamLifecycleConsumerTest.consumerInfoReportsExpectedDurableNameAndFilterSubject
NatsJetStreamLifecycleConsumerTest.consumerCreationIsIdempotent
NatsJetStreamLifecycleConsumerTest.consumerAckWaitMatchesSeedConstant
```

### G3 — Lifecycle retention/replay

```text
AC-JSH-007  Lifecycle subject is retained by SCB_LIFECYCLE_V1.
AC-JSH-008  Durable pull consumer can retrieve retained lifecycle message.
AC-JSH-009  Pull consumer tests call pull(...) before nextMessage(...).
```

Evidence:

```text
NatsJetStreamLifecycleRetentionTest.lifecycleSubjectIsRetainedBySCB_LIFECYCLE_V1Stream
NatsJetStreamLifecycleRetentionTest.lifecycleDurableConsumerCanPullRetainedMessage
```

### G4 — Ack boundary

```text
AC-JSH-010  JetStream PublishAck is asserted only as transport confirmation.
AC-JSH-011  No assertion treats JetStream ack as command success, manifest acceptance or adapter activation.
```

Evidence:

```text
NatsJetStreamLifecycleRetentionTest.jetStreamPublishAckIsTransportOnlyNotSemanticSuccess
Implementation report ack-boundary section
```

### G5 — Conflict/reconnect/drain debt

```text
AC-JSH-012  Incompatible stream conflict behavior is either validated or retained as DEBT-B-NATS-009.
AC-JSH-013  Reconnect/resubscribe gap is implemented or retained as DEBT-B-NATS-004.
AC-JSH-014  Drain behavior gap is implemented or retained as DEBT-B-NATS-005.
```

Expected for this package:

```text
DEBT-B-NATS-009 is retained unless implementation verifies a non-updateable stream conflict.
DEBT-B-NATS-004 / 005 may be retained if reconnect/drain remain out of scope.
```

### G6 — Architecture hardening

```text
AC-JSH-015  Aggregate string-presence NATS serialization test is replaced by file-specific import checks.
AC-JSH-016  NATS package does not import dispatch persistence ports/repositories.
AC-JSH-017  SC-C continues not to import io.nats.* or bus.runtime.nats.*.
```

Evidence:

```text
ScBusNatsArchitectureTest.natsScBusPortImportsScJsonWireCodecAndWireEnvelopeValidator
ScBusNatsArchitectureTest.natsSubjectBuilderImportsScSubjectIdTokenCodecAndScCorrelationTokenCodec
ScBusNatsArchitectureTest.jetStreamHardeningClassesDoNotImportDispatchPersistencePorts
existing SC-C NATS boundary tests remain green
```

---

## 2. Required verification commands

```bash
mvn -q test -Dtest="NatsJetStreamStreamApplicatorTest,NatsJetStreamLifecycleConsumerTest,NatsJetStreamLifecycleRetentionTest,ScBusNatsArchitectureTest"
mvn -q test
```

Expected:

```text
sovereign-connect: >=469 tests, 0 failures, 0 errors, 0 skipped
EIB: 56 tests, 0 failures, 0 errors, 0 skipped
```

---

## 3. STOP map

```text
STOP-1  AC-JSH-001 not met.
STOP-2  AC-JSH-004 not met.
STOP-3  AC-JSH-010/011 violated.
STOP-4  AC-JSH-013/014 not met or debt IDs absent.
STOP-5  Out-of-scope runtime introduced.
STOP-6  AC-JSH-003 violated without SDD patch.
STOP-7  AC-JSH-016 violated.
STOP-8  Live JetStream tests use mock broker.
STOP-9  jnats API names guessed.
STOP-10 AC-JSH-012 neither validated nor retained.
STOP-11 AC-JSH-009 violated.
```
