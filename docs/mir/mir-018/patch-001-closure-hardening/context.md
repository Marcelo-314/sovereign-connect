# Context — MU-018 Patch 001 Closure Hardening

```text
Version:       v0.2.1
Baseline ZIP:  sovereign-connect-018.zip (168 tests / 0 failures)
Branch:        fix/sc-c-mir-018-storage-legacy-cleanup
```

---

## 0. How to use this file

Read completely before touching any file. Section 2 gives the exact current
code for each blocker. Section 3 gives the exact replacements. Section 4 gives
the verification commands. This is a three-item patch — no new features, no
scope expansion.

---

## 1. Scope

Close exactly three blockers:

```text
B-018-001  jackson-annotations explicit version skew in pom.xml
B-018-002  remainingH2TestsAreTaggedLegacyH2 can false-pass on itself
B-018-003  Evidence ZIP working-tree hygiene
```

Do NOT change:
```text
Any production class or SQLite adapter
TemporalActSeedTest behavior (only preserve existing @Tag and comment)
Any topology or temporal test except StorageLegacyCleanupArchitectureTest
Spring Boot dependencies
```

Do NOT introduce:
```text
Northbound Facade / EIB / SC-B / SC-D / SemanticPayloadCodec / ActionTemporalPayload
H2 back into src/main or runtime scope
com.fasterxml.** back into src/main
```

---

## 2. Exact current code — what must change

### 2.1 pom.xml — current jackson-annotations declaration (B-018-001)

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.21</version>
</dependency>
```

**Why remove entirely (not just version tag):**
Static scan of all `src/main/java` and `src/test/java` files confirms zero usage of
`import com.fasterxml.jackson.annotation.*`, `@JsonProperty`, `@JsonDeserialize`,
`@JsonSerialize`, `@JsonIgnore`, or `@JsonInclude` in the codebase. The dependency
was introduced to support the fake annotation shims that MU-018 deleted. With those
shims gone and no other usage, the dependency has no purpose. Removing it entirely
is the correct resolution.

Spring Boot's dependency management already includes `jackson-annotations` through
`spring-boot-starter` → `jackson-databind` → `jackson-annotations`. No explicit
declaration is required.

### 2.2 StorageLegacyCleanupArchitectureTest — current false-positive test (B-018-002)

File: `src/test/java/com/sovereign/connect/architecture/StorageLegacyCleanupArchitectureTest.java`

Current `remainingH2TestsAreTaggedLegacyH2` method:

```java
@Test
void remainingH2TestsAreTaggedLegacyH2() throws IOException {
    if (!Files.exists(testJava)) {
        return;
    }
    try (Stream<Path> paths = Files.walk(testJava)) {
        List<String> violations = paths
            .filter(path -> path.toString().endsWith("Test.java"))
            .filter(path -> containsAny(path, "H2TemporalActRepository", "jdbc:h2:", "import org.h2"))
            .filter(path -> !containsAny(path, "@Tag(\"legacy-h2\")", "@Tag('legacy-h2')"))
            .map(Path::toString)
            .toList();
        assertThat(violations)
            .as("Any remaining H2-backed test must be tagged @Tag(\"legacy-h2\")")
            .isEmpty();
    }
}
```

**The problem:** `containsAny(path, "@Tag(\"legacy-h2\")")` matches ANY file containing
that string literal — including `StorageLegacyCleanupArchitectureTest.java` itself,
which contains `"@Tag(\"legacy-h2\")"` as a string inside the assertion. The filter
`!containsAny(path, "@Tag(\"legacy-h2\")")` therefore excludes the architecture test
from being flagged as a violation, even though it uses `adapter.persistence.legacy`
in the scan logic (not as an import).

In this specific codebase, `StorageLegacyCleanupArchitectureTest.java` does NOT
actually import `H2TemporalActRepository`, `jdbc:h2:`, or `org.h2` — it only scans
for them. So the false-positive doesn't manifest in the current implementation.
**However**, the test logic is fragile and must be hardened. If in a future patch
someone adds H2 usage to the architecture test file itself (e.g., for a negative test),
the self-exclusion via `@Tag` string would silently mask it.

---

## 3. Exact replacements

### 3.1 pom.xml — remove jackson-annotations dependency block

**Remove this entire block** from `pom.xml` (the full `<dependency>...</dependency>` element):

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-annotations</artifactId>
    <version>2.21</version>
</dependency>
```

After removal, Spring Boot's BOM will continue to provide `jackson-annotations`
transitively at the managed version (currently `2.17.2` via `jackson-databind`).
No explicit declaration is needed.

Run dependency-tree verification after the change to verify whether the dependency still resolves transitively at the Spring Boot managed version.

Linux/macOS:

```bash
mvn dependency:tree | grep jackson-annotations
```

PowerShell:

```powershell
mvn dependency:tree | Select-String jackson-annotations
```

### 3.2 StorageLegacyCleanupArchitectureTest — replace the vulnerable method

Replace the entire `remainingH2TestsAreTaggedLegacyH2()` method with:

```java
@Test
void remainingH2BackedTestsAreTaggedLegacyH2() throws IOException {
    if (!Files.exists(testJava)) {
        return;
    }
    // Exclude this architecture test class from the scan —
    // it contains H2-detection strings as literal source text
    // but is not itself an H2-backed test.
    String selfName = "StorageLegacyCleanupArchitectureTest.java";

    try (Stream<Path> paths = Files.walk(testJava)) {
        List<String> violations = paths
            .filter(path -> path.toString().endsWith("Test.java"))
            .filter(path -> !path.getFileName().toString().equals(selfName))
            .filter(this::isH2BackedTest)
            .filter(path -> !hasLegacyH2Tag(path))
            .map(Path::toString)
            .toList();

        assertThat(violations)
            .as("Every H2-backed test must declare @Tag(\"legacy-h2\")")
            .isEmpty();
    }
}

// A test file is H2-backed if it directly imports or uses H2 at runtime.
// Note: scanning for the TAG string itself is intentionally NOT part of this check.
private boolean isH2BackedTest(Path file) {
    return containsAny(file,
        "import com.sovereign.connect.adapter.persistence.legacy.H2TemporalActRepository",
        "new H2TemporalActRepository",
        "jdbc:h2:",
        "import org.h2");
}

// A test file has the legacy tag if it contains the tag at the class or method level.
// This check is deliberately separate from isH2BackedTest to avoid self-reference.
private boolean hasLegacyH2Tag(Path file) {
    return containsAny(file, "@Tag(\"legacy-h2\")");
}
```

**Why the self-exclusion is correct and safe:**

`StorageLegacyCleanupArchitectureTest` is excluded by filename, not by content.
It does not import `H2TemporalActRepository` or use `jdbc:h2:` — it only passes
those strings to `containsAny()` as search needles. The exclusion is documented
inline with a comment explaining exactly why.

The self-exclusion is valid only because `StorageLegacyCleanupArchitectureTest`
contains H2-detection strings as source text. It MUST remain H2-free: it must not
import `org.h2`, instantiate H2 repositories, create `jdbc:h2` connections, or become
an H2-backed test itself.

If `StorageLegacyCleanupArchitectureTest` ever needs real H2 fixtures, split the
test: keep source-scan architecture checks H2-free, and place any H2-backed behavior
in a separate test class marked `@Tag("legacy-h2")`.

**Method rename:** `remainingH2TestsAreTaggedLegacyH2` → `remainingH2BackedTestsAreTaggedLegacyH2`.
This is a minor hygiene rename — the word "Backed" makes explicit that the check
applies to tests that actively USE H2, not tests that merely mention H2 strings.
If renaming creates a git diff concern, keeping the original name is also acceptable.

**The `containsAny` helper is unchanged** — it already compiles and works correctly.

---

## 4. TemporalActSeedTest — preserve exactly (no change needed)

`TemporalActSeedTest` already has:

```java
@Tag("legacy-h2")
// LEGACY-H2-TEMPORAL: H2TemporalActRepository is retained only as a legacy
// seed fixture. This class is not production persistence evidence.
class TemporalActSeedTest {
```

This satisfies both the architecture test (it contains `@Tag("legacy-h2")`) and
AC-018-006 (explicitly marked legacy). **Do not change this class** for this patch.

---

## 5. Verification sequence

```bash
# Step 1: remove jackson-annotations from pom.xml

# Step 2: verify dependency tree if needed
# Linux/macOS:
mvn dependency:tree | grep jackson-annotations
# PowerShell:
mvn dependency:tree | Select-String jackson-annotations
# Expected: either no direct dependency, or a Spring Boot managed transitive jackson-annotations version.

# Step 3: update StorageLegacyCleanupArchitectureTest

# Step 4: run full suite
mvn test
# Expected: 168 tests, 0 failures, 0 errors, 0 skipped

# Step 5: git evidence
git status --short
git branch --show-current
git diff --name-status
git diff --stat
```

Expected changed files for this patch:
```text
M  pom.xml
M  src/test/java/com/sovereign/connect/architecture/StorageLegacyCleanupArchitectureTest.java
A  docs/mir/mir-018/patch-001-closure-hardening/implementation-report.md
```

No other file should appear in `git diff --name-status`.

---

## 6. Stop conditions

```text
1. mvn dependency:tree does not show jackson-annotations transitively after removal —
   add it back WITHOUT an explicit version: <groupId>...<artifactId>jackson-annotations</artifactId>
   (no <version> tag) and re-run.

2. mvn test fails after pom.xml change — check if any test file imports
   com.fasterxml.jackson.annotation.* directly. If found, add the dependency
   back without version tag.

3. mvn test fails after the architecture test change — check that the renamed
   method doesn't conflict with a cached Surefire run. Run mvn clean test.

4. git diff shows more than 2 source files changed — stop and report which
   extra files changed and why.
```
