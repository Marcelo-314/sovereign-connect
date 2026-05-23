# implementation-report.md - MU-018 Storage Legacy Cleanup

Package version: v0.2.1

## 1. Summary

```text
MU: MU-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001
Branch: fix/sc-c-mir-018-storage-legacy-cleanup
Commit: see git log final commit for this report
Result: PASS
```

## 2. H2 disposition

```text
H2BaseTopologyRepository:
  removed from src/main/java: YES
  retained elsewhere: NO

H2TemporalActRepository:
  moved to src/test/java legacy namespace: YES
  removed entirely: NO

H2 dependency:
  removed: NO
  retained as test scope only: YES
```

## 3. Test migrations

List tests migrated from `H2BaseTopologyRepository` to SQLite production adapters:

```text
- RoomZoneTopologySeedTest
- ConcurrencyIdempotencySeedTest
- TopologyMaterializationSeedTest
- CoreSnapshotQuerySeedTest
- PersistenceBoundaryHardeningTest
- PersistenceMemorySeedTest
- ScCoreKernelHardeningTest
- OutboxLedgerStorageSeedTest
- TopologyPersistenceSpringContextTest
```

List remaining `@Tag("legacy-h2")` tests:

```text
- TemporalActSeedTest
```

If `TemporalActSeedTest` remains legacy-H2, confirm:

```text
TemporalActSeedTest is not production transactional persistence evidence: YES
Reason: It retains H2TemporalActRepository as a legacy temporal seed fixture while ledger/outbox writes use SQLite; this is mixed storage and not a single production SQLite transaction.
```

SQLite-backed temporal production evidence:

```text
- TemporalEngineReviewBlockersTest or equivalent: PASS
- TemporalEngineIndustrialBoundaryTest or equivalent: PASS
- SQLiteTemporalIdBindingTest or equivalent: PASS
- TemporalEngineSpringContextTest or equivalent: PASS
```

## 4. Serialization boundary

```text
src/main/java/com/fasterxml exists: NO
JsonDeserializeAs / JsonSerializeAs in main source: NO
Domain imports Jackson annotations: NO
```

## 5. Architecture tests

Confirm architecture tests added/executed:

```text
noFasterxmlPackageUnderMainSource: PASS
noH2AdaptersUnderMainSource: PASS
noMainSourceReferencesH2Repositories: PASS
noJsonDeserializeAsOrJsonSerializeAsInMainSource: PASS
canonicalDomainPackagesDoNotImportJacksonAnnotations: PASS
h2DependencyIsTestScopeOnlyWhenRetained: PASS
remainingH2TestsAreTaggedLegacyH2: PASS
```

## 6. Out-of-scope confirmation

```text
No Northbound Facade: YES
No EIB / View Composer: YES
No SC-B runtime: YES
No SC-D: YES
No SemanticPayloadCodec implementation: YES
No schema redesign: YES
```

## 7. Validation

```text
mvn test
Tests run: 168
Failures: 0
Errors: 0
Skipped: 0
Build: SUCCESS
```

## 8. Remaining debt

```text
DEBT-018-001:
  ObjectMapper remains in temporal core services until SemanticPayloadCodec or equivalent is designed.
Status: Open / non-blocking for MU-018
```
