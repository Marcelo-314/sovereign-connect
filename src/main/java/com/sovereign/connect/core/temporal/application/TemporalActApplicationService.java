package com.sovereign.connect.core.temporal.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.engine.TemporalEngineStatus;
import com.sovereign.connect.core.temporal.model.CreatedByRef;
import com.sovereign.connect.core.temporal.model.SignalTemporalPayload;
import com.sovereign.connect.core.temporal.model.TemporalAct;
import com.sovereign.connect.core.temporal.model.TemporalActStatus;
import com.sovereign.connect.core.temporal.observation.TemporalActObservation;
import com.sovereign.connect.core.temporal.observation.TemporalActObservationPort;
import com.sovereign.connect.core.temporal.service.TemporalActService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class TemporalActApplicationService implements TemporalActApplicationPort {

    private static final String CREATE_SIGNAL = "CREATE_SIGNAL";
    private static final String CANCEL = "CANCEL";

    private final TemporalActService actService;
    private final TemporalActObservationPort observationPort;
    private final TemporalRequestIdempotencyPort idempotencyPort;
    private final TemporalEngineHealth health;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public TemporalActApplicationService(
        TemporalActService actService,
        TemporalActObservationPort observationPort,
        TemporalRequestIdempotencyPort idempotencyPort,
        TemporalEngineHealth health,
        ObjectMapper objectMapper,
        TransactionTemplate txTemplate
    ) {
        this.actService = Objects.requireNonNull(actService, "actService is required");
        this.observationPort = Objects.requireNonNull(observationPort, "observationPort is required");
        this.idempotencyPort = Objects.requireNonNull(idempotencyPort, "idempotencyPort is required");
        this.health = Objects.requireNonNull(health, "health is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.txTemplate = Objects.requireNonNull(txTemplate, "txTemplate is required");
    }

    @Override
    public CreateSignalTemporalActResult createSignalTemporalAct(CreateSignalTemporalActRequest request) {
        TemporalRequestRejection rejection = validateCreate(request);
        if (rejection != null) {
            return new CreateSignalTemporalActResult.Rejected(rejection);
        }
        if (!health.isReady()) {
            return new CreateSignalTemporalActResult.Failed(toRuntimeFailure(health.status()));
        }
        byte[] fingerprint = createFingerprint(request);
        try {
            return txTemplate.execute(status -> createSignalTemporalActInTransaction(request, fingerprint));
        } catch (DataIntegrityViolationException ex) {
            return replayCreateAfterInsertConflict(request, fingerprint);
        } catch (RuntimeException ex) {
            return new CreateSignalTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                ex.getMessage()
            ));
        }
    }

    @Override
    public CancelTemporalActResult cancelTemporalAct(CancelTemporalActRequest request) {
        TemporalRequestRejection rejection = validateCancel(request);
        if (rejection != null) {
            return new CancelTemporalActResult.Rejected(rejection);
        }
        if (!health.isReady()) {
            return new CancelTemporalActResult.Failed(toRuntimeFailure(health.status()));
        }
        byte[] fingerprint = cancelFingerprint(request);
        try {
            CancelTemporalActResult result = txTemplate.execute(status -> cancelTemporalActInTransaction(request, fingerprint));
            if (result instanceof CancelTemporalActResult.Cancelled) {
                health.incrementCancelled();
            }
            return result;
        } catch (DataIntegrityViolationException ex) {
            return replayCancelAfterInsertConflict(request, fingerprint);
        } catch (RuntimeException ex) {
            return new CancelTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                ex.getMessage()
            ));
        }
    }

    private CreateSignalTemporalActResult createSignalTemporalActInTransaction(
        CreateSignalTemporalActRequest request,
        byte[] fingerprint
    ) {
        Optional<TemporalRequestIdempotencyRecord> existing =
            idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CREATE_SIGNAL);
        if (existing.isPresent()) {
            return replayCreate(request, fingerprint, existing.get());
        }
        TemporalAct act = actService.createSignalTemporalActInExistingTransaction(
            request.habitatId(),
            new SignalTemporalPayload(request.label(), request.signalKind()),
            request.dueAt(),
            request.notificationTargetRef(),
            new CreatedByRef(request.createdByRef()),
            request.requestedAt()
        );
        TemporalActObservation observation = toObservationDirect(act);
        idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
            request.habitatId(),
            request.idempotencyKey(),
            CREATE_SIGNAL,
            fingerprint,
            act.temporalActId(),
            "ACCEPTED",
            null,
            writeJson(observation)
        ), request.requestedAt());
        return new CreateSignalTemporalActResult.Accepted(observation);
    }

    private CancelTemporalActResult cancelTemporalActInTransaction(CancelTemporalActRequest request, byte[] fingerprint) {
        Optional<TemporalRequestIdempotencyRecord> existing =
            idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CANCEL);
        if (existing.isPresent()) {
            return replayCancel(request, fingerprint, existing.get());
        }
        Optional<TemporalActObservation> before = observationPort.findById(request.habitatId(), request.temporalActId());
        if (before.isEmpty()) {
            CancelTemporalActResult result = new CancelTemporalActResult.NotFound(request.habitatId(), request.temporalActId());
            idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
                request.habitatId(),
                request.idempotencyKey(),
                CANCEL,
                fingerprint,
                null,
                "ACCEPTED",
                "NOT_FOUND",
                writeJson(result)
            ), request.requestedAt());
            return result;
        }
        int updated = actService.cancelTemporalActInExistingTransaction(
            request.habitatId(),
            request.temporalActId(),
            request.requestedAt()
        );
        TemporalActObservation after = observationPort.findById(request.habitatId(), request.temporalActId()).orElseThrow();
        if (updated == 1) {
            insertCancelIdempotency(request, fingerprint, "ACCEPTED", "CANCELLED", writeJson(after));
            return new CancelTemporalActResult.Cancelled(after);
        }
        if (after.status() != TemporalActStatus.PENDING && after.status() != TemporalActStatus.ARMED) {
            insertCancelIdempotency(request, fingerprint, "ACCEPTED", "ALREADY_TERMINAL", writeJson(after));
            return new CancelTemporalActResult.AlreadyTerminal(after);
        }
        TemporalRuntimeFailure failure = new TemporalRuntimeFailure(
            TemporalRuntimeFailureCode.INTERNAL_FAILURE,
            "cancel did not update a non-terminal temporal act"
        );
        insertCancelIdempotency(request, fingerprint, "FAILED", "INTERNAL_FAILURE", writeJson(failure));
        return new CancelTemporalActResult.Failed(failure);
    }

    private CreateSignalTemporalActResult replayCreateAfterInsertConflict(
        CreateSignalTemporalActRequest request,
        byte[] fingerprint
    ) {
        return idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CREATE_SIGNAL)
            .map(record -> replayCreate(request, fingerprint, record))
            .orElseGet(() -> new CreateSignalTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.IDEMPOTENCY_STORE_UNAVAILABLE,
                "idempotency insert failed and no replay record was available"
            )));
    }

    private CancelTemporalActResult replayCancelAfterInsertConflict(CancelTemporalActRequest request, byte[] fingerprint) {
        return idempotencyPort.find(request.habitatId(), request.idempotencyKey(), CANCEL)
            .map(record -> replayCancel(request, fingerprint, record))
            .orElseGet(() -> new CancelTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.IDEMPOTENCY_STORE_UNAVAILABLE,
                "idempotency insert failed and no replay record was available"
            )));
    }

    private CreateSignalTemporalActResult replayCreate(
        CreateSignalTemporalActRequest request,
        byte[] fingerprint,
        TemporalRequestIdempotencyRecord record
    ) {
        if (!record.sameFingerprint(fingerprint)) {
            return new CreateSignalTemporalActResult.Rejected(rejection(
                TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT,
                "idempotency key conflicts with a different create request"
            ));
        }
        return readObservation(record)
            .<CreateSignalTemporalActResult>map(CreateSignalTemporalActResult.IdempotentReplay::new)
            .or(() -> observationPort.findById(request.habitatId(), record.temporalActId())
                .map(CreateSignalTemporalActResult.IdempotentReplay::new))
            .orElseGet(() -> new CreateSignalTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.STORAGE_UNAVAILABLE,
                "idempotency record references a missing temporal act"
            )));
    }

    private CancelTemporalActResult replayCancel(
        CancelTemporalActRequest request,
        byte[] fingerprint,
        TemporalRequestIdempotencyRecord record
    ) {
        if (!record.sameFingerprint(fingerprint)) {
            return new CancelTemporalActResult.Rejected(rejection(
                TemporalRequestRejectionCode.IDEMPOTENCY_CONFLICT,
                "idempotency key conflicts with a different cancel request"
            ));
        }
        if (record.resultCode() == null) {
            return observationPort.findById(request.habitatId(), record.temporalActId())
                .<CancelTemporalActResult>map(CancelTemporalActResult.IdempotentReplay::new)
                .orElseGet(() -> new CancelTemporalActResult.NotFound(request.habitatId(), request.temporalActId()));
        }
        return switch (record.resultCode()) {
            case "NOT_FOUND" -> new CancelTemporalActResult.NotFound(request.habitatId(), request.temporalActId());
            case "ALREADY_TERMINAL" -> readObservation(record)
                .<CancelTemporalActResult>map(CancelTemporalActResult.AlreadyTerminal::new)
                .or(() -> observationPort.findById(request.habitatId(), record.temporalActId())
                    .map(CancelTemporalActResult.AlreadyTerminal::new))
                .orElseGet(() -> new CancelTemporalActResult.NotFound(request.habitatId(), request.temporalActId()));
            case "CANCELLED" -> readObservation(record)
                .<CancelTemporalActResult>map(CancelTemporalActResult.IdempotentReplay::new)
                .or(() -> observationPort.findById(request.habitatId(), record.temporalActId())
                    .map(CancelTemporalActResult.IdempotentReplay::new))
                .orElseGet(() -> new CancelTemporalActResult.NotFound(request.habitatId(), request.temporalActId()));
            case "INTERNAL_FAILURE" -> new CancelTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                "previous cancel request failed"
            ));
            default -> new CancelTemporalActResult.Failed(new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                "unknown cancel idempotency result code: " + record.resultCode()
            ));
        };
    }

    private void insertCancelIdempotency(
        CancelTemporalActRequest request,
        byte[] fingerprint,
        String resultKind,
        String resultCode,
        String resultJson
    ) {
        idempotencyPort.insert(new TemporalRequestIdempotencyRecord(
            request.habitatId(),
            request.idempotencyKey(),
            CANCEL,
            fingerprint,
            request.temporalActId(),
            resultKind,
            resultCode,
            resultJson
        ), request.requestedAt());
    }

    private TemporalRequestRejection validateCreate(CreateSignalTemporalActRequest request) {
        if (request == null || blank(request.habitatId())) return rejection(TemporalRequestRejectionCode.INVALID_HABITAT_ID, "habitatId is required");
        if (request.requestedAt() == null) return rejection(TemporalRequestRejectionCode.INVALID_REQUESTED_AT, "requestedAt is required");
        if (request.dueAt() == null) return rejection(TemporalRequestRejectionCode.INVALID_DUE_AT, "dueAt is required");
        if (!request.dueAt().isAfter(request.requestedAt())) return rejection(TemporalRequestRejectionCode.PAST_DUE_AT_NOT_SUPPORTED, "dueAt must be after requestedAt");
        if (blank(request.label())) return rejection(TemporalRequestRejectionCode.MISSING_LABEL, "label is required");
        if (blank(request.signalKind())) return rejection(TemporalRequestRejectionCode.MISSING_SIGNAL_KIND, "signalKind is required");
        if (blank(request.notificationTargetRef())) return rejection(TemporalRequestRejectionCode.MISSING_NOTIFICATION_TARGET_REF, "notificationTargetRef is required");
        if (blank(request.createdByRef())) return rejection(TemporalRequestRejectionCode.MISSING_CREATED_BY_REF, "createdByRef is required");
        if (blank(request.idempotencyKey())) return rejection(TemporalRequestRejectionCode.MISSING_IDEMPOTENCY_KEY, "idempotencyKey is required");
        return null;
    }

    private TemporalRequestRejection validateCancel(CancelTemporalActRequest request) {
        if (request == null || blank(request.habitatId())) return rejection(TemporalRequestRejectionCode.INVALID_HABITAT_ID, "habitatId is required");
        if (blank(request.temporalActId())) return rejection(TemporalRequestRejectionCode.INVALID_TEMPORAL_ACT_ID, "temporalActId is required");
        if (blank(request.requestedByRef())) return rejection(TemporalRequestRejectionCode.MISSING_REQUESTED_BY_REF, "requestedByRef is required");
        if (blank(request.idempotencyKey())) return rejection(TemporalRequestRejectionCode.MISSING_IDEMPOTENCY_KEY, "idempotencyKey is required");
        if (request.requestedAt() == null) return rejection(TemporalRequestRejectionCode.INVALID_REQUESTED_AT, "requestedAt is required");
        return null;
    }

    private TemporalRequestRejection rejection(TemporalRequestRejectionCode code, String message) {
        return new TemporalRequestRejection(code, message);
    }

    private TemporalRuntimeFailure toRuntimeFailure(TemporalEngineStatus status) {
        return switch (status) {
            case DISABLED -> new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.TEMPORAL_ENGINE_DISABLED,
                "engine is disabled"
            );
            case RECOVERING -> new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.RECOVERY_NOT_COMPLETED,
                "engine is still recovering"
            );
            case STARTING -> new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY,
                "engine is starting"
            );
            case STOPPED -> new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY,
                "engine is stopped"
            );
            case FAILED -> new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.INTERNAL_FAILURE,
                "engine has failed"
            );
            default -> new TemporalRuntimeFailure(
                TemporalRuntimeFailureCode.TEMPORAL_ENGINE_NOT_READY,
                "engine not ready: " + status
            );
        };
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private byte[] createFingerprint(CreateSignalTemporalActRequest request) {
        String value = request.habitatId() + "|" + request.idempotencyKey() + "|CREATE_SIGNAL|"
            + request.dueAt().toEpochMilli() + "|" + request.label() + "|" + request.signalKind()
            + "|" + request.notificationTargetRef() + "|" + request.createdByRef();
        return sha256(value);
    }

    private byte[] cancelFingerprint(CancelTemporalActRequest request) {
        String value = request.habitatId() + "|" + request.idempotencyKey() + "|CANCEL|"
            + request.temporalActId() + "|" + request.requestedByRef();
        return sha256(value);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize temporal application result", ex);
        }
    }

    private Optional<TemporalActObservation> readObservation(TemporalRequestIdempotencyRecord record) {
        if (record.resultJson() == null || record.resultJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(record.resultJson(), TemporalActObservation.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private TemporalActObservation toObservationDirect(TemporalAct act) {
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
