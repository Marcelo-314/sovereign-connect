# codex-prompt.md — MU-018 Storage Legacy Cleanup

Package version: v0.2.1

You are implementing MU-018: SC-C Storage Legacy Cleanup / Serialization Boundary Hardening.

Read first:

```text
docs/mir/mir-018/context.md
docs/mir/mir-018/acceptance-map.md
docs/mir/mir-018/code-surface-audit.md
docs/mir/mir-018/MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001.md
```

## Task

Implement the bounded cleanup exactly as scoped.

Required:

```text
1. Remove H2BaseTopologyRepository from src/main/java.
2. Move H2TemporalActRepository to src/test/java/com/sovereign/connect/adapter/persistence/legacy/.
3. Delete src/main/java/com/fasterxml/** fake Jackson annotation classes.
4. Change H2 dependency in pom.xml to test scope if retained.
5. Migrate all H2BaseTopologyRepository-backed tests to SQLite production adapters.
6. Migrate OutboxLedgerStorageSeedTest to SQLiteScLedgerOutboxRepository.
7. Mark remaining H2 temporal seed tests with @Tag("legacy-h2") and LEGACY-H2-TEMPORAL comment.
8. Add StorageLegacyCleanupArchitectureTest with all required source-level checks.
9. Run mvn test and update implementation-report.md.
```

Critical rules:

```text
- Do not delete H2TemporalActRepository before moving it.
- Do not keep H2BaseTopologyRepository in src/main or src/test.
- Do not keep any src/main/java/com/fasterxml/** package.
- Do not count legacy-H2 TemporalActSeedTest as production temporal persistence evidence.
- Do not implement SemanticPayloadCodec in this MU.
- Do not add Northbound Facade, EIB, View Composer, SC-B, SC-D or dispatcher code.
```

Validation:

```bash
mvn test
```

Expected: 0 failures and 0 errors.
