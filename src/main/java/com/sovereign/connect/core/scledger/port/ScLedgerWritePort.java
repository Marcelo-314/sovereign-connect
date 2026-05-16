package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.LedgerEntry;

public interface ScLedgerWritePort {
    void appendLedgerEntry(LedgerEntry entry);
}
