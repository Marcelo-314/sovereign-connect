package com.sovereign.connect.core.temporal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.scledger.model.LedgerEntry;
import com.sovereign.connect.core.scledger.model.LedgerRecordClass;
import com.sovereign.connect.core.scledger.model.SemanticKind;
import com.sovereign.connect.core.scledger.port.ScLedgerWritePort;
import com.sovereign.connect.core.temporal.model.CreatedByRef;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.model.TemporalActStatus;
import com.sovereign.connect.core.temporal.port.TemporalActReadPort;
import com.sovereign.connect.core.temporal.port.TemporalActWritePort;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TemporalActService {

    private final TemporalActWritePort writePort;
    private final TemporalActReadPort readPort;
    private final ScLedgerWritePort ledgerPort;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TemporalActService(
        TemporalActWritePort writePort,
        TemporalActReadPort readPort,
        ScLedgerWritePort ledgerPort,
        TransactionTemplate txTemplate,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.writePort = Objects.requireNonNull(writePort, "writePort is required");
        this.readPort = Objects.requireNonNull(readPort, "readPort is required");
        this.ledgerPort = Objects.requireNonNull(ledgerPort, "ledgerPort is required");
        this.txTemplate = Objects.requireNonNull(txTemplate, "txTemplate is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public String createSignalTemporalAct(
        String habitatId,
        SignalTemporalPayload payload,
        Instant dueAt,
        String notificationTargetRef,
        CreatedByRef createdByRef
    ) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(dueAt, "dueAt is required");
        Objects.requireNonNull(createdByRef, "createdByRef is required");
        return txTemplate.execute(status ->
            createSignalTemporalActInExistingTransaction(habitatId, payload, dueAt, notificationTargetRef, createdByRef, Instant.now(clock))
                .temporalActId()
        );
    }

    public int cancelTemporalAct(String habitatId, String temporalActId, Instant now) {
        Objects.requireNonNull(now, "now is required");
        return txTemplate.execute(status -> cancelTemporalActInExistingTransaction(habitatId, temporalActId, now));
    }

    public TemporalAct createSignalTemporalActInExistingTransaction(
        String habitatId,
        SignalTemporalPayload payload,
        Instant dueAt,
        String notificationTargetRef,
        CreatedByRef createdByRef,
        Instant requestedAt
    ) {
        assertActiveTransaction();
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(payload, "payload is required");
        Objects.requireNonNull(dueAt, "dueAt is required");
        Objects.requireNonNull(createdByRef, "createdByRef is required");
        Objects.requireNonNull(requestedAt, "requestedAt is required");
        String temporalActId = UUID.randomUUID().toString();
        TemporalAct act = new TemporalAct(
            temporalActId,
            habitatId,
            TemporalActStatus.PENDING,
            dueAt,
            payload,
            notificationTargetRef,
            createdByRef,
            null,
            requestedAt,
            requestedAt,
            null,
            null,
            null
        );
        writePort.insertCreated(act);
        ledgerPort.appendLedgerEntry(createdLedgerEntry(act, requestedAt));
        return act;
    }

    public int cancelTemporalActInExistingTransaction(String habitatId, String temporalActId, Instant now) {
        assertActiveTransaction();
        Objects.requireNonNull(now, "now is required");
        int updated = writePort.cancelIfNonTerminal(habitatId, temporalActId, now);
        if (updated == 1) {
            ledgerPort.appendLedgerEntry(cancelledLedgerEntry(habitatId, temporalActId, now));
        }
        return updated;
    }

    public Optional<TemporalAct> findById(String habitatId, String temporalActId) {
        return readPort.findById(habitatId, temporalActId);
    }

    public List<TemporalAct> listActive(String habitatId) {
        return readPort.listActive(habitatId);
    }

    public List<TemporalAct> listTerminal(String habitatId) {
        return readPort.listTerminal(habitatId);
    }

    public List<TemporalAct> listMisfired(String habitatId) {
        return readPort.listMisfired(habitatId);
    }

    private LedgerEntry createdLedgerEntry(TemporalAct act, Instant now) {
        return new LedgerEntry(
            UUID.randomUUID(),
            act.habitatId(),
            LedgerRecordClass.LEDGER_ONLY,
            "TEMPORAL_ACT",
            act.temporalActId(),
            SemanticKind.TEMPORAL_ACT_CREATED,
            "TemporalActCreated",
            writeJson(Map.of("temporalActId", act.temporalActId(), "dueAt", act.dueAt().toString())),
            "temporal-act-created:" + act.temporalActId(),
            now,
            null
        );
    }

    private void assertActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("temporal act mutation requires an active transaction");
        }
    }

    private LedgerEntry cancelledLedgerEntry(String habitatId, String temporalActId, Instant now) {
        return new LedgerEntry(
            UUID.randomUUID(),
            habitatId,
            LedgerRecordClass.LEDGER_ONLY,
            "TEMPORAL_ACT",
            temporalActId,
            SemanticKind.TEMPORAL_ACT_CANCELLED,
            "TemporalActCancelled",
            writeJson(Map.of("temporalActId", temporalActId, "cancelledAt", now.toString())),
            "temporal-act-cancelled:" + temporalActId,
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
