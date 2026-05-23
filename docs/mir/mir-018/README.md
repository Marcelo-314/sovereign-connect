# MU-018 Execution Package — Storage Legacy Cleanup / Serialization Boundary Hardening

Package version: v0.2.1  
Status: Draft / ready for review before Codex

This package implements a bounded cleanup after MU-016/MU-017.

Primary scope:

```text
- remove H2BaseTopologyRepository from src/main/java;
- move H2TemporalActRepository to test legacy namespace;
- change H2 dependency to test scope if retained;
- remove fake com.fasterxml.jackson annotation classes from main source;
- migrate H2BaseTopologyRepository-backed tests to SQLite fixtures;
- mark remaining H2 temporal seed tests as @Tag("legacy-h2");
- add architecture tests preventing regression.
```

Non-goals:

```text
Northbound Facade, EIB, View Composer, SC-B runtime, SC-D, graph DB,
SemanticPayloadCodec, broad ObjectMapper cleanup, schema redesign.
```

Recommended branch:

```text
fix/sc-c-mir-018-storage-legacy-cleanup
```

Recommended commit:

```text
fix(sc-c): cleanup H2 legacy storage and serialization boundary
```
