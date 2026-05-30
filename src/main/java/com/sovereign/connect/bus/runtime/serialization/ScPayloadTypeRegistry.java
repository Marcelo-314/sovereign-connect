package com.sovereign.connect.bus.runtime.serialization;

import java.util.regex.Pattern;

public final class ScPayloadTypeRegistry {

    public static final String COMMAND_STUB_V1               = "sc.command.stub.v1";
    public static final String EVENT_TIMER_FIRED_V1          = "sc.event.timer-fired.v1";
    public static final String RESPONSE_STUB_V1              = "sc.response.stub.v1";
    public static final String LIFECYCLE_ADAPTER_ANNOUNCE_V1 = "sc.lifecycle.adapter-announce.v1";

    private static final Pattern PAYLOAD_TYPE_PATTERN =
        Pattern.compile("^sc\\.[a-z0-9-]+\\.[a-z0-9-]+\\.v[1-9][0-9]*$");

    private ScPayloadTypeRegistry() {}

    public static boolean hasValidPayloadTypeSyntax(String payloadType) {
        if (payloadType == null || payloadType.isBlank()) {
            return false;
        }
        if (looksLikeJavaClassName(payloadType)) {
            return false;
        }
        return PAYLOAD_TYPE_PATTERN.matcher(payloadType).matches();
    }

    public static void requireValidPayloadTypeSyntax(String payloadType) {
        if (!hasValidPayloadTypeSyntax(payloadType)) {
            throw new IllegalArgumentException(
                "invalid payloadType: '" + payloadType + "'");
        }
    }

    public static boolean looksLikeJavaClassName(String payloadType) {
        if (payloadType == null) {
            return false;
        }
        return payloadType.startsWith("com.")
            || payloadType.startsWith("org.")
            || payloadType.startsWith("net.")
            || payloadType.startsWith("io.")
            || payloadType.startsWith("java.");
    }
}
