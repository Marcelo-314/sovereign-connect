# Acceptance Map — MU-022 Failure-Boundary Patch

```text
Patch: PATCH-SOV-SC-EIB-MIR-022-FAILURE-BOUNDARY-001
Scope: Normalize upstream transport failure across EIB API/service layer
```

| Patch AC | Required evidence | Expected test / verification |
|---|---|---|
| AC-PATCH-022-FB-001 | Signal admission catches upstream failure | `EibTemporalAdmissionServiceTest` — create throws -> decision `FAILED_UPSTREAM_UNAVAILABLE` |
| AC-PATCH-022-FB-002 | Signal admission trace carries `UPSTREAM_UNAVAILABLE` | Same test verifies `canonicalTrace.scNorthboundStatus` |
| AC-PATCH-022-FB-003 | Signal controller returns `EibResponse<InteractionAdmissionDecision>` on upstream failure | `EibApiControllerTest` or controller-specific test |
| AC-PATCH-022-FB-004 | Cancel list+match upstream failure does not become `REJECTED_NOT_VISIBLE` | `EibTemporalAdmissionServiceTest` — list throws |
| AC-PATCH-022-FB-005 | Cancel submit upstream failure does not escape | `EibTemporalAdmissionServiceTest` — cancel throws |
| AC-PATCH-022-FB-006 | Temporal list projection returns `UPSTREAM_UNAVAILABLE` | `EibTemporalActProjectionServiceTest` |
| AC-PATCH-022-FB-007 | Temporal get projection returns `UPSTREAM_UNAVAILABLE` | `EibTemporalActProjectionServiceTest` |
| AC-PATCH-022-FB-008 | Single device route propagates parent `UPSTREAM_UNAVAILABLE` | `EibHabitatControllerFailureBoundaryTest` |
| AC-PATCH-022-FB-009 | Single endpoint route propagates parent `UPSTREAM_UNAVAILABLE` | `EibHabitatControllerFailureBoundaryTest` |
| AC-PATCH-022-FB-010 | Diagnostics route returns `UPSTREAM_UNAVAILABLE` on upstream failure | `EibHabitatControllerFailureBoundaryTest` or API test |
| AC-PATCH-022-FB-011 | Routing-fix tests still use MU-021 routes | `EibScNorthboundClientTest` expectations |
| AC-PATCH-022-FB-012 | HTTP 503 + valid `ScEnvelope(DEFERRED_SC_B_REQUIRED)` remains semantic | `EibScNorthboundClientTest` |
| AC-PATCH-022-FB-013 | malformed JSON / invalid envelope maps to upstream unavailable | `EibScNorthboundClientTest` |
| AC-PATCH-022-FB-014 | EIB remains isolated from SC-C imports | `EibArchitectureBoundaryTest` |
| AC-PATCH-022-FB-015 | EIB suite passes | Surefire / Maven output |
| AC-PATCH-022-FB-016 | SC-C baseline suite passes | Surefire / Maven output |
| AC-PATCH-022-FB-017 | implementation report records patch evidence | `docs/mir/mir-022/implementation-report.md` |

## L4 re-review gate

MU-022 may be re-reviewed for L4 only if all rows above are PASS or explicitly justified with equivalent evidence.
