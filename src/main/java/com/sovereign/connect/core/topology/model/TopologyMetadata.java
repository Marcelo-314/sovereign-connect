package com.sovereign.connect.core.topology.model;

import java.time.Instant;
import java.util.Objects;

public record TopologyMetadata(
    String schemaVersion,
    Instant lastModified,
    String source,
    String checksum
) {

    public TopologyMetadata {
        Objects.requireNonNull(schemaVersion, "schemaVersion is required");
        Objects.requireNonNull(lastModified, "lastModified is required");
        Objects.requireNonNull(source, "source is required");
    }
}
