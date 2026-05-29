package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEntryDispatchProjectorTest {
    private final OutboxEntryDispatchProjector projector = new OutboxEntryDispatchProjector(
            new DispatchRecordIdFactory(),
            new DeliveryLaneToScBusLaneMapper()
    );

    @Test
    void outboxEntryIdMapsToSourceRecordId() {
        OutboxEntry entry = entry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event");

        DispatchCandidate candidate = projector.project(entry).orElseThrow();

        assertThat(candidate.sourceRecordId()).isEqualTo(entry.outboxEntryId());
    }

    @Test
    void dispatchRecordIdIsDeterministicForSameOutboxEntry() {
        OutboxEntry entry = entry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event");

        UUID first = projector.project(entry).orElseThrow().dispatchRecordId();
        UUID second = projector.project(entry).orElseThrow().dispatchRecordId();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void dispatchRecordIdIsDistinctFromRawOutboxEntryId() {
        OutboxEntry entry = entry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event");

        assertThat(projector.project(entry).orElseThrow().dispatchRecordId()).isNotEqualTo(entry.outboxEntryId());
    }

    @Test
    void partitionKeyAndLogicalTopicArePreserved() {
        OutboxEntry entry = entry(DeliveryLane.RESPONSE, OutboundKind.COMMAND, "topic.response");

        DispatchCandidate candidate = projector.project(entry).orElseThrow();

        assertThat(candidate.partitionKey()).isEqualTo(entry.habitatId());
        assertThat(candidate.logicalTopic()).isEqualTo(entry.logicalTopic());
        assertThat(candidate.causationId()).isEqualTo(entry.ledgerEntryId());
        assertThat(candidate.correlationId()).isNotNull();
        assertThat(candidate.messageId()).isNotNull();
    }

    @Test
    void commandEventAndResponseLanesMapDirectly() {
        assertThat(projector.project(entry(DeliveryLane.COMMAND, OutboundKind.COMMAND, "topic.command")).orElseThrow().lane())
                .isEqualTo(ScBusLane.COMMAND);
        assertThat(projector.project(entry(DeliveryLane.EVENT, OutboundKind.COMMAND, "topic.event")).orElseThrow().lane())
                .isEqualTo(ScBusLane.EVENT);
        assertThat(projector.project(entry(DeliveryLane.RESPONSE, OutboundKind.COMMAND, "topic.response")).orElseThrow().lane())
                .isEqualTo(ScBusLane.RESPONSE);
    }

    @Test
    void signalNeverMapsToCommand() {
        Optional<DispatchCandidate> candidate = projector.project(
                entry(DeliveryLane.SIGNAL, OutboundKind.TIMER_FIRED_SIGNAL, "sc-c.timer-fired")
        );

        assertThat(candidate).isPresent();
        assertThat(candidate.orElseThrow().lane()).isNotEqualTo(ScBusLane.COMMAND);
    }

    @Test
    void timerFiredSignalOnScopedTopicMapsToEvent() {
        Optional<DispatchCandidate> candidate = projector.project(
                entry(DeliveryLane.SIGNAL, OutboundKind.TIMER_FIRED_SIGNAL, "sc-c.timer-fired")
        );

        assertThat(candidate).isPresent();
        assertThat(candidate.orElseThrow().lane()).isEqualTo(ScBusLane.EVENT);
    }

    @Test
    void nonScopedSignalIsSkipped() {
        assertThat(projector.project(entry(
                DeliveryLane.SIGNAL,
                OutboundKind.TEMPORAL_ACT_CANCELLED_SIGNAL,
                "sc-c.temporal-cancelled"
        ))).isEmpty();
    }

    @Test
    void semanticPayloadJsonIsNotParsedIntoDomainCommand() {
        OutboxEntry entry = new OutboxEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "habitat-alpha",
                OutboundKind.COMMAND,
                DeliveryLane.COMMAND,
                "topic.command",
                "not-json-but-still-opaque",
                null,
                "idem",
                OutboxEntryStatus.PENDING,
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:01Z"),
                null
        );

        assertThat(projector.project(entry)).isPresent();
    }

    private OutboxEntry entry(DeliveryLane lane, OutboundKind kind, String topic) {
        return new OutboxEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "habitat-alpha",
                kind,
                lane,
                topic,
                "{\"opaque\":true}",
                null,
                "idem-" + UUID.randomUUID(),
                OutboxEntryStatus.PENDING,
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:01Z"),
                null
        );
    }
}
