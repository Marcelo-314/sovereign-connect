# MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001

## SC-C Storage Legacy Cleanup / Serialization Boundary Hardening

Document ID: MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001  
Title: SC-C Storage Legacy Cleanup / Serialization Boundary Hardening  
Version: v0.1.0-draft  
Package version: v0.2.1  
Status: Draft  
Corpus: Sovereign Connect  
Type: MIR  
Plane: SC-C  
Operational Slot: MU-018  
Materialization Unit: MU-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001

---

## 1. Thesis

MU-018 removes production-visible H2 legacy storage and fake Jackson annotation classes after MU-016/MU-017 industrialized temporal runtime and canonical topology persistence on SQLite/Flyway.

The MU hardens the storage boundary without opening a new feature surface.

```text
SQLite/Flyway remains the production persistence authority.
H2 may remain only as explicitly marked legacy test evidence.
Canonical domain source must not contain fake or domain-level Jackson annotation leakage.
```

---

## 2. Goals

```text
G-018-001 Remove H2BaseTopologyRepository from src/main/java.
G-018-002 Move H2TemporalActRepository to src/test/java legacy namespace if retained.
G-018-003 Change H2 dependency to test scope if retained.
G-018-004 Remove src/main/java/com/fasterxml/** fake annotation classes.
G-018-005 Migrate H2BaseTopologyRepository-backed production-evidence tests to SQLite adapters.
G-018-006 Mark remaining H2-backed tests with @Tag("legacy-h2").
G-018-007 Add architecture tests preventing H2/Jackson regression.
```

---

## 3. Non-goals

```text
- Northbound Facade;
- Effective Interaction Boundary;
- View Composer;
- SC-B runtime;
- SC-D runtime;
- graph database;
- schema redesign;
- outbox dispatcher;
- SemanticPayloadCodec extraction;
- broad ObjectMapper cleanup in temporal core services.
```

---

## 4. Acceptance Criteria

```text
AC-018-001 H2BaseTopologyRepository does not exist in src/main/java.
AC-018-002 H2TemporalActRepository does not exist in src/main/java.
AC-018-003 If H2 remains in pom.xml, its scope is test.
AC-018-004 src/main/java/com/fasterxml does not exist.
AC-018-005 JsonDeserializeAs / JsonSerializeAs do not occur in main source.
AC-018-006 Remaining H2-backed tests are tagged @Tag("legacy-h2").
AC-018-007 H2BaseTopologyRepository-backed topology/materialization/snapshot tests are migrated to SQLite production adapters.
AC-018-008 Production temporal persistence evidence is SQLite-backed.
AC-018-009 TemporalActSeedTest, if H2-backed, is explicitly legacy and not production transactional evidence.
AC-018-010 OutboxLedgerStorageSeedTest uses SQLiteScLedgerOutboxRepository.
AC-018-011 Main source has no H2 repository references, jdbc:h2 or org.h2.
AC-018-012 Canonical domain packages do not import Jackson annotations.
AC-018-013 No SemanticPayloadCodec is implemented in this MU.
AC-018-014 No northbound/EIB/VC/SC-B/SC-D code is added.
AC-018-015 Full test suite passes.
```

---

## 5. Explicit debt

```text
DEBT-018-001:
  TemporalActService, TemporalActApplicationService and TemporalEngineService still use ObjectMapper for semantic payload serialization.
  This requires a SemanticPayloadCodec port or equivalent design before it can be cleaned safely.
```

This debt is non-blocking for MU-018.
