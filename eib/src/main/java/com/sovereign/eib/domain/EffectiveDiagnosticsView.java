package com.sovereign.eib.domain;

import java.time.Instant;
import java.util.List;

public record EffectiveDiagnosticsView(
        String habitatRef,
        String topologyVersion,
        String migrationReadinessStatus,
        String migrationReadinessSource,
        String temporalEngineStatus,
        Instant readAt,
        List<EibWarning> warnings
) {
}
