package com.sovereign.connect.core.scledger.model;

public enum LedgerRecordClass {
    LEDGER_ONLY,
    EVENT_OUTBOX,
    COMMAND_OUTBOX,
    RESPONSE_OUTBOX,
    DIAGNOSTIC_OUTBOX,
    DELIVERY_OBSERVATION
}
