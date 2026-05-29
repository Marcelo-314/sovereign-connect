package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ScBusTestSupport {
    static final String TOPIC = "adapter.command.set";
    static final String PARTITION = "endpoint.light.main";

    private ScBusTestSupport() {
    }

    static ScMessageMetadata rootMetadata() {
        UUID messageId = UUID.randomUUID();
        return new ScMessageMetadata(messageId, Instant.parse("2026-05-29T12:00:00Z"), messageId, null, "tv-1");
    }

    static ScMessageMetadata childMetadata() {
        return new ScMessageMetadata(UUID.randomUUID(), Instant.parse("2026-05-29T12:00:00Z"), UUID.randomUUID(), UUID.randomUUID(), "tv-1");
    }

    static ScRoutingKey routing(ScBusLane lane) {
        return new ScRoutingKey(lane, PARTITION, TOPIC, "habitat-1", "adapter-1", "device.opaque/with:chars", PARTITION);
    }

    static ScCommandEnvelope<TestCommandPayload> commandEnvelope() {
        return new ScCommandEnvelope<>(rootMetadata(), new TestCommandPayload("on"), routing(ScBusLane.COMMAND));
    }

    static ScEventEnvelope<TestEventPayload> eventEnvelope() {
        return new ScEventEnvelope<>(rootMetadata(), new TestEventPayload("changed"), routing(ScBusLane.EVENT));
    }

    static ScResponseEnvelope<TestResponsePayload> responseEnvelope() {
        return new ScResponseEnvelope<>(
                childMetadata(),
                new TestResponsePayload("ok"),
                routing(ScBusLane.RESPONSE),
                new ScResponseMetadata(UUID.randomUUID(), UUID.randomUUID(), ScResponseKind.EXECUTION_RESULT, true, false, null, List.of())
        );
    }

    static DispatchCandidate candidate(ScBusLane lane) {
        UUID messageId = UUID.randomUUID();
        return new DispatchCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                lane,
                TOPIC,
                "partition-1",
                messageId,
                null,
                messageId
        );
    }

    static DispatchCandidate childCandidate(ScBusLane lane) {
        return new DispatchCandidate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                lane,
                TOPIC,
                "partition-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );
    }
}
