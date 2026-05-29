# CSA-SOV-SC-B-RUNTIME-DISPATCH-001

## Post-SDD Code Surface Audit — SC-B Runtime Dispatch Abstract Seed

```text
Document ID:  CSA-SOV-SC-B-RUNTIME-DISPATCH-001
Title:        Post-SDD Code Surface Audit — SC-B Runtime Dispatch Abstract Seed
Version:      v0.2.0-merged
Status:       Merged / Post-SDD / Pre-MIR / MIR-ready
Date:         2026-05-28
Corpus:       Sovereign Connect
Type:         CSA
Plane:        SC-B / SC-C boundary
Scope:        Code surface reconnaissance for MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
Baseline:     sovereign-connect-023.zip (post-MU-023)
Input PDR:    PDR-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
Input SDD:    SDD-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
Tests:        SC-C 240 / 0 failures / 0 errors — EIB 56 / 0 failures / 0 errors
Result:       Approvable — greenfield implementation with scoped constraints
```

---

## Changelog v0.2.0-merged

Merged review findings from:

```text
1. CSA-SOV-SC-B-RUNTIME-DISPATCH-001 v0.1.0-draft;
2. post-audit code review notes supplied after inspecting the real code surface.
```

This version:

1. Consolidates the finding that the implementation is effectively greenfield.
2. Records that no `ScCommandEnvelope`, `ScEventEnvelope`, `ScResponseEnvelope`, `ScBusPort`, `ScRoutingKey`, `ScMessageMetadata`, `correlationId` or `causationId` exists in production code.
3. Identifies `core.scledger` as the only existing bridge surface.
4. Clarifies that `OutboxEntry.semanticPayloadJson` is raw SC-C outbox JSON and not an SC-B envelope.
5. Requires `DispatchCandidateReadPort` to project from SC-C outbox records into dispatch candidates with SC-B envelope semantics.
6. Preserves the separation between SC-C outbox persistence model and SC-B runtime dispatch model.
7. Records that `DeliveryLane` currently contains only `SIGNAL` and `COMMAND` and must be extended with `EVENT` and `RESPONSE`.
8. Records that `LedgerRecordClass` already contains `RESPONSE_OUTBOX` and `DELIVERY_OBSERVATION`, allowing the abstract bus seed to align with existing ledger classification without schema change.
9. Confirms zero physical broker dependencies and requires architecture tests to preserve that property.
10. Marks the codebase as MIR-ready for `MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001`.

---

# 0. Purpose

This is the required post-SDD / pre-MIR Code Surface Audit for:

```text
SDD-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-candidate
```

It resolves the CSA targets declared in the SDD and establishes the exact implementation constraints for:

```text
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
```

This CSA does not authorize implementation directly.

It authorizes opening the MIR with the constraints declared here.

---

# 1. Executive verdict

```text
Verdict: Approvable to open MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001.
```

The implementation is greenfield for SC-B runtime dispatch.

No existing production package implements the abstract bus contract, runtime dispatch service, bus port, canonical envelopes, routing key, correlation metadata or dispatch state machine.

This is not a blocker. It is the expected condition for the abstract seed.

The MIR must create the SC-B abstract bus/runtime surface from scratch under a new package rooted at:

```text
com.sovereign.connect.bus
```

The only existing production-code extension required by the MIR is:

```text
DeliveryLane: add EVENT and RESPONSE
```

All other bus/runtime/contract components are new.

---

# 2. Mandatory implementation actions

```text
ACTION-B-RD-001  Create the new SC-B package root: com.sovereign.connect.bus
ACTION-B-RD-002  Create canonical bus contract shapes: envelopes, metadata, routing and lane
ACTION-B-RD-003  Create ScBusPort and handler interfaces
ACTION-B-RD-004  Create RuntimeDispatchService and dispatch model
ACTION-B-RD-005  Create validation utilities for envelope, routing key and correlation
ACTION-B-RD-006  Create dispatch candidate/state/observation ports and in-memory seed adapters
ACTION-B-RD-007  Extend DeliveryLane with EVENT and RESPONSE
ACTION-B-RD-008  Preserve OutboxEntry as SC-C outbox record, not DispatchCandidate
ACTION-B-RD-009  Preserve DispatchState as SC-B dispatch state, not OutboxEntryStatus alias
ACTION-B-RD-010  Preserve broker-clean architecture: no NATS, JetStream, Redis, Vert.x, HTTP/gRPC binding
```

---

# 3. Completeness level

```text
CSA-C3 — Change-impact-complete
```

## 3.1 Baseline

```text
ZIP:         sovereign-connect-023.zip
Branch:      post-MU-023 service baseline
Test state:  SC-C 240 / 0 failures / 0 errors
             EIB 56 / 0 failures / 0 errors
```

## 3.2 Inspected surfaces

```text
src/main/java/com/sovereign/connect/core/scledger/model/
  DeliveryLane.java
  LedgerEntry.java
  LedgerRecordClass.java
  OutboundKind.java
  OutboxEntry.java
  OutboxEntryStatus.java
  SemanticKind.java

src/main/java/com/sovereign/connect/core/scledger/port/
  ScLedgerWritePort.java
  ScOutboxWritePort.java

src/main/java/com/sovereign/connect/core/topology/event/
  TopologyChanged.java
  TopologyChangeKind.java

pom.xml
src/main/java/**
eib/pom.xml
eib/src/main/java/**
```

## 3.3 Search ledger

Searches covered:

```text
ScCommandEnvelope
ScEventEnvelope
ScResponseEnvelope
ScMessageMetadata
ScRoutingKey
ScBusLane
ScBusPort
ScCommandHandler
ScEventHandler
ScResponseHandler
ScDeliveryError
ActionTargetFailure
ActionRequest
correlationId
causationId
messageId
nats
jetstream
vertx
redis
```

Result:

```text
SC-B contract/runtime shapes: absent
Correlation metadata: absent
Physical broker dependencies: absent
```

---

# 4. Full code surface inventory

## 4.1 Existing SC-B-adjacent package: `core.scledger`

The `scledger` package is the only existing SC-B-adjacent surface in the baseline.

It is not an SC-B runtime package.

It is an SC-C-owned persistence/outbox/ledger package.

### 4.1.1 `DeliveryLane`

Current values:

```java
SIGNAL
COMMAND
```

Finding:

```text
DeliveryLane has no EVENT or RESPONSE lane.
```

MIR action:

```java
SIGNAL
COMMAND
EVENT
RESPONSE
```

Interpretation:

```text
SIGNAL remains for temporal/diagnostic signal delivery.
COMMAND aligns with command-lane dispatch.
EVENT and RESPONSE are required for the abstract bus seed.
```

This is the only direct extension to existing production code required by the seed.

### 4.1.2 `OutboxEntry`

Observed shape:

```java
UUID outboxEntryId
UUID ledgerEntryId
String habitatId
OutboundKind outboundKind
DeliveryLane deliveryLane
String logicalTopic
String semanticPayloadJson
String notificationTargetRef
String idempotencyKey
OutboxEntryStatus status
Instant createdAt
Instant updatedAt
String metadataJson
```

Critical finding:

```text
OutboxEntry.semanticPayloadJson is raw JSON, not an SC-B canonical envelope.
```

This is correct.

Reason:

```text
SC-C writes semantic outbox records without knowing or owning the bus runtime.
```

MIR rule:

```text
DispatchCandidateReadPort MUST project from OutboxEntry into a DispatchCandidate.
DispatchCandidate MAY carry or reference an SC-B envelope.
OutboxEntry MUST NOT be reused directly as DispatchCandidate.
```

This separation preserves:

```text
SC-C outbox authority
SC-B technical dispatch authority
broker neutrality
future serialization ADR independence
```

### 4.1.3 `OutboxEntryStatus`

Current values:

```java
PENDING
CLAIMED
DISPATCHED
DISPATCH_FAILED
RETRY_WAIT
DEAD_LETTERED
SUPPRESSED
```

Finding:

```text
OutboxEntryStatus is close to, but not identical to, the PDR/SDD dispatch lifecycle.
```

Required separation:

```text
OutboxEntryStatus = SC-C outbox persistence status
DispatchState     = SC-B runtime dispatch status
```

Reference mapping only:

```text
OutboxEntryStatus        DispatchState
────────────────────────────────────────────
PENDING               -> PENDING
CLAIMED               -> CLAIMED
DISPATCHED            -> DISPATCHED
DISPATCH_FAILED       -> DELIVERY_FAILED
RETRY_WAIT            -> RETRY_SCHEDULED
DEAD_LETTERED         -> EXHAUSTED
SUPPRESSED            -> CANCELLED_BY_SUPERSEDE, if explicit SC-C supersession exists
—                     -> DISPATCHING
```

MIR rule:

```text
Do not alias OutboxEntryStatus as DispatchState.
Do not mutate SC-C outbox status as the primary SC-B dispatch state in the seed.
Use a seed-local DispatchStateWritePort / in-memory repository unless SDD hardening later designs durable dispatch status.
```

### 4.1.4 `LedgerRecordClass`

Current values include:

```java
RESPONSE_OUTBOX
DELIVERY_OBSERVATION
```

Finding:

```text
The ledger architecture already anticipates response outbox and delivery observation families.
```

MIR implication:

```text
The abstract bus seed can align with this model without modifying ledger schema.
```

This is important because it means delivery observation does not need to be invented as a new persistence class for the seed. The seed may use in-memory observation ports while preserving a future path toward `DELIVERY_OBSERVATION` ledger records.

### 4.1.5 `OutboundKind`

Current values are temporal/runtime specific:

```java
TIMER_FIRED_SIGNAL
TEMPORAL_ACT_CANCELLED_SIGNAL
TEMPORAL_ACT_MISFIRED_DIAGNOSTIC
COMMAND
```

Finding:

```text
OutboundKind does not yet cover the full dispatch candidate taxonomy.
```

MIR constraint:

```text
Do not force OutboundKind to become the SC-B dispatch taxonomy.
Use DispatchCandidate / ScBusLane / ScRoutingKey to express bus dispatch semantics.
```

OutboundKind may be extended later, but it is not the canonical SC-B bus lane model.

### 4.1.6 Existing ports

Existing ports:

```java
ScLedgerWritePort
ScOutboxWritePort
```

Finding:

```text
Only write ports exist. No DispatchCandidateReadPort exists.
```

MIR action:

```text
Create DispatchCandidateReadPort.
```

Seed interpretation:

```text
DispatchCandidateReadPort may be implemented in-memory or as a simple adapter over existing outbox state for tests.
Production-grade outbox scanning/claiming is deferred.
```

---

## 4.2 Existing topology events

`TopologyChanged` exists as an SC-C domain event with event identity/timestamp semantics.

Finding:

```text
TopologyChanged is not wrapped in ScEventEnvelope.
It has no correlationId / causationId discipline.
```

This is acceptable.

Interpretation:

```text
TopologyChanged remains an SC-C-owned domain event/fact.
SC-B wrapping into ScEventEnvelope belongs to the abstract bus layer.
```

MIR rule:

```text
Do not modify SC-C domain event shapes merely to satisfy bus envelope requirements.
Wrap at the SC-B boundary.
```

---

# 5. Absent surface inventory

The following are absent and must be created if included in MIR scope.

## 5.1 Canonical / bus contract shapes

```text
ScCommandEnvelope<TCommand>
ScEventEnvelope<TEvent>
ScResponseEnvelope<TResponse>
ScMessageMetadata
ScRoutingKey
ScBusLane
ScResponseMetadata
ScResponseWarning
ScResponseKind
ScCanonicalEvent
```

## 5.2 Runtime bus abstractions

```text
ScBusPort
ScCommandHandler
ScEventHandler
ScResponseHandler
InMemoryScBusPort
```

## 5.3 Runtime dispatch model

```text
RuntimeDispatchService
DispatchCandidate
DispatchAttempt
DispatchState
DispatchOutcome
DispatchFailure
DispatchObservationRecord
```

## 5.4 Runtime dispatch ports

```text
DispatchCandidateReadPort
DispatchStateWritePort
DispatchObservationPort
```

## 5.5 Runtime validation utilities

```text
EnvelopeValidationService
RoutingKeyValidationService
CorrelationValidationService
```

## 5.6 Error / action payloads absent from current code

```text
ScDeliveryError
ScTimeoutEvent
ActionTargetFailure
ActionRequest
```

Seed rule:

```text
The MIR should not expand into full action execution payload semantics unless explicitly required by the SDD acceptance map.
```

---

# 6. Package placement decision

## 6.1 Required package root

The MIR should create a new package family:

```text
com.sovereign.connect.bus
```

Recommended structure:

```text
com.sovereign.connect.bus.contract
com.sovereign.connect.bus.runtime.dispatch
com.sovereign.connect.bus.runtime.dispatch.model
com.sovereign.connect.bus.runtime.port
com.sovereign.connect.bus.runtime.inmemory
com.sovereign.connect.bus.runtime.validation
```

## 6.2 Ownership of package families

```text
bus.contract
  Owns SC-B abstract contract shapes required by the seed.

bus.runtime.dispatch
  Owns dispatch orchestration.

bus.runtime.dispatch.model
  Owns DispatchCandidate, DispatchState, DispatchAttempt, DispatchOutcome,
  DispatchFailure and DispatchObservationRecord.

bus.runtime.port
  Owns ScBusPort, dispatch ports and handler interfaces.

bus.runtime.inmemory
  Owns no-broker seed adapters.

bus.runtime.validation
  Owns envelope/routing/correlation validation utilities.
```

## 6.3 Dependency rule

Allowed:

```text
RuntimeDispatchService -> ScBusPort
RuntimeDispatchService -> DispatchCandidateReadPort
RuntimeDispatchService -> DispatchStateWritePort
RuntimeDispatchService -> DispatchObservationPort
RuntimeDispatchService -> EnvelopeValidationService
RuntimeDispatchService -> RoutingKeyValidationService
RuntimeDispatchService -> CorrelationValidationService
```

Rule:

```text
Validation services are internal dispatch utilities, not domain services.
They MUST NOT import SC-C domain service classes.
```

Forbidden:

```text
bus.contract.*        -> core.*
bus.runtime.*         -> core.* domain services
core.* domain service -> bus.runtime.*
bus.*                -> NATS / JetStream / Redis / Vert.x APIs
```

Permitted only if explicitly scoped by the MIR:

```text
bus.runtime.* -> core.scledger.model.OutboxEntry
bus.runtime.* -> core.scledger.model.DeliveryLane
```

Even when permitted, those imports must preserve the projection boundary:

```text
OutboxEntry -> DispatchCandidate
DeliveryLane -> ScBusLane mapping
```

---

# 7. Dispatch projection model

## 7.1 Required projection boundary

The seed must distinguish:

```text
SC-C outbox record
  OutboxEntry
  semanticPayloadJson
  OutboxEntryStatus
  DeliveryLane

SC-B dispatch candidate
  DispatchCandidate
  ScCommandEnvelope / ScEventEnvelope / ScResponseEnvelope
  DispatchState
  ScRoutingKey
  ScMessageMetadata
```

## 7.2 Critical rule

```text
OutboxEntry is not DispatchCandidate.
semanticPayloadJson is not ScCommandEnvelope / ScEventEnvelope / ScResponseEnvelope.
OutboxEntryStatus is not DispatchState.
DeliveryLane is not ScBusLane, although it may map to it.
```

## 7.3 Seed projection behavior

The `DispatchCandidateReadPort` may project from `OutboxEntry` as follows:

```text
OutboxEntry.outboxEntryId      -> DispatchCandidate.sourceOutboxEntryId
OutboxEntry.ledgerEntryId      -> DispatchCandidate.sourceLedgerEntryId
OutboxEntry.habitatId          -> ScRoutingKey.habitatId
OutboxEntry.deliveryLane       -> ScBusLane mapping
OutboxEntry.logicalTopic       -> ScRoutingKey.topic
OutboxEntry.semanticPayloadJson -> payload carrier / serialized payload reference
OutboxEntry.idempotencyKey     -> DispatchCandidate.idempotencyKey, if used
OutboxEntry.metadataJson       -> optional dispatch metadata source
```

Correlation metadata does not currently exist in `OutboxEntry`.

Therefore, the seed must either:

```text
1. generate ScMessageMetadata at projection time; or
2. obtain it from metadataJson if present; or
3. use a deterministic seed-only metadata factory.
```

The chosen strategy must be explicit in the MIR acceptance map.

---

# 8. Required contract shapes for the seed

The MIR must create the following minimal contract shapes in `bus.contract` or equivalent.

## 8.1 `ScBusLane`

```java
public enum ScBusLane {
    COMMAND,
    EVENT,
    RESPONSE,
    INTERNAL_CONTROL
}
```

## 8.2 `ScMessageMetadata`

```java
public record ScMessageMetadata(
    UUID messageId,
    Instant emittedAt,
    UUID correlationId,
    UUID causationId,
    String topologyVersion
) {}
```

Validation:

```text
messageId MUST be non-null.
emittedAt MUST be non-null.
correlationId MUST be non-null.
For root messages: correlationId == messageId and causationId == null.
For non-root messages: causationId MUST be non-null.
```

## 8.3 `ScRoutingKey`

```java
public record ScRoutingKey(
    ScBusLane lane,
    String partitionKey,
    String topic,
    String habitatId,
    String adapterId,
    String deviceId,
    String endpointId
) {}
```

Validation:

```text
lane MUST be non-null.
topic MUST be non-blank.
partitionKey MUST be non-blank for dispatch.
endpointId MUST NOT be degraded to device-only routing without explicit downstream rule.
provider-native IDs MUST NOT be used as canonical routing authority.
```

## 8.4 Envelopes

```java
public record ScCommandEnvelope<TCommand>(
    ScMessageMetadata metadata,
    TCommand payload,
    ScRoutingKey routingKey
) {}

public record ScEventEnvelope<TEvent>(
    ScMessageMetadata metadata,
    TEvent payload,
    ScRoutingKey routingKey
) {}

public record ScResponseEnvelope<TResponse>(
    ScMessageMetadata metadata,
    TResponse payload,
    ScRoutingKey routingKey,
    ScResponseMetadata responseMetadata
) {}
```

## 8.5 Response metadata

```java
public record ScResponseMetadata(
    UUID requestMessageId,
    UUID requestId,
    ScResponseKind responseKind,
    boolean terminal,
    boolean retryable,
    String sanitizedReason,
    List<ScResponseWarning> warnings
) {}

public record ScResponseWarning(
    String source,
    String code,
    String sanitizedReason
) {}

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

---

# 9. Runtime dispatch seed model

## 9.1 `DispatchState`

Required state vocabulary:

```java
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

## 9.2 Allowed transitions

```text
PENDING -> CLAIMED
CLAIMED -> DISPATCHING
DISPATCHING -> DISPATCHED
DISPATCHING -> DELIVERY_FAILED
DELIVERY_FAILED -> RETRY_SCHEDULED
RETRY_SCHEDULED -> CLAIMED
DELIVERY_FAILED -> EXHAUSTED
PENDING -> CANCELLED_BY_SUPERSEDE, if explicit SC-C supersession evidence exists
CLAIMED -> CANCELLED_BY_SUPERSEDE, if explicit SC-C supersession evidence exists
```

## 9.3 Forbidden transitions

```text
DISPATCHED -> semantic success
EXHAUSTED -> semantic failure
DELIVERY_FAILED -> ActionFailed
CLAIMED -> adapter accepted
RETRY_SCHEDULED -> semantic retry
CANCELLED_BY_SUPERSEDE without SC-C-owned supersession evidence
```

---

# 10. ScDeliveryError / DispatchFailure boundary

## 10.1 Seed decision

For the seed, `ScDeliveryError` MAY be projected from `DispatchFailure` fields.

The seed does not need to implement the full canonical event-lane `ScDeliveryError` shape unless the MIR explicitly includes it.

## 10.2 CSA requirement for MIR package

The MIR must instruct implementation to verify whether the canonical `ScDeliveryError` shape from `PDR-SOV-SC-BUS-CONTRACT-001` is already present in the codebase.

Current audit result:

```text
ScDeliveryError is absent.
```

## 10.3 Implementation-safe seed strategy

Recommended seed strategy:

```text
DispatchFailure is internal runtime failure information.
DispatchObservationRecord exposes delivery failure diagnostics.
ScDeliveryError event-lane emission is deferred unless explicitly required.
```

This preserves scope while leaving a direct future path:

```text
DispatchFailure -> DispatchObservationRecord -> future ScDeliveryError projection
```

---

# 11. Broker dependency audit

## 11.1 Result

```text
No physical broker dependency found.
```

Absent from production code and POMs:

```text
NATS
JetStream
Redis
Vert.x
Kafka
RabbitMQ
broker-specific subject syntax
broker-specific ACK model
broker-specific consumer API
```

## 11.2 MIR rule

The MIR must preserve this property.

Forbidden in abstract bus seed:

```text
io.nats.*
JetStream APIs
Redis client APIs
Vert.x event bus APIs
Kafka clients
RabbitMQ clients
MQTT clients
physical subject names as doctrine
serialization ADR implementation
```

The seed may implement only:

```text
in-memory no-broker dispatch
synchronous or deterministic local handler invocation
local observation records
architecture tests proving absence of broker dependencies
```

---

# 12. Architecture test requirements

The MIR must include architecture tests equivalent to the following.

## 12.1 Bus contract does not import domain services

```java
@Test
void busContractPackageDoesNotImportDomainServices() {
    // bus.contract.** must not import core.** domain services
}
```

## 12.2 Bus runtime does not import SC-C domain services

```java
@Test
void busRuntimePackageDoesNotImportDomainServices() {
    // bus.runtime.** must not import core.** domain services
    // explicit exceptions, if any, must be limited to scledger model projection adapters
}
```

## 12.3 Bus package does not import physical broker APIs

```java
@Test
void busPackageDoesNotImportNatsJetStreamRedisVertxOrBrokerApis() {
    // bus.** must not import io.nats, jetstream, redis, vertx or broker clients
}
```

## 12.4 SC-C domain does not import bus runtime internals

```java
@Test
void scCDomainDoesNotImportBusRuntimeInternals() {
    // core.** must not import bus.runtime.**
}
```

## 12.5 Dispatch model separation

```java
@Test
void dispatchStateIsNotOutboxEntryStatusAlias() {
    // DispatchState must be declared under bus.runtime.dispatch.model
    // It must not be typealias/reuse of OutboxEntryStatus
}
```

## 12.6 Broker-clean POM

```java
@Test
void pomsDoNotIntroduceBrokerDependenciesForAbstractSeed() {
    // pom.xml and eib/pom.xml must not introduce NATS, JetStream, Redis, Vert.x, Kafka, RabbitMQ or MQTT clients
}
```

---

# 13. Test strategy implications for MIR

The MIR must require local tests covering at least:

```text
1. DispatchCandidate projection from outbox-like input.
2. Command envelope publication through InMemoryScBusPort.
3. Event envelope publication through InMemoryScBusPort.
4. Response envelope publication through InMemoryScBusPort.
5. Routing key validation, including endpoint-aware rejection/degradation guard.
6. Correlation validation for root and non-root metadata.
7. Dispatch state transitions and forbidden semantic-state mappings.
8. DispatchFailure / DispatchObservationRecord creation.
9. No-broker architecture boundary tests.
10. DeliveryLane EVENT/RESPONSE extension compatibility.
```

---

# 14. Retained debts

```text
DEBT-B-RD-001  Full ScDeliveryError event-lane shape is deferred unless MIR explicitly scopes it.
DEBT-B-RD-002  OutboundKind is temporal-specific; future MUs may extend outbound classification.
DEBT-B-RD-003  OutboxEntry.semanticPayloadJson remains raw JSON; future serialization ADR may change representation.
DEBT-B-RD-004  Durable outbox scanning/claiming is not implemented; seed may use in-memory dispatch state.
DEBT-B-RD-005  Correlation metadata is absent in LedgerEntry/OutboxEntry; seed creates ScMessageMetadata at dispatch boundary.
DEBT-B-RD-006  Physical NATS + JetStream binding remains deferred behind ADR/SERIALIZATION/SDD gates.
DEBT-B-RD-007  Adapter lifecycle admission is not part of this MIR.
```

---

# 15. Surface inventory matrix

| Artifact | Expected by SDD | Current state | MIR action |
|---|---:|---:|---|
| `com.sovereign.connect.bus` package | Required | Absent | Create |
| `ScCommandEnvelope` | Required | Absent | Create in `bus.contract` |
| `ScEventEnvelope` | Required | Absent | Create in `bus.contract` |
| `ScResponseEnvelope` | Required | Absent | Create in `bus.contract` |
| `ScMessageMetadata` | Required | Absent | Create in `bus.contract` |
| `ScRoutingKey` | Required | Absent | Create in `bus.contract` |
| `ScBusLane` | Required | Absent | Create in `bus.contract` |
| `ScResponseMetadata` | Required | Absent | Create in `bus.contract` |
| `ScResponseWarning` | Required | Absent | Create in `bus.contract` |
| `ScResponseKind` | Required | Absent | Create in `bus.contract` |
| `ScBusPort` | Required | Absent | Create in `bus.runtime.port` |
| `ScCommandHandler` | Required | Absent | Create in `bus.runtime.port` |
| `ScEventHandler` | Required | Absent | Create in `bus.runtime.port` |
| `ScResponseHandler` | Required | Absent | Create in `bus.runtime.port` |
| `InMemoryScBusPort` | Required | Absent | Create in `bus.runtime.inmemory` |
| `RuntimeDispatchService` | Required | Absent | Create in `bus.runtime.dispatch` |
| `DispatchCandidate` | Required | Absent | Create in `bus.runtime.dispatch.model` |
| `DispatchAttempt` | Required | Absent | Create in `bus.runtime.dispatch.model` |
| `DispatchState` | Required | Absent | Create; do not alias `OutboxEntryStatus` |
| `DispatchOutcome` | Required | Absent | Create in `bus.runtime.dispatch.model` |
| `DispatchFailure` | Required | Absent | Create in `bus.runtime.dispatch.model` |
| `DispatchObservationRecord` | Required | Absent | Create in `bus.runtime.dispatch.model` |
| `DispatchCandidateReadPort` | Required | Absent | Create in `bus.runtime.port` |
| `DispatchStateWritePort` | Required | Absent | Create in `bus.runtime.port` |
| `DispatchObservationPort` | Required | Absent | Create in `bus.runtime.port` |
| `EnvelopeValidationService` | Required | Absent | Create in `bus.runtime.validation` |
| `RoutingKeyValidationService` | Required | Absent | Create in `bus.runtime.validation` |
| `CorrelationValidationService` | Required | Absent | Create in `bus.runtime.validation` |
| `DeliveryLane.EVENT` | Required extension | Absent | Add |
| `DeliveryLane.RESPONSE` | Required extension | Absent | Add |
| `LedgerRecordClass.RESPONSE_OUTBOX` | Helpful existing support | Present | Reuse classification path later; no schema change |
| `LedgerRecordClass.DELIVERY_OBSERVATION` | Helpful existing support | Present | Reuse classification path later; no schema change |
| `NATS / JetStream / Redis / Vert.x` | Must be absent | Absent | Preserve absence |

---

# 16. MIR readiness decision

```text
Decision: MIR may be opened.
```

Candidate MIR:

```text
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
```

Candidate operational slot:

```text
MU-024, if the active INDEX/SYNC records MU-023 as the last validated slot.
```

Suggested branch:

```text
feat/sc-b-mir-024-abstract-bus-seed
```

Suggested commit message for CSA integration:

```text
docs(sc-b): merge runtime dispatch CSA for abstract bus seed
```

---

# 17. MIR scope constraints

The MIR must explicitly include:

```text
1. Greenfield SC-B abstract bus package under com.sovereign.connect.bus.
2. Canonical envelope/routing/metadata records required by the SDD.
3. ScBusPort and no-broker InMemoryScBusPort.
4. RuntimeDispatchService.
5. DispatchCandidate projection boundary.
6. DispatchState distinct from OutboxEntryStatus.
7. DeliveryLane EVENT and RESPONSE extension.
8. Validation services as internal dispatch utilities.
9. Dispatch observation model.
10. Architecture tests preserving broker-clean and SC-C/SC-B boundaries.
```

The MIR must explicitly exclude:

```text
1. NATS Core.
2. JetStream.
3. Redis Streams.
4. Vert.x Event Bus.
5. Kafka/RabbitMQ/MQTT.
6. Physical subject naming.
7. Serialization ADR implementation.
8. Adapter lifecycle admission.
9. Physical hot onboarding.
10. Product-facing API.
11. SC-D adapter implementation.
12. Semantic command retry.
13. Physical effect verification.
```

---

# 18. Final verdict

```text
CSA-SOV-SC-B-RUNTIME-DISPATCH-001 v0.2.0-merged is Approvable.
```

The codebase is clean and ready for the abstract bus seed.

The implementation is greenfield except for the additive `DeliveryLane` extension.

`scledger` is the only existing bridge, but it must remain SC-C-owned and raw-outbox-oriented.

`DispatchCandidateReadPort` is the proper projection boundary from SC-C outbox records into SC-B dispatch candidates.

`LedgerRecordClass.RESPONSE_OUTBOX` and `LedgerRecordClass.DELIVERY_OBSERVATION` confirm that the existing ledger architecture anticipates the response/delivery-observation families needed downstream.

No broker dependency exists.

The architecture tests of the MIR must preserve this property.

The next artifact is:

```text
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
```
