# MIR-SOV-SC-B-JETSTREAM-HARDENING-001

## SC-B JetStream Hardening

```text
Document ID:  MIR-SOV-SC-B-JETSTREAM-HARDENING-001
Title:        SC-B JetStream Hardening
Version:      v0.2.0-candidate
Status:       Candidate / Pre-execution
Date:         2026-06-01
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-B
MU Slot:      MU-030
Scope:        Hardening of the JetStream portion of the SC-B NATS binding after MU-029.
```

---

## Changelog

```text
v0.2.0-candidate
  Promotes MU-030 to candidate after review.
  Requires execution-package context to include jnats 2.25.2 API signatures verified against the classpath, not inferred.
  Adds DEBT-B-NATS-009 for incompatible stream conflict behavior if not validated.
  Adds candidate-promotion criteria for jnats API-shape verification.

v0.1.0-draft
  Opens MU-030 as the SC-B JetStream hardening increment.
  Consumes SDD-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate.
  Consumes CSA-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-merged.
  Reorders downstream execution so JetStream hardening precedes live manifest-over-NATS work.
  Defines bounded implementation scope: live stream application, durable lifecycle consumer,
  lifecycle retention fixture, ack boundary tests, architecture hardening, and explicit reconnect/drain debt.
```

---

## 1. Purpose

This MIR materializes the hardening step that MU-029 deliberately deferred.

MU-029 delivered:

```text
NATS Core live binding: validated L4.
JetStream: config-only seed.
```

This MIR converts the JetStream portion from config-only into a tested broker-level hardening substrate:

```text
NatsStreamConfiguration constants
  → live JetStream stream application
  → durable SCB_LIFECYCLE_V1 consumer
  → lifecycle retention/replay fixture
  → explicit ack/consumer policy
  → hardened architecture checks
```

This MIR is an SC-B hardening increment. It is not a new SC-D seed, not Adapter Manifest runtime, and not route assignment runtime.

---

## 2. Consumed artifacts

This MIR consumes:

```text
SDD-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-candidate
CSA-SOV-SC-B-JETSTREAM-HARDENING-001 v0.2.0-merged
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v1.0.0-accepted
PDR-SOV-SC-D-ADAPTER-MANIFEST-001 v0.2.0-candidate
SDD-SOV-SC-D-ADAPTER-MANIFEST-001 v0.2.0-candidate
TCK-SOV-SC-D-CONFORMANCE-MODEL-001 v0.2.0-candidate
```

The CSA confirms that post-MU-029 code has:

```text
NatsScBusPort              — NATS Core publish/subscribe; no JetStream publish.
NatsSubjectBuilder         — subject grammar.
NatsStreamConfiguration    — five stream definitions; config-only.
NatsLifecycleChannelMapper — lifecycle subject grammar.
NatsLocalServer            — local nats-server with JetStream enabled.
jnats                      — 2.25.2.
testcontainers             — 1.20.6 available.
```

---

## 3. Industrialization decision

### D-MIR-JSH-001 — MU-030 is SC-B hardening, not another seed on top of broker seed

This MIR is opened before executing live manifest-over-NATS or route-assignment runtime work.

Rationale:

```text
AdapterManifestProposal, AdapterManifestDecision, RouteAssignment, RouteReady,
AdapterRevoked, AdapterDrain and AdapterReconnectResync cross sc.v1.*.lifecycle.>,
which belongs to SCB_LIFECYCLE_V1.
```

After MU-029, `SCB_LIFECYCLE_V1` exists as configuration only. Therefore live lifecycle/manifest transport MUST NOT be built on top of it until this hardening closes the broker substrate gap.

### D-MIR-JSH-002 — Adapter Manifest Seed is downstream or non-NATS-only

`MIR-SOV-SC-D-ADAPTER-MANIFEST-SEED-001` may proceed only if it remains non-NATS-runtime.

Any downstream MIR that transports manifest proposal, manifest decision, route assignment or route-ready over NATS / JetStream MUST depend on this MIR.

---

## 4. Implementation scope

### In scope

```text
1. Live JetStream stream application for all SCB_*_V1 streams.
2. Idempotent stream ensure behavior.
3. Observable conflict/failure for incompatible existing stream configuration.
4. Durable consumer creation for SCB_LIFECYCLE_V1.
5. Explicit lifecycle consumer policy: durable name, filter subject, ack policy,
   ack wait if applicable, max deliver if applicable, deliver policy.
6. Lifecycle retention/replay fixture using an existing lifecycle payload type.
7. Explicit NATS Core publish vs JetStream publish boundary decision.
8. JetStream ack boundary tests.
9. Architecture hardening tests stronger than aggregate string presence.
10. Reconnect/drain disposition recorded as implementation or DEBT IDs.
```

### Out of scope

```text
1. Adapter Manifest runtime.
2. AdapterManifestProposal live transport.
3. Route assignment runtime.
4. Adapter lifecycle runtime.
5. Provider execution.
6. Command admission runtime.
7. Capability registry runtime.
8. TCK executable harness.
9. SC-C direct NATS publishing.
10. Semantic retry / terminal request-state ownership.
11. Durable manifest persistence.
12. Any Flyway migration.
```

---

## 5. Baseline code facts from CSA

### F-MIR-JSH-001 — stream definitions are present but config-only

`NatsStreamConfiguration` defines:

```text
SCB_COMMANDS_V1   -> sc.v1.*.command.>
SCB_EVENTS_V1     -> sc.v1.*.event.>
SCB_RESPONSES_V1  -> sc.v1.*.response.>
SCB_LIFECYCLE_V1  -> sc.v1.*.lifecycle.>
SCB_DLQ_V1        -> sc.v1.*.dlq.>
```

Current tests validate object construction only. They do not apply streams against a live JetStream server.

### F-MIR-JSH-002 — NatsScBusPort publishes through NATS Core only

Current production path is:

```java
connection.publish(subject, wireCodec.marshal(wire));
```

No `connection.jetStream()` / `jetStream.publish()` path exists in production NATS package.

### F-MIR-JSH-003 — no durable consumer exists

No durable consumer or `ConsumerConfiguration` exists in `bus.runtime.nats`.

### F-MIR-JSH-004 — NatsLocalServer can run JetStream-enabled tests

`NatsLocalServer` starts `nats-server` with JetStream enabled. It is acceptable for this MIR if tests exercise real JetStream APIs.

### F-MIR-JSH-005 — reconnect/drain behavior is absent

No reconnect/resubscribe or drain validation exists in `NatsScBusPort`.

---

## 6. Design decisions

### D-MIR-JSH-003 — introduce a live JetStream applicator

The implementation MUST introduce a component such as:

```text
JetStreamStreamApplicator
NatsJetStreamConfigurator
NatsJetStreamManager
```

It MUST call real jnats JetStream management APIs against a live broker.

A plausible production surface is:

```java
public final class JetStreamStreamApplicator {
    public JetStreamConfigurationReport ensureStreams(Connection connection);
}
```

A combined component is also acceptable:

```java
public final class NatsJetStreamConfigurator {
    public JetStreamHardeningReport ensureAll(Connection connection);
}
```

The name is non-normative. The responsibility is normative.

### D-MIR-JSH-004 — apply all five SCB streams

The MIR MUST apply these streams against a live JetStream server:

```text
SCB_COMMANDS_V1
SCB_EVENTS_V1
SCB_RESPONSES_V1
SCB_LIFECYCLE_V1
SCB_DLQ_V1
```

A test that constructs `NatsStreamConfiguration.StreamDefinition` objects without contacting a live JetStream server fails this MIR.

### D-MIR-JSH-005 — stream application is idempotent

Repeated application of an equivalent stream configuration MUST not fail solely because the stream already exists.

If an existing stream has incompatible configuration, the implementation MUST report or fail observably. Silent overwrite is forbidden.

### D-MIR-JSH-006 — preserve stream names and subject patterns

This MIR MUST NOT change the stream names or subject patterns from `NatsStreamConfiguration` unless a separate SDD patch authorizes the change.

### D-MIR-JSH-007 — create durable lifecycle consumer

The MIR MUST create and validate a durable consumer for:

```text
SCB_LIFECYCLE_V1
```

Minimum seed policy:

```text
Durable name:  sc-b-lifecycle-consumer-v1
Filter subject: sc.v1.*.lifecycle.>
Ack policy: explicitly recorded
Ack wait: explicitly recorded if applicable
Max deliver: explicitly recorded if applicable
Deliver policy: explicitly recorded
```

The exact policy values may vary if justified by implementation constraints, but they MUST be recorded in code/tests/report.

If durable consumer creation is technically blocked, the implementation MUST report STOP and MUST NOT downgrade to ephemeral-only, stream-only, or NATS Core-only behavior.

### D-MIR-JSH-008 — lifecycle retention fixture uses existing lifecycle payload surface

Because manifest payload classes are not in scope, the lifecycle retention fixture SHOULD use an existing minimal lifecycle payload family, for example:

```text
payloadType = sc.lifecycle.adapter-announce.v1
```

The fixture must publish a message under a subject covered by:

```text
sc.v1.*.lifecycle.>
```

and verify broker-level retention/replay/consume through `SCB_LIFECYCLE_V1` and its durable consumer.

### D-MIR-JSH-009 — NATS Core vs JetStream publish boundary must be explicit

The implementation MUST choose and document one of:

```text
Option A — Core publish captured by configured JetStream stream:
  connection.publish(subject, payload)
  subject matches SCB_LIFECYCLE_V1 stream pattern
  retention is verified through JetStream consumer/replay

Option B — explicit JetStream publish:
  connection.jetStream().publish(subject, payload)
  publish ack is observed as transport confirmation
```

Both are acceptable for this MIR if tested.

In both cases:

```text
JetStream ack != semantic success.
JetStream ack != manifest acceptance.
JetStream ack != adapter activation.
JetStream ack != command terminal state.
```

### D-MIR-JSH-010 — JetStream ack remains transport-only

JetStream publish ack and consumer ack are broker-level confirmations only.

The implementation MUST NOT use JetStream ack to set or imply:

```text
command success
manifest acceptance
adapter activation
capability admission
request terminal state
provider execution success
```

### D-MIR-JSH-011 — DispatchState and DispatchObservation remain authoritative technical persistence

JetStream retention MUST NOT replace:

```text
DispatchStateWritePort
DispatchObservationPort
JdbcDispatchStateRepository
JdbcDispatchObservationRepository
```

`bus.runtime.nats` MUST NOT start writing dispatch state or dispatch observations directly.

### D-MIR-JSH-012 — strengthen architecture tests

The MIR MUST improve NATS architecture tests beyond aggregate source-string presence.

Minimum acceptable hardening:

```text
NatsScBusPort imports or directly references ScJsonWireCodec and WireEnvelopeValidator.
NatsSubjectBuilder imports or directly references ScSubjectIdTokenCodec and ScCorrelationTokenCodec.
bus.runtime.nats does not import core.**, adapter.**, or integration.**.
SC-C does not import io.nats.* or bus.runtime.nats.*.
JetStream hardening components do not import dispatch state or observation repositories.
```

File-specific import checks are acceptable for this MIR. A stronger parser/architecture tool is optional.

### D-MIR-JSH-013 — reconnect/drain disposition

The MIR MAY implement reconnect/resubscribe and/or drain behavior.

If it does not, the implementation report MUST record:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in JetStream hardening MIR.
DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.
```

Informal notes are not acceptable.

---

## 7. Required production surface

Recommended package:

```text
src/main/java/com/sovereign/connect/bus/runtime/nats/
```

Recommended classes:

```text
JetStreamStreamApplicator.java       // idempotent stream ensure against live JetStreamManagement
JetStreamConsumerApplicator.java     // durable lifecycle consumer creation
JetStreamHardeningConfiguration.java // seed consumer config constants and policy
JetStreamConfigurationReport.java    // result/evidence DTO for tests
```

Alternative names are acceptable if the responsibilities are clear.

No new SC-C package, SC-D runtime package, persistence adapter package, or Flyway migration is required.

---

## 8. Validation requirements

### V-MIR-JSH-001 — live stream application

Tests MUST prove that all five streams are applied to a real JetStream server.

### V-MIR-JSH-002 — idempotent stream ensure

Tests MUST prove repeated application succeeds when stream configuration is equivalent.

### V-MIR-JSH-003 — incompatible stream conflict

Tests SHOULD prove incompatible existing stream configuration is observed as conflict or failure.

If technically infeasible within seed hardening, it MUST be recorded as the explicit retained debt below in the implementation report:

```text
DEBT-B-NATS-009 — incompatible stream conflict behavior not validated in JetStream hardening MIR.
```

A generic note such as "stream conflict test deferred" is not sufficient.

### V-MIR-JSH-004 — durable lifecycle consumer

Tests MUST prove `SCB_LIFECYCLE_V1` durable consumer is created against a real JetStream server.

### V-MIR-JSH-005 — lifecycle retention/replay

Tests MUST publish a lifecycle subject message and verify it is retained and can be received or replayed through the durable lifecycle consumer.

### V-MIR-JSH-006 — ack boundary

Tests MUST prove that JetStream ack is treated as transport-only and does not create semantic success, manifest acceptance, adapter activation, or dispatch-state authority.

### V-MIR-JSH-007 — architecture hardening

Architecture tests MUST preserve SC-C / SC-B / SC-D boundaries and improve the weak MU-029 string-presence architecture check.

---

## 9. Test substrate

Preferred:

```text
Testcontainers NATS with JetStream enabled.
```

Accepted fallback:

```text
NatsLocalServer via jnats-server-runner with JetStream enabled.
```

Fallback is acceptable if:

```text
1. the implementation report justifies Docker unavailability or CI constraint;
2. tests exercise real JetStream APIs;
3. tests do not use mocks to satisfy live JetStream criteria.
```

Forbidden:

```text
mocked Connection / mocked JetStreamManagement / in-memory bus as live hardening proof.
```

---

## 10. Acceptance criteria

### Stream hardening

```text
AC-JSH-001 — Implementation introduces live JetStream stream application component.
AC-JSH-002 — All five SCB_*_V1 streams are applied against a live JetStream server.
AC-JSH-003 — Stream application is idempotent for equivalent configuration.
AC-JSH-004 — Incompatible existing stream configuration is observable as conflict/failure or retained as named debt if not fully testable.
AC-JSH-005 — NatsStreamConfiguration stream names and subject patterns remain unchanged.
```

### Lifecycle durable consumer

```text
AC-JSH-006 — SCB_LIFECYCLE_V1 durable consumer is created against a live JetStream server.
AC-JSH-007 — Consumer policy is explicit: durable name, filter subject, ack policy, deliver policy and relevant timeout/redelivery values.
AC-JSH-008 — Lifecycle fixture message is retained and can be consumed or replayed by the durable lifecycle consumer.
AC-JSH-009 — No silent downgrade to ephemeral-only, stream-only, or NATS Core-only lifecycle hardening occurs.
```

### Ack and authority boundary

```text
AC-JSH-010 — MIR records Core publish vs JetStream publish boundary.
AC-JSH-011 — JetStream publish ack / consumer ack is transport-only.
AC-JSH-012 — JetStream ack is not used as semantic command success, manifest acceptance, adapter activation or request terminal state.
AC-JSH-013 — DispatchStateWritePort and DispatchObservationPort remain the SC-B technical persistence boundary.
AC-JSH-014 — NatsScBusPort / JetStream hardening components do not write dispatch repositories directly.
```

### Architecture and scope

```text
AC-JSH-015 — NATS architecture tests are strengthened beyond broad string presence.
AC-JSH-016 — bus.runtime.nats does not import core.**, adapter.**, or integration.**.
AC-JSH-017 — SC-C does not import io.nats.* or bus.runtime.nats.*.
AC-JSH-018 — JetStream hardening does not introduce Adapter Manifest runtime, route assignment runtime or provider execution.
AC-JSH-019 — JetStream hardening does not introduce Flyway migrations or SC-C persistence changes.
```

### Test substrate / retained debt

```text
AC-JSH-020 — Live JetStream tests use Testcontainers NATS or justified NatsLocalServer fallback.
AC-JSH-021 — Mocked broker cannot satisfy live hardening tests.
AC-JSH-022 — Reconnect/drain behavior is implemented or retained as DEBT-B-NATS-004 / DEBT-B-NATS-005.
AC-JSH-023 — Incompatible stream conflict behavior is validated or retained as DEBT-B-NATS-009.
AC-JSH-023 — Implementation report records any fallback substrate and retained debt explicitly.
```

---

## 11. Expected test delta

Baseline:

```text
Post-MU-029: 454 tests, 0 failures, 0 errors, 0 skipped
```

Expected additions:

```text
JetStreamStreamApplicatorTest:        +6 to +8
JetStreamConsumerApplicatorTest:      +4 to +5
JetStreamLifecycleRetentionTest:      +3 to +4
JetStreamAckBoundaryTest:             +2 to +4
ScBusNatsArchitectureHardeningTest:   +3 to +5
```

Expected full regression:

```text
>= 469 tests, 0 failures, 0 errors, 0 skipped
```

---

## 12. Hard stops

```text
STOP-JSH-001 — SCB_LIFECYCLE_V1 durable consumer omitted, downgraded to ephemeral-only,
               or replaced by NATS Core-only behavior.

STOP-JSH-002 — Stream tests construct config objects but do not apply streams against a
               live JetStream server.

STOP-JSH-003 — JetStream ack is treated as command success, manifest acceptance,
               adapter activation, capability admission or request terminal state.

STOP-JSH-004 — Reconnect/drain gaps retained as informal notes instead of
               DEBT-B-NATS-004 / DEBT-B-NATS-005 implementation-report entries.

STOP-JSH-005 — Hardening expands into Adapter Manifest runtime, AdapterManifestProposal
               live transport, route assignment runtime, lifecycle runtime or provider execution.

STOP-JSH-006 — NatsStreamConfiguration stream names or subject patterns are modified
               without a separate SDD patch.

STOP-JSH-007 — DispatchStateWritePort or DispatchObservationPort is removed, bypassed
               or replaced by JetStream retention.

STOP-JSH-008 — Mocked broker or in-memory bus is used to satisfy live JetStream hardening.

STOP-JSH-009 — SC-C production code imports io.nats.* or bus.runtime.nats.*.
```

---

## 13. Risks

```text
RISK-JSH-001 — jnats JetStreamManagement API details differ from CSA sketch.
Mitigation: execution package context.md MUST include the exact jnats 2.25.2 API shape verified against the project classpath, not inferred from memory or external examples. At minimum, the context MUST record the verified signatures or invocation forms for JetStreamManagement.addStream(...), JetStreamManagement.updateStream(...), addOrUpdateConsumer or equivalent consumer-creation API, and relevant JetStreamApiException / API error handling.

RISK-JSH-002 — tests pass by starting JetStream-enabled server but not applying streams.
Mitigation: AC-JSH-002 and STOP-JSH-002.

RISK-JSH-003 — durable lifecycle consumer becomes lifecycle semantic authority.
Mitigation: D-MIR-JSH-010 and AC-JSH-011/012.

RISK-JSH-004 — Test substrate is machine-local.
Mitigation: Testcontainers preferred; NatsLocalServer fallback documented.

RISK-JSH-005 — Hardening expands into SC-D manifest runtime.
Mitigation: negative scope and STOP-JSH-005.
```

---

## 14. Retained debts

This MIR may close:

```text
DEBT-B-NATS-003 — NATS serialization architecture test uses source-string presence rather than stronger dependency/import-level verification.
```

This MIR must retain as named debt if not implemented:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in JetStream hardening MIR.
DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.
DEBT-B-NATS-009 — incompatible stream conflict behavior not validated in JetStream hardening MIR.
```

No informal retained-debt wording is acceptable for reconnect/drain or incompatible stream conflict behavior.

---

## 15. MIR acceptance checklist

```text
[ ] Uses live JetStreamManagement or equivalent real jnats API.
[ ] Applies all five SCB_*_V1 streams to a live JetStream server.
[ ] Proves idempotent stream ensure.
[ ] Proves incompatible stream conflict behavior or records DEBT-B-NATS-009 explicitly.
[ ] Creates durable SCB_LIFECYCLE_V1 consumer.
[ ] Records durable consumer policy explicitly.
[ ] Proves lifecycle subject retention/replay/consume.
[ ] Records Core publish vs JetStream publish boundary.
[ ] Proves JetStream ack is transport-only.
[ ] Preserves DispatchState / DispatchObservation authority.
[ ] Strengthens architecture tests beyond aggregate string presence.
[ ] Does not introduce manifest runtime or route assignment runtime.
[ ] Records Testcontainers or NatsLocalServer substrate decision.
[ ] Records DEBT-B-NATS-004 / DEBT-B-NATS-005 if reconnect/drain are deferred.
[ ] Full regression passes with >=469 tests.
```

---

## 16. Descent package notes

The execution package MUST include:

```text
1. Exact jnats 2.25.2 JetStreamManagement / JetStream API shape from the codebase dependency, verified against the classpath. This MUST include addStream(...), updateStream(...), consumer creation/update API, and relevant JetStreamApiException / API error handling. It MUST NOT rely on inferred signatures.
2. Exact current NatsStreamConfiguration class shape.
3. Exact NatsLocalServer usage pattern.
4. Existing NATS architecture test file paths and current weak string-presence test.
5. Paste-ready guidance only after verifying actual jnats API signatures.
6. Explicit warning that JetStream ack is not semantic success.
7. Explicit exclusion of Adapter Manifest runtime / live manifest transport.
```

### 16.1 Verified jnats API-shape requirement

The execution package context MUST verify jnats 2.25.2 APIs from the actual dependency available to the codebase before giving Codex paste-ready implementation code. The context MUST include, at minimum:

```text
JetStreamManagement.addStream(...) actual invocation form
JetStreamManagement.updateStream(...) actual invocation form
Consumer creation/update invocation form, for example addOrUpdateConsumer or the exact available alternative
JetStreamApiException / API error handling shape and the relevant existing-stream conflict signal
```

If the API cannot be verified from the classpath, the execution package MUST avoid paste-ready calls and must instruct Codex to inspect the dependency before implementation.

---

## 17. Branch and commit recommendation

```text
Branch:
  feat/sc-b-mir-030-jetstream-hardening

Commit:
  feat(sc-b): harden jetstream stream and lifecycle consumer setup
```

---

## 18. Candidate promotion criteria

This MIR may be promoted to candidate when:

```text
[ ] Required stream/consumer/ack/scope decisions are explicit.
[ ] Mandatory CSA actions are mapped to design decisions and ACs.
[ ] Hard stops include live stream application, durable lifecycle consumer, ack boundary,
    reconnect/drain debt and scope creep.
[ ] Expected test delta is realistic and bounded.
[ ] Negative scope excludes manifest/lifecycle runtime.
[ ] Execution package notes identify jnats API-shape verification as mandatory.
[ ] Candidate text requires context.md to include verified jnats 2.25.2 API signatures/invocation forms before Codex receives paste-ready implementation guidance.
```
