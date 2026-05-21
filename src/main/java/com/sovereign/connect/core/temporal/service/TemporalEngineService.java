package com.sovereign.connect.core.temporal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.scledger.model.DeliveryLane;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.OutboundKind;
import com.sovereign.connect.core.scledger.model.OutboxEntry;
import com.sovereign.connect.core.scledger.model.OutboxEntryStatus;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.scledger.port.ScOutboxWritePort;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.port.TemporalActReadPort;
import com.sovereign.connect.core.temporal.port.TemporalActWritePort;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TemporalEngineService {

    private final TemporalActWritePort writePort;
    private final TemporalActReadPort readPort;
    private final ScLedgerWritePort ledgerPort;
    private final ScOutboxWritePort outboxPort;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TemporalRecoveryObservationPort recoveryObservationPort;
    private final TemporalEngineHealth health;

    public TemporalEngineService(
        TemporalActWritePort writePort,
        TemporalActReadPort readPort,
        ScLedgerWritePort ledgerPort,
        ScOutboxWritePort outboxPort,
        TransactionTemplate txTemplate,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this(writePort, readPort, ledgerPort, outboxPort, txTemplate, objectMapper, clock, TemporalRecoveryObservationPort.noOp(), null);
    }

    public TemporalEngineService(
        TemporalActWritePort writePort,
        TemporalActReadPort readPort,
        ScLedgerWritePort ledgerPort,
        ScOutboxWritePort outboxPort,
        TransactionTemplate txTemplate,
        ObjectMapper objectMapper,
        Clock clock,
        TemporalRecoveryObservationPort recoveryObservationPort
    ) {
        this(writePort, readPort, ledgerPort, outboxPort, txTemplate, objectMapper, clock, recoveryObservationPort, null);
    }

    public TemporalEngineService(
        TemporalActWritePort writePort,
        TemporalActReadPort readPort,
        ScLedgerWritePort ledgerPort,
        ScOutboxWritePort outboxPort,
        TransactionTemplate txTemplate,
        ObjectMapper objectMapper,
        Clock clock,
        TemporalRecoveryObservationPort recoveryObservationPort,
        TemporalEngineHealth health
    ) {
        this.writePort = Objects.requireNonNull(writePort, "writePort is required");
        this.readPort = Objects.requireNonNull(readPort, "readPort is required");
        this.ledgerPort = Objects.requireNonNull(ledgerPort, "ledgerPort is required");
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort is required");
        this.txTemplate = Objects.requireNonNull(txTemplate, "txTemplate is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.recoveryObservationPort = Objects.requireNonNull(recoveryObservationPort, "recoveryObservationPort is required");
        this.health = health;
    }

    public void pollDueOnce(String habitatId, Instant now) {
        pollDueOnce(habitatId, now, Integer.MAX_VALUE);
    }

    public void pollDueOnce(String habitatId, Instant now, int maxDueActsPerCycle) {
        for (TemporalAct act : readPort.findDue(habitatId, now, maxDueActsPerCycle)) {
            if (act.payload() == null) {
                failUnknownPayload(habitatId, act.temporalActId(), now);
                incrementFailed();
                continue;
            }
            fireDueTemporalActOnce(habitatId, act.temporalActId(), now);
        }
    }

    public void fireDueTemporalActOnce(String habitatId, String temporalActId, Instant now) {
        Objects.requireNonNull(now, "now is required");
        txTemplate.executeWithoutResult(status -> {
            int updated = writePort.markFiredIfDueAndNonTerminal(habitatId, temporalActId, now);
            if (updated == 0) {
                incrementSkipped();
                return;
            }
            TemporalAct act = readPort.findById(habitatId, temporalActId)
                .orElseThrow(() -> new IllegalStateException("act disappeared after fire"));
            if (act.payload() == null) {
                throw new IllegalStateException("act fired with unknown payload kind: " + temporalActId);
            }
            LedgerEntry firedEntry = firedLedgerEntry(act, now);
            ledgerPort.appendLedgerEntry(firedEntry);
            outboxPort.appendOutboxEntry(timerFiredOutboxEntry(act, firedEntry.ledgerEntryId(), now));
            incrementFired();
        });
    }

    public void classifyMisfires(String habitatId, Instant recoveryNow) {
        classifyMisfires(habitatId, recoveryNow, Integer.MAX_VALUE);
    }

    public TemporalRecoveryResult classifyMisfires(String habitatId, Instant recoveryNow, int maxRecoveryBatch) {
        UUID recoveryRunId = recoveryObservationPort.startRun("STARTUP", recoveryNow);
        List<TemporalAct> overdue = readPort.findNonTerminalDueBefore(habitatId, recoveryNow, maxRecoveryBatch);
        int misfired = 0;
        int failed = 0;
        for (TemporalAct act : overdue) {
            if (act.payload() == null) {
                failUnknownPayload(habitatId, act.temporalActId(), recoveryNow);
                incrementFailed();
                recoveryObservationPort.recordFinding(
                    recoveryRunId,
                    "ERROR",
                    "TEMPORAL_ACT",
                    "UNKNOWN_PAYLOAD_KIND",
                    "TEMPORAL_ACT",
                    act.temporalActId(),
                    "Temporal act quarantined because payload kind is unknown",
                    true,
                    recoveryNow
                );
                failed++;
                continue;
            }
            txTemplate.executeWithoutResult(status -> {
                int updated = writePort.markMisfiredIfDueAndNonTerminal(
                    habitatId,
                    act.temporalActId(),
                    recoveryNow,
                    recoveryNow
                );
                if (updated == 1) {
                    ledgerPort.appendLedgerEntry(misfiredLedgerEntry(act, recoveryNow));
                    recoveryObservationPort.recordFinding(
                        recoveryRunId,
                        "WARN",
                        "TEMPORAL_ACT",
                        "MISFIRED",
                        "TEMPORAL_ACT",
                        act.temporalActId(),
                        "Temporal act marked MISFIRED during recovery",
                        true,
                        recoveryNow
                    );
                }
            });
            misfired++;
        }
        boolean complete = overdue.size() < maxRecoveryBatch;
        TemporalRecoveryResult result = new TemporalRecoveryResult(misfired, failed, complete);
        recoveryObservationPort.completeRun(recoveryRunId, complete ? "COMPLETED" : "PARTIAL", result, recoveryNow);
        return result;
    }

    private void failUnknownPayload(String habitatId, String temporalActId, Instant now) {
        txTemplate.executeWithoutResult(status -> {
            writePort.markFailed(habitatId, temporalActId, "unknown payload kind", now);
            ledgerPort.appendLedgerEntry(failedLedgerEntry(habitatId, temporalActId, now));
        });
    }

    private void incrementFired() {
        if (health != null) {
            health.incrementFired();
        }
    }

    private void incrementFailed() {
        if (health != null) {
            health.incrementFailed(1);
        }
    }

    private void incrementSkipped() {
        if (health != null) {
            health.incrementSkipped();
        }
    }

    private LedgerEntry firedLedgerEntry(TemporalAct act, Instant now) {
        return new LedgerEntry(
            UUID.randomUUID(),
            act.habitatId(),
            LedgerRecordClass.EVENT_OUTBOX,
            "TEMPORAL_ACT",
            act.temporalActId(),
            SemanticKind.TIMER_FIRED,
            "TimerFired",
            writeJson(Map.of(
                "temporalActId", act.temporalActId(),
                "habitatId", act.habitatId(),
                "firedAt", now.toString(),
                "notificationTargetRef", act.notificationTargetRef()
            )),
            "temporal-act-fired:" + act.temporalActId(),
            now,
            null
        );
    }

    private OutboxEntry timerFiredOutboxEntry(TemporalAct act, UUID ledgerEntryId, Instant now) {
        SignalTemporalPayload payload = (SignalTemporalPayload) act.payload();
        return new OutboxEntry(
            UUID.randomUUID(),
            ledgerEntryId,
            act.habitatId(),
            OutboundKind.TIMER_FIRED_SIGNAL,
            DeliveryLane.SIGNAL,
            "sc-c.timer-fired",
            writeJson(Map.of(
                "temporalActId", act.temporalActId(),
                "habitatId", act.habitatId(),
                "firedAt", now.toString(),
                "label", payload.label()
            )),
            act.notificationTargetRef(),
            "timer-fired-signal:" + act.temporalActId(),
            OutboxEntryStatus.PENDING,
            now,
            now,
            null
        );
    }

    private LedgerEntry misfiredLedgerEntry(TemporalAct act, Instant now) {
        return new LedgerEntry(
            UUID.randomUUID(),
            act.habitatId(),
            LedgerRecordClass.LEDGER_ONLY,
            "TEMPORAL_ACT",
            act.temporalActId(),
            SemanticKind.TEMPORAL_ACT_MISFIRED,
            "TemporalActMisfired",
            writeJson(Map.of("temporalActId", act.temporalActId(), "misfiredAt", now.toString())),
            "temporal-act-misfired:" + act.temporalActId(),
            now,
            null
        );
    }

    private LedgerEntry failedLedgerEntry(String habitatId, String temporalActId, Instant now) {
        return new LedgerEntry(
            UUID.randomUUID(),
            habitatId,
            LedgerRecordClass.LEDGER_ONLY,
            "TEMPORAL_ACT",
            temporalActId,
            SemanticKind.TEMPORAL_ACT_FAILED,
            "TemporalActFailed",
            writeJson(Map.of(
                "temporalActId", temporalActId,
                "failedAt", now.toString(),
                "reason", "unknown payload kind"
            )),
            "temporal-act-failed:" + temporalActId + ":unknown-payload",
            now,
            null
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize temporal payload", ex);
        }
    }
}
