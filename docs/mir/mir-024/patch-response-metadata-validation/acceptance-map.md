# Acceptance Map — MU-024 Response Metadata Validation Patch

```text
Version:      v0.2.0-candidate
Parent MIR:   MIR-SOV-SC-B-ABSTRACT-BUS-SEED-001 v0.2.0-candidate
Patch Scope:  EnvelopeValidationService response metadata validation
```

---

## Patch acceptance criteria

| ID | Criterion | Evidence |
|---|---|---|
| PATCH-AC-001 | `validateResponse(...)` rejects `responseMetadata == null`. | `rejectsNullResponseMetadata` PASS |
| PATCH-AC-002 | `validateResponse(...)` rejects `responseMetadata.requestMessageId == null`. | `rejectsNullRequestMessageIdInResponseMetadata` PASS |
| PATCH-AC-003 | `validateResponse(...)` rejects `responseMetadata.responseKind == null`. | `rejectsNullResponseKindInResponseMetadata` PASS |
| PATCH-AC-004 | `validateResponse(...)` rejects `responseMetadata.warnings == null`. | `rejectsNullWarningsListInResponseMetadata` PASS |
| PATCH-AC-005 | `validateResponse(...)` accepts empty `List.of()` warnings. | `acceptsEmptyWarningsListInResponseMetadata` PASS |
| PATCH-AC-006 | Existing response lane/family mismatch rule still enforced. | `rejectsCommandEventAndResponseLaneFamilyMismatches` PASS |
| PATCH-AC-007 | Existing command and event validation tests still pass. | All original `EnvelopeValidationServiceTest` methods PASS |
| PATCH-AC-008 | `ScBusArchitectureTest` (7 tests) still passes after patch. | `ScBusArchitectureTest` PASS |
| PATCH-AC-009 | No canonical shape field order or constructor changed. | Git diff: only `EnvelopeValidationService.java` and test file changed |
| PATCH-AC-010 | No production `ScdCommand` or fact-family class introduced. | `ScBusArchitectureTest.productionHasNoScdCommandClass` PASS |
| PATCH-AC-011 | No broker/physical binding dependency introduced. | `ScBusArchitectureTest.busDoesNotImportPhysicalBrokerRuntimeApis` + `pomsDoNotIntroducePhysicalBusBindingDependency` PASS |
| PATCH-AC-012 | `bus.**` does not import `core.*`. | `ScBusArchitectureTest.busRuntimeDoesNotImportCore` PASS |
| PATCH-AC-013 | Full regression: 294 tests, 0 failures, 0 errors. | `mvn -q test` output |
| PATCH-AC-014 | SC-C baseline 240 tests unchanged. | Surefire report confirms non-bus suites green |
| PATCH-AC-015 | Implementation report records patch commit and test evidence. | `docs/mir/mir-024/implementation-report.md` updated |

---

## Test count expectation

```text
Pre-patch:   289 tests (240 SC-C + 49 bus)
Post-patch:  294 tests (240 SC-C + 54 bus)
Delta:       +5 new test methods in EnvelopeValidationServiceTest
```

---

## Parent MIR criteria closed by this patch

```text
AC-024-028..037 — Envelope validation behavior (response metadata now fully covered)
AC-024-051      — All tests pass (294 total)
AC-024-053      — Implementation report records patch evidence and closure
```

---

## Closure

If all 15 patch ACs pass:

```text
MU-SOV-SC-B-ABSTRACT-BUS-SEED-001 — Validated L4
```
