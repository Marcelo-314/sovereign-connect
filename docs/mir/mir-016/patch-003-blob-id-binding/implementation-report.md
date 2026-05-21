# implementation-report.md - MU-016 Patch 003 BLOB ID Binding Closure

Document: implementation-report.md
Version: v0.2.1
MU: MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot: MU-016
Patch: patch-003-blob-id-binding
Status: PASS

## 1. Execution metadata

```text
Branch: main
Baseline commit: current workspace baseline before patch-003 changes
Implementation commit: not committed
Executor: Codex
Date: 2026-05-21
```

## 2. Summary

```text
Objective:
  Close H-05 by aligning temporal SQLite ID binding with BLOB(16) schema.

Result:
  PASS
```

Patch-003 keeps the public/domain temporal act ID contract as canonical UUID String, while binding SQLite persistence columns as 16-byte UUID BLOBs inside the adapter.

## 3. Files changed

Production:

```text
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteCanonicalIdCodec.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteTemporalActRepository.java
src/main/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteTemporalRequestIdempotencyRepository.java
```

Tests:

```text
src/test/java/com/sovereign/connect/adapter/persistence/sqlite/SQLiteTemporalIdBindingTest.java
src/test/java/com/sovereign/connect/core/temporal/TemporalEngineReviewBlockersTest.java
```

## 4. Codec implementation

```text
Codec class:
  com.sovereign.connect.adapter.persistence.sqlite.SQLiteCanonicalIdCodec

Visibility:
  package-private final class, adapter-local.

Methods:
  static byte[] toBlob16(String canonicalId)
  static String fromBlob16(byte[] value)
  static byte[] toBlob16Nullable(String canonicalId)
  static String fromBlob16Nullable(byte[] value)

Invalid input behavior:
  Rejects null canonical IDs.
  Rejects blank canonical IDs.
  Rejects non-UUID canonical IDs.
  Rejects null non-nullable BLOB values.
  Rejects BLOB values whose length is not exactly 16 bytes.

Byte order:
  UUID most significant bits followed by UUID least significant bits, using ByteBuffer long order.

UUID canonicalization behavior:
  Readback returns UUID.toString() canonical lowercase hyphenated UUID text.
```

## 5. Columns converted

```text
temporal_acts.temporal_act_id:
  BLOB byte[16] binding on insert, findById, and status update WHERE clauses.
  Repository readback converts BLOB byte[16] to canonical UUID String.

temporal_request_idempotency.temporal_act_id:
  BLOB byte[16] binding for non-null temporal_act_id.
  SQL NULL BLOB binding preserved for null temporal_act_id.
  Repository readback converts BLOB byte[16] to canonical UUID String or null.
```

## 6. SQLite evidence

Direct SQLite assertions are covered by `SQLiteTemporalIdBindingTest`.

```sql
SELECT typeof(temporal_act_id), length(temporal_act_id) FROM temporal_acts;
```

Observed in `temporalActIdStoredAsBlob16`:

```text
blob | 16
```

```sql
SELECT typeof(temporal_act_id), length(temporal_act_id) FROM temporal_request_idempotency;
```

Observed in `temporalRequestIdempotencyTemporalActIdStoredAsBlob16`:

```text
blob | 16
```

NULL-path evidence for idempotency records without a TemporalAct:

```sql
SELECT temporal_act_id
FROM temporal_request_idempotency
WHERE idempotency_key = 'idem-key-rejected';
```

Observed in `nullTemporalActIdInIdempotencyRecordStoredAsNull`:

```text
NULL
```

Repository readback also returned `TemporalRequestIdempotencyRecord.temporalActId() == null`.

## 7. Tests

```text
mvn test:
Tests run: 139
Failures: 0
Errors: 0
Skipped: 0
Result: BUILD SUCCESS
Total time: 04:56 min
Finished at: 2026-05-21T19:12:01-03:00
```

New tests:

```text
SQLiteTemporalIdBindingTest#temporalActIdStoredAsBlob16
SQLiteTemporalIdBindingTest#temporalRequestIdempotencyTemporalActIdStoredAsBlob16
SQLiteTemporalIdBindingTest#temporalActIdRoundTripsToCanonicalString
SQLiteTemporalIdBindingTest#invalidTemporalActIdRejectedBeforePersistence
SQLiteTemporalIdBindingTest#nullTemporalActIdInIdempotencyRecordStoredAsNull
```

Updated tests:

```text
TemporalEngineReviewBlockersTest direct SQLite fixtures now insert/query temporal_acts.temporal_act_id as byte[16] UUID BLOB values where the test exercises SQLite temporal persistence directly.
```

## 8. Scope preservation

```text
No schema TEXT change: yes
No domain String ID contract change: yes
No Temporal Engine lifecycle change: yes
No recovery gate change: yes
No ActionTemporalPayload: yes
No ActionRequest: yes
No SC-B/NATS/JetStream: yes
No dispatcher: yes
No View Composer: yes
No unrelated working tree noise: patch-003 introduced no unrelated files; existing broader MU-016 workspace noise remains present and was not reverted.
```

## 9. Residual risks

```text
None known for patch-003.
```
