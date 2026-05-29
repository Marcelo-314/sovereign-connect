package com.sovereign.connect.bus.runtime.inmemory;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchObservationRecord;
import com.sovereign.connect.bus.runtime.port.DispatchObservationPort;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryDispatchObservationRepository implements DispatchObservationPort {
    private final List<DispatchObservationRecord> observations = new CopyOnWriteArrayList<>();

    @Override
    public void record(DispatchObservationRecord observation) {
        if (observation == null) {
            throw new IllegalArgumentException("observation is required");
        }
        observations.add(observation);
    }

    @Override
    public List<DispatchObservationRecord> observationsFor(UUID dispatchRecordId) {
        return observations.stream()
                .filter(observation -> observation.dispatchRecordId().equals(dispatchRecordId))
                .toList();
    }
}
