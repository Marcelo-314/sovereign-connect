package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.OutboxEntry;

public interface ScOutboxWritePort {
    void appendOutboxEntry(OutboxEntry entry);
}
