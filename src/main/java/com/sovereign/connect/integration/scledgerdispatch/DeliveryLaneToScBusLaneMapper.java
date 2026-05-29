package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;

import java.util.Objects;
import java.util.Optional;

public final class DeliveryLaneToScBusLaneMapper {
    // Verified against TemporalEngineService.java:233 - exact string
    static final String TIMER_FIRED_TOPIC = "sc-c.timer-fired";

    public Optional<ScBusLane> map(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        return switch (entry.deliveryLane()) {
            case COMMAND  -> Optional.of(ScBusLane.COMMAND);
            case EVENT    -> Optional.of(ScBusLane.EVENT);
            case RESPONSE -> Optional.of(ScBusLane.RESPONSE);
            case SIGNAL   -> mapSignal(entry);
        };
    }

    private Optional<ScBusLane> mapSignal(OutboxEntry entry) {
        if (entry.outboundKind() == OutboundKind.TIMER_FIRED_SIGNAL
                && TIMER_FIRED_TOPIC.equals(entry.logicalTopic())) {
            return Optional.of(ScBusLane.EVENT);
        }
        return Optional.empty();
    }
}
