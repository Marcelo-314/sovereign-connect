package com.sovereign.connect.core.northbound.runtime;

import com.sovereign.connect.core.northbound.ScNorthboundWarning;

import java.time.Instant;
import java.util.List;

public record NorthboundDiagnosticsView(
    String habitatId,
    String topologyVersion,
    NorthboundTemporalRuntimeStatusView temporalEngineStatus,
    NorthboundMigrationReadinessView migrationReadiness,
    Instant readAt,
    List<ScNorthboundWarning> warnings
) {
    public NorthboundDiagnosticsView {
        warnings = List.copyOf(warnings);
    }
}
