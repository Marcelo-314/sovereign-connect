# acceptance-map.md — MU-018 Storage Legacy Cleanup

Package version: v0.2.1

| AC | Requirement | Evidence |
|---|---|---|
| AC-018-001 | `H2BaseTopologyRepository` removed from `src/main/java`. | `StorageLegacyCleanupArchitectureTest.noH2AdaptersUnderMainSource`; source tree. |
| AC-018-002 | `H2TemporalActRepository` removed from `src/main/java` and moved to test legacy namespace if retained. | source tree; package declaration; architecture tests. |
| AC-018-003 | H2 dependency removed or test scope only. | `h2DependencyIsTestScopeOnlyWhenRetained`; `pom.xml`. |
| AC-018-004 | No `src/main/java/com/fasterxml/**` package. | `noFasterxmlPackageUnderMainSource`. |
| AC-018-005 | No `JsonDeserializeAs` / `JsonSerializeAs` in main source. | `noJsonDeserializeAsOrJsonSerializeAsInMainSource`. |
| AC-018-006 | Remaining H2-backed tests are explicitly legacy. | `remainingH2TestsAreTaggedLegacyH2`; `@Tag("legacy-h2")`; `LEGACY-H2-*` comment. |
| AC-018-007 | H2BaseTopologyRepository-backed topology/core/materialization tests migrated to SQLite production adapters. | migrated tests; no H2BaseTopologyRepository imports. |
| AC-018-008 | Production temporal persistence evidence is SQLite-backed. | report lists SQLite temporal tests, e.g. `TemporalEngineReviewBlockersTest`, `TemporalEngineIndustrialBoundaryTest`, `SQLiteTemporalIdBindingTest`, `TemporalEngineSpringContextTest` or equivalents. |
| AC-018-009 | `TemporalActSeedTest`, if still H2-backed, is legacy-only and not production transactional evidence. | `@Tag("legacy-h2")`; implementation report statement. |
| AC-018-010 | `OutboxLedgerStorageSeedTest` uses `SQLiteScLedgerOutboxRepository`, not H2. | test source; no H2BaseTopologyRepository import. |
| AC-018-011 | Main source does not reference H2 repositories, `jdbc:h2`, `org.h2` or H2 packages. | `noMainSourceReferencesH2Repositories`. |
| AC-018-012 | Canonical domain packages do not import Jackson annotations. | `canonicalDomainPackagesDoNotImportJacksonAnnotations`. |
| AC-018-013 | No broad ObjectMapper/SemanticPayloadCodec refactor is attempted. | diff review; implementation report. |
| AC-018-014 | No Northbound/EIB/VC/SC-B/SC-D code is added. | diff review. |
| AC-018-015 | Full test suite passes. | `mvn test` result. |

Required architecture test methods:

```text
noFasterxmlPackageUnderMainSource
noH2AdaptersUnderMainSource
noMainSourceReferencesH2Repositories
noJsonDeserializeAsOrJsonSerializeAsInMainSource
canonicalDomainPackagesDoNotImportJacksonAnnotations
h2DependencyIsTestScopeOnlyWhenRetained
remainingH2TestsAreTaggedLegacyH2
```

Required final implementation report statements:

```text
- H2 status: removed or test-scope only.
- H2TemporalActRepository status: moved to test legacy namespace or removed.
- List of tests migrated from H2BaseTopologyRepository to SQLite.
- List of remaining @Tag("legacy-h2") tests, if any.
- SQLite temporal tests that count as production temporal persistence evidence.
- Confirmation that TemporalActSeedTest legacy-H2 is not production transactional evidence.
- mvn test result.
```
