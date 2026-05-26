# Implementation Report — MIR-020 Northbound Facade Hardening

```text
Document ID: IR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Version:     v0.1.0-template
Status:      Template
MU:          MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Slot:        MU-020
```

## 0. Execution metadata

```text
Branch:
Commit:
Author / executor:
Date:
Baseline:
```

## 1. Summary

```text
Implementation result:
Scope completed:
Scope deviations:
```

## 2. Files changed

### Main source

```text
- 
```

### Tests

```text
- 
```

### Docs

```text
- docs/mir/mir-020/implementation-report.md
```

## 3. Test evidence

Command:

```bash
mvn test
```

Result:

```text
Tests run:
Failures:
Errors:
Skipped:
```

Surefire summary / relevant suites:

```text
NorthboundFacadeBehavioralTest:
NorthboundFacadeNegativeBoundaryTest:
NorthboundFacadeArchitectureTest:
NorthboundFacadeSpringContextTest:
```

## 4. AC pass/fail matrix

```text
AC-020-001:
AC-020-002:
AC-020-003:
AC-020-004:
AC-020-005:
AC-020-006:
AC-020-007:
AC-020-008:
AC-020-009:
AC-020-010:
AC-020-011:
AC-020-012:
AC-020-013:
AC-020-014:
AC-020-015:
AC-020-016:
AC-020-017:
AC-020-018:
AC-020-019:
AC-020-020:
AC-020-021:
AC-020-022:
AC-020-023:
AC-020-024:
AC-020-025:
AC-020-026:
AC-020-027:
AC-020-028:
AC-020-029:
AC-020-030:
AC-020-031:
AC-020-032:
AC-020-033:
AC-020-034:
```

## 5. DeviceHealth derivation evidence

Record:

```text
Algorithm implemented:
Forbidden sources avoided:
Tests:
  - all endpoints HEALTHY:
  - any OFFLINE:
  - DEGRADED/UNKNOWN/missing:
  - empty endpoint set:
```

## 6. Diagnostics evidence

Record:

```text
getNorthboundDiagnostics added to ScCoreNorthboundFacade:
NorthboundDiagnosticsView fields:
topologyVersion authority:
temporalEngineStatus authority:
migrationReadiness status/source/message:
readAt source:
warnings:
```

## 7. VALIDATION_ERROR reachability evidence

Required:

```text
ScNorthboundStatus.VALIDATION_ERROR added:
ScNorthboundResponse.validationError(...) added:
Facade path returning VALIDATION_ERROR:
Test name:
Expected test input:
  createSignalTemporalAct(... dueAt in the past ...)
Observed status:
```

## 8. Error/warning source-field migration evidence

Confirm exact replacements.

### ScNorthboundError

```text
notFound -> northbound.query:
invalidRequest -> northbound.validation:
validationError -> northbound.validation:
invalidCanonicalId -> northbound.validation:
unsupportedProfile -> northbound.unsupported_profile:
unknownPendingNormalization -> northbound.pending_normalization:
internalError -> northbound.internal:
```

### ScNorthboundWarning

```text
IDEMPOTENT_REPLAY / Request already processed -> temporal.application:
ALREADY_TERMINAL / Act was already in terminal state -> temporal.application:
IDEMPOTENT_REPLAY / Cancel already recorded -> temporal.application:
MIGRATION_READINESS_PENDING_NORMALIZATION -> migration.readiness, if emitted:
```

## 9. Boundary scan evidence

Record method/tool used and result:

```text
No HTTP/SSE:
No gRPC/ConnectRPC:
No MCP:
No GraphQL:
No WebSocket:
No NATS/JetStream:
No SC-B runtime dependency:
No SC-D adapter invocation:
No persistence adapter imports in northbound:
No topology_json usage in northbound:
No EIB/View Composer implementation:
No discovery methods:
```

## 10. Retained debt

Expected retained debt:

```text
DEBT-019-001 — EndpointRuntimeState remains UNSUPPORTED_PROFILE:
DEBT-019-002 — RecoveryStatus remains UNSUPPORTED_PROFILE:
DEBT-019-003 — DeviceHealth normalized authority remains deferred:
DEBT-020-001 — Migration readiness read authority absent:
DEBT-019-004 — EIB / View Composer implementation absent:
DEBT-019-005 — Outbox dispatcher / SC-B delivery runtime absent:
```

New debt, if any:

```text
- 
```

## 11. Deviations / exceptions

```text
- 
```

## 12. Final disposition

```text
Recommended MU status:
  Validated L4 / Not validated / Requires patch

Rationale:
```
