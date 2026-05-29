package com.sovereign.connect.bus.runtime.persistence;

import com.sovereign.connect.bus.runtime.dispatch.model.DispatchAttempt;
import com.sovereign.connect.bus.runtime.dispatch.model.DispatchState;
import com.sovereign.connect.bus.runtime.port.DispatchStateWritePort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDispatchStateRepository implements DispatchStateWritePort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcDispatchStateRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public synchronized DispatchAttempt claim(UUID dispatchRecordId) {
        requireDispatchRecordId(dispatchRecordId);
        StoredAttempt current = currentStoredAttempt(dispatchRecordId).orElse(null);
        DispatchState currentState = current == null ? DispatchState.PENDING : current.state();
        if (currentState != DispatchState.PENDING && currentState != DispatchState.RETRY_SCHEDULED) {
            throw new IllegalStateException("dispatch record is not claimable from " + currentState);
        }

        int nextAttemptNumber = current == null ? 1 : current.attemptNumber() + 1;
        DispatchAttempt claimed = new DispatchAttempt(
                UUID.randomUUID(),
                dispatchRecordId,
                nextAttemptNumber,
                DispatchState.CLAIMED,
                now()
        );
        upsertRecord(claimed, null);
        insertAttempt(claimed, null);
        return claimed;
    }

    @Override
    public synchronized DispatchAttempt transition(UUID dispatchRecordId, DispatchState targetState) {
        requireDispatchRecordId(dispatchRecordId);
        if (targetState == null) {
            throw new IllegalArgumentException("targetState is required");
        }
        if (targetState == DispatchState.CANCELLED_BY_SUPERSEDE) {
            throw new IllegalArgumentException("evidenceRef is required for supersede cancellation");
        }
        return transitionCurrent(dispatchRecordId, targetState, null);
    }

    @Override
    public synchronized DispatchAttempt transitionWithEvidence(UUID dispatchRecordId, DispatchState targetState, String evidenceRef) {
        requireDispatchRecordId(dispatchRecordId);
        if (targetState == null) {
            throw new IllegalArgumentException("targetState is required");
        }
        if (targetState == DispatchState.CANCELLED_BY_SUPERSEDE && (evidenceRef == null || evidenceRef.isBlank())) {
            throw new IllegalArgumentException("evidenceRef is required for supersede cancellation");
        }
        return transitionCurrent(dispatchRecordId, targetState, evidenceRef);
    }

    @Override
    public Optional<DispatchAttempt> currentAttempt(UUID dispatchRecordId) {
        if (dispatchRecordId == null) {
            return Optional.empty();
        }
        return currentStoredAttempt(dispatchRecordId).map(StoredAttempt::toDispatchAttempt);
    }

    private DispatchAttempt transitionCurrent(UUID dispatchRecordId, DispatchState targetState, String evidenceRef) {
        StoredAttempt current = currentStoredAttempt(dispatchRecordId).orElse(null);
        DispatchState sourceState = current == null ? DispatchState.PENDING : current.state();
        if (!isAllowed(sourceState, targetState)) {
            throw new IllegalStateException("transition " + sourceState + " -> " + targetState + " is not allowed");
        }

        DispatchAttempt updated;
        if (current == null) {
            updated = new DispatchAttempt(UUID.randomUUID(), dispatchRecordId, 0, targetState, now());
            upsertRecord(updated, evidenceRef);
            insertAttempt(updated, evidenceRef);
        } else {
            updated = new DispatchAttempt(
                    current.attemptId(),
                    dispatchRecordId,
                    current.attemptNumber(),
                    targetState,
                    current.claimedAt()
            );
            upsertRecord(updated, evidenceRef);
            updateAttempt(updated, evidenceRef);
        }
        return updated;
    }

    private Optional<StoredAttempt> currentStoredAttempt(UUID dispatchRecordId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    """
                    SELECT records.current_attempt_id, records.dispatch_record_id,
                           records.current_attempt_number, records.current_state,
                           attempts.claimed_at_ms
                    FROM sc_b_dispatch_records records
                    LEFT JOIN sc_b_dispatch_attempts attempts
                      ON attempts.attempt_id = records.current_attempt_id
                    WHERE records.dispatch_record_id = ?
                    """,
                    (rs, rowNum) -> mapStoredAttempt(rs),
                    dispatchRecordId.toString()
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private StoredAttempt mapStoredAttempt(ResultSet rs) throws SQLException {
        String attemptId = rs.getString("current_attempt_id");
        if (attemptId == null) {
            return null;
        }
        return new StoredAttempt(
                UUID.fromString(attemptId),
                UUID.fromString(rs.getString("dispatch_record_id")),
                rs.getInt("current_attempt_number"),
                DispatchState.valueOf(rs.getString("current_state")),
                Instant.ofEpochMilli(rs.getLong("claimed_at_ms"))
        );
    }

    private void upsertRecord(DispatchAttempt attempt, String evidenceRef) {
        long nowMs = now().toEpochMilli();
        jdbcTemplate.update(
                """
                INSERT INTO sc_b_dispatch_records (
                    dispatch_record_id, source_record_id, current_attempt_id,
                    current_attempt_number, current_state, supersession_evidence_ref,
                    created_at_ms, updated_at_ms
                )
                VALUES (?, NULL, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(dispatch_record_id) DO UPDATE SET
                    current_attempt_id = excluded.current_attempt_id,
                    current_attempt_number = excluded.current_attempt_number,
                    current_state = excluded.current_state,
                    supersession_evidence_ref = excluded.supersession_evidence_ref,
                    updated_at_ms = excluded.updated_at_ms
                """,
                attempt.dispatchRecordId().toString(),
                attempt.attemptId().toString(),
                attempt.attemptNumber(),
                attempt.state().name(),
                evidenceRef,
                nowMs,
                nowMs
        );
    }

    private void insertAttempt(DispatchAttempt attempt, String evidenceRef) {
        long nowMs = now().toEpochMilli();
        jdbcTemplate.update(
                """
                INSERT INTO sc_b_dispatch_attempts (
                    attempt_id, dispatch_record_id, attempt_number, state,
                    claimed_at_ms, updated_at_ms, supersession_evidence_ref
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                attempt.attemptId().toString(),
                attempt.dispatchRecordId().toString(),
                attempt.attemptNumber(),
                attempt.state().name(),
                attempt.claimedAt().toEpochMilli(),
                nowMs,
                evidenceRef
        );
    }

    private void updateAttempt(DispatchAttempt attempt, String evidenceRef) {
        jdbcTemplate.update(
                """
                UPDATE sc_b_dispatch_attempts
                SET state = ?, updated_at_ms = ?, supersession_evidence_ref = ?
                WHERE attempt_id = ?
                """,
                attempt.state().name(),
                now().toEpochMilli(),
                evidenceRef,
                attempt.attemptId().toString()
        );
    }

    private boolean isAllowed(DispatchState sourceState, DispatchState targetState) {
        return sourceState == DispatchState.PENDING && targetState == DispatchState.CLAIMED
                || sourceState == DispatchState.CLAIMED && targetState == DispatchState.DISPATCHING
                || sourceState == DispatchState.DISPATCHING && targetState == DispatchState.DISPATCHED
                || sourceState == DispatchState.DISPATCHING && targetState == DispatchState.DELIVERY_FAILED
                || sourceState == DispatchState.DELIVERY_FAILED && targetState == DispatchState.RETRY_SCHEDULED
                || sourceState == DispatchState.RETRY_SCHEDULED && targetState == DispatchState.CLAIMED
                || sourceState == DispatchState.DELIVERY_FAILED && targetState == DispatchState.EXHAUSTED
                || sourceState == DispatchState.PENDING && targetState == DispatchState.CANCELLED_BY_SUPERSEDE
                || sourceState == DispatchState.CLAIMED && targetState == DispatchState.CANCELLED_BY_SUPERSEDE
                || sourceState == DispatchState.RETRY_SCHEDULED && targetState == DispatchState.CANCELLED_BY_SUPERSEDE;
    }

    private void requireDispatchRecordId(UUID dispatchRecordId) {
        if (dispatchRecordId == null) {
            throw new IllegalArgumentException("dispatchRecordId is required");
        }
    }

    private Instant now() {
        return Instant.ofEpochMilli(System.currentTimeMillis());
    }

    private record StoredAttempt(
            UUID attemptId,
            UUID dispatchRecordId,
            int attemptNumber,
            DispatchState state,
            Instant claimedAt
    ) {
        DispatchAttempt toDispatchAttempt() {
            return new DispatchAttempt(attemptId, dispatchRecordId, attemptNumber, state, claimedAt);
        }
    }
}
