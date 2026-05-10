# MIR-004 — SC-C Persistence and Memory Seed — Implementation Context

```text
MIR: MIR-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
MIR Version: v1.0.0-accepted
MU: MU-SOV-SC-C-PERSISTENCE-MEMORY-SEED-001
Status: Approved execution artifact
Date: 2026-05-10
```

---

## 0. Purpose

Read this file before executing `docs/mir/mir-004/codex-prompt.md`.

This context defines the existing codebase surface, new domain shapes, infrastructure guidance, adapter patterns and boundary constraints for MU-004.

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

Seed persistence technology:

```text
H2 file-backed (preferred)
or simplest durable local store compatible with the existing project
```

Seed storage is NOT final production doctrine.

---

## 2. Existing MU-001/MU-002 Codebase

The repository contains validated MU-001 and MU-002 implementations. Do not duplicate existing types. Extend or refine them.

### 2.1 Existing domain model

```text
com.sovereign.connect.core.topology.model
  HabitatBaseTopology        — root aggregate, contains TopologyVersion
  RoomNode, ZoneNode         — spatial nodes
  DeviceNode                 — stable topological container
  EndpointNode               — addressable operational locus
  CapabilityNode             — canonical affordance
  ProviderDeviceRef          — provider binding metadata
  ProviderEndpointRef        — provider binding metadata
  TopologyMetadata           — schema version, timestamp, source, checksum
  TopologyVersion            — typed version with scope and value
  TopologyVersionScope       — scope type + scope id
  TopologyVersionScopeType   — enum: HABITAT
  TopologyNode               — sealed interface with canonicalId()
  TopologyMutationResult     — fromVersion/toVersion/changeKinds
  TopologyTargetRef          — deviceId/endpointId/capabilityId
  TargetValidationResult     — VALID / VALID_AFTER_REVALIDATION / TARGET_NOT_FOUND / etc.
  IdempotencyIdentity        — operationKind/target/params (excludes topologyVersion)
  DeviceKind, DeviceProvider, EndpointKind, CapabilityKind
  DeviceHealth, EndpointHealth, HealthStatus
  DeviceTraits, EndpointTraits, CapabilityTraits, RoomTraits, ZoneTraits
  EndpointMetadata
```

### 2.2 Existing repository port

```java
public interface BaseTopologyRepository {
    void save(HabitatBaseTopology topology);
    Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
    Optional<TopologyVersion> findCurrentVersion(String habitatId);
}
```

### 2.3 Existing in-memory adapter

```text
com.sovereign.connect.adapter.persistence.InMemoryBaseTopologyRepository
```

This adapter MUST be preserved for existing MU-001/MU-002 tests.

MU-004 tests use the NEW durable adapter, not this one.

### 2.4 Existing service API

```java
public class BaseTopologyService {

    public BaseTopologyService(BaseTopologyRepository repository);
    public BaseTopologyService(BaseTopologyRepository repository, Clock clock);

    // MU-001: creates initial topology with version 1
    public HabitatBaseTopology createInitialTopology(
        String habitatId,
        List<RoomNode> rooms, List<ZoneNode> zones,
        List<DeviceNode> devices, List<EndpointNode> endpoints
    );

    // MU-001: structural mutation, advances version, emits TopologyChanged
    public HabitatBaseTopology addEndpoint(String habitatId, EndpointNode endpoint);

    // MU-002: structural mutation returning typed result
    public TopologyMutationResult addEndpointWithResult(String habitatId, EndpointNode endpoint);

    // MU-002: non-structural — does NOT advance topologyVersion
    public void updateEndpointHealth(String habitatId, String endpointId, HealthStatus status);

    // MU-002: non-structural — stores state in service-local ConcurrentMap
    public void updateDeviceState(String habitatId, String deviceId, Map<String, Object> state);

    // MU-002: retrieves service-local device state
    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId);

    // MU-002: stale target validation
    public TargetValidationResult validateTarget(
        String habitatId, TopologyTargetRef target, TopologyVersion requestTopologyVersion
    );

    // delegates to repository
    public Optional<TopologyVersion> findCurrentVersion(String habitatId);

    // domain events (not published to bus)
    public List<TopologyChanged> emittedEvents();
}
```

### 2.5 Existing TopologyVersion API

```java
public record TopologyVersion(TopologyVersionScope scope, String value) {
    public static TopologyVersion initialForHabitat(String habitatId);
    public static TopologyVersion habitatVersion(String habitatId, long value);
    public TopologyVersion next();
    public long asLong();
    public boolean isScopedToHabitat(String habitatId);
}
```

### 2.6 Existing test classes

```text
com.sovereign.connect.core.topology.BaseTopologyServiceTest        — 8 tests
com.sovereign.connect.core.topology.TopologyVersionHardeningTest   — 12 tests
Total: 20 tests — all must continue passing
```

### 2.7 Key observation: device state is service-local

MU-002's `updateDeviceState` stores state in a `ConcurrentMap<String, Map<String, Object>>` inside the service instance, NOT inside HabitatBaseTopology. This means device state does not survive service recreation by default.

MU-004 must make device state recoverable through a durable mechanism.

Options:

```text
- persist device state in a durable adapter alongside topology;
- persist device state through a separate durable memory port;
- use adapter-direct seed tests to prove device state recovery without changing existing service constructors.
```

---

## 3. New Domain Shapes — Reference

### 3.1 BaseTopologySnapshot

Persistence/query envelope. Source of truth remains `HabitatBaseTopology.topologyVersion`.

```java
public record BaseTopologySnapshot(
    String habitatId,
    TopologyVersion topologyVersion,
    HabitatBaseTopology topology,
    Instant capturedAt
) {}
```

Place in:

```text
com.sovereign.connect.core.topology.model
```

Critical rule:

```text
snapshot.topologyVersion MUST equal snapshot.topology.topologyVersion.
```

A mismatch is a critical failure signal.

### 3.2 TopologyMutationRecord

Minimal mutation memory/ledger entry.

```java
public record TopologyMutationRecord(
    UUID mutationId,
    String habitatId,
    TopologyVersion fromVersion,
    TopologyVersion toVersion,
    Set<TopologyChangeKind> changeKinds,
    List<String> affectedDeviceIds,
    List<String> affectedEndpointIds,
    String reason,
    Instant acceptedAt
) {}
```

Place in:

```text
com.sovereign.connect.core.topology.model
```

---

## 4. Infrastructure Guidance — H2 File-Backed Seed

### 4.1 Maven dependency

If not already present, add to `pom.xml`:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

Do not introduce JPA entities for MIR-004 unless the repository already uses JPA.

Preferred seed implementation:

```text
plain JDBC or Spring JDBC over H2 file-backed storage
```

Rationale:

```text
MIR-004 validates durability/recovery semantics, not ORM mapping.
```

If the project already uses Spring Data JPA or JDBC, adapt accordingly.

Do not introduce:

```text
@Entity
JpaRepository
schema migrations
production persistence model
```

unless the repository already relies on those mechanisms.

### 4.2 Test configuration

For MU-004 persistence tests, configure H2 in file-backed mode so data survives across DataSource/repository recreations within the same test.

Preferred programmatic configuration:

```java
String url = "jdbc:h2:file:" + tempDir.resolve("sc-topology");
DataSource dataSource = new DriverManagerDataSource(url, "sa", "");
```

Alternative direct DriverManager usage is also acceptable.

Avoid using `org.h2.jdbcx.JdbcDataSource` directly unless H2 is available on the test compile classpath.

The programmatic approach is preferred because it avoids Spring context coupling and gives explicit control over DataSource lifecycle for recovery tests.

If Spring test properties are used instead, keep them test-specific:

```text
spring.datasource.url=jdbc:h2:file:./target/test-db/sc-topology;AUTO_SERVER=FALSE
spring.datasource.driver-class-name=org.h2.Driver
```

Do not introduce application-wide production persistence configuration for this MIR.

### 4.3 Schema approach for seed

For seed purposes, store topology as a JSON blob.

Do NOT design a normalized relational schema.

Minimal tables:

```sql
CREATE TABLE IF NOT EXISTS topology_snapshots (
    habitat_id VARCHAR(255) PRIMARY KEY,
    topology_version VARCHAR(255) NOT NULL,
    topology_json CLOB NOT NULL,
    captured_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS mutation_records (
    mutation_id VARCHAR(255) PRIMARY KEY,
    habitat_id VARCHAR(255) NOT NULL,
    from_version VARCHAR(255) NOT NULL,
    to_version VARCHAR(255) NOT NULL,
    change_kinds VARCHAR(1024),
    affected_device_ids VARCHAR(2048),
    affected_endpoint_ids VARCHAR(2048),
    reason VARCHAR(1024),
    accepted_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS device_states (
    habitat_id VARCHAR(255) NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    state_json CLOB NOT NULL,
    PRIMARY KEY (habitat_id, device_id)
);

CREATE TABLE IF NOT EXISTS endpoint_health (
    habitat_id VARCHAR(255) NOT NULL,
    endpoint_id VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    last_seen_at TIMESTAMP,
    details VARCHAR(1024),
    PRIMARY KEY (habitat_id, endpoint_id)
);
```

This schema is a SEED schema, NOT production doctrine.

### 4.4 JSON serialization

Use Jackson for serializing `HabitatBaseTopology` to/from JSON CLOB.

Use an `ObjectMapper` with Java Time support, for example:

```java
ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
```

This is important for records containing `Instant`.

Ensure all records are Jackson-serializable. Java records with canonical constructors typically work with Jackson if parameter names and Java Time modules are available.

If serialization fails, add `@JsonProperty` annotations or minimal ObjectMapper configuration.

Keep it minimal.

Do not redesign the domain model merely to satisfy persistence serialization.

---

## 5. Durable Adapter Pattern

### 5.1 Adapter class

Create a durable adapter implementing `BaseTopologyRepository`:

```text
com.sovereign.connect.adapter.persistence.H2BaseTopologyRepository
```

or equivalent name following project conventions.

This adapter implements the same port as `InMemoryBaseTopologyRepository`.

The service constructor receives either adapter — hexagonal architecture means the service does not know which adapter it is using.

### 5.2 Additional persistence for mutation ledger, state, health

The durable adapter MAY also implement mutation ledger, device state and health persistence as additional methods or through a combined adapter class.

For seed simplicity, a single adapter class that handles all four tables is acceptable.

Alternative:

```text
- separate small adapter for mutation ledger;
- separate small adapter for device state memory;
- separate small adapter for endpoint health memory.
```

Choose the smallest structure that preserves boundary clarity.

### 5.3 Adapter recreation pattern for recovery tests

This is the critical test pattern for MU-004:

```text
1. Create DataSource backed by a temp file path.
2. Create H2BaseTopologyRepository(dataSource).
3. Create BaseTopologyService(repository).
4. Perform operations: create topology, add endpoint, save state, save health.
5. Close/discard the DataSource, repository and service instances.
6. Create a NEW DataSource backed by the SAME temp file path.
7. Create a NEW H2BaseTopologyRepository(newDataSource).
8. Create a NEW BaseTopologyService(newRepository).
9. Retrieve topology, version, mutation records, state, health.
10. Assert all invariants.
```

Do NOT reuse adapter or service instances across the recreation boundary.

The point is proving data survives independently of Java object lifecycle.

---

## 6. BaseTopologyRepository Compatibility Rule

MIR-004-D-001:

```text
Do NOT replace BaseTopologyRepository as a breaking change.
```

The durable adapter MUST implement the existing port:

```java
void save(HabitatBaseTopology topology);
Optional<HabitatBaseTopology> findByHabitatId(String habitatId);
Optional<TopologyVersion> findCurrentVersion(String habitatId);
```

`save(...)` on the durable adapter MUST persist durably, not just in memory.

Existing MU-001/MU-002 tests keep using `InMemoryBaseTopologyRepository`.

MU-004 tests use `H2BaseTopologyRepository` or equivalent durable adapter.

Both existing test classes must pass in the same `mvn test` run.

Disallowed:

```text
- remove save(...);
- remove findByHabitatId(...);
- remove findCurrentVersion(...);
- change public signatures silently;
- force InMemoryBaseTopologyRepository to become durable;
- break existing MU-001/MU-002 tests.
```

Any public API break MUST be reported in `implementation-report.md`.

---

## 7. Device State Persistence Rule

`BaseTopologyService` MUST NOT depend on `H2BaseTopologyRepository` as a concrete class.

Allowed approaches:

### Approach A — optional port

Introduce a small `DeviceStateMemoryPort` and inject it through an additional `BaseTopologyService` constructor while preserving existing constructors.

### Approach B — repository extension

Introduce an interface such as `PersistentTopologyMemoryPort` implemented by the durable adapter, while keeping `BaseTopologyRepository` methods intact.

### Approach C — adapter-direct seed tests

For MIR-004 tests, persist/recover device state directly through the durable adapter, while preserving the existing service-local behavior for `InMemoryBaseTopologyRepository`.

Disallowed:

```text
- service checks for H2BaseTopologyRepository concrete type;
- breaking existing BaseTopologyService constructors;
- forcing InMemoryBaseTopologyRepository to become durable;
- coupling SC-C domain/service logic to H2/JDBC classes.
```

---

## 8. BaseTopologySnapshot Envelope Rule

MIR-004-D-002:

```text
BaseTopologySnapshot.topologyVersion is a derived/indexed copy of
BaseTopologySnapshot.topology.topologyVersion.

HabitatBaseTopology.topologyVersion remains the source of truth.
```

The envelope exists for fast version reads and persistence indexing.

Required test proof:

```java
assertThat(snapshot.topologyVersion()).isEqualTo(snapshot.topology().topologyVersion());
```

---

## 9. Mutation Ledger Rule

Mutation memory MUST be explicit.

Preferred source:

```text
TopologyMutationResult returned by addEndpointWithResult(...)
```

Preferred flow:

```text
1. call addEndpointWithResult(...);
2. convert TopologyMutationResult into TopologyMutationRecord;
3. append it through durable adapter method such as appendMutationRecord(...);
4. read it back through findMutationRecords(habitatId).
```

Do NOT require `save(HabitatBaseTopology)` to infer mutation semantics from the aggregate alone.

Allowed fallback:

```text
If repository-local structure makes explicit append awkward, the durable adapter MAY derive a minimal mutation record by comparing previous and new topologyVersion.
```

If fallback is used, report it in `implementation-report.md` as a seed limitation.

Do not implement full event sourcing.

Do not implement transactional outbox.

---

## 10. Architectural Placement Rules

```text
New model types (BaseTopologySnapshot, TopologyMutationRecord)
  → com.sovereign.connect.core.topology.model

New durable adapter (H2BaseTopologyRepository or equivalent)
  → com.sovereign.connect.adapter.persistence

New optional ports/interfaces, if used
  → com.sovereign.connect.core.topology.port

New persistence tests
  → com.sovereign.connect.core.topology.PersistenceMemorySeedTest
     or equivalent test class

SQL schema, if file-based
  → created programmatically by the adapter
     or placed in test resources only

Test configuration
  → test-specific, NOT application-wide
```

Do not create new packages beyond what is needed.

---

## 11. Boundary Constraints

### 11.1 Recovery independence

Recovery MUST NOT require:

```text
SC-B replay
SC-D rediscovery
Hub memory
Projection
Session
Identity
Authority
Policy
Surface state
```

### 11.2 Version consistency

Persisted topology and topologyVersion MUST be atomically consistent.

Invalid states:

```text
new topology with old topologyVersion
old topology with new topologyVersion
```

### 11.3 Non-advancement rule

Device state persistence does NOT advance `topologyVersion`.

Endpoint health persistence does NOT advance `topologyVersion`.

### 11.4 Provider binding rule

Provider refs persist as metadata.

They MUST NOT become canonical identity after recovery.

Required distinction:

```text
providerDeviceId != deviceId
providerEndpointId != endpointId
```

### 11.5 Technology non-finality

Seed storage is not production doctrine.

Report must state:

```text
Seed storage technology:
  <technology>

Production storage decision:
  not decided

ADR required before production:
  ADR-SOV-SC-C-STORAGE-TECH-001
```

---

## 12. Negative Scope

Do NOT implement:

```text
final production database decision
final database vendor doctrine
final ORM doctrine
final schema migration strategy
final normalized relational schema
distributed persistence
multi-node consensus
cloud synchronization
event sourcing framework
transactional outbox
SC-B storage/replay
SC-D rediscovery/cache
SC-D provider-native cache as canonical store
Hub memory
Projection/Effective View cache
Session persistence
Identity persistence
Authority persistence
Policy persistence
Surface preferences
TemporalActs
action terminal results
idempotency persistence
full snapshot query API
historical snapshots
MCP
TL audit
production backup/encryption
```

If any of these becomes necessary, STOP and report as failure signal.
