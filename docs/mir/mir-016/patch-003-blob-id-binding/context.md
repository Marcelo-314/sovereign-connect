# context.md — MU-016 Patch 003 BLOB ID Binding Closure

```text
Document:            context.md
Version:             v0.2.1
MU:                  MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot:    MU-016
Patch:               patch-003-blob-id-binding
Baseline ZIP:        sovereign-connect-016-patch-002-close-mir-016.zip
Baseline tests:      134 / 0 failures
```

---

## 0. How to use this file

This is a focused one-concern patch. Read completely before touching code.
Section 3 shows the exact current binding call sites.
Section 4 gives the codec implementation ready to paste.
Section 5 gives the exact replacement for each call site.
Section 6 gives the test bodies with exact SQL assertions.

---

## 1. Objective

Close H-05: `temporal_acts.temporal_act_id` and
`temporal_request_idempotency.temporal_act_id` are declared `BLOB` in the
Flyway V2 schema but are currently bound as Java `String`. SQLite stores them
as TEXT via dynamic typing. This patch aligns adapter binding with the schema
declaration by introducing UUID ↔ byte[16] conversion in the SQLite adapter
layer only.

---

## 2. Decision

**Option A — adapter-side conversion, schema intact.**

```text
Schema BLOB columns: unchanged.
Adapter layer: UUID String → byte[16] on write; byte[16] → UUID String on read.
Domain/application/observation APIs: String IDs unchanged.
```

Do NOT alter the Flyway migrations. Do NOT change domain types.

---

## 3. Exact current call sites — every place that must change

### 3.1 SQLiteTemporalActRepository — write-path bindings (String → must become byte[16])

**`insertCreated`** — parameter index 2 in the INSERT:

```java
jdbcTemplate.update(
    "INSERT INTO temporal_acts (habitat_id, temporal_act_id, ...) VALUES (?, ?, ...)",
    act.habitatId(),
    act.temporalActId(),   // ← index 2: currently String, must become byte[16]
    act.status().name(),
    ...
);
```

**`cancelIfNonTerminal`** — parameter at `WHERE temporal_act_id = ?`:

```java
jdbcTemplate.update(
    "UPDATE temporal_acts SET ... WHERE temporal_act_id = ? AND habitat_id = ? ...",
    now.toEpochMilli(),   // terminal_at_ms
    now.toEpochMilli(),   // updated_at_ms
    temporalActId,         // ← currently String, must become byte[16]
    habitatId
);
```

**`markFiredIfDueAndNonTerminal`** — parameter at `WHERE temporal_act_id = ?`:

```java
jdbcTemplate.update(
    "UPDATE temporal_acts SET ... WHERE temporal_act_id = ? AND habitat_id = ? ...",
    now.toEpochMilli(),   // fired_at_ms
    now.toEpochMilli(),   // terminal_at_ms
    now.toEpochMilli(),   // updated_at_ms
    temporalActId,         // ← currently String, must become byte[16]
    habitatId,
    now.toEpochMilli()    // due_at_ms <=
);
```

**`markMisfiredIfDueAndNonTerminal`** — parameter at `WHERE temporal_act_id = ?`:

```java
jdbcTemplate.update(
    "UPDATE temporal_acts SET ... WHERE temporal_act_id = ? AND habitat_id = ? ...",
    now.toEpochMilli(),   // terminal_at_ms
    now.toEpochMilli(),   // updated_at_ms
    temporalActId,         // ← currently String, must become byte[16]
    habitatId,
    cutoff.toEpochMilli()
);
```

**`markFailed`** — parameter at `WHERE temporal_act_id = ?`:

```java
jdbcTemplate.update(
    "UPDATE temporal_acts SET ... WHERE temporal_act_id = ? AND habitat_id = ? ...",
    now.toEpochMilli(),   // terminal_at_ms
    now.toEpochMilli(),   // updated_at_ms
    reason,
    temporalActId,         // ← currently String, must become byte[16]
    habitatId
);
```

### 3.2 SQLiteTemporalActRepository — read-path (must read byte[16] and convert back)

**`findById`** — WHERE binding:

```java
jdbcTemplate.query(
    selectBase() + " WHERE habitat_id = ? AND temporal_act_id = ?",
    (rs, rowNum) -> toTemporalAct(rs),
    habitatId,
    temporalActId   // ← currently String, must become byte[16]
);
```

**`toTemporalAct` ResultSet mapping**:

```java
private TemporalAct toTemporalAct(ResultSet rs) throws SQLException {
    return new TemporalAct(
        rs.getString("temporal_act_id"),   // ← currently String; must become fromBlob16(rs.getBytes(...))
        rs.getString("habitat_id"),
        ...
    );
}
```

All other read methods (`listActive`, `findDue`, `findNonTerminalDueBefore`,
`listTerminal`, `listMisfired`) use `toTemporalAct(rs)` — fixing that one method
fixes all of them. No separate changes required for those methods.

### 3.3 SQLiteTemporalRequestIdempotencyRepository — write-path

**`insert`** — `temporal_act_id` parameter:

```java
jdbcTemplate.update(
    "INSERT INTO temporal_request_idempotency (..., temporal_act_id, ...) VALUES ...",
    record.habitatId(),
    record.idempotencyKey(),
    record.requestKind(),
    record.semanticFingerprint(),   // already byte[] — no change
    record.temporalActId(),          // ← currently String (nullable); must become byte[16] or null
    record.resultKind(),
    record.resultCode(),
    record.resultJson(),
    now.toEpochMilli(),
    now.toEpochMilli()
);
```

### 3.4 SQLiteTemporalRequestIdempotencyRepository — read-path

**`toRecord` ResultSet mapping**:

```java
private TemporalRequestIdempotencyRecord toRecord(ResultSet rs) throws SQLException {
    return new TemporalRequestIdempotencyRecord(
        rs.getString("habitat_id"),
        rs.getString("idempotency_key"),
        rs.getString("request_kind"),
        rs.getBytes("semantic_fingerprint"),    // already byte[] — no change
        rs.getString("temporal_act_id"),         // ← must become fromBlob16(rs.getBytes(...)) or null
        rs.getString("result_kind"),
        rs.getString("result_code"),
        rs.getString("result_json")
    );
}
```

**Summary:** 9 binding points total:
- 5 write bindings in `SQLiteTemporalActRepository` (INSERT + 4 UPDATEs)
- 1 WHERE binding in `SQLiteTemporalActRepository.findById`
- 1 ResultSet read in `SQLiteTemporalActRepository.toTemporalAct`
- 1 write binding in `SQLiteTemporalRequestIdempotencyRepository.insert`
- 1 ResultSet read in `SQLiteTemporalRequestIdempotencyRepository.toRecord`

---

## 4. Codec — exact implementation (paste into new file)

File: `src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteCanonicalIdCodec.java`

```java
package com.sovereign.connect.adapter.persistence.sqlite;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Adapter-local codec for canonical temporal ID storage.
 * Converts UUID Strings to 16-byte BLOB representation for SQLite persistence
 * and back to canonical UUID String on read.
 *
 * This class must NOT be used outside the adapter/persistence/sqlite package.
 * Domain, application, and observation layers always work with String IDs.
 */
final class SQLiteCanonicalIdCodec {

    private SQLiteCanonicalIdCodec() {}

    /**
     * Converts a canonical UUID String to a 16-byte BLOB array.
     *
     * @throws IllegalArgumentException if value is null, blank, or not a valid UUID
     */
    static byte[] toBlob16(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canonical ID must not be null or blank");
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("canonical ID is not a valid UUID: " + value, ex);
        }
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * Converts a 16-byte BLOB array back to a canonical UUID String.
     *
     * @throws IllegalArgumentException if bytes is null or length is not 16
     */
    static String fromBlob16(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("BLOB bytes must not be null");
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException(
                "BLOB bytes must be exactly 16 bytes; got " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
        return uuid.toString();
    }

    /**
     * Converts a nullable temporal_act_id from the idempotency table.
     * Returns null when bytes is null (row has no associated TemporalAct).
     */
    static String fromBlob16Nullable(byte[] bytes) {
        return bytes == null ? null : fromBlob16(bytes);
    }

    /**
     * Converts a nullable temporal_act_id for the idempotency table insert.
     * Returns null when value is null (row has no associated TemporalAct).
     */
    static byte[] toBlob16Nullable(String value) {
        return value == null ? null : toBlob16(value);
    }
}
```

**Package-private visibility (`final class`, no `public`):** The codec must not
be accessible from outside the adapter package. Domain and application services
must never import it. The `TemporalEngineIndustrialBoundaryTest` already asserts
that domain services have no storage imports — the codec must not appear in their
classpath usage.

---

## 5. Exact replacements — each call site

### 5.1 `SQLiteTemporalActRepository.insertCreated` — index 2

```java
// Before
act.temporalActId(),

// After
SQLiteCanonicalIdCodec.toBlob16(act.temporalActId()),
```

### 5.2 `SQLiteTemporalActRepository.cancelIfNonTerminal` — WHERE binding

```java
// Before (parameter in WHERE temporal_act_id = ?)
temporalActId,

// After
SQLiteCanonicalIdCodec.toBlob16(temporalActId),
```

### 5.3 `SQLiteTemporalActRepository.markFiredIfDueAndNonTerminal` — WHERE binding

```java
// Before
temporalActId,

// After
SQLiteCanonicalIdCodec.toBlob16(temporalActId),
```

### 5.4 `SQLiteTemporalActRepository.markMisfiredIfDueAndNonTerminal` — WHERE binding

```java
// Before
temporalActId,

// After
SQLiteCanonicalIdCodec.toBlob16(temporalActId),
```

### 5.5 `SQLiteTemporalActRepository.markFailed` — WHERE binding

```java
// Before
temporalActId,

// After
SQLiteCanonicalIdCodec.toBlob16(temporalActId),
```

### 5.6 `SQLiteTemporalActRepository.findById` — WHERE binding

```java
// Before
jdbcTemplate.query(selectBase() + " WHERE habitat_id = ? AND temporal_act_id = ?",
    (rs, rowNum) -> toTemporalAct(rs),
    habitatId,
    temporalActId    // ← String

// After
jdbcTemplate.query(selectBase() + " WHERE habitat_id = ? AND temporal_act_id = ?",
    (rs, rowNum) -> toTemporalAct(rs),
    habitatId,
    SQLiteCanonicalIdCodec.toBlob16(temporalActId)    // ← byte[16]
```

### 5.7 `SQLiteTemporalActRepository.toTemporalAct` — ResultSet read

```java
// Before
rs.getString("temporal_act_id"),

// After
SQLiteCanonicalIdCodec.fromBlob16(rs.getBytes("temporal_act_id")),
```

This single change fixes all read methods (listActive, findDue,
findNonTerminalDueBefore, listTerminal, listMisfired) because they all
call `toTemporalAct(rs)`.

### 5.8 `SQLiteTemporalRequestIdempotencyRepository.insert` — temporal_act_id binding

```java
// Before
record.temporalActId(),    // String or null

// After
SQLiteCanonicalIdCodec.toBlob16Nullable(record.temporalActId()),    // byte[16] or null
```

### 5.9 `SQLiteTemporalRequestIdempotencyRepository.toRecord` — ResultSet read

```java
// Before
rs.getString("temporal_act_id"),    // String or null

// After
SQLiteCanonicalIdCodec.fromBlob16Nullable(rs.getBytes("temporal_act_id")),    // String or null
```

---

## 6. Test bodies — exact implementations

Place all four tests in a new class: `SQLiteTemporalIdBindingTest`.
Use the same `migratedDataSource()` helper pattern from `TemporalEngineReviewBlockersTest`:

```java
// Copy this helper into the new test class:
private DataSource migratedDataSource() {
    SQLiteDataSource delegate = new SQLiteDataSource();
    delegate.setUrl("jdbc:sqlite:target/temporal-blob-id-" + UUID.randomUUID() + ".sqlite");
    DataSource dataSource = new PerConnectionPragmaDataSource(delegate);
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    return dataSource;
}
```

---

### T-1: temporalActIdStoredAsBlob16

```java
@Test
void temporalActIdStoredAsBlob16() {
    DataSource ds = migratedDataSource();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    SQLiteTemporalActRepository repo = new SQLiteTemporalActRepository(ds, mapper, Clock.systemUTC(), 30);

    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    TemporalAct act = new TemporalAct(
        id, "habitat-001", TemporalActStatus.PENDING,
        now.plusSeconds(60),
        new SignalTemporalPayload("Wake up", "alarm"),
        "surface:test", new CreatedByRef("creator:test"),
        null, now, now, null, null, null
    );
    repo.insertCreated(act);

    // Direct SQLite assertion — not through repository
    Map<String, Object> row = jdbc.queryForMap(
        "SELECT typeof(temporal_act_id) AS type_of, length(temporal_act_id) AS len " +
        "FROM temporal_acts WHERE habitat_id = 'habitat-001'"
    );
    assertThat(row.get("type_of")).isEqualTo("blob");
    assertThat(((Number) row.get("len")).intValue()).isEqualTo(16);
}
```

---

### T-2: temporalRequestIdempotencyTemporalActIdStoredAsBlob16

```java
@Test
void temporalRequestIdempotencyTemporalActIdStoredAsBlob16() {
    DataSource ds = migratedDataSource();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    SQLiteTemporalRequestIdempotencyRepository repo =
        new SQLiteTemporalRequestIdempotencyRepository(ds);

    String temporalActId = UUID.randomUUID().toString();
    byte[] fingerprint = new byte[32];   // arbitrary non-empty fingerprint

    TemporalRequestIdempotencyRecord record = new TemporalRequestIdempotencyRecord(
        "habitat-001", "idem-key-001", "CREATE_SIGNAL",
        fingerprint,
        temporalActId,   // non-null
        "ACCEPTED", null, "{}"
    );
    repo.insert(record, Instant.now());

    Map<String, Object> row = jdbc.queryForMap(
        "SELECT typeof(temporal_act_id) AS type_of, length(temporal_act_id) AS len " +
        "FROM temporal_request_idempotency WHERE idempotency_key = 'idem-key-001'"
    );
    assertThat(row.get("type_of")).isEqualTo("blob");
    assertThat(((Number) row.get("len")).intValue()).isEqualTo(16);
}
```

---

### T-3: temporalActIdRoundTripsToCanonicalString

```java
@Test
void temporalActIdRoundTripsToCanonicalString() {
    DataSource ds = migratedDataSource();
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    SQLiteTemporalActRepository repo = new SQLiteTemporalActRepository(ds, mapper, Clock.systemUTC(), 30);

    String id = UUID.randomUUID().toString();
    Instant now = Instant.now();
    TemporalAct act = new TemporalAct(
        id, "habitat-001", TemporalActStatus.PENDING,
        now.plusSeconds(60),
        new SignalTemporalPayload("Timer", "signal"),
        "surface:bedroom", new CreatedByRef("hub:main"),
        null, now, now, null, null, null
    );
    repo.insertCreated(act);

    // Read back through repository — must return canonical UUID String
    Optional<TemporalAct> found = repo.findById("habitat-001", id);
    assertThat(found).isPresent();
    assertThat(found.get().temporalActId()).isEqualTo(id);   // exact String equality
}
```

---

### T-4: invalidTemporalActIdRejectedBeforePersistence

```java
@Test
void invalidTemporalActIdRejectedBeforePersistence() {
    DataSource ds = migratedDataSource();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    SQLiteTemporalActRepository repo = new SQLiteTemporalActRepository(ds, mapper, Clock.systemUTC(), 30);

    Instant now = Instant.now();
    // Construct a TemporalAct with a non-UUID temporalActId
    TemporalAct act = new TemporalAct(
        "not-a-uuid",   // ← invalid ID
        "habitat-001", TemporalActStatus.PENDING,
        now.plusSeconds(60),
        new SignalTemporalPayload("Timer", "signal"),
        "surface:test", new CreatedByRef("creator"),
        null, now, now, null, null, null
    );

    // insertCreated must throw before any row is written
    assertThatThrownBy(() -> repo.insertCreated(act))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a valid UUID");

    // No rows must have been written
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM temporal_acts", Integer.class);
    assertThat(count).isZero();
}
```

---

### T-5: nullTemporalActIdInIdempotencyRecordStoredAsNull

```java
@Test
void nullTemporalActIdInIdempotencyRecordStoredAsNull() {
    DataSource ds = migratedDataSource();
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    SQLiteTemporalRequestIdempotencyRepository repo =
        new SQLiteTemporalRequestIdempotencyRepository(ds);

    byte[] fingerprint = new byte[32];
    // temporalActId is null — e.g. for a REJECTED outcome
    TemporalRequestIdempotencyRecord record = new TemporalRequestIdempotencyRecord(
        "habitat-001", "idem-key-rejected", "CREATE_SIGNAL",
        fingerprint,
        null,   // no temporalActId for rejected requests
        "REJECTED", "MISSING_LABEL", null
    );
    repo.insert(record, Instant.now());

    // DB must store NULL, not NPE or empty string
    String stored = jdbc.queryForObject(
        "SELECT temporal_act_id FROM temporal_request_idempotency WHERE idempotency_key = 'idem-key-rejected'",
        String.class
    );
    assertThat(stored).isNull();

    // Read back through repository — must return null, not throw
    Optional<TemporalRequestIdempotencyRecord> found =
        repo.find("habitat-001", "idem-key-rejected", "CREATE_SIGNAL");
    assertThat(found).isPresent();
    assertThat(found.get().temporalActId()).isNull();
}
```

**T-5 is required, not optional.** It directly verifies the `toBlob16Nullable` / `fromBlob16Nullable`
path. Without it, a broken implementation that calls `toBlob16(null)` and throws `NullPointerException`
on `Rejected` records would not be caught by T-1 through T-4.

---

## 7. Preservation requirements

After this patch, all 134 existing tests must still pass.

The following must remain unchanged:

```text
Domain IDs exposed as String in:
  TemporalAct.temporalActId()
  TemporalActObservation.temporalActId()
  CreateSignalTemporalActResult.Accepted.temporalAct().temporalActId()
  TemporalRequestIdempotencyRecord.temporalActId()

No change to:
  Flyway migrations V1, V2
  Schema column types
  Codec visibility (package-private only)
  TemporalEngineLifecycle.stop() semantics
  recovery completion gate
  atomic idempotency orchestration
  unknown payload handling
  heartbeat / lock-loss logic
  H2TemporalActRepository (seed legacy)
  TemporalActSeedTest (26 tests)
```

---

## 8. Stop conditions

Stop and report if:

```text
1. temporal_act_id values in the existing database are not UUID strings
   (e.g. were previously stored as TEXT via dynamic typing and need migration)
   — do NOT attempt data migration; report for architect decision.
2. Adding the codec causes any existing test to fail.
3. SQLiteCanonicalIdCodec is imported from a domain or application package.
4. T-5 cannot be written because TemporalRequestIdempotencyRecord does not
   accept null temporalActId — report without changing the domain model.
```


---

## 9. v0.2.1 review deltas

```text
- Package version unified to v0.2.1.
- T-5 nullTemporalActIdInIdempotencyRecordStoredAsNull remains mandatory.
- Downstream prompt, acceptance map and implementation report must all reference T-5.
- NULL temporal_act_id in idempotency records is an acceptance criterion, not an optional note.
```
