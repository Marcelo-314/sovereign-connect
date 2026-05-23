# code-surface-audit.md — MU-018 Storage Legacy Cleanup

Version: v0.2.1
Status: Draft

---

## 1. Audit target

MU-018 targets only storage legacy cleanup and serialization boundary hardening.

Known surfaces:

```text
src/main/java/com/sovereign/connect/adapter/persistence/H2BaseTopologyRepository.java
src/main/java/com/sovereign/connect/adapter/persistence/H2TemporalActRepository.java
src/main/java/com/fasterxml/jackson/annotation/JsonDeserializeAs.java
src/main/java/com/fasterxml/jackson/annotation/JsonSerializeAs.java
pom.xml
H2-backed tests using H2BaseTopologyRepository
TemporalActSeedTest if it uses H2TemporalActRepository
```

---

## 2. Findings

### F-018-001 — H2BaseTopologyRepository is legacy production-visible storage

`H2BaseTopologyRepository` must not remain in `src/main/java` because SQLite/Flyway is the production persistence authority after MU-016/MU-017.

Disposition:

```text
Remove from main source. Do not move to test.
```

### F-018-002 — H2TemporalActRepository may remain only as test legacy fixture

`H2TemporalActRepository` may remain temporarily for legacy TemporalAct seed tests, but only under `src/test/java` and with legacy-H2 tagging on tests that use it.

Disposition:

```text
Move to src/test/java/com/sovereign/connect/adapter/persistence/legacy.
```

### F-018-003 — fake `com.fasterxml.jackson.*` classes are forbidden

Project-defined classes under `src/main/java/com/fasterxml/**` contaminate the source tree and must be removed.

Disposition:

```text
Delete fake JsonDeserializeAs / JsonSerializeAs classes.
```

### F-018-004 — ObjectMapper in temporal services is separate debt

ObjectMapper usage in core temporal services requires a later `SemanticPayloadCodec` or equivalent.

Disposition:

```text
Record DEBT-018-001. Do not fix in MU-018.
```

---

## 3. Required controls

```text
- H2 dependency is test scope only if retained.
- No H2*.java under src/main/java.
- No main source references H2 repositories, jdbc:h2 or org.h2.
- No src/main/java/com/fasterxml.
- Remaining H2-backed tests are @Tag("legacy-h2").
- Production temporal persistence evidence is SQLite-backed.
```
