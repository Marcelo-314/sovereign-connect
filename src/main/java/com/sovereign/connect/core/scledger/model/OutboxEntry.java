package com.sovereign.connect.core.scledger.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxEntry(
    UUID outboxEntryId,
    UUID ledgerEntryId,
    String habitatId,
    OutboundKind outboundKind,
    DeliveryLane deliveryLane,
    String logicalTopic,
    String semanticPayloadJson,
    String notificationTargetRef,
    String idempotencyKey,
    OutboxEntryStatus status,
    Instant createdAt,
    Instant updatedAt,
    String metadataJson
) {

    public OutboxEntry {
        Objects.requireNonNull(outboxEntryId, "outboxEntryId is required");
        Objects.requireNonNull(ledgerEntryId, "ledgerEntryId is required");
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(outboundKind, "outboundKind is required");
        Objects.requireNonNull(deliveryLane, "deliveryLane is required");
        Objects.requireNonNull(logicalTopic, "logicalTopic is required");
        Objects.requireNonNull(semanticPayloadJson, "semanticPayloadJson is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
