package com.sovereign.connect.core.topology.query;

import com.sovereign.connect.core.topology.model.BaseTopologySnapshot;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.HabitatBaseTopology;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.port.CoreSnapshotReadPort;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CoreSnapshotQueryService {

    private final CoreSnapshotReadPort readPort;
    private final Clock clock;

    public CoreSnapshotQueryService(CoreSnapshotReadPort readPort, Clock clock) {
        this.readPort = Objects.requireNonNull(readPort, "readPort is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public Optional<CoreSnapshot> findCurrentSnapshot(String habitatId) {
        return readPort.findSnapshot(habitatId).map(this::toCoreSnapshot);
    }

    public Optional<TopologyVersion> findCurrentTopologyVersion(String habitatId) {
        return readPort.findCurrentVersion(habitatId);
    }

    public Optional<DeviceSnapshot> findDevice(String habitatId, String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId is required");
        Optional<HabitatBaseTopology> topology = readPort.findTopology(habitatId);
        if (topology.isEmpty()) {
            return Optional.empty();
        }
        Optional<DeviceNode> device = topology.get().devices().stream()
            .filter(candidate -> candidate.deviceId().equals(deviceId))
            .findFirst();
        if (device.isEmpty()) {
            return Optional.empty();
        }
        TopologyVersion version = readPort.findCurrentVersion(habitatId)
            .orElse(topology.get().topologyVersion());
        return Optional.of(new DeviceSnapshot(
            habitatId,
            version,
            device.get(),
            readPort.findDeviceState(habitatId, deviceId).orElse(Map.of()),
            Instant.now(clock)
        ));
    }

    public Optional<EndpointSnapshot> findEndpoint(String habitatId, String endpointId) {
        Objects.requireNonNull(endpointId, "endpointId is required");
        Optional<HabitatBaseTopology> topology = readPort.findTopology(habitatId);
        if (topology.isEmpty()) {
            return Optional.empty();
        }
        Optional<EndpointNode> endpoint = topology.get().endpoints().stream()
            .filter(candidate -> candidate.endpointId().equals(endpointId))
            .findFirst();
        if (endpoint.isEmpty()) {
            return Optional.empty();
        }
        TopologyVersion version = readPort.findCurrentVersion(habitatId)
            .orElse(topology.get().topologyVersion());
        EndpointHealth health = readPort.findEndpointHealth(habitatId, endpointId)
            .orElse(endpoint.get().health());
        return Optional.of(new EndpointSnapshot(
            habitatId,
            version,
            endpoint.get(),
            health,
            Instant.now(clock)
        ));
    }

    public Optional<Map<String, Object>> findDeviceState(String habitatId, String deviceId) {
        return readPort.findDeviceState(habitatId, deviceId);
    }

    public Optional<EndpointHealth> findEndpointHealth(String habitatId, String endpointId) {
        return readPort.findEndpointHealth(habitatId, endpointId);
    }

    private CoreSnapshot toCoreSnapshot(BaseTopologySnapshot snapshot) {
        Map<String, Map<String, Object>> deviceStates = new LinkedHashMap<>();
        for (DeviceNode device : snapshot.topology().devices()) {
            readPort.findDeviceState(snapshot.habitatId(), device.deviceId())
                .ifPresent(state -> deviceStates.put(device.deviceId(), state));
        }

        Map<String, EndpointHealth> endpointHealth = new LinkedHashMap<>();
        for (EndpointNode endpoint : snapshot.topology().endpoints()) {
            readPort.findEndpointHealth(snapshot.habitatId(), endpoint.endpointId())
                .ifPresent(health -> endpointHealth.put(endpoint.endpointId(), health));
        }

        return new CoreSnapshot(
            snapshot.habitatId(),
            snapshot.topologyVersion(),
            snapshot.topology(),
            deviceStates,
            endpointHealth,
            Instant.now(clock)
        );
    }
}
