# MIR-002 — SC-C topologyVersion Seed — Implementation Context

```text
MIR: MIR-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
MIR Version: v0.1.0-draft
MU: MU-SOV-SC-C-TOPOLOGY-VERSION-SEED-001
Status: Draft execution artifact
Date: 2026-05-09
```

---

## 0. Purpose

Read this file before executing `docs/mir/mir-002/codex-prompt.md`.

This context defines the existing codebase surface, new domain shapes,
architectural placement rules and boundary constraints for MU-002.

This file is operational. It does not override MIR, PDR, RFC or INDEX documents.

---

## 1. Technology Stack

```text
Java 21+
Spring Boot 3.x
Maven
Hexagonal architecture (ports & adapters)
Sealed interfaces + pattern matching
Java records for value objects
```

---

## 2. Existing MU-001 Codebase

The repository contains the validated MU-001 implementation.
Do not duplicate existing types. Extend or refine them.

### 2.1 Existing domain model

```text
com.sovereign.connect.core.topology.model
  HabitatBaseTopology        — root aggregate, contains TopologyVersion
  RoomNode, ZoneNode         — spatial nodes
  DeviceNode                 — stable topological container
  EndpointNode               — addressable operational locus
  CapabilityNode             — canonical affordance
  ProviderDeviceRef           — provider binding metadata
  ProviderEndpointRef         — provider binding metadata
  TopologyMetadata           — schema version, timestamp, source, checksum
  TopologyVersion            — typed version with scope and value
  TopologyVersionScope       — scope type + scope id
  TopologyVersionScopeType   — enum: HABITAT
  TopologyNode               — sealed interface with canonicalId()
  DeviceKind, DeviceProvider, EndpointKind, CapabilityKind
  DeviceHealth, EndpointHealth, HealthStatus
  DeviceTraits, EndpointTraits, CapabilityTraits, RoomTraits, ZoneTraits
  EndpointMetadata
```

### 2.2 Existing service API

```java
public class BaseTopologyService {

    // Creates initial topology with version 1
    public HabitatBaseTopology createInitialTopology(
        String habitatId,
        List<RoomNode> rooms,
        List<ZoneNode> zones,
        List<DeviceNode> devices,
        List<EndpointNode> endpoints
    );

    // Structural mutation: adds endpoint, advances version, emits TopologyChanged
    public HabitatBaseTopology addEndpoint(String habitatId, EndpointNode endpoint);

    // Delegates to repository
    public Optional<TopologyVersion> findCurrentVersion(String habitatId);

    // Returns internal domain events (not published to bus)
    public List<TopologyChanged> emittedEvents();
}
```

### 2.3 Existing repository port

```java
public interface BaseTopologyRepository {
    void save(HabitatBaseTopology topology);
    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);
}
```

### 2.4 Existing domain event

```java
public record TopologyChanged(
    UUID eventId, Instant timestamp, String habitatId,
    String fromVersion, String toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds, List<String> affectedEndpointIds,
    String reason
) {}
```

### 2.5 Existing TopologyVersion

```java
public record TopologyVersion(TopologyVersionScope scope, String value) {
    public static TopologyVersion initialForHabitat(String habitatId);
    public static TopologyVersion habitatVersion(String habitatId, long value);
    public TopologyVersion next();
    public long asLong();
    public boolean isScopedToHabitat(String habitatId);
}
```

### 2.6 Existing test class

```text
com.sovereign.connect.core.topology.BaseTopologyServiceTest
  8 tests — all passing
```

### 2.7 Existing adapter

```text
com.sovereign.connect.adapter.persistence.InMemoryBaseTopologyRepository
```

---

## 3. New Domain Shapes — Reference

These are recommended shapes for MU-002. Adapt to existing conventions.

### 3.1 TopologyMutationResult

Return type for structural mutations. Separates mutation result from full snapshot.

```java
public record TopologyMutationResult(
    String habitatId,
    TopologyVersion fromVersion,
    TopologyVersion toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds
) {}
```

Place in: `com.sovereign.connect.core.topology.model`

Preserve backwards compatibility with the existing MU-001 `addEndpoint` API.

Preferred approach:
  - keep the existing `addEndpoint` method behavior unchanged;
  - add a new method such as `addEndpointWithResult`, `mutateTopology`, or
    equivalent, returning `TopologyMutationResult`.

Alternative:
  - if changing `addEndpoint` is unavoidable, update all existing tests and
    report the signature change explicitly in implementation-report.md.

Do not break MU-001 behavior silently.

### 3.2 Stale target validation

Minimal validator for stale topology version detection. Does not require
full ActionTarget or command dispatch.

```java
public record TopologyTargetRef(
    String deviceId,
    String endpointId,
    String capabilityId
) {}

public enum TargetValidationResult {
    VALID,
    VALID_AFTER_REVALIDATION,
    TARGET_NOT_FOUND,
    CAPABILITY_MISMATCH,
    TOPOLOGY_VERSION_CONFLICT
}
```

Place `TopologyTargetRef` in: `com.sovereign.connect.core.topology.model`

Validation logic as a method on `BaseTopologyService`:

```java
public TargetValidationResult validateTarget(
    String habitatId,
    TopologyTargetRef target,
    TopologyVersion requestTopologyVersion
);
```

Three required cases:
1. `requestVersion == currentVersion` and target exists → `VALID`
2. `requestVersion != currentVersion` but target still exists with same capability → `VALID_AFTER_REVALIDATION`
3. `requestVersion != currentVersion` and target missing or capability changed → `TARGET_NOT_FOUND` or `CAPABILITY_MISMATCH`

### 3.3 Idempotency identity

Minimal value object proving topologyVersion is excluded from identity computation.

```java
public record IdempotencyIdentity(
    String operationKind,
    TopologyTargetRef target,
    Map<String, Object> canonicalParams
) {}
```

Place in: `com.sovereign.connect.core.topology.model`

The test must prove: same `operationKind` + same `target` + same `params`
+ different `topologyVersion` → same `IdempotencyIdentity`.

This means `topologyVersion` is NOT a field of `IdempotencyIdentity`.

### 3.4 Non-structural update seed methods

Add minimal methods to `BaseTopologyService` for testing non-advancement:

```java
// Updates endpoint health without advancing topologyVersion
public void updateEndpointHealth(String habitatId, String endpointId, HealthStatus status);

// Updates device operational state metadata without advancing topologyVersion
public void updateDeviceState(String habitatId, String deviceId, Map<String, Object> state);
```

These are seed-only methods. They do NOT implement full state/health materialization.
They exist to prove that non-structural changes do not advance topologyVersion.

Note:
  topologyVersion versions structural Base Topology.
  Seed state/health updates MAY change operational metadata without advancing
  topologyVersion.
  This is not a snapshot inconsistency, provided structural topology remains
  unchanged.

---

## 4. Architectural Placement Rules

```text
New model types        → com.sovereign.connect.core.topology.model
New enums              → com.sovereign.connect.core.topology.model
Service extensions     → com.sovereign.connect.core.topology.service (existing class)
New tests              → com.sovereign.connect.core.topology (extend existing test class
                         or create TopologyVersionHardeningTest)
```

Do not create new packages for MU-002.
Do not create separate validator classes unless the service becomes unwieldy.
Prefer adding methods to the existing `BaseTopologyService`.

---

## 5. Boundary Constraints

### 5.1 SC-C owns topologyVersion

SC-B must not own topologyVersion semantics.
SC-D must not assign or advance topologyVersion.
Projection must not assign or advance topologyVersion.
Session, Identity, Authority, Policy must not participate.

### 5.2 Advancement rule

topologyVersion advances ONLY after accepted structural Base Topology mutation.
Rejected mutations, state updates, health updates, Projection/Session/Authority/Policy
changes do NOT advance topologyVersion.

### 5.3 Snapshot consistency

Retrieved snapshot must be consistent with its topologyVersion.
No new-topology-with-old-version. No old-topology-with-new-version.

### 5.4 Provider-native versions

Provider revisions are metadata only. They never become canonical topologyVersion.

---

## 6. Negative Scope

Do NOT implement: durable persistence, DB schema, distributed versioning,
event sourcing, transactional outbox, SC-B transport (NATS/JetStream/Vert.x/gRPC/MQTT/WebSocket),
real SC-D adapters, provider discovery/parsing/reconciliation, full ActionTarget PDR,
full command dispatch, device execution, adapter lifecycle/admission, TemporalActs,
full snapshot query API, historical snapshots, Projection/Effective View/VisibilityRule,
Session/Identity/Authority/Policy modules, Hub/Surface/MCP/UI.

If any of these becomes necessary to pass tests, STOP and report as failure signal.
