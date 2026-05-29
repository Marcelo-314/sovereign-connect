package com.sovereign.connect.integration.scledgerdispatch;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class DispatchRecordIdFactory {
    private static final String PREFIX = "sc-b.dispatch:";

    public UUID fromSourceRecordId(UUID sourceRecordId) {
        Objects.requireNonNull(sourceRecordId, "sourceRecordId is required");
        return UUID.nameUUIDFromBytes(
            (PREFIX + sourceRecordId).getBytes(StandardCharsets.UTF_8));
    }
}
