package com.sovereign.connect.bus.runtime.inmemory;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDispatchStateRepository implements DispatchStateWritePort {
    private final Map<UUID, DispatchAttempt> attempts = new ConcurrentHashMap<>();

    @Override
    public DispatchAttempt claim(UUID dispatchRecordId) {
        requireDispatchRecordId(dispatchRecordId);
        return attempts.compute(dispatchRecordId, (id, current) -> {
            DispatchState currentState = current == null ? DispatchState.PENDING : current.state();
            if (currentState != DispatchState.PENDING && currentState != DispatchState.RETRY_SCHEDULED) {
                throw new IllegalStateException("dispatch record is not claimable from " + currentState);
            }
            int attemptNumber = current == null ? 1 : current.attemptNumber() + 1;
            return new DispatchAttempt(UUID.randomUUID(), id, attemptNumber, DispatchState.CLAIMED, Instant.now());
        });
    }

    @Override
    public DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState) {
        requireDispatchRecordId(dispatchRecordId);
        if (targetState == DispatchState.CANCELLED_BY_SUPERSEDE) {
            throw new IllegalArgumentException("evidenceRef is required for supersede cancellation");
        }
        return attempts.compute(dispatchRecordId, (id, current) -> transitionCurrent(id, current, targetState));
    }

    @Override
    public DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef) {
        requireDispatchRecordId(dispatchRecordId);
        if (targetState == DispatchState.CANCELLED_BY_SUPERSEDE && (evidenceRef == null || evidenceRef.isBlank())) {
            throw new IllegalArgumentException("evidenceRef is required for supersede cancellation");
        }
        return attempts.compute(dispatchRecordId, (id, current) -> transitionCurrent(id, current, targetState));
    }

    @Override
    public Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId) {
        return Optional.ofNullable(attempts.get(dispatchRecordId));
    }

    private DispatchAttempt transitionCurrent(UUID dispatchRecordId, DispatchAttempt current, DispatchState targetState) {
        if (targetState == null) {
            throw new IllegalArgumentException("targetState is required");
        }
        DispatchState sourceState = current == null ? DispatchState.PENDING : current.state();
        if (!isAllowed(sourceState, targetState)) {
            throw new IllegalStateException("transition " + sourceState + " -> " + targetState + " is not allowed");
        }
        if (current == null) {
            return new DispatchAttempt(UUID.randomUUID(), dispatchRecordId, 0, targetState, Instant.now());
        }
        return new DispatchAttempt(current.attemptId(), dispatchRecordId, current.attemptNumber(), targetState, current.claimedAt());
    }

    private boolean isAllowed(DispatchState sourceState, DispatchState targetState) {
        return sourceState == DispatchState.PENDING && targetState == DispatchState.CLAIMED
                || sourceState == DispatchState.CLAIMED && targetState == DispatchState.DISPATCHING
                || sourceState == DispatchState.DISPATCHING && targetState == DispatchState.DISPATCHED
                || sourceState == DispatchState.DISPATCHING && targetState == DispatchState.DELIVERY_FAILED
                || sourceState == DispatchState.DELIVERY_FAILED && targetState == DispatchState.RETRY_SCHEDULED
                || sourceState == DispatchState.RETRY_SCHEDULED && targetState == DispatchState.CLAIMED
                || sourceState == DispatchState.DELIVERY_FAILED && targetState == DispatchState.EXHAUSTED
                || sourceState == DispatchState.PENDING && targetState == DispatchState.CANCELLED_BY_SUPERSEDE
                || sourceState == DispatchState.CLAIMED && targetState == DispatchState.CANCELLED_BY_SUPERSEDE
                || sourceState == DispatchState.RETRY_SCHEDULED && targetState == DispatchState.CANCELLED_BY_SUPERSEDE;
    }

    private void requireDispatchRecordId(UUID dispatchRecordId) {
        if (dispatchRecordId == null) {
            throw new IllegalArgumentException("dispatchRecordId is required");
        }
    }
}
