# Acceptance Map Patch — MU-022 Routing Fix

```text
Document ID:  ACCEPTANCE-MAP-PATCH-SOV-SC-EIB-MIR-022-ROUTING-FIX
Version:      v0.1.0
Status:       Patch acceptance map
MU:           MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
```

This file maps the blockers from the merged MU-022 implementation review to concrete patch acceptance checks.

---

## PATCH-AC-001 — topology snapshot route

**Requirement:** `getTopologySnapshot(habitatId)` calls `GET /habitats/{habitatId}/topology`.

**Reject if:** any upstream client production code or test expectation uses `/topology/snapshot`.

**Evidence:** `EibScNorthboundClientTest` exact URL expectation and grep/architecture check.

---

## PATCH-AC-002 — topology version route and DTO

**Requirement:** `getTopologyVersion(habitatId)` calls `GET /habitats/{habitatId}/topology/version` and returns `ScEnvelope<NorthboundTopologyVersionViewDto>`.

**Reject if:** response is treated as a bare string.

**Evidence:** test verifies `payload.value()`.

---

## PATCH-AC-003 — device runtime state route

**Requirement:** `getDeviceRuntimeState(habitatId, deviceId)` calls `GET /habitats/{habitatId}/devices/{deviceId}/runtime-state`.

**Reject if:** EIB uses a generic `/runtime-state/{subjectId}` route.

**Evidence:** exact URL expectation in client test.

---

## PATCH-AC-004 — endpoint runtime state route

**Requirement:** `getEndpointRuntimeState(habitatId, endpointId)` calls `GET /habitats/{habitatId}/endpoints/{endpointId}/runtime-state`.

**Reject if:** EIB uses a generic `/runtime-state/{subjectId}` route.

**Evidence:** exact URL expectation in client test.

---

## PATCH-AC-005 — temporal acts query params

**Requirement:** `listTemporalActs(habitatId, mode, maxResults)` uses query params `mode` and `maxResults`.

**Reject if:** query params `status` or `limit` are used for upstream SC-C calls.

**Evidence:** request matcher verifies `mode=ACTIVE` and `maxResults=50`.

---

## PATCH-AC-006 — Signal TemporalAct create route

**Requirement:** `createSignalTemporalAct(...)` posts to `POST /habitats/{habitatId}/temporal-acts`.

**Reject if:** upstream SC-C client posts to `/temporal-acts/signal`.

**Evidence:** exact URL expectation in client test.

---

## PATCH-AC-007 — cancel TemporalAct route

**Requirement:** `cancelTemporalAct(...)` posts to `POST /habitats/{habitatId}/temporal-acts/{temporalActId}/cancel`.

**Evidence:** exact URL expectation and request body does not include `temporalActId`.

---

## PATCH-AC-008 — semantic 503 preservation

**Requirement:** HTTP 503 with a valid `ScEnvelope(DEFERRED_SC_B_REQUIRED)` body is returned as a semantic SC-C envelope, not thrown as `EibUpstreamUnavailableException`.

**Evidence:** focused client test.

---

## PATCH-AC-009 — transport 503 classification

**Requirement:** HTTP 503 with no valid `ScEnvelope` body is classified as `EibUpstreamUnavailableException`.

**Evidence:** focused client test.

---

## PATCH-AC-010 — malformed JSON classification

**Requirement:** malformed JSON from upstream is classified as `EibUpstreamUnavailableException`.

**Evidence:** focused client test.

---

## PATCH-AC-011 — admission API wrapper

**Requirement:** EIB admission routes return `EibResponse<InteractionAdmissionDecision>` body.

**Reject if:** public EIB routes return bare `InteractionAdmissionDecision`.

**Evidence:** API/controller tests assert root `status`, root `payload`, and nested `payload.status`.

---

## PATCH-AC-012 — architecture boundary preserved

**Requirement:** EIB remains under `/eib`, no imports of `com.sovereign.connect.*` in `eib/src/main/java`.

**Evidence:** existing architecture test plus grep.

---

## PATCH-AC-013 — implementation report updated

**Requirement:** `docs/mir/mir-022/implementation-report.md` records patch commit, resolved blockers, test counts and residual status.

**Evidence:** report section added.

---

## Validation summary required for re-review

```text
EIB compile: PASS
EIB focused client tests: PASS
EIB full suite: PASS
SC-C baseline: PASS
```
