package com.sovereign.connect.bus.runtime.nats;

import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NatsJetStreamStreamApplicator {
    private final JetStreamManagement management;

    public NatsJetStreamStreamApplicator(JetStreamManagement management) {
        this.management = Objects.requireNonNull(management, "management is required");
    }

    public List<StreamInfo> ensureAllConfiguredStreams() throws IOException, JetStreamApiException {
        List<StreamInfo> infos = new ArrayList<>();
        for (NatsStreamConfiguration.StreamDefinition definition : NatsStreamConfiguration.streamDefinitions()) {
            infos.add(ensureStream(definition));
        }
        return List.copyOf(infos);
    }

    public StreamInfo ensureStream(NatsStreamConfiguration.StreamDefinition definition)
            throws IOException, JetStreamApiException {
        Objects.requireNonNull(definition, "definition is required");
        StreamConfiguration config = StreamConfiguration.builder()
                .name(definition.name())
                .subjects(definition.subjects())
                .storageType(StorageType.Memory)
                .build();
        try {
            return management.addStream(config);
        } catch (JetStreamApiException apiEx) {
            if (apiEx.getApiErrorCode() != 10058) {
                throw new IllegalStateException(
                        "JetStream stream application failed with unexpected API error: " + definition.name(),
                        apiEx);
            }
            try {
                StreamInfo existing = management.getStreamInfo(definition.name());
                if (existing.getConfiguration().getStorageType() != config.getStorageType()) {
                    throw new IllegalStateException(
                            "JetStream stream is incompatible or cannot be updated: "
                                    + definition.name()
                                    + " - existing storageType: "
                                    + existing.getConfiguration().getStorageType()
                                    + ", canonical: "
                                    + config.getStorageType());
                }
                return management.updateStream(config);
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (IOException | JetStreamApiException updateFailed) {
                throw new IllegalStateException(
                        "JetStream stream is incompatible or cannot be updated: " + definition.name(),
                        updateFailed);
            }
        }
    }
}
