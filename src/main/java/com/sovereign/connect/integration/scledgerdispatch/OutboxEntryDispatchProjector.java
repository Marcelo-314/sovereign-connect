package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.core.scledger.model.OutboxEntry;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OutboxEntryDispatchProjector {

    private final DispatchRecordIdFactory dispatchRecordIdFactory;
    private final DeliveryLaneToScBusLaneMapper laneMapper;

    public OutboxEntryDispatchProjector(
            DispatchRecordIdFactory dispatchRecordIdFactory,
            DeliveryLaneToScBusLaneMapper laneMapper) {
        this.dispatchRecordIdFactory =
            Objects.requireNonNull(dispatchRecordIdFactory, "dispatchRecordIdFactory is required");
        this.laneMapper =
            Objects.requireNonNull(laneMapper, "laneMapper is required");
    }

    public Optional<DispatchCandidate> project(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        if (entry.habitatId() == null || entry.habitatId().isBlank()) {
            throw new IllegalArgumentException("habitatId is required");
        }
        if (entry.logicalTopic() == null || entry.logicalTopic().isBlank()) {
            throw new IllegalArgumentException("logicalTopic is required");
        }
        Optional<ScBusLane> lane = laneMapper.map(entry);
        if (lane.isEmpty()) {
            return Optional.empty();
        }
        UUID sourceRecordId  = entry.outboxEntryId();
        UUID dispatchRecordId = dispatchRecordIdFactory.fromSourceRecordId(sourceRecordId);
        UUID correlationId    = deterministicUuid("sc-b.correlation:" + sourceRecordId);
        UUID messageId        = deterministicUuid("sc-b.message:" + sourceRecordId);
        return Optional.of(new DispatchCandidate(
            dispatchRecordId,
            sourceRecordId,
            lane.get(),
            entry.logicalTopic(),
            entry.habitatId(),
            correlationId,
            entry.ledgerEntryId(),
            messageId
        ));
    }

    private UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
