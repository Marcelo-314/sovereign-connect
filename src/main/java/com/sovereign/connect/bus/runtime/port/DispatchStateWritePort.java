package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;

import java.util.Optional;
import java.util.UUID;

public interface DispatchStateWritePort {
    DispatchAttempt claim(UUID dispatchRecordId);
    DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState);
    DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef);
    Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId);
}
