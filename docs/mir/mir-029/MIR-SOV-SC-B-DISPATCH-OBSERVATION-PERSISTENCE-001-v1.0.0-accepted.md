# MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001

## Materialization Increment Record — SC-B Dispatch Observation Persistence

```text
Document ID:  MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
Title:        Materialization Increment Record — SC-B Dispatch Observation Persistence
Version:      v1.0.0-accepted
Status:       Accepted / implementation-attempted / Validated L4
Date:         2026-05-30
Corpus:       Sovereign Connect
Type:         MIR
Plane:        SC-B
Track:        SC-B Runtime Dispatch Hardening / H3
MU ID:        MU-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001
MU Slot:      MU-027
```

---


## Changelog v1.0.0-accepted

Promotes `v0.2.0-candidate` to accepted after implementation, validation review and merge into `develop`.

This version:

```text
1. Records MU-027 as Validated L4.
2. Records final validation: sovereign-connect 378/0/0/0 and EIB reports 56/0/0/0.
3. Records branch `feat/sc-b-mir-027-dispatch-observation-persistence`, feature final commit `c8429c8` and merge commit `dba2d9d`.
4. Records the H3 outcome: SC-B dispatch observations are durable, restart-visible and queryable.
5. Records that `JdbcDispatchObservationRepository` implements `DispatchObservationPort`.
6. Records that `V101__sc_b_dispatch_observation_persistence.sql` creates `sc_b_dispatch_observations` with FK to `sc_b_dispatch_records`.
7. Preserves that observations remain technical delivery diagnostics and do not become semantic authority.
8. Preserves that H3 does not implement NATS, JetStream, lifecycle runtime, productive ScdCommand, SC-D adapter execution or ScDeliveryError event emission.
```

---

## 0.1 Acceptance and validation record

```text
Accepted version: v1.0.0-accepted
Validation level: Validated L4
Evidence package: docs/mir/mir-027/
Implementation branch: feat/sc-b-mir-027-dispatch-observation-persistence
Feature final commit: c8429c8
Merge commit: dba2d9d
Validation evidence:
  sovereign-connect module: 378 tests, 0 failures, 0 errors, 0 skipped
  EIB module reports present: 56 tests, 0 failures, 0 errors, 0 skipped
Review evidence:
  VALIDATION-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001-v0.1.0-review.md
```

Validated H3 outcome:

```text
- `JdbcDispatchObservationRepository` implements `DispatchObservationPort`;
- `V101__sc_b_dispatch_observation_persistence.sql` creates `sc_b_dispatch_observations`;
- FK toward `sc_b_dispatch_records(dispatch_record_id)` is implemented;
- observations are restart-visible and queryable by dispatchRecordId;
- `RuntimeDispatchService` was not redesigned;
- `CANDIDATE_SELECTED` remains reserved / not emitted;
- existing architecture tests were updated to recognize V101 as intentional;
- no NATS, JetStream, lifecycle runtime, productive ScdCommand or SC-D adapter execution is introduced.
```

Closed debt:

```text
DEBT-B-RD-H-002 — Dispatch observation persistence absent.
  disposition: closed by MU-027.

DEBT-BRD-H-004 — Dispatch observation persistence absent after outbox bridge.
  disposition: closed by MU-027.
```

Retained scope exclusions:

```text
This MIR does not implement NATS, JetStream, lifecycle-channel runtime, productive ScdCommand,
SC-D adapter execution, ScDeliveryError event emission, semantic retry or SC-C terminal request-state ownership.
```

---

## 0. MIR boundary

This MIR records the accepted and validated **H3 Dispatch Observation Persistence** increment after:

```text
H0 / MU-024 — SC-B Abstract Bus Seed                     Validated L4
H1 / MU-025 — SC-B Dispatch State Persistence            Validated L4
H2 / MU-026 — SC-B Outbox Bridge Seed                    Validated L4
```

This MIR does not include an execution prompt, implementation context, acceptance map or Codex instructions. Those artifacts must be produced separately under the execution package if this MIR is promoted to candidate.

This MIR is not a NATS binding. It is not a JetStream binding. It is not lifecycle-channel implementation. It is not SC-D adapter onboarding. It is not productive `ScdCommand`. It is not command execution against a provider. It is not SC-C terminal request-state persistence.

This MIR hardens only the SC-B technical dispatch observation boundary.

---

## 0.1 Changelog v0.2.0-candidate

Promotes `v0.1.0-draft` to candidate after MIR review.

This version:

```text
1. Records the MIR as Approvable and ready for execution-package descent.
2. Makes no normative scope change to H3.
3. Preserves the H3-only boundary: dispatch observation persistence only.
4. Preserves that CANDIDATE_SELECTED remains optional/reserved unless the current runtime exposes a concrete hook.
5. Preserves that the execution package should recommend the FK from sc_b_dispatch_observations(dispatch_record_id) to sc_b_dispatch_records(dispatch_record_id) when the active schema supports it.
6. Defers both review observations to context.md / codex-prompt.md of the execution package.
```

---

## 0.2 Changelog v0.1.0-draft

Initial draft.

This version:

```text
1. Opens MU-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 as MU-027 / H3.
2. Consumes MU-024, MU-025 and MU-026 as validated baselines.
3. Scopes the increment to durable SC-B DispatchObservationRecord persistence and diagnostic queryability.
4. Preserves OutboxEntry != DispatchCandidate != ScEnvelope.
5. Preserves SC-C semantic authority and forbids SC-C OutboxEntryStatus mutation.
6. Preserves that observations are technical delivery evidence only.
7. Explicitly excludes NATS, JetStream, lifecycle-channel runtime, SC-D adapter execution, productive ScdCommand and ScDeliveryError event emission.
8. Requires restart-visible observations for dispatch success, no-handler, delivery failure, retry scheduling, exhaustion and supersede cancellation.
9. Requires a schema/migration strategy that does not collide with SC-C or previous SC-B persistence migrations.
```

---

## 1. Depends on

```text
Sovereign Connect — Curator & Descent Instructions
PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate
CSA-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-merged
MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-001 v1.0.0-accepted / Validated L4
MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v1.0.0-accepted / Validated L4
ADR-SOV-SC-SERIALIZATION-001 v0.2.0-candidate
PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001 v0.2.0-candidate
PDR-SOV-SC-BUS-CONTRACT-001 v0.4.6-draft
PDR-SOV-SC-CANONICAL-CONTRACT-001 v0.2.7-draft
INDEX-SOV-SC-MATERIALIZATION-UNITS-001 v0.2.26-draft
```

---

## 2. Related / downstream

```text
SDD-SOV-SC-B-LIFECYCLE-CHANNEL-001
SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001
MIR-SOV-SC-B-NATS-JETSTREAM-BINDING-SEED-001
TCK-SOV-SC-BUS-CONTRACT-001
TCK-SOV-SC-D-CONFORMANCE-001
```

---

# 3. Purpose

The purpose of this MIR is to make SC-B technical dispatch observations durable, restart-visible and queryable before physical broker binding descent.

After MU-026, SC-B has the following no-broker executable path:

```text
SC-C OutboxEntry
  -> ScOutboxDispatchReadPort
  -> OutboxEntryDispatchProjector
  -> DispatchCandidate
  -> DispatchState filtering
  -> RuntimeDispatchService
  -> ScBusPort / in-process handler
```

However, dispatch observations still remain seed-local/in-memory diagnostic evidence. Without H3, a future NATS/JetStream binding could become the first durable place where delivery failures and exhaustion are visible. That would violate the hardening thesis: a physical broker must not substitute for SC-B runtime diagnostics.

H3 makes the following durable:

```text
technical dispatch observation records
no-handler evidence
delivery failure evidence
retry-scheduling evidence
exhaustion diagnostics
supersede cancellation observations
correlation/source traceability where available
```

---

# 4. Baseline state

The post-MU-026 validated baseline is:

```text
sovereign-connect module: 354 tests, 0 failures, 0 errors, 0 skipped
bus subset:               85 tests, 0 failures, 0 errors, 0 skipped
integration subset:       24 tests, 0 failures, 0 errors, 0 skipped
SQLite outbox read:        5 tests, 0 failures, 0 errors, 0 skipped
EIB reports present:      56 tests, 0 failures, 0 errors, 0 skipped
```

Current H3-relevant code properties:

```text
DispatchObservationRecord exists.
DispatchObservationPort exists.
InMemoryDispatchObservationRepository exists.
RuntimeDispatchService records observations for claim, dispatch start, dispatch completed, no-handler, failed delivery, retry scheduled, retry claimed, exhausted and supersede cancellation.
JdbcDispatchStateRepository persists dispatch state.
ScLedgerDispatchCandidateReadAdapter filters candidates using dispatch state.
OutboxEntry rows can be projected into DispatchCandidate through integration.scledgerdispatch.
```

Current H3-relevant limitations:

```text
observations are not restart-visible;
observation queryability is limited to the current in-memory repository;
observation records do not yet form a durable diagnostic substrate;
ScDeliveryError remains deferred as a productive event-lane shape;
no NATS/JetStream binding exists;
no SC-D lifecycle runtime exists.
```

---

# 5. Problem statement

SC-B dispatch state is now durable and SC-C outbox rows can now become SC-B dispatch candidates, but the technical evidence generated during dispatch is not durable.

This creates four risks:

```text
1. delivery failures disappear on restart;
2. exhaustion diagnostics are not queryable after process recreation;
3. no-handler/no-subscriber situations become visible only through transient logs/tests;
4. a future physical broker may be incorrectly treated as the diagnostic source of truth.
```

H3 closes this gap by persisting dispatch observations as SC-B-owned technical evidence.

---

# 6. Goals

## G-MIR-027-001 — Persistent observation adapter

Create a persistent implementation for the SC-B dispatch observation port/query surface.

## G-MIR-027-002 — Restart-visible dispatch diagnostics

Observations recorded before repository/service recreation must remain visible after recreation.

## G-MIR-027-003 — Preserve technical-only semantics

Observations must not become semantic success, semantic failure, device-effect verification, adapter admission decision or SC-C terminal request state.

## G-MIR-027-004 — Preserve H1/H2 boundaries

H3 must consume the H1/H2 runtime substrate without reimplementing dispatch state persistence or outbox bridge projection.

## G-MIR-027-005 — Queryability

At minimum, observations must be queryable by `dispatchRecordId`.

If `sourceRecordId`, `correlationId`, `messageId`, `lane` or `logicalTopic` are available at the observation point, H3 should persist them and expose query/test coverage for at least `sourceRecordId` or `correlationId`.

## G-MIR-027-006 — Migration separation

SC-B observation persistence must not collide with SC-C migrations or previous SC-B migrations.

## G-MIR-027-007 — Broker independence

No NATS, JetStream or physical broker dependency may be introduced.

---

# 7. Non-goals

This MIR does not authorize:

```text
- NATS Core;
- JetStream;
- physical broker client dependencies;
- NATS subjects;
- wire serialization implementation;
- lifecycle-channel runtime;
- adapter announce/challenge/registration;
- adapter-scoped route assignment;
- productive ScdCommand shape;
- SC-D discovery/state/health fact family production shapes;
- provider command execution;
- semantic retry of physical actions;
- device-effect verification;
- SC-C terminal request-state mutation;
- mutation of SC-C OutboxEntryStatus;
- mirroring observations into SC-C ledger;
- productive ScDeliveryError event emission;
- EIB changes;
- View Composer / SApp / Surface construction.
```

---

# 8. Design decisions

## D-MIR-027-001 — H3 only

This MIR implements H3 only: dispatch observation persistence.

It must not reopen H1 dispatch state persistence or H2 outbox bridge design except where H3 needs to read already available identifiers for diagnostic enrichment.

## D-MIR-027-002 — Observations are SC-B-owned technical evidence

SC-B owns technical delivery observations.

Observations are allowed to say:

```text
candidate selected
claim recorded
dispatch started
technical dispatch completed
no handler available
technical delivery failed
retry scheduled
technical delivery exhausted
superseded by another flow
validation rejected a candidate/envelope/routing key/correlation
```

Observations must not say or imply:

```text
command semantically succeeded
command semantically failed
device state changed
adapter was operationally admitted
SC-C request reached terminal semantic state
policy accepted or rejected a request
```

## D-MIR-027-003 — Existing DispatchObservationPort must remain usable

The current `DispatchObservationPort` surface is the seed runtime hook:

```java
void record(DispatchObservationRecord observation)
List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId)
```

H3 should preserve this surface where possible and add a persistent implementation.

If richer diagnostic queryability requires an additive `DispatchObservationReadPort` or additive methods, execution may introduce it, provided the implementation report maps the decision to the acceptance criteria and does not import `core.*` into `bus.*`.

## D-MIR-027-004 — Persistent adapter package

Expected package:

```text
com.sovereign.connect.bus.runtime.persistence
```

Expected adapter:

```text
JdbcDispatchObservationRepository
```

Equivalent naming is allowed if mapped in the implementation report.

## D-MIR-027-005 — In-memory repository preserved

`InMemoryDispatchObservationRepository` may remain for seed/unit-test/no-persistence profiles.

H3 adds a persistent alternative; it does not need to remove the in-memory repository.

## D-MIR-027-006 — Observation model may be enriched additively

The current seed observation shape is minimal:

```text
observationId
dispatchRecordId
attemptId
state
code
sanitizedReason
observedAt
```

H3 may enrich this shape with additional nullable technical fields when available:

```text
sourceRecordId
lane
logicalTopic
partitionKey
correlationId
causationId
messageId
observationKind
outcomeKind
retryable
metadataJson
```

Enrichment is allowed only as SC-B technical diagnostic metadata.

H3 must not require SC-C semantic interpretation to populate these fields.

## D-MIR-027-007 — Observation kind vocabulary

H3 should define a stable observation-kind vocabulary, either as an enum or controlled string constants.

Required seed vocabulary:

```text
CANDIDATE_SELECTED, optional if selected by the read adapter
CLAIMED
DISPATCH_STARTED
DISPATCHED
NO_HANDLER
DELIVERY_FAILED
RETRY_SCHEDULED
EXHAUSTED
CANCELLED_BY_SUPERSEDE
VALIDATION_REJECTED, if RuntimeDispatchService records validation failures in this increment
```

If implementation chooses not to introduce `CANDIDATE_SELECTED` or `VALIDATION_REJECTED`, the implementation report must state that these remain deferred because the current runtime does not yet emit those observations.

## D-MIR-027-008 — Failure evidence is sanitized

Observation persistence may store a code and sanitized reason.

It must not store raw provider payloads, raw secrets, adapter credentials, full untrusted exception stacks or unredacted physical broker diagnostics.

## D-MIR-027-009 — ScDeliveryError remains deferred

H3 may persist observation data that later supports a `ScDeliveryError` projection.

H3 must not introduce productive event-lane `ScDeliveryError` emission unless a later MIR explicitly scopes it.

If any `ScDeliveryError` helper/DTO is introduced for tests or projection preparation, it must be diagnostic-only and must not be published onto the event lane.

## D-MIR-027-010 — No SC-C ledger mirroring

H3 stores technical observations in SC-B persistence first.

It must not mirror observations into SC-C ledger, mutate SC-C outbox rows or treat SC-C ledger as SC-B diagnostics storage.

## D-MIR-027-011 — Migration numbering

MU-025 introduced:

```text
V100__sc_b_dispatch_state_persistence.sql
```

H3 should use the next SC-B migration slot if the single-stream V100+ strategy remains active:

```text
V101__sc_b_dispatch_observation_persistence.sql
```

Equivalent names are allowed if the active repository already uses a different SC-B migration strategy, but the implementation report must record the migration choice.

## D-MIR-027-012 — Minimal relational schema

If relational persistence is used, H3 should create:

```text
sc_b_dispatch_observations
```

Minimum columns:

```text
observation_id TEXT PRIMARY KEY
dispatch_record_id TEXT NOT NULL
attempt_id TEXT NULL
state TEXT NOT NULL
code TEXT NULL
sanitized_reason TEXT NULL
observed_at_ms INTEGER NOT NULL
```

Recommended additional nullable columns:

```text
source_record_id TEXT NULL
lane TEXT NULL
logical_topic TEXT NULL
partition_key TEXT NULL
correlation_id TEXT NULL
causation_id TEXT NULL
message_id TEXT NULL
observation_kind TEXT NULL
outcome_kind TEXT NULL
retryable INTEGER NULL
metadata_json TEXT NULL
```

Recommended indexes:

```text
idx_sc_b_dispatch_observations_record     (dispatch_record_id, observed_at_ms)
idx_sc_b_dispatch_observations_source     (source_record_id, observed_at_ms)      if source_record_id is persisted
idx_sc_b_dispatch_observations_corr       (correlation_id, observed_at_ms)        if correlation_id is persisted
idx_sc_b_dispatch_observations_kind       (observation_kind, observed_at_ms)      if observation_kind is persisted
```

## D-MIR-027-013 — FK behavior

If `sc_b_dispatch_records` exists in the same database, observations may reference `sc_b_dispatch_records(dispatch_record_id)`.

If the current test/storage setup makes FK enforcement brittle, the MIR allows omission of the FK for H3 only, provided:

```text
- dispatchRecordId is still required;
- tests prove observations remain queryable by dispatchRecordId;
- implementation report records the FK decision;
- no cross-habitat or SC-C semantic authority is inferred from the FK.
```

## D-MIR-027-014 — Ordering

Observation query results must be deterministic.

Default ordering:

```text
observedAt ASC, insertion/order surrogate ASC if available
```

If no insertion surrogate exists, `observationId` may be used only as a deterministic tie-breaker, not as chronological authority.

---

# 9. Expected implementation surface

Expected production surface:

```text
src/main/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchObservationRepository.java
src/main/resources/db/migration/V101__sc_b_dispatch_observation_persistence.sql
```

Possible additive production surface:

```text
src/main/java/com/sovereign/connect/bus/runtime/dispatch/model/DispatchObservationKind.java
src/main/java/com/sovereign/connect/bus/runtime/port/DispatchObservationReadPort.java
src/main/java/com/sovereign/connect/bus/runtime/persistence/DispatchObservationRow.java
```

Expected test surface:

```text
src/test/java/com/sovereign/connect/bus/runtime/persistence/JdbcDispatchObservationRepositoryTest.java
src/test/java/com/sovereign/connect/bus/runtime/dispatch/RuntimeDispatchObservationPersistenceTest.java
src/test/java/com/sovereign/connect/bus/ScBusObservationPersistenceArchitectureTest.java
```

Equivalent names are allowed if mapped to acceptance criteria in the implementation report.

---

# 10. Required behavior

## 10.1 Record behavior

`record(observation)` must:

```text
- reject null observation;
- reject null observationId;
- reject null dispatchRecordId;
- reject null state;
- reject null observedAt;
- persist code and sanitizedReason as nullable fields;
- preserve attemptId when present;
- not mutate dispatch state;
- not mutate OutboxEntryStatus;
- not publish any bus event.
```

## 10.2 Query behavior

At minimum:

```text
observationsFor(dispatchRecordId)
```

must:

```text
- reject null dispatchRecordId;
- return an immutable or defensive-copy result;
- return an empty list for unknown dispatchRecordId;
- return persisted observations after repository recreation;
- return results in deterministic observedAt order.
```

If additive query methods are introduced, they must follow the same null/empty/deterministic-result rules.

## 10.3 Runtime dispatch integration

When RuntimeDispatchService uses the persistent observation adapter, observations must survive a new repository instance over the same database for at least:

```text
CLAIMED / technical claim recorded
DISPATCHING / technical dispatch started
DISPATCHED / technical dispatch completed
DELIVERY_FAILED / NO_HANDLER
DELIVERY_FAILED / technical failure code
RETRY_SCHEDULED
EXHAUSTED
CANCELLED_BY_SUPERSEDE
```

## 10.4 Failure and no-handler behavior

No-handler and failed technical dispatch outcomes must persist enough diagnostic evidence to distinguish:

```text
no handler found
technical delivery failure
unsupported/non-dispatchable lane, if produced by the runtime
```

The reason must be sanitized.

## 10.5 Retry/exhaustion behavior

Retry scheduling and exhaustion observations must remain visible after restart.

`EXHAUSTED` observation must not be interpreted as domain or device failure.

## 10.6 Supersede behavior

Supersede cancellation observations may include the evidence reference as sanitized diagnostic text.

They must not expose it as authority/policy decision data.

## 10.7 Migration behavior

The SC-B observation migration must:

```text
- create only sc_b_* tables/indexes;
- not alter SC-C tables;
- not reuse SC-C-owned V1–V99 range;
- not replace or mutate V100 dispatch state migration;
- not introduce broker tables or NATS/JetStream storage concepts.
```

---

# 11. Required tests

At minimum, the implementation must add tests for:

```text
1. record rejects null observation;
2. record rejects missing observationId / dispatchRecordId / state / observedAt;
3. observation survives repository recreation;
4. observationsFor unknown dispatchRecordId returns empty list;
5. observationsFor returns deterministic observedAt ordering;
6. no-handler observation persists code/reason;
7. delivery-failed observation persists code/reason;
8. retry-scheduled observation survives restart;
9. exhausted observation survives restart and remains technical;
10. supersede cancellation observation persists sanitized evidence;
11. RuntimeDispatchService records persistent observations using the JDBC adapter;
12. SC-B observation migration uses V101+ or approved SC-B migration strategy;
13. bus.** still does not import core.**;
14. core.** still does not import bus.runtime.**;
15. no NATS/JetStream/broker dependency is introduced;
16. H3 does not mutate SC-C OutboxEntryStatus or write SC-C ledger diagnostics.
```

Recommended minimum test delta:

```text
+10 tests
```

The final implementation report must record the actual test delta.

---

# 12. Acceptance criteria

## 12.1 Scope and boundaries

```text
AC-027-001 — MIR implements H3 dispatch observation persistence only.
AC-027-002 — MIR does not reimplement H1 dispatch state persistence.
AC-027-003 — MIR does not reimplement H2 outbox bridge projection.
AC-027-004 — MIR does not introduce NATS, JetStream or broker dependencies.
AC-027-005 — MIR does not implement lifecycle-channel runtime or SC-D adapter onboarding.
AC-027-006 — MIR does not introduce productive ScdCommand or SC-D fact family shapes.
AC-027-007 — MIR does not mutate SC-C OutboxEntryStatus or SC-C ledger records.
AC-027-008 — bus.** production code does not import core.**.
AC-027-009 — core.** production code does not import bus.runtime.**.
```

## 12.2 Persistent observation adapter

```text
AC-027-010 — A persistent adapter for dispatch observations exists.
AC-027-011 — The adapter supports record(...).
AC-027-012 — The adapter supports observationsFor(dispatchRecordId) or an explicitly mapped equivalent.
AC-027-013 — InMemoryDispatchObservationRepository remains usable for seed/no-persistence profiles.
AC-027-014 — Persistent adapter uses JDBC/Flyway-compatible storage without JPA.
```

## 12.3 Restart visibility and ordering

```text
AC-027-015 — Observations survive repository recreation.
AC-027-016 — Unknown dispatchRecordId returns an empty observation list.
AC-027-017 — Observation query results are deterministically ordered.
AC-027-018 — Observation records preserve observationId, dispatchRecordId, attemptId when present, state, code, sanitizedReason and observedAt.
```

## 12.4 Runtime observation coverage

```text
AC-027-019 — CLAIMED observation is persisted when dispatch claims a candidate.
AC-027-020 — DISPATCHING observation is persisted when dispatch starts.
AC-027-021 — DISPATCHED observation is persisted after technical publish success.
AC-027-022 — NO_HANDLER observation is persisted when no handler is available.
AC-027-023 — DELIVERY_FAILED observation is persisted for technical dispatch failure.
AC-027-024 — RETRY_SCHEDULED observation is persisted when retry is scheduled.
AC-027-025 — EXHAUSTED observation is persisted when technical delivery is exhausted.
AC-027-026 — CANCELLED_BY_SUPERSEDE observation is persisted when supersede cancellation occurs.
```

## 12.5 Technical-only semantics

```text
AC-027-027 — DISPATCHED observation does not mean semantic success.
AC-027-028 — DELIVERY_FAILED observation does not mean domain failure.
AC-027-029 — EXHAUSTED observation does not mean provider/device failure.
AC-027-030 — Observations do not become SC-C terminal request state.
AC-027-031 — Observations do not perform adapter admission, policy, authority or visibility decisions.
```

## 12.6 Diagnostic metadata and sanitization

```text
AC-027-032 — Failure code/reason are sanitized before persistence.
AC-027-033 — Raw provider payloads, credentials and full untrusted exception stacks are not persisted in observations.
AC-027-034 — If sourceRecordId/correlationId/messageId are persisted, they remain technical correlation metadata only.
AC-027-035 — If ScDeliveryError-related data is prepared, it remains observation data only and is not emitted as a productive event.
```

## 12.7 Migration and schema

```text
AC-027-036 — SC-B observation migration creates only sc_b_* persistence objects.
AC-027-037 — SC-B observation migration does not collide with SC-C Flyway V1–V99 or MU-025 V100.
AC-027-038 — Migration strategy is recorded in the implementation report.
```

## 12.8 Evidence and reporting

```text
AC-027-039 — Implementation report records branch, commit hash and changed files.
AC-027-040 — Implementation report records test command and full test summary.
AC-027-041 — Implementation report maps every AC-027 criterion to tests, source files or rationale.
AC-027-042 — Implementation report records retained debts for lifecycle, NATS, ScDeliveryError event emission and SC-D execution.
```

---

# 13. Retained debt

The following debts remain explicitly open after H3 unless separately closed by later artifacts:

```text
DEBT-BRD-H-005 — Productive ScDeliveryError event-lane shape/emission remains deferred.
  Target: future diagnostic/event contract or NATS/bus-conformance artifact.

DEBT-BRD-H-006 — Physical broker binding absent.
  Target: SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001 after hardening/lifecycle gates.

DEBT-BRD-H-007 — SC-D lifecycle channel not implemented.
  Target: SDD/MIR descent from PDR-SOV-SC-B-LIFECYCLE-CHANNEL-001.

DEBT-SEED-001 — ScdCommand canonical shape deferred.
  Target: SC-D protocol / command-to-adapter artifacts.

DEBT-SEED-002 — SC-D fact family canonical shapes deferred.
  Target: SC-D discovery/state/health fact artifacts.

DEBT-SEED-003 — adapter-scoped route assignment deferred.
  Target: lifecycle channel / admission artifacts.

DEBT-B-NATS-001 — physical NATS Core + JetStream binding absent.
  Target: SDD-SOV-SC-B-NATS-CORE-JETSTREAM-001.
```

Debt closed by this MIR if validated:

```text
DEBT-B-RD-H-002 — Dispatch observation persistence absent.
  Closure condition: persistent observation adapter exists, restart-visible tests pass and observations remain technical evidence only.

DEBT-BRD-H-004 — Dispatch observation persistence absent.
  Closure condition: same as DEBT-B-RD-H-002; retained as alias/local naming variant if present in governance docs.
```

---

# 14. Risks

## RISK-MIR-027-001 — Semantic leakage

Observation persistence may be mistaken for semantic terminal request state.

Mitigation:

```text
ACs explicitly require DISPATCHED/DELIVERY_FAILED/EXHAUSTED to remain technical-only.
```

## RISK-MIR-027-002 — Broker diagnostic substitution

A future NATS/JetStream implementation may use broker state/logs as the only diagnostic persistence.

Mitigation:

```text
H3 creates SC-B-owned observation persistence before physical binding descent.
```

## RISK-MIR-027-003 — SC-C coupling

Implementation may try to write observations into SC-C ledger or mutate OutboxEntryStatus.

Mitigation:

```text
H3 forbids SC-C ledger writes and OutboxEntryStatus mutation.
```

## RISK-MIR-027-004 — Over-enrichment

Implementation may attempt to redesign DispatchCandidate, lifecycle routing or payload semantics to enrich observations.

Mitigation:

```text
Enrichment is nullable/best-effort and must not force lifecycle, SC-D, ScdCommand or broker work into H3.
```

## RISK-MIR-027-005 — Migration collision

Implementation may create a migration that collides with SC-C or MU-025 SC-B migration ownership.

Mitigation:

```text
Use V101+ or an explicitly documented SC-B migration strategy.
```

## RISK-MIR-027-006 — Raw diagnostic leakage

Failure reasons may persist raw provider/broker payloads or secrets.

Mitigation:

```text
ACs require sanitized reason storage only.
```

---

# 15. Expected validation

Minimum expected validation:

```text
mvn -q test
```

Expected baseline:

```text
sovereign-connect module:
  previous total: 354 tests, 0 failures, 0 errors, 0 skipped
  expected total: >= 364 tests, 0 failures, 0 errors, 0 skipped

EIB module:
  expected to remain 56 tests, 0 failures, 0 errors, 0 skipped if run
```

If the EIB module is not run because H3 modifies only the `sovereign-connect` module, the implementation report must state this explicitly.

---

# 16. MIR acceptance checklist

This MIR may be promoted to candidate when:

```text
- PDR-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate is accepted.
- SDD-SOV-SC-B-RUNTIME-DISPATCH-HARDENING-001 v0.2.0-candidate is accepted.
- MU-024 is recorded as Validated L4.
- MU-025 is recorded as Validated L4.
- MU-026 is recorded as Validated L4.
- H3-only scope is accepted.
- Observation persistence technical-only semantics are accepted.
- Migration strategy constraints are accepted.
- No prompt/context is embedded in the MIR.
- Branch and commit suggestions are present.
```

Recommended before execution package:

```text
A lightweight post-MU-026 code-surface refresh SHOULD confirm the current
DispatchObservationRecord, DispatchObservationPort, RuntimeDispatchService
and migration layout before Codex receives the execution package.
```

---

# 17. Suggested branch and commit

Suggested branch:

```text
feat/sc-b-mir-027-dispatch-observation-persistence
```

Suggested implementation commit:

```text
feat(sc-b): persist dispatch observations
```

Suggested evidence commit:

```text
docs(sc-b): record dispatch observation persistence evidence
```

---

# 18. Final dictum

```text
MIR-SOV-SC-B-DISPATCH-OBSERVATION-PERSISTENCE-001 v0.2.0-candidate
is accepted for execution-package descent.
```

This MIR is the third hardening step after MU-024.

It is intentionally narrower than NATS/JetStream physical binding, lifecycle-channel runtime and SC-D adapter execution.

It closes the remaining local hardening gap before physical binding can responsibly claim durable diagnostic readiness:

```text
H1 — durable dispatch state             [closed by MU-025]
H2 — SC-C outbox bridge                 [closed by MU-026]
H3 — durable dispatch observations      [opened and candidate-approved by this MIR]
```
