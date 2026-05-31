# Implementation Report - MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001

```text
Document ID:  implementation-report-MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
Version:      v0.2.3-candidate
Status:       Implemented
Date:         2026-05-30
Target MIR:   MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v0.2.0-candidate
```

---

## 1. Execution summary

```text
Branch: feat/sc-b-mir-029-nats-jetstream-binding-seed
Base branch / base commit: develop / c8d50d0
Primary implementation commit: d616cf9129c9fdc21bd0b7d9239b267c79933446
Documentation/evidence commit: c175a0a8ef4d9e24a784df1a274da2a09936f09c
Execution date: 2026-05-30
Executor: Codex
Verdict: Validated L4
```

Summary:

```text
Implemented the first SC-B physical NATS binding seed as NatsScBusPort.
Preserved ScBusPort and RuntimeDispatchService constructor shapes.
Added NATS subject construction using MU-028 scid1_ and correlation-token codecs.
Added SC-D command/result wire reference records under bus.contract.scd.
Added NATS Core live tests for event publish/subscribe and RuntimeDispatchService -> NATS EVENT path.
Added command/response direct wire fixture tests using explicit routing keys.
Added JetStream stream name/pattern configuration seed; live stream application is split downstream.
```

---

## 2. Scope confirmation

Implemented:

```text
[x] io.nats:jnats dependency
[x] Testcontainers NATS dependency plus justified local nats-server fallback
[x] NatsScBusPort implements ScBusPort
[x] NatsSubjectBuilder
[x] NatsStreamConfiguration / JetStream disposition
[x] NatsLifecycleChannelMapper mapping fixture
[x] ScdCommand production wire reference record
[x] ScdExecutionResult production wire reference record
[x] COMMAND_SCD_V1 / RESPONSE_SCD_EXECUTION_RESULT_V1 registry constants
[x] RuntimeDispatchService -> NATS EVENT / TIMER_FIRED_SIGNAL E2E
[x] COMMAND wire fixture
[x] RESPONSE wire fixture
[x] architecture test allowlist update
```

Explicitly not implemented:

```text
[x] productive adapter runtime
[x] lifecycle admission runtime
[x] Adapter Manifest runtime
[x] Capability Semantics runtime
[x] provider execution
[x] productive fact-family runtime
[x] semantic retry
[x] terminal semantic success ownership
[x] SC-C direct NATS publishing
```

---

## 3. Changed files

```text
Production files:
- src/main/java/com/sovereign/connect/bus/contract/scd/ScdCommand.java
- src/main/java/com/sovereign/connect/bus/contract/scd/ScdExecutionResult.java
- src/main/java/com/sovereign/connect/bus/runtime/nats/NatsSubjectBuilder.java
- src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java
- src/main/java/com/sovereign/connect/bus/runtime/nats/NatsStreamConfiguration.java
- src/main/java/com/sovereign/connect/bus/runtime/nats/NatsLifecycleChannelMapper.java
- src/main/java/com/sovereign/connect/bus/runtime/serialization/ScPayloadTypeRegistry.java

Test files:
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsLocalServer.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsSubjectBuilderTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/ScdCommandWirePayloadTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/ScdExecutionResultWirePayloadTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPortEventIntegrationTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/RuntimeDispatchToNatsEventIntegrationTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPortCommandWireFixtureTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPortResponseWireFixtureTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsStreamConfigurationTest.java
- src/test/java/com/sovereign/connect/bus/runtime/nats/NatsLifecycleChannelMapperTest.java
- src/test/java/com/sovereign/connect/bus/ScBusNatsArchitectureTest.java
- architecture boundary tests updated for intentional NATS allowlist

Docs/evidence files:
- docs/mir/mir-029/*

Build files:
- pom.xml
```

---

## 4. Dependency and fixture strategy

NATS dependency:

```text
jnats version: 2.25.2
nats-server runner test fallback: 3.1.0
Testcontainers version: 1.20.6
```

Test fixture:

```text
[x] Testcontainers NATS dependency included as MIR default substrate
[x] local nats-server fallback used for this run
```

Fallback justification:

```text
Docker was unavailable locally:
  docker version -> cannot connect to Docker Desktop Linux engine pipe.

The tests therefore use NatsLocalServer, an automated local nats-server fixture.
It downloads nats-server v2.14.1 into target/nats-server-cache when no
NATS_SERVER_EXECUTABLE is configured, starts it with JetStream enabled, and
connects through jnats. No manual NATS server is required.
```

---

## 5. JetStream disposition

```text
[x] Config-only stream names/patterns implemented; live JetStream hardening split recommended.
```

Evidence:

```text
Test class: NatsStreamConfigurationTest
Assertions: SCB_COMMANDS_V1, SCB_EVENTS_V1, SCB_RESPONSES_V1, SCB_LIFECYCLE_V1, SCB_DLQ_V1 and subject patterns.
Real server with JetStream enabled: yes, integration fixture starts nats-server v2.14.1 with --js.
How stream existence/ack was verified: not verified in MU-029; stream creation/application is not claimed.
Recommended artifact: MIR-SOV-SC-B-JETSTREAM-HARDENING-001 / MU-029B.
Reason: MU-029 validates live NATS Core binding and config seed without treating JetStream as dispatch state or observation persistence.
```

---

## 6. Test evidence

Baseline:

```text
mvn -q test: PASS - 411 tests, 0 failures, 0 errors, 0 skipped
```

Targeted tests:

```text
mvn -q compile: PASS

mvn -q test -Dtest="ScBusArchitectureTest,ScBusHardeningArchitectureTest,ScBusObservationPersistenceArchitectureTest,ScBusOutboxBridgeArchitectureTest,ScBusSerializationArchitectureTest"
Result: PASS

mvn -q test -Dtest="NatsSubjectBuilderTest,ScdCommandWirePayloadTest,ScdExecutionResultWirePayloadTest,ScBusNatsArchitectureTest,NatsLifecycleChannelMapperTest"
Result: PASS - 26 tests, 0 failures, 0 errors, 0 skipped

mvn -q test -Dtest="NatsScBusPortEventIntegrationTest,RuntimeDispatchToNatsEventIntegrationTest,NatsScBusPortCommandWireFixtureTest,NatsScBusPortResponseWireFixtureTest,NatsStreamConfigurationTest"
Result: PASS
```

Full regression:

```text
mvn -q test
Tests run: 454
Failures: 0
Errors: 0
Skipped: 0
```

---

## 7. Acceptance criteria disposition

```text
AC-029-001..AC-029-052: PASS, with AC-029-040 satisfied by explicit split disposition rather than live stream application.

Key evidence:
- NatsScBusPort implements ScBusPort and publishes through ScJsonWireCodec + WireEnvelopeValidator.
- ScBusNatsArchitectureTest verifies NATS package boundaries, SC-C no direct NATS imports, serialization NATS-free, and no replacement of dispatch persistence ports.
- NatsSubjectBuilderTest verifies event, command, response, lifecycle subjects and token encodings.
- RuntimeDispatchToNatsEventIntegrationTest verifies RuntimeDispatchService -> NATS EVENT path and JDBC DispatchState/DispatchObservation persistence.
- NatsScBusPortCommandWireFixtureTest and NatsScBusPortResponseWireFixtureTest verify direct wire fixtures only.
- NatsStreamConfigurationTest verifies SCB_* stream names and subject patterns.
- This implementation report records dependency strategy, fixture fallback, JetStream split, retained debts, and no scope expansion.
```

---

## 8. Boundary confirmations

```text
[x] SC-C does not publish directly to NATS.
[x] NATS/JetStream does not replace SC-C semantic authority.
[x] NATS/JetStream does not replace DispatchStateWritePort.
[x] NATS/JetStream does not replace DispatchObservationPort.
[x] Adapter runtime is not implemented.
[x] Lifecycle admission / ACTIVE authority is not implemented.
[x] Manifest runtime is not implemented.
[x] Capability runtime is not implemented.
[x] Command runtime E2E is deferred until route assignment/manifest/lifecycle substrate exists.
```

---

## 9. Retained debts

```text
DEBT-B-NATS-H-001 - JetStream stream/consumer hardening and live stream application.
DEBT-SCD-MANIFEST-001 - Adapter Manifest runtime absent.
DEBT-SCD-CAPABILITY-001 - Capability Semantics runtime absent.
DEBT-SCD-FACTS-001 - SC-D fact family payloads absent.
DEBT-SCD-LIFECYCLE-RUNTIME-001 - lifecycle admission runtime absent.
DEBT-SCD-COMMAND-E2E-001 - productive command E2E to adapter absent.
DEBT-B-ROUTE-001 - adapter-scoped route assignment runtime absent.
```

---

## 10. v0.2.2 precision evidence

```text
DispatchOutcome.Dispatched constructor:
  src/main/java/com/sovereign/connect/bus/runtime/nats/NatsScBusPort.java
  Uses Dispatched(UUID dispatchRecordId, UUID attemptId, String topic, String partitionKey).

RuntimeDispatchToNatsEventIntegrationTest DataSource pattern:
  raw SQLiteDataSource migrated through Flyway classpath:db/migration.

Flyway V100/V101 confirmation:
  Full regression logs show migrations through V100 and V101 before constructing
  JdbcDispatchStateRepository and JdbcDispatchObservationRepository in the runtime E2E test.
```
