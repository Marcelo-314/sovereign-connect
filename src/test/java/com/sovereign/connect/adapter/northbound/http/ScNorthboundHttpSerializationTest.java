package com.sovereign.connect.adapter.northbound.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sovereign.connect.core.northbound.ScNorthboundError;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.ScNorthboundStatus;
import com.sovereign.connect.core.northbound.ScNorthboundWarning;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScNorthboundHttpSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    @Test
    void instantSerializesAsIso8601String() throws Exception {
        var view = new NorthboundTemporalActView(
            "act-1", "habitat-001", "ACTIVE",
            Instant.parse("2026-05-26T12:00:00Z"),
            "SIGNAL", "label", null, null, "hub-1",
            Instant.parse("2026-05-26T11:00:00Z"),
            Instant.parse("2026-05-26T11:00:00Z"),
            null, null, null
        );
        String json = mapper.writeValueAsString(view);
        assertThat(json).contains("\"2026-05-26T12:00:00Z\"");
        assertThat(json).doesNotContainPattern("\"dueAt\"\\s*:\\s*\\d{10,}");
    }

    @Test
    void northboundCreateSignalTemporalActRequest_deserializesInstantFromIso8601() throws Exception {
        String json = """
            {"dueAt":"2026-05-26T14:00:00Z","label":"test","signalKind":"ALARM",
             "notificationTargetRef":"surface:1","createdByRef":"hub-1","idempotencyKey":"k1"}
            """;
        var req = mapper.readValue(json, NorthboundCreateSignalTemporalActRequest.class);
        assertThat(req.dueAt()).isEqualTo(Instant.parse("2026-05-26T14:00:00Z"));
    }

    @Test
    void scNorthboundResponseBodyContainsAllRootFields() throws Exception {
        var response = ScNorthboundResponse.notFound("DEVICE_NOT_FOUND", "device not found");
        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"status\"");
        assertThat(json).contains("\"payload\"");
        assertThat(json).contains("\"warnings\"");
        assertThat(json).contains("\"error\"");
    }

    @Test
    void scNorthboundErrorSerializesSourceField() throws Exception {
        var err = new ScNorthboundError("CODE", "message", "northbound.validation");
        String json = mapper.writeValueAsString(err);
        assertThat(json).contains("\"source\"");
        assertThat(json).contains("northbound.validation");
    }

    @Test
    void scNorthboundWarningSerializesSourceField() throws Exception {
        var warn = new ScNorthboundWarning("WARN_CODE", "warning", "northbound.query");
        String json = mapper.writeValueAsString(warn);
        assertThat(json).contains("\"source\"");
        assertThat(json).contains("northbound.query");
    }

    @Test
    void unknownPendingNormalizationStatusRemainsVisibleInBody() throws Exception {
        var response = ScNorthboundResponse.unknownPendingNormalization(
            "UNKNOWN_PENDING_NORMALIZATION", "normalization pending");
        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("UNKNOWN_PENDING_NORMALIZATION");
    }

    @Test
    void responseMapperMapsDeferredScBRequiredTo503() {
        var response = ScNorthboundResponse.of(ScNorthboundStatus.DEFERRED_SC_B_REQUIRED, null, java.util.List.of(),
            new ScNorthboundError("SC_B_REQUIRED", "SC-B required", "northbound.deferred"));

        assertThat(ScNorthboundHttpResponseMapper.toResponseEntity(response).getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void responseMapperMapsValidationErrorTo422() {
        var response = ScNorthboundResponse.validationError("INVALID", "invalid");

        assertThat(ScNorthboundHttpResponseMapper.toResponseEntity(response).getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void responseMapperMapsUnknownPendingNormalizationTo200() {
        var response = ScNorthboundResponse.unknownPendingNormalization("pending");

        assertThat(ScNorthboundHttpResponseMapper.toResponseEntity(response).getStatusCode().value()).isEqualTo(200);
    }
}
