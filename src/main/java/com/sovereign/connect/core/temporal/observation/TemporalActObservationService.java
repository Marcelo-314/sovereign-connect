package com.sovereign.connect.core.temporal.observation;

import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.port.TemporalActReadPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TemporalActObservationService implements TemporalActObservationPort {

    private final TemporalActReadPort readPort;

    public TemporalActObservationService(TemporalActReadPort readPort) {
        this.readPort = Objects.requireNonNull(readPort, "readPort is required");
    }

    @Override
    public List<TemporalActObservation> listActive(String habitatId) {
        return readPort.listActive(habitatId).stream().map(this::toObservation).toList();
    }

    @Override
    public Optional<TemporalActObservation> findById(String habitatId, String temporalActId) {
        return readPort.findById(habitatId, temporalActId).map(this::toObservation);
    }

    @Override
    public List<TemporalActObservation> listTerminal(String habitatId, int maxResults) {
        return readPort.listTerminal(habitatId, maxResults).stream().map(this::toObservation).toList();
    }

    @Override
    public List<TemporalActObservation> listMisfired(String habitatId, int maxResults) {
        return readPort.listMisfired(habitatId, maxResults).stream().map(this::toObservation).toList();
    }

    private TemporalActObservation toObservation(TemporalAct act) {
        SignalTemporalPayload payload = act.payload() instanceof SignalTemporalPayload signalPayload ? signalPayload : null;
        return new TemporalActObservation(
            act.temporalActId(),
            act.habitatId(),
            act.status(),
            act.dueAt(),
            payload == null ? "UNKNOWN" : "SIGNAL",
            payload == null ? null : payload.label(),
            payload == null ? null : payload.signalKind(),
            act.notificationTargetRef(),
            act.createdByRef().value(),
            act.createdAt(),
            act.updatedAt(),
            act.firedAt(),
            act.terminalAt(),
            act.terminalReason()
        );
    }
}
