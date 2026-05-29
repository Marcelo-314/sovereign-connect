package com.sovereign.connect.bus.runtime.port;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;

import java.util.List;

public interface DispatchCandidateReadPort {
    List<DispatchCandidate> pendingCandidates();
}
