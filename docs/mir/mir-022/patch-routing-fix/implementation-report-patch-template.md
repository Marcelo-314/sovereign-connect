# Implementation Report Patch Template — MU-022 Routing Fix

```text
Document ID:  IMPLEMENTATION-REPORT-PATCH-SOV-SC-EIB-MIR-022-ROUTING-FIX
Version:      v0.1.0-template
Status:       Template
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Branch:       feat/sc-eib-mir-022-effective-interaction-boundary-seed
```

---

## 1. Patch summary

```text
Patch commit: <hash>
Patch title:  fix(eib): align upstream routes with northbound http binding
```

Purpose:

```text
Correct the blocking route mismatches between EIB RestClientEibScNorthboundClient
and the validated SC-C MU-021 HTTP/OpenAPI binding.
```

---

## 2. Resolved blockers

```text
BLOCKER-001 — getTopologySnapshot route fixed:
  from /habitats/{id}/topology/snapshot
  to   /habitats/{id}/topology

BLOCKER-002 — runtime-state route fixed:
  from generic /habitats/{habitatId}/runtime-state/{subjectId}
  to split routes:
       /habitats/{id}/devices/{deviceId}/runtime-state
       /habitats/{id}/endpoints/{endpointId}/runtime-state

BLOCKER-003 — temporal list query params fixed:
  from status / limit
  to   mode / maxResults

BLOCKER-004 — Signal TemporalAct create route fixed:
  from /habitats/{id}/temporal-acts/signal
  to   /habitats/{id}/temporal-acts

BLOCKER-005 — getTopologyVersion implemented:
  route: /habitats/{id}/topology/version
  response: ScEnvelope<NorthboundTopologyVersionViewDto>

BLOCKER-006 — EIB admission routes wrapped:
  public POST /eib/v1/.../temporal-acts/signal returns EibResponse<InteractionAdmissionDecision>
  public POST /eib/v1/.../temporal-acts/{ref}/cancel returns EibResponse<InteractionAdmissionDecision>
```

---

## 3. Files changed

```text
<list files changed>
```

Expected changed areas:

```text
eib/src/main/java/com/sovereign/eib/northbound/**
eib/src/main/java/com/sovereign/eib/api/**
eib/src/test/java/com/sovereign/eib/**
docs/mir/mir-022/implementation-report.md
```

Unexpected changes requiring explanation:

```text
<none / list and explain>
```

---

## 4. Validation commands

```bash
cd eib
mvn compile
mvn test -Dtest=EibScNorthboundClientTest
mvn test -Dtest=EibApiControllerTest,EibTemporalAdmissionServiceTest
mvn test
cd ..
mvn test
```

---

## 5. Validation results

```text
EIB compile:
  PASS / FAIL

EibScNorthboundClientTest:
  Tests: <N>
  Failures: 0
  Errors: 0
  Skipped: 0

EibApiControllerTest + EibTemporalAdmissionServiceTest:
  Tests: <N>
  Failures: 0
  Errors: 0
  Skipped: 0

EIB full suite:
  Tests: <N>
  Failures: 0
  Errors: 0
  Skipped: 0

SC-C baseline:
  Tests: <N>
  Failures: 0
  Errors: 0
  Skipped: 0
```

---

## 6. Route-regression evidence

Record the output of:

```bash
grep -R "topology/snapshot\|runtime-state/\{subjectId\}\|status=\|limit=\|temporal-acts/signal" eib/src/main eib/src/test
```

Expected note:

```text
No forbidden upstream route fragments remain in EIB northbound client production code.
The EIB public API route /eib/v1/.../temporal-acts/signal remains valid and is not an upstream SC-C route.
```

---

## 7. Boundary evidence

Record the output of:

```bash
grep -R "com.sovereign.connect" eib/src/main || true
```

Expected:

```text
No output.
```

---

## 8. Residual debts

No new debt should be introduced by this patch.

Retained debts remain as previously recorded:

```text
DEBT-HTTP-001 — SSE remains SSE-P0 / deferred.
DEBT-HTTP-002 — local-trusted only; no auth/authz.
DEBT-HTTP-003 — Swagger UI / static YAML deferred.
DEBT-HTTP-005 — gRPC/ConnectRPC downstream.
DEBT-HTTP-006 — MCP adapter separate track.
DEBT-HTTP-007 — ACTIVE mode ignores maxResults upstream behavior inherited.
DEBT-HTTP-008 — HTTP TCK absent.
DEBT-EIB-* retained as documented in MIR/CSA.
```

---

## 9. Patch verdict

```text
Patch status: Ready for re-review / Not ready
Reason: <summary>
```
