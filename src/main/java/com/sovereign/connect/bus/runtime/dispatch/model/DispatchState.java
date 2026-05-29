package com.sovereign.connect.bus.runtime.dispatch.model;

public enum DispatchState {
    PENDING,
    CLAIMED,
    DISPATCHING,
    DISPATCHED,
    DELIVERY_FAILED,
    RETRY_SCHEDULED,
    EXHAUSTED,
    CANCELLED_BY_SUPERSEDE
}
