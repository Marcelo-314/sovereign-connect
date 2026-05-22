# implementation-report.md — MU-017 Canonical Topology SQLite/Flyway Persistence

Package version: v0.2.1-template  
Target: MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
Operational slot: MU-017

---

## 1. Implementation summary

Status:

```text
[PASS / PARTIAL / FAIL]
```

Branch:

```text
<branch>
```

Commit:

```text
<commit hash and subject>
```

Short summary:

```text
<what was implemented>
```

---

## 2. Test evidence

Command:

```text
mvn test
```

Result:

```text
Tests run: <n>
Failures: 0
Errors: 0
Skipped: 0
```

Surefire / relevant reports:

```text
<list report files or summary>
```

---

## 3. Files changed

List changed files. Exclude unrelated noise.

```text
src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql
src/main/java/...
src/test/java/...
docs/mir/mir-017/implementation-report.md
```

Confirm excluded:

```text
.idea/*: absent
.gitignore unrelated changes: absent
docs/mir/mir-001/*: absent
docs/mir/mir-002/*: absent
#U2014 duplicate artifacts: absent
target/*.sqlite: absent
```

---

## 4. V4 migration evidence

Migration file:

```text
src/main/resources/db/migration/V4__sc_c_normalized_topology_persistence.sql
```

Tables created:

```text
topology_versions
topology_metadata
rooms
zones
devices
endpoints
capabilities
topology_spatial_relations
topology_snapshots
mutation_records
device_states
endpoint_health
materialization_decision_replay
```

FK / index notes:

```text
<summary>
```

---

## 5. Normalized persistence evidence

Rows verified for:

```text
rooms: PASS/FAIL
zones: PASS/FAIL
devices: PASS/FAIL
endpoints: PASS/FAIL
capabilities: PASS/FAIL
topology_spatial_relations: PASS/FAIL
```

Authority statement:

```text
Normalized tables are authoritative.
topology_json is compatibility/read model only.
```

---

## 6. Write pattern evidence

Transaction mechanism used:

```text
<TransactionTemplate / @Transactional / single-connection transaction>
```

Delete order implemented:

```text
1. topology_spatial_relations
2. capabilities
3. endpoints
4. devices
5. zones
6. rooms
```

Insert order implemented:

```text
1. topology_versions
2. topology_metadata
3. rooms
4. zones
5. devices
6. endpoints
7. capabilities
8. topology_spatial_relations
9. topology_snapshots
```

State/health preservation:

```text
Structural save does not delete/write device_states: PASS/FAIL
Structural save does not delete/write endpoint_health: PASS/FAIL
```

---

## 7. Hydration evidence

Full topology hydration source:

```text
Normalized tables, not topology_json.
```

Entity-level query source:

```text
findRoom: normalized table
findZone: normalized table
findSpatialRelation: normalized table
findLocatedDevices: normalized table/relation query
findLocatedEndpoints: normalized table/relation query
resolvePrimaryPlacement: normalized relation table
```

Fallbacks:

```text
Endpoint health missing row fallback: <describe>
```

---

## 8. Spring wiring evidence

Beans wired:

```text
BaseTopologyRepository -> SQLiteBaseTopologyRepository
CoreSnapshotReadPort -> SQLiteBaseTopologyRepository
EndpointHealthWritePort -> SQLiteEndpointHealthRepository
TopologyMaterializationStatePort -> SQLiteTopologyMaterializationStateRepository
MaterializationDecisionReplayPort -> SQLiteMaterializationDecisionReplayRepository
BaseTopologyService -> bean
CoreSnapshotQueryService -> bean
TopologyMaterializationService -> bean
```

Production exclusion:

```text
H2BaseTopologyRepository selected in production context: NO
InMemoryBaseTopologyRepository production default: NO
```

---

## 9. Outbox/Ledger disposition

Choose one:

```text
Option A — SQLiteScLedgerOutboxSeedTest added and passed.
Option B — OutboxLedgerStorageSeedTest retained as legacy H2 seed evidence; production SQLite outbox/ledger coverage deferred with rationale.
```

Selected option:

```text
<one option>
```

Evidence:

```text
<test name or rationale>
```

---

## 10. Jackson shim disposition

```text
Option A — dependency alignment resolved; shims removed.
Option B — shims retained as bounded technical debt; rationale documented.
```

Selected option:

```text
<option>
```

Rationale:

```text
<rationale>
```

---

## 11. Non-goal confirmation

Confirm not implemented:

```text
SC-C north-facing facade: not implemented
Effective Interaction Boundary: not implemented
View Composer: not implemented
SC-B runtime: not implemented
NATS / JetStream: not implemented
SC-D runtime: not implemented
ActionTemporalPayload: not implemented
command dispatch: not implemented
outbox dispatcher: not implemented
graph database / Kuzu / Neo4j / AGE: not introduced
```

---

## 12. Acceptance map result

| AC | Status | Evidence |
|---|---|---|
| AC-017-001 | PASS/FAIL | |
| AC-017-002 | PASS/FAIL | |
| AC-017-003 | PASS/FAIL | |
| AC-017-004 | PASS/FAIL | |
| AC-017-005 | PASS/FAIL | |
| AC-017-006 | PASS/FAIL | |
| AC-017-007 | PASS/FAIL | |
| AC-017-008 | PASS/FAIL | |
| AC-017-009 | PASS/FAIL | |
| AC-017-010 | PASS/FAIL | |
| AC-017-011 | PASS/FAIL | |
| AC-017-012 | PASS/FAIL | |
| AC-017-013 | PASS/FAIL | |
| AC-017-014 | PASS/FAIL | |
| AC-017-015 | PASS/FAIL | |
| AC-017-016 | PASS/FAIL | |
| AC-017-017 | PASS/FAIL | |
| AC-017-018 | PASS/FAIL | |
| AC-017-019 | PASS/FAIL | |
| AC-017-020 | PASS/FAIL | |
| AC-017-021 | PASS/FAIL | |
| AC-017-022 | PASS/FAIL | |
| AC-017-023 | PASS/FAIL | |
| AC-017-024 | PASS/FAIL | |
| AC-017-025 | PASS/FAIL | |
| AC-017-026 | PASS/FAIL | |
| AC-017-027 | PASS/FAIL | |
| AC-017-028 | PASS/FAIL | |
| AC-017-029 | PASS/FAIL | |
| AC-017-030 | PASS/FAIL | |

---

## 13. Final disposition

```text
MU-017 implementation disposition:
  <Validated L4 candidate / Requires correction / Failed>
```
