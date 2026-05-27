# Implementation Report Patch Template — MU-022 Failure-Boundary Patch

```text
Patch ID:      PATCH-SOV-SC-EIB-MIR-022-FAILURE-BOUNDARY-001
Parent MU:     MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Branch:        feat/sc-eib-mir-022-effective-interaction-boundary-seed
Commit:        <fill after commit>
Date:          <fill>
```

## 1. Summary

```text
Routing-fix patch had aligned upstream SC-C routes with MU-021.
This patch normalizes upstream transport/wire failures across EIB API/service layer.
```

## 2. Files changed

```text
<list production files>
<list test files>
<list docs/mir/mir-022 updates>
```

## 3. Behavioral corrections

```text
- EibTemporalAdmissionService catches upstream unavailable for signal create.
- EibTemporalAdmissionService catches upstream unavailable for cancel list+match.
- EibTemporalAdmissionService catches upstream unavailable for cancel submit.
- EibTemporalActProjectionService returns EibResponse status UPSTREAM_UNAVAILABLE.
- EibHabitatController single device/endpoint routes propagate parent non-OK response.
- EibHabitatController diagnostics returns EibResponse on upstream unavailable.
- EibTemporalActController preserves EibResponse<T> wrapper for upstream failure.
```

## 4. Tests

### EIB

```text
Command: cd eib && mvn test
Tests:   <N>
Failures:<N>
Errors:  <N>
Skipped: <N>
```

### SC-C baseline

```text
Command: mvn test
Tests:   <N>
Failures:<N>
Errors:  <N>
Skipped: <N>
```

## 5. Acceptance map

| Patch AC | Status | Evidence |
|---|---|---|
| AC-PATCH-022-FB-001 | PASS/FAIL | |
| AC-PATCH-022-FB-002 | PASS/FAIL | |
| AC-PATCH-022-FB-003 | PASS/FAIL | |
| AC-PATCH-022-FB-004 | PASS/FAIL | |
| AC-PATCH-022-FB-005 | PASS/FAIL | |
| AC-PATCH-022-FB-006 | PASS/FAIL | |
| AC-PATCH-022-FB-007 | PASS/FAIL | |
| AC-PATCH-022-FB-008 | PASS/FAIL | |
| AC-PATCH-022-FB-009 | PASS/FAIL | |
| AC-PATCH-022-FB-010 | PASS/FAIL | |
| AC-PATCH-022-FB-011 | PASS/FAIL | |
| AC-PATCH-022-FB-012 | PASS/FAIL | |
| AC-PATCH-022-FB-013 | PASS/FAIL | |
| AC-PATCH-022-FB-014 | PASS/FAIL | |
| AC-PATCH-022-FB-015 | PASS/FAIL | |
| AC-PATCH-022-FB-016 | PASS/FAIL | |
| AC-PATCH-022-FB-017 | PASS/FAIL | |

## 6. Retained debts

```text
DEBT-EIB-014 — EIB upstream timeout property is configured but not yet enforced by RestClient wiring.
<retain previously declared DEBT-EIB and DEBT-HTTP items unless explicitly closed by governance>
```

## 7. Final state

```text
MU-022 status after patch:
  Implementation attempted + routing fix + failure-boundary patch applied.
  Pending re-review for L4.
```
