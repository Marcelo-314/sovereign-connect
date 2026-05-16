package com.sovereign.connect.core.scledger.model;

public enum OutboundKind {
    TIMER_FIRED_SIGNAL,
    TEMPORAL_ACT_CANCELLED_SIGNAL,
    TEMPORAL_ACT_MISFIRED_DIAGNOSTIC,
    COMMAND
}
