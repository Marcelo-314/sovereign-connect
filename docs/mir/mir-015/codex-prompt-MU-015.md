# Codex Prompt — MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

```text
MU:               MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Operational Slot: MU-015
Branch:           feat/sc-c-outbox-ledger-storage-seed
Commit:           feat(sc-c): add outbox ledger storage seed
Baseline commit:  8261186 feat(sc-c): add materialization decision replay seed
Baseline tests:   62 / 0 failures / 0 errors / 0 skipped
Package version:  v0.1.1-patched
```

---

## 0. Mandatory reading

Read these files before coding:

```text
docs/mir/mir-015/MIR-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001.md
docs/mir/mir-015/code-surface-audit.md
docs/mir/mir-015/decision-confirmation.md
docs/mir/mir-015/context.md
docs/mir/mir-015/acceptance-map.md
```

---

## 1. Mission

Add two H2 tables, two write ports, minimal model types and storage tests.

```text
sc_c_ledger_entries  — append-only semantic evidence records
sc_c_outbox_entries  — append-created dispatch intent records, PENDING only in MU-015
ScLedgerWritePort    — SC-C port, append-only
ScOutboxWritePort    — SC-C port, append-only
```

Do not implement dispatcher behavior, TemporalActs, command execution, SC-B, NATS or JetStream.

---

## 2. Phase 1 — Create model and ports

Create package tree:

```text
src/main/java/com/sovereign/connect/core/scledger/model/
src/main/java/com/sovereign/connect/core/scledger/port/
```

Do not use `core.outbox` or `core.topology` packages.

### 2.1 ScLedgerWritePort.java

```java
package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.LedgerEntry;

public interface ScLedgerWritePort {
    void appendLedgerEntry(LedgerEntry entry);
}
```

### 2.2 ScOutboxWritePort.java

```java
package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.OutboxEntry;

public interface ScOutboxWritePort {
    void appendOutboxEntry(OutboxEntry entry);
}
```

### 2.3 LedgerEntry.java

```java
package com.sovereign.connect.core.scledger.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
    UUID ledgerEntryId,
    String habitatId,
    LedgerRecordClass recordClass,
    String aggregateType,
    String aggregateId,
    SemanticKind semanticKind,
    String payloadType,
    String payloadJson,
    String idempotencyKey,
    Instant recordedAt,
    String metadataJson
) {
    public LedgerEntry {
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(recordClass, "recordClass is required");
        Objects.requireNonNull(aggregateType, "aggregateType is required");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(semanticKind, "semanticKind is required");
        Objects.requireNonNull(payloadType, "payloadType is required");
        Objects.requireNonNull(payloadJson, "payloadJson is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
    }
}
```

### 2.4 OutboxEntry.java

```java
package com.sovereign.connect.core.scledger.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxEntry(
    UUID outboxEntryId,
    UUID ledgerEntryId,
    String habitatId,
    OutboundKind outboundKind,
    DeliveryLane deliveryLane,
    String logicalTopic,
    String semanticPayloadJson,
    String notificationTargetRef,
    String idempotencyKey,
    OutboxEntryStatus status,
    Instant createdAt,
    Instant updatedAt,
    String metadataJson
) {
    public OutboxEntry {
        Objects.requireNonNull(outboxEntryId, "outboxEntryId is required");
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(outboundKind, "outboundKind is required");
        Objects.requireNonNull(deliveryLane, "deliveryLane is required");
        Objects.requireNonNull(logicalTopic, "logicalTopic is required");
        Objects.requireNonNull(semanticPayloadJson, "semanticPayloadJson is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
```

### 2.5 Enums

Create one file for each enum.

```java
public enum LedgerRecordClass {
    LEDGER_ONLY,
    EVENT_OUTBOX,
    COMMAND_OUTBOX,
    RESPONSE_OUTBOX,
    DIAGNOSTIC_OUTBOX,
    DELIVERY_OBSERVATION
}

public enum SemanticKind {
    TEMPORAL_ACT_CREATED,
    TEMPORAL_ACT_CANCELLED,
    TEMPORAL_ACT_FIRED,
    TEMPORAL_ACT_MISFIRED,
    TEMPORAL_ACT_FAILED,
    TIMER_FIRED
}

public enum OutboundKind {
    TIMER_FIRED_SIGNAL,
    TEMPORAL_ACT_CANCELLED_SIGNAL,
    TEMPORAL_ACT_MISFIRED_DIAGNOSTIC,
    COMMAND
}

public enum DeliveryLane {
    SIGNAL,
    COMMAND
}

public enum OutboxEntryStatus {
    PENDING,
    CLAIMED,
    DISPATCHED,
    DISPATCH_FAILED,
    RETRY_WAIT,
    DEAD_LETTERED,
    SUPPRESSED
}
```

`COMMAND` is reserved vocabulary only. Do not implement command behavior.

Compile check after Phase 1:

```bash
mvn -q -DskipTests compile
```

---

## 3. Phase 2 — Update H2BaseTopologyRepository

File:

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
```

### 3.1 Update implements clause

Add these imports:

```java
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
```

Update the class declaration:

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository,
               CoreSnapshotReadPort,
               EndpointHealthWritePort,
               TopologyMaterializationStatePort,
               MaterializationDecisionReplayPort,
               ScLedgerWritePort,
               ScOutboxWritePort
```

### 3.2 Add tables and FK enforcement to createSchema()

Add the following at the end of `createSchema()`, after existing table/index creation.

Use one `jdbcTemplate.execute(...)` per DDL statement.

```java
jdbcTemplate.execute(
    """
        CREATE TABLE IF NOT EXISTS sc_c_ledger_entries (
            ledger_entry_id VARCHAR(36) PRIMARY KEY,
            habitat_id VARCHAR(255) NOT NULL,
            record_class VARCHAR(64) NOT NULL,
            aggregate_type VARCHAR(128) NOT NULL,
            aggregate_id VARCHAR(255) NOT NULL,
            semantic_kind VARCHAR(128) NOT NULL,
            payload_type VARCHAR(255) NOT NULL,
            payload_json CLOB NOT NULL,
            idempotency_key VARCHAR(512) NOT NULL,
            recorded_at_ms BIGINT NOT NULL,
            metadata_json CLOB,
            CONSTRAINT uq_sc_c_ledger_entry_habitat
                UNIQUE (ledger_entry_id, habitat_id),
            CONSTRAINT uq_sc_c_ledger_idempotency
                UNIQUE (habitat_id, idempotency_key)
        )
        """
);

jdbcTemplate.execute(
    """
        CREATE INDEX IF NOT EXISTS ix_ledger_habitat_aggregate
        ON sc_c_ledger_entries(habitat_id, aggregate_type, aggregate_id)
        """
);

jdbcTemplate.execute(
    """
        CREATE TABLE IF NOT EXISTS sc_c_outbox_entries (
            outbox_entry_id VARCHAR(36) PRIMARY KEY,
            ledger_entry_id VARCHAR(36) NOT NULL,
            habitat_id VARCHAR(255) NOT NULL,
            outbound_kind VARCHAR(128) NOT NULL,
            delivery_lane VARCHAR(64) NOT NULL,
            logical_topic VARCHAR(255) NOT NULL,
            semantic_payload_json CLOB NOT NULL,
            notification_target_ref VARCHAR(512),
            idempotency_key VARCHAR(512) NOT NULL,
            status VARCHAR(64) NOT NULL
                CHECK(status IN ('PENDING','CLAIMED','DISPATCHED',
                                 'DISPATCH_FAILED','RETRY_WAIT',
                                 'DEAD_LETTERED','SUPPRESSED')),
            attempt_count INTEGER NOT NULL DEFAULT 0,
            claimed_at_ms BIGINT,
            claim_expires_at_ms BIGINT,
            claimed_by VARCHAR(255),
            expires_at_ms BIGINT,
            created_at_ms BIGINT NOT NULL,
            updated_at_ms BIGINT NOT NULL,
            metadata_json CLOB,
            CONSTRAINT fk_sc_c_outbox_ledger
                FOREIGN KEY (ledger_entry_id, habitat_id)
                REFERENCES sc_c_ledger_entries(ledger_entry_id, habitat_id),
            CONSTRAINT uq_sc_c_outbox_idempotency
                UNIQUE (habitat_id, idempotency_key)
        )
        """
);

jdbcTemplate.execute(
    """
        CREATE INDEX IF NOT EXISTS ix_outbox_status_created
        ON sc_c_outbox_entries(habitat_id, status, created_at_ms)
        """
);

jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
```

Critical:

```text
SET REFERENTIAL_INTEGRITY TRUE must come after table creation.
The FK must reject:
  - missing ledger_entry_id;
  - outbox habitat_id that does not match the referenced ledger habitat_id.
```

If H2 FK behavior blocks implementation, use explicit adapter validation in `appendOutboxEntry(...)` before insert and document it in the implementation report.

### 3.3 appendLedgerEntry — INSERT only, no MERGE

```java
@Override
public void appendLedgerEntry(LedgerEntry entry) {
    Objects.requireNonNull(entry, "entry is required");
    jdbcTemplate.update(
        """
            INSERT INTO sc_c_ledger_entries
            (ledger_entry_id, habitat_id, record_class, aggregate_type, aggregate_id,
             semantic_kind, payload_type, payload_json, idempotency_key,
             recorded_at_ms, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        entry.ledgerEntryId().toString(),
        entry.habitatId(),
        entry.recordClass().name(),
        entry.aggregateType(),
        entry.aggregateId(),
        entry.semanticKind().name(),
        entry.payloadType(),
        entry.payloadJson(),
        entry.idempotencyKey(),
        entry.recordedAt().toEpochMilli(),
        entry.metadataJson()
    );
}
```

### 3.4 appendOutboxEntry — INSERT only, no MERGE

```java
@Override
public void appendOutboxEntry(OutboxEntry entry) {
    Objects.requireNonNull(entry, "entry is required");
    jdbcTemplate.update(
        """
            INSERT INTO sc_c_outbox_entries
            (outbox_entry_id, ledger_entry_id, habitat_id, outbound_kind,
             delivery_lane, logical_topic, semantic_payload_json,
             notification_target_ref, idempotency_key, status,
             attempt_count, created_at_ms, updated_at_ms, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        entry.outboxEntryId().toString(),
        entry.ledgerEntryId().toString(),
        entry.habitatId(),
        entry.outboundKind().name(),
        entry.deliveryLane().name(),
        entry.logicalTopic(),
        entry.semanticPayloadJson(),
        entry.notificationTargetRef(),
        entry.idempotencyKey(),
        entry.status().name(),
        0,
        entry.createdAt().toEpochMilli(),
        entry.updatedAt().toEpochMilli(),
        entry.metadataJson()
    );
}
```

Compile check after Phase 2:

```bash
mvn -q -DskipTests compile
```

Run full test suite:

```bash
mvn test
```

If any existing test fails, stop and report `BLOCKED`.

---

## 4. Phase 3 — Tests

Create:

```text
src/test/java/com/sovereign/connect/adapter/persistence/OutboxLedgerStorageSeedTest.java
```

Use file-backed H2 for durability/recreation tests.

Use test-local `JdbcTemplate` for readback. Do not create read ports.

### 4.1 Test infrastructure

```java
@TempDir Path tempDir;
private final Clock clock = Clock.fixed(Instant.parse("2026-05-16T12:00:00Z"), ZoneOffset.UTC);

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

### 4.2 Required tests

#### T-1 — appendLedgerEntryPersistsDurably

```text
1. Create file-backed DataSource with jdbcUrl("ledger-durability").
2. Construct H2BaseTopologyRepository.
3. Call appendLedgerEntry(...) with semanticKind=TEMPORAL_ACT_CREATED.
4. Construct a new H2BaseTopologyRepository against the same URL.
5. Query sc_c_ledger_entries using test-local JdbcTemplate.
6. Assert one row exists and semantic_kind / habitat_id match.
```

#### T-2 — appendOutboxEntryPersistsDurably

```text
1. Append a LedgerEntry.
2. Append an OutboxEntry with status=PENDING and matching habitat_id / ledger_entry_id.
3. Recreate repository.
4. Query sc_c_outbox_entries.
5. Assert one row exists, status=PENDING and notification_target_ref equals the provided value.
```

#### T-3 — duplicateLedgerIdempotencyKeyRejected

```text
1. Append LedgerEntry with idempotencyKey="temporal-act-fired:act-001".
2. Attempt second LedgerEntry append in the same habitat with a different ledgerEntryId and the same idempotencyKey.
3. Assert DataIntegrityViolationException or equivalent.
4. This must prove UNIQUE(habitat_id, idempotency_key), not primary-key rejection.
```

#### T-4 — duplicateOutboxIdempotencyKeyRejected

```text
1. Append a valid LedgerEntry.
2. Append OutboxEntry with idempotencyKey="timer-fired-signal:act-001".
3. Attempt second OutboxEntry append in the same habitat with a different outboxEntryId and the same idempotencyKey.
4. Assert DataIntegrityViolationException or equivalent.
```

#### T-5 — sameIdempotencyKeyAllowedAcrossDifferentHabitats

```text
1. Append LedgerEntry with idempotencyKey="temporal-act-fired:act-001" in habitat-A.
2. Append LedgerEntry with the same idempotencyKey in habitat-B using a different ledgerEntryId.
3. Assert both rows exist.
4. Optionally repeat the same pattern for OutboxEntry using two matching per-habitat ledger rows.
5. This proves uniqueness is habitat-scoped, not global.
```

#### T-6 — outboxEntryRequiresExistingLedgerEntry

```text
1. Append LedgerEntry in habitat-A.
2. Append OutboxEntry referencing that ledgerEntryId and habitat-A.
3. Assert no exception and row stored.
```

#### T-7 — outboxEntryWithoutLedgerReferenceRejected

```text
1. Do not append any LedgerEntry.
2. Construct OutboxEntry with a random ledgerEntryId.
3. Call appendOutboxEntry(...).
4. Assert exception from FK enforcement or deterministic adapter validation.
```

#### T-8 — outboxEntryHabitatMustMatchLedgerHabitat

```text
1. Append LedgerEntry with habitatId=habitat-A.
2. Construct OutboxEntry referencing the same ledgerEntryId but habitatId=habitat-B.
3. Call appendOutboxEntry(...).
4. Assert exception from composite FK enforcement or deterministic adapter validation.
5. This proves an outbox row cannot point to semantic evidence from another habitat.
```

#### T-9 — notificationTargetRefCarriedOpaquely

```text
1. Append valid LedgerEntry and OutboxEntry.
2. Use notificationTargetRef="surface:bedroom-left".
3. Query row from sc_c_outbox_entries.
4. Assert column value equals "surface:bedroom-left" exactly.
5. Do not parse or resolve this string.
```

#### T-10 — temporalActFiredLikeRecordCanBeStoredWithoutTemporalEngine

```text
1. Append LedgerEntry with semanticKind=TEMPORAL_ACT_FIRED or TIMER_FIRED.
2. Append OutboxEntry with outboundKind=TIMER_FIRED_SIGNAL and deliveryLane=SIGNAL.
3. Include notificationTargetRef="surface:living-room".
4. Recreate repository.
5. Query both tables.
6. Assert both rows exist.
7. Assert no TemporalAct class or Temporal Engine is required.
```

#### T-11 — noDispatcherSurfaceIntroduced

Implement a production-source scan using `java.nio.file.Files.walk(Path.of("src/main/java"))`.

Skip missing directories safely.

Assert no production source introduces:

```text
ScLedgerReadPort
ScOutboxReadPort
claimReady
markClaimed
markDispatched
retryLoop
pollLoop
pollingLoop
org.nats
io.nats
JetStream
```

Do not fail because enum values such as `CLAIMED` or `DISPATCHED` exist.

#### T-12 — domainServicesRemainSqlFree

Scan these packages if present:

```text
src/main/java/com/sovereign/connect/core/topology/service/
src/main/java/com/sovereign/connect/core/topology/materialization/
```

Assert no file contains:

```text
import java.sql
import javax.sql.DataSource
import org.springframework.jdbc
JdbcTemplate
import org.h2
```

---

## 5. Phase 4 — Validate

Run:

```bash
mvn test
```

Expected:

```text
All 62 existing tests pass.
All new OutboxLedgerStorageSeedTest tests pass.
0 failures, 0 errors.
Total: >= 74 tests.
```

---

## 6. Strict non-goals — STOP if these appear

```text
ScLedgerReadPort or ScOutboxReadPort
claimReadyEntries, markClaimed, markDispatched, retryLoop, pollingLoop
sc_c_delivery_observations, sc_c_terminal_responses, sc_c_outbox_attempts
temporal_acts table or TemporalAct aggregate
Temporal Engine
ActionRequest
command dispatch
SC-B
NATS
JetStream
broker subjects
Flyway
production SQLite migrations
changes to BaseTopologyService
changes to DefaultTopologyMaterializationService constructors
```

Report `BLOCKED` and do not continue if any stop condition occurs.

---

## 7. Files summary

```text
NEW (10 files):
  src/main/java/com/sovereign/connect/core/scledger/model/LedgerEntry.java
  src/main/java/com/sovereign/connect/core/scledger/model/OutboxEntry.java
  src/main/java/com/sovereign/connect/core/scledger/model/LedgerRecordClass.java
  src/main/java/com/sovereign/connect/core/scledger/model/SemanticKind.java
  src/main/java/com/sovereign/connect/core/scledger/model/OutboundKind.java
  src/main/java/com/sovereign/connect/core/scledger/model/DeliveryLane.java
  src/main/java/com/sovereign/connect/core/scledger/model/OutboxEntryStatus.java
  src/main/java/com/sovereign/connect/core/scledger/port/ScLedgerWritePort.java
  src/main/java/com/sovereign/connect/core/scledger/port/ScOutboxWritePort.java
  src/test/java/com/sovereign/connect/adapter/persistence/OutboxLedgerStorageSeedTest.java

MODIFY (1 file):
  src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
    - implements clause: add two write ports;
    - createSchema(): add two tables, two indexes and SET REFERENTIAL_INTEGRITY TRUE;
    - add appendLedgerEntry(...);
    - add appendOutboxEntry(...).
```

No other files change. No existing test call sites need updating.
