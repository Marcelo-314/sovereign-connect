package com.sovereign.connect.core.temporal.application;

import com.sovereign.connect.core.temporal.observation.TemporalActObservation;

public sealed interface CreateSignalTemporalActResult permits
    CreateSignalTemporalActResult.Accepted,
    CreateSignalTemporalActResult.IdempotentReplay,
    CreateSignalTemporalActResult.Rejected,
    CreateSignalTemporalActResult.Failed {

    record Accepted(TemporalActObservation temporalAct) implements CreateSignalTemporalActResult {
    }

    record IdempotentReplay(TemporalActObservation act) implements CreateSignalTemporalActResult {
    }

    record Rejected(TemporalRequestRejection rejection) implements CreateSignalTemporalActResult {
    }

    record Failed(TemporalRuntimeFailure failure) implements CreateSignalTemporalActResult {
    }
}
