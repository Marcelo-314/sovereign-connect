# Implementation Report Template — MU-017 Patch 001 Review Gaps

**Package:** `execution-package-MU-017-review-gaps-patch-001`  
**Version:** `v0.2.1`  
**Implementation branch:** `fix/sc-c-mir-017-normalized-topology-sqlite-persistence`  
**Commit:** `<fill after commit>`  
**Date:** `<fill>`  

---

## 1. Summary

```text
MU-017 patch-001-review-gaps:
  <PASS / PARTIAL / FAIL>
```

Brief summary:

```text
<Describe whether B-01, B-02, B-03, H-04 and governance cleanup were closed.>
```

---

## 2. Review findings closure

| Finding | Status | Evidence |
|---|---|---|
| B-01 located fallback regression | `<CLOSED / OPEN>` | `<classes/tests>` |
| B-02 initial endpoint health loss | `<CLOSED / OPEN>` | `<classes/tests>` |
| B-03 global capability id validation | `<CLOSED / OPEN>` | `<classes/tests>` |
| H-04 replay repository Clock | `<CLOSED / OPEN>` | `<classes/tests>` |
| G-01 working tree/governance cleanup | `<CLOSED / OPEN>` | `<git status / changed files>` |
| H-01 BaseTopologyService.updateDeviceState in-memory | `<DOCUMENTED / FIXED / OPEN>` | `<disposition>` |
| H-02 device_health embedded in devices | `<DOCUMENTED / FIXED / OPEN>` | `<disposition>` |
| H-03 provider_bindings deferred | `<DOCUMENTED / FIXED / OPEN>` | `<disposition>` |

---

## 3. Code changes

List changed files:

```text
<git diff --name-only>
```

Key implementation notes:

```text
Located fallback:
  <describe relation ∪ room_id/zone_id behavior and deduplication>

Endpoint health seed-if-absent:
  <describe INSERT OR IGNORE behavior and every-endpoint row invariant>

Capability validation:
  <describe global set validation across deviceCapabilities + endpoint capabilities>

Clock injection:
  <describe constructor and TopologyPersistenceConfiguration change>
```

---

## 4. Test evidence

Command:

```bash
mvn test
```

Result:

```text
Tests run: <n>
Failures: <n>
Errors: <n>
Skipped: <n>
```

Required patch tests:

```text
findLocatedDevicesFallsBackToDeviceRoomIdWhenRelationMissing: <PASS/FAIL>
findLocatedDevicesFallsBackToDeviceZoneIdWhenRelationMissing: <PASS/FAIL>
findLocatedEndpointsFallsBackToEndpointRoomIdWhenRelationMissing: <PASS/FAIL>
findLocatedEndpointsFallsBackToEndpointZoneIdWhenRelationMissing: <PASS/FAIL>
locatedQueryDeduplicatesRelationAndColumnFallbackMatches: <PASS/FAIL>
initialEndpointHealthRoundTripsThroughSQLiteWithoutExplicitHealthWrite: <PASS/FAIL>
structuralSaveDoesNotOverwriteExistingEndpointHealthInSQLite: <PASS/FAIL>
everyPersistedEndpointGetsEndpointHealthRowIfAbsent: <PASS/FAIL>
duplicateCapabilityIdAcrossOwnersRejectedBeforePersistence: <PASS/FAIL>
materializationDecisionReplayUsesInjectedClock: <PASS/FAIL>
```

---

## 5. Governance / working tree evidence

Paste:

```bash
git status --short
```

Expected:

```text
Only MU-017 patch-relevant code and docs.
No .idea/*.
No unrelated .gitignore changes.
No docs/mir/mir-001 or docs/mir/mir-002 noise.
No #U2014 duplicate artifacts.
No target/*.sqlite runtime artifacts.
```

---

## 6. Scope preservation

Confirm:

```text
No V4 redesign: <yes/no>
No graph database: <yes/no>
No northbound facade: <yes/no>
No EIB / View Composer: <yes/no>
No SC-B / SC-D runtime: <yes/no>
No ActionTemporalPayload / command dispatch: <yes/no>
No outbox dispatcher: <yes/no>
No topology_json authority regression: <yes/no>
```
