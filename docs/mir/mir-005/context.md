# Context — MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001

```text
Document:         context.md
Version:          v0.1.1-patched
Patch status:     MISFIRED per-act ledger fix + Clock/ObjectMapper precision
MU:               MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Operational Slot: MU-005
Profile:          A — Signal-first only
Branch:           feat/sc-c-temporal-acts-seed
Baseline tests:   75 / 0 failures / 0 errors / 0 skipped
Expected total:   ≥ 95 tests
```

---

## 0. Thesis

SC-C owns durable temporal commitments.

```text
"avísame en 45 minutos"
"pon una alarma dentro de cincuenta minutos"
```

These are Profile A — Signal-first TemporalActs. No device target. No SC-B.
No ActionRequest. No NATS. SC-C persists, fires, and carries notificationTargetRef
opaquely. Hub/Surface renders the notification.

---

## 1. What already exists — use directly

### 1.1 Ledger/outbox ports (MU-015)

```java
// Ports — use as-is, do not modify
ScLedgerWritePort.appendLedgerEntry(LedgerEntry entry)
ScOutboxWritePort.appendOutboxEntry(OutboxEntry entry)  // PENDING only

// Models already present in core.scledger.model:
SemanticKind.TEMPORAL_ACT_CREATED
SemanticKind.TEMPORAL_ACT_CANCELLED
SemanticKind.TEMPORAL_ACT_FIRED
SemanticKind.TEMPORAL_ACT_MISFIRED
SemanticKind.TEMPORAL_ACT_FAILED
SemanticKind.TIMER_FIRED

OutboundKind.TIMER_FIRED_SIGNAL
DeliveryLane.SIGNAL
OutboxEntryStatus.PENDING
LedgerRecordClass.EVENT_OUTBOX
LedgerRecordClass.LEDGER_ONLY
```

### 1.2 H2BaseTopologyRepository — constructor and helpers to replicate

`H2TemporalActRepository` must follow this exact pattern:

```java
public class H2TemporalActRepository
    implements TemporalActWritePort, TemporalActReadPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Convenience constructor — matches H2BaseTopologyRepository pattern
    public H2TemporalActRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
    }

    // Full constructor — used in tests with injected clock
    public H2TemporalActRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        createSchema();    // called at construction time
    }
}
```

### 1.3 writeJson helper — replicate from H2BaseTopologyRepository

```java
private String writeJson(Object value) {
    try {
        return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
        throw new IllegalStateException(
            "failed to serialize " + value.getClass().getSimpleName(), ex);
    }
}

private <T> T readJson(String json, Class<T> type) {
    try {
        return objectMapper.readValue(json, type);
    } catch (Exception ex) {
        throw new IllegalStateException("failed to deserialize " + type.getSimpleName(), ex);
    }
}
```

### 1.4 Test infrastructure — exact pattern from OutboxLedgerStorageSeedTest

```java
@TempDir Path tempDir;
private final Clock clock = Clock.fixed(Instant.parse("2026-05-18T12:00:00Z"), ZoneOffset.UTC);

private ObjectMapper mapper() {
    return new ObjectMapper().findAndRegisterModules();
}

private String jdbcUrl(String name) {
    String dbPath = tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
}

private DataSource dataSource(String jdbcUrl) {
    return new DriverManagerDataSource(jdbcUrl, "sa", "");
}
```

Use `jdbc:h2:file:...` for tests that verify durability after recreation.
Recreate `H2TemporalActRepository` against the same URL to simulate restart.

---

## 2. What to create

### 2.1 Package structure

```text
core/temporal/model/
  TemporalAct.java
  TemporalActStatus.java
  TemporalActPayload.java       sealed interface
  SignalTemporalPayload.java    implements TemporalActPayload
  CreatedByRef.java

core/temporal/port/
  TemporalActWritePort.java
  TemporalActReadPort.java

core/temporal/service/
  TemporalActService.java
  TemporalEngineService.java
  TemporalEngineRunner.java

adapter/persistence/
  H2TemporalActRepository.java
```

---

## 3. TemporalAct record — exact field set

```java
public record TemporalAct(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    TemporalActPayload payload,
    String notificationTargetRef,             // nullable
    CreatedByRef createdByRef,
    String topologyVersionAtRegistration,     // nullable String, not TopologyVersion
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,                          // nullable
    Instant terminalAt,                       // nullable
    String terminalReason                     // nullable
)
```

**Critical notes:**

- `temporalActId` is assigned by `TemporalActService`, never accepted from caller.
- `topologyVersionAtRegistration` is a nullable `String` (version value string),
  not a `TopologyVersion` object — simpler to serialize into the table column.
- `notificationTargetRef` is opaque — store and carry, never parse or resolve.
- `CreatedByRef` is opaque to SC-C. The seed validates only non-null / non-blank. Callers MUST NOT pass personal, session or conversation identifiers. SC-C MUST NOT parse `CreatedByRef`.

---

## 4. TemporalActPayload serialization — exact strategy

`TemporalActPayload` is a sealed interface. Store in two columns:

```text
payload_type  VARCHAR(255) — discriminator string
payload_json  CLOB         — serialized payload body
```

**Write:**
```java
String payloadType = payload.getClass().getSimpleName();  // "SignalTemporalPayload"
String payloadJson = writeJson(payload);
```

**Read — exact switch:**
```java
TemporalActPayload payload = switch (payloadType) {
    case "SignalTemporalPayload" ->
        readJson(payloadJson, SignalTemporalPayload.class);
    default ->
        throw new IllegalArgumentException(
            "Unknown temporal payload type in MU-005: " + payloadType);
};
```

Do NOT use Jackson `@JsonTypeInfo` or `@JsonSubTypes` for this. The explicit switch
is simpler, testable, and avoids annotation-driven polymorphism complexity.

`ActionTemporalPayload` must NOT appear in this switch. If it appears in stored
data, throw — it is a schema consistency violation for Profile A.

---

## 5. H2TemporalActRepository — createSchema()

Add in `createSchema()`:

```java
jdbcTemplate.execute("""
    CREATE TABLE IF NOT EXISTS temporal_acts (
        temporal_act_id         VARCHAR(36)   PRIMARY KEY,
        habitat_id              VARCHAR(255)  NOT NULL,
        status                  VARCHAR(64)   NOT NULL
                                CHECK(status IN ('PENDING','ARMED','FIRED',
                                                 'CANCELLED','EXPIRED','MISFIRED','FAILED')),
        due_at_ms               BIGINT        NOT NULL,
        payload_type            VARCHAR(255)  NOT NULL,
        payload_json            CLOB          NOT NULL,
        notification_target_ref VARCHAR(512),
        created_by_ref_json     CLOB          NOT NULL,
        topology_version_at_registration VARCHAR(255),
        created_at_ms           BIGINT        NOT NULL,
        updated_at_ms           BIGINT        NOT NULL,
        fired_at_ms             BIGINT,
        terminal_at_ms          BIGINT,
        terminal_reason         VARCHAR(2048)
    )
    """);
jdbcTemplate.execute("""
    CREATE INDEX IF NOT EXISTS ix_temporal_acts_due_status
    ON temporal_acts(habitat_id, status, due_at_ms)
    """);
jdbcTemplate.execute("""
    CREATE INDEX IF NOT EXISTS ix_temporal_acts_status
    ON temporal_acts(habitat_id, status)
    """);
```

---

## 6. TemporalActWritePort — method naming

```java
public interface TemporalActWritePort {
    void insertCreated(TemporalAct act);
    int cancelIfNonTerminal(String habitatId, String temporalActId, Instant now);
    int markFiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant now);
    int markMisfiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant cutoff, Instant now);
}
```

Return `int` for transition methods — the row count is the idempotency signal:
- `0` → already terminal or not eligible
- `1` → transition applied

Do NOT expose `transitionTo(status)`, `markArmed()`, or `armTemporalAct()`.

---

## 7. Fire idempotency — exact SQL

This is the fire guard. No ledger/outbox entry if row count is 0:

```java
int updated = jdbcTemplate.update("""
    UPDATE temporal_acts
    SET status = 'FIRED',
        fired_at_ms = ?,
        terminal_at_ms = ?,
        updated_at_ms = ?
    WHERE temporal_act_id = ?
      AND habitat_id = ?
      AND status IN ('PENDING','ARMED')
      AND due_at_ms <= ?
    """,
    now.toEpochMilli(), now.toEpochMilli(), now.toEpochMilli(),
    temporalActId, habitatId, now.toEpochMilli()
);

if (updated == 0) {
    return;  // already terminal, not due, or not eligible — no semantic fire
}
// Only reach here if updated == 1:
// append TemporalActFired ledger entry
// append TimerFired outbox entry (PENDING, TIMER_FIRED_SIGNAL, SIGNAL)
```

Similarly for cancel:

```java
int updated = jdbcTemplate.update("""
    UPDATE temporal_acts
    SET status = 'CANCELLED', terminal_at_ms = ?, updated_at_ms = ?
    WHERE temporal_act_id = ? AND habitat_id = ?
      AND status IN ('PENDING','ARMED')
    """,
    now.toEpochMilli(), now.toEpochMilli(), temporalActId, habitatId
);
// updated == 0 → already terminal, skip ledger
// updated == 1 → append TemporalActCancelled ledger entry
```


Similarly for MISFIRED classification:

```java
int updated = jdbcTemplate.update("""
    UPDATE temporal_acts
    SET status = 'MISFIRED', terminal_at_ms = ?, updated_at_ms = ?
    WHERE temporal_act_id = ? AND habitat_id = ?
      AND status IN ('PENDING','ARMED')
      AND due_at_ms <= ?
    """,
    now.toEpochMilli(), now.toEpochMilli(), temporalActId, habitatId, cutoff.toEpochMilli()
);
// updated == 0 → already terminal, not due, or not eligible; skip ledger
// updated == 1 → append TemporalActMisfired ledger entry for that same act
```

---

## 8. TransactionTemplate wiring

`TemporalEngineService` or `TemporalActService` must own the transaction boundary.

**Do NOT use `@Transactional`.** The adapters are not Spring beans — they are
instantiated directly. `@Transactional` requires a Spring proxy and will not work.

Required wiring. Services that build `LedgerEntry` / `OutboxEntry` semantic payload JSON MUST receive an `ObjectMapper` or equivalent JSON helper; do not leave `writeJson(...)` implicit.

Required wiring:

```java
// In TemporalEngineService or TemporalActService:
private final TransactionTemplate txTemplate;

public TemporalActService(
    TemporalActWritePort writePort,
    TemporalActReadPort readPort,
    ScLedgerWritePort ledgerPort,
    ScOutboxWritePort outboxPort,
    DataSource dataSource,
    ObjectMapper objectMapper,
    Clock clock
) {
    this.txTemplate = new TransactionTemplate(
        new DataSourceTransactionManager(dataSource)
    );
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
    // ...
}
```

Fire method:

```java
public void fireDueTemporalActOnce(String habitatId, String temporalActId, Instant now) {
    txTemplate.executeWithoutResult(status -> {
        int updated = writePort.markFiredIfDueAndNonTerminal(habitatId, temporalActId, now);
        if (updated == 0) return;
        ledgerPort.appendLedgerEntry(buildFiredLedgerEntry(habitatId, temporalActId, now));
        outboxPort.appendOutboxEntry(buildTimerFiredOutboxEntry(habitatId, temporalActId, now));
    });
}
```

Create method:

```java
public String createSignalTemporalAct(
    String habitatId, SignalTemporalPayload payload,
    Instant dueAt, String notificationTargetRef, CreatedByRef createdByRef
) {
    String temporalActId = UUID.randomUUID().toString();
    TemporalAct act = new TemporalAct(temporalActId, habitatId, TemporalActStatus.PENDING,
        dueAt, payload, notificationTargetRef, createdByRef, null,
        Instant.now(clock), Instant.now(clock), null, null, null);
    txTemplate.executeWithoutResult(status -> {
        writePort.insertCreated(act);
        ledgerPort.appendLedgerEntry(buildCreatedLedgerEntry(act));
    });
    return temporalActId;
}
```

---

## 9. Ledger idempotency key formats

Use these exact formats for consistency with MU-015 patterns:

```text
Create:   temporal-act-created:{temporalActId}
Cancel:   temporal-act-cancelled:{temporalActId}
Fire:     temporal-act-fired:{temporalActId}
Misfire:  temporal-act-misfired:{temporalActId}
```

The `UNIQUE(habitat_id, idempotency_key)` constraint on `sc_c_ledger_entries`
acts as a secondary guard against duplicate ledger entries. The primary guard
remains the conditional UPDATE row count.

---

## 10. TimerFired outbox entry shape

```java
new OutboxEntry(
    UUID.randomUUID(),
    ledgerEntryId,              // FK to the TemporalActFired ledger entry
    habitatId,
    OutboundKind.TIMER_FIRED_SIGNAL,
    DeliveryLane.SIGNAL,
    "sc-c.timer-fired",         // logical topic
    writeJson(Map.of(           // semantic payload
        "temporalActId", temporalActId,
        "habitatId", habitatId,
        "firedAt", now.toString()
    )),
    act.notificationTargetRef(),    // opaque — copy as-is, may be null
    "timer-fired-signal:" + temporalActId,  // idempotency key
    OutboxEntryStatus.PENDING,
    now,
    now,
    null
)
```

`notificationTargetRef` is nullable — pass it through opaquely. Do not parse,
resolve, or validate it. SC-C carries it; Hub resolves it.

---

## 11. Virtual-thread runner

```java
public class TemporalEngineRunner {

    private final TemporalEngineService engine;
    private final long pollingIntervalMs;
    private final String habitatId;
    private final Clock clock;
    private volatile boolean running;
    private Thread runnerThread;

    public void start() {
        running = true;
        runnerThread = Thread.ofVirtual().name("temporal-engine-runner").start(() -> {
            while (running) {
                try {
                    engine.pollDueOnce(habitatId, Instant.now(clock));
                } catch (Exception ex) {
                    // log and continue — do not crash the runner
                }
                try {
                    Thread.sleep(pollingIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void stop() {
        running = false;
        if (runnerThread != null) {
            runnerThread.interrupt();
        }
    }
}
```

`pollingIntervalMs` must be configurable. Default for Profile A seed: `≤ 1000ms`.
The runner MUST NOT dispatch outbox entries.

---

## 12. MISFIRED classification on startup

Call before the runner starts:

```java
public void classifyMisfires(String habitatId, Instant recoveryNow) {
    // Find all overdue non-terminal acts. Seed may process all in one pass.
    List<TemporalAct> overdue = readPort.findNonTerminalDueBefore(habitatId, recoveryNow);
    for (TemporalAct act : overdue) {
        txTemplate.executeWithoutResult(status -> {
            int updated = writePort.markMisfiredIfDueAndNonTerminal(
                habitatId,
                act.temporalActId(),
                recoveryNow,
                recoveryNow
            );
            if (updated == 0) {
                return;
            }
            ledgerPort.appendLedgerEntry(buildMisfiredLedgerEntry(act, recoveryNow));
        });
    }
}
```

MISFIRED acts:
- Are terminal — excluded from `listActive()` and `findDue()`.
- Remain queryable via `listMisfired()` and `listTerminal()`.
- Must not be re-fired by the runner.

MISFIRED classification MUST NOT block the startup gate indefinitely. The seed MAY process all overdue acts in a single pass; a configurable batch limit is optional. The implementation MUST NOT use one batch `UPDATE` unless it also records one `TemporalActMisfired` ledger entry per TemporalAct classified as MISFIRED.

---

## 13. Invariants from prior MUs — must remain intact

```text
MU-015: appendOutboxEntry(PENDING only) — do not remove or weaken
MU-015: appendLedgerEntry (INSERT only, no MERGE) — do not modify
MU-014: MaterializationDecisionReplayPort wiring — do not touch
MU-012: DefaultTopologyMaterializationService has no H2 import — do not break
MU-011: H2BaseTopologyRepository.save() structural-only — do not add health/state side effects
MU-013: validateTopology coherence checks — no bypass
```

All 75 existing tests must pass without modification.

---

## 14. Files to create

```text
NEW — production (10 files):
  core/temporal/model/TemporalAct.java
  core/temporal/model/TemporalActStatus.java
  core/temporal/model/TemporalActPayload.java
  core/temporal/model/SignalTemporalPayload.java
  core/temporal/model/CreatedByRef.java
  core/temporal/port/TemporalActWritePort.java
  core/temporal/port/TemporalActReadPort.java
  core/temporal/service/TemporalActService.java
  core/temporal/service/TemporalEngineService.java
  core/temporal/service/TemporalEngineRunner.java

NEW — adapter (1 file):
  adapter/persistence/H2TemporalActRepository.java

NEW — test (1 file):
  core/temporal/TemporalActSeedTest.java
    (or adapter/persistence/TemporalActSeedTest.java if testing H2 directly)
```
