# context.md — MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001

```text
Document:        context.md
Version:         v0.2.1
MU:              MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot: MU-016
Canonical path:   docs/mir/mir-016/
MIR:             MIR-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
CSA:             code-surface-audit-MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001 v0.1.1-draft
Baseline ZIP:    sovereign-connect-005-unblocked-hardening-2.zip
Baseline commit: a76d261 Merge pull request #17 from Marcelo-314/feat/sc-c-temporal-acts-seed
Baseline tests:  101 / 0 failures / 0 errors / 0 skipped
```

---

## 0. Purpose of this file

This context file bridges the approved corpus (architecture, PDRs, SDDs) to the
exact current state of the repository. Every section resolves something a Codex
agent would otherwise have to infer. Nothing here should require a second look-up.

The implementation is a focused hardening of the existing TemporalActs seed — not
a redesign of topology, ledger, or outbox code.

---

## 1. Industrial v1 boundary — what this MU does and does not touch

**In scope:**

```text
TemporalActs domain (core/temporal/**)
H2TemporalActRepository -> industrial SQLite adapter path
TemporalActService -> TemporalActApplicationPort
TemporalEngineService -> bounded polling + recovery gate
TemporalEngineRunner -> SmartLifecycle wrapper
New: TemporalActObservationPort + TemporalActObservation DTO
New: temporal_request_idempotency table + store
New: TemporalEngineLifecycle (Spring-managed)
New: TemporalEngineProperties (configuration)
New: SQLite Flyway migrations
New: application.yml / application-test.yml profiles
```

**Do NOT touch — these are correct and must be preserved byte-for-byte:**

```text
H2BaseTopologyRepository (ledger/outbox storage — MU-015 validated)
ScLedgerWritePort / ScOutboxWritePort / their models (MU-015 contract)
BaseTopologyService and all topology services
Any test outside TemporalActSeedTest unless adding new tests
SovereignConnectApplication business/bootstrap behavior (prefer a dedicated @Configuration class; minimal bean registration only if unavoidable)
```

**Do NOT introduce under any circumstances:**

```text
ActionTemporalPayload / ActionRequest
SC-B / NATS / JetStream / outbox dispatcher
View Composer / Effective Access Boundary
Session / Identity / Authority / Policy
```

---

## 2. Exact current code surface — read before modifying anything

### 2.1 TemporalAct aggregate (preserve structure, evolve constructor callers)

File: `src/main/java/com/sovereign/connect/core/temporal/model/TemporalAct.java`

```java
public record TemporalAct(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    TemporalActPayload payload,
    String notificationTargetRef,   // currently nullable — industrial v1 makes this required
    CreatedByRef createdByRef,
    String topologyVersionAtRegistration,
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,
    Instant terminalAt,
    String terminalReason
) {}
```

`TemporalActPayload` is a sealed interface permitting only `SignalTemporalPayload`. Do not add
`ActionTemporalPayload`. This is an invariant enforced by the sealed interface at compile time.

### 2.2 Current ports (persist; new ports are added alongside, not replacing)

File: `src/main/java/com/sovereign/connect/core/temporal/port/TemporalActReadPort.java`

```java
public interface TemporalActReadPort {
    Optional<TemporalAct> findById(String habitatId, String temporalActId);
    List<TemporalAct> listActive(String habitatId);
    List<TemporalAct> findDue(String habitatId, Instant now);                         // engine-internal
    List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff);     // engine-internal
    List<TemporalAct> listTerminal(String habitatId);
    List<TemporalAct> listMisfired(String habitatId);
}
```

`findDue` and `findNonTerminalDueBefore` are engine-internal. They must NOT appear in
`TemporalActObservationPort`. The separation is intentional and load-bearing.

File: `src/main/java/com/sovereign/connect/core/temporal/port/TemporalActWritePort.java`

```java
public interface TemporalActWritePort {
    void insertCreated(TemporalAct act);
    int cancelIfNonTerminal(String habitatId, String temporalActId, Instant now);
    int markFiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant now);
    int markMisfiredIfDueAndNonTerminal(String habitatId, String temporalActId,
                                        Instant cutoff, Instant now);
}
```

These port signatures must not change. The industrial SQLite adapter implements both ports
the same way `H2TemporalActRepository` does: one class, two interfaces.

### 2.3 TemporalActService — exact current methods

File: `src/main/java/com/sovereign/connect/core/temporal/service/TemporalActService.java`

Current constructor (6 args — preserve or add idempotency store as 7th):

```java
public TemporalActService(
    TemporalActWritePort writePort,
    TemporalActReadPort readPort,
    ScLedgerWritePort ledgerPort,
    TransactionTemplate txTemplate,
    ObjectMapper objectMapper,
    Clock clock
)
```

Current create (returns String temporalActId — this becomes `TemporalActApplicationPort`):

```java
public String createSignalTemporalAct(
    String habitatId,
    SignalTemporalPayload payload,
    Instant dueAt,
    String notificationTargetRef,   // currently accepts null — must reject null/blank in v1
    CreatedByRef createdByRef       // must add idempotencyKey + requestedAt for v1
)
```

Current cancel (returns int updated count — this becomes `CancelTemporalActResult`):

```java
public int cancelTemporalAct(String habitatId, String temporalActId, Instant now)
```

**Migration rule:** The existing `TemporalActService` can implement `TemporalActApplicationPort`
or delegate to a new `TemporalActApplicationService`. Existing tests call the old signatures
directly — preserve those call sites by keeping the old methods as package-private delegation
or by making the new port methods the canonical entry and adapting tests. Either approach is
valid; do not break existing test assertions.

### 2.4 TemporalEngineService — exact current methods and the critical nullableValue invariant

File: `src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineService.java`

Current constructor (7 args):

```java
public TemporalEngineService(
    TemporalActWritePort writePort,
    TemporalActReadPort readPort,
    ScLedgerWritePort ledgerPort,
    ScOutboxWritePort outboxPort,
    TransactionTemplate txTemplate,
    ObjectMapper objectMapper,
    Clock clock
)
```

Current `pollDueOnce` — unbounded loop, must be bounded:

```java
public void pollDueOnce(String habitatId, Instant now) {
    for (TemporalAct act : readPort.findDue(habitatId, now)) {       // NO LIMIT — must bound
        fireDueTemporalActOnce(habitatId, act.temporalActId(), now);
    }
}
```

**Invariant B-11-a:** Add `maxDueActsPerCycle` parameter. Pass it to `findDue(habitatId, now, maxDueActsPerCycle)`
or take the first N acts at the service level. Both approaches are valid; what matters is that one
polling cycle processes at most `maxDueActsPerCycle` acts.

Current `classifyMisfires` — unbounded, must be bounded:

```java
public void classifyMisfires(String habitatId, Instant recoveryNow) {
    List<TemporalAct> overdue = readPort.findNonTerminalDueBefore(habitatId, recoveryNow);  // NO LIMIT
    for (TemporalAct act : overdue) {
        txTemplate.executeWithoutResult(status -> {
            int updated = writePort.markMisfiredIfDueAndNonTerminal(
                habitatId, act.temporalActId(), recoveryNow, recoveryNow);
            if (updated == 1) {
                ledgerPort.appendLedgerEntry(misfiredLedgerEntry(act, recoveryNow));
            }
        });
    }
}
```

**Invariant B-11-b:** Add `maxRecoveryBatch` parameter. If overdue count exceeds the batch,
process the batch and return a `TemporalRecoveryResult` indicating whether classification is
complete or resume is needed.

**CRITICAL — the nullableValue coercion must be eliminated:**

In `firedLedgerEntry(...)` there is:

```java
private String nullableValue(String value) {
    return value == null ? "" : value;
}
```

This method silently converts a null `notificationTargetRef` to an empty string in the firing
ledger payload. In industrial v1 `notificationTargetRef` is NOT NULL at the schema level — if a
null value somehow reaches this point, it is an invariant violation, not something to silently
coerce. Remove `nullableValue(...)` and use `act.notificationTargetRef()` directly. The schema
constraint will prevent null from persisting; the validation layer will reject null at request time.

`timerFiredOutboxEntry(...)` already uses `act.notificationTargetRef()` directly (no
nullableValue call there) — that is correct, preserve it.

### 2.5 TemporalEngineRunner — exact current body (preserve all loop invariants)

File: `src/main/java/com/sovereign/connect/core/temporal/service/TemporalEngineRunner.java`

```java
public final class TemporalEngineRunner implements AutoCloseable {

    public static final long DEFAULT_POLLING_INTERVAL_MS = 1000L;

    private final TemporalEngineService engine;
    private final long pollingIntervalMs;
    private final String habitatId;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread runnerThread;
    private volatile RuntimeException lastFailure;

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        runnerThread = Thread.ofVirtual()
            .name("temporal-engine-runner")
            .start(this::runLoop);
    }

    private void runLoop() {
        try {
            while (running.get()) {
                try {
                    engine.pollDueOnce(habitatId, Instant.now(clock));
                } catch (RuntimeException ex) {
                    lastFailure = ex;              // stores failure — currently no backpressure
                }
                if (!running.get()) break;
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(pollingIntervalMs));
            }
        } finally {
            running.set(false);
        }
    }

    public void stop() {
        running.set(false);
        Thread thread = runnerThread;
        if (thread != null) LockSupport.unpark(thread);
    }

    public boolean awaitStopped(Duration timeout) { ... }
}
```

**Preserve exactly:** virtual thread, `LockSupport.parkNanos`, `AtomicBoolean`, `awaitStopped`.
**Do NOT use:** `@Scheduled`, `Thread.sleep`, `ScheduledExecutorService`.

**Add to runLoop:** consecutive failure tracking and backpressure. After N consecutive failures,
park for `failureBackoffMs` instead of `pollingIntervalMs`:

```java
private int consecutiveFailures = 0;

// In runLoop catch block:
lastFailure = ex;
consecutiveFailures++;
long waitMs = consecutiveFailures >= maxConsecutiveFailures
    ? failureBackoffMs
    : pollingIntervalMs;
LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(waitMs));
// On success: consecutiveFailures = 0;
```

### 2.6 H2TemporalActRepository — exact schema and critical column mapping differences

File: `src/main/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepository.java`

The H2 seed schema uses these column names (different from industrial schema):

| H2 seed column | Industrial SQLite column | Notes |
|---|---|---|
| `payload_type` (VARCHAR) | `payload_kind` (TEXT + CHECK IN ('SIGNAL')) | rename + constraint |
| `notification_target_ref` (nullable) | `notification_target_ref` NOT NULL | add NOT NULL |
| `created_by_ref_json` (CLOB) | `created_by_ref` (TEXT NOT NULL) | flatten to opaque string |
| _(absent)_ | `label` TEXT NOT NULL | denormalized from SignalTemporalPayload |
| _(absent)_ | `signal_kind` TEXT NOT NULL | denormalized from SignalTemporalPayload |
| _(absent)_ | `requested_at_ms` INTEGER | nullable, from request |

The seed stores `SignalTemporalPayload` as JSON blob under `payload_json`. The industrial schema
denormalizes `label` and `signal_kind` directly into the row (in addition to or instead of
`signal_payload_json`). This means `insertCreated` must extract `payload.label()` and
`payload.signalKind()` and write them as separate columns.

**readPayload — the fail-closed rewrite (replace throw with quarantine):**

Current (throws, crashes recovery):

```java
private TemporalActPayload readPayload(String payloadType, String payloadJson) {
    return switch (payloadType) {
        case "SignalTemporalPayload" -> readJson(payloadJson, SignalTemporalPayload.class);
        default -> throw new IllegalArgumentException("Unknown temporal payload type in MU-005: " + payloadType);
    };
}
```

Industrial (fail-closed — a TemporalAct with unknown payload must not crash the engine):

```java
private TemporalActPayload readPayload(String payloadKind, String payloadJson) {
    return switch (payloadKind) {
        case "SIGNAL" -> readJson(payloadJson, SignalTemporalPayload.class);
        default -> {
            // Record a diagnostic. Caller (recovery or engine) must handle FAILED/quarantine.
            // Never throw here during list/scan operations — caller must check for null and
            // transition the act to FAILED via writePort.markFailed(...) in its own transaction.
            log.warn("Unknown payload kind '{}' for act in habitat — act will be quarantined", payloadKind);
            yield null;   // null sentinel: caller transitions to FAILED
        }
    };
}
```

Add `markFailed(String habitatId, String temporalActId, String reason, Instant now)` to
`TemporalActWritePort` and implement it in both repositories.

**The H2 test repository must continue to work for seed tests.** Do not delete
`H2TemporalActRepository`. Add `SQLiteTemporalActRepository` as the industrial adapter.

### 2.7 Test Fixture wiring — how tests currently construct the stack

The current test `Fixture` wires everything manually (no Spring context):

```java
private Fixture fixture(String jdbcUrl) {
    DataSource dataSource = dataSource(jdbcUrl);
    ObjectMapper mapper = mapper();
    H2TemporalActRepository repository = new H2TemporalActRepository(dataSource, mapper, clock);
    H2BaseTopologyRepository ledgerOutboxRepository = new H2BaseTopologyRepository(dataSource, mapper, clock);
    TransactionTemplate txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    TemporalActService actService = new TemporalActService(
        repository, repository, ledgerOutboxRepository, txTemplate, mapper, clock
    );
    TemporalEngineService engine = new TemporalEngineService(
        repository, repository, ledgerOutboxRepository, ledgerOutboxRepository, txTemplate, mapper, clock
    );
    return new Fixture(jdbcUrl, repository, actService, engine);
}
```

Note: `H2TemporalActRepository` is passed as **both** `TemporalActWritePort` AND `TemporalActReadPort`.
The industrial `SQLiteTemporalActRepository` must implement both ports the same way.

New tests for industrial contracts may use `@SpringBootTest` with a SQLite test DataSource, but
must not break existing tests. Add new test classes rather than modifying `TemporalActSeedTest`.

---

## 3. New types to create — exact signatures

### 3.1 TemporalActObservation (DTO — no aggregate internals)

Package: `core.temporal.observation`

```java
public record TemporalActObservation(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    String payloadKind,           // "SIGNAL" for v1
    String label,
    String signalKind,
    String notificationTargetRef,
    String createdByRef,          // opaque string, not CreatedByRef aggregate
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,
    Instant terminalAt,
    String terminalReason
) {}
```

Note: `createdByRef` is exposed as a `String` in the observation DTO (opaque reference), not
as `CreatedByRef`. This decouples the northbound observation contract from the internal model.

### 3.2 TemporalActObservationPort

Package: `core.temporal.observation`

```java
public interface TemporalActObservationPort {
    List<TemporalActObservation> listActive(String habitatId);
    Optional<TemporalActObservation> findById(String habitatId, String temporalActId);
    List<TemporalActObservation> listTerminal(String habitatId, int maxResults);
    List<TemporalActObservation> listMisfired(String habitatId, int maxResults);
}
```

`listTerminal` and `listMisfired` take an explicit `maxResults` parameter — the industrial
implementation uses this to enforce bounded result sets (default from `TemporalEngineProperties`).
The port signature is bounded by design to prevent accidental unbounded reads by callers.

### 3.3 TemporalActApplicationPort

Package: `core.temporal.application`

```java
public interface TemporalActApplicationPort {
    CreateSignalTemporalActResult createSignalTemporalAct(CreateSignalTemporalActRequest request);
    CancelTemporalActResult cancelTemporalAct(CancelTemporalActRequest request);
}
```

This port is write/request-only. It MUST NOT expose any read/query method.

### 3.4 Request DTOs

```java
public record CreateSignalTemporalActRequest(
    String habitatId,
    Instant dueAt,
    String label,
    String signalKind,
    String notificationTargetRef,  // required, non-null, non-blank
    String createdByRef,           // opaque, required
    String idempotencyKey,         // required
    Instant requestedAt            // required; derive from Clock.instant() before building DTO
) {}

public record CancelTemporalActRequest(
    String habitatId,
    String temporalActId,
    String requestedByRef,         // opaque, required
    String idempotencyKey,         // required
    String reason,                 // nullable
    Instant requestedAt            // required
) {}
```

`requestedAt` must never be null at the canonical DTO boundary. If a caller does not provide it,
derive it before constructing the DTO: `Instant.now(clock)`.

`requestedAt` must NOT be part of the semantic fingerprint for idempotency — two replays of
the same logical create at different times should still be treated as the same request.

### 3.5 Result types

```java
public sealed interface CreateSignalTemporalActResult {
    record Accepted(TemporalActObservation temporalAct)     implements CreateSignalTemporalActResult {}
    record IdempotentReplay(TemporalActObservation act)     implements CreateSignalTemporalActResult {}
    record Rejected(TemporalRequestRejection rejection)     implements CreateSignalTemporalActResult {}
    record Failed(TemporalRuntimeFailure failure)           implements CreateSignalTemporalActResult {}
}

public sealed interface CancelTemporalActResult {
    record Cancelled(TemporalActObservation temporalAct)    implements CancelTemporalActResult {}
    record AlreadyTerminal(TemporalActObservation act)      implements CancelTemporalActResult {}
    record NotFound(String habitatId, String id)            implements CancelTemporalActResult {}
    record IdempotentReplay(TemporalActObservation act)     implements CancelTemporalActResult {}
    record Rejected(TemporalRequestRejection rejection)     implements CancelTemporalActResult {}
    record Failed(TemporalRuntimeFailure failure)           implements CancelTemporalActResult {}
}
```

**The Rejected/Failed separation is non-negotiable.** `Rejected` = domain validation error
(caller can fix and retry). `Failed` = runtime/infra/engine error (caller cannot fix).

```java
public record TemporalRequestRejection(TemporalRequestRejectionCode code, String message) {}

public enum TemporalRequestRejectionCode {
    INVALID_HABITAT_ID, INVALID_TEMPORAL_ACT_ID,
    INVALID_DUE_AT, INVALID_REQUESTED_AT, PAST_DUE_AT_NOT_SUPPORTED,
    MISSING_LABEL, MISSING_SIGNAL_KIND,
    MISSING_NOTIFICATION_TARGET_REF, MISSING_CREATED_BY_REF, MISSING_REQUESTED_BY_REF,
    MISSING_IDEMPOTENCY_KEY, IDEMPOTENCY_CONFLICT,
    UNSUPPORTED_PAYLOAD_KIND, ACTION_TEMPORAL_PAYLOAD_NOT_SUPPORTED_IN_V1,
    SESSION_IDENTITY_AUTHORITY_POLICY_FIELD_NOT_ALLOWED
}

public record TemporalRuntimeFailure(TemporalRuntimeFailureCode code, String message) {}

public enum TemporalRuntimeFailureCode {
    STORAGE_UNAVAILABLE, SCHEMA_INCOMPATIBLE,
    SERIALIZATION_FAILURE, DESERIALIZATION_FAILURE,
    LEDGER_APPEND_FAILED, OUTBOX_APPEND_FAILED,
    IDEMPOTENCY_STORE_UNAVAILABLE,
    TEMPORAL_ENGINE_NOT_READY, TEMPORAL_ENGINE_DISABLED,
    RECOVERY_NOT_COMPLETED, SINGLE_NODE_GUARD_VIOLATED,
    INTERNAL_FAILURE
}
```

### 3.6 TemporalEngineLifecycle (SmartLifecycle — the Spring wiring)

Package: `core.temporal.engine`

```java
@Component
public class TemporalEngineLifecycle implements SmartLifecycle {

    private final TemporalEngineService engineService;
    private final TemporalEngineRunner runner;
    private final TemporalEngineProperties properties;
    private final TemporalEngineHealth health;
    private final Clock clock;
    private volatile boolean running = false;

    @Override
    public void start() {
        health.transitionTo(TemporalEngineStatus.RECOVERING);
        // Step 1: classify misfires before any polling
        engineService.classifyMisfires(
            properties.habitatId(),
            Instant.now(clock),
            properties.maxRecoveryBatchSize()
        );
        health.transitionTo(TemporalEngineStatus.RUNNING);
        runner.start();
        running = true;
    }

    @Override
    public void stop() {
        runner.stop();
        runner.awaitStopped(Duration.ofMillis(properties.shutdownTimeoutMs()));
        running = false;
        health.transitionTo(TemporalEngineStatus.STOPPED);
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public int getPhase() {
        // Run after DataSource, Flyway, and SC-C core recovery beans (which use lower phase numbers).
        // Spring starts beans from lowest phase to highest; this must be > SC-C recovery bean phase.
        return Integer.MAX_VALUE - 100;
    }
}
```

**Phase ordering rule:** `TemporalEngineLifecycle.getPhase()` must return a value HIGHER than
any SC-C topology/persistence recovery bean. Topology services start at lower phases (default
0 or low positive). The Temporal Engine must start last. `Integer.MAX_VALUE - 100` is safe.

**Recovery gate rule:** `classifyMisfires(...)` runs inside `start()` before `runner.start()`.
Until `start()` completes, the engine is not running. Any `TemporalActApplicationPort` call
that checks `health.isReady()` before executing must return `Failed(RECOVERY_NOT_COMPLETED)`
if called before the lifecycle bean is started.

### 3.7 TemporalEngineHealth

Package: `core.temporal.engine`

```java
public class TemporalEngineHealth {
    private volatile TemporalEngineStatus status = TemporalEngineStatus.STOPPED;
    private volatile Instant lastPollAt;
    private volatile Instant lastSuccessfulPollAt;
    private volatile RuntimeException lastFailure;
    private volatile long firedTotal;
    private volatile long misfiredTotal;
    private volatile long cancelledTotal;
    private volatile long failedTotal;
    private volatile long skippedTotal;     // due to idempotency guard (update count = 0)

    public void transitionTo(TemporalEngineStatus newStatus) { this.status = newStatus; }
    public boolean isReady() {
        return status == TemporalEngineStatus.RUNNING || status == TemporalEngineStatus.DEGRADED;
    }
    // getters ...
}

public enum TemporalEngineStatus {
    STOPPED, STARTING, RECOVERING, RUNNING, DEGRADED, FAILED, DISABLED
}
```

### 3.8 TemporalEngineProperties

Package: `core.temporal.engine`

```java
@ConfigurationProperties(prefix = "sovereign.temporal.engine")
public record TemporalEngineProperties(
    boolean enabled,
    String habitatId,
    long pollingIntervalMs,
    int maxDueActsPerCycle,
    int maxRecoveryBatchSize,
    int terminalRetentionDays,
    int maxTerminalResults,
    int maxMisfiredResults,
    boolean singleNodeGuardEnabled,
    long singleNodeGuardTtlMs,
    long shutdownTimeoutMs,
    long failureBackoffMs,
    int maxConsecutiveFailures
) {}
```

Add to `src/main/resources/application.yml`:

```yaml
sovereign:
  temporal:
    engine:
      enabled: true
      habitat-id: "habitat-001"
      polling-interval-ms: 1000
      max-due-acts-per-cycle: 50
      max-recovery-batch-size: 200
      terminal-retention-days: 30
      max-terminal-results: 500
      max-misfired-results: 500
      single-node-guard-enabled: true
      single-node-guard-ttl-ms: 30000
      shutdown-timeout-ms: 5000
      failure-backoff-ms: 5000
      max-consecutive-failures: 5
```

---

## 4. Storage: Flyway migrations and SQLite adapter

### 4.1 Required pom.xml dependencies

Add to `pom.xml` (do not remove the H2 test dependency):

```xml
<!-- SQLite JDBC -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.45.3.0</version>   <!-- or latest stable -->
</dependency>

<!-- Flyway core + SQLite support (for Flyway >= 10.x) -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-community-db-support</artifactId>
    <!-- Provides SQLite support; check exact artifact for selected Flyway version -->
</dependency>
```

If using Flyway 9.x, the SQLite community module artifact name differs — check the official
Flyway docs for the version resolved by Spring Boot's BOM.

### 4.2 Migration file location and naming

```text
src/main/resources/db/migration/
  V1__sc_c_base_schema.sql          (if base topology tables not already here)
  V2__sc_c_temporal_engine.sql      (new — the industrial temporal schema)
```

Flyway discovers migrations under `classpath:db/migration` by default. Add to `application.yml`:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
```

### 4.3 V2__sc_c_temporal_engine.sql — exact DDL to create

```sql
-- temporal_acts: industrial v1 Signal-only
CREATE TABLE IF NOT EXISTS temporal_acts (
  habitat_id              TEXT NOT NULL,
  temporal_act_id         BLOB NOT NULL,
  status                  TEXT NOT NULL
    CHECK(status IN ('PENDING','ARMED','FIRED','CANCELLED','MISFIRED','FAILED','EXPIRED')),
  payload_kind            TEXT NOT NULL CHECK(payload_kind IN ('SIGNAL')),
  due_at_ms               INTEGER NOT NULL,
  label                   TEXT NOT NULL,
  signal_kind             TEXT NOT NULL,
  notification_target_ref TEXT NOT NULL,
  created_by_ref          TEXT NOT NULL,
  requested_at_ms         INTEGER,
  created_at_ms           INTEGER NOT NULL,
  updated_at_ms           INTEGER NOT NULL,
  fired_at_ms             INTEGER,
  terminal_at_ms          INTEGER,
  terminal_reason         TEXT,
  signal_payload_json     TEXT,
  metadata_json           TEXT,
  PRIMARY KEY (habitat_id, temporal_act_id),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- indexes for performance
CREATE INDEX IF NOT EXISTS ix_temporal_acts_active_due
  ON temporal_acts(habitat_id, status, due_at_ms)
  WHERE status IN ('PENDING','ARMED');

CREATE INDEX IF NOT EXISTS ix_temporal_acts_terminal
  ON temporal_acts(habitat_id, status, terminal_at_ms)
  WHERE status IN ('FIRED','CANCELLED','MISFIRED','FAILED','EXPIRED');

CREATE INDEX IF NOT EXISTS ix_temporal_acts_misfired
  ON temporal_acts(habitat_id, terminal_at_ms)
  WHERE status = 'MISFIRED';

CREATE INDEX IF NOT EXISTS ix_temporal_acts_notification_target
  ON temporal_acts(habitat_id, notification_target_ref);

-- request idempotency table
CREATE TABLE IF NOT EXISTS temporal_request_idempotency (
  habitat_id          TEXT NOT NULL,
  idempotency_key     TEXT NOT NULL,
  request_kind        TEXT NOT NULL CHECK(request_kind IN ('CREATE_SIGNAL','CANCEL')),
  semantic_fingerprint BLOB NOT NULL,
  temporal_act_id     BLOB,
  result_kind         TEXT NOT NULL
    CHECK(result_kind IN ('ACCEPTED','IDEMPOTENT_REPLAY','REJECTED','FAILED')),
  result_code         TEXT,
  result_json         TEXT,
  created_at_ms       INTEGER NOT NULL,
  updated_at_ms       INTEGER NOT NULL,
  PRIMARY KEY (habitat_id, idempotency_key, request_kind),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id),
  FOREIGN KEY (habitat_id, temporal_act_id)
    REFERENCES temporal_acts(habitat_id, temporal_act_id)
);

CREATE INDEX IF NOT EXISTS ix_temporal_request_idempotency_act
  ON temporal_request_idempotency(habitat_id, temporal_act_id, request_kind);

-- single-node guard (recommended: SQLite lock/lease row)
CREATE TABLE IF NOT EXISTS temporal_engine_locks (
  habitat_id            TEXT NOT NULL,
  storage_partition_ref TEXT NOT NULL,
  engine_instance_id    TEXT NOT NULL,
  acquired_at_ms        INTEGER NOT NULL,
  heartbeat_at_ms       INTEGER NOT NULL,
  expires_at_ms         INTEGER,
  status                TEXT NOT NULL
    CHECK(status IN ('ACTIVE','RELEASED','EXPIRED','FAILED')),
  metadata_json         TEXT,
  PRIMARY KEY (habitat_id, storage_partition_ref),
  FOREIGN KEY (habitat_id) REFERENCES habitats(habitat_id)
);

-- recovery observability
CREATE TABLE IF NOT EXISTS recovery_runs (
  recovery_run_id BLOB PRIMARY KEY,
  mode            TEXT NOT NULL,
  status          TEXT NOT NULL,
  started_at_ms   INTEGER NOT NULL,
  completed_at_ms INTEGER,
  schema_version  TEXT,
  storage_profile TEXT,
  summary_json    TEXT
);

CREATE TABLE IF NOT EXISTS recovery_findings (
  finding_id       BLOB PRIMARY KEY,
  recovery_run_id  BLOB NOT NULL,
  severity         TEXT NOT NULL CHECK(severity IN ('INFO','WARN','ERROR','FATAL')),
  category         TEXT NOT NULL,
  code             TEXT NOT NULL,
  entity_type      TEXT,
  entity_id        BLOB,
  message          TEXT NOT NULL,
  resolved         INTEGER NOT NULL CHECK(resolved IN (0,1)),
  metadata_json    TEXT,
  created_at_ms    INTEGER NOT NULL,
  FOREIGN KEY (recovery_run_id) REFERENCES recovery_runs(recovery_run_id)
);
```

**Note on habitats FK:** If the `habitats` table does not yet exist in the migration sequence,
add it in `V1__sc_c_base_schema.sql` before `V2`, or replace the FK with a soft reference and
a comment explaining the ordering constraint. Do not fail migration because of FK ordering.

### 4.4 SQLite connection setup (required PRAGMA per connection)

Every SQLite connection opened by the industrial adapter MUST execute:

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA synchronous = FULL;
PRAGMA busy_timeout = 5000;
```

`PRAGMA synchronous = FULL` is the industrial default from SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001. `NORMAL` is allowed only by an explicit deployment profile with recovery/backup evidence; do not silently downgrade durability in this MU.

The `foreign_keys` pragma is **connection-local** in SQLite — setting it once at startup is not
sufficient. It must be set every time a connection is opened. Use a `DataSource` wrapper or a
`ConnectionInitializer` / `DataSourcePostProcessor` bean to inject this into every connection:

```java
@Bean
public DataSource sqliteDataSource(...) {
    var ds = new SQLiteDataSource();
    ds.setUrl(properties.getUrl());
    // wrap to enforce PRAGMAs per-connection
    return new PerConnectionPragmaDataSource(ds);
}

// PerConnectionPragmaDataSource: on getConnection(), execute the four PRAGMAs before returning
```

This is a load-bearing requirement. Tests MUST verify FK enforcement is active.

---

## 5. Idempotency implementation — semantic fingerprint construction

The semantic fingerprint for `CreateSignalTemporalActRequest` must include:

```java
String fingerprint = habitatId + "|" + idempotencyKey + "|CREATE_SIGNAL|"
    + dueAt.toEpochMilli() + "|" + label + "|" + signalKind
    + "|" + notificationTargetRef + "|" + createdByRef;
byte[] hash = MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8));
```

Store the hash as `BLOB` (`semantic_fingerprint`). On replay:
- Same hash → `IdempotentReplay` (return existing `TemporalActObservation`)
- Different hash → `Rejected(IDEMPOTENCY_CONFLICT)`

`requestedAt` must NOT be included — two replays at different timestamps are the same request.

For `CancelTemporalActRequest`:

```java
String fingerprint = habitatId + "|" + idempotencyKey + "|CANCEL|"
    + temporalActId + "|" + requestedByRef;
```

---

## 6. Recovery gate integration — exact wiring rule

The `TemporalActApplicationPort` implementation must check engine readiness before executing:

```java
public CreateSignalTemporalActResult createSignalTemporalAct(CreateSignalTemporalActRequest req) {
    // validation first (cheap, no storage)
    var violation = validate(req);
    if (violation != null) return new Rejected(violation);

    // engine readiness gate
    if (!health.isReady()) {
        return new Failed(new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.RECOVERY_NOT_COMPLETED, "engine not yet ready"));
    }
    // ... proceed with idempotency check + insert
}
```

`health.isReady()` returns true only when `TemporalEngineStatus` is `RUNNING` or `DEGRADED`.
Before `TemporalEngineLifecycle.start()` completes, status is `STOPPED` or `RECOVERING` —
requests must fail with `RECOVERY_NOT_COMPLETED`.

---

## 7. Validation rules — exact rejection map

For `CreateSignalTemporalActRequest`:

| Field | Rejection code |
|---|---|
| `habitatId` null/blank | `INVALID_HABITAT_ID` |
| `dueAt` null | `INVALID_DUE_AT` |
| `dueAt <= requestedAt` | `PAST_DUE_AT_NOT_SUPPORTED` |
| `label` null/blank | `MISSING_LABEL` |
| `signalKind` null/blank | `MISSING_SIGNAL_KIND` |
| `notificationTargetRef` null/blank | `MISSING_NOTIFICATION_TARGET_REF` |
| `createdByRef` null/blank | `MISSING_CREATED_BY_REF` |
| `idempotencyKey` null/blank | `MISSING_IDEMPOTENCY_KEY` |
| `requestedAt` null | `INVALID_REQUESTED_AT` |

For `CancelTemporalActRequest`:

| Field | Rejection code |
|---|---|
| `habitatId` null/blank | `INVALID_HABITAT_ID` |
| `temporalActId` null/blank | `INVALID_TEMPORAL_ACT_ID` |
| `requestedByRef` null/blank | `MISSING_REQUESTED_BY_REF` |
| `idempotencyKey` null/blank | `MISSING_IDEMPOTENCY_KEY` |
| `requestedAt` null | `INVALID_REQUESTED_AT` |

---

## 8. Preserved invariants — do not change these

### 8.1 Fire idempotency (from MU-005 — never regress)

```java
// In fireDueTemporalActOnce: conditional update is the guard
int updated = writePort.markFiredIfDueAndNonTerminal(habitatId, temporalActId, now);
if (updated == 0) return;   // already fired or cancelled — append nothing
// Only append ledger+outbox when updated == 1
```

This guard prevents double-fire and double ledger-append. The industrial bounded polling
adds a query LIMIT before this method is called, but must not alter the conditional update
logic itself.

### 8.2 Atomic fire transaction (from MU-005 — never regress)

```java
txTemplate.executeWithoutResult(status -> {
    int updated = writePort.markFiredIfDueAndNonTerminal(...);
    if (updated == 0) return;
    TemporalAct act = readPort.findById(...).orElseThrow(...);
    ledgerPort.appendLedgerEntry(firedLedgerEntry(act, now));
    outboxPort.appendOutboxEntry(timerFiredOutboxEntry(act, firedLedgerEntry.ledgerEntryId(), now));
});
```

All three writes — status update, ledger, outbox — are in one transaction. They must remain
co-transactional in the industrial path.

### 8.3 PENDING-only outbox append guard (from MU-015 — never regress)

The existing test `nonPendingOutboxAppendRejected` must continue to pass. The outbox port
`appendOutboxEntry(OutboxEntry)` rejects entries where `status != PENDING`. Do not change this
contract when migrating the temporal engine to SQLite.

### 8.4 Cancel/fire race (from MU-005 — never regress)

Both cancel and fire use conditional non-terminal updates. The test
`cancelThenFireDoesNotCreateTimerFired` must continue to pass unchanged.

---

## 9. Boundary test assertions — exact Java code

Add these tests to a new class (e.g. `TemporalEngineIndustrialBoundaryTest`). They verify
structural invariants that cannot be checked at runtime.

### 9.1 No H2 imports in SQLite adapter (T-005)

```java
@Test
void h2ExcludedFromSqliteAdapterPath() throws IOException {
    Path sqlitePath = Path.of("src/main/java/com/sovereign/connect/adapter/persistence/sqlite");
    if (!Files.exists(sqlitePath)) return; // adapter not yet created — skip until Phase 2

    try (Stream<Path> paths = Files.walk(sqlitePath)) {
        for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
            String source = Files.readString(file);
            assertThat(source)
                .as("SQLite adapter file %s must not import H2", file)
                .doesNotContain("org.h2");
        }
    }
}
```

### 9.2 Domain/application services remain storage-free (extends existing T-031)

```java
@Test
void domainAndApplicationServicesHaveNoStorageImports() throws IOException {
    List<Path> roots = List.of(
        Path.of("src/main/java/com/sovereign/connect/core/temporal/service"),
        Path.of("src/main/java/com/sovereign/connect/core/temporal/application"),
        Path.of("src/main/java/com/sovereign/connect/core/temporal/observation")
    );
    List<String> forbidden = List.of(
        "import java.sql", "import javax.sql",
        "JdbcTemplate", "DataSource",
        "org.h2", "org.xerial", "org.flywaydb"
    );
    for (Path root : roots) {
        if (!Files.exists(root)) continue;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String banned : forbidden) {
                    assertThat(source)
                        .as("Domain service %s must not import %s", file, banned)
                        .doesNotContain(banned);
                }
            }
        }
    }
}
```

### 9.3 Signal-only sealed interface (T-029)

```java
@Test
void temporalActPayloadPermitsOnlySignalTemporalPayload() {
    Class<?>[] permitted = TemporalActPayload.class.getPermittedSubclasses();
    assertThat(permitted)
        .extracting(Class::getSimpleName)
        .containsExactly("SignalTemporalPayload");
}
```

### 9.4 No ActionTemporalPayload class exists (T-029)

```java
@Test
void actionTemporalPayloadDoesNotExist() throws IOException {
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
        List<String> actionClasses = paths
            .filter(p -> p.toString().endsWith(".java"))
            .map(p -> p.getFileName().toString())
            .filter(name -> name.contains("ActionTemporalPayload") || name.contains("ActionRequest"))
            .toList();
        assertThat(actionClasses)
            .as("ActionTemporalPayload and ActionRequest must not exist in v1")
            .isEmpty();
    }
}
```

### 9.5 Runner uses no @Scheduled (T-022 — existing test, keep as-is)

```java
// Already in TemporalActSeedTest — preserve:
@Test
void virtualThreadRunnerDoesNotUseScheduledAnnotation() {
    assertThat(Arrays.stream(TemporalEngineRunner.class.getDeclaredMethods())
        .map(Method::getDeclaredAnnotations)
        .flatMap(Arrays::stream)
        .map(a -> a.annotationType().getName())
        .toList())
        .doesNotContain(Scheduled.class.getName());
}
```

### 9.6 Application port is write-only (T-028)

```java
@Test
void temporalActApplicationPortHasNoReadMethods() {
    List<String> methods = Arrays.stream(TemporalActApplicationPort.class.getMethods())
        .map(Method::getName)
        .toList();
    assertThat(methods).doesNotContain("findById", "listActive", "listTerminal", "listMisfired");
    assertThat(methods).contains("createSignalTemporalAct", "cancelTemporalAct");
}
```

### 9.7 Observation DTO has no aggregate or remainingMs (T-009)

```java
@Test
void temporalActObservationDoesNotExposeInternals() {
    List<String> fields = Arrays.stream(TemporalActObservation.class.getRecordComponents())
        .map(java.lang.reflect.RecordComponent::getName)
        .toList();
    assertThat(fields).doesNotContain("payload", "remainingMs", "createdByRefObject");
    assertThat(fields).contains("payloadKind", "label", "signalKind",
        "notificationTargetRef", "createdByRef");
}
```

---

## 10. Stop conditions — when to pause and report

Stop the implementation and report upstream if:

1. Adding SQLite/Flyway requires modifying `H2BaseTopologyRepository` or any topology service.
2. `SmartLifecycle.start()` cannot invoke `classifyMisfires` before `runner.start()` without
   a framework cycle or circular dependency.
3. The `habitats` FK in the temporal schema cannot be satisfied because the table does not exist
   yet — drop the FK constraint and document the decision in the implementation report.
4. Existing 101 tests cannot be preserved (do not delete tests; adapt as needed with rationale).
5. Implementing idempotency requires modifying `ScLedgerWritePort` or `ScOutboxWritePort`.
6. Unknown payload handling cannot quarantine without a new port method on `TemporalActWritePort`
   for `markFailed(...)` — add the port method; do not throw.
