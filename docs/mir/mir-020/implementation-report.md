# Implementation Report - MIR-020 Northbound Facade Hardening

```text
Document ID: IR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Version:     v1.0.0
Status:      Completed
MU:          MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Slot:        MU-020
```

## 0. Execution Metadata

```text
Branch:          feat/sc-c-mir-020-northbound-facade-hardening
Commit:          a1c9573
Author/executor: Marcelo Peressoni / Codex
Date:            2026-05-25
Baseline:        MIR-019 northbound facade seed present
```

## 1. Summary

```text
Implementation result: completed and validated
Scope completed: northbound status/source hardening, device health endpoint derivation, diagnostics view, validation error path, tests, and report
Scope deviations: none
```

## 2. Files Changed

Main source:

```text
- src/main/java/com/sovereign/connect/core/northbound/DefaultScCoreNorthboundFacade.java
- src/main/java/com/sovereign/connect/core/northbound/ScCoreNorthboundFacade.java
- src/main/java/com/sovereign/connect/core/northbound/ScNorthboundError.java
- src/main/java/com/sovereign/connect/core/northbound/ScNorthboundResponse.java
- src/main/java/com/sovereign/connect/core/northbound/ScNorthboundStatus.java
- src/main/java/com/sovereign/connect/core/northbound/ScNorthboundWarning.java
- src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDeviceHealthView.java
- src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundDiagnosticsView.java
- src/main/java/com/sovereign/connect/core/northbound/runtime/NorthboundMigrationReadinessView.java
```

Tests:

```text
- src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeArchitectureTest.java
- src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeBehavioralTest.java
- src/test/java/com/sovereign/connect/core/northbound/NorthboundFacadeSpringContextTest.java
```

Docs:

```text
- docs/mir/mir-020/implementation-report.md
```

## 3. Test Evidence

Command:

```bash
mvn test
```

Result:

```text
Tests run: 196
Failures: 0
Errors:   0
Skipped:  0
Build:    SUCCESS
```

Relevant suites:

```text
NorthboundFacadeBehavioralTest:       18 run, 0 failures, 0 errors
NorthboundFacadeNegativeBoundaryTest:  2 run, 0 failures, 0 errors
NorthboundFacadeArchitectureTest:      4 run, 0 failures, 0 errors
NorthboundFacadeSpringContextTest:     4 run, 0 failures, 0 errors
```

## 4. AC Pass/Fail Matrix

```text
AC-020-001: PASS
AC-020-002: PASS
AC-020-003: PASS
AC-020-004: PASS
AC-020-005: PASS
AC-020-006: PASS
AC-020-007: PASS
AC-020-008: PASS
AC-020-009: PASS
AC-020-010: PASS
AC-020-011: PASS
AC-020-012: PASS
AC-020-013: PASS
AC-020-014: PASS
AC-020-015: PASS
AC-020-016: PASS
AC-020-017: PASS
AC-020-018: PASS
AC-020-019: PASS
AC-020-020: PASS
AC-020-021: PASS
AC-020-022: PASS
AC-020-023: PASS
AC-020-024: PASS
AC-020-025: PASS
AC-020-026: PASS
AC-020-027: PASS
AC-020-028: PASS
AC-020-029: PASS
AC-020-030: PASS
AC-020-031: PASS
AC-020-032: PASS
AC-020-033: PASS
AC-020-034: PASS
```

## 5. DeviceHealth Derivation Evidence

```text
Algorithm implemented:
  getDeviceHealth resolves the device through queryService.findDevice(...), then derives device health from deviceSnapshot.device().endpointIds() and queryService.findEndpointHealth(...).

Forbidden sources avoided:
  No use of DeviceNode.health(), EndpointNode.health(), EndpointSnapshot.health(), provider health, topology_json, caches, or persistence adapters.

Rules:
  Empty endpoint set -> UNKNOWN_PENDING_NORMALIZATION response and payload.
  Any endpoint OFFLINE -> OFFLINE.
  Any endpoint DEGRADED, UNKNOWN, or missing health row -> DEGRADED.
  All endpoints HEALTHY -> HEALTHY.
  Payload source for derived data -> DERIVED_FROM_ENDPOINTS.

Tests:
  - deviceHealthDerivedHealthyWhenAllEndpointsHealthy
  - deviceHealthDerivedOfflineWhenAnyEndpointOffline
  - deviceHealthDerivedDegradedWhenEndpointDegradedUnknownOrMissing
  - deviceHealthEmptyEndpointSetReturnsUnknownPendingNormalization
```

## 6. Diagnostics Evidence

```text
getNorthboundDiagnostics added to ScCoreNorthboundFacade: yes
NorthboundDiagnosticsView fields: habitatId, topologyVersion, temporalEngineStatus, migrationReadiness, readAt, warnings
topologyVersion authority: queryService.findCurrentTopologyVersion(...).map(TopologyVersion::value).orElse("UNKNOWN")
temporalEngineStatus authority: NorthboundMapper.toTemporalRuntimeStatusView(...)
migrationReadiness status/source/message: UNKNOWN_PENDING_NORMALIZATION / migration.readiness / deferred to future MU
readAt source: Instant.now(clock)
warnings: MIGRATION_READINESS_PENDING_NORMALIZATION with source migration.readiness
Tests: diagnosticsContainsMandatoryFields, getNorthboundDiagnosticsIsAccessibleThroughFacadeBean
```

## 7. VALIDATION_ERROR Reachability Evidence

```text
ScNorthboundStatus.VALIDATION_ERROR added: yes
ScNorthboundResponse.validationError(...) added: yes
Facade path returning VALIDATION_ERROR: createSignalTemporalAct -> validateCreateRequest -> dueAt not in the future
Test name: createSignalTemporalActWithPastDueAtReturnsValidationError
Expected test input: createSignalTemporalAct(... dueAt in the past ...)
Observed status: VALIDATION_ERROR
Observed error source: northbound.validation
```

## 8. Error/Warning Source-Field Migration Evidence

ScNorthboundError:

```text
notFound -> northbound.query: yes
invalidRequest -> northbound.validation: yes
validationError -> northbound.validation: yes
invalidCanonicalId -> northbound.validation: yes
unsupportedProfile -> northbound.unsupported_profile: yes
unknownPendingNormalization -> northbound.pending_normalization: yes
internalError -> northbound.internal: yes
```

ScNorthboundWarning:

```text
IDEMPOTENT_REPLAY / Request already processed -> temporal.application: yes
ALREADY_TERMINAL / Act was already in terminal state -> temporal.application: yes
IDEMPOTENT_REPLAY / Cancel already recorded -> temporal.application: yes
MIGRATION_READINESS_PENDING_NORMALIZATION -> migration.readiness: yes
```

## 9. Boundary Scan Evidence

Evidence:

```text
Method/tool: mvn test, including NorthboundFacadeArchitectureTest and NorthboundFacadeNegativeBoundaryTest.
Additional spot scan: rg over src/main/java/com/sovereign/connect/core/northbound for persistence adapter terms returned no production matches.

No HTTP/SSE: pass
No gRPC/ConnectRPC: pass
No MCP: pass
No GraphQL: pass
No WebSocket: pass
No NATS/JetStream: pass
No SC-B runtime dependency: pass
No SC-D adapter invocation: pass
No persistence adapter imports in northbound: pass
No topology_json usage in northbound: pass
No EIB/View Composer implementation: pass
No discovery methods: pass
```

## 10. Retained Debt

```text
DEBT-019-001 - EndpointRuntimeState remains UNSUPPORTED_PROFILE: retained
DEBT-019-002 - RecoveryStatus remains UNSUPPORTED_PROFILE: retained
DEBT-019-003 - DeviceHealth normalized authority remains deferred: retained, with interim DERIVED_FROM_ENDPOINTS source
DEBT-020-001 - Migration readiness read authority absent: retained, exposed as UNKNOWN_PENDING_NORMALIZATION
DEBT-019-004 - EIB / View Composer implementation absent: retained
DEBT-019-005 - Outbox dispatcher / SC-B delivery runtime absent: retained
```

New debt:

```text
- None
```

## 11. Deviations / Exceptions

```text
- None
```

## 12. Final Disposition

```text
Recommended MU status: Validated L4

Rationale:
  MIR-020 scope was implemented without adding external northbound transport, discovery, SC-B, SC-D, or persistence adapter dependencies. The full Maven test suite passes with the new facade hardening coverage.
```
