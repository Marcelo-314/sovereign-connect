package com.sovereign.connect.core.scledger.port;

import com.sovereign.connect.core.scledger.model.OutboxEntry;
import java.util.List;

public interface ScOutboxDispatchReadPort {
    List<OutboxEntry> findDispatchableEntries(int limit);
}
