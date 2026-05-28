# MU-023 Acceptance Map — EIB Hardening

```text
MIR:     MIR-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001 v0.2.0-candidate
MU:      MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-HARDENING-001
Slot:    MU-023
Status:  Candidate acceptance map
Total ACs: 54
```

## Mapping summary

| AC | Evidence expected |
|---|---|
| AC-023-001 | Architecture/boundary tests and unchanged `/eib` runtime placement |
| AC-023-002 | Git diff: no SC-C production code changes |
| AC-023-003 | Architecture test: no SC-C internals imported by EIB |
| AC-023-004 | Source inspection/tests: EIB consumes SC-C through HTTP/OpenAPI client only |
| AC-023-005 | `EibConfiguration.eibRestClientBuilder` applies connect timeout factory |
| AC-023-006 | `EibConfiguration.eibRestClientBuilder` applies read timeout factory |
| AC-023-007 | `RestClient.Builder` bean preserved; factory bound before build |
| AC-023-008 | Client-level timeout test -> `EibUpstreamUnavailableException` |
| AC-023-008a | API-level test -> HTTP 503 + `EibResponse.status = UPSTREAM_UNAVAILABLE` |
| AC-023-009 | No generic fallback to HTTP 202 / ACCEPTED remains |
| AC-023-010 | Full mapper or equivalent exhaustive controller logic exists |
| AC-023-011 | Mapper test: ADMITTED -> 202 / ADMITTED |
| AC-023-012 | Mapper test: COMPLETED -> 200 / COMPLETED |
| AC-023-013 | Mapper test: REJECTED_NOT_VISIBLE -> 404 / NOT_VISIBLE |
| AC-023-014 | Mapper test: REJECTED_INVALID_REQUEST -> 400 / INVALID_REQUEST |
| AC-023-015 | Mapper test: DEFERRED_SC_B_REQUIRED -> 503 / DEFERRED |
| AC-023-016 | Mapper test: DEFERRED_PENDING_NORMALIZATION -> 409 / PENDING_NORMALIZATION |
| AC-023-017 | Mapper test: DEFERRED_UNSUPPORTED_PROFILE -> 501 / UNSUPPORTED |
| AC-023-018 | Mapper test: FAILED_UPSTREAM_UNAVAILABLE -> 503 / UPSTREAM_UNAVAILABLE |
| AC-023-019 | Mapper test: FAILED_CANONICAL_SUBMISSION -> 500 / FAILED; include INTERNAL_ERROR input coverage |
| AC-023-020 | Service test/source: SC-C CANCELLED -> EIB COMPLETED |
| AC-023-021 | Regression asserts CANCELLED no longer -> ADMITTED |
| AC-023-022 | Controller/API test: synchronous cancel returns 200 / COMPLETED |
| AC-023-023 | Service test/source: list envelope success checked before ref resolution |
| AC-023-024 | Service test: list DEFERRED_SC_B_REQUIRED propagated, not NOT_VISIBLE |
| AC-023-025 | Service test: list UNKNOWN_PENDING_NORMALIZATION -> DEFERRED_PENDING_NORMALIZATION |
| AC-023-026 | Service test: list UNSUPPORTED_PROFILE -> DEFERRED_UNSUPPORTED_PROFILE |
| AC-023-027 | Service test verifies `cancelTemporalAct` not called on non-success list |
| AC-023-028 | Gating truth table row false,false -> ordinary |
| AC-023-029 | Gating truth table row true,false -> ordinary |
| AC-023-030 | Gating truth table row false,true -> ordinary |
| AC-023-031 | Gating truth table row true,true -> diagnostic/admin |
| AC-023-032 | Mapper/serialization test: provider-native IDs absent in all product views |
| AC-023-033 | Mapper test: canonical IDs absent in ordinary views |
| AC-023-034 | Mapper test: canonical IDs present only in diagnostic/admin mode |
| AC-023-035 | Route test: GET /topology |
| AC-023-036 | Route test: GET /topology/version |
| AC-023-037 | Route test: GET /devices/{deviceId}/health |
| AC-023-038 | Route test: GET /endpoints/{endpointId}/health |
| AC-023-039 | Route test: GET /devices/{deviceId}/runtime-state |
| AC-023-040 | Route test: GET /endpoints/{endpointId}/runtime-state |
| AC-023-041 | Route test: GET /diagnostics |
| AC-023-042 | Route test: GET /temporal-acts?mode=ACTIVE&maxResults=50 |
| AC-023-043 | Route test: GET /temporal-acts/{temporalActId} |
| AC-023-044 | Route test: POST /temporal-acts |
| AC-023-045 | Route test: POST /temporal-acts/{temporalActId}/cancel |
| AC-023-046 | Negative route regression: rejected old routes absent |
| AC-023-047 | Implementation report: final EIB test count/result |
| AC-023-048 | Implementation report: SC-C baseline result or not-rerun rationale |
| AC-023-049 | Implementation report closes DEBT-EIB-014 only if timeout tests pass |
| AC-023-050 | Implementation report closes DEBT-EIB-015 only if propagation tests pass |
| AC-023-051 | Implementation report retained debt table |
| AC-023-052 | Implementation report commit/evidence refs |
| AC-023-053 | This acceptance map is updated with actual evidence after implementation |
```

## Minimum expected tests

```text
Baseline EIB tests: 33
Expected EIB tests after hardening: >= 56
Expected failures/errors/skips: 0/0/0
```
