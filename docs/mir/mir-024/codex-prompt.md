# Codex Prompt — MU-024 SC-B Abstract Bus Seed

```text
Document ID:  codex-prompt-MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001
Version:      v0.2.0-candidate
Status:       Candidate Execution Prompt
Date:         2026-05-29
Corpus:       Sovereign Connect
Plane:        SC-B
MU:           MU-SOV-SC-B-ABSTRACT-BUS-SEED-001
Slot:         MU-024
Repository path: docs/mir/mir-024/codex-prompt.md
```

Implement `MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate` in the `sovereign-connect` Maven module.

Read in this order before writing code:

```text
docs/mir/mir-024/context.md
docs/mir/mir-024/acceptance-map.md
docs/mir/mir-024/code-surface-audit.md
```

This is an abstract, no-broker SC-B seed. It satisfies GATE-001 of `ADR-SOV-SC-BUS-TECH-001`. No physical broker is introduced.

---

## Branch and commit

```text
Branch: feat/sc-b-mir-024-abstract-bus-seed
Commit: feat(sc-b): add abstract bus seed runtime dispatch
```

---

## Critical constraints verified against the baseline

```text
1. New package root: src/main/java/com/sovereign/connect/bus
   NOT a new Maven module. NOT under eib/. NOT under core/.

2. DeliveryLane currently has exactly { SIGNAL, COMMAND }.
   No exhaustive switch over it exists.
   Adding EVENT, RESPONSE is safe.
   This is the ONLY change to existing production code.

3. The codebase uses file-walk + Files.readString + AssertJ for architecture tests,
   NOT ArchUnit. Match that pattern.

4. The codebase prefers sealed interfaces + pattern matching.
   DispatchOutcome MUST be a sealed interface.

5. No production class named ScdCommand.
   Test payloads only, in src/test/java.

6. No new dependency may be added for NATS, JetStream, Redis, Vert.x, gRPC,
   HTTP bus binding, WebSocket or any physical bus/runtime binding.
```

---

## Step 1 — Contract shapes

Create the 9 contract types in `com.sovereign.connect.bus.contract` using the exact code in `context.md §5`. Field order is normative.

STOP-1:

```bash
mvn -q compile
```

Normative verification is the architecture test added in Step 8. Optional shell checks may be used, but do not rely on shell tooling as the only verification.

---

## Step 2 — Runtime model

Create model types in `com.sovereign.connect.bus.runtime.dispatch.model` using `context.md §6`.

`DispatchOutcome` MUST be a sealed interface with these permitted records:

```text
Dispatched
Failed
NoHandler
```

---

## Step 3 — Ports

Create port interfaces in `com.sovereign.connect.bus.runtime.port` using `context.md §7`:

```text
ScBusPort
ScCommandHandler
ScEventHandler
ScResponseHandler
DispatchCandidateReadPort
DispatchStateWritePort
DispatchObservationPort
```

---

## Step 4 — In-memory implementations

Create four in-memory classes in `com.sovereign.connect.bus.runtime.inmemory` per `context.md §8`.

```text
InMemoryScBusPort
InMemoryDispatchCandidateReadRepository
InMemoryDispatchStateRepository
InMemoryDispatchObservationRepository
```

Rules:

```text
No executor.
No async.
No broker.
No SC-C imports.
Synchronous local dispatch only.
```

---

## Step 5 — Validation services

Create these services in `com.sovereign.connect.bus.runtime.validation` per `context.md §10`:

```text
EnvelopeValidationService
RoutingKeyValidationService
CorrelationValidationService
```

They MUST NOT import `com.sovereign.connect.core.*`.

Key rules:

```text
CorrelationValidationService:
  root -> correlationId == messageId && causationId == null
  non-root -> correlationId != null && causationId != null

RoutingKeyValidationService:
  deviceId/endpointId opaque;
  no '.', '/', ':' parsing;
  reject endpointId in {"", "none", "default"};
  endpointId != null => partitionKey == endpointId

EnvelopeValidationService:
  reject null envelope/payload/metadata/messageId/correlationId;
  reject lane/family mismatches
```

---

## Step 6 — RuntimeDispatchService

Create `RuntimeDispatchService` in `com.sovereign.connect.bus.runtime.dispatch` per `context.md §9`.

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

State machine:

```text
PENDING → CLAIMED → DISPATCHING → DISPATCHED
DISPATCHING → DELIVERY_FAILED → EXHAUSTED
DELIVERY_FAILED → RETRY_SCHEDULED → CLAIMED
{PENDING, CLAIMED, RETRY_SCHEDULED} → CANCELLED_BY_SUPERSEDE requires evidenceRef
```

Hard rules tests must verify:

```text
DISPATCHED != semantic success
EXHAUSTED != domain failure
CLAIMED != adapter acceptance
NoHandler outcome -> DELIVERY_FAILED transition
supersede with null evidenceRef -> IllegalArgumentException
retry reuses dispatchRecordId, creates new DispatchAttempt
no SC-C OutboxEntryStatus mutation
```

STOP-3:

```bash
mvn -q test -Dtest="RuntimeDispatchServiceTest,InMemoryScBusPortTest"
```

---

## Step 7 — DeliveryLane extension

Modify only:

```text
src/main/java/com/sovereign/connect/core/scledger/model/DeliveryLane.java
```

Expected:

```java
public enum DeliveryLane {
    SIGNAL,
    COMMAND,
    EVENT,
    RESPONSE
}
```

Do not touch any other core file for this extension.

---

## Step 8 — Architecture test

Create `ScBusArchitectureTest` in:

```text
src/test/java/com/sovereign/connect/bus/
```

Use file-walk + `Files.readString` + AssertJ, matching the repository pattern. Do not add ArchUnit.

It MUST enforce:

```text
bus.contract.** does not import core.**
bus.contract.** does not import bus.runtime.**
bus.runtime.** does not import core.**
core.** does not import bus.runtime.**
bus.** does not import physical broker/runtime APIs
no production class named ScdCommand
no new physical binding dependency in pom.xml
```

---

## Step 9 — Test suites

Create tests covering the areas in `context.md §14` and `acceptance-map.md`.

Minimum: +40 tests.

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

Use only test payloads under `src/test/java`:

```text
TestCommandPayload
TestEventPayload
TestResponsePayload
ScdCommandStub
```

No production `ScdCommand`.

STOP-4:

```bash
mvn -q test -Dtest="ScBusContractTest,InMemoryScBusPortTest,EnvelopeValidationServiceTest,CorrelationValidationServiceTest,RoutingKeyValidationServiceTest,RuntimeDispatchServiceTest,DispatchOutcomeTest,DeliveryLaneExtensionTest,ScBusArchitectureTest,ScDProtocolBoundarySeedTest"
```

Expected: ≥40 tests, 0 failures, 0 errors.

---

## Step 10 — Full regression

Run:

```bash
mvn -q test
```

STOP-5:

```text
Entire sovereign-connect module must remain green.
SC-C 240 baseline tests must still pass after DeliveryLane extension.
```

If any SC-C test fails, the DeliveryLane change broke a hidden assumption. Stop and report. Do not silence it.

---

## Step 11 — Implementation report

Update:

```text
docs/mir/mir-024/implementation-report.md
```

Include:

```text
Branch, commit hash, changed files, new packages
Test command + summary
Bus test delta + SC-C 240 confirmation
AC-024-001..054 PASS/FAIL map
Architecture test results
Broker-clean verification
DeliveryLane regression gate result
GATE-001 closure statement
Retained debts: DEBT-SEED-001..004, DEBT-B-RD-001..006
Next recommended artifacts: ADR-SOV-SC-SERIALIZATION-001,
  PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001
```

---

## Hard stops

Report instead of improvising if:

```text
- A fix requires importing com.sovereign.connect.core.* into bus.**
- A fix requires a new Maven module or new test dependency
- DeliveryLane extension breaks an SC-C test
- Any test passes only by treating DISPATCHED as success or EXHAUSTED as failure
- A broker library seems necessary
- A production ScdCommand or SC-D fact shape seems necessary
```

Do not claim success without the STOP-5 full-module test summary showing SC-C 240 still green.
