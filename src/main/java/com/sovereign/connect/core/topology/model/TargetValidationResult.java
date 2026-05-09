package com.sovereign.connect.core.topology.model;

public enum TargetValidationResult {
    VALID,
    VALID_AFTER_REVALIDATION,
    TARGET_NOT_FOUND,
    CAPABILITY_MISMATCH,
    TOPOLOGY_VERSION_CONFLICT
}
