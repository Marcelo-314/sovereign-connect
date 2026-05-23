# context.md — MU-018 Storage Legacy Cleanup / Serialization Boundary Hardening

```text
Package version: v0.2.1
Status: Draft / ready for Codex execution after human review
Baseline: develop after MU-017 closure
Baseline validation expectation: 162 tests, 0 failures, 0 errors, 0 skipped
```

---

## 1. Objective

MU-018 is a bounded cleanup / hardening increment after MU-016 and MU-017.

It removes remaining production-visible H2 persistence adapters and fake Jackson annotation classes from `src/main/java`, while preserving required legacy temporal seed coverage as explicitly marked test-only evidence.

Only these changes are in scope:

```text
1. Remove H2BaseTopologyRepository from src/main/java.
2. Move H2TemporalActRepository from src/main/java to a legacy test fixture package.
3. Delete fake Jackson annotation classes from src/main/java/com/fasterxml/**.
4. Change the H2 dependency from runtime to test scope.
5. Migrate topology/core/materialization/outbox tests from H2BaseTopologyRepository to SQLite production adapters.
6. Mark any remaining H2-backed temporal seed test as @Tag("legacy-h2").
7. Add source-level architecture tests preventing H2/Jackson boundary regressions.
```

Non-goals:

```text
- no Northbound Facade;
- no EIB / View Composer;
- no SC-B runtime;
- no SC-D;
- no schema redesign;
- no graph database;
- no outbox dispatcher;
- no SemanticPayloadCodec extraction;
- no broad ObjectMapper cleanup in temporal core services.
```

---

## 2. Binding decisions

```text
DEC-018-001 — SQLite is the only production persistence path.
DEC-018-002 — H2 may remain only as an explicitly marked legacy test fixture.
DEC-018-003 — src/main/java/com/fasterxml/** is forbidden.
DEC-018-004 — Jackson may remain in adapter/codec packages; canonical domain annotations are forbidden.
DEC-018-005 — DEBT-018-001 remains out of scope: ObjectMapper in temporal core services requires SemanticPayloadCodec.
DEC-018-006 — Strategy for H2 adapters:
  H2BaseTopologyRepository MUST be removed from src/main/java and not moved.
  H2TemporalActRepository MUST be moved to src/test/java legacy namespace.
```

---

## 3. Exact file operations

### 3.1 Delete from `src/main/java`

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
src/main/java/com/fasterxml/jackson/annotation/JsonDeserializeAs.java
src/main/java/com/fasterxml/jackson/annotation/JsonSerializeAs.java
```

### 3.2 Move from `src/main/java` to `src/test/java`

Do **not** delete `H2TemporalActRepository` before moving it.

```text
FROM:
  src/main/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepository.java

TO:
  src/test/java/com/sovereign/connect/adapter/persistence/legacy/H2TemporalActRepository.java
```

Change package declaration:

```java
// FROM
package com.sovereign.connect.adapter.persistence;

// TO
package com.sovereign.connect.adapter.persistence.legacy;
```

### 3.3 Update `pom.xml`

H2 must not be runtime/compile scope.

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

If H2 is removed completely, this AC is also satisfied. For this MU, preferred path is `test` scope because `TemporalActSeedTest` may temporarily retain legacy H2 temporal storage.

---

## 4. Tests and code that must stop referencing `H2BaseTopologyRepository`

All imports, constructors, assertions and type checks involving `H2BaseTopologyRepository` must be removed or replaced.

Known affected tests / likely surfaces:

```text
RoomZoneTopologySeedTest
ConcurrencyIdempotencySeedTest
TopologyMaterializationSeedTest
CoreSnapshotQuerySeedTest
PersistenceBoundaryHardeningTest
PersistenceMemorySeedTest
ScCoreKernelHardeningTest
OutboxLedgerStorageSeedTest
TopologyPersistenceSpringContextTest
```

Mandatory rule:

```text
All assertions that reference H2BaseTopologyRepository.class MUST be removed or replaced by:
  - positive SQLite repository assertions;
  - source-tree architecture tests;
  - Spring context assertions against SQLite production types.
```

Example disposition:

```text
TopologyPersistenceSpringContextTest:
  remove import H2BaseTopologyRepository;
  remove isNotInstanceOf(H2BaseTopologyRepository.class);
  assert positive SQLite beans instead:
    BaseTopologyRepository is SQLiteBaseTopologyRepository
    CoreSnapshotReadPort is SQLiteBaseTopologyRepository
    EndpointHealthWritePort is SQLiteEndpointHealthRepository
    TopologyMaterializationStatePort is SQLiteTopologyMaterializationStateRepository
    MaterializationDecisionReplayPort is SQLiteMaterializationDecisionReplayRepository
```

---

## 5. SQLite fixture pattern for topology / materialization / snapshot tests

The following is a mandatory implementation pattern, not necessarily literal code if existing test fixtures already provide equivalent helpers.

### 5.1 Shared helper

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.adapter.persistence.sqlite.PerConnectionPragmaDataSource;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteBaseTopologyRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteEndpointHealthRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteMaterializationDecisionReplayRepository;
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteTopologyMaterializationStateRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;

@TempDir
Path tempDir;

private DataSource sqliteDataSource(String name) {
    SQLiteDataSource delegate = new SQLiteDataSource();
    delegate.setUrl("jdbc:sqlite:" + tempDir.resolve(name + ".sqlite"));
    DataSource ds = new PerConnectionPragmaDataSource(delegate);
    Flyway.configure()
        .dataSource(ds)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    return ds;
}

private ObjectMapper mapper() {
    return new ObjectMapper().findAndRegisterModules();
}
```

### 5.2 SQLite topology fixture

```java
private record Fixture(
    SQLiteBaseTopologyRepository repository,
    SQLiteEndpointHealthRepository healthRepo,
    SQLiteTopologyMaterializationStateRepository stateRepo,
    SQLiteMaterializationDecisionReplayRepository replayRepo,
    BaseTopologyService service,
    CoreSnapshotQueryService query,
    DefaultTopologyMaterializationService materializer,
    JdbcTemplate jdbc
) {}

private Fixture sqliteFixture(String name) {
    DataSource ds = sqliteDataSource(name);
    ObjectMapper mapper = mapper();
    Clock clock = Clock.systemUTC();
    TransactionTemplate txTemplate = new TransactionTemplate(new DataSourceTransactionManager(ds));

    SQLiteBaseTopologyRepository repository =
        new SQLiteBaseTopologyRepository(ds, mapper, txTemplate, clock);
    SQLiteEndpointHealthRepository healthRepo =
        new SQLiteEndpointHealthRepository(ds, clock);
    SQLiteTopologyMaterializationStateRepository stateRepo =
        new SQLiteTopologyMaterializationStateRepository(ds, mapper, repository, healthRepo, clock);
    SQLiteMaterializationDecisionReplayRepository replayRepo =
        new SQLiteMaterializationDecisionReplayRepository(ds, mapper, clock);

    BaseTopologyService service = new BaseTopologyService(repository, healthRepo, clock);
    CoreSnapshotQueryService query = new CoreSnapshotQueryService(repository, clock);
    DefaultTopologyMaterializationService materializer = new DefaultTopologyMaterializationService(
        service,
        stateRepo,
        adapterInstanceId -> true,
        replayRepo,
        clock
    );

    return new Fixture(repository, healthRepo, stateRepo, replayRepo,
        service, query, materializer, new JdbcTemplate(ds));
}
```

Mapping from old H2 God Object to SQLite split:

| Old H2 role | SQLite replacement |
|---|---|
| BaseTopologyRepository | SQLiteBaseTopologyRepository |
| CoreSnapshotReadPort | SQLiteBaseTopologyRepository |
| EndpointHealthWritePort | SQLiteEndpointHealthRepository |
| TopologyMaterializationStatePort | SQLiteTopologyMaterializationStateRepository |
| MaterializationDecisionReplayPort | SQLiteMaterializationDecisionReplayRepository |
| ScLedgerWritePort / ScOutboxWritePort | SQLiteScLedgerOutboxRepository |

Habitat setup:

```java
f.jdbc().update("INSERT OR IGNORE INTO habitats(habitat_id) VALUES (?)", habitatId);
f.service().createInitialTopology(habitatId, rooms, zones, devices, endpoints);
```

---

## 6. OutboxLedgerStorageSeedTest migration

`OutboxLedgerStorageSeedTest` must not use `H2BaseTopologyRepository` as ledger/outbox adapter.

Use `SQLiteScLedgerOutboxRepository` directly:

```java
import com.sovereign.connect.adapter.persistence.sqlite.SQLiteScLedgerOutboxRepository;

private record LedgerFixture(SQLiteScLedgerOutboxRepository repository, JdbcTemplate jdbc) {}

private LedgerFixture ledgerFixture(String name) {
    DataSource ds = sqliteDataSource(name);
    SQLiteScLedgerOutboxRepository repository = new SQLiteScLedgerOutboxRepository(ds);
    return new LedgerFixture(repository, new JdbcTemplate(ds));
}
```

Ledger/outbox table names remain:

```text
sc_c_ledger_entries
sc_c_outbox_entries
```

---

## 7. TemporalActSeedTest transitional fixture

### 7.1 Accepted transitional state

`TemporalActSeedTest` may remain legacy-H2 if it still depends on `H2TemporalActRepository`.

However:

```text
- H2TemporalActRepository must live in src/test/java/...legacy.
- TemporalActSeedTest must be marked @Tag("legacy-h2").
- The class must contain a LEGACY-H2-TEMPORAL comment.
- It must not count as production temporal persistence evidence.
```

Required class annotation and comment:

```java
import org.junit.jupiter.api.Tag;

@Tag("legacy-h2")
// LEGACY-H2-TEMPORAL: H2TemporalActRepository is retained only as a legacy
// seed fixture. This class is not production persistence evidence.
class TemporalActSeedTest {
    ...
}
```

### 7.2 Mixed H2 + SQLite fixture is legacy-only

If `TemporalActSeedTest` uses:

```text
H2TemporalActRepository for temporal_acts
SQLiteScLedgerOutboxRepository for ledger/outbox
TransactionTemplate over H2 DataSource
```

that is accepted only as a transitional legacy test fixture.

It is **not** production transactional evidence, because H2 temporal rows and SQLite ledger/outbox rows do not participate in the same physical transaction.

Record this explicitly in `implementation-report.md`.

### 7.3 Production temporal persistence evidence

Production temporal persistence evidence must come from SQLite temporal tests, not `TemporalActSeedTest` if it remains legacy-H2.

Expected SQLite-backed evidence includes existing or updated tests such as:

```text
TemporalEngineReviewBlockersTest
TemporalEngineIndustrialBoundaryTest
SQLiteTemporalIdBindingTest
TemporalEngineSpringContextTest
```

If any of these test class names differ in the actual repository, use the equivalent SQLite-backed temporal tests and list them in the implementation report.

---

## 8. Architecture tests to add or update

Create or update:

```text
src/test/java/com/sovereign/connect/architecture/StorageLegacyCleanupArchitectureTest.java
```

The test class must include at least the following test methods. Code may be adapted to existing architecture-test helpers, but the assertions are mandatory.

```java
package com.sovereign.connect.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StorageLegacyCleanupArchitectureTest {

    private final Path mainJava = Path.of("src/main/java");
    private final Path testJava = Path.of("src/test/java");
    private final Path pom = Path.of("pom.xml");

    @Test
    void noFasterxmlPackageUnderMainSource() {
        assertThat(mainJava.resolve("com/fasterxml"))
            .as("src/main/java/com/fasterxml must not exist")
            .doesNotExist();
    }

    @Test
    void noH2AdaptersUnderMainSource() throws IOException {
        try (Stream<Path> paths = Files.walk(mainJava)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.getFileName().toString().startsWith("H2"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("No H2*.java files may exist under src/main/java")
                .isEmpty();
        }
    }

    @Test
    void noMainSourceReferencesH2Repositories() throws IOException {
        try (Stream<Path> paths = Files.walk(mainJava)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p,
                    "H2BaseTopologyRepository",
                    "H2TemporalActRepository",
                    "jdbc:h2:",
                    "org.h2"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("No main source may reference H2 repositories, jdbc:h2 or org.h2")
                .isEmpty();
        }
    }

    @Test
    void noJsonDeserializeAsOrJsonSerializeAsInMainSource() throws IOException {
        try (Stream<Path> paths = Files.walk(mainJava)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> containsAny(p, "JsonDeserializeAs", "JsonSerializeAs"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("JsonDeserializeAs and JsonSerializeAs must not appear in main source")
                .isEmpty();
        }
    }

    @Test
    void canonicalDomainPackagesDoNotImportJacksonAnnotations() throws IOException {
        List<Path> domainRoots = List.of(
            mainJava.resolve("com/sovereign/connect/core/topology/model"),
            mainJava.resolve("com/sovereign/connect/core/topology/port"),
            mainJava.resolve("com/sovereign/connect/core/temporal/model"),
            mainJava.resolve("com/sovereign/connect/core/temporal/port")
        );
        List<String> forbidden = List.of(
            "@JsonProperty", "@JsonDeserialize", "@JsonSerialize",
            "@JsonIgnore", "@JsonInclude", "import com.fasterxml.jackson.annotation"
        );
        for (Path root : domainRoots) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String content = Files.readString(file);
                    for (String banned : forbidden) {
                        assertThat(content)
                            .as("Domain file %s must not contain %s", file, banned)
                            .doesNotContain(banned);
                    }
                }
            }
        }
    }

    @Test
    void h2DependencyIsTestScopeOnlyWhenRetained() throws IOException {
        String xml = Files.readString(pom);
        int h2Index = xml.indexOf("<artifactId>h2</artifactId>");
        if (h2Index < 0) {
            return;
        }
        int depStart = xml.lastIndexOf("<dependency>", h2Index);
        int depEnd = xml.indexOf("</dependency>", h2Index);
        assertThat(depStart).isGreaterThanOrEqualTo(0);
        assertThat(depEnd).isGreaterThan(h2Index);
        String dependencyBlock = xml.substring(depStart, depEnd + "</dependency>".length());
        assertThat(dependencyBlock)
            .as("H2 may be retained only as test scope")
            .contains("<scope>test</scope>")
            .doesNotContain("<scope>runtime</scope>")
            .doesNotContain("<scope>compile</scope>");
    }

    @Test
    void remainingH2TestsAreTaggedLegacyH2() throws IOException {
        if (!Files.exists(testJava)) return;
        try (Stream<Path> paths = Files.walk(testJava)) {
            List<String> violations = paths
                .filter(p -> p.toString().endsWith("Test.java"))
                .filter(p -> containsAny(p,
                    "H2TemporalActRepository",
                    "jdbc:h2:",
                    "org.h2"))
                .filter(p -> !containsAny(p, "@Tag(\"legacy-h2\")", "@Tag('legacy-h2')"))
                .map(Path::toString)
                .toList();
            assertThat(violations)
                .as("Any remaining H2-backed test must be tagged @Tag(\"legacy-h2\")")
                .isEmpty();
        }
    }

    private boolean containsAny(Path file, String... needles) {
        try {
            String content = Files.readString(file);
            for (String needle : needles) {
                if (content.contains(needle)) return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
```

Do not weaken `noMainSourceReferencesH2Repositories`. Do not whitelist `H2BaseTopologyRepository` anywhere.

---

## 9. Acceptance evidence expectations

The implementation report must identify:

```text
1. Whether H2 was removed entirely or retained only as test scope.
2. Whether H2TemporalActRepository was moved to src/test/java legacy namespace.
3. Which tests were migrated from H2BaseTopologyRepository to SQLite.
4. Which tests, if any, remain @Tag("legacy-h2").
5. Which SQLite-backed temporal tests count as production persistence evidence.
6. Confirmation that TemporalActSeedTest, if legacy-H2, is not counted as production persistence evidence.
7. Confirmation that src/main/java/com/fasterxml does not exist.
8. Confirmation that all tests pass.
```

Minimum final validation:

```bash
mvn test
```

Expected result:

```text
0 failures
0 errors
```

Do not claim L4 if test execution is not reported.

---

## 10. DEBT-018-001 — record only

```text
DEBT-018-001:
  TemporalActService, TemporalActApplicationService and TemporalEngineService still use ObjectMapper for semantic payload serialization.
  This requires a SemanticPayloadCodec port or equivalent design before it can be cleaned without leaking implementation detail or breaking payload semantics.
```

This debt is not a blocker for MU-018.

Do not implement `SemanticPayloadCodec` in this MU.
