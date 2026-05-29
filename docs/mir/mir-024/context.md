# context — MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001

```text
Document ID:  context-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
Version:      v0.2.0-candidate
Status:       Execution Package Context / Candidate
Date:         2026-05-29
Corpus:       Sovereign Connect
Plane:        SC-B
MU:           MU-SOV-SC-B-ABSTRACT-BUS-SEED-001
Slot:         MU-024
Branch:       feat/sc-b-mir-024-abstract-bus-seed
Repository path: docs/mir/mir-024/context.md
```

---

# 0. Purpose

This context file supports implementation of `MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate`.

The implementation target is the first SC-B abstract bus seed: a no-broker, in-memory implementation of the bus contract substrate required before any NATS Core + JetStream binding.

This package is intentionally scoped to the abstract seed. It does not implement a physical broker, SC-D protocol, adapter lifecycle channel, serialization ADR, NATS subjects, JetStream streams, product-facing APIs, or real device adapters.

---

# 1. Normative chain

```text
PDR-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
  ↓
SDD-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
  ↓
CSA-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-merged
  ↓
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate
  ↓
Execution package v0.2.0-candidate
```

Additional dependencies:

```text
PDR-SOV-SC-BUS-CONTRACT-001 v0.4.6-draft
PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft
PDR-SOV-SC-COMMAND-EVENT-ENVELOPE-001 v0.1.3-draft
ADR-SOV-SC-BUS-TECH-001 v0.2.2-draft
MIR-SOV-SC-B-ENVELOPE-CONTRACT-001 v1.0.0-accepted
```

---

# 2. Verified baseline facts

Baseline: `sovereign-connect-023.zip` after MU-023.

Verified test baseline from included Surefire reports:

```text
SC-C: 240 tests / 0 failures / 0 errors / 0 skipped
EIB:   56 tests / 0 failures / 0 errors / 0 skipped
```

Verified source facts:

```text
No production package exists under com.sovereign.connect.bus.
No ScCommandEnvelope, ScEventEnvelope or ScResponseEnvelope exists.
No ScMessageMetadata, ScRoutingKey or ScBusLane exists.
No ScBusPort or command/event/response handler interfaces exist.
No RuntimeDispatchService, DispatchCandidate, DispatchAttempt, DispatchState or DispatchOutcome exists.
No production correlationId / causationId discipline exists.
No production class named ScdCommand exists.
No NATS / JetStream / Redis / Vert.x / gRPC / WebSocket bus binding exists.
```

Verified `scledger` facts:

```text
DeliveryLane currently has exactly: SIGNAL, COMMAND.
No exhaustive switch over DeliveryLane was found in production or tests.
TemporalEngineService uses the literal DeliveryLane.SIGNAL.
OutboxEntry has DeliveryLane only as a field.
OutboxEntry.semanticPayloadJson is raw SC-C JSON, not an SC-B envelope.
LedgerRecordClass already includes RESPONSE_OUTBOX and DELIVERY_OBSERVATION.
```

Implication:

```text
Adding DeliveryLane.EVENT and DeliveryLane.RESPONSE is a verified low-risk additive extension.
It remains the only permitted edit to existing production code in this MU.
```

---

# 3. Implementation objective

Create a new package root:

```text
src/main/java/com/sovereign/connect/bus
```

Do not create a new Maven module. Do not place this under `eib/`. Do not place this under `com.sovereign.connect.core`.

Create two families of types:

```text
A. Canonical contract shapes consumed by SC-B.
B. SC-B technical runtime/boundary types.
```

The seed must validate that SC-B can publish command, event and response envelopes through an abstract port without any physical broker dependency.

This MU satisfies `GATE-001` of `ADR-SOV-SC-BUS-TECH-001 v0.2.2-draft`:

```text
An abstract ScBusPort seed must exist before physical NATS Core + JetStream binding.
```

---

# 4. Package layout

Create exactly these production packages:

```text
com.sovereign.connect.bus.contract
com.sovereign.connect.bus.runtime.dispatch
com.sovereign.connect.bus.runtime.dispatch.model
com.sovereign.connect.bus.runtime.port
com.sovereign.connect.bus.runtime.inmemory
com.sovereign.connect.bus.runtime.validation
```

Dependency rules:

```text
bus.contract.** MUST NOT import core.**.
bus.contract.** MUST NOT import bus.runtime.**.
bus.runtime.** MUST NOT import core.**.
bus.runtime.** MAY import bus.contract.**.
core.** MUST NOT import bus.runtime.**.
```

Validation services are internal dispatch utilities, not domain services. They MUST NOT import `com.sovereign.connect.core.*`.

---

# 5. Contract shapes — exact code

Create the following files in `src/main/java/com/sovereign/connect/bus/contract/`.

## 5.1 `ScMessageMetadata.java`

```java
package com.sovereign.connect.bus.contract;

import java.time.Instant;
import java.util.UUID;

public record ScMessageMetadata(
        UUID messageId,
        Instant emittedAt,
        UUID correlationId,
        UUID causationId,
        String topologyVersion
) {
}
```

## 5.2 `ScBusLane.java`

```java
package com.sovereign.connect.bus.contract;

public enum ScBusLane {
    COMMAND,
    EVENT,
    RESPONSE,
    INTERNAL_CONTROL
}
```

## 5.3 `ScRoutingKey.java`

```java
package com.sovereign.connect.bus.contract;

public record ScRoutingKey(
        ScBusLane lane,
        String partitionKey,
        String topic,
        String habitatId,
        String adapterId,
        String deviceId,
        String endpointId
) {
}
```

## 5.4 `ScCommandEnvelope.java`

```java
package com.sovereign.connect.bus.contract;

public record ScCommandEnvelope<TCommand>(
        ScMessageMetadata metadata,
        TCommand payload,
        ScRoutingKey routingKey
) {
}
```

## 5.5 `ScEventEnvelope.java`

```java
package com.sovereign.connect.bus.contract;

public record ScEventEnvelope<TEvent>(
        ScMessageMetadata metadata,
        TEvent payload,
        ScRoutingKey routingKey
) {
}
```

## 5.6 `ScResponseEnvelope.java`

```java
package com.sovereign.connect.bus.contract;

public record ScResponseEnvelope<TResponse>(
        ScMessageMetadata metadata,
        TResponse payload,
        ScRoutingKey routingKey,
        ScResponseMetadata responseMetadata
) {
}
```

## 5.7 `ScResponseWarning.java`

```java
package com.sovereign.connect.bus.contract;

public record ScResponseWarning(
        String source,
        String code,
        String sanitizedReason
) {
}
```

## 5.8 `ScResponseMetadata.java`

```java
package com.sovereign.connect.bus.contract;

import java.util.List;
import java.util.UUID;

public record ScResponseMetadata(
        UUID requestMessageId,
        UUID requestId,
        ScResponseKind responseKind,
        boolean terminal,
        boolean retryable,
        String sanitizedReason,
        List<ScResponseWarning> warnings
) {
}
```

## 5.9 `ScResponseKind.java`

```java
package com.sovereign.connect.bus.contract;

public enum ScResponseKind {
    VALIDATION_FAILURE,
    EXECUTION_PROGRESS,
    EXECUTION_RESULT,
    READ_RESULT,
    REFRESH_RESULT,
    ADMISSION_PROGRESS,
    ADMISSION_RESULT,
    LIFECYCLE_PROGRESS,
    LIFECYCLE_RESULT,
    IDEMPOTENCY_STATUS
}
```

Field order is normative.

---

# 6. Runtime model — exact code

Create the following files in `src/main/java/com/sovereign/connect/bus/runtime/dispatch/model/`.

## 6.1 `DispatchState.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

public enum DispatchState {
    PENDING,
    CLAIMED,
    DISPATCHING,
    DISPATCHED,
    DELIVERY_FAILED,
    RETRY_SCHEDULED,
    EXHAUSTED,
    CANCELLED_BY_SUPERSEDE
}
```

## 6.2 `DispatchOutcome.java`

The codebase uses Java sealed interfaces for result-like types. `DispatchOutcome` must follow that style.

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

public sealed interface DispatchOutcome permits Dispatched, Failed, NoHandler {
}
```

## 6.3 `Dispatched.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import java.util.UUID;

public record Dispatched(
        UUID dispatchRecordId,
        UUID attemptId,
        String topic,
        String partitionKey
) implements DispatchOutcome {
}
```

## 6.4 `Failed.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import java.util.UUID;

public record Failed(
        UUID dispatchRecordId,
        UUID attemptId,
        String code,
        String sanitizedReason,
        boolean retryable
) implements DispatchOutcome {
}
```

## 6.5 `NoHandler.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import java.util.UUID;

public record NoHandler(
        UUID dispatchRecordId,
        UUID attemptId,
        String topic,
        String sanitizedReason
) implements DispatchOutcome {
}
```

## 6.6 `DispatchCandidate.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import com.sovereign.connect.bus.contract.ScBusLane;

import java.util.UUID;

public record DispatchCandidate(
        UUID dispatchRecordId,
        UUID sourceRecordId,
        ScBusLane lane,
        String logicalTopic,
        String partitionKey,
        UUID correlationId,
        UUID causationId,
        UUID messageId
) {
}
```

## 6.7 `DispatchAttempt.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import java.time.Instant;
import java.util.UUID;

public record DispatchAttempt(
        UUID attemptId,
        UUID dispatchRecordId,
        int attemptNumber,
        DispatchState state,
        Instant claimedAt
) {
}
```

## 6.8 `DispatchObservationRecord.java`

```java
package com.sovereign.connect.bus.runtime.dispatch.model;

import java.time.Instant;
import java.util.UUID;

public record DispatchObservationRecord(
        UUID observationId,
        UUID dispatchRecordId,
        UUID attemptId,
        DispatchState state,
        String code,
        String sanitizedReason,
        Instant observedAt
) {
}
```

---

# 7. Ports — exact code

Create the following files in `src/main/java/com/sovereign/connect/bus/runtime/port/`.

## 7.1 `ScBusPort.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;

public interface ScBusPort {
    DispatchOutcome publishCommand(ScCommandEnvelope<?> envelope);
    DispatchOutcome publishEvent(ScEventEnvelope<?> envelope);
    DispatchOutcome publishResponse(ScResponseEnvelope<?> envelope);

    void registerCommandHandler(String topicOrRoute, ScCommandHandler handler);
    void registerEventHandler(String topicOrRoute, ScEventHandler handler);
    void registerResponseHandler(String topicOrRoute, ScResponseHandler handler);
}
```

## 7.2 `ScCommandHandler.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScCommandEnvelope;

@FunctionalInterface
public interface ScCommandHandler {
    void handle(ScCommandEnvelope<?> envelope);
}
```

## 7.3 `ScEventHandler.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScEventEnvelope;

@FunctionalInterface
public interface ScEventHandler {
    void handle(ScEventEnvelope<?> envelope);
}
```

## 7.4 `ScResponseHandler.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.contract.ScResponseEnvelope;

@FunctionalInterface
public interface ScResponseHandler {
    void handle(ScResponseEnvelope<?> envelope);
}
```

## 7.5 `DispatchCandidateReadPort.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;

import java.util.List;

public interface DispatchCandidateReadPort {
    List<DispatchCandidate> pendingCandidates();
}
```

## 7.6 `DispatchStateWritePort.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;

import java.util.Optional;
import java.util.UUID;

public interface DispatchStateWritePort {
    DispatchAttempt claim(UUID dispatchRecordId);
    DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState);
    DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef);
    Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId);
}
```

## 7.7 `DispatchObservationPort.java`

```java
package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;

import java.util.List;
import java.util.UUID;

public interface DispatchObservationPort {
    void record(DispatchObservationRecord observation);
    List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId);
}
```

---

# 8. In-memory implementation requirements

Create the following classes in `src/main/java/com/sovereign/connect/bus/runtime/inmemory/`:

```text
InMemoryScBusPort
InMemoryDispatchCandidateReadRepository
InMemoryDispatchStateRepository
InMemoryDispatchObservationRepository
```

Requirements:

```text
InMemoryScBusPort:
  - synchronous only;
  - no executor;
  - no async;
  - no broker;
  - stores handlers by topic string;
  - validates envelope family against lane;
  - rejects null envelope and null payload;
  - returns Dispatched when a handler exists and handles successfully;
  - returns NoHandler when no handler exists;
  - returns Failed when validation or handler execution fails.

InMemoryDispatchCandidateReadRepository:
  - implements DispatchCandidateReadPort;
  - stores candidates in memory;
  - test-support methods may be package-private or public if needed by tests;
  - must not import scledger / core.

InMemoryDispatchStateRepository:
  - implements DispatchStateWritePort;
  - stores attempts in ConcurrentHashMap;
  - first claim creates attemptNumber 1;
  - retry claim creates next attempt with same dispatchRecordId;
  - supersede cancellation requires non-null/non-blank evidenceRef.

InMemoryDispatchObservationRepository:
  - implements DispatchObservationPort;
  - stores observations in memory;
  - returns observations by dispatchRecordId.
```

This MU does not implement a scledger-backed `DispatchCandidateReadPort`.

---

# 9. RuntimeDispatchService requirements

Create:

```text
src/main/java/com/sovereign/connect/bus/runtime/dispatch/RuntimeDispatchService.java
```

Allowed dependencies:

```text
RuntimeDispatchService -> ScBusPort
RuntimeDispatchService -> DispatchCandidateReadPort
RuntimeDispatchService -> DispatchStateWritePort
RuntimeDispatchService -> DispatchObservationPort
RuntimeDispatchService -> EnvelopeValidationService
RuntimeDispatchService -> RoutingKeyValidationService
RuntimeDispatchService -> CorrelationValidationService
```

Forbidden dependencies:

```text
RuntimeDispatchService MUST NOT import com.sovereign.connect.core.*.
RuntimeDispatchService MUST NOT import NATS, JetStream, Redis, Vert.x, gRPC, HTTP or WebSocket binding APIs.
RuntimeDispatchService MUST NOT mutate OutboxEntryStatus.
```

State machine:

```text
PENDING → CLAIMED → DISPATCHING → DISPATCHED
DISPATCHING → DELIVERY_FAILED → EXHAUSTED
DELIVERY_FAILED → RETRY_SCHEDULED → CLAIMED
{PENDING, CLAIMED, RETRY_SCHEDULED} → CANCELLED_BY_SUPERSEDE requires evidenceRef
```

Hard semantic boundaries:

```text
DISPATCHED != semantic success.
EXHAUSTED != domain failure.
CLAIMED != adapter acceptance.
NoHandler outcome => DELIVERY_FAILED transition.
retry reuses dispatchRecordId and creates new DispatchAttempt.
```

---

# 10. Validation services

Create these in `src/main/java/com/sovereign/connect/bus/runtime/validation/`:

```text
EnvelopeValidationService
RoutingKeyValidationService
CorrelationValidationService
```

Validation services are internal dispatch utilities. They are not domain services.

Rules:

```text
EnvelopeValidationService:
  - rejects null envelope;
  - rejects null payload;
  - rejects null metadata;
  - rejects missing messageId;
  - rejects missing correlationId;
  - rejects non-root message without causationId;
  - rejects COMMAND lane with ScEventEnvelope;
  - rejects RESPONSE lane with ScEventEnvelope;
  - rejects EVENT lane with ScResponseEnvelope.

CorrelationValidationService:
  - root message: correlationId == messageId && causationId == null;
  - non-root message: correlationId != null && causationId != null.

RoutingKeyValidationService:
  - treats deviceId and endpointId as opaque strings;
  - never parses/splits deviceId or endpointId on '.', '/', ':';
  - rejects endpointId values: "", "none", "default";
  - if endpointId != null, partitionKey must equal endpointId;
  - preserves habitatId when present.
```

---

# 11. Existing code edit: DeliveryLane

Modify only:

```text
src/main/java/com/sovereign/connect/core/scledger/model/DeliveryLane.java
```

Expected final enum:

```java
public enum DeliveryLane {
    SIGNAL,
    COMMAND,
    EVENT,
    RESPONSE
}
```

Rules:

```text
SIGNAL and COMMAND are preserved unchanged.
EVENT and RESPONSE are additive.
No other core file may be modified for this extension.
If full regression fails after this change, stop and report.
```

---

# 12. Architecture test pattern

The codebase uses file-walk + `Files.readString` + AssertJ for architecture tests, not ArchUnit.

Create:

```text
src/test/java/com/sovereign/connect/bus/ScBusArchitectureTest.java
```

Normative checks:

```text
bus.contract.** does not import com.sovereign.connect.core.
bus.contract.** does not import com.sovereign.connect.bus.runtime.
bus.runtime.** does not import com.sovereign.connect.core.
core.** does not import com.sovereign.connect.bus.runtime.
bus.** does not import io.nats, jetstream, redis, lettuce, vertx, grpc, websocket physical binding APIs.
No production class named ScdCommand exists.
No new Maven dependency is added for NATS, JetStream, Redis, Vert.x, gRPC, HTTP bus binding, WebSocket or equivalent physical bus/runtime binding.
```

Use `Path.of("pom.xml")` when scanning `pom.xml`, matching the existing repository pattern.

Optional shell checks may be used during implementation, but the normative verification must be the Java architecture test.

---

# 13. Test payloads

No production SC-D payload shape may be introduced by this MU.

Allowed test-only payloads under `src/test/java`:

```text
TestCommandPayload
TestEventPayload
TestResponsePayload
ScdCommandStub
```

Hard rule:

```text
No production class named ScdCommand.
No production DeviceDiscoveredFact / EndpointDiscoveredFact / DeviceStateObservedFact / EndpointStateObservedFact / DeviceHealthObservedFact / EndpointHealthObservedFact.
```

---

# 14. Required test suites

Minimum expected test delta: +40 tests.

Suggested classes:

```text
ScBusContractTest
InMemoryScBusPortTest
EnvelopeValidationServiceTest
CorrelationValidationServiceTest
RoutingKeyValidationServiceTest
RuntimeDispatchServiceTest
DispatchOutcomeTest
DeliveryLaneExtensionTest
ScBusArchitectureTest
ScDProtocolBoundarySeedTest
```

Coverage areas:

```text
1. contract shape existence and field behavior;
2. lane enum values;
3. handler registration and topic dispatch;
4. null rejection;
5. lane/family mismatch rejection;
6. correlation root/non-root validation;
7. routing key opacity and endpoint-aware partitioning;
8. dispatch lifecycle transitions;
9. forbidden semantic interpretation assertions;
10. sealed DispatchOutcome behavior;
11. DeliveryLane compatibility;
12. architecture boundaries;
13. SC-D protocol readiness debts.
```

---

# 15. Hard stops

Report instead of improvising if:

```text
STOP-1 — mvn -q compile fails after contract shapes.
STOP-2 — implementing a fix requires importing com.sovereign.connect.core.* into bus.**.
STOP-3 — implementing a fix requires a new Maven module or new test dependency.
STOP-4 — any test passes only by treating DISPATCHED as semantic success or EXHAUSTED as domain failure.
STOP-5 — DeliveryLane extension breaks any SC-C test; this means a hidden exhaustive switch or assumption exists.
STOP-6 — any broker library appears necessary; it is not necessary for this MU.
STOP-7 — a production ScdCommand or SC-D fact shape seems necessary; use test stubs only and record retained debt.
```

---

# 16. Retained debts to preserve

```text
DEBT-SEED-001 — ScdCommand canonical shape deferred to SC-D protocol artifacts.
DEBT-SEED-002 — Discovery/state/health fact family deferred to SC-D discovery/fact artifacts.
DEBT-SEED-003 — Adapter-scoped routing assignment deferred to PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001.
DEBT-SEED-004 — Hot onboarding protocol deferred to RFC admission + lifecycle channel artifacts.
DEBT-B-RD-001 — ScDeliveryError canonical event-lane shape deferred to hardening MU.
DEBT-B-RD-002 — OutboundKind covers temporal domain only; extension deferred.
DEBT-B-RD-003 — OutboxEntry.semanticPayloadJson carries raw JSON; envelope migration deferred.
DEBT-B-RD-004 — No production outbox read port; DispatchCandidateReadPort seed is in-memory.
DEBT-B-RD-005 — Claim state is in-memory only; production hardening deferred.
DEBT-B-RD-006 — scledger-backed DispatchCandidateReadPort adapter deferred.
```

---

# 17. Required implementation report update

`docs/mir/mir-024/implementation-report.md` must include:

```text
branch;
commit hash;
changed files;
new packages;
test command and summary;
bus test delta;
SC-C 240-test baseline confirmation;
AC-024-001 through AC-024-054 PASS/FAIL map;
architecture test results;
broker-clean verification;
DeliveryLane regression gate result;
GATE-001 closure statement;
retained debts;
next recommended artifacts.
```
