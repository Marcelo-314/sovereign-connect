# Codex Prompt — MIR-007 — SC-C Topology Materialization Seed

```text
MIR: MIR-SOV-SC-C-TOPOLOGY-MATERIALIZATION-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-TOPOLOGY-MATERIALIZATION-001
Operational Slot: MU-007
Execution Status: Authorized
```

---

## Read First

Read `docs/mir/mir-007/context.md` entirely before writing a single line of code.

Pay special attention to the critical decisions:

```text
§2  emittedEvents() delta pattern
§3  duplicate detection before service call
§4  habitat pre-condition
§5  addDeviceWithResult must be added to BaseTopologyService
§6  state/health goes to repository, NOT BaseTopologyService
§7  capability materialization must not re-add endpoint
§8  deterministic seed canonical IDs
§14 AC numbering is AC-001 through AC-025 only
```

Use `docs/mir/mir-007/acceptance-map.md` for AC-001 through AC-025.

---

## Goal

Prove:

```text
local TopologyFact → TopologyMaterializationService → MaterializationDecision
  → BaseTopologyService structural mutation
  → topologyVersion advances only when structural
  → TopologyChanged uses existing transitional shape
  → H2 persistence
  → CoreSnapshotQueryService observes materialized topology after recovery
```

No real SC-D.

No SC-B.

No Projection.

No Authority / Session / Identity / Policy.

---

## Implementation steps

### 1. Verify baseline

Confirm 23 tests pass before writing anything.

```bash
mvn test
```

If any fail, stop and report.

### 2. Add additive BaseTopologyService helpers

The existing service has `addEndpointWithResult(...)` but not `addDeviceWithResult(...)`.

Add `addDeviceWithResult(...)` following the exact pattern shown in `context.md §5`.

Rules:

```text
same topologyVersion advancement pattern as addEndpointWithResult;
same emit(...) pattern with DEVICE_ADDED;
same save → emit sequence;
backward-compatible;
no existing test should break.
```

Also add:

```text
appendDevice(RoomNode, String)
appendDevice(ZoneNode, String)
```

following the existing `appendEndpoint(...)` pattern.

Capability materialization requires an additive helper:

```java
TopologyMutationResult addCapabilityWithResult(
    String habitatId,
    String endpointId,
    CapabilityNode capability
)
```

Rules:

```text
must update/replace existing EndpointNode;
must not call addEndpointWithResult(...) on an existing endpoint;
must advance topologyVersion;
must persist through repository.save(...);
must emit existing transitional TopologyChanged with CAPABILITY_ADDED.
```

### 3. Add materialization package

Add all types from `context.md §9` to:

```text
com.sovereign.connect.core.topology.materialization
```

Required:

```text
TopologyFact sealed interface
DeviceDiscoveryFact
EndpointDiscoveryFact
CapabilityDiscoveryFact
DeviceStateFact
HealthFact
MaterializationDecisionKind
MaterializationDecision
TopologyMaterializationService
DefaultTopologyMaterializationService
```

Do not add `RelationFact` for this seed.

### 4. Implement DefaultTopologyMaterializationService

Constructor per `context.md §10`.

Implement:

```java
materialize(String habitatId, TopologyFact fact)
```

using pattern matching on sealed types:

```java
return switch (fact) {
    case DeviceDiscoveryFact f -> materializeDevice(habitatId, f);
    case EndpointDiscoveryFact f -> materializeEndpoint(habitatId, f);
    case CapabilityDiscoveryFact f -> materializeCapability(habitatId, f);
    case DeviceStateFact f -> materializeDeviceState(habitatId, f);
    case HealthFact f -> materializeHealth(habitatId, f);
};
```

Every structural branch must obey:

### Rule A — Delta pattern

```java
int before = baseTopologyService.emittedEvents().size();

// call mutation

List<TopologyChanged> delta = baseTopologyService.emittedEvents()
    .subList(before, baseTopologyService.emittedEvents().size());
```

### Rule B — Duplicate detection before service call

```java
Optional<HabitatBaseTopology> topology =
    repository.findByHabitatId(habitatId);

if (topology.isEmpty()) {
    return rejectInvalid(fact, "habitat not initialized");
}

boolean alreadyExists = topology.get().devices().stream()
    .anyMatch(d -> d.deviceId().equals(canonicalId));

if (alreadyExists) {
    return rejectDuplicate(fact);
}
```

Adapt for endpoint/capability.

### Rule C — Habitat check first

```java
Optional<HabitatBaseTopology> topology =
    repository.findByHabitatId(habitatId);

if (topology.isEmpty()) {
    return rejectInvalid(fact, "habitat not initialized");
}
```

Do not let `IllegalArgumentException` propagate for missing habitat.

### Rule D — State/health to repository

```java
repository.saveDeviceState(...);
repository.saveEndpointHealth(...);
```

Do NOT use:

```java
baseTopologyService.updateDeviceState(...);
```

from the materializer.

### Rule E — HealthFact endpoint-level only

For seed:

```text
HealthFact.providerEndpointId must be present.
If absent, return REJECT_INVALID_FACT or NOOP.
Do not add device-level health persistence.
```

### Rule F — Capability helper only

Do not call `addEndpointWithResult(...)` to update an existing endpoint with a new capability.

Use `addCapabilityWithResult(...)`.

### 5. Implement deterministic ID mapping

Per `context.md §8`:

```java
String deviceId =
    "device." + fact.providerId() + "." + fact.providerDeviceId();

String endpointId =
    "endpoint." + fact.providerId() + "." + fact.providerDeviceId()
    + "." + fact.providerEndpointId();

String capabilityId =
    "capability." + fact.providerId() + "." + fact.providerDeviceId()
    + "." + fact.providerEndpointId()
    + "." + fact.providerCapabilityKey();
```

Assertions required:

```text
deviceId != providerDeviceId
endpointId != providerEndpointId
capabilityId != providerCapabilityKey
```

### 6. Add TopologyMaterializationSeedTest

Use durable wiring from `context.md §12`.

Initialize habitat before materializing any fact:

```java
mutationService.createInitialTopology(
    "habitat-007",
    List.of(room),
    List.of(zone),
    List.of(),
    List.of()
);
```

Required assertion groups:

### Structural materialization assertions

```java
MaterializationDecision deviceDecision =
    materializationService.materialize("habitat-007", deviceDiscoveryFact);

assertThat(deviceDecision.kind())
    .isEqualTo(ACCEPT_STRUCTURAL_MUTATION);

assertThat(deviceDecision.resultingTopologyVersion())
    .isPresent()
    .isNotEqualTo(deviceDecision.previousTopologyVersion());

assertThat(deviceDecision.emittedChanges())
    .hasSize(1);

TopologyChanged event = deviceDecision.emittedChanges().get(0);

assertThat(event.changeKinds())
    .contains(TopologyChangeKind.DEVICE_ADDED);

assertThat(event.fromVersion())
    .isNotEqualTo(event.toVersion());

assertThat(event.affectedDeviceIds())
    .contains(canonicalDeviceId);

assertThat(canonicalDeviceId)
    .isNotEqualTo(deviceDiscoveryFact.providerDeviceId());
```

### Duplicate rejection

```java
MaterializationDecision duplicate =
    materializationService.materialize("habitat-007", sameDeviceDiscoveryFact);

assertThat(duplicate.kind())
    .isEqualTo(REJECT_DUPLICATE);

assertThat(duplicate.emittedChanges())
    .isEmpty();

assertThat(queryService.findCurrentTopologyVersion("habitat-007"))
    .isEqualTo(deviceDecision.resultingTopologyVersion());
```

### Non-structural state assertion

```java
TopologyVersion versionBefore =
    queryService.findCurrentTopologyVersion("habitat-007").orElseThrow();

materializationService.materialize("habitat-007", deviceStateFact);

assertThat(queryService.findCurrentTopologyVersion("habitat-007"))
    .contains(versionBefore);
```

### Endpoint health assertion

```java
TopologyVersion versionBefore =
    queryService.findCurrentTopologyVersion("habitat-007").orElseThrow();

materializationService.materialize("habitat-007", endpointHealthFact);

assertThat(queryService.findCurrentTopologyVersion("habitat-007"))
    .contains(versionBefore);

assertThat(repository.findEndpointHealth("habitat-007", canonicalEndpointId))
    .isPresent();
```

### Capability assertion

```java
MaterializationDecision capabilityDecision =
    materializationService.materialize("habitat-007", capabilityDiscoveryFact);

assertThat(capabilityDecision.kind())
    .isEqualTo(ACCEPT_STRUCTURAL_MUTATION);

assertThat(capabilityDecision.emittedChanges())
    .hasSize(1);

assertThat(capabilityDecision.emittedChanges().get(0).changeKinds())
    .contains(TopologyChangeKind.CAPABILITY_ADDED);
```

### Recovery assertion

After discarding all instances and recreating from same H2 path:

```java
CoreSnapshot recovered =
    newQueryService.findCurrentSnapshot("habitat-007").orElseThrow();

assertThat(recovered.topology().devices())
    .anyMatch(d -> d.deviceId().equals(canonicalDeviceId));

assertThat(recovered.topologyVersion())
    .isEqualTo(deviceDecision.resultingTopologyVersion().orElseThrow());
```

---

## Build

```bash
mvn test
```

Expected:

```text
at least 24 tests passing
23 existing + at least 1 new
```

---

## Output

Create `docs/mir/mir-007/implementation-report.md` with:

```text
1. Summary
2. Files changed
3. BaseTopologyService methods added
4. Materialization types added
5. Materialization service behavior
6. Tests added
7. AC-001 through AC-025 — pass/fail with evidence
8. topologyVersion evidence per fact type
9. TopologyChanged transitional shape evidence
10. Provider ID vs canonical ID evidence
11. Duplicate detection evidence
12. State/health persistence path
13. Admission predicate evidence
14. Capability materialization evidence
15. Recovery evidence
16. Boundary evidence
17. Required statements:
      Structural mutation path: BaseTopologyService.addDeviceWithResult / addEndpointWithResult / addCapabilityWithResult
      Independent materializer mutation engine: no
      emittedEvents delta pattern used: yes
      Duplicate detection before service call: yes
      Habitat pre-condition check: yes
      State/health path: H2BaseTopologyRepository direct, not service ConcurrentMap
      Capability path: addCapabilityWithResult, not addEndpointWithResult on existing endpoint
      HealthFact scope: endpoint-level only for seed
      Deterministic canonical IDs: device.<providerId>.<providerDeviceId> etc.
      TopologyChanged shape: existing transitional shape preserved
      SC-B required: no
      SC-D required: no
      Projection/Authority/Session required: no
18. Deviations from scope
19. Failure signals encountered
20. Assumptions made
21. Recommended next MU
```

---

## Stop conditions

Stop and report if:

```text
provider ID becomes canonical ID
materializer directly mutates HabitatBaseTopology and calls repository.save bypassing service
materializer uses baseTopologyService.updateDeviceState for DeviceStateFact persistence
emittedEvents() delta pattern not used
duplicate detection happens after service call and exception propagates
habitat check missing and IllegalArgumentException propagates
SC-D / SC-B / Projection / Authority / Session required
duplicate fact advances topologyVersion
state/health fact advances topologyVersion
accepted structural mutation fails to advance topologyVersion
TopologyChanged replaced with singular topologyVersion/changeKind variant
existing 23 tests fail
RelationFact becomes mandatory
CapabilityDiscoveryFact uses addEndpointWithResult(...) to update existing endpoint
HealthFact introduces unsupported device-level health persistence
```
