package com.sovereign.connect.adapter.persistence.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sovereign.connect.core.topology.event.TopologyChanged;
import com.sovereign.connect.core.topology.materialization.MaterializationDecision;
import com.sovereign.connect.core.topology.materialization.MaterializationDecisionKind;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.port.MaterializationDecisionReplayPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SQLiteMaterializationDecisionReplayRepository implements MaterializationDecisionReplayPort {

    private static final TypeReference<List<TopologyChanged>> TOPOLOGY_CHANGED_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SQLiteMaterializationDecisionReplayRepository(DataSource dataSource, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource is required"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public Optional<MaterializationDecision> findDecision(String habitatId, UUID factId) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(factId, "factId is required");
        List<MaterializationDecision> results = jdbcTemplate.query(
            """
                SELECT decision_id, kind,
                       previous_version_value, resulting_version_value,
                       emitted_changes_json, reason
                FROM materialization_decision_replay
                WHERE habitat_id = ? AND fact_id = ?
                """,
            (rs, rowNum) -> toDecision(habitatId, factId, rs),
            habitatId,
            factId.toString()
        );
        return results.stream().findFirst();
    }

    @Override
    public void recordDecision(String habitatId, UUID factId, MaterializationDecision decision) {
        Objects.requireNonNull(habitatId, "habitatId is required");
        Objects.requireNonNull(factId, "factId is required");
        Objects.requireNonNull(decision, "decision is required");
        jdbcTemplate.update(
            """
                INSERT INTO materialization_decision_replay
                  (habitat_id, fact_id, decision_id, kind,
                   previous_version_value, resulting_version_value,
                   emitted_changes_json, reason, recorded_at_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(habitat_id, fact_id) DO UPDATE SET
                  decision_id = excluded.decision_id,
                  kind = excluded.kind,
                  previous_version_value = excluded.previous_version_value,
                  resulting_version_value = excluded.resulting_version_value,
                  emitted_changes_json = excluded.emitted_changes_json,
                  reason = excluded.reason,
                  recorded_at_ms = excluded.recorded_at_ms
                """,
            habitatId,
            factId.toString(),
            decision.decisionId().toString(),
            decision.kind().name(),
            decision.previousTopologyVersion().map(TopologyVersion::value).orElse(null),
            decision.resultingTopologyVersion().map(TopologyVersion::value).orElse(null),
            writeJson(decision.emittedChanges()),
            decision.reason(),
            Instant.now(clock).toEpochMilli()
        );
    }

    private MaterializationDecision toDecision(String habitatId, UUID factId, ResultSet rs) throws SQLException {
        Optional<TopologyVersion> previous = topologyVersion(habitatId, rs.getString("previous_version_value"));
        Optional<TopologyVersion> resulting = topologyVersion(habitatId, rs.getString("resulting_version_value"));
        return new MaterializationDecision(
            UUID.fromString(rs.getString("decision_id")),
            factId,
            habitatId,
            MaterializationDecisionKind.valueOf(rs.getString("kind")),
            previous,
            resulting,
            readJson(rs.getString("emitted_changes_json"), TOPOLOGY_CHANGED_LIST_TYPE),
            rs.getString("reason")
        );
    }

    private Optional<TopologyVersion> topologyVersion(String habitatId, String value) {
        return value == null
            ? Optional.empty()
            : Optional.of(TopologyVersion.habitatVersion(habitatId, Long.parseLong(value)));
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize materialization decision replay json", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize materialization decision replay json", ex);
        }
    }
}
