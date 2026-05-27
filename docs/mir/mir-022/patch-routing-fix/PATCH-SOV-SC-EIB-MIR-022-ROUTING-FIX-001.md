# PATCH-SOV-SC-EIB-MIR-022-ROUTING-FIX-001

```text
Document ID: PATCH-SOV-SC-EIB-MIR-022-ROUTING-FIX-001
Title:       MU-022 Routing Alignment Patch Package
Version:     v0.1.0
Status:      Execution patch package
MU:          MU-SOV-SC-EFFECTIVE-INTERACTION-BOUNDARY-SEED-001
Slot:        MU-022
```

## Decision

The first MU-022 implementation attempt remains **not L4** until EIB consumes the real SC-C MU-021 HTTP/OpenAPI route surface.

This patch authorizes a focused implementation correction inside the current MU-022 branch.

## Scope

In scope:

```text
- Fix upstream routes in RestClientEibScNorthboundClient.
- Add missing getTopologyVersion.
- Split runtime-state methods into device/endpoint-specific routes.
- Wrap admission API responses in EibResponse<InteractionAdmissionDecision>.
- Update tests to validate the real MU-021 route contract.
- Update implementation report with patch evidence.
```

Out of scope:

```text
- Reopening PDR/SDD/MIR decisions.
- Moving EIB into SC-C runtime.
- Modifying SC-C production code.
- Implementing SSE, gRPC/ConnectRPC, MCP, GraphQL or WebSocket.
- Implementing action/discovery admission.
- Implementing Authority/Policy/Identity system of record.
```

## Required outcome

```text
EIB tests green.
SC-C baseline remains green.
Merged review blockers BLOCKER-001 through BLOCKER-006 resolved.
MU-022 ready for re-review, not automatically L4.
```
