# implementation-report-template.md — MU-018 Storage Legacy Cleanup

Package version: v0.2.1

## 1. Summary

```text
MU: MU-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001
Branch:
Commit:
Result:
```

## 2. H2 disposition

```text
H2BaseTopologyRepository:
  removed from src/main/java: YES/NO
  retained elsewhere: YES/NO

H2TemporalActRepository:
  moved to src/test/java legacy namespace: YES/NO
  removed entirely: YES/NO

H2 dependency:
  removed: YES/NO
  retained as test scope only: YES/NO
```

## 3. Test migrations

List tests migrated from `H2BaseTopologyRepository` to SQLite production adapters:

```text
-
```

List remaining `@Tag("legacy-h2")` tests:

```text
-
```

If `TemporalActSeedTest` remains legacy-H2, confirm:

```text
TemporalActSeedTest is not production transactional persistence evidence: YES/NO
Reason:
```

SQLite-backed temporal production evidence:

```text
- TemporalEngineReviewBlockersTest or equivalent:
- TemporalEngineIndustrialBoundaryTest or equivalent:
- SQLiteTemporalIdBindingTest or equivalent:
- TemporalEngineSpringContextTest or equivalent:
```

## 4. Serialization boundary

```text
src/main/java/com/fasterxml exists: YES/NO
JsonDeserializeAs / JsonSerializeAs in main source: YES/NO
Domain imports Jackson annotations: YES/NO
```

## 5. Architecture tests

Confirm architecture tests added/executed:

```text
noFasterxmlPackageUnderMainSource: PASS/FAIL
noH2AdaptersUnderMainSource: PASS/FAIL
noMainSourceReferencesH2Repositories: PASS/FAIL
noJsonDeserializeAsOrJsonSerializeAsInMainSource: PASS/FAIL
canonicalDomainPackagesDoNotImportJacksonAnnotations: PASS/FAIL
h2DependencyIsTestScopeOnlyWhenRetained: PASS/FAIL
remainingH2TestsAreTaggedLegacyH2: PASS/FAIL
```

## 6. Out-of-scope confirmation

```text
No Northbound Facade: YES/NO
No EIB / View Composer: YES/NO
No SC-B runtime: YES/NO
No SC-D: YES/NO
No SemanticPayloadCodec implementation: YES/NO
No schema redesign: YES/NO
```

## 7. Validation

```text
mvn test
Tests run:
Failures:
Errors:
Skipped:
```

## 8. Remaining debt

```text
DEBT-018-001:
  ObjectMapper remains in temporal core services until SemanticPayloadCodec or equivalent is designed.
Status: Open / non-blocking for MU-018
```
