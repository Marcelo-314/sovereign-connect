package com.sovereign.eib.northbound.dto;

import java.util.List;

public record ScEnvelope<T>(String status, T payload, List<ScWarningDto> warnings, ScErrorDto error) {
}
