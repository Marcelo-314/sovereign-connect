# Codex Prompt — MU-018 Patch 001 Closure Hardening

Document ID: PROMPT-SOV-SC-C-STORAGE-LEGACY-CLEANUP-PATCH-001
Version: v0.2.1
Status: Draft
Branch: `fix/sc-c-mir-018-storage-legacy-cleanup`

You are working in the Sovereign Connect repository on the existing MU-018 branch.

## Goal

Close the remaining review blockers for MU-018 Storage Legacy Cleanup / Serialization Boundary Hardening.

Do not reopen MU-018 scope. Do not implement new features.

## Inputs

Read:

```text
docs/mir/mir-018/patch-001-closure-hardening/context.md
docs/mir/mir-018/patch-001-closure-hardening/acceptance-map.md
docs/mir/mir-018/patch-001-closure-hardening/review-report.md
```

## Required changes

### 1. Fix Jackson annotations dependency skew

Inspect `pom.xml`.

Remove the direct `jackson-annotations` dependency block entirely. The current package context records zero real usages of `com.fasterxml.jackson.annotation.*`; the direct dependency is therefore unnecessary after the fake annotation shims were deleted.

Fallback is allowed only if compilation fails after removal and the failing source proves that `jackson-annotations` is required. In that exceptional case, keep `jackson-annotations` without an explicit `<version>` so Spring Boot dependency management controls it, and record the concrete source requiring it in the implementation report.

Do not introduce new Jackson version properties.

### 2. Harden legacy-H2 architecture test

Update `StorageLegacyCleanupArchitectureTest` so the check for remaining H2-backed tests cannot false-pass by scanning its own source literal `@Tag("legacy-h2")`.

The test must enforce that any test using H2 directly, `jdbc:h2:`, `org.h2`, or the legacy `H2TemporalActRepository` is explicitly annotated with `@Tag("legacy-h2")`.

Exclude `StorageLegacyCleanupArchitectureTest.java` from this specific scan only because it contains H2-detection strings as source text. The architecture test itself must remain H2-free: it must not import `org.h2`, instantiate H2 repositories, create `jdbc:h2` connections, or become an H2-backed test.

### 3. Preserve TemporalActSeedTest legacy classification

If `TemporalActSeedTest` still uses `H2TemporalActRepository`, keep it tagged:

```java
@Tag("legacy-h2")
```

and preserve a clear `LEGACY-H2-TEMPORAL` comment stating it is not production transactional persistence evidence.

### 4. Update implementation report

Update the MU-018 implementation report or add a patch-001 section that records:

```text
B-018-001 closed, including whether the preferred removal path was used or why the exceptional BOM-managed fallback was necessary.
B-018-002 closed.
B-018-003 addressed by clean local status / packaging hygiene.
```

Include local command outputs:

```bash
git status --short
git branch --show-current
git diff --name-status
git diff --stat
mvn test
```

## Required tests

Run:

```bash
mvn test
```

Ensure all tests pass.

## Forbidden changes

Do not implement:

```text
Northbound Facade
EIB
View Composer
SC-B runtime
SC-D
SemanticPayloadCodec
ObjectMapper cleanup in core temporal services
TemporalAct Profile B
ActionTemporalPayload
command dispatch
outbox dispatcher
```

Do not reintroduce:

```text
H2 under src/main/java
H2 runtime/compile dependency
src/main/java/com/fasterxml/**
JsonDeserializeAs / JsonSerializeAs fake annotation classes
```

## Commit message

Use:

```text
fix(sc-c): harden MU-018 legacy cleanup checks
```
