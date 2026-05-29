# acceptance-map — MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001

```text
Document ID:  acceptance-map-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
Version:      v0.2.0-candidate
Status:       Candidate Execution Package Acceptance Map
Date:         2026-05-29
Corpus:       Sovereign Connect
Plane:        SC-B
MU:           MU-SOV-SC-B-ABSTRACT-BUS-SEED-001
Slot:         MU-024
Repository path: docs/mir/mir-024/acceptance-map.md
```

---

# 0. Purpose

This file maps `MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate` acceptance criteria to expected implementation evidence.

Status values:

```text
PASS
FAIL
PARTIAL
NOT RUN
N/A
```

---

# 1. Acceptance criteria map

| ID | Requirement | Expected evidence | Status | Notes |
|---|---|---|---|---|
| AC-024-001 | New package root `com.sovereign.connect.bus` exists. | Source tree contains `src/main/java/com/sovereign/connect/bus`. | NOT RUN |  |
| AC-024-002 | Required sub-packages exist. | `bus.contract`, `bus.runtime.dispatch`, `bus.runtime.dispatch.model`, `bus.runtime.port`, `bus.runtime.inmemory`, `bus.runtime.validation`. | NOT RUN |  |
| AC-024-003 | No `bus.**` production code imports `core.*`. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-004 | No `bus.**` production code imports physical broker/runtime APIs. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-005 | `ScMessageMetadata` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-006 | `ScBusLane` has `COMMAND`, `EVENT`, `RESPONSE`, `INTERNAL_CONTROL`. | Enum test. | NOT RUN |  |
| AC-024-007 | `ScRoutingKey` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-008 | `ScCommandEnvelope<T>` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-009 | `ScEventEnvelope<T>` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-010 | `ScResponseEnvelope<T>` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-011 | `ScResponseWarning` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-012 | `ScResponseMetadata` exists with required fields and order. | Compile + contract test. | NOT RUN |  |
| AC-024-013 | `ScResponseKind` has all required values. | Enum test. | NOT RUN |  |
| AC-024-014 | `DispatchOutcome` is a sealed interface permitting `Dispatched`, `Failed`, `NoHandler`. | Compile + DispatchOutcomeTest. | NOT RUN |  |
| AC-024-015 | `ScBusPort` exists with command/event/response publish and handler registration methods. | Compile + bus port tests. | NOT RUN |  |
| AC-024-016 | Handler interfaces exist and are functional interfaces. | Compile + handler registration tests. | NOT RUN |  |
| AC-024-017 | `InMemoryScBusPort` implements `ScBusPort`, stores handlers by topic, validates lane/family, returns `DispatchOutcome`. | InMemoryScBusPortTest. | NOT RUN |  |
| AC-024-018 | `InMemoryScBusPort` rejects response envelope on event lane and equivalent family/lane mismatches. | Negative tests. | NOT RUN |  |
| AC-024-019 | `InMemoryScBusPort` rejects null envelope and raw null payload. | Negative tests. | NOT RUN |  |
| AC-024-020 | `DispatchState` enum has required values. | Enum test. | NOT RUN |  |
| AC-024-021 | `DispatchCandidate` exists with minimum fields. | Compile + model test. | NOT RUN |  |
| AC-024-022 | `DispatchAttempt` exists with minimum fields. | Compile + model test. | NOT RUN |  |
| AC-024-023 | `DispatchObservationRecord` exists with minimum fields. | Compile + model test. | NOT RUN |  |
| AC-024-024 | Happy path transition `PENDING → CLAIMED → DISPATCHING → DISPATCHED`. | RuntimeDispatchServiceTest. | NOT RUN |  |
| AC-024-025 | Failure path `DISPATCHING → DELIVERY_FAILED → EXHAUSTED`. | RuntimeDispatchServiceTest. | NOT RUN |  |
| AC-024-026 | Retry path `DELIVERY_FAILED → RETRY_SCHEDULED → CLAIMED`. | RuntimeDispatchServiceTest. | NOT RUN |  |
| AC-024-027 | `CANCELLED_BY_SUPERSEDE` requires explicit supersession evidence. | Negative transition test. | NOT RUN |  |
| AC-024-028 | `DISPATCHED` does not imply semantic success. | Forbidden-interpretation assertion. | NOT RUN |  |
| AC-024-029 | `EXHAUSTED` does not imply domain failure. | Forbidden-interpretation assertion. | NOT RUN |  |
| AC-024-030 | `CLAIMED` does not imply adapter acceptance. | Forbidden-interpretation assertion. | NOT RUN |  |
| AC-024-031 | `NoHandler` outcome leads to technical `DELIVERY_FAILED`, not semantic failure. | RuntimeDispatchServiceTest. | NOT RUN |  |
| AC-024-032 | Retry reuses `dispatchRecordId` and creates a new `DispatchAttempt`. | RuntimeDispatchServiceTest. | NOT RUN |  |
| AC-024-033 | `EnvelopeValidationService` rejects null envelope, null payload and null metadata. | EnvelopeValidationServiceTest. | NOT RUN |  |
| AC-024-034 | `EnvelopeValidationService` rejects missing `messageId` and missing `correlationId`. | EnvelopeValidationServiceTest. | NOT RUN |  |
| AC-024-035 | `EnvelopeValidationService` rejects non-root message without `causationId`. | EnvelopeValidationServiceTest. | NOT RUN |  |
| AC-024-036 | `EnvelopeValidationService` rejects command/event/response lane-family mismatches. | EnvelopeValidationServiceTest. | NOT RUN |  |
| AC-024-037 | `CorrelationValidationService` verifies root and non-root rules. | CorrelationValidationServiceTest. | NOT RUN |  |
| AC-024-038 | `RoutingKeyValidationService` treats deviceId/endpointId as opaque strings. | RoutingKeyValidationServiceTest. | NOT RUN |  |
| AC-024-039 | `RoutingKeyValidationService` rejects endpoint sentinels `""`, `none`, `default`. | RoutingKeyValidationServiceTest. | NOT RUN |  |
| AC-024-040 | Endpoint-aware partition key: when endpointId is non-null, partitionKey equals endpointId. | RoutingKeyValidationServiceTest. | NOT RUN |  |
| AC-024-041 | Routing validation preserves habitatId when present. | RoutingKeyValidationServiceTest. | NOT RUN |  |
| AC-024-042 | `bus.contract.**` does not import `core.**`. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-043 | `bus.contract.**` does not import `bus.runtime.**`. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-044 | `bus.runtime.**` does not import `core.**`. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-045 | `core.**` does not import `bus.runtime.**`. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-046 | No physical broker/runtime imports are introduced under `bus.**`. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-047 | No new physical binding dependency is added to `pom.xml`. | ScBusArchitectureTest / report evidence. | NOT RUN |  |
| AC-024-048 | No production class named `ScdCommand` exists. | ScBusArchitectureTest. | NOT RUN |  |
| AC-024-049 | `DeliveryLane` includes `EVENT` and `RESPONSE`; `SIGNAL` and `COMMAND` preserved. | DeliveryLaneExtensionTest. | NOT RUN |  |
| AC-024-050 | Full regression after `DeliveryLane` extension remains green; SC-C 240 baseline still passes. | STOP-5 test summary. | NOT RUN |  |
| AC-024-051 | Implementation report documents DEBT-SEED-001..004 and DEBT-B-RD-001..006. | Report section. | NOT RUN |  |
| AC-024-052 | No productive SC-D payload shapes are introduced; test stubs only. | Source scan + report. | NOT RUN |  |
| AC-024-053 | Implementation report states GATE-001 closure and remaining real-adapter readiness conditions. | Report section. | NOT RUN |  |
| AC-024-054 | All tests pass with at least +40 bus-related tests and 0 failures/errors. | Full test summary. | NOT RUN |  |


---

# 2. Required evidence summary

The implementation report must include:

```text
Total tests before / after
New test classes
Changed production files
Changed test files
Architecture test results
Broker-clean verification
DeliveryLane compatibility confirmation
STOP-5 full regression result
No production ScdCommand confirmation
GATE-001 closure statement
Retained debts
```

---

# 3. Expected test classes

Suggested test class names:

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

Names may vary if equivalent coverage is provided.

---

# 4. Pass/fail rule

The MU may be marked L4 only if:

```text
1. AC-024-001 through AC-024-054 are PASS or explicitly N/A with justification.
2. All repository tests pass.
3. No physical broker/runtime dependency is introduced.
4. No SC-C / SC-B runtime boundary violation is introduced.
5. No production ScdCommand or SC-D fact family shape is introduced.
6. The implementation report records retained debts and GATE-001 closure.
```
