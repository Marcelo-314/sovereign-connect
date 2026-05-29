package com.sovereign.connect.bus.runtime.inmemory;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryDispatchCandidateReadRepository implements DispatchCandidateReadPort {
    private final List<DispatchCandidate> candidates = new CopyOnWriteArrayList<>();

    @Override
    public List<DispatchCandidate> pendingCandidates() {
        return List.copyOf(candidates);
    }

    public void add(DispatchCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        candidates.add(candidate);
    }

    public void clear() {
        candidates.clear();
    }
}
