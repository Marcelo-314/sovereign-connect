package com.sovereign.connect.bus.runtime.nats;

import java.util.List;

public final class NatsStreamConfiguration {
    public static final String SCB_COMMANDS_V1 = "SCB_COMMANDS_V1";
    public static final String SCB_EVENTS_V1 = "SCB_EVENTS_V1";
    public static final String SCB_RESPONSES_V1 = "SCB_RESPONSES_V1";
    public static final String SCB_LIFECYCLE_V1 = "SCB_LIFECYCLE_V1";
    public static final String SCB_DLQ_V1 = "SCB_DLQ_V1";

    private NatsStreamConfiguration() {}

    public static List<StreamDefinition> streamDefinitions() {
        return List.of(
                new StreamDefinition(SCB_COMMANDS_V1, List.of("sc.v1.*.command.>")),
                new StreamDefinition(SCB_EVENTS_V1, List.of("sc.v1.*.event.>")),
                new StreamDefinition(SCB_RESPONSES_V1, List.of("sc.v1.*.response.>")),
                new StreamDefinition(SCB_LIFECYCLE_V1, List.of("sc.v1.*.lifecycle.>")),
                new StreamDefinition(SCB_DLQ_V1, List.of("sc.v1.*.dlq.>"))
        );
    }

    public record StreamDefinition(String name, List<String> subjects) {
        public StreamDefinition {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }
            if (subjects == null || subjects.isEmpty()) {
                throw new IllegalArgumentException("subjects are required");
            }
            subjects = List.copyOf(subjects);
        }
    }
}
