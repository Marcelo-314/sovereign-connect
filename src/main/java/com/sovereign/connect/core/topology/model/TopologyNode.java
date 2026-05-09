package com.sovereign.connect.core.topology.model;

public sealed interface TopologyNode permits RoomNode, ZoneNode, DeviceNode, EndpointNode, CapabilityNode {

    String canonicalId();
}
