# Context — MIR-011 Persistence Boundary Hardening

```text
Artifact:      context.md
MIR:           MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
MIR version:   v0.1.1-draft, architect-approved
MU:            MU-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001
Slot:          MU-011
Audit:         CSA-MU-011 v0.1.1-merged
Status:        Ready for codex-prompt.md
```

---

## 0. Role of this context

This file is operational context for implementation. It does not replace the MIR or the Code Surface Audit.

Codex must read, in order:

```text
docs/mir/mir-011/MIR-SOV-SC-C-PERSISTENCE-BOUNDARY-HARDENING-001.md
docs/mir/mir-011/code-surface-audit.md
docs/mir/mir-011/acceptance-map.md
docs/mir/mir-011/context.md
```

The prompt must be executed only after MIR and audit approval.

---

## 1. Objective

Close `DEBT-007-001` by separating structural topology persistence from endpoint/device health and state persistence in the current H2/JDBC seed implementation.

The immediate target is the existing coupling in:

```text
H2BaseTopologyRepository.save(HabitatBaseTopology)
```

After MU-011, structural topology save must be structural-only.

---

## 2. Architectural boundary

Preserve the global SC-C boundary:

```text
SC-C owns canonical topology, topologyVersion, materialization, persistence, state/health and query semantics.
SC-B transports and correlates.
SC-D observes, translates and executes.
Projection / Effective View lives outside SC.
```

MU-011 must not introduce dependencies on:

```text
SC-B
SC-D
NATS / JetStream
Vert.x
Projection / Effective View
Hub
Session
Identity
Authority
Policy
Surface UX
MCP facade
```

---

## 3. Code surface

Primary code surface:

```text
com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository
  save(HabitatBaseTopology)
  saveEndpointHealth(...)
  saveDeviceState(...)
  findEndpointHealth(...)
  findDeviceState(...)
  appendMutationRecord(...)
  createSchema()
```

Relevant domain/service surface:

```text
com.sovereign.connect.core.topology.port.BaseTopologyRepository
com.sovereign.connect.core.topology.port.CoreSnapshotReadPort
com.sovereign.connect.core.topology.service.BaseTopologyService
com.sovereign.connect.core.topology.materialization.DefaultTopologyMaterializationService
```

Relevant tests:

```text
BaseTopologyServiceTest
TopologyVersionHardeningTest
PersistenceMemorySeedTest
CoreSnapshotQuerySeedTest
ScCoreKernelHardeningTest
TopologyMaterializationSeedTest
```

---

## 4. Root cause

`H2BaseTopologyRepository.save(HabitatBaseTopology)` currently has mixed responsibility:

```text
1. structural topology snapshot write;
2. endpoint health maintenance side effect.
```

The MU-007 merge semantics prevents a known overwrite defect, but it is not the final boundary. MU-011 must remove the hidden health write from structural save.

Canonical rule:

```text
The problem is not only correctness.
The problem is coupling.
```

---

## 5. Required implementation decisions

### D-011-001 — Structural save becomes structural-only

`H2BaseTopologyRepository.save(...)` must write only:

```text
topology_snapshots / topology_json / topologyVersion / capturedAt
```

It must not create, update, restore or overwrite rows in:

```text
endpoint_health
device_states
future terminal request tables
future TemporalActs tables
```

---

### D-011-002 — Initial endpoint health is explicit

Endpoint materialization currently creates an endpoint with initial `UNKNOWN` health. That initial health must be explicitly persisted when the endpoint structural mutation is accepted.

Expected location:

```text
DefaultTopologyMaterializationService.materializeEndpoint(...)
```

Rules:

```text
- write initial UNKNOWN health only after accepted endpoint structural mutation;
- do not write initial health if endpoint materialization is rejected/duplicate;
- initial health write must not advance topologyVersion;
- observedAt / lastSeenAt must preserve the fact timestamp semantics already established by MU-007.
```

---

### D-011-003 — HealthFact persistence remains explicit

`DefaultTopologyMaterializationService.materializeHealth(...)` already writes endpoint health explicitly. Preserve that behavior.

---

### D-011-004 — DeviceState persistence remains explicit

`DefaultTopologyMaterializationService.materializeDeviceState(...)` already writes device state explicitly. Preserve that behavior.

---

### D-011-005 — Service-level updateEndpointHealth durability must not regress

`BaseTopologyService.updateEndpointHealth(...)` currently mutates aggregate endpoint health and calls repository `save(...)`. With H2, durability currently depends on the hidden health side effect inside `save(...)`.

MU-011 must preserve durability through an explicit boundary.

Preferred strategy:

```text
Introduce a narrow endpoint-health write boundary, e.g. EndpointHealthWritePort.
```

Constraints:

```text
- do not add health methods to BaseTopologyRepository;
- do not make BaseTopologyService depend on H2BaseTopologyRepository concrete type;
- do not introduce TopologyMaterializationStatePort;
- preserve constructor compatibility where practical;
- health write must not advance topologyVersion.
```

A narrower equivalent mechanism is acceptable only if it preserves the same architectural properties.

---

### D-011-006 — CLOB-embedded health is not health source-of-truth

`EndpointHealth` may remain embedded inside `EndpointNode` in `topology_json` because that is the current aggregate shape.

This does not make `topology_json` the durable operational-health source.

For health queries and recovery, the source-of-truth is:

```text
endpoint_health table
  -> CoreSnapshotReadPort.findEndpointHealth(...)
  -> CoreSnapshotQueryService / query consumers
```

---

## 6. Allowed changes

Allowed:

```text
- make H2BaseTopologyRepository.save(...) structural-only;
- add a narrow endpoint-health write interface if needed;
- have H2BaseTopologyRepository implement that narrow interface;
- preserve existing constructors or add overloads for tests/backward compatibility;
- explicitly persist initial UNKNOWN health on accepted endpoint materialization;
- preserve durable service-level updateEndpointHealth behavior;
- add regression tests required by acceptance-map.md;
- adjust tests only to strengthen, not weaken, semantics.
```

---

## 7. Prohibited changes

Prohibited:

```text
- extracting TopologyMaterializationStatePort;
- adding saveEndpointHealth(...) or saveDeviceState(...) to BaseTopologyRepository;
- making BaseTopologyService depend on H2BaseTopologyRepository;
- changing topologyVersion semantics;
- treating provider-native IDs as canonical lookup keys;
- changing RoomNode / ZoneNode semantics;
- implementing RoomDiscoveryFact, ZoneDiscoveryFact or TopologySpatialRelation;
- introducing production persistence schema or migration tooling;
- introducing TemporalActs, terminal request state, outbox/ledger semantics;
- introducing SC-B, SC-D, NATS, JetStream, Projection, Hub, Session, Identity, Authority, Policy or Surface dependencies;
- weakening tests to pass.
```

---

## 8. Required tests or equivalent coverage

Codex should add/update tests equivalent to:

```text
structuralSaveDoesNotTouchEndpointHealthTable
initialEndpointHealthIsWrittenOnMaterialization
serviceLevelEndpointHealthUpdatePersistsDurablyWithoutSaveSideEffect
healthWriteDoesNotAdvanceTopologyVersion
```

All current tests must remain passing.

Test-only direct JDBC access is acceptable for verifying that a structural save does not recreate a deleted or absent `endpoint_health` row. Do not add a production delete API for that purpose.

---

## 9. Implementation strategy summary

Recommended sequence:

```text
1. Inspect current H2BaseTopologyRepository.save(...).
2. Remove endpoint-health write loop / merge side effect from save(...).
3. Ensure endpoint materialization explicitly writes initial UNKNOWN health after accepted structural mutation.
4. Preserve HealthFact explicit health persistence.
5. Preserve DeviceStateFact explicit state persistence.
6. Preserve BaseTopologyService.updateEndpointHealth(...) durability through narrow explicit boundary.
7. Add/adjust regression tests.
8. Run full test suite.
9. Fill implementation-report.md with AC-001 through AC-021 evidence.
```

---

## 10. Out-of-scope follow-up

Remain downstream:

```text
MU-SOV-SC-C-MATERIALIZATION-STATE-PORT-001
PDR-SOV-SC-C-ROOM-ZONE-TOPOLOGY-001
MU-SOV-SC-C-ROOM-ZONE-TOPOLOGY-SEED-001
PDR-SOV-SC-C-CANONICAL-TOPOLOGY-INTERFACE-001
SDD-SOV-SC-C-PERSISTENCE-SCHEMA-001
SDD-SOV-SC-C-RECOVERY-001
SDD-SOV-SC-C-OUTBOX-LEDGER-001
SDD-SOV-SC-C-TEMPORAL-ENGINE-001
```

---

## 11. Current code excerpts (read-only reference)

These excerpts are provided so implementation derives from the actual codebase, not assumptions about it.

### 11.1 `H2BaseTopologyRepository.save(...)` — current body to modify

```java
@Override
public void save(HabitatBaseTopology topology) {
    Objects.requireNonNull(topology, "topology is required");
    String topologyJson = writeJson(topology);
    Instant capturedAt = Instant.now(clock);
    jdbcTemplate.update(
        """
            MERGE INTO topology_snapshots
            (habitat_id, topology_version, topology_json, captured_at)
            KEY (habitat_id)
            VALUES (?, ?, ?, ?)
            """,
        topology.habitatId(),
        topology.topologyVersion().value(),
        topologyJson,
        Timestamp.from(capturedAt)
    );
    // REMOVE from here:
    topology.endpoints().forEach(endpoint -> {
        Optional<EndpointHealth> durable =
            findEndpointHealth(topology.habitatId(), endpoint.endpointId());
        if (durable.isEmpty() || endpoint.health().status() != HealthStatus.UNKNOWN) {
            saveEndpointHealth(
                topology.habitatId(),
                endpoint.endpointId(),
                endpoint.health()
            );
        }
    });
    // REMOVE to here.
}
```

After MU-011, `save(...)` must end after the `MERGE INTO topology_snapshots` write.

---

### 11.2 `BaseTopologyService.updateEndpointHealth(...)` — current body

```java
public void updateEndpointHealth(String habitatId, String endpointId, HealthStatus status) {
    Objects.requireNonNull(status, "status is required");
    HabitatBaseTopology current = repository.findByHabitatId(habitatId)
        .orElseThrow(() -> new IllegalArgumentException(
            "base topology does not exist for habitatId " + habitatId));
    if (current.endpoints().stream()
            .noneMatch(endpoint -> endpoint.endpointId().equals(endpointId))) {
        throw new IllegalArgumentException(
            "endpointId does not exist in habitat topology: " + endpointId);
    }
    List<EndpointNode> endpoints = current.endpoints().stream()
        .map(endpoint -> endpoint.endpointId().equals(endpointId)
            ? withHealth(endpoint, status) : endpoint)
        .toList();
    repository.save(new HabitatBaseTopology(
        current.habitatId(), current.topologyVersion(),
        current.rooms(), current.zones(),
        current.devices(), endpoints, current.metadata()
    ));
    // After MU-011 this call no longer persists health via save(...) side effect.
    // Add explicit healthWritePort.saveEndpointHealth(...) after the save().
}
```

---

### 11.3 `BaseTopologyService` — current constructors

```java
public BaseTopologyService(BaseTopologyRepository repository) {
    this(repository, Clock.systemUTC());
}

public BaseTopologyService(BaseTopologyRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

Existing tests such as `BaseTopologyServiceTest` and `TopologyVersionHardeningTest` use `InMemoryBaseTopologyRepository`.

Those tests do not test H2 health-table durability. Their assertions read aggregate health via `repository.findByHabitatId(...)`, not `findEndpointHealth(...)`.

---

### 11.4 `EndpointHealthWritePort` — recommended pattern

```java
package com.sovereign.connect.core.topology.port;

import com.sovereign.connect.core.topology.model.EndpointHealth;

public interface EndpointHealthWritePort {
    void saveEndpointHealth(String habitatId, String endpointId, EndpointHealth health);

    static EndpointHealthWritePort noOp() {
        return (habitatId, endpointId, health) -> { };
    }
}
```

`H2BaseTopologyRepository` already has `saveEndpointHealth(...)` as a public method. Add the interface to its implements clause without changing the method body unless required by compilation.

Update `BaseTopologyService` with a third constructor:

```java
public BaseTopologyService(
    BaseTopologyRepository repository,
    EndpointHealthWritePort healthWritePort,
    Clock clock
) {
    this.repository = Objects.requireNonNull(repository, "repository is required");
    this.healthWritePort = Objects.requireNonNull(healthWritePort, "healthWritePort is required");
    this.clock = Objects.requireNonNull(clock, "clock is required");
}
```

Existing constructors should delegate to the new constructor using `EndpointHealthWritePort.noOp()`:

```java
public BaseTopologyService(BaseTopologyRepository repository) {
    this(repository, EndpointHealthWritePort.noOp(), Clock.systemUTC());
}

public BaseTopologyService(BaseTopologyRepository repository, Clock clock) {
    this(repository, EndpointHealthWritePort.noOp(), clock);
}
```

Existing tests using `InMemoryBaseTopologyRepository` continue to work through the no-op port because their assertions read health from the aggregate, not from `findEndpointHealth(...)`.

For AC-011, add an H2-backed durability test that constructs `BaseTopologyService` with the three-argument constructor and injects `H2BaseTopologyRepository` as `EndpointHealthWritePort`.

