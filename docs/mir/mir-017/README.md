# MU-017 Execution Package — Canonical Topology SQLite/Flyway Persistence

Package version: v0.2.1  
Date: 2026-05-22  
MU: MU-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
MIR: MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001  
Operational slot: MU-017

## Purpose

This package descends MIR-017 into an implementation prompt for Codex.

MU-017 replaces seed/H2/in-memory topology persistence as the production path with normalized SQLite/Flyway persistence for SC-C Base Topology.

## Files

```text
docs/mir/mir-017/
  README.md
  MIR-SOV-SC-C-CANONICAL-TOPOLOGY-SQLITE-PERSISTENCE-001.md
  code-surface-audit.md
  context.md
  codex-prompt.md
  acceptance-map.md
  implementation-report-template.md
```

## Critical decision

```text
Normalized SQLite topology tables are authoritative after MU-017.
topology_json MAY remain only as a derived compatibility snapshot/read model.
No external graph database is introduced in MU-017.
```

## Suggested branch

```text
feat/sc-c-mir-017-canonical-topology-sqlite-persistence
```

## Suggested commit

```text
feat(sc-c): add normalized SQLite topology persistence
```


## Review delta v0.2.1

```text
- Internal package version unified to v0.2.1.
- mutation_records retained as the SQLite table name for mutation history.
- capability_id declared habitat-global; owner_kind / owner_id remain ownership metadata.
- Test fixture guidance uses compile-safe constructors and avoids duplicate capability_id.
- Java bodies are mandatory implementation patterns, not literal helper APIs.
```
