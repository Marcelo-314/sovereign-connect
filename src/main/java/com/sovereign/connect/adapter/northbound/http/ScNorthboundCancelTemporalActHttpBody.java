package com.sovereign.connect.adapter.northbound.http;

record ScNorthboundCancelTemporalActHttpBody(
    String requestedByRef,
    String idempotencyKey,
    String reason
) {}
