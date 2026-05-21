# codex-prompt.md — MU-016 Patch 003 BLOB ID Binding Closure

Document: codex-prompt.md
Version: v0.2.1
MU: MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot: MU-016
Patch: patch-003-blob-id-binding
Status: Draft / Codex prompt

## Task

Close H-05 in MU-016 by aligning temporal SQLite ID binding with the existing BLOB storage contract.

Current issue:

```text
temporal_act_id is declared as BLOB in SQLite/Flyway schema,
but repositories bind Java String values.
SQLite accepts this via dynamic typing, but storage conformance is not strict.
```

Implement adapter-local UUID String <-> byte[16] conversion for temporal act IDs.

## Required implementation

1. Add a SQLite adapter helper, for example:

```text
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteCanonicalIdCodec.java
```

Required API:

```java
static byte[] toBlob16(String canonicalId)
static String fromBlob16(byte[] value)
```

Behavior:

```text
- accepts UUID canonical strings;
- returns exactly 16 bytes;
- rejects null/blank/non-UUID strings;
- rejects null or non-16-byte arrays on read;
- does not leak into domain/application packages.
```

2. Update `SQLiteTemporalActRepository`:

```text
- bind temporal_acts.temporal_act_id as byte[16];
- read temporal_acts.temporal_act_id from bytes and return canonical String;
- update all WHERE clauses involving temporal_act_id to bind byte[16].
```

3. Update `SQLiteTemporalRequestIdempotencyRepository`:

```text
- bind temporal_request_idempotency.temporal_act_id as byte[16] when non-null;
- preserve null semantics;
- read byte[16] back to canonical String;
- do not alter semantic fingerprint semantics.
```

4. Add/extend tests for:

```text
- temporalActIdStoredAsBlob16;
- temporalRequestIdempotencyTemporalActIdStoredAsBlob16;
- temporalActIdRoundTripsToCanonicalString;
- invalidTemporalActIdRejectedBeforePersistence;
- nullTemporalActIdInIdempotencyRecordStoredAsNull.
```


## Null temporalActId requirement

`SQLiteTemporalRequestIdempotencyRepository` MUST preserve `null` for `temporal_request_idempotency.temporal_act_id`.

Implementation requirement:

```java
byte[] toWrite = record.temporalActId() != null
    ? SQLiteCanonicalIdCodec.toBlob16(record.temporalActId())
    : null;

if (toWrite != null) {
    ps.setBytes(idx, toWrite);
} else {
    ps.setNull(idx, java.sql.Types.BLOB);
}

byte[] bytes = rs.getBytes("temporal_act_id");
String temporalActId = bytes != null
    ? SQLiteCanonicalIdCodec.fromBlob16(bytes)
    : null;
```

Do not call `toBlob16(null)`. The test `nullTemporalActIdInIdempotencyRecordStoredAsNull` is mandatory.

Use direct SQLite assertions:

```sql
SELECT typeof(temporal_act_id), length(temporal_act_id) FROM temporal_acts;
SELECT typeof(temporal_act_id), length(temporal_act_id) FROM temporal_request_idempotency;
```

Expected:

```text
typeof = blob
length = 16
```

5. Run full test suite:

```bash
mvn test
```

## Hard constraints

Do not:

```text
- change schema from BLOB to TEXT;
- change public/domain temporal ID types from String;
- modify Temporal Engine lifecycle semantics;
- modify recovery gate semantics;
- modify unknown payload handling;
- modify single-node lock/heartbeat logic;
- add ActionTemporalPayload;
- add ActionRequest;
- add SC-B/NATS/JetStream;
- add outbox dispatcher;
- add View Composer or Effective Access Boundary.
```

## Deliverables

Update implementation report for patch-003 with:

```text
- files changed;
- codec behavior;
- columns converted;
- tests added;
- direct SQLite typeof/length evidence;
- full mvn test result;
- confirmation that no unrelated working tree noise is present.
```
