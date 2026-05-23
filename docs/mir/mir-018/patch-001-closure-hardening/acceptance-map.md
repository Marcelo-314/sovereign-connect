# Acceptance Map — MU-018 Patch 001 Closure Hardening

Document ID: AMAP-SOV-SC-C-STORAGE-LEGACY-CLEANUP-PATCH-001
Version: v0.2.1
Status: Draft

---

## AC-P18-001 — Jackson annotations dependency is not explicitly version-skewed

Requirement:

```text
pom.xml MUST NOT declare jackson-annotations with explicit version 2.21.
```

Pass if:

```text
Preferred: jackson-annotations dependency is removed entirely.
Fallback: jackson-annotations remains without explicit version ONLY if compilation fails after removal and the implementation report identifies the concrete source requiring it.
```

Evidence:

```text
pom.xml diff
mvn test
```

---

## AC-P18-002 — Legacy-H2 architecture test cannot false-pass

Requirement:

```text
StorageLegacyCleanupArchitectureTest MUST NOT satisfy remainingH2TestsAreTaggedLegacyH2 by scanning its own source literal.
```

Pass if:

```text
- StorageLegacyCleanupArchitectureTest is excluded from the H2-backed test scan only because it contains H2-detection strings as source text; and
- StorageLegacyCleanupArchitectureTest remains H2-free; and
- the check detects actual @Tag("legacy-h2") annotation on H2-backed test classes/methods.
```

Evidence:

```text
StorageLegacyCleanupArchitectureTest
mvn test
```

---

## AC-P18-003 — Remaining H2-backed tests are explicitly legacy-tagged

Requirement:

```text
Any test that uses jdbc:h2, org.h2, H2TemporalActRepository or adapter.persistence.legacy must be marked @Tag("legacy-h2"). Architecture tests may mention these strings only as scan needles; they MUST NOT import org.h2, instantiate H2 repositories, create jdbc:h2 connections, or become H2-backed tests themselves.
```

Pass if:

```text
Architecture test enforces this rule and passes.
```

Evidence:

```text
StorageLegacyCleanupArchitectureTest
TemporalActSeedTest if still H2-backed
mvn test
```

---

## AC-P18-004 — TemporalActSeedTest legacy classification remains explicit

Requirement:

```text
If TemporalActSeedTest still uses H2TemporalActRepository, it must remain @Tag("legacy-h2") and documented as LEGACY-H2-TEMPORAL.
```

Pass if:

```text
TemporalActSeedTest contains the tag and legacy comment.
```

Evidence:

```text
TemporalActSeedTest
```

---

## AC-P18-005 — No H2 production path regresses

Requirement:

```text
No H2 repository or H2 dependency may return to the production path.
```

Pass if existing MU-018 architecture tests still verify:

```text
- no H2 adapter under src/main/java;
- H2 dependency is test scope only;
- no jdbc:h2 or org.h2 in src/main/java.
```

Evidence:

```text
StorageLegacyCleanupArchitectureTest
pom.xml
mvn test
```

---

## AC-P18-006 — No fake Jackson annotation classes regress

Requirement:

```text
src/main/java/com/fasterxml/** MUST NOT exist.
JsonDeserializeAs / JsonSerializeAs fake classes MUST NOT exist.
```

Pass if existing MU-018 architecture tests still enforce this and pass.

Evidence:

```text
StorageLegacyCleanupArchitectureTest
mvn test
```

---

## AC-P18-007 — Final evidence is clean

Requirement:

```text
Final submitted package must be generated from a clean local branch and must not contain #U2014 duplicate artifacts.
```

Pass if implementation report records:

```bash
git status --short
git branch --show-current
git diff --name-status
git diff --stat
mvn test
```

and there are no accidental `#U2014` duplicate files in the delivery ZIP.

Evidence:

```text
implementation-report.md
final ZIP review
```

---

## AC-P18-008 — Scope remains constrained

Requirement:

```text
Patch must not implement north-facing, EIB, VC, SC-B runtime, SC-D, SemanticPayloadCodec or ObjectMapper cleanup.
```

Pass if code diff is limited to dependency cleanup, architecture test hardening, report update and evidence hygiene.

Evidence:

```text
git diff --name-status
git diff --stat
review
```
