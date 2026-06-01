# SDD-SOV-SC-B-JETSTREAM-HARDENING-001

## SC-B JetStream Hardening Design

```text
Document ID:  SDD-SOV-SC-B-JETSTREAM-HARDENING-001
Title:        SC-B JetStream Hardening Design
Version:      v0.2.0-candidate
Status:       Candidate
Date:         2026-06-01
Corpus:       Sovereign Connect
Type:         SDD
Plane:        SC-B
Scope:        Hardening design for the JetStream portion of the SC-B NATS binding after MU-029.
```

---

## Changelog

```text
v0.2.0-candidate
  Promotes the SDD to candidate.
  Strengthens SCB_LIFECYCLE_V1 durable consumer from SHOULD to MUST.
  Adds STOP semantics for silent degradation of lifecycle durable consumer creation.
  Requires retained reconnect/drain debt to be recorded with explicit DEBT IDs in the implementation report.

v0.1.0-draft
  Initial draft.
  Opens the hardening track for MU-029 after NATS Core live binding seed closure.
  Records that MU-029 left JetStream in config-only disposition.
  Defines stream application, consumer semantics, lifecycle retention, retry/ack boundaries,
  reconnect/drain behavior, CI substrate expectations and architecture-hardening targets.
  Reorders downstream so live manifest-over-NATS work must not proceed before JetStream hardening.
```

---

## 1. Purpose

This SDD defines the hardening design for the JetStream portion of the SC-B NATS binding.

MU-029 introduced the first physical SC-B binding:

```text
NATS Core live binding: validated seed.
JetStream: config-only seed.
```

This SDD exists to prevent downstream SC-D work from building live protocol flows on top of a broker configuration that exists only as Java constants and unit-level stream definitions.

This SDD does not implement code.

It defines the design boundary for:

```text
MIR-SOV-SC-B-JETSTREAM-HARDENING-001
```

---

## 2. Consumed artifacts

This SDD consumes:

```text
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 v0.2.0-candidate
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001 v1.0.0-accepted
PDR-SOV-SC-D-ADAPTER-MANIFEST-001 v0.2.0-candidate
PDR-SOV-SC-D-CAPABILITY-SEMANTICS-001 v0.2.0-candidate
SDD-SOV-SC-D-ADAPTER-MANIFEST-001 v0.2.0-candidate
TCK-SOV-SC-D-CONFORMANCE-MODEL-001 v0.2.0-candidate
```

It assumes the post-MU-029 code surface contains:

```text
NatsScBusPort
NatsSubjectBuilder
NatsLifecycleChannelMapper
NatsStreamConfiguration
NatsLocalServer
SCB_COMMANDS_V1
SCB_EVENTS_V1
SCB_RESPONSES_V1
SCB_LIFECYCLE_V1
SCB_DLQ_V1
```

---

## 3. Thesis

```text
NATS Core validates that SC-B can publish and subscribe.
JetStream hardening validates that SC-B can retain, replay and consume technical delivery streams with explicit broker semantics.
```

JetStream hardening MUST NOT change SC-C semantic authority.

Canonical rule:

```text
JetStream durability is transport durability.
SC-C remains semantic authority.
SC-B remains transport and correlation authority.
```

---

## 4. Baseline from MU-029

MU-029 validated:

```text
NATS Core live publish/subscribe for EVENT / TIMER_FIRED_SIGNAL.
COMMAND and RESPONSE direct wire fixtures.
NatsSubjectBuilder subject grammar.
NatsLifecycleChannelMapper subject grammar.
NatsStreamConfiguration stream definitions.
NatsLocalServer local test substrate with JetStream enabled.
```

MU-029 did not validate:

```text
JetStream stream creation against a live server.
JetStream consumer creation against a live server.
JetStream publish acknowledgements.
JetStream replay behavior.
JetStream durable consumer semantics.
Lifecycle retention over SCB_LIFECYCLE_V1.
DLQ behavior.
Reconnect / drain / resubscribe behavior under JetStream.
CI-grade broker substrate policy.
```

---

## 5. Industrialization sequencing decision

### D-SDD-JSH-001 — JetStream hardening before live SC-D manifest transport

Any MIR that transports one of the following over NATS / JetStream MUST depend on JetStream hardening:

```text
AdapterManifestProposal
AdapterManifestDecision
RouteAssignment
RouteReady
AdapterRevoked
AdapterDrain
AdapterReconnectResync
CapabilityManifestRevision
```

Rationale:

```text
These flows cross sc.v1.*.lifecycle.>, which belongs to SCB_LIFECYCLE_V1.
SCB_LIFECYCLE_V1 exists after MU-029 only as config-only stream definition.
```

Therefore:

```text
MIR-SOV-SC-D-ADAPTER-MANIFEST-SEED-001 MAY proceed only while it remains non-NATS-runtime.
If MIR-030 includes live manifest transport over NATS/JetStream, JetStream hardening MUST precede it.
```

---

## 6. Stream hardening model

### D-SDD-JSH-002 — stream definitions must be applied to a live JetStream server

Hardening MUST include live stream creation or idempotent stream ensure logic against a NATS server with JetStream enabled.

The following stream names are the seed baseline:

```text
SCB_COMMANDS_V1
SCB_EVENTS_V1
SCB_RESPONSES_V1
SCB_LIFECYCLE_V1
SCB_DLQ_V1
```

A test that only instantiates `NatsStreamConfiguration.StreamDefinition` objects is not sufficient for hardening.

### D-SDD-JSH-003 — stream application must be idempotent

Applying stream configuration repeatedly MUST NOT fail solely because the stream already exists with the expected configuration.

If an existing stream has incompatible configuration, the result MUST be observable as a technical configuration conflict.

### D-SDD-JSH-004 — subject patterns remain logical binding patterns, not semantic ownership

Stream subject patterns are transport subscription / retention declarations.

They do not make JetStream the semantic owner of:

```text
command success
request terminal state
manifest acceptance
route assignment acceptance
adapter activation
capability admission
```

---

## 7. Consumer hardening model

### D-SDD-JSH-005 — consumers are technical delivery cursors

JetStream consumers MUST be treated as technical delivery cursors.

They MUST NOT become:

```text
semantic retry state
canonical command state
manifest acceptance state
adapter lifecycle state
command verification state
```

### D-SDD-JSH-006 — seed hardening must define at least one durable lifecycle consumer

The hardening MIR MUST define and validate at least one durable consumer for:

```text
SCB_LIFECYCLE_V1
```

Purpose:

```text
prove that lifecycle/manifest subjects can be retained and replayed by broker infrastructure without making the broker semantically authoritative.
```

This is a hardening requirement, not an optional enhancement.

If durable consumer creation for `SCB_LIFECYCLE_V1` is technically blocked, the MIR MUST report a STOP condition and MUST NOT silently downgrade lifecycle hardening to stream-only configuration, ephemeral-only consumers, or NATS Core-only behavior.

The implementation report MUST record evidence that the durable lifecycle consumer is created against a live JetStream server, or record the STOP condition that prevented validation.

### D-SDD-JSH-007 — ack policy must be explicit

The hardening MIR MUST specify the selected seed ack policy.

Minimum acceptable decisions:

```text
AckPolicy: explicit or none, but explicitly recorded.
AckWait: explicitly recorded if manual/explicit ack is selected.
MaxDeliver: explicitly recorded if retry/re-delivery is enabled.
DeliverPolicy: explicitly recorded.
```

### D-SDD-JSH-008 — JetStream ack is not semantic success

A JetStream publish ack or consumer ack means only:

```text
transport-level store/delivery confirmation
```

It does not mean:

```text
provider executed the command
adapter accepted operational work
SC-C accepted a manifest
SC-C activated an adapter
SC-C verified final device state
```

---

## 8. Lifecycle stream hardening

### D-SDD-JSH-009 — SCB_LIFECYCLE_V1 must cover manifest/lifecycle flows

The lifecycle stream must be capable of retaining seed messages under:

```text
sc.v1.*.lifecycle.>
```

Seed hardening SHOULD include at least one retained lifecycle message using the Adapter Manifest proposal family once the payload fixture exists, or a minimal lifecycle fixture if manifest runtime is intentionally deferred.

Acceptable seed messages:

```text
AdapterManifestProposal EVENT fixture
AdapterRouteAssignment fixture
AdapterLifecycleAnnounce fixture
```

If the MIR does not include AdapterManifestProposal because MIR-030 has not yet introduced the payload, it MUST record this as an explicit sequencing limitation.

---

## 9. Reconnect / drain / failure behavior

### D-SDD-JSH-010 — reconnect behavior must be observable

The hardening MIR SHOULD include tests or explicit design fixtures for:

```text
connection close
reconnect
publish after reconnect
subscription re-registration or documented non-support
```

If reconnect is not implemented, it MUST be named as retained hardening debt in the implementation report using an explicit debt identifier:

```text
DEBT-B-NATS-004 — reconnect / resubscribe behavior not validated in hardening MIR.
```

If drain behavior is not validated beyond semantic-boundary assertions, the implementation report MUST also record:

```text
DEBT-B-NATS-005 — drain behavior not validated under live JetStream hardening conditions.
```

Informal notes are not sufficient. Retained reconnect/drain gaps MUST be machine-searchable governance debt entries.

### D-SDD-JSH-011 — drain behavior must not imply semantic completion

Drain confirms transport-level handling of pending messages/subscriptions.

Drain MUST NOT be interpreted as:

```text
all commands succeeded
all manifests accepted
all adapters cleanly deactivated
```

---

## 10. Test substrate policy

### D-SDD-JSH-012 — preferred hardening substrate is Testcontainers NATS with JetStream enabled

Hardening SHOULD use Testcontainers NATS with JetStream enabled when CI can provide Docker.

### D-SDD-JSH-013 — NatsLocalServer is acceptable only with explicit justification

`NatsLocalServer` may remain an accepted fallback if:

```text
Docker is unavailable;
NATS_SERVER_EXECUTABLE is documented or the binary is cached;
the test still starts a real nats-server with JetStream enabled;
the implementation report records the fallback.
```

### D-SDD-JSH-014 — no mocked broker for JetStream hardening acceptance

A mocked `Connection`, mocked `JetStreamManagement`, or in-memory bus cannot satisfy live JetStream hardening acceptance criteria.

---

## 11. Architecture hardening

### D-SDD-JSH-015 — NATS package architecture tests must be stronger than string presence

MU-029 accepted seed-level architecture checks.

Hardening SHOULD improve architecture tests so that use of serialization utilities and wire validation is verified through explicit imports, typed references or more targeted source checks.

Retained debt to close:

```text
DEBT-B-NATS-003 — NATS serialization architecture test uses source-string presence rather than stronger dependency/import-level verification.
```

### D-SDD-JSH-016 — SC-C and SC-D must not import bus.runtime.nats

Hardening MUST preserve:

```text
SC-C does not publish directly to NATS.
SC-D protocol documents do not become NATS implementation code.
bus.runtime.nats remains an SC-B physical binding package.
```

---

## 12. Negative scope

This SDD does not implement:

```text
Adapter Manifest runtime
AdapterManifestProposal live transport
full adapter lifecycle runtime
SC-D adapter implementation
capability registry runtime
command semantic retry
terminal request-state ownership
provider execution
SC-C direct NATS publishing
production DLQ policy beyond seed technical structure
```

---

## 13. Design risks

```text
RISK-JSH-001 — JetStream ack leaks into semantic success.
Mitigation: explicit ack boundary and architecture tests / acceptance-map checks.

RISK-JSH-002 — lifecycle stream hardening guesses flows before Manifest semantics.
Mitigation: consume Adapter Manifest PDR/SDD and keep manifest-specific transport downstream unless fixture is available.

RISK-JSH-003 — test substrate becomes machine-local and non-CI reproducible.
Mitigation: Testcontainers preferred, NatsLocalServer fallback documented.

RISK-JSH-004 — stream creation tests only instantiate configuration objects.
Mitigation: require live JetStream server interaction.

RISK-JSH-005 — hardening expands into adapter lifecycle runtime.
Mitigation: restrict to broker retention/consumer semantics.
```

---

## 14. Hard stops

```text
STOP-JSH-001 — SCB_LIFECYCLE_V1 durable consumer is omitted, downgraded to ephemeral-only, or replaced by NATS Core-only behavior without reporting STOP.
STOP-JSH-002 — Stream application tests instantiate configuration objects but do not apply them against a live JetStream server.
STOP-JSH-003 — JetStream ack is treated as command success, manifest acceptance, adapter activation, or any other semantic terminal state.
STOP-JSH-004 — Reconnect/drain gaps are retained as informal notes instead of DEBT-B-NATS-004 / DEBT-B-NATS-005 implementation-report entries.
STOP-JSH-005 — Hardening expands into adapter manifest runtime, route assignment runtime, or provider execution.
```

---

## 15. Acceptance criteria

```text
AC-JSH-001 — The hardening design records MU-029 JetStream as config-only baseline.
AC-JSH-002 — The hardening design requires applying stream definitions against a live JetStream server.
AC-JSH-003 — SCB_LIFECYCLE_V1 is explicitly included in the hardening scope.
AC-JSH-004 — Stream application is idempotent or conflict-observable.
AC-JSH-005 — At least one durable SCB_LIFECYCLE_V1 consumer is created and validated against a live JetStream server; if technically blocked, the MIR reports STOP rather than deferring silently.
AC-JSH-006 — Ack policy / ack wait / deliver policy are explicit for any consumer created by the MIR.
AC-JSH-007 — JetStream ack is explicitly non-semantic.
AC-JSH-008 — Testcontainers NATS is preferred; NatsLocalServer fallback requires report justification.
AC-JSH-009 — No mock broker can satisfy live JetStream hardening acceptance.
AC-JSH-010 — Architecture tests are strengthened beyond broad string presence where feasible.
AC-JSH-011 — SC-C does not import bus.runtime.nats or io.nats.
AC-JSH-012 — SC-D manifest runtime remains out of scope for this hardening SDD.
AC-JSH-013 — Any downstream live manifest-over-NATS MIR depends on JetStream hardening.
AC-JSH-014 — Any retained reconnect or drain gap is recorded in the implementation report as DEBT-B-NATS-004 and/or DEBT-B-NATS-005.
```

---

## 16. CSA targets

The CSA before `MIR-SOV-SC-B-JETSTREAM-HARDENING-001` MUST inspect:

```text
1. NatsStreamConfiguration stream names and subjects.
2. Whether jnats exposes JetStreamManagement in the current dependency version.
3. Whether any stream application helper already exists.
4. Whether tests currently use NatsLocalServer or Testcontainers.
5. Whether NatsLocalServer starts with JetStream enabled.
6. Whether lifecycle subjects are covered by NatsLifecycleChannelMapper.
7. Whether bus.runtime.nats imports SC-C or SC-D runtime packages.
8. Whether existing architecture tests can be hardened without brittle false positives.
9. Whether RuntimeDispatchService relies on DispatchStateWritePort / DispatchObservationPort after NATS publish.
10. Whether manifest payload classes exist when deciding lifecycle stream fixture scope.
11. Whether SCB_LIFECYCLE_V1 durable consumer creation can be validated against the live test broker.
12. Whether reconnect/drain gaps need to be retained as DEBT-B-NATS-004 / DEBT-B-NATS-005.
```

---

## 17. Downstream sequence

```text
1. CSA-SOV-SC-B-JETSTREAM-HARDENING-001
2. MIR-SOV-SC-B-JETSTREAM-HARDENING-001
3. Resume MIR-SOV-SC-D-ADAPTER-MANIFEST-SEED-001 execution only if it remains non-NATS-runtime,
   or after JetStream hardening if manifest-over-NATS is included.
4. Manifest-over-NATS runtime / route assignment runtime / adapter lifecycle runtime.
```

---

## 18. Promotion checklist

```text
[ ] The SDD records that MU-029 left JetStream config-only.
[ ] The SDD requires live JetStream stream application, not object construction.
[ ] The SDD includes SCB_LIFECYCLE_V1 explicitly.
[ ] The SDD requires a durable SCB_LIFECYCLE_V1 consumer as MUST or STOP.
[ ] The SDD preserves JetStream ack != semantic success.
[ ] The SDD defines test substrate policy.
[ ] The SDD requires reconnect/drain retained gaps to be recorded as explicit DEBT IDs.
[ ] The SDD states that live manifest-over-NATS depends on JetStream hardening.
[ ] The SDD preserves SC-C / SC-B / SC-D boundaries.
[ ] The SDD defines CSA targets before MIR descent.
```

