# acceptance-map.md — MU-017 Canonical Topology SQLite/Flyway Persistence

Package version: v0.2.1  
Target: MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
Operational slot: MU-017

---

## Acceptance level target

```text
Target acceptance: L4
Definition: implementation attempted, local tests pass, evidence recorded.
```

---

## Acceptance criteria

| AC | Requirement | Verification |
|---|---|---|
| AC-017-001 | Flyway V4 exists with exact name `V4__sc_c_normalized_topology_persistence.sql`. | Source inspection + migration test |
| AC-017-002 | V4 creates normalized topology tables. | `sqliteNormalizedTopologyFlywayMigrationCreatesRequiredTables` |
| AC-017-003 | Rooms persist as rows, not only `topology_json`. | `roomsPersistAsRowsNotOnlyTopologyJson` |
| AC-017-004 | Zones persist as rows, not only `topology_json`. | `zonesPersistAsRowsNotOnlyTopologyJson` |
| AC-017-005 | Devices persist as rows, not only `topology_json`. | `devicesPersistAsRowsNotOnlyTopologyJson` |
| AC-017-006 | Endpoints persist as rows, not only `topology_json`. | `endpointsPersistAsRowsNotOnlyTopologyJson` |
| AC-017-007 | Capabilities persist as rows with owner mapping and habitat-global capability_id uniqueness. | `capabilitiesPersistAsRowsNotOnlyTopologyJson` |
| AC-017-008 | Spatial relations persist as rows. | `spatialRelationsPersistAsRowsNotOnlyTopologyJson` |
| AC-017-009 | `topology_json` is compatibility snapshot, not authority. | `topologyJsonIsCompatibilitySnapshotNotAuthority` |
| AC-017-010 | `save(...)` uses one transaction. | Test evidence or implementation report source evidence |
| AC-017-011 | Structural delete order prevents FK violations. | `baseTopologyRowsPersistAndHydrateThroughSQLite` + source evidence |
| AC-017-012 | Structural insert order prevents FK violations. | `baseTopologyRowsPersistAndHydrateThroughSQLite` + source evidence |
| AC-017-013 | `save(...)` does not delete or overwrite device state. | `structuralSaveDoesNotOverwriteDeviceStateInSQLite` if implemented, or combined state test |
| AC-017-014 | `save(...)` does not delete or overwrite endpoint health. | `structuralSaveDoesNotOverwriteEndpointHealthInSQLite` |
| AC-017-015 | Entity-level queries do not require full `topology_json` deserialization. | `findRoomDoesNotRequireTopologyJsonFallback`, `findEndpointDoesNotRequireTopologyJsonFallback` |
| AC-017-016 | Full topology hydration round-trips from normalized rows. | `baseTopologyRowsPersistAndHydrateThroughSQLite` |
| AC-017-017 | CoreSnapshotReadPort reads topology from SQLite normalized persistence. | `coreSnapshotQueryReadsTopologyFromSQLite` |
| AC-017-018 | Endpoint health persists through SQLite endpoint_health. | `endpointHealthRoundTripsThroughSQLite` |
| AC-017-019 | Materialization decision replay persists through SQLite. | `materializationDecisionReplayRoundTripsThroughSQLite` |
| AC-017-020 | Duplicate fact replay uses SQLite replay repository. | `materializationDuplicateFactReplayUsesSQLite` |
| AC-017-021 | Room/Zone/LOCATED_IN queries round-trip through SQLite. | `roomZoneLocatedInQueriesRoundTripThroughSQLite` |
| AC-017-022 | Mutation records persist and recover through SQLite. | `mutationRecordsPersistAndRecoverThroughSQLite` |
| AC-017-023 | Production Spring context wires SQLite topology ports/services. | `springContextWiresTopologyPortsToSQLiteRepositories` |
| AC-017-024 | H2BaseTopologyRepository is not selected in production context. | `h2BaseTopologyRepositoryIsNotSelectedInProductionContext` |
| AC-017-025 | InMemoryBaseTopologyRepository is not production default. | `inMemoryBaseTopologyRepositoryIsNotProductionDefault` |
| AC-017-026 | Outbox/Ledger production SQLite path has direct coverage or legacy H2 evidence is explicitly marked. | `sqliteScLedgerOutboxRepositoryPersistsLedgerAndOutbox` or implementation report disposition |
| AC-017-027 | Temporal runtime SQLite stack does not regress. | Full `mvn test`; existing temporal tests pass |
| AC-017-028 | No graph DB or external persistence authority is added. | dependency scan + implementation report |
| AC-017-029 | No north-facing facade, EIB, VC, SC-B runtime or SC-D runtime is implemented. | source scan + implementation report |
| AC-017-030 | Working tree contains only MU-017 scoped changes. | implementation report changed-file list |

---

## Required test scenarios

```text
sqliteNormalizedTopologyFlywayMigrationCreatesRequiredTables
springContextWiresTopologyPortsToSQLiteRepositories
h2BaseTopologyRepositoryIsNotSelectedInProductionContext
inMemoryBaseTopologyRepositoryIsNotProductionDefault
baseTopologyRowsPersistAndHydrateThroughSQLite
roomsPersistAsRowsNotOnlyTopologyJson
zonesPersistAsRowsNotOnlyTopologyJson
devicesPersistAsRowsNotOnlyTopologyJson
endpointsPersistAsRowsNotOnlyTopologyJson
capabilitiesPersistAsRowsNotOnlyTopologyJson
spatialRelationsPersistAsRowsNotOnlyTopologyJson
coreSnapshotQueryReadsTopologyFromSQLite
findRoomDoesNotRequireTopologyJsonFallback
findEndpointDoesNotRequireTopologyJsonFallback
structuralSaveDoesNotOverwriteEndpointHealthInSQLite
endpointHealthRoundTripsThroughSQLite
materializationDecisionReplayRoundTripsThroughSQLite
materializationDuplicateFactReplayUsesSQLite
roomZoneLocatedInQueriesRoundTripThroughSQLite
mutationRecordsPersistAndRecoverThroughSQLite
topologyJsonIsCompatibilitySnapshotNotAuthority
sqliteScLedgerOutboxRepositoryPersistsLedgerAndOutbox
```

If exact names differ, implementation report must map each scenario to the concrete test method.

---

## Evidence required in implementation report

```text
- mvn test result
- final test count
- V4 migration file path and table list
- repository classes created
- Spring configuration class created
- FK delete/insert order implemented
- normalized hydration implemented
- topology_json compatibility-only rule verified
- H2/InMemory production exclusion verified
- outbox/ledger SQLite test or legacy evidence disposition
- Jackson shim disposition
- working tree hygiene evidence
```
