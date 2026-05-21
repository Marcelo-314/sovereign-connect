package com.sovereign.connect.core.temporal.application;

public interface TemporalActApplicationPort {
    CreateSignalTemporalActResult createSignalTemporalAct(CreateSignalTemporalActRequest request);

    CancelTemporalActResult cancelTemporalAct(CancelTemporalActRequest request);
}
