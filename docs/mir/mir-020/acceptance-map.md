# Acceptance Map — MIR-020 Northbound Facade Hardening

```text
Document ID: AM-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Version:     v0.2.0
Status:      Execution package acceptance map
MU:          MU-SOV-SC-C-NORTHBOUND-FACADE-HARDENING-001
Slot:        MU-020
```

## 0. Purpose

This map connects MIR-020 acceptance criteria to implementation evidence and tests.

## 1. Acceptance criteria map

| AC | Requirement | Evidence |
|---|---|---|
| AC-020-001 | Opens MU-020 | MIR file present under `docs/mir/mir-020/` |
| AC-020-002 | Depends on approved PDR/SDD/CSA | MIR metadata and implementation report |
| AC-020-003 | Preserves in-process canonical Northbound | Architecture tests + package diff |
| AC-020-004 | Forbids EIB/View Composer/Projection implementation | Architecture tests + grep/import scan |
| AC-020-005 | Forbids HTTP/SSE/gRPC/ConnectRPC/MCP/GraphQL/WebSocket | Architecture tests + grep/import scan |
| AC-020-006 | Forbids SC-B runtime/NATS/JetStream/SC-D/discovery execution | Architecture tests + grep/import scan |
| AC-020-007 | Adopts DERIVED_FROM_ENDPOINTS for DeviceHealth | Behavioral tests for derived health |
| AC-020-008 | Uses durable endpoint health lookup | Mockito verify `findEndpointHealth(...)`; no fallback usage |
| AC-020-009 | Forbids stale/non-authoritative health sources | Architecture/behavioral tests + implementation review |
| AC-020-010 | EndpointRuntimeState remains UNSUPPORTED_PROFILE | Behavioral test or existing assertion |
| AC-020-011 | RecoveryStatus remains UNSUPPORTED_PROFILE | Behavioral test or existing assertion |
| AC-020-012 | Adds `getNorthboundDiagnostics(String habitatId)` | Interface + implementation + Spring test |
| AC-020-013 | Diagnostics include mandatory fields | Behavioral test |
| AC-020-014 | topologyVersion diagnostics use queryService | Mockito verify or implementation review |
| AC-020-015 | temporalEngineStatus uses TemporalEngineHealth | Behavioral test / mapper evidence |
| AC-020-016 | migrationReadiness is UNKNOWN_PENDING_NORMALIZATION with source | Behavioral test |
| AC-020-017 | Does not infer migration readiness from unsafe authorities | Implementation review + no Flyway/DataSource in northbound |
| AC-020-018 | Adds VALIDATION_ERROR and proves reachability | Test: past `dueAt` returns `VALIDATION_ERROR` |
| AC-020-019 | Adds source to error/warning records | Compile + behavioral assertions |
| AC-020-020 | Copies CSA exact call-site replacements | Code diff + implementation report source migration section |
| AC-020-021 | Keeps `ScNorthboundResponse<T>` distinct from SC-B envelope | Architecture test / no SC-B imports |
| AC-020-022 | Uses dedicated Northbound DTOs | API signatures + architecture test |
| AC-020-023 | Preserves Signal-only TemporalAct operations | Behavioral tests + no ActionTemporalPayload execution |
| AC-020-024 | Forbids command scheduling/cron/product UX timers | Architecture/grep scan |
| AC-020-025 | Tests DeviceHealth HEALTHY/OFFLINE/DEGRADED/empty endpoint set | Behavioral tests |
| AC-020-026 | Tests diagnostics mandatory fields | Behavioral test |
| AC-020-027 | Tests source fields populated | Behavioral test |
| AC-020-028 | Tests forbidden exposure/cross-plane imports | Architecture/negative-boundary tests |
| AC-020-029 | Tests no persistence adapter/topology_json/Flyway/JdbcTemplate/DataSource in northbound | Architecture/grep scan |
| AC-020-030 | Tests Spring bean and diagnostics method access | Spring context test |
| AC-020-031 | Implementation report records retained debts | implementation-report.md |
| AC-020-032 | Fresh test validation evidence | Surefire summary / `mvn test` output |
| AC-020-033 | Execution assets externalized from MIR | docs/mir/mir-020 package structure |
| AC-020-034 | Implementation only after candidate + package acceptance | governance state + branch execution |

## 2. Required behavioral tests

Minimum expected test cases:

```text
deviceHealthDerivedHealthyWhenAllEndpointsHealthy
deviceHealthDerivedOfflineWhenAnyEndpointOffline
deviceHealthDerivedDegradedWhenEndpointDegradedUnknownOrMissing
deviceHealthEmptyEndpointSetReturnsUnknownPendingNormalization
diagnosticsContainsMandatoryFields
createSignalTemporalActWithPastDueAtReturnsValidationError
errorAndWarningSourcesArePopulated
endpointRuntimeStateRemainsUnsupported
recoveryStatusRemainsUnsupported
```

## 3. Required architecture / negative tests

```text
No HTTP / SSE annotations or imports.
No gRPC / ConnectRPC imports.
No MCP imports.
No GraphQL imports.
No WebSocket imports.
No NATS / JetStream imports.
No SC-B runtime dependency.
No SC-D adapter invocation.
No persistence adapter imports in northbound package.
No topology_json usage in northbound package.
No Effective / Projected / Surface / Session / Policy / Authority DTO naming drift.
No discovery methods in ScCoreNorthboundFacade.
```

## 4. Source-field migration checklist

The implementation report must confirm these replacements.

### ScNorthboundError

```text
notFound -> northbound.query
invalidRequest -> northbound.validation
validationError -> northbound.validation
invalidCanonicalId -> northbound.validation
unsupportedProfile -> northbound.unsupported_profile
unknownPendingNormalization -> northbound.pending_normalization
internalError -> northbound.internal
```

### ScNorthboundWarning

```text
IDEMPOTENT_REPLAY / Request already processed -> temporal.application
ALREADY_TERMINAL / Act was already in terminal state -> temporal.application
IDEMPOTENT_REPLAY / Cancel already recorded -> temporal.application
MIGRATION_READINESS_PENDING_NORMALIZATION -> migration.readiness, if warning emitted
```

## 5. Expected retained debt after successful MU-020

```text
DEBT-019-001 — EndpointRuntimeState remains UNSUPPORTED_PROFILE.
DEBT-019-002 — RecoveryStatus remains UNSUPPORTED_PROFILE.
DEBT-019-003 — DeviceHealth normalized authority remains deferred; derived strategy partially closes observation gap.
DEBT-020-001 — Migration readiness read authority absent; diagnostics expose UNKNOWN_PENDING_NORMALIZATION.
DEBT-019-004 — EIB / View Composer implementation absent.
DEBT-019-005 — Outbox dispatcher / SC-B delivery runtime absent.
```
