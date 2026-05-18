# Codex Prompt — MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001

```text
Document:         codex-prompt.md
Version:          v0.1.1-patched
Patch status:     MISFIRED per-act ledger fix + Clock/ObjectMapper precision
MU:               MU-SOV-SC-C-TEMPORAL-ACTS-SEED-001
Slot:             MU-005
Branch:           feat/sc-c-temporal-acts-seed
Commit:           feat(sc-c): add signal-first temporal acts seed
Profile:          A — Signal-first only
Baseline:         75 tests / 0 failures / 0 errors / 0 skipped
Expected total:   ≥ 95 tests / 0 failures
```

---

## 0. Mandatory reading

```text
docs/mir/mir-005/MIR-SOV-SC-C-TEMPORAL-ACTS-SEED-001.md
docs/mir/mir-005/code-surface-audit.md
docs/mir/mir-005/decision-confirmation.md
docs/mir/mir-005/context.md
docs/mir/mir-005/acceptance-map.md
```

---

## 1. Mission

Implement SC-C's first durable timer engine. Profile A only:
targetless signal timers — alarms, reminders, countdowns — with no device,
no SC-B, no NATS, no ActionRequest.

---

## 2. Phase 1 — Create domain model

Create in `core/temporal/model/`:

### TemporalActStatus.java

```java
public enum TemporalActStatus {
    PENDING, ARMED, FIRED, CANCELLED, EXPIRED, MISFIRED, FAILED
}
```

Terminal: `FIRED`, `CANCELLED`, `EXPIRED`, `MISFIRED`, `FAILED`.
Non-terminal: `PENDING`, `ARMED`.
Seed creates only `PENDING`. No `PENDING → ARMED` transition. `ARMED` is equivalent
to `PENDING` in all guards.

### TemporalActPayload.java

```java
public sealed interface TemporalActPayload
    permits SignalTemporalPayload { }
```

Do NOT add `ActionTemporalPayload` to the permits clause in MU-005.

### SignalTemporalPayload.java

```java
public record SignalTemporalPayload(
    String label,       // human hint, not authoritative UX text
    String signalKind   // "alarm" | "timer" | "reminder" — opaque category
) implements TemporalActPayload {
    public SignalTemporalPayload {
        Objects.requireNonNull(label, "label is required");
        Objects.requireNonNull(signalKind, "signalKind is required");
    }
}
```

Must NOT contain deviceId, endpointId, capabilityId, or TopologyTargetRef.

### CreatedByRef.java

`CreatedByRef` is opaque to SC-C. Validate only non-null / non-blank. Do not parse it and do not attempt to infer user, session, conversation, authority or policy semantics from it.

```java
public record CreatedByRef(String value) {
    public CreatedByRef {
        Objects.requireNonNull(value, "value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
```

### TemporalAct.java

```java
public record TemporalAct(
    String temporalActId,
    String habitatId,
    TemporalActStatus status,
    Instant dueAt,
    TemporalActPayload payload,
    String notificationTargetRef,           // nullable — opaque
    CreatedByRef createdByRef,
    String topologyVersionAtRegistration,   // nullable String, not TopologyVersion
    Instant createdAt,
    Instant updatedAt,
    Instant firedAt,                        // nullable
    Instant terminalAt,                     // nullable
    String terminalReason                   // nullable
) {
    public TemporalAct {
        Objects.requireNonNull(temporalActId, ...);
        Objects.requireNonNull(habitatId, ...);
        Objects.requireNonNull(status, ...);
        Objects.requireNonNull(dueAt, ...);
        Objects.requireNonNull(payload, ...);
        Objects.requireNonNull(createdByRef, ...);
        Objects.requireNonNull(createdAt, ...);
        Objects.requireNonNull(updatedAt, ...);
        // nullable fields: notificationTargetRef, topologyVersionAtRegistration,
        //                  firedAt, terminalAt, terminalReason
    }
}
```

Compile check: `mvn -q -DskipTests compile`

---

## 3. Phase 2 — Create ports

Create in `core/temporal/port/`:

### TemporalActWritePort.java

```java
public interface TemporalActWritePort {
    void insertCreated(TemporalAct act);
    int cancelIfNonTerminal(String habitatId, String temporalActId, Instant now);
    int markFiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant now);
    int markMisfiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant cutoff, Instant now);
}
```

Do NOT expose:
```java
transitionTo(TemporalActStatus)
markArmed(...)
armTemporalAct(...)
```

### TemporalActReadPort.java

```java
public interface TemporalActReadPort {
    Optional<TemporalAct> findById(String habitatId, String temporalActId);
    List<TemporalAct> listActive(String habitatId);
    List<TemporalAct> findDue(String habitatId, Instant now);
    List<TemporalAct> findNonTerminalDueBefore(String habitatId, Instant cutoff);
    List<TemporalAct> listTerminal(String habitatId);
    List<TemporalAct> listMisfired(String habitatId);
}
```

Compile check: `mvn -q -DskipTests compile`

---

## 4. Phase 3 — Create H2TemporalActRepository

Create in `adapter/persistence/`:

### Constructor pattern (copy from H2BaseTopologyRepository exactly)

```java
public class H2TemporalActRepository
    implements TemporalActWritePort, TemporalActReadPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public H2TemporalActRepository(DataSource dataSource) {
        this(dataSource, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
    }

    public H2TemporalActRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        createSchema();
    }
}
```

### createSchema() — add temporal_acts table

```java
private void createSchema() {
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
}
```

### Payload serialization — exact approach

Write:
```java
String payloadType = act.payload().getClass().getSimpleName();
String payloadJson = writeJson(act.payload());
```

Read (in RowMapper):
```java
TemporalActPayload payload = switch (rs.getString("payload_type")) {
    case "SignalTemporalPayload" ->
        readJson(rs.getString("payload_json"), SignalTemporalPayload.class);
    default ->
        throw new IllegalArgumentException(
            "Unknown temporal payload type: " + rs.getString("payload_type"));
};
```

Do NOT use Jackson `@JsonTypeInfo`. Use this explicit switch.

### insertCreated — INSERT only

```java
@Override
public void insertCreated(TemporalAct act) {
    Objects.requireNonNull(act, "act is required");
    jdbcTemplate.update("""
        INSERT INTO temporal_acts
        (temporal_act_id, habitat_id, status, due_at_ms, payload_type, payload_json,
         notification_target_ref, created_by_ref_json, topology_version_at_registration,
         created_at_ms, updated_at_ms)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        act.temporalActId(), act.habitatId(), act.status().name(),
        act.dueAt().toEpochMilli(),
        act.payload().getClass().getSimpleName(),
        writeJson(act.payload()),
        act.notificationTargetRef(),
        writeJson(act.createdByRef()),
        act.topologyVersionAtRegistration(),
        act.createdAt().toEpochMilli(),
        act.updatedAt().toEpochMilli()
    );
}
```

### markFiredIfDueAndNonTerminal — conditional UPDATE

```java
@Override
public int markFiredIfDueAndNonTerminal(String habitatId, String temporalActId, Instant now) {
    return jdbcTemplate.update("""
        UPDATE temporal_acts
        SET status = 'FIRED',
            fired_at_ms = ?, terminal_at_ms = ?, updated_at_ms = ?
        WHERE temporal_act_id = ?
          AND habitat_id = ?
          AND status IN ('PENDING','ARMED')
          AND due_at_ms <= ?
        """,
        now.toEpochMilli(), now.toEpochMilli(), now.toEpochMilli(),
        temporalActId, habitatId, now.toEpochMilli()
    );
}
```

Row count is the idempotency signal: `0` = no fire, `1` = fire applied.


### markMisfiredIfDueAndNonTerminal — per-act conditional UPDATE

```java
@Override
public int markMisfiredIfDueAndNonTerminal(
    String habitatId,
    String temporalActId,
    Instant cutoff,
    Instant now
) {
    return jdbcTemplate.update("""
        UPDATE temporal_acts
        SET status = 'MISFIRED',
            terminal_at_ms = ?, updated_at_ms = ?
        WHERE temporal_act_id = ?
          AND habitat_id = ?
          AND status IN ('PENDING','ARMED')
          AND due_at_ms <= ?
        """,
        now.toEpochMilli(), now.toEpochMilli(),
        temporalActId, habitatId, cutoff.toEpochMilli()
    );
}
```

Do not implement a batch `UPDATE` unless it also records one `TemporalActMisfired` ledger entry for each TemporalAct classified as MISFIRED. The seed should prefer per-act conditional update + per-act ledger entry.

After Phase 3: `mvn test` — all 75 existing tests must pass before continuing.

---

## 5. Phase 4 — Create services

### TemporalActService — constructor with TransactionTemplate

**Do NOT use `@Transactional`.** Adapters are not Spring beans.
Use `TransactionTemplate` directly:

```java
public class TemporalActService {

    private final TemporalActWritePort writePort;
    private final TemporalActReadPort readPort;
    private final ScLedgerWritePort ledgerPort;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TemporalActService(
        TemporalActWritePort writePort,
        TemporalActReadPort readPort,
        ScLedgerWritePort ledgerPort,
        DataSource dataSource,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.writePort = Objects.requireNonNull(writePort, "writePort is required");
        this.readPort = Objects.requireNonNull(readPort, "readPort is required");
        this.ledgerPort = Objects.requireNonNull(ledgerPort, "ledgerPort is required");
        this.txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }
}

private String writeJson(Object value) {
    try {
        return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
        throw new IllegalStateException("failed to serialize temporal payload", ex);
    }
}
```

`TemporalEngineService` must also receive an `ObjectMapper` or equivalent helper if it builds ledger/outbox JSON payloads. Do not leave `writeJson(...)` undefined.

### createSignalTemporalAct — atomic create

```java
public String createSignalTemporalAct(
    String habitatId,
    SignalTemporalPayload payload,
    Instant dueAt,
    String notificationTargetRef,   // nullable, opaque; SHOULD be present for dispatchable TimerFired
    CreatedByRef createdByRef
) {
    String temporalActId = UUID.randomUUID().toString();
    Instant now = Instant.now(clock);
    TemporalAct act = new TemporalAct(
        temporalActId, habitatId, TemporalActStatus.PENDING,
        dueAt, payload, notificationTargetRef, createdByRef,
        null, now, now, null, null, null
    );
    UUID ledgerEntryId = UUID.randomUUID();
    LedgerEntry createdEntry = new LedgerEntry(
        ledgerEntryId, habitatId,
        LedgerRecordClass.LEDGER_ONLY, "TEMPORAL_ACT", temporalActId,
        SemanticKind.TEMPORAL_ACT_CREATED, "TemporalActCreated",
        writeJson(Map.of("temporalActId", temporalActId, "dueAt", dueAt.toString())),
        "temporal-act-created:" + temporalActId,
        now, null
    );
    txTemplate.executeWithoutResult(status -> {
        writePort.insertCreated(act);
        ledgerPort.appendLedgerEntry(createdEntry);
    });
    return temporalActId;
}
```

### TemporalEngineService — fire with full atomic transaction

`TemporalEngineService` must own a `TransactionTemplate`, `TemporalActWritePort`, `TemporalActReadPort`, `ScLedgerWritePort`, `ScOutboxWritePort`, `ObjectMapper`, and `Clock`. It must use the same `DataSource` as the H2 temporal repository and H2 ledger/outbox adapter so that `temporal_acts + ledger + outbox` commit atomically.

```java
public void fireDueTemporalActOnce(String habitatId, String temporalActId, Instant now) {
    UUID ledgerEntryId = UUID.randomUUID();
    UUID outboxEntryId = UUID.randomUUID();
    txTemplate.executeWithoutResult(status -> {
        int updated = writePort.markFiredIfDueAndNonTerminal(habitatId, temporalActId, now);
        if (updated == 0) return;  // already terminal or not due

        // Retrieve to get notificationTargetRef
        TemporalAct act = readPort.findById(habitatId, temporalActId)
            .orElseThrow(() -> new IllegalStateException("act disappeared after fire"));

        LedgerEntry firedEntry = new LedgerEntry(
            ledgerEntryId, habitatId,
            LedgerRecordClass.EVENT_OUTBOX, "TEMPORAL_ACT", temporalActId,
            SemanticKind.TIMER_FIRED, "TimerFired",
            writeJson(Map.of("temporalActId", temporalActId, "firedAt", now.toString())),
            "temporal-act-fired:" + temporalActId,
            now, null
        );
        ledgerPort.appendLedgerEntry(firedEntry);

        OutboxEntry timerFiredEntry = new OutboxEntry(
            outboxEntryId, ledgerEntryId, habitatId,
            OutboundKind.TIMER_FIRED_SIGNAL, DeliveryLane.SIGNAL,
            "sc-c.timer-fired",
            writeJson(Map.of(
                "temporalActId", temporalActId,
                "habitatId", habitatId,
                "firedAt", now.toString(),
                "label", ((SignalTemporalPayload) act.payload()).label()
            )),
            act.notificationTargetRef(),   // opaque — may be null
            "timer-fired-signal:" + temporalActId,
            OutboxEntryStatus.PENDING,
            now, now, null
        );
        outboxPort.appendOutboxEntry(timerFiredEntry);
    });
}
```

---

## 6. Phase 5 — TemporalEngineRunner

```java
public class TemporalEngineRunner {

    private final TemporalEngineService engine;
    private final long pollingIntervalMs;      // configurable, default ≤ 1000
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
                    // log, do not crash
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

Must NOT be annotated with `@Scheduled`.
Must NOT dispatch outbox entries.
Polling interval must be configurable. `TemporalEngineRunner` must receive `Clock` in its constructor and use `Instant.now(clock)`, not `Instant.now()`, so tests remain deterministic.

After Phase 5: `mvn test` — all 75 existing tests must still pass.


---

## 6.1 Phase 5b — MISFIRED classification

Implement deterministic startup/recovery classification as a method on `TemporalEngineService` or a narrow recovery helper:

```java
public void classifyMisfires(String habitatId, Instant recoveryNow) {
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

MISFIRED classification MUST NOT block the startup gate indefinitely. The seed MAY process all overdue acts in one pass; a configurable batch limit is optional.

Do not use one batch `UPDATE` unless the implementation also records one `TemporalActMisfired` ledger entry per TemporalAct classified as MISFIRED.

MISFIRED acts:

```text
- are terminal;
- are excluded from listActive() and findDue();
- remain queryable through listMisfired() and listTerminal();
- must never be re-fired by the runner.
```

---

## 7. Phase 6 — Tests

Create test class (prefer file-backed H2 for durability tests):

```java
@TempDir Path tempDir;
private final Clock clock = Clock.fixed(Instant.parse("2026-05-18T12:00:00Z"), ZoneOffset.UTC);

private String jdbcUrl(String name) {
    String dbPath = tempDir.resolve(name).toAbsolutePath().toString().replace('\\', '/');
    return "jdbc:h2:file:" + dbPath + ";DB_CLOSE_DELAY=0";
}

private DataSource dataSource(String jdbcUrl) {
    return new DriverManagerDataSource(jdbcUrl, "sa", "");
}
```

Required tests T-001 through T-022 from `acceptance-map.md`.

**Key tests to implement carefully:**

T-007 `cancelThenFireDoesNotCreateTimerFired`:
```text
1. Create act.
2. Cancel it (markFiredIfDueAndNonTerminal returns 0 after cancel).
3. Advance clock past dueAt.
4. Call fireDueTemporalActOnce.
5. Assert: no TimerFired ledger entry. No TimerFired outbox entry. Status CANCELLED.
```

T-011 `fireIdempotencyGuardPreventsDoubleFire`:
```text
1. Create act with dueAt = now.
2. Call fireDueTemporalActOnce → status FIRED, 1 ledger entry, 1 outbox entry.
3. Call fireDueTemporalActOnce again (same act).
4. Assert: row count 0 on second attempt. No additional ledger/outbox entry.
5. Assert: status still FIRED. Exactly 1 TimerFired ledger entry total.
```

T-019 `durabilityAfterRepositoryRecreation`:
```text
1. Create act using H2TemporalActRepository against jdbcUrl("durability").
2. Fire it.
3. Create NEW H2TemporalActRepository against same jdbcUrl.
4. Assert: act still present with status FIRED.
5. Assert: TimerFired ledger entry present (query via JdbcTemplate).
6. Assert: TimerFired outbox entry present with PENDING status.
```

T-022 `targetlessSignalTimerDoesNotRequireTopologyTarget`:
```text
1. Create SignalTemporalPayload with no deviceId, no endpointId, no capabilityId.
2. Create TemporalAct with that payload.
3. Assert: creation succeeds without any topology lookup.
4. Fire the act.
5. Assert: TimerFired outbox entry contains the notificationTargetRef.
6. Assert: no validateTarget call occurs anywhere in the fire path.
```

T-021 `virtualThreadRunnerDoesNotUseScheduledAnnotation`:
```text
Scan TemporalEngineRunner methods with reflection.
Assert: no method is annotated with @Scheduled.
```

---

## 8. Summary of files

```text
NEW production:
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
  adapter/persistence/H2TemporalActRepository.java

NEW test:
  [package]/TemporalActSeedTest.java

MODIFY: none required for existing files
```

---

## 9. Stop conditions — report BLOCKED

```text
- Any existing test fails after Phase 3 or Phase 5
- TransactionTemplate cannot coordinate H2TemporalActRepository + H2BaseTopologyRepository
  in one atomic write (different DataSources)
- ActionTemporalPayload or ActionRequest becomes necessary for Profile A
- @Scheduled is needed to make the engine work
- SC-B / NATS / JetStream / dispatcher required
- SignalTemporalPayload requires a topology target
- Fire occurs before dueAt
- Fire occurs twice for one TemporalAct
- MISFIRED batch update marks multiple acts but creates fewer ledger entries
- Outbox entry appended with non-PENDING status
- MU-015 PENDING-only guard is removed or weakened
```
