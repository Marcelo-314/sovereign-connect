package com.sovereign.connect.core.temporal.application;

import com.sovereign.connect.core.temporal.observation.TemporalActObservation;

public sealed interface CancelTemporalActResult permits
    CancelTemporalActResult.Cancelled,
    CancelTemporalActResult.AlreadyTerminal,
    CancelTemporalActResult.NotFound,
    CancelTemporalActResult.IdempotentReplay,
    CancelTemporalActResult.Rejected,
    CancelTemporalActResult.Failed {

    record Cancelled(TemporalActObservation temporalAct) implements CancelTemporalActResult {
    }

    record AlreadyTerminal(TemporalActObservation act) implements CancelTemporalActResult {
    }

    record NotFound(String habitatId, String id) implements CancelTemporalActResult {
    }

    record IdempotentReplay(TemporalActObservation act) implements CancelTemporalActResult {
    }

    record Rejected(TemporalRequestRejection rejection) implements CancelTemporalActResult {
    }

    record Failed(TemporalRuntimeFailure failure) implements CancelTemporalActResult {
    }
}
