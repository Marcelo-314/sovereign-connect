package com.sovereign.connect.core.northbound.runtime;

public record NorthboundMigrationReadinessView(
    String status,
    String source,
    String message
) {
}
