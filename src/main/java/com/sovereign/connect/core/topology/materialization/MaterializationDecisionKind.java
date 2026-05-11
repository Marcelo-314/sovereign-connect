package com.sovereign.connect.core.topology.materialization;

public enum MaterializationDecisionKind {
    ACCEPT_STRUCTURAL_MUTATION,
    ACCEPT_NON_STRUCTURAL_STATE,
    ACCEPT_HEALTH_UPDATE,
    REJECT_DUPLICATE,
    REJECT_INVALID_FACT,
    REJECT_UNAUTHORIZED_ADAPTER,
    NOOP
}
