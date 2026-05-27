package com.sovereign.eib.northbound.dto;

public record NorthboundMigrationReadinessViewDto(String status, String source, String message) {
}
