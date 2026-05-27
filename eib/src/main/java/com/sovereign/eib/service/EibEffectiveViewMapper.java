package com.sovereign.eib.service;

import com.sovereign.eib.domain.EffectiveCapabilityAffordance;
import com.sovereign.eib.domain.EffectiveDeviceView;
import com.sovereign.eib.domain.EffectiveEndpointView;
import com.sovereign.eib.domain.EffectiveRoomView;
import com.sovereign.eib.domain.EffectiveTemporalActView;
import com.sovereign.eib.domain.EffectiveZoneView;
import com.sovereign.eib.domain.EibRequestContext;
import com.sovereign.eib.northbound.dto.NorthboundCapabilityViewDto;
import com.sovereign.eib.northbound.dto.NorthboundDeviceViewDto;
import com.sovereign.eib.northbound.dto.NorthboundEndpointViewDto;
import com.sovereign.eib.northbound.dto.NorthboundRoomViewDto;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.NorthboundZoneViewDto;
import com.sovereign.eib.ref.EibEffectiveRefCodec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EibEffectiveViewMapper {

    private final EibEffectiveRefCodec codec;

    public EibEffectiveViewMapper(EibEffectiveRefCodec codec) {
        this.codec = codec;
    }

    public EffectiveRoomView room(String habitatId, NorthboundRoomViewDto room) {
        return new EffectiveRoomView(
                codec.generateRef("eib.room", habitatId, room.roomId()),
                room.roomName(),
                refs("eib.device", habitatId, room.deviceIds()),
                "VISIBLE",
                List.of()
        );
    }

    public EffectiveZoneView zone(String habitatId, NorthboundZoneViewDto zone) {
        return new EffectiveZoneView(
                codec.generateRef("eib.zone", habitatId, zone.zoneId()),
                zone.zoneName(),
                refs("eib.device", habitatId, zone.deviceIds()),
                "VISIBLE",
                List.of()
        );
    }

    public EffectiveDeviceView device(
            String habitatId,
            NorthboundDeviceViewDto device,
            List<NorthboundEndpointViewDto> endpoints,
            EibRequestContext ctx
    ) {
        List<EffectiveEndpointView> effectiveEndpoints = endpoints.stream()
                .filter(endpoint -> device.deviceId().equals(endpoint.deviceId()))
                .map(endpoint -> endpoint(habitatId, endpoint, ctx))
                .toList();
        return new EffectiveDeviceView(
                codec.generateRef("eib.device", habitatId, device.deviceId()),
                ctx.diagnosticAdminAuthorized() ? device.deviceId() : null,
                firstNonBlank(device.displayName(), device.alias(), device.deviceId()),
                device.roomId() == null ? null : codec.generateRef("eib.room", habitatId, device.roomId()),
                device.zoneId() == null ? null : codec.generateRef("eib.zone", habitatId, device.zoneId()),
                effectiveEndpoints,
                capabilities(habitatId, device.capabilities()),
                "UNKNOWN",
                "UNKNOWN",
                "VISIBLE",
                "UNKNOWN",
                List.of()
        );
    }

    public EffectiveEndpointView endpoint(String habitatId, NorthboundEndpointViewDto endpoint, EibRequestContext ctx) {
        return new EffectiveEndpointView(
                codec.generateRef("eib.endpoint", habitatId, endpoint.endpointId()),
                ctx.diagnosticAdminAuthorized() ? endpoint.endpointId() : null,
                firstNonBlank(endpoint.displayName(), endpoint.alias(), endpoint.endpointId()),
                capabilities(habitatId, endpoint.capabilities()),
                "UNKNOWN",
                "UNKNOWN",
                "VISIBLE",
                "UNKNOWN",
                List.of()
        );
    }

    public EffectiveTemporalActView temporal(String habitatId, NorthboundTemporalActViewDto act, EibRequestContext ctx) {
        return new EffectiveTemporalActView(
                codec.generateRef("eib.temporal", habitatId, act.temporalActId()),
                ctx.diagnosticAdminAuthorized() ? act.temporalActId() : null,
                act.status(),
                act.dueAt(),
                act.label(),
                act.signalKind(),
                act.notificationTargetRef(),
                "VISIBLE",
                "UNKNOWN",
                List.of()
        );
    }

    private List<EffectiveCapabilityAffordance> capabilities(String habitatId, List<NorthboundCapabilityViewDto> capabilities) {
        if (capabilities == null) {
            return List.of();
        }
        return capabilities.stream()
                .map(capability -> new EffectiveCapabilityAffordance(
                        codec.generateRef("eib.capability", habitatId, capability.capabilityId()),
                        capability.kind(),
                        firstNonBlank(capability.name(), capability.kind(), capability.capabilityId()),
                        "VISIBLE",
                        "UNKNOWN",
                        false,
                        null
                ))
                .toList();
    }

    private List<String> refs(String type, String habitatId, List<String> canonicalIds) {
        if (canonicalIds == null) {
            return List.of();
        }
        return canonicalIds.stream()
                .map(id -> codec.generateRef(type, habitatId, id))
                .toList();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
