package com.sovereign.connect.core.scledger.model;

public enum OutboxEntryStatus {
    PENDING,
    CLAIMED,
    DISPATCHED,
    DISPATCH_FAILED,
    RETRY_WAIT,
    DEAD_LETTERED,
    SUPPRESSED
}
