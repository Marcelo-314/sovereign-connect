# MANIFEST.md — MU-018 Storage Legacy Cleanup Execution Package

Package version: v0.2.1

Files:

```text
README.md
MIR-SOV-SC-C-STORAGE-LEGACY-CLEANUP-001.md
code-surface-audit.md
context.md
codex-prompt.md
acceptance-map.md
implementation-report-template.md
MANIFEST.md
```

Disposition relative to v0.2.0:

```text
- versioning unified to v0.2.1;
- H2TemporalActRepository instruction corrected from delete+move to move-only;
- architecture tests added for H2 dependency scope and legacy H2 tagging;
- TemporalActSeedTest mixed fixture explicitly marked legacy-only;
- H2BaseTopologyRepository.class assertion rewrites specified;
- SQLite temporal production evidence named;
- "exact code" wording softened to mandatory implementation pattern where appropriate.
```
