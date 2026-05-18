# Code Surface Audit — MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001

```text
Audit ID:            CSA-MU-005
Version:             v0.1.1-merged
MU:                  MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Operational Slot:    MU-005
SDD reference:       SDD-SOV-SC-C-TEMPORAL-ENGINE-001 v0.1.2-draft
PDR reference:       PDR-SOV-SC-C-TEMPORAL-ACTS-001 v0.1.1-draft
Repository state:    feat/sc-c-outbox-ledger-storage-seed (post-MU-015 hardening)
Baseline tests:      75 / 0 failures / 0 errors / 0 skipped
Date:                2026-05-17
Surface category:    Greenfield-with-neighbors / bounded infrastructure integration
Execution discipline: Non-greenfield for execution-package purposes
Status:              Approvable as code-surface-audit.md for docs/mir/mir-005/
```

---

## 0. Merge disposition

This merged audit consolidates two sources:

1. The architect audit draft for `CSA-MU-005`, which provides the detailed operational surface for MU-005.
2. The independent audit review, which adds boundary cautions around adapter separation, atomic transaction ownership, Profile A-only scope, and deterministic testability.

Canonical merge decision:

```text
Use the architect audit as the operational base.
Apply boundary and implementation-discipline patches from the review.
Proceed to MIR after this merged audit is accepted.
```

Key merge outcomes:

```text
- MU-005 remains Profile A only.
- SignalTemporalPayload is implemented.
- ActionTemporalPayload is reserved but not materialized in MU-005.
- ScLedgerWritePort / ScOutboxWritePort from MU-015 are reused.
- H2TemporalActRepository is preferred over further expanding H2BaseTopologyRepository.
- A shared transaction boundary is mandatory for temporal_acts + ledger + outbox writes.
- Fire idempotency is enforced by conditional UPDATE row count plus ledger idempotency key.
- Background scheduling must be testable through deterministic one-shot methods.
```

---

## 1. Purpose

This audit establishes the implementation surface for `MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001` before opening the MIR and execution package.

It answers:

1. What already exists and must be consumed?
2. What does not exist and must be created?
3. Which implementation paths are acceptable?
4. Which invariants must not be broken?
5. What must be explicitly excluded from Profile A?

This audit is not an implementation prompt.

It is the normative code-surface input for:

```text
docs/mir/mir-005/code-surface-audit.md
docs/mir/mir-005/decision-confirmation.md
docs/mir/mir-005/context.md
docs/mir/mir-005/codex-prompt.md
docs/mir/mir-005/acceptance-map.md
```

---

## 2. Surface classification

The MU is not greenfield in the operational sense.

Domain types for TemporalActs do not exist, but the implementation must integrate with validated neighboring surfaces:

```text
- H2/JdbcTemplate persistence pattern
- Clock injection pattern
- SC-C ledger/outbox append ports from MU-015
- existing H2 schema creation conventions
- existing durability/recovery test patterns
- existing boundary assertions around SC-B, SC-D, Projection, Session, Identity, Authority and Policy absence
```

Canonical classification:

```text
Surface category:
  Greenfield-with-neighbors / bounded infrastructure integration

Execution-package discipline:
  Treat as non-greenfield because it modifies persistence and transactional infrastructure.
```

Implication:

```text
The implementation prompt MUST be explicit about touched files, transaction boundary, package placement, and non-goals.
```

---

## 3. Existing surfaces available to MU-005

### 3.1 Outbox/Ledger storage — MU-015 available

Validated by `MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001`:

```java
// Ports
ScLedgerWritePort   -> appendLedgerEntry(LedgerEntry)
ScOutboxWritePort   -> appendOutboxEntry(OutboxEntry)  // PENDING only
```

Available model surface:

```java
LedgerEntry(
    ledgerEntryId,
    habitatId,
    recordClass,
    aggregateType,
    aggregateId,
    semanticKind,
    payloadType,
    payloadJson,
    idempotencyKey,
    recordedAt,
    metadataJson
)

OutboxEntry(
    outboxEntryId,
    ledgerEntryId,
    habitatId,
    outboundKind,
    deliveryLane,
    logicalTopic,
    semanticPayloadJson,
    notificationTargetRef,
    idempotencyKey,
    status,
    createdAt,
    updatedAt,
    metadataJson
)
```

Available enums include TemporalAct-related values:

```text
SemanticKind:
  TEMPORAL_ACT_CREATED
  TEMPORAL_ACT_CANCELLED
  TEMPORAL_ACT_FIRED
  TEMPORAL_ACT_MISFIRED
  TEMPORAL_ACT_FAILED
  TIMER_FIRED

OutboundKind:
  TIMER_FIRED_SIGNAL
  TEMPORAL_ACT_CANCELLED_SIGNAL
  TEMPORAL_ACT_MISFIRED_DIAGNOSTIC
  COMMAND (reserved)

DeliveryLane:
  SIGNAL
  COMMAND (reserved)

OutboxEntryStatus:
  PENDING only for append in MU-015 seed
```

Available H2 tables:

```text
sc_c_ledger_entries
  UNIQUE(habitat_id, idempotency_key)

sc_c_outbox_entries
  FK(ledger_entry_id, habitat_id)
  UNIQUE(habitat_id, idempotency_key)
  append accepts only OutboxEntryStatus.PENDING
```

Preserved invariant:

```text
appendOutboxEntry(...) MUST continue to reject non-PENDING rows.
```

---

### 3.2 Topology infrastructure — available but not active in Profile A

Available for future Profile B fire-time validation:

```java
BaseTopologyService.validateTarget(
    String habitatId,
    TopologyTargetRef target,
    TopologyVersion requestTopologyVersion
) -> TargetValidationResult
```

Known result families:

```text
VALID
VALID_AFTER_REVALIDATION
TARGET_NOT_FOUND
CAPABILITY_MISMATCH
TOPOLOGY_VERSION_CONFLICT
```

Available supporting types:

```text
TopologyTargetRef(deviceId, endpointId, capabilityId)
TopologyVersion.habitatVersion(habitatId, long value)
TopologyVersion.isScopedToHabitat(String habitatId)
```

Profile A rule:

```text
Profile A MUST NOT call validateTarget(...).
Profile A does not execute actions and does not validate action targets.
```

---

### 3.3 H2/JdbcTemplate constructor and schema pattern

Existing seed persistence uses the constructor pattern:

```java
new H2BaseTopologyRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock)
```

and convenience constructor:

```java
new H2BaseTopologyRepository(DataSource dataSource)
```

Existing behavior:

```text
createSchema() is called at construction time.
```

MU-005 may either:

```text
A. add temporal_acts schema creation to the existing schema bootstrap path; or
B. introduce a sibling H2TemporalActRepository with its own schema bootstrap using the same DataSource.
```

Canonical recommendation: **B preferred**.

---

### 3.4 Test infrastructure pattern

File-backed H2 tests use the pattern:

```java
@TempDir Path tempDir;
Clock clock = Clock.fixed(Instant.parse("2026-05-16T12:00:00Z"), ZoneOffset.UTC);

private String jdbcUrl(String name) {
    String dbPath = tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
}

private DataSource dataSource(String jdbcUrl) {
    return new DriverManagerDataSource(jdbcUrl, "sa", "");
}
```

Recovery-style tests simulate restart by creating a new repository or service instance against the same `jdbcUrl`.

MU-005 tests SHOULD follow this pattern.

---

## 4. Missing surfaces MU-005 must create

### 4.1 Domain model

Missing and required:

```text
TemporalAct
TemporalActStatus
TemporalActPayload
SignalTemporalPayload
CreatedByRef
```

Reserved, not created in MU-005:

```text
ActionTemporalPayload
```

Rationale:

```text
Profile B is deferred.
ActionRequest is not materialized in code.
Creating ActionTemporalPayload now risks silently widening Profile A.
```

---

### 4.2 Ports

Required:

```text
TemporalActWritePort
TemporalActReadPort
```

Recommended method granularity:

```text
TemporalActWritePort:
  insertCreated(...)
  cancelIfNonTerminal(...)
  markFiredIfDueAndNonTerminal(...)
  markMisfiredIfDueAndNonTerminal(...)

TemporalActReadPort:
  findById(...)
  listActive(...)
  findDue(...)
  findNonTerminalDueBefore(...)
  listTerminal(...)
  listMisfired(...)
```

Avoid exposing a generic transition method such as:

```text
transitionStatus(...)
```

because it allows invalid state transitions unless every call site re-implements the state machine.

---

### 4.3 Services

Required service surface:

```text
TemporalActService
  create signal TemporalAct
  cancel TemporalAct
  query TemporalAct state

TemporalEngineService
  poll/fire due acts
  classify recovery misfires
  enforce fire idempotency
```

Recommended internal operations for deterministic testing:

```text
createSignalTemporalAct(...)
cancelTemporalAct(...)
fireDueTemporalActOnce(habitatId, temporalActId, now)
pollDueOnce(habitatId, now)
classifyMisfires(habitatId, recoveryNow)
```

A background scheduler MAY wrap these operations, but tests MUST NOT depend on `Thread.sleep(...)` timing.

---

### 4.4 Persistence table

Required new H2 table:

```text
temporal_acts
```

The table does not currently exist.

---

### 4.5 Scheduler infrastructure

No temporal scheduler exists.

MU-005 may introduce a minimal seed scheduler, but the scheduler MUST remain a thin wrapper around deterministic service methods.

Unacceptable scheduler sources of truth:

```text
in-memory timers as semantic truth
Hub queue
Surface queue
broker delay queue as semantic truth
SC-B scheduled message as semantic truth
```

---

## 5. Recommended package structure

```text
src/main/java/com/sovereign/connect/core/temporal/model/
  TemporalAct.java
  TemporalActStatus.java
  TemporalActPayload.java
  SignalTemporalPayload.java
  CreatedByRef.java

src/main/java/com/sovereign/connect/core/temporal/port/
  TemporalActWritePort.java
  TemporalActReadPort.java

src/main/java/com/sovereign/connect/core/temporal/service/
  TemporalActService.java
  TemporalEngineService.java
  DefaultTemporalActService.java
  DefaultTemporalEngineService.java

src/main/java/com/sovereign/connect/adapter/persistence/
  H2TemporalActRepository.java
```

Do not place temporal types in:

```text
core.topology
core.scledger
core.materialization
adapter.bus
```

`core.temporal` is the correct package for a first-class SC-C runtime domain.

---

## 6. TemporalAct aggregate shape

Recommended seed aggregate:

```java
public record TemporalAct(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    TemporalActPayload payload,
    String notificationTargetRef,
    CreatedByRef createdByRef,
    TopologyVersion topologyVersionAtRegistration,
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,
    Instant terminalAt,
    String terminalReason
) {}
```

Critical constraints:

```text
- temporalActId is assigned by SC-C in the public create service.
- dueAt is an absolute UTC Instant.
- SC-C does not parse natural language or relative time expressions.
- notificationTargetRef is opaque and non-personal.
- createdByRef is opaque to SC-C but must remain non-personal and non-session-bound.
- topologyVersionAtRegistration is nullable and non-operative for SignalTemporalPayload in Profile A.
- topologyVersionAtRegistration MUST NOT trigger target validation or stale-target behavior in Profile A.
```

Repository-level tests may insert duplicate IDs to validate DB uniqueness.

The public create service MUST NOT accept caller-supplied `temporalActId`.

---

## 7. TemporalActStatus vocabulary

```java
public enum TemporalActStatus {
    PENDING,
    ARMED,
    FIRED,
    CANCELLED,
    EXPIRED,
    MISFIRED,
    FAILED
}
```

Terminal states:

```text
FIRED
CANCELLED
EXPIRED
MISFIRED
FAILED
```

Non-terminal states:

```text
PENDING
ARMED
```

Rules:

```text
A terminal TemporalAct MUST NOT return to non-terminal.
A terminal TemporalAct MUST NOT fire again.
MISFIRED is terminal but queryable for diagnostics.
```

---

## 8. Payload model

### 8.1 TemporalActPayload

```java
public sealed interface TemporalActPayload permits SignalTemporalPayload {
}
```

### 8.2 SignalTemporalPayload — implemented in Profile A

```java
public record SignalTemporalPayload(
    String label,
    String signalKind
) implements TemporalActPayload {}
```

Rules:

```text
label is a descriptive hint, not Surface UX rendering state.
signalKind is an opaque category such as timer, alarm or reminder.
Profile A behavior exists only for SignalTemporalPayload.
```

### 8.3 ActionTemporalPayload — reserved, not implemented

`ActionTemporalPayload` is reserved for Profile B.

It MUST NOT be created in MU-005.

It MUST NOT be referenced from Profile A code paths.

Profile B requires a later MIR/SDD patch and canonical `ActionRequest` code materialization.

---

## 9. `temporal_acts` H2 table

Recommended seed table:

```sql
CREATE TABLE IF NOT EXISTS temporal_acts (
    temporal_act_id       VARCHAR(36)   PRIMARY KEY,
    habitat_id            VARCHAR(255)  NOT NULL,
    status                VARCHAR(64)   NOT NULL
                          CHECK(status IN ('PENDING','ARMED','FIRED',
                                           'CANCELLED','EXPIRED','MISFIRED','FAILED')),
    due_at_ms             BIGINT        NOT NULL,
    payload_type          VARCHAR(255)  NOT NULL,
    payload_json          CLOB          NOT NULL,
    notification_target_ref VARCHAR(512),
    created_by_ref_json   CLOB          NOT NULL,
    topology_version_at_registration VARCHAR(255),
    created_at_ms         BIGINT        NOT NULL,
    updated_at_ms         BIGINT        NOT NULL,
    fired_at_ms           BIGINT,
    terminal_at_ms        BIGINT,
    terminal_reason       VARCHAR(2048)
);
```

Required indexes:

```sql
CREATE INDEX IF NOT EXISTS ix_temporal_acts_due_status
ON temporal_acts(habitat_id, status, due_at_ms);

CREATE INDEX IF NOT EXISTS ix_temporal_acts_status
ON temporal_acts(habitat_id, status);
```

The `status` CHECK constraint enforces the state vocabulary at DB level.

---

## 10. Persistence adapter decision

### 10.1 Preferred approach

Create a sibling adapter:

```text
H2TemporalActRepository
```

It implements:

```text
TemporalActWritePort
TemporalActReadPort
```

Rationale:

```text
H2BaseTopologyRepository already aggregates several seed-local responsibilities.
MU-011 and MU-012 hardened persistence boundaries and materialization port boundaries.
TemporalActs are a first-class runtime domain, not Base Topology structure.
A sibling adapter avoids growing H2BaseTopologyRepository into a monolithic persistence object.
```

### 10.2 Transaction coordination

The Temporal Engine or an explicit transactional application service MUST coordinate:

```text
TemporalActWritePort
ScLedgerWritePort
ScOutboxWritePort
```

inside a single transaction using the same `DataSource`.

Recommended mechanism:

```text
TransactionTemplate + DataSourceTransactionManager
```

Acceptable only if proven by code surface:

```text
another Spring/JDBC transaction mechanism that guarantees temporal_acts + ledger + outbox atomicity
```

### 10.3 Fallback option

Extending `H2BaseTopologyRepository` with temporal methods is allowed only if the MIR explicitly accepts seed-local adapter consolidation.

If chosen, the execution package MUST justify why it does not regress MU-011/MU-012 boundary discipline.

---

## 11. Fire idempotency — critical mechanism

Fire idempotency is not a separate port.

It is enforced by the firing transaction.

Required SQL shape:

```sql
UPDATE temporal_acts
SET status = 'FIRED',
    fired_at_ms = ?,
    terminal_at_ms = ?,
    updated_at_ms = ?
WHERE temporal_act_id = ?
  AND habitat_id = ?
  AND status IN ('PENDING', 'ARMED')
  AND due_at_ms <= ?;
```

If update count is `0`:

```text
The act was already terminal, not due, missing, or lost a race.
Do not append ledger.
Do not append outbox.
Return current state or skipped outcome.
```

If update count is `1`:

```text
Append ledger entry.
Append outbox entry for TimerFired / TemporalActFired.
Commit all in the same transaction.
```

Secondary idempotency guard:

```text
sc_c_ledger_entries UNIQUE(habitat_id, idempotency_key)
```

Recommended firing ledger idempotency key:

```text
temporal-act-fired:{temporalActId}
```

This secondary guard is not a substitute for the conditional update.

Both are required.

---

## 12. Atomic transaction rules

### 12.1 Create

Profile A create MUST commit atomically:

```text
INSERT temporal_acts row with status=PENDING
append TEMPORAL_ACT_CREATED ledger entry
```

If `TemporalActCreated` is dispatchable in the selected seed profile, the corresponding outbox entry MUST commit in the same transaction.

Default recommendation for MU-005:

```text
TemporalActCreated is ledger-only.
```

A persisted `temporal_acts` row without its required `TemporalActCreated` ledger entry is a consistency violation.

---

### 12.2 Cancel

Cancel operation shape:

```sql
UPDATE temporal_acts
SET status = 'CANCELLED',
    terminal_at_ms = ?,
    updated_at_ms = ?
WHERE temporal_act_id = ?
  AND habitat_id = ?
  AND status IN ('PENDING', 'ARMED');
```

If update count is `0`:

```text
The act is missing or already terminal.
Do not append cancellation ledger as a new semantic cancellation.
Return current terminal state or not-found outcome.
```

If update count is `1`:

```text
Append TEMPORAL_ACT_CANCELLED ledger entry in the same transaction.
```

Default recommendation for MU-005:

```text
TemporalActCancelled is ledger-only.
```

---

### 12.3 Fire — Profile A

When a `SignalTemporalPayload` fires, commit atomically:

```text
status = FIRED
firedAt
terminalAt
TemporalActFired / TimerFired ledger entry
TemporalActFired / TimerFired outbox entry
```

The firing record MUST carry `notificationTargetRef` opaquely.

SC-C MUST NOT resolve `notificationTargetRef` into:

```text
user identity
session identity
authority
policy
projection
surface rendering
```

---

### 12.4 MISFIRED — recovery classification

MISFIRED classification shape:

```sql
UPDATE temporal_acts
SET status = 'MISFIRED',
    terminal_at_ms = ?,
    updated_at_ms = ?
WHERE habitat_id = ?
  AND status IN ('PENDING', 'ARMED')
  AND due_at_ms <= ?;
```

For each classified act, append a `TEMPORAL_ACT_MISFIRED` ledger entry in the same recovery transaction or bounded recovery transaction batch.

Default recommendation for MU-005:

```text
TemporalActMisfired is ledger-only.
```

MISFIRED acts are terminal and queryable.

They MUST NOT be re-fired.

---

### 12.5 FAILED

`FAILED` is reserved for cases where an act became eligible but firing/handoff failed after eligibility was established.

For Profile A, prefer transaction rollback if ledger/outbox append fails before commit.

If the firing transition committed but later delivery fails:

```text
TemporalAct remains FIRED.
Delivery failure is outbox delivery state, not semantic firing failure.
```

---

## 13. Polling engine and deterministic execution

The seed may use database-backed polling.

Required deterministic methods:

```text
pollDueOnce(habitatId, now)
fireDueTemporalActOnce(habitatId, temporalActId, now)
classifyMisfires(habitatId, recoveryNow)
```

A background scheduler MAY wrap `pollDueOnce(...)`.

Tests MUST NOT depend on `Thread.sleep(...)` timing.

Recommended profile:

```text
configurable polling interval
default <= 1000 ms for Profile A near-real-time TimerFired observability
```

Boundary rule:

```text
Countdown rendering remains outside SC-C.
Consumers compute remaining duration from dueAt - Instant.now().
SC-C MUST NOT expose remainingMs, countdown ticks or UX countdown state as canonical engine state.
```

The polling loop MUST NOT become:

```text
outbox dispatcher
SC-B delivery loop
NATS publisher
retry worker
adapter command loop
```

---

## 14. Recovery integration

On SC-C startup, before write-capable services and Temporal Engine firing are enabled, recovery must classify overdue non-terminal TemporalActs.

Profile A recovery operation:

```text
find non-terminal acts where due_at_ms <= recoveryNow
mark them MISFIRED
append TemporalActMisfired ledger entries
leave MISFIRED acts queryable
never re-fire them
```

Feature-gate rule:

```text
If temporal_acts table exists in the selected schema version:
  Recovery MUST validate TemporalAct status vocabulary and classify non-terminal overdue rows.

If temporal_acts table does not exist because the schema version predates TemporalActs support:
  Recovery MUST record TemporalActs recovery as NOT_APPLICABLE.

If temporal_acts table is required by the selected schema version but missing:
  Recovery MUST treat it as schema consistency failure.
```

---

## 15. Query surface

`TemporalActReadPort` must support at minimum:

```java
Optional<TemporalAct> findById(String habitatId, String temporalActId);
List<TemporalAct> listActive(String habitatId);
List<TemporalAct> findDue(String habitatId, Instant now);
List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff);
List<TemporalAct> listTerminal(String habitatId);
List<TemporalAct> listMisfired(String habitatId);
```

Rules:

```text
listActive returns PENDING / ARMED only.
listActive MUST NOT return FIRED, CANCELLED, EXPIRED, MISFIRED or FAILED.
MISFIRED remains queryable through listMisfired or terminal queries.
SC-C exposes dueAt.
SC-C does not expose remainingMs.
```

Query by `notificationTargetRef` is optional and must remain opaque.

If implemented, it MUST NOT become:

```text
identity filtering
session filtering
authority filtering
policy filtering
Projection filtering
```

---

## 16. Serialization strategy

`TemporalActPayload` is a sealed interface.

Recommended persistence strategy:

```text
payload_type column stores discriminator.
payload_json column stores serialized payload body.
```

Profile A discriminator:

```text
SignalTemporalPayload
```

Deserialization rule:

```java
TemporalActPayload payload = switch (payloadType) {
    case "SignalTemporalPayload" -> readJson(payloadJson, SignalTemporalPayload.class);
    default -> throw new IllegalArgumentException("Unknown payload type: " + payloadType);
};
```

Do not deserialize `ActionTemporalPayload` in MU-005.

Unknown payload type is a schema/data consistency error for Profile A.

---

## 17. Invariants from prior MUs

MU-005 must preserve:

```text
MU-011: H2BaseTopologyRepository.save() remains structural-only.
MU-012: DefaultTopologyMaterializationService does not import H2/JDBC/SQL.
MU-013: validateTopology() spatial coherence checks remain intact.
MU-014: replayPort.findDecision() before materialize switch remains intact.
MU-015: appendOutboxEntry rejects non-PENDING entries.
MU-015: appendLedgerEntry remains INSERT-only, no MERGE.
MU-015: no dispatcher, claim loop, polling delivery loop or retry worker is introduced.
```

All 75 baseline tests must continue passing.

---

## 18. Files to create / modify

### 18.1 New production files

```text
src/main/java/com/sovereign/connect/core/temporal/model/TemporalAct.java
src/main/java/com/sovereign/connect/core/temporal/model/TemporalActStatus.java
src/main/java/com/sovereign/connect/core/temporal/model/TemporalActPayload.java
src/main/java/com/sovereign/connect/core/temporal/model/SignalTemporalPayload.java
src/main/java/com/sovereign/connect/core/temporal/model/CreatedByRef.java

src/main/java/com/sovereign/connect/core/temporal/port/TemporalActWritePort.java
src/main/java/com/sovereign/connect/core/temporal/port/TemporalActReadPort.java

src/main/java/com/sovereign/connect/core/temporal/service/TemporalActService.java
src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineService.java
src/main/java/com/sovereign/connect/core/temporal/service/DefaultTemporalActService.java
src/main/java/com/sovereign/connect/core/temporal/service/DefaultTemporalEngineService.java

src/main/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepository.java
```

### 18.2 New test files

```text
src/test/java/com/sovereign/connect/core/temporal/TemporalActSeedTest.java
```

Optional split if test grows too large:

```text
src/test/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepositoryTest.java
src/test/java/com/sovereign/connect/core/temporal/TemporalEngineSeedTest.java
```

### 18.3 Modified production files

Expected minimal modifications:

```text
Build / configuration files only if needed for transaction support.
```

Avoid modifying:

```text
H2BaseTopologyRepository.java
```

unless the MIR explicitly chooses seed-local consolidation.

If H2BaseTopologyRepository must be modified for shared schema bootstrap, the prompt must restrict changes to schema creation only and must not add temporal business logic there.

---

## 19. Scope boundaries — explicit non-goals

MU-005 MUST NOT implement:

```text
ActionTemporalPayload runtime behavior
ActionRequest code materialization
Profile B behavior
validateTarget(...) for temporal actions
command dispatch
COMMAND outbox handoff
outbox dispatcher / delivery loop
SC-B runtime
NATS / JetStream
SC-D adapter execution
distributed scheduling
clustering / leader election
calendar semantics
recurrence
cron
natural language time parsing
Hub memory
Surface UX
session/user identity
authority/policy scheduling
Flyway / production SQLite migrations
```

---

## 20. Test scope — minimum required

Minimum test suite:

```text
T-001  createSignalTemporalActPersistsDurably
T-002  repositoryDuplicateTemporalActIdRejected
T-003  createTemporalActAlsoCreatesTemporalActCreatedLedgerEntry
T-004  cancelNonTerminalActSucceedsAndCreatesLedgerEntry
T-005  fireThenCancelReturnsAlreadyTerminal
T-006  cancelThenFireDoesNotCreateTimerFired
T-007  listActiveReturnsOnlyNonTerminalActs
T-008  fireTransitionAdvancesStatusToFired
T-009  fireIdempotencyGuardPreventsDoubleFire
T-010  fireBeforeDueDoesNotFire
T-011  fireLedgerEntryCreatedAtomically
T-012  fireOutboxEntryCreatedAtomically
T-013  notificationTargetRefCarriedInFiringRecord
T-014  misfiredClassificationOnStartupForOverdueActs
T-015  misfiredActIsNotRefired
T-016  misfiredActRemainsQueryable
T-017  pollingIntervalIsConfigurable
T-018  durabilityAfterRepositoryRecreation
T-019  domainServicesHaveNoSqlImports
T-020  baselineScLedgerAndOutboxInvariantsRemainIntact
```

Expected result after implementation:

```text
Baseline: 75 tests
New tests: >= 20
Expected total: >= 95 tests
Failures: 0
Errors: 0
Skipped: 0
```

The exact total may exceed 95 if tests are split.

---

## 21. Required implementation decisions for decision-confirmation.md

The MIR should confirm these decisions before prompt generation:

```text
DEC-005-001  Profile A only: SignalTemporalPayload.
DEC-005-002  ActionTemporalPayload is reserved and not materialized.
DEC-005-003  H2TemporalActRepository is preferred over H2BaseTopologyRepository expansion.
DEC-005-004  TransactionTemplate / shared DataSource transaction boundary is required.
DEC-005-005  TemporalActCreated is ledger-only in seed.
DEC-005-006  TimerFired / TemporalActFired is ledger + outbox in seed.
DEC-005-007  TemporalActCancelled is ledger-only in seed.
DEC-005-008  TemporalActMisfired is ledger-only in seed.
DEC-005-009  No background timing-dependent tests; deterministic one-shot methods required.
DEC-005-010  No SC-B / NATS / dispatcher / ActionRequest in MU-005.
```

---

## 22. Risks and mitigations

### R-005-001 — Transaction split

Risk:

```text
TemporalAct state changes commit separately from ledger/outbox records.
```

Mitigation:

```text
Use one TransactionTemplate over a shared DataSource for temporal_acts + ledger + outbox writes.
Test atomicity by forcing or simulating conflict paths where possible.
```

---

### R-005-002 — H2BaseTopologyRepository monolith

Risk:

```text
Adding temporal persistence into H2BaseTopologyRepository regresses boundary discipline.
```

Mitigation:

```text
Prefer H2TemporalActRepository.
Only allow H2BaseTopologyRepository expansion with explicit MIR decision.
```

---

### R-005-003 — Profile B bleed

Risk:

```text
ActionTemporalPayload, ActionRequest, validateTarget or COMMAND outbox handoff enters Profile A.
```

Mitigation:

```text
Do not create ActionTemporalPayload in MU-005.
Add tests/assertions that Profile A has no action dispatch path.
```

---

### R-005-004 — Flaky scheduler tests

Risk:

```text
Tests rely on Thread.sleep or real-time timing.
```

Mitigation:

```text
Use injectable Clock and deterministic pollDueOnce/fireDueTemporalActOnce methods.
```

---

### R-005-005 — Duplicate semantic fire

Risk:

```text
Repeated poll or concurrent fire creates duplicate TimerFired records.
```

Mitigation:

```text
Conditional UPDATE with due_at_ms <= now and status guard.
Ledger idempotency key temporal-act-fired:{temporalActId}.
Outbox idempotency key stable from ledger_entry_id + outbound kind / topic / lane.
```

---

## 23. Acceptance disposition

```text
Recommended decision: Proceed to MIR and implementation package.
Surface category:     Greenfield-with-neighbors / bounded infrastructure integration.
Expected acceptance:  Validated L4.
Primary risk:         Atomic transaction boundary across temporal_acts + ledger + outbox.
Secondary risk:       Scheduler/threading model and sealed payload serialization.
Key invariant:        Fire idempotency via UPDATE row count + ledger idempotency, not by port flag.
Baseline tests:       75 — none may regress.
New tests expected:   >= 20.
```

Recommended path:

```text
docs/mir/mir-005/code-surface-audit.md
```

Recommended next artifact:

```text
MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001 v0.1.0-draft
```
