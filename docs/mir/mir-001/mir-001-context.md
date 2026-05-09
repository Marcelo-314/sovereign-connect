# MIR-SOV-SC-C-BASE-TOPOLOGY-SEED-001 — Implementation Context

## Technology Stack

```text
Java 21+
Spring Boot 3.x
Maven
Hexagonal architecture (ports & adapters)
Sealed interfaces + pattern matching for type safety
Java records for domain value objects
```

## Package Structure Guidance

```text
com.sovereign.connect.core.topology          — domain model
com.sovereign.connect.core.topology.model    — records, enums, value objects
com.sovereign.connect.core.topology.port     — ports (repository interfaces)
com.sovereign.connect.core.topology.service  — domain services
com.sovereign.connect.core.topology.event    — domain events (TopologyChanged)
com.sovereign.connect.adapter.persistence    — in-memory repository adapters
```

Adapt to existing project conventions if they exist. If the repository already has
a different package structure, follow it — but preserve the hexagonal separation.

---

## Reference Domain Types

These Java records are extracted from `RFC-SOV-SC-BASE-TOPOLOGY-001 v0.2.1-draft`
and `PDR-SOV-SC-ENDPOINT-NODE-001 v0.2.1-draft`. They are reference shapes, not
mandatory verbatim implementations. Semantic distinctions MUST be preserved.

### Root Aggregate

```java
public record HabitatBaseTopology(
    String habitatId,
    String topologyVersion,
    List<RoomNode> rooms,
    List<ZoneNode> zones,
    List<DeviceNode> devices,
    List<EndpointNode> endpoints,
    TopologyMetadata metadata
) {}
```

### Habitat Root Rule

For this seed, `HabitatBaseTopology` is the root aggregate representing the Habitat-level Base Topology scope.

Do not create a separate `HabitatNode` unless the existing repository already contains that concept.

When the prompt says:

Habitat → Room → Zone → Device → Endpoint → Capability

interpret `Habitat` as the `HabitatBaseTopology` / `habitatId` scope.

### TopologyMetadata

```java
public record TopologyMetadata(
    String schemaVersion,
    Instant lastModified,
    String source,
    String checksum
) {}
```

### TopologyVersion

```java
public record TopologyVersion(
    TopologyVersionScope scope,
    String value
) {}

public record TopologyVersionScope(
    TopologyVersionScopeType type,
    String id
) {}

public enum TopologyVersionScopeType {
    HABITAT
}
```

### TopologyVersion Representation Rule

The implementation MAY represent the version inside `HabitatBaseTopology` either as:

- `TopologyVersion topologyVersion`; or
- `String topologyVersion`, where the string is `TopologyVersion.value`.

If the aggregate keeps `String topologyVersion` for compatibility with Canonical Contract shapes, the repository/service layer MUST still expose `TopologyVersion` as the typed version object.

`TopologyVersion` MUST be habitat-scoped.

`TopologyVersion` MUST NOT derive from session, user, Projection, Authority, Policy or provider-native versions.

### RoomNode

```java
public record RoomNode(
    String roomId,
    String roomName,
    List<String> zoneIds,
    List<String> deviceIds,
    List<String> endpointIds,
    RoomTraits traits
) {}
```

### ZoneNode

```java
public record ZoneNode(
    String zoneId,
    String zoneName,
    String roomId,
    List<String> deviceIds,
    List<String> endpointIds,
    ZoneTraits traits
) {}
```

### DeviceNode

```java
public record DeviceNode(
    String deviceId,
    String alias,
    String displayName,
    String roomId,
    String zoneId,
    DeviceKind kind,
    DeviceProvider provider,
    List<String> endpointIds,
    List<CapabilityNode> deviceCapabilities,
    DeviceTraits traits,
    DeviceHealth health,
    ProviderDeviceRef providerRef
) {}
```

### EndpointNode

```java
public record EndpointNode(
    String endpointId,
    String deviceId,
    String alias,
    String displayName,
    EndpointKind kind,
    String roomId,
    String zoneId,
    List<CapabilityNode> capabilities,
    EndpointTraits traits,
    EndpointHealth health,
    ProviderEndpointRef providerRef,
    EndpointMetadata metadata
) {}
```

### CapabilityNode

```java
public record CapabilityNode(
    String capabilityId,
    String name,
    CapabilityKind kind,
    CapabilityTraits traits
) {}
```

### ProviderDeviceRef

```java
public record ProviderDeviceRef(
    String provider,
    String providerDeviceId,
    Map<String, String> nativeCoordinates
) {}
```

### ProviderEndpointRef

```java
public record ProviderEndpointRef(
    String provider,
    String providerDeviceId,
    String providerEndpointId,
    Map<String, String> nativeCoordinates
) {}
```

### Health

```java
public record DeviceHealth(
    HealthStatus status,
    Instant lastSeenAt,
    String details
) {}

public record EndpointHealth(
    HealthStatus status,
    Instant lastSeenAt,
    String details
) {}

public enum HealthStatus {
    HEALTHY, DEGRADED, OFFLINE, UNKNOWN
}
```

### Traits (simplified for seed)

```java
public record DeviceTraits(
    boolean safetyCritical,
    boolean supportsVccFollowUp,
    boolean requiresStableStateReadback
) {}

public record EndpointTraits(
    boolean addressable,
    boolean independentlyLocatable,
    boolean independentlyOperable,
    boolean stateful,
    boolean readOnly,
    boolean safetyCritical
) {}

public record CapabilityTraits(
    boolean idempotent,
    boolean stateChanging,
    boolean requiresConfirmationByDefault
) {}

public record RoomTraits(
    boolean privateRoom,
    boolean guestDefaultRoom
) {}

public record ZoneTraits(
    boolean fineGrainedTargeting
) {}
```

### Enums

```java
public enum DeviceKind {
    LIGHT, SWITCH, DIMMER, LOCK, CLIMATE, SENSOR, MEDIA,
    SHADE, POWER_STRIP, BRIDGE, CONTROLLER, VIRTUAL, OTHER
}

public enum DeviceProvider {
    TUYA, SHELLY, MATTER, ZIGBEE, ZWAVE, HUE,
    MODBUS, BLE, IR, VIRTUAL, OTHER
}

public enum EndpointKind {
    ROOT, DEFAULT, LIGHT, SWITCH_CHANNEL, OUTLET, SENSOR,
    TEMPERATURE_SENSOR, HUMIDITY_SENSOR, MOTION_SENSOR,
    CLIMATE_ZONE, LOCK_MECHANISM, SHADE_CHANNEL,
    MEDIA_COMPONENT, BRIDGE_CHILD, DIAGNOSTIC, OTHER
}

public enum CapabilityKind {
    BINARY_SWITCH, LEVEL, TOGGLE, OPEN_CLOSE,
    LOCK_UNLOCK, SET_TEMPERATURE, READ_ONLY,
    SCENE_ACTIVATE, DIAGNOSTIC, OTHER
}
```

### TopologyChanged (optional domain event)

```java
public record TopologyChanged(
    UUID eventId,
    Instant timestamp,
    String habitatId,
    String fromVersion,
    String toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    String reason
) {}

public enum TopologyChangeKind {
    DEVICE_ADDED, DEVICE_REMOVED,
    ENDPOINT_ADDED, ENDPOINT_REMOVED, ENDPOINT_CHANGED,
    CAPABILITY_ADDED, CAPABILITY_REMOVED,
    TRAITS_CHANGED, SPATIAL_ASSIGNMENT_CHANGED
}
```

---

## Identity Rules

```text
deviceId       — canonical SC-C identity, assigned by SC-C, unique per habitat
endpointId     — canonical SC-C identity, assigned by SC-C, unique per habitat
capabilityId   — unique within containing node (endpoint or device)

providerDeviceId    — provider-native, stored as metadata in ProviderDeviceRef
providerEndpointId  — provider-native, stored as metadata in ProviderEndpointRef

ProviderEndpointRef MUST NEVER become canonical endpointId.
Provider-native IDs are binding metadata, not topology identity.
```

---

## Architectural Invariants

```text
SC-C owns Base Topology.
SC-C owns topologyVersion.
SC-C emits TopologyChanged (domain event, no bus publication in this seed).

DeviceNode is the stable topological container.
EndpointNode is the addressable operational locus.
CapabilityNode is the canonical affordance.

Base Topology does NOT contain: VisibilityRule, Effective View,
session/identity/authority/policy fields, conversation metadata.

EndpointNode is first-class — NOT an adapter detail.
Endpoint awareness is a Base Topology invariant of SC-Core.
```

---

## Negative Scope — Do NOT Implement

```text
Effective View / Projection / VisibilityRule
Authority / Identity / Session / Policy
Hub / Surfaces / NLU / MCP
SC-B transport (NATS, JetStream, Vert.x, gRPC, MQTT, WebSocket)
Real SC-D adapters / provider discovery / vendor payload parsing
Command dispatch / device execution / verification
Adapter lifecycle / admission / quarantine
TemporalActs
Full snapshot query API
Production-grade durable persistence
Distributed topology synchronization
```

Any of these appearing in the implementation is a deviation that MUST be reported.
