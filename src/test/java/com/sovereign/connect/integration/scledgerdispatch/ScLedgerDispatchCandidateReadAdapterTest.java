package com.sovereign.connect.integration.scledgerdispatch;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort;
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.port.ScOutboxDispatchReadPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScLedgerDispatchCandidateReadAdapterTest {
    private final DispatchRecordIdFactory factory = new DispatchRecordIdFactory();
    private final OutboxEntryDispatchProjector projector = new OutboxEntryDispatchProjector(
            factory,
            new DeliveryLaneToScBusLaneMapper()
    );

    @Test
    void emitsCandidateWhenNoDispatchRecordExists() {
        Fixture fixture = fixture(entry());
        when(fixture.stateWritePort.currentAttempt(any())).thenReturn(Optional.empty());

        List<DispatchCandidate> candidates = fixture.adapter.pendingCandidates();

        assertThat(candidates).hasSize(1);
    }

    @Test
    void emitsCandidateWhenDispatchStateIsRetryScheduled() {
        OutboxEntry entry = entry();
        Fixture fixture = fixture(entry);
        UUID dispatchRecordId = factory.fromSourceRecordId(entry.outboxEntryId());
        when(fixture.stateWritePort.currentAttempt(dispatchRecordId))
                .thenReturn(Optional.of(attempt(dispatchRecordId, DispatchState.RETRY_SCHEDULED)));

        assertThat(fixture.adapter.pendingCandidates()).hasSize(1);
    }

    @Test
    void skipsCandidateWhenDispatchStateIsClaimed() {
        assertSkippedFor(DispatchState.CLAIMED);
    }

    @Test
    void skipsCandidateWhenDispatchStateIsDispatching() {
        assertSkippedFor(DispatchState.DISPATCHING);
    }

    @Test
    void skipsCandidateWhenDispatchStateIsDispatched() {
        assertSkippedFor(DispatchState.DISPATCHED);
    }

    @Test
    void skipsCandidateWhenDispatchStateIsDeliveryFailed() {
        assertSkippedFor(DispatchState.DELIVERY_FAILED);
    }

    @Test
    void skipsCandidateWhenDispatchStateIsExhausted() {
        assertSkippedFor(DispatchState.EXHAUSTED);
    }

    @Test
    void skipsCandidateWhenDispatchStateIsCancelledBySupersede() {
        assertSkippedFor(DispatchState.CANCELLED_BY_SUPERSEDE);
    }

    @Test
    void pendingCandidatesDoesNotClaimOrTransitionDispatchState() {
        Fixture fixture = fixture(entry());
        when(fixture.stateWritePort.currentAttempt(any())).thenReturn(Optional.empty());

        fixture.adapter.pendingCandidates();

        verify(fixture.stateWritePort, never()).claim(any());
        verify(fixture.stateWritePort, never()).transition(any(), any());
        verify(fixture.stateWritePort, never()).transitionWithEvidence(any(), any(), any());
    }

    @Test
    void repeatedReadsAfterDispatchedDoNotReemitSameCandidate() {
        OutboxEntry entry = entry();
        Fixture fixture = fixture(entry);
        UUID dispatchRecordId = factory.fromSourceRecordId(entry.outboxEntryId());
        when(fixture.stateWritePort.currentAttempt(dispatchRecordId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(attempt(dispatchRecordId, DispatchState.DISPATCHED)));

        assertThat(fixture.adapter.pendingCandidates()).hasSize(1);
        assertThat(fixture.adapter.pendingCandidates()).isEmpty();
    }

    private void assertSkippedFor(DispatchState state) {
        OutboxEntry entry = entry();
        Fixture fixture = fixture(entry);
        UUID dispatchRecordId = factory.fromSourceRecordId(entry.outboxEntryId());
        when(fixture.stateWritePort.currentAttempt(dispatchRecordId))
                .thenReturn(Optional.of(attempt(dispatchRecordId, state)));

        assertThat(fixture.adapter.pendingCandidates()).isEmpty();
    }

    private Fixture fixture(OutboxEntry entry) {
        ScOutboxDispatchReadPort readPort = limit -> List.of(entry);
        DispatchStateWritePort stateWritePort = mock(DispatchStateWritePort.class);
        return new Fixture(
                stateWritePort,
                new ScLedgerDispatchCandidateReadAdapter(readPort, projector, stateWritePort, 10)
        );
    }

    private DispatchAttempt attempt(UUID dispatchRecordId, DispatchState state) {
        return new DispatchAttempt(UUID.randomUUID(), dispatchRecordId, 1, state, Instant.parse("2026-05-29T10:00:00Z"));
    }

    private OutboxEntry entry() {
        return new OutboxEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "habitat-alpha",
                OutboundKind.COMMAND,
                DeliveryLane.EVENT,
                "topic.event",
                "{\"opaque\":true}",
                null,
                "idem-" + UUID.randomUUID(),
                OutboxEntryStatus.PENDING,
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:01Z"),
                null
        );
    }

    private record Fixture(
            DispatchStateWritePort stateWritePort,
            ScLedgerDispatchCandidateReadAdapter adapter
    ) {
    }
}
