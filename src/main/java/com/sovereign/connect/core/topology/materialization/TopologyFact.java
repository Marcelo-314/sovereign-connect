package com.sovereign.connect.core.topology.materialization;

import java.time.Instant;
import java.util.UUID;

public sealed interface TopologyFact permits
    DeviceDiscoveryFact,
    EndpointDiscoveryFact,
    CapabilityDiscoveryFact,
    DeviceStateFact,
    HealthFact,
    RoomDiscoveryFact,
    ZoneDiscoveryFact {

    UUID factId();

    String adapterInstanceId();

    String providerId();

    Instant observedAt();
}
