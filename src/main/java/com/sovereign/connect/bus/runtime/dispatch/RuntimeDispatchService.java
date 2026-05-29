package com.sovereign.connect.bus.runtime.dispatch;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.contract.ScCommandEnvelope;
import com.sovereign.connect.bus.contract.ScEventEnvelope;
import com.sovereign.connect.bus.contract.ScMessageMetadata;
import com.sovereign.connect.bus.contract.ScResponseEnvelope;
import com.sovereign.connect.bus.contract.ScResponseKind;
import com.sovereign.connect.bus.contract.ScResponseMetadata;
import com.sovereign.connect.bus.contract.ScRoutingKey;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.dispatch.model.NoHandler;
import com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort;
import com.sovereign.connect.bus.runtime.port.DispatchObservationPort;
import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort;
import com.sovereign.connect.bus.runtime.port.ScBusPort;
import com.sovereign.connect.bus.runtime.validation.CorrelationValidationService;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import com.sovereign.connect.bus.runtime.validation.RoutingKeyValidationService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RuntimeDispatchService {
    private final ScBusPort busPort;
    private final DispatchCandidateReadPort candidateReadPort;
    private final DispatchStateWritePort stateWritePort;
    private final DispatchObservationPort observationPort;
    private final EnvelopeValidationService envelopeValidationService;
    private final RoutingKeyValidationService routingKeyValidationService;
    private final CorrelationValidationService correlationValidationService;

    public RuntimeDispatchService(
            ScBusPort busPort,
            DispatchCandidateReadPort candidateReadPort,
            DispatchStateWritePort stateWritePort,
            DispatchObservationPort observationPort,
            EnvelopeValidationService envelopeValidationService,
            RoutingKeyValidationService routingKeyValidationService,
            CorrelationValidationService correlationValidationService
    ) {
        this.busPort = busPort;
        this.candidateReadPort = candidateReadPort;
        this.stateWritePort = stateWritePort;
        this.observationPort = observationPort;
        this.envelopeValidationService = envelopeValidationService;
        this.routingKeyValidationService = routingKeyValidationService;
        this.correlationValidationService = correlationValidationService;
    }

    public List<DispatchOutcome> dispatchPending() {
        List<DispatchOutcome> outcomes = new ArrayList<>();
        for (DispatchCandidate candidate : candidateReadPort.pendingCandidates()) {
            outcomes.add(dispatch(candidate));
        }
        return List.copyOf(outcomes);
    }

    public DispatchOutcome dispatch(DispatchCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        DispatchAttempt claimed = stateWritePort.claim(candidate.dispatchRecordId());
        record(claimed, null, "technical claim recorded");
        DispatchAttempt dispatching = stateWritePort.transition(candidate.dispatchRecordId(), DispatchState.DISPATCHING);
        record(dispatching, null, "technical dispatch started");
        DispatchOutcome outcome = normalizeOutcome(candidate, dispatching, publish(candidate));
        if (outcome instanceof Dispatched) {
            DispatchAttempt dispatched = stateWritePort.transition(candidate.dispatchRecordId(), DispatchState.DISPATCHED);
            record(dispatched, null, "technical dispatch completed");
        } else if (outcome instanceof NoHandler noHandler) {
            DispatchAttempt failed = stateWritePort.transition(candidate.dispatchRecordId(), DispatchState.DELIVERY_FAILED);
            record(failed, "NO_HANDLER", noHandler.sanitizedReason());
        } else if (outcome instanceof Failed failedOutcome) {
            DispatchAttempt failed = stateWritePort.transition(candidate.dispatchRecordId(), DispatchState.DELIVERY_FAILED);
            record(failed, failedOutcome.code(), failedOutcome.sanitizedReason());
        }
        return outcome;
    }

    private DispatchOutcome normalizeOutcome(DispatchCandidate candidate, DispatchAttempt attempt, DispatchOutcome outcome) {
        if (outcome instanceof Dispatched dispatched) {
            return new Dispatched(
                    candidate.dispatchRecordId(),
                    attempt.attemptId(),
                    dispatched.topic(),
                    dispatched.partitionKey()
            );
        }
        if (outcome instanceof NoHandler noHandler) {
            return new NoHandler(
                    candidate.dispatchRecordId(),
                    attempt.attemptId(),
                    noHandler.topic(),
                    noHandler.sanitizedReason()
            );
        }
        if (outcome instanceof Failed failed) {
            return new Failed(
                    candidate.dispatchRecordId(),
                    attempt.attemptId(),
                    failed.code(),
                    failed.sanitizedReason(),
                    failed.retryable()
            );
        }
        throw new IllegalStateException("unknown dispatch outcome");
    }

    public DispatchAttempt scheduleRetry(UUID dispatchRecordId) {
        DispatchAttempt retryScheduled = stateWritePort.transition(dispatchRecordId, DispatchState.RETRY_SCHEDULED);
        record(retryScheduled, null, "technical retry scheduled");
        DispatchAttempt claimed = stateWritePort.claim(dispatchRecordId);
        record(claimed, null, "technical retry claimed");
        return claimed;
    }

    public DispatchAttempt exhaust(UUID dispatchRecordId) {
        DispatchAttempt exhausted = stateWritePort.transition(dispatchRecordId, DispatchState.EXHAUSTED);
        record(exhausted, null, "technical delivery exhausted");
        return exhausted;
    }

    public DispatchAttempt cancelBySupersede(UUID dispatchRecordId, String evidenceRef) {
        DispatchAttempt cancelled = stateWritePort.transitionWithEvidence(
                dispatchRecordId,
                DispatchState.CANCELLED_BY_SUPERSEDE,
                evidenceRef
        );
        record(cancelled, "SUPERSEDED", evidenceRef);
        return cancelled;
    }

    private DispatchOutcome publish(DispatchCandidate candidate) {
        ScMessageMetadata metadata = new ScMessageMetadata(
                candidate.messageId(),
                Instant.now(),
                candidate.correlationId(),
                candidate.causationId(),
                null
        );
        correlationValidationService.validate(metadata);
        ScRoutingKey routingKey = new ScRoutingKey(
                candidate.lane(),
                candidate.partitionKey(),
                candidate.logicalTopic(),
                null,
                null,
                null,
                null
        );
        routingKeyValidationService.validate(routingKey);
        return switch (candidate.lane()) {
            case COMMAND -> publishCommand(candidate, metadata, routingKey);
            case EVENT -> publishEvent(candidate, metadata, routingKey);
            case RESPONSE -> publishResponse(candidate, metadata, routingKey);
            case INTERNAL_CONTROL -> new Failed(
                    candidate.dispatchRecordId(),
                    UUID.randomUUID(),
                    "UNSUPPORTED_LANE",
                    "internal control lane is not dispatchable by the abstract seed",
                    false
            );
        };
    }

    private DispatchOutcome publishCommand(DispatchCandidate candidate, ScMessageMetadata metadata, ScRoutingKey routingKey) {
        ScCommandEnvelope<DispatchCandidate> envelope = new ScCommandEnvelope<>(metadata, candidate, routingKey);
        envelopeValidationService.validateCommand(envelope);
        return busPort.publishCommand(envelope);
    }

    private DispatchOutcome publishEvent(DispatchCandidate candidate, ScMessageMetadata metadata, ScRoutingKey routingKey) {
        ScEventEnvelope<DispatchCandidate> envelope = new ScEventEnvelope<>(metadata, candidate, routingKey);
        envelopeValidationService.validateEvent(envelope);
        return busPort.publishEvent(envelope);
    }

    private DispatchOutcome publishResponse(DispatchCandidate candidate, ScMessageMetadata metadata, ScRoutingKey routingKey) {
        ScResponseMetadata responseMetadata = new ScResponseMetadata(
                candidate.causationId(),
                candidate.sourceRecordId(),
                ScResponseKind.EXECUTION_RESULT,
                true,
                false,
                null,
                List.of()
        );
        ScResponseEnvelope<DispatchCandidate> envelope = new ScResponseEnvelope<>(metadata, candidate, routingKey, responseMetadata);
        envelopeValidationService.validateResponse(envelope);
        return busPort.publishResponse(envelope);
    }

    private void record(DispatchAttempt attempt, String code, String reason) {
        observationPort.record(new DispatchObservationRecord(
                UUID.randomUUID(),
                attempt.dispatchRecordId(),
                attempt.attemptId(),
                attempt.state(),
                code,
                reason,
                Instant.now()
        ));
    }
}
