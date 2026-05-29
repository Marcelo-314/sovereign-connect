package com.sovereign.connect.bus.runtime.dispatch.model;

public sealed interface DispatchOutcome permits Dispatched, Failed, NoHandler {
}
