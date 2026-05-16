# Context — MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001

```text
MU:               MU-SOV-SC-C-OUTBOX-LEDGER-STORAGE-SEED-001
Operational Slot: MU-015
Audit:            CSA-MU-015 v0.1.1-merged
Branch:           feat/sc-c-outbox-ledger-storage-seed
Baseline commit:  8261186 feat(sc-c): add materialization decision replay seed
Baseline tests:   62 / 0 failures / 0 errors / 0 skipped
Package version:  v0.1.1-patched
```

---

## 0. Execution thesis

MU-015 adds the minimum append-only storage substrate required before TemporalActs can implement Profile A firing without inventing an engine-local log.

```text
SC-C owns semantic ledger records.
SC-C owns durable outbox append intent.
SC-B is absent here.
Temporal Engine is absent here.
The output is: two H2 tables + two write ports + model types + tests.
```

This package incorporates the post-review patches:

```text
- package canonical: com.sovereign.connect.core.scledger.*;
- ScLedgerWritePort, not ScLedgerPort;
- UNIQUE(habitat_id, idempotency_key), not global idempotency uniqueness;
- composite ledger/outbox FK to enforce same-habitat linkage;
- H2 FK enforcement note: SET REFERENTIAL_INTEGRITY TRUE or explicit adapter validation;
- no partial H2 index requirement;
- no read ports;
- no dispatcher;
- no TemporalAct implementation.
```

---

## 1. What already exists — exact code to build from

### 1.1 H2BaseTopologyRepository current state

```java
public class H2BaseTopologyRepository
    implements BaseTopologyRepository,
               CoreSnapshotReadPort,
               EndpointHealthWritePort,
               TopologyMaterializationStatePort,
               MaterializationDecisionReplayPort
```

Five tables currently in `createSchema()`:

```text
topology_snapshots
mutation_records
device_states
endpoint_health
materialization_decision_replay
```

None of the following exist anywhere:

```text
sc_c_ledger_entries
sc_c_outbox_entries
ScLedgerWritePort
ScOutboxWritePort
TemporalAct
ActionRequest
NATS
JetStream
```

### 1.2 Schema creation pattern — exact style to follow

Each table uses a dedicated `jdbcTemplate.execute(...)` call.

```java
jdbcTemplate.execute(
    """
        CREATE TABLE IF NOT EXISTS mutation_records (
            mutation_id VARCHAR(255) PRIMARY KEY,
            habitat_id VARCHAR(255) NOT NULL,
            ...
        )
        """
);

jdbcTemplate.execute(
    """
        CREATE INDEX IF NOT EXISTS ix_name
        ON table_name(col1, col2)
        """
);
```

Add one `jdbcTemplate.execute(...)` per table DDL and one per index. Do not combine DDL statements.

### 1.3 Append INSERT pattern — from mutation records

The reference append method is the current mutation-record writer.

Ledger rows are immutable semantic evidence and must use `INSERT`.

Outbox rows are append-created in MU-015 and remain `PENDING` only. Future dispatcher/status updates are explicitly deferred.

```text
Use INSERT, not MERGE.
If duplicate primary key or duplicate habitat-scoped idempotency key appears, let the database constraint throw.
```

### 1.4 Test infrastructure — file-backed H2 pattern

Use the same pattern as existing persistence tests.

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

Use `jdbc:h2:file:...` for recovery-visible durability tests. Construct a new `H2BaseTopologyRepository(dataSource, mapper(), clock)` instance to simulate repository recreation after restart.

---

## 2. New artifacts to create

### 2.1 Package structure

```text
src/main/java/com/sovereign/connect/core/scledger/model/
  LedgerEntry.java
  OutboxEntry.java
  LedgerRecordClass.java
  SemanticKind.java
  OutboundKind.java
  DeliveryLane.java
  OutboxEntryStatus.java

src/main/java/com/sovereign/connect/core/scledger/port/
  ScLedgerWritePort.java
  ScOutboxWritePort.java
```

Do not use `core.outbox` or `core.topology` for these types.

### 2.2 ScLedgerWritePort

```java
package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.LedgerEntry;

public interface ScLedgerWritePort {
    void appendLedgerEntry(LedgerEntry entry);
}
```

### 2.3 ScOutboxWritePort

```java
package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.OutboxEntry;

public interface ScOutboxWritePort {
    void appendOutboxEntry(OutboxEntry entry);
}
```

### 2.4 LedgerEntry record

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
        // metadataJson is nullable
    }
}
```

### 2.5 OutboxEntry record

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
        // notificationTargetRef and metadataJson are nullable
    }
}
```

### 2.6 Enums

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

`COMMAND` is vocabulary reserved for Profile B. MU-015 must not implement command behavior.

---

## 3. H2BaseTopologyRepository changes

### 3.1 Implements clause update

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

### 3.2 Tables — add to createSchema() after existing tables

Follow the `jdbcTemplate.execute(...)` pattern exactly.

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

Critical notes:

```text
SET REFERENTIAL_INTEGRITY TRUE must come after table creation.
The composite FK prevents cross-habitat ledger/outbox corruption.
If H2 FK enforcement fails or is unavailable, appendOutboxEntry(...) MUST explicitly validate both:
  - ledger_entry_id exists;
  - ledger habitat_id equals outbox habitat_id.
```

### 3.3 appendLedgerEntry — exact implementation

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

### 3.4 appendOutboxEntry — exact implementation

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

No manual existence check is required if the composite FK is active. If the implementation discovers H2 enforcement does not behave as expected, add deterministic adapter validation before insert.

---

## 4. Test infrastructure note

The test uses file-backed H2 so rows survive repository recreation.

The test reads back with test-local `JdbcTemplate`.

```text
No ScLedgerReadPort.
No ScOutboxReadPort.
No production read contract.
```

Example readback:

```java
JdbcTemplate testJdbc = new JdbcTemplate(dataSource(jdbcUrl));
List<Map<String, Object>> rows = testJdbc.queryForList(
    "SELECT * FROM sc_c_ledger_entries WHERE habitat_id = ?",
    habitatId
);
```

---

## 5. Files to create and modify

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
    - add ScLedgerWritePort, ScOutboxWritePort to implements;
    - add two tables, two indexes and SET REFERENTIAL_INTEGRITY TRUE to createSchema();
    - add appendLedgerEntry(...);
    - add appendOutboxEntry(...).
```

No existing constructor call site needs updating. No existing test class needs modification.

---

## 6. Strict non-goals

```text
No ScLedgerReadPort.
No ScOutboxReadPort.
No dispatcher.
No claim loop.
No polling loop.
No retry loop.
No status transition methods.
No sc_c_delivery_observations.
No sc_c_terminal_responses.
No sc_c_outbox_attempts.
No temporal_acts table.
No TemporalAct aggregate.
No Temporal Engine.
No ActionRequest.
No command dispatch.
No SC-B runtime.
No NATS / JetStream.
No Flyway migrations.
No production SQLite migrations.
No changes to BaseTopologyService.
No changes to DefaultTopologyMaterializationService constructors.
```
