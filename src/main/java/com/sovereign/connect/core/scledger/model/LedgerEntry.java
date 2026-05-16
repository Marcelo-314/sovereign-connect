package com.sovereign.connect.core.scledger.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record LedgerEntry(
    UUID ledgerEntryId,
    String habitatId,
    LedgerRecordClass recordClass,
    String aggregateType,
    String aggregateId,
    SemanticKind semanticKind,
    String payloadType,
    String payloadJson,
    String idempotencyKey,
    Instant recordedAt,
    String metadataJson
) {

    public LedgerEntry {
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(recordClass, "recordClass is required");
        Objects.requireNonNull(aggregateType, "aggregateType is required");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(semanticKind, "semanticKind is required");
        Objects.requireNonNull(payloadType, "payloadType is required");
        Objects.requireNonNull(payloadJson, "payloadJson is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
    }
}
