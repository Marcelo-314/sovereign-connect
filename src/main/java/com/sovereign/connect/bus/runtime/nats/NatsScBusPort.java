package com.sovereign.connect.bus.runtime.nats;

import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.scd.ScdCommand;
import com.sovereign.connect.bus.contract.scd.ScdExecutionResult;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.port.ScBusPort;
import com.sovereign.connect.bus.runtime.port.ScCommandHandler;
import com.sovereign.connect.bus.runtime.port.ScEventHandler;
import com.sovereign.connect.bus.runtime.port.ScResponseHandler;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireCodec;
import com.sovereign.connect.bus.runtime.serialization.ScJsonWireEnvelope;
import com.sovereign.connect.bus.runtime.serialization.ScPayloadTypeRegistry;
import com.sovereign.connect.bus.runtime.serialization.WireEnvelopeValidator;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.Dispatcher;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class NatsScBusPort implements ScBusPort, AutoCloseable {
    private static final String SCHEMA_VERSION = "1.0";
    private static final String TIMER_FIRED_TOPIC = "sc-c.timer-fired";

    private final Connection connection;
    private final NatsSubjectBuilder subjectBuilder;
    private final ScJsonWireCodec wireCodec;
    private final EnvelopeValidationService envelopeValidationService;
    private final CopyOnWriteArrayList<Dispatcher> dispatchers = new CopyOnWriteArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger reconnectEvents = new AtomicInteger(0);
    private final AtomicInteger resubscribedEvents = new AtomicInteger(0);

    public NatsScBusPort(
            Connection connection,
            NatsSubjectBuilder subjectBuilder,
            ScJsonWireCodec wireCodec,
            EnvelopeValidationService envelopeValidationService
    ) {
        this.connection = Objects.requireNonNull(connection, "connection is required");
        this.subjectBuilder = Objects.requireNonNull(subjectBuilder, "subjectBuilder is required");
        this.wireCodec = Objects.requireNonNull(wireCodec, "wireCodec is required");
        this.envelopeValidationService = Objects.requireNonNull(envelopeValidationService,
                "envelopeValidationService is required");
        connection.addConnectionListener(new ConnectionListener() {
            @Override
            public void connectionEvent(Connection conn, Events type) {
                switch (type) {
                    case RECONNECTED -> reconnectEvents.incrementAndGet();
                    case RESUBSCRIBED -> resubscribedEvents.incrementAndGet();
                    default -> { }
                }
            }
        });
    }

    @Override
    public DispatchOutcome publishCommand(ScCommandEnvelope<?> envelope) {
        UUID dispatchRecordId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        try {
            requireOpen();
            envelopeValidationService.validateCommand(envelope);
            if (!(envelope.payload() instanceof ScdCommand)) {
                return unsupportedPayloadType(dispatchRecordId, attemptId);
            }
            ScJsonWireEnvelope wire = wireCodec.toWireEnvelope(
                    envelope,
                    ScPayloadTypeRegistry.COMMAND_SCD_V1,
                    SCHEMA_VERSION
            );
            publishWire(wire, commandSubject(envelope));
            return dispatched(dispatchRecordId, attemptId, envelope);
        } catch (RuntimeException ex) {
            return failed(dispatchRecordId, attemptId, ex);
        }
    }

    @Override
    public DispatchOutcome publishEvent(ScEventEnvelope<?> envelope) {
        UUID dispatchRecordId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        try {
            requireOpen();
            envelopeValidationService.validateEvent(envelope);
            if (!TIMER_FIRED_TOPIC.equals(envelope.routingKey().topic())) {
                return unsupportedPayloadType(dispatchRecordId, attemptId);
            }
            ScJsonWireEnvelope wire = wireCodec.toWireEnvelope(
                    envelope,
                    ScPayloadTypeRegistry.EVENT_TIMER_FIRED_V1,
                    SCHEMA_VERSION
            );
            publishWire(wire, eventSubject(envelope));
            return dispatched(dispatchRecordId, attemptId, envelope);
        } catch (RuntimeException ex) {
            return failed(dispatchRecordId, attemptId, ex);
        }
    }

    @Override
    public DispatchOutcome publishResponse(ScResponseEnvelope<?> envelope) {
        UUID dispatchRecordId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        try {
            requireOpen();
            envelopeValidationService.validateResponse(envelope);
            if (!(envelope.payload() instanceof ScdExecutionResult)) {
                return unsupportedPayloadType(dispatchRecordId, attemptId);
            }
            ScJsonWireEnvelope wire = wireCodec.toWireEnvelope(
                    envelope,
                    ScPayloadTypeRegistry.RESPONSE_SCD_EXECUTION_RESULT_V1,
                    SCHEMA_VERSION
            );
            publishWire(wire, responseSubject(envelope));
            return dispatched(dispatchRecordId, attemptId, envelope);
        } catch (RuntimeException ex) {
            return failed(dispatchRecordId, attemptId, ex);
        }
    }

    @Override
    public void registerCommandHandler(String topicOrRoute, ScCommandHandler handler) {
        requireOpen();
        requireTopic(topicOrRoute);
        Objects.requireNonNull(handler, "handler is required");
    }

    @Override
    public void registerEventHandler(String topicOrRoute, ScEventHandler handler) {
        requireOpen();
        String topic = requireTopic(topicOrRoute);
        Objects.requireNonNull(handler, "handler is required");
        if (!TIMER_FIRED_TOPIC.equals(topic)) {
            throw new IllegalArgumentException("only sc-c.timer-fired event subscriptions are supported by this seed");
        }
        Dispatcher dispatcher = connection.createDispatcher(message -> {
            ScJsonWireEnvelope wire = wireCodec.unmarshal(message.getData());
            WireEnvelopeValidator.validate(wire);
            handler.handle(new ScEventEnvelope<>(wire.metadata(), wire.payload(), wire.routingKey()));
        });
        dispatcher.subscribe(subjectBuilder.buildTimerFiredEventSubscriptionPattern());
        dispatchers.add(dispatcher);
        flush();
    }

    @Override
    public void registerResponseHandler(String topicOrRoute, ScResponseHandler handler) {
        requireOpen();
        requireTopic(topicOrRoute);
        Objects.requireNonNull(handler, "handler is required");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Dispatcher dispatcher : dispatchers) {
                try {
                    connection.closeDispatcher(dispatcher);
                } catch (RuntimeException ignored) {
                    // Dispatcher cleanup is best effort; connection drain is the transport boundary.
                }
            }
            dispatchers.clear();
            Boolean drained = connection.drain(Duration.ofSeconds(10)).get();
            if (!Boolean.TRUE.equals(drained)) {
                throw new IllegalStateException("NATS connection drain timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while draining NATS connection", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to drain NATS connection", ex);
        }
    }

    int getReconnectEventCount() {
        return reconnectEvents.get();
    }

    int getResubscribedEventCount() {
        return resubscribedEvents.get();
    }

    private void publishWire(ScJsonWireEnvelope wire, String subject) {
        WireEnvelopeValidator.validate(wire);
        connection.publish(subject, wireCodec.marshal(wire));
        flush();
    }

    private String commandSubject(ScCommandEnvelope<?> envelope) {
        return subjectBuilder.buildCommandSubject(
                requireRouteValue(envelope.routingKey().habitatId(), "routingKey.habitatId"),
                requireRouteValue(envelope.routingKey().adapterId(), "routingKey.adapterId"),
                requireRouteValue(envelope.routingKey().topic(), "routingKey.topic"),
                commandTargetId(envelope)
        );
    }

    private String eventSubject(ScEventEnvelope<?> envelope) {
        String habitatId = envelope.routingKey().habitatId() != null
                ? envelope.routingKey().habitatId()
                : envelope.routingKey().partitionKey();
        return subjectBuilder.buildTimerFiredEventSubject(habitatId);
    }

    private String responseSubject(ScResponseEnvelope<?> envelope) {
        return subjectBuilder.buildResponseSubject(
                requireRouteValue(envelope.routingKey().habitatId(), "routingKey.habitatId"),
                requireRouteValue(envelope.routingKey().adapterId(), "routingKey.adapterId"),
                envelope.metadata().correlationId()
        );
    }

    private String commandTargetId(ScCommandEnvelope<?> envelope) {
        String deviceId = envelope.routingKey().deviceId();
        if (deviceId != null && !deviceId.isBlank()) {
            return deviceId;
        }
        return requireRouteValue(envelope.routingKey().partitionKey(), "routingKey.partitionKey");
    }

    private Dispatched dispatched(UUID dispatchRecordId, UUID attemptId, ScCommandEnvelope<?> envelope) {
        return new Dispatched(dispatchRecordId, attemptId, envelope.routingKey().topic(), envelope.routingKey().partitionKey());
    }

    private Dispatched dispatched(UUID dispatchRecordId, UUID attemptId, ScEventEnvelope<?> envelope) {
        return new Dispatched(dispatchRecordId, attemptId, envelope.routingKey().topic(), envelope.routingKey().partitionKey());
    }

    private Dispatched dispatched(UUID dispatchRecordId, UUID attemptId, ScResponseEnvelope<?> envelope) {
        return new Dispatched(dispatchRecordId, attemptId, envelope.routingKey().topic(), envelope.routingKey().partitionKey());
    }

    private Failed unsupportedPayloadType(UUID dispatchRecordId, UUID attemptId) {
        return new Failed(
                dispatchRecordId,
                attemptId,
                "UNSUPPORTED_PAYLOAD_TYPE",
                "payload type not resolvable for seed",
                false
        );
    }

    private Failed failed(UUID dispatchRecordId, UUID attemptId, RuntimeException ex) {
        return new Failed(dispatchRecordId, attemptId, "SC_B_NATS_PUBLISH_FAILED", ex.getMessage(), true);
    }

    private String requireTopic(String topicOrRoute) {
        if (topicOrRoute == null || topicOrRoute.isBlank()) {
            throw new IllegalArgumentException("topicOrRoute is required");
        }
        return topicOrRoute;
    }

    private String requireRouteValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("NatsScBusPort is closed");
        }
    }

    private void flush() {
        try {
            connection.flush(Duration.ofSeconds(2));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while flushing NATS connection", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("timed out while flushing NATS connection", ex);
        }
    }
}
