package com.sovereign.eib;

import com.sovereign.eib.api.EibHttpResponseMapper;
import com.sovereign.eib.domain.CanonicalSubmissionTrace;
import com.sovereign.eib.domain.InteractionAdmissionDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

class EibAdmissionResponseMapperTest {

    private InteractionAdmissionDecision decision(String status) {
        return new InteractionAdmissionDecision(
                "adm.test", status, null,
                new CanonicalSubmissionTrace("ref", "adm.test", status, null, List.of()),
                List.of(), null);
    }

    @Test
    void admittedMaps202AndAdmitted() {
        var r = EibHttpResponseMapper.admissionResponse(decision("ADMITTED"));
        assertThat(r.getStatusCode()).isEqualTo(ACCEPTED);
        assertThat(r.getBody().status()).isEqualTo("ADMITTED");
    }

    @Test
    void completedMaps200AndCompleted() {
        var r = EibHttpResponseMapper.admissionResponse(decision("COMPLETED"));
        assertThat(r.getStatusCode()).isEqualTo(OK);
        assertThat(r.getBody().status()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectedNotVisibleMaps404AndNotVisible() {
        var r = EibHttpResponseMapper.admissionResponse(decision("REJECTED_NOT_VISIBLE"));
        assertThat(r.getStatusCode()).isEqualTo(NOT_FOUND);
        assertThat(r.getBody().status()).isEqualTo("NOT_VISIBLE");
    }

    @Test
    void rejectedInvalidRequestMaps400AndInvalidRequest() {
        var r = EibHttpResponseMapper.admissionResponse(decision("REJECTED_INVALID_REQUEST"));
        assertThat(r.getStatusCode()).isEqualTo(BAD_REQUEST);
        assertThat(r.getBody().status()).isEqualTo("INVALID_REQUEST");
    }

    @Test
    void deferredScBMaps503AndDeferred() {
        var r = EibHttpResponseMapper.admissionResponse(decision("DEFERRED_SC_B_REQUIRED"));
        assertThat(r.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(r.getBody().status()).isEqualTo("DEFERRED");
    }

    @Test
    void deferredPendingNormalizationMaps409AndPendingNormalization() {
        var r = EibHttpResponseMapper.admissionResponse(decision("DEFERRED_PENDING_NORMALIZATION"));
        assertThat(r.getStatusCode()).isEqualTo(CONFLICT);
        assertThat(r.getBody().status()).isEqualTo("PENDING_NORMALIZATION");
    }

    @Test
    void deferredUnsupportedProfileMaps501AndUnsupported() {
        var r = EibHttpResponseMapper.admissionResponse(decision("DEFERRED_UNSUPPORTED_PROFILE"));
        assertThat(r.getStatusCode()).isEqualTo(NOT_IMPLEMENTED);
        assertThat(r.getBody().status()).isEqualTo("UNSUPPORTED");
    }

    @Test
    void failedUpstreamUnavailableMaps503AndUpstreamUnavailable() {
        var r = EibHttpResponseMapper.admissionResponse(decision("FAILED_UPSTREAM_UNAVAILABLE"));
        assertThat(r.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(r.getBody().status()).isEqualTo("UPSTREAM_UNAVAILABLE");
    }

    @Test
    void failedCanonicalSubmissionMaps500AndFailed() {
        var r = EibHttpResponseMapper.admissionResponse(decision("FAILED_CANONICAL_SUBMISSION"));
        assertThat(r.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().status()).isEqualTo("FAILED");
    }

    @Test
    void scCInternalErrorReachesFailedPath() {
        var r = EibHttpResponseMapper.admissionResponse(decision("FAILED_CANONICAL_SUBMISSION"));
        assertThat(r.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().status()).isEqualTo("FAILED");
    }

    @Test
    void noRejectedOrDeferredStatusReturnsTopLevelAccepted() {
        for (String nonAdmittedStatus : List.of(
                "REJECTED_NOT_VISIBLE", "REJECTED_INVALID_REQUEST",
                "DEFERRED_SC_B_REQUIRED", "DEFERRED_PENDING_NORMALIZATION",
                "DEFERRED_UNSUPPORTED_PROFILE", "FAILED_UPSTREAM_UNAVAILABLE",
                "FAILED_CANONICAL_SUBMISSION")) {
            var r = EibHttpResponseMapper.admissionResponse(decision(nonAdmittedStatus));
            assertThat(r.getBody().status())
                    .as("Top-level EibResponse.status for " + nonAdmittedStatus)
                    .isNotEqualTo("ACCEPTED");
        }
    }
}
