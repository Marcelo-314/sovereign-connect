package com.sovereign.connect.core.northbound;

import com.sovereign.connect.core.northbound.runtime.NorthboundEndpointHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRuntimeStateView;
import com.sovereign.connect.core.northbound.runtime.NorthboundTemporalRuntimeStatusView;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import com.sovereign.connect.core.northbound.topology.NorthboundCapabilityView;
import com.sovereign.connect.core.northbound.topology.NorthboundDeviceView;
import com.sovereign.connect.core.northbound.topology.NorthboundEndpointView;
import com.sovereign.connect.core.northbound.topology.NorthboundRoomView;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologyVersionView;
import com.sovereign.connect.core.northbound.topology.NorthboundZoneView;
import com.sovereign.connect.core.temporal.engine.TemporalEngineHealth;
import com.sovereign.connect.core.temporal.observation.TemporalActObservation;
import com.sovereign.connect.core.topology.model.CapabilityNode;
import com.sovereign.connect.core.topology.model.DeviceNode;
import com.sovereign.connect.core.topology.model.EndpointHealth;
import com.sovereign.connect.core.topology.model.EndpointNode;
import com.sovereign.connect.core.topology.model.RoomNode;
import com.sovereign.connect.core.topology.model.TopologyVersion;
import com.sovereign.connect.core.topology.model.ZoneNode;
import com.sovereign.connect.core.topology.query.CoreSnapshot;
import com.sovereign.connect.core.topology.query.DeviceSnapshot;
import com.sovereign.connect.core.topology.query.EndpointSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

final class NorthboundMapper {

    private NorthboundMapper() {
    }

    static NorthboundTopologySnapshot toTopologySnapshot(CoreSnapshot snapshot) {
        return new NorthboundTopologySnapshot(
            snapshot.habitatId(),
            snapshot.topologyVersion().value(),
            toScope(snapshot.topologyVersion()),
            snapshot.topology().rooms().stream().map(NorthboundMapper::toRoomView).toList(),
            snapshot.topology().zones().stream().map(NorthboundMapper::toZoneView).toList(),
            snapshot.topology().devices().stream().map(NorthboundMapper::toDeviceView).toList(),
            snapshot.topology().endpoints().stream().map(NorthboundMapper::toEndpointView).toList(),
            snapshot.readAt()
        );
    }

    static NorthboundTopologyVersionView toTopologyVersionView(String habitatId, TopologyVersion version) {
        return new NorthboundTopologyVersionView(
            habitatId,
            version.value(),
            version.scope().type().name(),
            version.scope().id()
        );
    }

    static NorthboundDeviceView toDeviceView(DeviceSnapshot snapshot) {
        return toDeviceView(snapshot.device());
    }

    static NorthboundDeviceView toDeviceView(DeviceNode device) {
        return new NorthboundDeviceView(
            device.deviceId(),
            device.alias(),
            device.displayName(),
            device.roomId(),
            device.zoneId(),
            device.kind().name(),
            device.provider().name(),
            device.endpointIds(),
            device.deviceCapabilities().stream().map(NorthboundMapper::toCapabilityView).toList(),
            device.providerRef().providerDeviceId()
        );
    }

    static NorthboundEndpointView toEndpointView(EndpointSnapshot snapshot) {
        return toEndpointView(snapshot.endpoint());
    }

    static NorthboundEndpointView toEndpointView(EndpointNode endpoint) {
        return new NorthboundEndpointView(
            endpoint.endpointId(),
            endpoint.deviceId(),
            endpoint.alias(),
            endpoint.displayName(),
            endpoint.kind().name(),
            endpoint.roomId(),
            endpoint.zoneId(),
            endpoint.capabilities().stream().map(NorthboundMapper::toCapabilityView).toList(),
            endpoint.providerRef().providerEndpointId()
        );
    }

    static NorthboundRoomView toRoomView(RoomNode room) {
        return new NorthboundRoomView(
            room.roomId(),
            room.roomName(),
            room.zoneIds(),
            room.deviceIds(),
            room.endpointIds()
        );
    }

    static NorthboundZoneView toZoneView(ZoneNode zone) {
        return new NorthboundZoneView(
            zone.zoneId(),
            zone.zoneName(),
            zone.roomId(),
            zone.deviceIds(),
            zone.endpointIds()
        );
    }

    static NorthboundEndpointHealthView toEndpointHealthView(String endpointId, EndpointHealth health) {
        return new NorthboundEndpointHealthView(
            endpointId,
            health.status().name(),
            health.lastSeenAt(),
            health.details()
        );
    }

    static NorthboundRuntimeStateView toDeviceRuntimeStateView(
        String habitatId,
        String deviceId,
        Map<String, Object> state,
        Instant readAt
    ) {
        return new NorthboundRuntimeStateView(habitatId, deviceId, "DEVICE", state, readAt);
    }

    static NorthboundTemporalRuntimeStatusView toTemporalRuntimeStatusView(
        String habitatId,
        TemporalEngineHealth health
    ) {
        return new NorthboundTemporalRuntimeStatusView(
            habitatId,
            health.status().name(),
            health.isReady(),
            health.lastPollAt(),
            health.lastSuccessfulPollAt(),
            health.firedTotal(),
            health.misfiredTotal(),
            health.cancelledTotal(),
            health.failedTotal(),
            health.skippedTotal()
        );
    }

    static NorthboundTemporalActView toTemporalActView(TemporalActObservation act) {
        return new NorthboundTemporalActView(
            act.temporalActId(),
            act.habitatId(),
            act.status().name(),
            act.dueAt(),
            act.payloadKind(),
            act.label(),
            act.signalKind(),
            act.notificationTargetRef(),
            act.createdByRef(),
            act.createdAt(),
            act.updatedAt(),
            act.firedAt(),
            act.terminalAt(),
            act.terminalReason()
        );
    }

    static List<NorthboundTemporalActView> toTemporalActViews(List<TemporalActObservation> acts) {
        return acts.stream().map(NorthboundMapper::toTemporalActView).toList();
    }

    private static NorthboundCapabilityView toCapabilityView(CapabilityNode capability) {
        return new NorthboundCapabilityView(
            capability.capabilityId(),
            capability.name(),
            capability.kind().name()
        );
    }

    private static String toScope(TopologyVersion version) {
        return version.scope().type().name() + ":" + version.scope().id();
    }
}
