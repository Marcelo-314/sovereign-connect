# Review Report — MU-018 Storage Legacy Cleanup / Serialization Boundary Hardening

Document ID: REVIEW-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001
Version: v0.2.1
Status: Draft
Scope: Technical review of `sovereign-connect-018.zip`
Reviewed branch: `fix/sc-c-mir-018-storage-legacy-cleanup`
Observed HEAD: `d014ffa fix(sc-c): cleanup H2 legacy storage and serialization boundary`

---

## 1. Dictamen

```text
MU-018 implementation: FUNCTIONAL PASS with closure blockers.
Validated L4: not yet as submitted.
```

The implementation materially closes the intended cleanup scope:

- `H2BaseTopologyRepository` is removed from `src/main/java`.
- `H2TemporalActRepository` is moved to `src/test/java/.../legacy`.
- H2 is retained only with Maven `test` scope.
- fake `com.fasterxml.jackson.annotation.JsonDeserializeAs` and `JsonSerializeAs` classes are removed from main source.
- topology/materialization/core tests were migrated to SQLite fixtures.
- `TemporalActSeedTest` is explicitly marked `@Tag("legacy-h2")` and documented as legacy-only.
- Surefire evidence included in the ZIP reports `168 tests, 0 failures, 0 errors, 0 skipped`.

However, I would not close MU-018 formally until three items are addressed:

1. `jackson-annotations` is still explicitly versioned and misaligned with the Spring Boot managed Jackson stack.
2. `remainingH2TestsAreTaggedLegacyH2` is vulnerable to false positives because it searches for `@Tag("legacy-h2")` as raw text.
3. The submitted ZIP has working-tree / filename noise under Linux extraction, including the historical `#U2014` duplicates / em-dash path drift.

---

## 2. Positive findings

### F-018-001 — H2 topology production adapter removed

`H2BaseTopologyRepository` is deleted from `src/main/java`. No main-source references to `H2BaseTopologyRepository`, `H2TemporalActRepository`, `jdbc:h2:` or `org.h2` were found.

Status: CLOSED.

### F-018-002 — H2 temporal adapter demoted to test legacy fixture

`H2TemporalActRepository` was moved to:

```text
src/test/java/com/sovereign/connect/adapter/persistence/legacy/H2TemporalActRepository.java
```

`TemporalActSeedTest` imports it from the legacy namespace and is marked:

```java
@Tag("legacy-h2")
```

with a `LEGACY-H2-TEMPORAL` comment.

Status: CLOSED.

### F-018-003 — H2 dependency no longer runtime scope

`pom.xml` changes H2 from runtime to test scope:

```xml
<dependency>
  <groupId>com.h2database</groupId>
  <artifactId>h2</artifactId>
  <scope>test</scope>
</dependency>
```

Status: CLOSED.

### F-018-004 — Fake Jackson annotation classes removed

The following main-source files are deleted:

```text
src/main/java/com/fasterxml/jackson/annotation/JsonDeserializeAs.java
src/main/java/com/fasterxml/jackson/annotation/JsonSerializeAs.java
```

No `src/main/java/com/fasterxml/**` tree remains.

Status: CLOSED.

### F-018-005 — SQLite-backed regression fixtures introduced

`SQLiteTestSupport` centralizes production-path SQLite/Flyway fixtures for topology, materialization, state, replay and ledger/outbox testing. This is an improvement over scattered H2 setup and aligns tests with the production storage path.

Status: CLOSED.

---

## 3. Closure blockers

### B-018-001 — Direct `jackson-annotations` version remains explicit and misaligned

`pom.xml` still declares:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-annotations</artifactId>
  <version>2.21</version>
</dependency>
```

The Surefire classpath in the submitted evidence shows Spring Boot-managed Jackson artifacts at `2.17.2`, while `jackson-annotations` is resolved as `2.21`. That introduces a Jackson version skew.

This is contrary to the cleanup direction: MU-018 removes fake Jackson annotations and should not introduce or preserve an unmanaged annotations version unless there is a concrete usage. Static search found no `import com.fasterxml.jackson.annotation` in main source.

Required fix:

```text
Preferred: remove the direct jackson-annotations dependency entirely if unused.
Fallback: keep jackson-annotations but remove the explicit <version> and let Spring Boot dependency management control it.
```

Severity: BLOCKER for formal closure.

### B-018-002 — Legacy-H2 architecture test can false-pass

`remainingH2TestsAreTaggedLegacyH2` scans all `*Test.java` files and accepts a file if the raw source contains the string:

```text
@Tag("legacy-h2")
```

This means `StorageLegacyCleanupArchitectureTest` can satisfy its own check because it contains that literal string inside the assertion logic, even though it is not actually annotated.

Required fix:

```text
- Exclude StorageLegacyCleanupArchitectureTest from this specific scan; or
- implement a stricter check that detects a real class-level or method-level @Tag("legacy-h2") annotation near the test declaration; and
- continue to require TemporalActSeedTest to be explicitly tagged.
```

Severity: BLOCKER for AC-018-006 enforcement quality, but not a functional runtime blocker.

### B-018-003 — Submitted ZIP still has governance/working-tree noise

Running `git status --short` in the extracted ZIP shows many modified files due to extraction/line-ending/path issues. When ignoring end-of-line whitespace, the only semantic diff is the deletion of two old em-dash-named files, while untracked `#U2014` duplicates remain visible.

Observed historical noise:

```text
docs/mir/mir-001/... — Acceptance Map.md        deleted under extracted path
docs/mir/mir-001/... — Implementation Report.md deleted under extracted path
docs/mir/mir-001/... #U2014 Acceptance Map.md   untracked duplicate
docs/mir/mir-001/... #U2014 Implementation Report.md untracked duplicate
```

Required fix before formal archive/merge evidence:

```text
- Verify Windows `git status --short` on the branch is clean after commit.
- Ensure no #U2014 duplicate artifacts are included in final delivery ZIP.
- If the repository intentionally still tracks em-dash-named legacy docs, do not let ZIP encoding transform them into #U2014 duplicates.
```

Severity: GOVERNANCE BLOCKER for final L4 closure package.

---

## 4. Non-blocking observations

### O-018-001 — Historical docs still reference H2

Many old MIR packages still mention `H2BaseTopologyRepository`. This is acceptable as historical evidence and should not be rewritten in MU-018. The architecture tests correctly constrain main/test source behavior rather than historical documentation.

### O-018-002 — `TemporalActSeedTest` remains mixed-storage legacy evidence

The test remains H2-backed for temporal act persistence while ledger/outbox production evidence is covered by SQLite-backed tests. This is acceptable because it is now explicitly tagged as legacy and the implementation report states it is not production transactional persistence evidence.

### O-018-003 — DEBT-018-001 remains correctly open

`ObjectMapper` remains in temporal core services. This should remain non-blocking for MU-018 and should be handled later through a `SemanticPayloadCodec` or equivalent port if needed.

---

## 5. Evidence snapshot

Included Surefire reports indicate:

```text
Tests run: 168
Failures: 0
Errors: 0
Skipped: 0
```

Key test evidence:

```text
StorageLegacyCleanupArchitectureTest: 7 tests PASS
TemporalActSeedTest: 26 tests PASS
SQLiteTopologyPersistenceTest: 20 tests PASS
TemporalEngineReviewBlockersTest: 25 tests PASS
OutboxLedgerStorageSeedTest: 13 tests PASS
```

I could not rerun `mvn test` in this environment because Maven is not installed and the repository does not include `mvnw`.

---

## 6. Required patch plan

```text
P1. Remove or BOM-manage jackson-annotations.
P2. Harden remainingH2TestsAreTaggedLegacyH2 against false positives.
P3. Produce a final clean ZIP / branch status with no #U2014 duplicate artifacts.
```

Recommended branch remains:

```text
fix/sc-c-mir-018-storage-legacy-cleanup
```

Recommended commit for the patch:

```text
fix(sc-c): harden MU-018 legacy cleanup checks
```

---

## 7. Final verdict

```text
MU-018 is technically on the right path and most implementation goals are closed.
Do not declare Validated L4 yet.
A small patch is required before formal closure.
```
