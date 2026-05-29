package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;

import java.util.List;
import java.util.UUID;

public interface DispatchObservationPort {
    void record(DispatchObservationRecord observation);
    List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId);
}
