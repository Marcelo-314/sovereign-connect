package com.sovereign.connect.bus;

import com.sovereign.connect.bus.contract.ScBusLane;
import com.sovereign.connect.bus.runtime.dispatch.RuntimeDispatchService;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchCandidate;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchOutcome;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.dispatch.model.Dispatched;
import com.sovereign.connect.bus.runtime.dispatch.model.Failed;
import com.sovereign.connect.bus.runtime.dispatch.model.NoHandler;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryDispatchCandidateReadRepository;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryDispatchObservationRepository;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryDispatchStateRepository;
import com.sovereign.connect.bus.runtime.inmemory.InMemoryScBusPort;
import com.sovereign.connect.bus.runtime.validation.CorrelationValidationService;
import com.sovereign.connect.bus.runtime.validation.EnvelopeValidationService;
import com.sovereign.connect.bus.runtime.validation.RoutingKeyValidationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeDispatchServiceTest {
    @Test
    void happyPathTransitionsPendingClaimedDispatchingDispatched() {
        Fixture fixture = fixture();
        fixture.busPort.registerCommandHandler(ScBusTestSupport.TOPIC, envelope -> { });
        DispatchCandidate candidate = ScBusTestSupport.candidate(ScBusLane.COMMAND);
        DispatchOutcome outcome = fixture.service.dispatch(candidate);
        assertThat(outcome).isInstanceOf(Dispatched.class);
        assertThat(((Dispatched) outcome).dispatchRecordId()).isEqualTo(candidate.dispatchRecordId());
        assertThat(fixture.stateRepository.currentAttempt(candidate.dispatchRecordId()).orElseThrow().state()).isEqualTo(DispatchState.DISPATCHED);
        assertThat(fixture.observationRepository.observationsFor(candidate.dispatchRecordId()))
                .extracting(observation -> observation.state())
                .containsExactly(DispatchState.CLAIMED, DispatchState.DISPATCHING, DispatchState.DISPATCHED);
    }

    @Test
    void noHandlerOutcomeBecomesTechnicalDeliveryFailed() {
        Fixture fixture = fixture();
        DispatchCandidate candidate = ScBusTestSupport.candidate(ScBusLane.COMMAND);
        DispatchOutcome outcome = fixture.service.dispatch(candidate);
        assertThat(outcome).isInstanceOf(NoHandler.class);
        assertThat(fixture.stateRepository.currentAttempt(candidate.dispatchRecordId()).orElseThrow().state()).isEqualTo(DispatchState.DELIVERY_FAILED);
    }

    @Test
    void handlerFailureTransitionsDeliveryFailedThenExhausted() {
        Fixture fixture = fixture();
        fixture.busPort.registerEventHandler(ScBusTestSupport.TOPIC, envelope -> {
            throw new IllegalStateException("not delivered");
        });
        DispatchCandidate candidate = ScBusTestSupport.candidate(ScBusLane.EVENT);
        assertThat(fixture.service.dispatch(candidate)).isInstanceOf(Failed.class);
        assertThat(fixture.service.exhaust(candidate.dispatchRecordId()).state()).isEqualTo(DispatchState.EXHAUSTED);
    }

    @Test
    void retryReusesDispatchRecordIdAndCreatesNewAttempt() {
        Fixture fixture = fixture();
        DispatchCandidate candidate = ScBusTestSupport.candidate(ScBusLane.COMMAND);
        fixture.service.dispatch(candidate);
        DispatchAttempt failedAttempt = fixture.stateRepository.currentAttempt(candidate.dispatchRecordId()).orElseThrow();
        DispatchAttempt retryAttempt = fixture.service.scheduleRetry(candidate.dispatchRecordId());
        assertThat(retryAttempt.dispatchRecordId()).isEqualTo(candidate.dispatchRecordId());
        assertThat(retryAttempt.attemptId()).isNotEqualTo(failedAttempt.attemptId());
        assertThat(retryAttempt.attemptNumber()).isEqualTo(2);
        assertThat(retryAttempt.state()).isEqualTo(DispatchState.CLAIMED);
    }

    @Test
    void supersedeCancellationRequiresEvidence() {
        Fixture fixture = fixture();
        UUID dispatchRecordId = UUID.randomUUID();
        assertThatThrownBy(() -> fixture.service.cancelBySupersede(dispatchRecordId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(fixture.service.cancelBySupersede(dispatchRecordId, "ledger:suppressed").state())
                .isEqualTo(DispatchState.CANCELLED_BY_SUPERSEDE);
    }

    @Test
    void dispatchPendingReadsCandidatesFromPort() {
        Fixture fixture = fixture();
        fixture.busPort.registerCommandHandler(ScBusTestSupport.TOPIC, envelope -> { });
        fixture.candidateRepository.add(ScBusTestSupport.candidate(ScBusLane.COMMAND));
        List<DispatchOutcome> outcomes = fixture.service.dispatchPending();
        assertThat(outcomes).singleElement().isInstanceOf(Dispatched.class);
    }

    @Test
    void responseCandidateDispatchesThroughResponseLane() {
        Fixture fixture = fixture();
        fixture.busPort.registerResponseHandler(ScBusTestSupport.TOPIC, envelope -> { });
        assertThat(fixture.service.dispatch(ScBusTestSupport.childCandidate(ScBusLane.RESPONSE))).isInstanceOf(Dispatched.class);
    }

    @Test
    void semanticBoundariesAreNotEncodedAsDispatchStates() {
        assertThat(DispatchState.DISPATCHED.name()).doesNotContain("SUCCESS");
        assertThat(DispatchState.EXHAUSTED.name()).doesNotContain("FAILURE");
        assertThat(DispatchState.CLAIMED.name()).doesNotContain("ACCEPTED");
    }

    private Fixture fixture() {
        InMemoryScBusPort busPort = new InMemoryScBusPort();
        InMemoryDispatchCandidateReadRepository candidateRepository = new InMemoryDispatchCandidateReadRepository();
        InMemoryDispatchStateRepository stateRepository = new InMemoryDispatchStateRepository();
        InMemoryDispatchObservationRepository observationRepository = new InMemoryDispatchObservationRepository();
        RuntimeDispatchService service = new RuntimeDispatchService(
                busPort,
                candidateRepository,
                stateRepository,
                observationRepository,
                new EnvelopeValidationService(),
                new RoutingKeyValidationService(),
                new CorrelationValidationService()
        );
        return new Fixture(busPort, candidateRepository, stateRepository, observationRepository, service);
    }

    private record Fixture(
            InMemoryScBusPort busPort,
            InMemoryDispatchCandidateReadRepository candidateRepository,
            InMemoryDispatchStateRepository stateRepository,
            InMemoryDispatchObservationRepository observationRepository,
            RuntimeDispatchService service
    ) {
    }
}
