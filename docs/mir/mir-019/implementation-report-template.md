# implementation-report.md — MIR-019 Northbound Facade Seed

Document ID: IR-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Version: v0.1.1-template
Status: Draft / to be completed after implementation
Date: YYYY-MM-DD
MU: MU-SOV-SC-C-NORTHBOUND-FACADE-SEED-001
Slot: MU-019
Branch: TBD
Commit: TBD

---

## 0. Implementation summary

```text
Summary:
TBD

Implementation scope:
Profile A — SC-C Local Observation
Profile B — selected Signal TemporalAct local runtime requests
In-process canonical facade only
```

---

## 1. Final file list

### 1.1 Main files added

```text
TBD
```

### 1.2 Test files added

```text
TBD
```

### 1.3 Existing files modified

```text
TBD
```

If existing files were modified, explain why each change was required and why it does not broaden MIR scope.

---

## 2. Package trees

### 2.1 Main northbound package

```text
src/main/java/com/sovereign/connect/core/northbound/
TBD
```

### 2.2 Test northbound package

```text
src/test/java/com/sovereign/connect/core/northbound/
TBD
```

---

## 3. Facade method summary

| Method | Implemented | Status behavior | Notes |
|---|---:|---|---|
| getTopologySnapshot | TBD | TBD | TBD |
| getTopologyVersion | TBD | TBD | TBD |
| getDevice | TBD | TBD | TBD |
| getEndpoint | TBD | TBD | TBD |
| listRooms | TBD | TBD | TBD |
| listZones | TBD | TBD | TBD |
| listDevices | TBD | TBD | TBD |
| listEndpoints | TBD | TBD | TBD |
| listDevicesLocatedIn | TBD | TBD | TBD |
| listEndpointsLocatedIn | TBD | TBD | TBD |
| getEndpointHealth | TBD | TBD | TBD |
| getDeviceHealth | TBD | UNKNOWN_PENDING_NORMALIZATION | TBD |
| getDeviceRuntimeState | TBD | TBD | TBD |
| getEndpointRuntimeState | TBD | UNSUPPORTED_PROFILE | TBD |
| getRecoveryStatus | TBD | proxy / UNSUPPORTED_PROFILE | TBD |
| getTemporalRuntimeStatus | TBD | TBD | TBD |
| createSignalTemporalAct | TBD | TBD | TBD |
| cancelTemporalAct | TBD | TBD | TBD |
| getTemporalAct | TBD | TBD | TBD |
| listTemporalActs | TBD | TBD | TBD |

---

## 4. Delegation map

| Facade method | Actual delegate | Result |
|---|---|---|
| getTopologySnapshot | TBD | TBD |
| getTopologyVersion | TBD | TBD |
| getDevice | TBD | TBD |
| getEndpoint | TBD | TBD |
| listRooms | TBD | TBD |
| listZones | TBD | TBD |
| listDevices | TBD | TBD |
| listEndpoints | TBD | TBD |
| listDevicesLocatedIn | TBD | TBD |
| listEndpointsLocatedIn | TBD | TBD |
| getEndpointHealth | TBD | TBD |
| getDeviceHealth | none / fixed status | UNKNOWN_PENDING_NORMALIZATION |
| getDeviceRuntimeState | TBD | TBD |
| getEndpointRuntimeState | none unless durable port exists | UNSUPPORTED_PROFILE |
| getTemporalRuntimeStatus | TBD | TBD |
| getRecoveryStatus | TBD | TBD |
| createSignalTemporalAct | TBD | TBD |
| cancelTemporalAct | TBD | TBD |
| getTemporalAct | TBD | TBD |
| listTemporalActs | TBD | TBD |

---

## 5. Status mapping table

| Source result / condition | ScNorthboundStatus | Notes |
|---|---|---|
| Successful observation | OK | TBD |
| Missing topology/entity | NOT_FOUND | TBD |
| Invalid request | INVALID_REQUEST | TBD |
| Invalid canonical ID | INVALID_CANONICAL_ID | TBD |
| Unsupported seed profile | UNSUPPORTED_PROFILE | TBD |
| Device health | UNKNOWN_PENDING_NORMALIZATION | Required |
| Temporal create accepted | CREATED / ACCEPTED | TBD |
| Temporal create idempotent replay | OK / ACCEPTED + warning | TBD |
| Temporal create rejected | INVALID_REQUEST | TBD |
| Temporal create failed | INTERNAL_ERROR | TBD |
| Temporal cancel cancelled | CANCELLED | TBD |
| Temporal cancel already terminal | OK + warning | TBD |
| Temporal cancel not found | NOT_FOUND | TBD |
| Temporal cancel rejected | INVALID_REQUEST | TBD |
| Temporal cancel failed | INTERNAL_ERROR | TBD |

---

## 6. Required strategy confirmations

### 6.1 DeviceHealth strategy

```text
Expected: getDeviceHealth returns UNKNOWN_PENDING_NORMALIZATION.
Actual: TBD
```

### 6.2 EndpointHealth authority

```text
Expected: getEndpointHealth delegates to CoreSnapshotQueryService.findEndpointHealth(...).
Actual: TBD
```

### 6.3 TemporalAct filter semantics

```text
Expected: NorthboundTemporalActFilter supports ACTIVE, TERMINAL and MISFIRED only; null/default maps to ACTIVE; ALL_SUPPORTED/combined listing remains deferred.
Actual: TBD
```

### 6.4 Spring wiring

```text
Expected: DefaultScCoreNorthboundFacade registered as @Service unless @Bean is justified.
Actual: TBD
Justification if @Bean used: TBD
```

---

## 7. Negative boundary scan summary

```text
No HTTP/Spring Web imports: TBD
No MCP imports/classes: TBD
No gRPC/ConnectRPC imports/classes: TBD
No WebSocket imports/classes: TBD
No GraphQL imports/classes: TBD
No NATS/JetStream imports/classes: TBD
No SC-D adapter imports/classes: TBD
No persistence adapter imports in northbound package: TBD
No JdbcTemplate/DataSource in northbound package: TBD
No discovery methods in ScCoreNorthboundFacade: TBD
No Effective/Projected/Surface/Session/Policy/Authority DTO naming: TBD
```

---

## 8. Test execution

Command:

```bash
mvn test
```

Result:

```text
TBD
```

Surefire summary:

```text
Tests run: TBD
Failures: TBD
Errors: TBD
Skipped: TBD
```

---

## 9. Acceptance criteria result table

| AC | Result | Evidence |
|---|---|---|
| AC-019-001 | TBD | TBD |
| AC-019-002 | TBD | TBD |
| AC-019-003 | TBD | TBD |
| AC-019-004 | TBD | TBD |
| AC-019-005 | TBD | TBD |
| AC-019-006 | TBD | TBD |
| AC-019-007 | TBD | TBD |
| AC-019-008 | TBD | TBD |
| AC-019-009 | TBD | TBD |
| AC-019-010 | TBD | TBD |
| AC-019-011 | TBD | TBD |
| AC-019-012 | TBD | TBD |
| AC-019-013 | TBD | TBD |
| AC-019-014 | TBD | TBD |
| AC-019-015 | TBD | TBD |
| AC-019-016 | TBD | TBD |
| AC-019-017 | TBD | TBD |
| AC-019-018 | TBD | TBD |
| AC-019-019 | TBD | TBD |
| AC-019-020 | TBD | TBD |
| AC-019-021 | TBD | TBD |
| AC-019-022 | TBD | TBD |
| AC-019-023 | TBD | TBD |
| AC-019-024 | TBD | TBD |
| AC-019-025 | TBD | TBD |
| AC-019-026 | TBD | TBD |
| AC-019-027 | TBD | TBD |
| AC-019-028 | TBD | TBD |
| AC-019-029 | TBD | TBD |
| AC-019-030 | TBD | TBD |
| AC-019-031 | TBD | TBD |
| AC-019-032 | TBD | TBD |
| AC-019-033 | TBD | TBD |

---

## 9.1 T-3 test rename traceability

The implementation report MUST record whether the representative T-3 test was implemented with the corrected method name:

```text
createSignalTemporalActMapsAllResultVariantsCorrectly
```

This replaces the previous package-draft name:

```text
createSignalTemporalActMapsCancelledVariantsCorrectly
```

Rationale: T-3 verifies the four `CreateSignalTemporalActResult` variants, not cancel variants. The body is correct; the rename preserves traceability with TemporalAct result-mapping coverage.

---

## 10. Retained open items required by AC-019-028

The implementation report MUST explicitly retain these open items:

```text
1. getEndpointRuntimeState -> UNSUPPORTED_PROFILE unless a durable endpoint runtime-state port exists.
2. getRecoveryStatus -> proxy/limited status or UNSUPPORTED_PROFILE until an explicit recovery read model exists.
3. DeviceHealth normalized authority / derivation debt remains open.
```

Actual retained-open-items statement:

```text
TBD
```

---

## 11. Deviations

```text
TBD
```

Every deviation must state:

```text
- What changed
- Why it was necessary
- Which MIR/CSA/SDD rule it affects
- Why the deviation does not broaden MU-019 scope
- Whether follow-up corpus patch is required
```

---

## 12. Final implementation verdict

```text
Candidate verdict:
TBD

Recommended MU status:
TBD
```
