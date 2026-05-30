package com.sovereign.connect.bus.runtime.serialization;

import com.sovereign.connect.bus.contract.ScBusLane;

import java.util.Objects;

public final class WireEnvelopeValidator {

    private WireEnvelopeValidator() {}

    public static void validate(ScJsonWireEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        requirePresent(envelope.envelopeKind(), "envelopeKind");
        requirePresent(envelope.payloadType(), "payloadType");
        requirePresent(envelope.payloadSchemaVersion(), "payloadSchemaVersion");
        requirePresent(envelope.metadata(), "metadata");
        requirePresent(envelope.routingKey(), "routingKey");

        ScPayloadTypeRegistry.requireValidPayloadTypeSyntax(envelope.payloadType());

        requireLaneConsistency(envelope);

        if (envelope.envelopeKind() == ScWireEnvelopeKind.RESPONSE
                && envelope.responseMetadata() == null) {
            throw new IllegalArgumentException(
                "responseMetadata is required for RESPONSE envelopes");
        }
    }

    private static void requireLaneConsistency(ScJsonWireEnvelope envelope) {
        ScBusLane lane = envelope.routingKey().lane();
        ScWireEnvelopeKind kind = envelope.envelopeKind();
        boolean consistent = switch (kind) {
            case COMMAND  -> lane == ScBusLane.COMMAND;
            case EVENT    -> lane == ScBusLane.EVENT;
            case RESPONSE -> lane == ScBusLane.RESPONSE;
        };
        if (!consistent) {
            throw new IllegalArgumentException(
                "envelopeKind " + kind + " is inconsistent with routingKey.lane " + lane);
        }
    }

    private static void requirePresent(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value instanceof String s && s.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
