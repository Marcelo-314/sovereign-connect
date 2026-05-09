package com.sovereign.connect.core.topology.model;

import java.util.Map;
import java.util.Objects;

public record IdempotencyIdentity(
    String operationKind,
    TopologyTargetRef target,
    Map<String, Object> canonicalParams
) {

    public IdempotencyIdentity {
        Objects.requireNonNull(operationKind, "operationKind is required");
        Objects.requireNonNull(target, "target is required");
        canonicalParams = Map.copyOf(Objects.requireNonNull(canonicalParams, "canonicalParams is required"));
    }
}
