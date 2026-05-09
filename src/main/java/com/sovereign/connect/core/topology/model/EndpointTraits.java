package com.sovereign.connect.core.topology.model;

public record EndpointTraits(
    boolean addressable,
    boolean independentlyLocatable,
    boolean independentlyOperable,
    boolean stateful,
    boolean readOnly,
    boolean safetyCritical
) {
}
