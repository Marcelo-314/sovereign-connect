package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.ScNorthboundStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ScNorthboundHttpResponseMapper {

    private ScNorthboundHttpResponseMapper() {}

    static <T> ResponseEntity<ScNorthboundResponse<T>> toResponseEntity(
        ScNorthboundResponse<T> response
    ) {
        return ResponseEntity.status(toHttpStatus(response.status())).body(response);
    }

    private static HttpStatus toHttpStatus(ScNorthboundStatus status) {
        return switch (status) {
            case OK -> HttpStatus.OK;
            case CREATED -> HttpStatus.CREATED;
            case ACCEPTED -> HttpStatus.ACCEPTED;
            case CANCELLED -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case INVALID_CANONICAL_ID -> HttpStatus.BAD_REQUEST;
            case VALIDATION_ERROR -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNSUPPORTED_PROFILE -> HttpStatus.NOT_IMPLEMENTED;
            case DEFERRED_SC_B_REQUIRED -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNKNOWN_PENDING_NORMALIZATION -> HttpStatus.OK;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
