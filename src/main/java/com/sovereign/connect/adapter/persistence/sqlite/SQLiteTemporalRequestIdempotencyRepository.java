package com.sovereign.connect.adapter.persistence.sqlite;

import com.sovereign.connect.core.temporal.application.TemporalRequestIdempotencyPort;
import com.sovereign.connect.core.temporal.application.TemporalRequestIdempotencyRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SQLiteTemporalRequestIdempotencyRepository implements TemporalRequestIdempotencyPort {

    private final JdbcTemplate jdbcTemplate;

    public SQLiteTemporalRequestIdempotencyRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
    }

    @Override
    public Optional<TemporalRequestIdempotencyRecord> find(String habitatId, String idempotencyKey, String requestKind) {
        List<TemporalRequestIdempotencyRecord> records = jdbcTemplate.query(
            """
                SELECT habitat_id, idempotency_key, request_kind, semantic_fingerprint,
                       temporal_act_id, result_kind, result_code, result_json
                FROM temporal_request_idempotency
                WHERE habitat_id = ? AND idempotency_key = ? AND request_kind = ?
                """,
            (rs, rowNum) -> toRecord(rs),
            habitatId,
            idempotencyKey,
            requestKind
        );
        return records.stream().findFirst();
    }

    @Override
    public void insert(TemporalRequestIdempotencyRecord record, Instant now) {
        byte[] temporalActId = SQLiteCanonicalIdCodec.toBlob16Nullable(record.temporalActId());
        jdbcTemplate.update(connection -> {
            java.sql.PreparedStatement ps = connection.prepareStatement(
            """
                INSERT INTO temporal_request_idempotency
                (habitat_id, idempotency_key, request_kind, semantic_fingerprint,
                 temporal_act_id, result_kind, result_code, result_json,
                 created_at_ms, updated_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            );
            ps.setString(1, record.habitatId());
            ps.setString(2, record.idempotencyKey());
            ps.setString(3, record.requestKind());
            ps.setBytes(4, record.semanticFingerprint());
            if (temporalActId != null) {
                ps.setBytes(5, temporalActId);
            } else {
                ps.setNull(5, Types.BLOB);
            }
            ps.setString(6, record.resultKind());
            ps.setString(7, record.resultCode());
            ps.setString(8, record.resultJson());
            ps.setLong(9, now.toEpochMilli());
            ps.setLong(10, now.toEpochMilli());
            return ps;
        });
    }

    private TemporalRequestIdempotencyRecord toRecord(ResultSet rs) throws SQLException {
        return new TemporalRequestIdempotencyRecord(
            rs.getString("habitat_id"),
            rs.getString("idempotency_key"),
            rs.getString("request_kind"),
            rs.getBytes("semantic_fingerprint"),
            SQLiteCanonicalIdCodec.fromBlob16Nullable(rs.getBytes("temporal_act_id")),
            rs.getString("result_kind"),
            rs.getString("result_code"),
            rs.getString("result_json")
        );
    }
}
