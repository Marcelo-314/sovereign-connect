package com.sovereign.eib.northbound.dto;

import java.time.Instant;
import java.util.List;

public record NorthboundDiagnosticsViewDto(
        String habitatId,
        String topologyVersion,
        NorthboundTemporalRuntimeStatusViewDto temporalEngineStatus,
        NorthboundMigrationReadinessViewDto migrationReadiness,
        Instant readAt,
        List<ScWarningDto> warnings
) {
}
