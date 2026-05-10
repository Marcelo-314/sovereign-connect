# Codex Prompt — MIR-006 — SC-C Core Snapshot Query Seed

```text
MIR: MIR-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-CORE-SNAPSHOT-QUERY-SEED-001
Execution Status: Authorized
```

---

## Read First

Read:

```text
docs/mir/mir-006/context.md
```

It contains the exact existing API, wiring pattern, query model shapes and recovery test strategy.

Use:

```text
docs/mir/mir-006/acceptance-map.md
```

for AC numbering.

---

## Goal

Add a canonical read-only query boundary over SC-C persisted state.

Three things to validate beyond MU-004:

```text
1. Composition:
   CoreSnapshot integrates topology + version + state + health.

2. Canonical ID lookup:
   findDevice / findEndpoint use canonical IDs and return not-found without provider fallback.

3. Read/write separation:
   query service reads from persistence ports, NOT from BaseTopologyService internal state.
```

---

## What to build

Build on existing MU-001/MU-002/MU-004 code.

Do not rewrite what works.

### 1. Inspect existing code

Identify all types and methods listed in `context.md §2`.

Preserve all 21 existing tests.

### 2. CoreSnapshotReadPort

Add the read port per `context.md §4.1`.

Make `H2BaseTopologyRepository` implement it per `context.md §4.2`.

All required methods already exist on the adapter except possibly:

```text
findTopology(...)
```

Add `findTopology(...)` as an alias for `findByHabitatId(...)` if needed.

Do not modify `BaseTopologyRepository`.

Do not modify `InMemoryBaseTopologyRepository`.

### 3. Query records

Add:

```text
CoreSnapshot
DeviceSnapshot
EndpointSnapshot
```

per `context.md §5`.

Place in:

```text
com.sovereign.connect.core.topology.query
```

### 4. CoreSnapshotQueryService

Create the query service per `context.md §6` with constructor:

```java
public CoreSnapshotQueryService(CoreSnapshotReadPort readPort, Clock clock)
```

Implement the six mandatory query methods following `context.md §7`.

Critical rule:

```text
findDeviceState delegates to readPort.findDeviceState(...),
NOT to BaseTopologyService.findDeviceState(...).
```

### 5. Canonical lookup semantics

`findDevice(habitatId, deviceId)`:

```text
- lookup by canonical deviceId in the persisted topology;
- providerDeviceId is NOT a valid lookup key;
- missing canonical ID returns Optional.empty();
- result includes topologyVersion.
```

`findEndpoint(habitatId, endpointId)`:

```text
- same rules, canonical endpointId only.
```

Capability lookup:

```text
test via findEndpoint(...) -> endpoint.capabilities().
```

No dedicated method is needed.

### 6. Endpoint health source

Endpoint health must be persisted through the same read boundary that `CoreSnapshotQueryService` will use.

Preferred:

```text
repository.saveEndpointHealth(habitatId, endpointId, endpointHealth)
```

Allowed:

```text
service.updateEndpointHealth(...), only if the durable repository persists
endpoint health into the same store used by findEndpointHealth(...), or if
findEndpointHealth(...) reconstructs health from the persisted aggregate.
```

The test MUST prove:

```text
queryService.findEndpointHealth(...) succeeds after repository/service/query recreation.
```

### 7. Recovery test

Implement the full recovery pattern from `context.md §9` as:

```text
CoreSnapshotQuerySeedTest
```

Key phases:

```text
setup -> destroy all instances -> recreate from same H2 file -> query everything -> verify
```

Include:

```text
- not-found for missing canonical IDs;
- not-found for provider IDs used as canonical IDs;
- provider refs remain metadata;
- queryService.findDeviceState(...) reads persisted state;
- queryService.findEndpointHealth(...) reads persisted health.
```

Phase 4:

```text
create a NEW BaseTopologyService only for validateTarget(...).
```

Do not reuse the old service instance.

Do not reuse the old query service.

Do not reuse the old repository.

Do not rely on any in-process map surviving recovery.

The only state that may survive is state persisted through the durable repository/read boundary.

### 8. Boundary assertions

Add tests/assertions showing:

```text
- query does not advance topologyVersion;
- query does not dispatch commands or call SC-D;
- no SC-B / Hub / Projection / Session / Identity / Authority / Policy dependency;
- findCurrentTopologyVersion matches findCurrentSnapshot version;
- validateTarget(...) is reused, not replaced by TargetResolutionSnapshot.
```

Negative satisfaction is acceptable when no such dependencies exist.

---

## What NOT to build

Everything under `Negative Scope` in `context.md §11`.

If any becomes necessary, STOP and report it.

Do not implement:

```text
Projection
Effective View
REST/gRPC/WebSocket API
SC-B transport
MCP facade
historical snapshots
command dispatch
real SC-D adapter calls
provider rediscovery
TargetResolutionSnapshot as required mechanism
```

---

## Build

```bash
mvn test
```

---

## Output

Create or update:

```text
docs/mir/mir-006/implementation-report.md
```

The report must include:

```text
1.  Summary (2-3 sentences)
2.  Files changed
3.  Query types added
4.  Query service/port added
5.  Persistence reads used
6.  Tests added (one-line each)
7.  Acceptance results: AC-001 through AC-025 — pass/fail with evidence
8.  Invariants preserved:
      SC-C owns query semantics — preserved/broken
      query returns Base Topology not Effective View — preserved/broken
      query does not perform Projection — preserved/broken
      topology responses include topologyVersion — preserved/broken
      version consistency (response == snapshot == aggregate) — preserved/broken/not applicable
      canonical lookup uses canonical IDs — preserved/broken
      provider refs remain metadata — preserved/broken
      state query does not advance version — preserved/broken
      health query does not advance version — preserved/broken
      query recovery independent of SC-B/SC-D — preserved/broken
      query does not require Projection/Session/Authority/Policy — preserved/broken
      read/write separation — preserved/broken
      validateTarget reused — preserved/broken
9.  Deviations from scope
10. Failure signals encountered
11. Assumptions made
12. Corpus issues discovered
13. State source statement:
      CoreSnapshotQueryService reads from: persistence/repository ports
      BaseTopologyService.findDeviceState used: no
      Target validation: reused validateTarget(...)
14. Recommended next MU
```

---

## Stop conditions

Stop and report if:

```text
query requires Projection or returns Effective View
query filters by user/session/authority/policy
query requires Hub memory, SC-B replay or SC-D rediscovery
query dispatches command or calls adapters
query advances topologyVersion
topology response lacks topologyVersion
version mismatch between response/snapshot/aggregate when applicable
provider refs become canonical identity
canonical lookup falls back to provider IDs
state/health query forces version advancement
historical snapshot required for seed
query transport must be chosen
query cannot operate after recovery
TargetResolutionSnapshot becomes mandatory
CoreSnapshotQueryService reads from BaseTopologyService.findDeviceState(...)
query correctness depends on retaining same service instance
```
