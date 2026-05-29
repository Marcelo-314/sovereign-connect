package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchCandidateReadPort;
import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.port.ScOutboxDispatchReadPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ScLedgerDispatchCandidateReadAdapter implements DispatchCandidateReadPort {

    private final ScOutboxDispatchReadPort outboxReadPort;
    private final OutboxEntryDispatchProjector projector;
    private final DispatchStateWritePort dispatchStateWritePort;
    private final int limit;

    public ScLedgerDispatchCandidateReadAdapter(
            ScOutboxDispatchReadPort outboxReadPort,
            OutboxEntryDispatchProjector projector,
            DispatchStateWritePort dispatchStateWritePort,
            int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.outboxReadPort =
            Objects.requireNonNull(outboxReadPort, "outboxReadPort is required");
        this.projector =
            Objects.requireNonNull(projector, "projector is required");
        this.dispatchStateWritePort =
            Objects.requireNonNull(dispatchStateWritePort, "dispatchStateWritePort is required");
        this.limit = limit;
    }

    @Override
    public List<DispatchCandidate> pendingCandidates() {
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (OutboxEntry entry : outboxReadPort.findDispatchableEntries(limit)) {
            projector.project(entry)
                .filter(this::isEmittable)
                .ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    // Read-only check - MUST NOT claim or transition
    private boolean isEmittable(DispatchCandidate candidate) {
        Optional<DispatchAttempt> current =
            dispatchStateWritePort.currentAttempt(candidate.dispatchRecordId());
        if (current.isEmpty()) {
            return true;
        }
        return current.get().state() == DispatchState.RETRY_SCHEDULED;
    }
}
