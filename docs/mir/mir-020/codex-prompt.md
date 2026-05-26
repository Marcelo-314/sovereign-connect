# Codex Prompt — MU-020 Northbound Facade Hardening

You are implementing `MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001` on branch:

```text
feat/sc-c-mir-020-northbound-facade-hardening
```

Read and obey:

```text
docs/mir/mir-020/MIR-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001.md
docs/mir/mir-020/code-surface-audit.md
docs/mir/mir-020/context.md
docs/mir/mir-020/acceptance-map.md
```

## Task

Harden the existing MU-019 in-process SC-C Northbound Facade. Do not implement EIB, View Composer, external exposure, SC-B runtime, SC-D invocation, discovery execution, provider command dispatch, or product UX.

## Required code changes

1. Add `VALIDATION_ERROR` to `ScNorthboundStatus`.
2. Add `String source` to `ScNorthboundError` and `ScNorthboundWarning`.
3. Update all existing error/warning constructor call sites using the exact replacements from `context.md`.
4. Add `validationError(String code, String message)` factory to `ScNorthboundResponse`.
5. Make `VALIDATION_ERROR` reachable. Required path:
   `createSignalTemporalAct(...)` with `dueAt` in the past must return `ScNorthboundStatus.VALIDATION_ERROR`.
6. Harden `getDeviceHealth(...)` to use `DERIVED_FROM_ENDPOINTS`:
   `queryService.findDevice(...)`, `deviceSnapshot.device().endpointIds()`, and `queryService.findEndpointHealth(...)` only.
7. Keep `getEndpointRuntimeState(...)` as `UNSUPPORTED_PROFILE`.
8. Keep `getRecoveryStatus(...)` as `UNSUPPORTED_PROFILE`.
9. Add `getNorthboundDiagnostics(String habitatId)` to `ScCoreNorthboundFacade` and `DefaultScCoreNorthboundFacade`.
10. Add `NorthboundDiagnosticsView` and `NorthboundMigrationReadinessView` in the runtime package.
11. Harden `NorthboundDeviceHealthView` to include source, endpointCount, readAt and warnings.
12. Preserve the existing `validateCreateRequest(String habitatId, NorthboundCreateSignalTemporalActRequest request)` shape and existing invalid-request guards; add only the dueAt-in-the-past `VALIDATION_ERROR` branch.
13. Update mapper/helper code as needed without leaking persistence adapters or external transport concerns.

## Required diagnostics behavior

`getNorthboundDiagnostics(habitatId)` must return:

```text
topologyVersion      -> queryService.findCurrentTopologyVersion(habitatId).map(value).orElse("UNKNOWN")
temporalEngineStatus -> TemporalEngineHealth mapped through existing NorthboundTemporalRuntimeStatusView
migrationReadiness   -> UNKNOWN_PENDING_NORMALIZATION, source migration.readiness
readAt               -> Instant.now(clock)
warnings             -> warning explaining migration readiness is deferred
```

Do not infer migration readiness from Flyway bean presence, Spring context startup, or repository availability.

## DeviceHealth derivation rule

Use only canonical topology and durable endpoint health lookup.

```text
all endpoint health values HEALTHY -> device HEALTHY
any endpoint OFFLINE -> device OFFLINE
any endpoint DEGRADED, UNKNOWN or missing -> device DEGRADED
empty endpoint set -> UNKNOWN_PENDING_NORMALIZATION
```

Do not use:

```text
DeviceNode.health()
EndpointNode.health()
EndpointSnapshot.health() fallback
provider-native health
service-local mutable cache
topology_json
```

## Tests to add/update

At minimum:

```text
NorthboundFacadeBehavioralTest:
  - deviceHealthDerivedHealthyWhenAllEndpointsHealthy
  - deviceHealthDerivedOfflineWhenAnyEndpointOffline
  - deviceHealthDerivedDegradedWhenEndpointDegradedUnknownOrMissing
  - deviceHealthEmptyEndpointSetReturnsUnknownPendingNormalization
  - diagnosticsContainsMandatoryFields
  - createSignalTemporalActWithPastDueAtReturnsValidationError
  - errorAndWarningSourcesArePopulated

NorthboundFacadeSpringContextTest:
  - getNorthboundDiagnosticsIsAccessibleThroughFacadeBean

NorthboundFacadeArchitectureTest / NegativeBoundaryTest:
  - keep forbidden imports/annotations absent
  - keep no topology_json/persistence-adapter usage in northbound
  - keep no discovery methods
  - keep no EIB/View Composer/external exposure implementation
```

Use existing testing style and Mockito patterns. Do not add SQLite/Flyway fixtures to Northbound behavioral tests unless strictly necessary; mocked `CoreSnapshotQueryService` evidence is sufficient for this MU.

## Forbidden changes

Do not modify unless impossible and justified in the implementation report:

```text
Flyway migrations
SQLite schema
TemporalEngineConfiguration
TopologyPersistenceConfiguration
SC-B packages
SC-D packages
external transport packages
EIB / View Composer packages
```

Do not add HTTP/SSE/gRPC/ConnectRPC/MCP/GraphQL/WebSocket/NATS/JetStream imports or annotations.

## Validation

Run:

```bash
mvn test
```

Update:

```text
docs/mir/mir-020/implementation-report.md
```

The report must include:

```text
commit hash
test summary
files changed
source-field migration evidence
VALIDATION_ERROR reachability evidence
DeviceHealth derivation evidence
diagnostics evidence
retained debts
boundary-scan evidence
```

Suggested commit:

```text
feat(sc-c): harden northbound facade
```
