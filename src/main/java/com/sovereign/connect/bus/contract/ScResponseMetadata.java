package com.sovereign.connect.bus.contract;

import java.util.List;
import java.util.UUID;

public record ScResponseMetadata(
        UUID requestMessageId,
        UUID requestId,
        ScResponseKind responseKind,
        boolean terminal,
        boolean retryable,
        String sanitizedReason,
        List<ScResponseWarning> warnings
) {
}
