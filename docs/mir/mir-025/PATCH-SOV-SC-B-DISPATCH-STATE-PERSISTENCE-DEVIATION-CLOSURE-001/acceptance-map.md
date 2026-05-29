# Acceptance Map — PATCH-SOV-SC-B-DISPATCH-STATE-PERSISTENCE-DEVIATION-CLOSURE-001

```text
Version:  v0.1.0
Scope:    MU-025 deviation closure patch
Branch:   feat/sc-b-mir-025-dispatch-state-persistence
Status:   Draft / execution package
```

---

## PATCH-AC-001 — transitionWithEvidence target restriction

Requirement:

```text
transitionWithEvidence(...) rejects any targetState other than CANCELLED_BY_SUPERSEDE.
```

Evidence:

```text
JdbcDispatchStateRepository.transitionWithEvidence(...)
JdbcDispatchStateRepositoryTest.transitionWithEvidenceRejectsNonSupersedeTargetState
```

Expected result: PASS.

---

## PATCH-AC-002 — evidence requirement preserved

Requirement:

```text
transitionWithEvidence(..., CANCELLED_BY_SUPERSEDE, evidenceRef) still rejects null/blank evidenceRef.
```

Evidence:

```text
JdbcDispatchStateRepositoryTest.supersedeCancellationRequiresEvidenceRef
```

Expected result: PASS.

---

## PATCH-AC-003 — valid supersede cancellation preserved

Requirement:

```text
CANCELLED_BY_SUPERSEDE with non-blank evidence remains allowed from PENDING / CLAIMED / RETRY_SCHEDULED.
```

Evidence:

```text
JdbcDispatchStateRepositoryTest.supersedeCancellationPersistsEvidenceRef
```

Expected result: PASS.

---

## PATCH-AC-004 — test naming aligned

Requirement:

```text
JdbcDispatchStateRepositoryTest contains acceptance-map-referenced method names.
```

Evidence:

```text
JdbcDispatchStateRepositoryTest method list
Implementation report
```

Expected result: PASS.

---

## PATCH-AC-005 — repository test count updated

Requirement:

```text
JdbcDispatchStateRepositoryTest runs 14 tests after patch.
```

Evidence:

```text
mvn -q test -Dtest="JdbcDispatchStateRepositoryTest"
Surefire report
Implementation report
```

Expected result: PASS.

---

## PATCH-AC-006 — bus tests do not import adapter infrastructure

Requirement:

```text
src/test/java/com/sovereign/connect/bus/** contains no import/reference to adapter-owned PerConnectionPragmaDataSource.
```

Evidence:

```text
ScBusHardeningArchitectureTest
source inspection
```

Expected result: PASS.

---

## PATCH-AC-007 — H1 scope preserved

Requirement:

```text
Patch does not introduce outbox bridge, ScOutboxDispatchReadPort, integration.scledgerdispatch, observation persistence, lifecycle channel or broker dependency.
```

Evidence:

```text
ScBusHardeningArchitectureTest
source inspection
implementation report
```

Expected result: PASS.

---

## PATCH-AC-008 — no SC-C outbox mutation

Requirement:

```text
Patch does not import/mutate OutboxEntry, OutboxEntryStatus or LedgerEntry.
```

Evidence:

```text
source inspection
architecture test if present
implementation report
```

Expected result: PASS.

---

## PATCH-AC-009 — regression remains green

Requirement:

```text
Full sovereign-connect test suite remains green.
```

Evidence:

```text
mvn -q test
Surefire reports
```

Expected result:

```text
>= 316 tests, 0 failures, 0 errors, 0 skipped
```

---

## PATCH-AC-010 — implementation report updated

Requirement:

```text
docs/mir/mir-025/implementation-report.md records patch closure, test counts and final status recommendation.
```

Evidence:

```text
docs/mir/mir-025/implementation-report.md
```

Expected result: PASS.
