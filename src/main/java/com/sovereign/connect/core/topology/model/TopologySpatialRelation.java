package com.sovereign.connect.core.topology.model;

import java.time.Instant;
import java.util.Map;

public record TopologySpatialRelation(
    String relationId,
    TopologySpatialRelationKind kind,
    TopologySpatialSubject subject,
    TopologySpatialTarget target,
    boolean primary,
    RelationConfidence confidence,
    SpatialRelationSource source,
    ProviderSpatialRef providerRef,
    Instant observedAt,
    Map<String, String> metadata
) {
}
