# acceptance-map.md — MU-016 Patch 003 BLOB ID Binding Closure

Document: acceptance-map.md
Version: v0.2.1
MU: MU-SOV-SC-C-TEMPORAL-ENGINE-INDUSTRIAL-HARDENING-001
Operational slot: MU-016
Patch: patch-003-blob-id-binding
Status: Draft / Acceptance map

## Acceptance criteria

| ID | Criterion | Verification |
|---|---|---|
| P3-001 | SQLite temporal ID codec exists in adapter layer | Source inspection: helper under adapter/persistence/sqlite only |
| P3-002 | `toBlob16` converts UUID String to exactly 16 bytes | Unit test |
| P3-003 | `fromBlob16` converts 16 bytes to canonical UUID String | Unit test |
| P3-004 | Invalid/null/blank/non-UUID IDs are rejected | Unit test |
| P3-005 | `temporal_acts.temporal_act_id` is stored as SQLite BLOB | Direct SQLite query: `typeof = blob` |
| P3-006 | `temporal_acts.temporal_act_id` length is 16 | Direct SQLite query: `length = 16` |
| P3-007 | `temporal_request_idempotency.temporal_act_id` is stored as SQLite BLOB when non-null | Direct SQLite query: `typeof = blob` |
| P3-008 | `temporal_request_idempotency.temporal_act_id` length is 16 when non-null | Direct SQLite query: `length = 16` |
| P3-009 | TemporalAct repository round-trips canonical String IDs | Integration test |
| P3-010 | Idempotency replay still returns canonical String temporalActId | Integration test |
| P3-011 | Schema remains BLOB; no TEXT migration is introduced | Source/migration inspection |
| P3-012 | Domain/application/observation APIs still expose String IDs | Source inspection / compilation |
| P3-013 | Full test suite passes | `mvn test` |
| P3-014 | No unrelated working tree noise is present | `git status --short` review |
| P3-015 | NULL `temporal_request_idempotency.temporal_act_id` remains NULL and round-trips as null | `nullTemporalActIdInIdempotencyRecordStoredAsNull` |

## Required tests

Minimum test names or equivalent:

```text
temporalActIdStoredAsBlob16
temporalRequestIdempotencyTemporalActIdStoredAsBlob16
temporalActIdRoundTripsToCanonicalString
invalidTemporalActIdRejectedBeforePersistence
nullTemporalActIdInIdempotencyRecordStoredAsNull
```

## Stop conditions

Stop and report if:

```text
- schema must be changed to TEXT to make tests pass;
- temporal IDs are not UUID strings in the current implementation;
- conversion would require changing domain/application public contracts;
- existing MU-016 tests fail after the patch;
- unrelated repo noise is present in the working tree.
```
