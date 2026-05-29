# Execution Context — MU-026 SC-B Outbox Bridge Seed

```text
Version:  v0.2.0-candidate
MIR:      MIR-SOV-SC-B-OUTBOX-BRIDGE-SEED-001 v0.2.0-candidate
MU:       MU-SOV-SC-B-OUTBOX-BRIDGE-SEED-001
Slot:     MU-026
Track:    SC-B Runtime Dispatch Hardening / H2
Branch:   feat/sc-b-mir-026-outbox-bridge-seed
Baseline: post-MU-025 / sovereign-connect 317/0/0 · bus 77/0/0 · SC-C 240/0/0
```

---

## 0. Purpose

Bridge SC-C scledger outbox rows into SC-B technical dispatch candidates:

```text
SC-C OutboxEntry
  → ScOutboxDispatchReadPort (core.scledger.port)
  → integration.scledgerdispatch projector/adapter
  → DispatchCandidate
  → RuntimeDispatchService
  → ScBusPort
```

---

## 1. Confirmed baseline facts (verified against sovereign-connect-025-patch.zip)

```text
OutboxEntry record shape (DO NOT ALTER):
  UUID    outboxEntryId
  UUID    ledgerEntryId
  String  habitatId
  OutboundKind outboundKind
  DeliveryLane deliveryLane
  String  logicalTopic
  String  semanticPayloadJson
  String  notificationTargetRef   (nullable)
  String  idempotencyKey
  OutboxEntryStatus status
  Instant createdAt
  Instant updatedAt
  String  metadataJson            (nullable)

sc_c_outbox_entries table columns (from V3 migration + INSERT statement):
  outbox_entry_id TEXT PRIMARY KEY
  ledger_entry_id TEXT
  habitat_id TEXT
  outbound_kind TEXT
  delivery_lane TEXT
  logical_topic TEXT
  semantic_payload_json TEXT
  notification_target_ref TEXT    (nullable)
  idempotency_key TEXT
  status TEXT
  attempt_count INTEGER DEFAULT 0  ← NOT in OutboxEntry record; DO NOT map
  created_at_ms INTEGER
  updated_at_ms INTEGER
  metadata_json TEXT               (nullable)

CRITICAL: attempt_count is in the DB schema but NOT in OutboxEntry Java record.
The SELECT in findDispatchableEntries MUST NOT include attempt_count.
The INSERT in SQLiteScLedgerOutboxRepository does include it — DO NOT copy that
column list for the SELECT.

DeliveryLane values: SIGNAL, COMMAND, EVENT, RESPONSE
OutboundKind includes: TIMER_FIRED_SIGNAL, TEMPORAL_ACT_CANCELLED_SIGNAL,
  TEMPORAL_ACT_MISFIRED_DIAGNOSTIC, COMMAND

SIGNAL scope rule — verified in TemporalEngineService.java:233:
  outboundKind = TIMER_FIRED_SIGNAL
  logicalTopic = "sc-c.timer-fired"   ← exact string, confirmed in source
This is the ONLY SIGNAL variant that maps to ScBusLane.EVENT in this seed.

DispatchCandidate record (DO NOT ALTER):
  UUID    dispatchRecordId
  UUID    sourceRecordId
  ScBusLane lane
  String  logicalTopic
  String  partitionKey
  UUID    correlationId
  UUID    causationId
  UUID    messageId

RuntimeDispatchService constructor (7 dependencies, exact order):
  ScBusPort busPort
  DispatchCandidateReadPort candidateReadPort
  DispatchStateWritePort stateWritePort
  DispatchObservationPort observationPort
  EnvelopeValidationService envelopeValidationService
  RoutingKeyValidationService routingKeyValidationService
  CorrelationValidationService correlationValidationService

sc_b_dispatch_records.source_record_id: nullable column already present from V100 migration.
H2 MUST NOT add a new migration. If a migration seems necessary: STOP and report.
```

---

## 2. Hard boundaries

```text
ALLOWED:
  core.scledger.port.ScOutboxDispatchReadPort          [new interface, one method]
  adapter.persistence.sqlite.SQLiteScLedgerOutboxRepository [extend, add implements]
  integration.scledgerdispatch.*                       [new package, four classes]

FORBIDDEN from bus.**:
  import com.sovereign.connect.core.**
  import com.sovereign.connect.adapter.**

FORBIDDEN from core.**:
  import com.sovereign.connect.bus.runtime.**

ONLY integration.scledgerdispatch may import BOTH:
  com.sovereign.connect.core.scledger.*
  com.sovereign.connect.bus.*

integration.scledgerdispatch MUST NOT import:
  core.topology.*
  core.temporal.*
  adapter.*
  NATS / JetStream / broker APIs

No new SC-B migrations (V100+ space). H2 uses existing V100 schema.
No ScdCommand production class.
No serialization runtime.
No OutboxEntryStatus mutation.
No semanticPayloadJson parsing into domain types.
DeliveryLane.SIGNAL MUST NEVER map to ScBusLane.COMMAND.
```

---

## 3. New port — ScOutboxDispatchReadPort

File: `src/main/java/com/sovereign/connect/core/scledger/port/ScOutboxDispatchReadPort.java`

```java
package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.OutboxEntry;
import java.util.List;

public interface ScOutboxDispatchReadPort {
    List<OutboxEntry> findDispatchableEntries(int limit);
}
```

---

## 4. Extend SQLiteScLedgerOutboxRepository

File: `src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteScLedgerOutboxRepository.java`

Add `ScOutboxDispatchReadPort` to the class declaration:

```java
public class SQLiteScLedgerOutboxRepository
    implements ScLedgerWritePort, ScOutboxWritePort, ScOutboxDispatchReadPort {
```

Add the method. The SELECT must NOT include `attempt_count` — that column exists
in the table but NOT in the OutboxEntry Java record:

```java
@Override
public List<OutboxEntry> findDispatchableEntries(int limit) {
    if (limit <= 0) {
        throw new IllegalArgumentException("limit must be positive");
    }
    return jdbcTemplate.query(
        """
            SELECT outbox_entry_id, ledger_entry_id, habitat_id, outbound_kind,
                   delivery_lane, logical_topic, semantic_payload_json,
                   notification_target_ref, idempotency_key, status,
                   created_at_ms, updated_at_ms, metadata_json
            FROM sc_c_outbox_entries
            WHERE status = ?
            ORDER BY created_at_ms ASC, outbox_entry_id ASC
            LIMIT ?
            """,
        (rs, rowNum) -> new OutboxEntry(
            UUID.fromString(rs.getString("outbox_entry_id")),
            UUID.fromString(rs.getString("ledger_entry_id")),
            rs.getString("habitat_id"),
            OutboundKind.valueOf(rs.getString("outbound_kind")),
            DeliveryLane.valueOf(rs.getString("delivery_lane")),
            rs.getString("logical_topic"),
            rs.getString("semantic_payload_json"),
            rs.getString("notification_target_ref"),
            rs.getString("idempotency_key"),
            OutboxEntryStatus.valueOf(rs.getString("status")),
            Instant.ofEpochMilli(rs.getLong("created_at_ms")),
            Instant.ofEpochMilli(rs.getLong("updated_at_ms")),
            rs.getString("metadata_json")
        ),
        OutboxEntryStatus.PENDING.name(),
        limit
    );
}
```

Required imports (add if not already present):
```java
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.port.ScOutboxDispatchReadPort;
import java.time.Instant;
import java.util.UUID;
```

This method must NOT execute UPDATE, DELETE, INSERT, or touch attempt_count.

---

## 5. DispatchRecordIdFactory

File: `src/main/java/com/sovereign/connect/integration/scledgerdispatch/DispatchRecordIdFactory.java`

```java
package com.sovereign.connect.integration.scledgerdispatch;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class DispatchRecordIdFactory {
    private static final String PREFIX = "sc-b.dispatch:";

    public UUID fromSourceRecordId(UUID sourceRecordId) {
        Objects.requireNonNull(sourceRecordId, "sourceRecordId is required");
        return UUID.nameUUIDFromBytes(
            (PREFIX + sourceRecordId).getBytes(StandardCharsets.UTF_8));
    }
}
```

The resulting UUID is deterministic and distinct from the raw sourceRecordId.
Random generation is forbidden — AC-026-016 requires determinism.

---

## 6. DeliveryLaneToScBusLaneMapper

File: `src/main/java/com/sovereign/connect/integration/scledgerdispatch/DeliveryLaneToScBusLaneMapper.java`

```java
package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;

import java.util.Objects;
import java.util.Optional;

public final class DeliveryLaneToScBusLaneMapper {
    // Verified against TemporalEngineService.java:233 — exact string
    static final String TIMER_FIRED_TOPIC = "sc-c.timer-fired";

    public Optional<ScBusLane> map(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        return switch (entry.deliveryLane()) {
            case COMMAND  -> Optional.of(ScBusLane.COMMAND);
            case EVENT    -> Optional.of(ScBusLane.EVENT);
            case RESPONSE -> Optional.of(ScBusLane.RESPONSE);
            case SIGNAL   -> mapSignal(entry);
        };
    }

    private Optional<ScBusLane> mapSignal(OutboxEntry entry) {
        if (entry.outboundKind() == OutboundKind.TIMER_FIRED_SIGNAL
                && TIMER_FIRED_TOPIC.equals(entry.logicalTopic())) {
            return Optional.of(ScBusLane.EVENT);
        }
        return Optional.empty();
    }
}
```

SIGNAL MUST NEVER map to COMMAND — any future change that routes SIGNAL to
COMMAND is a hard stop.

---

## 7. OutboxEntryDispatchProjector

File: `src/main/java/com/sovereign/connect/integration/scledgerdispatch/OutboxEntryDispatchProjector.java`

```java
package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.core.scledger.model.OutboxEntry;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OutboxEntryDispatchProjector {

    private final DispatchRecordIdFactory dispatchRecordIdFactory;
    private final DeliveryLaneToScBusLaneMapper laneMapper;

    public OutboxEntryDispatchProjector(
            DispatchRecordIdFactory dispatchRecordIdFactory,
            DeliveryLaneToScBusLaneMapper laneMapper) {
        this.dispatchRecordIdFactory =
            Objects.requireNonNull(dispatchRecordIdFactory, "dispatchRecordIdFactory is required");
        this.laneMapper =
            Objects.requireNonNull(laneMapper, "laneMapper is required");
    }

    public Optional<DispatchCandidate> project(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        if (entry.habitatId() == null || entry.habitatId().isBlank()) {
            throw new IllegalArgumentException("habitatId is required");
        }
        if (entry.logicalTopic() == null || entry.logicalTopic().isBlank()) {
            throw new IllegalArgumentException("logicalTopic is required");
        }
        Optional<ScBusLane> lane = laneMapper.map(entry);
        if (lane.isEmpty()) {
            return Optional.empty();
        }
        UUID sourceRecordId  = entry.outboxEntryId();
        UUID dispatchRecordId = dispatchRecordIdFactory.fromSourceRecordId(sourceRecordId);
        UUID correlationId    = deterministicUuid("sc-b.correlation:" + sourceRecordId);
        UUID messageId        = deterministicUuid("sc-b.message:" + sourceRecordId);
        return Optional.of(new DispatchCandidate(
            dispatchRecordId,
            sourceRecordId,
            lane.get(),
            entry.logicalTopic(),
            entry.habitatId(),      // partitionKey = habitatId
            correlationId,
            entry.ledgerEntryId(),  // causationId = ledgerEntryId
            messageId
        ));
    }

    private UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
```

`semanticPayloadJson` is NOT accessed in projection. It stays in OutboxEntry,
untouched, as raw SC-C payload evidence.

---

## 8. ScLedgerDispatchCandidateReadAdapter

File: `src/main/java/com/sovereign/connect/integration/scledgerdispatch/ScLedgerDispatchCandidateReadAdapter.java`

```java
package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort;
import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.port.ScOutboxDispatchReadPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ScLedgerDispatchCandidateReadAdapter implements DispatchCandidateReadPort {

    private final ScOutboxDispatchReadPort outboxReadPort;
    private final OutboxEntryDispatchProjector projector;
    private final DispatchStateWritePort dispatchStateWritePort;
    private final int limit;

    public ScLedgerDispatchCandidateReadAdapter(
            ScOutboxDispatchReadPort outboxReadPort,
            OutboxEntryDispatchProjector projector,
            DispatchStateWritePort dispatchStateWritePort,
            int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.outboxReadPort =
            Objects.requireNonNull(outboxReadPort, "outboxReadPort is required");
        this.projector =
            Objects.requireNonNull(projector, "projector is required");
        this.dispatchStateWritePort =
            Objects.requireNonNull(dispatchStateWritePort, "dispatchStateWritePort is required");
        this.limit = limit;
    }

    @Override
    public List<DispatchCandidate> pendingCandidates() {
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (OutboxEntry entry : outboxReadPort.findDispatchableEntries(limit)) {
            projector.project(entry)
                .filter(this::isEmittable)
                .ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    // Read-only check — MUST NOT claim or transition
    private boolean isEmittable(DispatchCandidate candidate) {
        Optional<DispatchAttempt> current =
            dispatchStateWritePort.currentAttempt(candidate.dispatchRecordId());
        if (current.isEmpty()) {
            return true;
        }
        return current.get().state() == DispatchState.RETRY_SCHEDULED;
    }
}
```

---

## 9. Test wiring guide

### 9.1 SQLiteScOutboxDispatchReadPortTest

Use the same SQLite+Flyway pattern from SC-C persistence tests (PerConnectionPragmaDataSource
is available in adapter.persistence tests):

```java
@TempDir Path tempDir;

private DataSource migratedDataSource(String name) {
    SQLiteDataSource delegate = new SQLiteDataSource();
    delegate.setUrl("jdbc:sqlite:" + tempDir.resolve(name));
    DataSource ds = new PerConnectionPragmaDataSource(delegate);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    return ds;
}
```

This test is in `adapter.persistence.sqlite` so it CAN use `PerConnectionPragmaDataSource`.

### 9.2 ScLedgerDispatchBridgeRuntimeTest — exact wiring

This test exercises the full flow. RuntimeDispatchService needs 7 dependencies.
Wire them manually — no Spring context needed:

```java
@TempDir Path tempDir;

private DataSource scCDataSource;
private DataSource scBDataSource;
private JdbcDispatchStateRepository dispatchStateRepo;
private ScLedgerDispatchCandidateReadAdapter candidateAdapter;
private InMemoryScBusPort busPort;
private RuntimeDispatchService dispatchService;

@BeforeEach
void setUp() {
    // SC-C datasource (with PerConnectionPragmaDataSource for WAL support)
    SQLiteDataSource scCDelegate = new SQLiteDataSource();
    scCDelegate.setUrl("jdbc:sqlite:" + tempDir.resolve("sc-c-bridge-test.sqlite"));
    scCDataSource = new PerConnectionPragmaDataSource(scCDelegate);
    Flyway.configure().dataSource(scCDataSource).locations("classpath:db/migration").load().migrate();

    // SC-B datasource (plain SQLiteDataSource per bus test convention)
    SQLiteDataSource scBDelegate = new SQLiteDataSource();
    scBDelegate.setUrl("jdbc:sqlite:" + tempDir.resolve("sc-b-bridge-test.sqlite"));
    scBDataSource = scBDelegate;
    Flyway.configure().dataSource(scBDataSource).locations("classpath:db/migration").load().migrate();

    // SC-C outbox read port
    var outboxReadPort = new SQLiteScLedgerOutboxRepository(new JdbcTemplate(scCDataSource));

    // SC-B persistence
    dispatchStateRepo = new JdbcDispatchStateRepository(scBDataSource);

    // Bridge
    var factory = new DispatchRecordIdFactory();
    var mapper  = new DeliveryLaneToScBusLaneMapper();
    var projector = new OutboxEntryDispatchProjector(factory, mapper);
    candidateAdapter = new ScLedgerDispatchCandidateReadAdapter(
        outboxReadPort, projector, dispatchStateRepo, 10);

    // Bus
    busPort = new InMemoryScBusPort();

    // Service (7 deps in order)
    dispatchService = new RuntimeDispatchService(
        busPort,
        candidateAdapter,
        dispatchStateRepo,
        new InMemoryDispatchObservationRepository(),
        new EnvelopeValidationService(),
        new RoutingKeyValidationService(),
        new CorrelationValidationService()
    );
}
```

To insert an outbox row for tests, use `SQLiteScOutboxWritePort` directly or
construct an `OutboxEntry` and insert via `ScOutboxWritePort` if available,
or insert raw SQL via `JdbcTemplate` — same approach as SQLiteTopologyPersistenceTest.

### 9.3 OutboxEntryDispatchProjectorTest and ScLedgerDispatchCandidateReadAdapterTest

These are pure unit tests — no datasource needed. Use `new DispatchRecordIdFactory()`,
`new DeliveryLaneToScBusLaneMapper()`, mock `DispatchStateWritePort` with
`Mockito.mock(DispatchStateWritePort.class)`, and build `OutboxEntry` records
directly using the constructor.

---

## 10. Test method names — exact (acceptance-map references these)

### SQLiteScOutboxDispatchReadPortTest (5 tests)
```text
findDispatchableEntriesReturnsOnlyPendingRows
findDispatchableEntriesRejectsNonPositiveLimit
findDispatchableEntriesAppliesLimitAndStableOrdering
findDispatchableEntriesMapsAllOutboxEntryFields
findDispatchableEntriesDoesNotMutateOutboxStatusOrUpdatedAt
```

### OutboxEntryDispatchProjectorTest (9 tests)
```text
outboxEntryIdMapsToSourceRecordId
dispatchRecordIdIsDeterministicForSameOutboxEntry
dispatchRecordIdIsDistinctFromRawOutboxEntryId
partitionKeyAndLogicalTopicArePreserved
commandEventAndResponseLanesMapDirectly
signalNeverMapsToCommand
timerFiredSignalOnScopedTopicMapsToEvent
nonScopedSignalIsSkipped
semanticPayloadJsonIsNotParsedIntoDomainCommand
```

### ScLedgerDispatchCandidateReadAdapterTest (10 tests)
```text
emitsCandidateWhenNoDispatchRecordExists
emitsCandidateWhenDispatchStateIsRetryScheduled
skipsCandidateWhenDispatchStateIsClaimed
skipsCandidateWhenDispatchStateIsDispatching
skipsCandidateWhenDispatchStateIsDispatched
skipsCandidateWhenDispatchStateIsDeliveryFailed
skipsCandidateWhenDispatchStateIsExhausted
skipsCandidateWhenDispatchStateIsCancelledBySupersede
pendingCandidatesDoesNotClaimOrTransitionDispatchState
repeatedReadsAfterDispatchedDoNotReemitSameCandidate
```

### ScLedgerDispatchBridgeRuntimeTest (5 tests)
```text
pendingEventOutboxRowDispatchesThroughRuntimeDispatchServiceToEventHandler
timerFiredSignalDispatchesThroughRuntimeDispatchServiceToEventHandler
commandOutboxRowDispatchesToCommandLaneWithoutScdCommand
dispatchDoesNotMutateOutboxStatus
dispatchStatePersistenceRecordsTechnicalDispatchForDerivedRecordId
```

### ScBusOutboxBridgeArchitectureTest (8 tests)
```text
busDoesNotImportCoreScledger
coreDoesNotImportBusRuntime
integrationScledgerdispatchIsOnlyPackageImportingBothScledgerAndBus
integrationScledgerdispatchDoesNotImportTopologyTemporalOrAdapters
noBrokerDependencyIntroduced
noProductionScdCommandIntroduced
dispatchObservationPersistenceStillDeferred
h2DoesNotAddScBMigrations
```

Expected test delta: +37 tests (5+9+10+5+8).
Expected total: ≥ 354 tests.

---

## 11. Stop conditions

```text
STOP-1: mvn -q compile → BUILD SUCCESS
  Verify: no core.** import from bus.**; no bus.runtime.** import from core.**
  Verify: attempt_count is absent from the SELECT in findDispatchableEntries

STOP-2: mvn -q test -Dtest="SQLiteScOutboxDispatchReadPortTest" → 5 tests, 0 failures
STOP-3: mvn -q test -Dtest="OutboxEntryDispatchProjectorTest,ScLedgerDispatchCandidateReadAdapterTest" → 19 tests, 0 failures
STOP-4: mvn -q test -Dtest="ScLedgerDispatchBridgeRuntimeTest,ScBusOutboxBridgeArchitectureTest,ScBusHardeningArchitectureTest,ScBusArchitectureTest" → all pass
STOP-5: mvn -q test → ≥ 354 tests, 0 failures, 0 errors, 0 skipped
  SC-C baseline: 240 tests still green
```

---

## 12. Negative scope

```text
No attempt_count in SELECT
No OutboxEntryStatus mutation
No semanticPayloadJson parsing
No ScdCommand production class
No NATS/JetStream/broker
No new SC-B migration
No DispatchStateWritePort interface change
No new core.** that imports bus.**
No new bus.** that imports core.**
SIGNAL MUST NOT map to COMMAND
```
