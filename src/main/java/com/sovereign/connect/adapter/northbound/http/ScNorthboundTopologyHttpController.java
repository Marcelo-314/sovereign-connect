package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.runtime.NorthboundDeviceHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundEndpointHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRuntimeStateView;
import com.sovereign.connect.core.northbound.topology.NorthboundDeviceView;
import com.sovereign.connect.core.northbound.topology.NorthboundEndpointView;
import com.sovereign.connect.core.northbound.topology.NorthboundRoomView;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologyVersionView;
import com.sovereign.connect.core.northbound.topology.NorthboundZoneView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnProperty(prefix = "sc.northbound.http", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/sc/v1/habitats/{habitatId}")
public class ScNorthboundTopologyHttpController {

    private final ScCoreNorthboundFacade facade;

    public ScNorthboundTopologyHttpController(ScCoreNorthboundFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/topology")
    public ResponseEntity<ScNorthboundResponse<NorthboundTopologySnapshot>> getTopologySnapshot(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getTopologySnapshot(habitatId));
    }

    @GetMapping("/topology/version")
    public ResponseEntity<ScNorthboundResponse<NorthboundTopologyVersionView>> getTopologyVersion(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getTopologyVersion(habitatId));
    }

    @GetMapping("/rooms")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundRoomView>>> listRooms(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.listRooms(habitatId));
    }

    @GetMapping("/zones")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundZoneView>>> listZones(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.listZones(habitatId));
    }

    @GetMapping("/devices")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundDeviceView>>> listDevices(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.listDevices(habitatId));
    }

    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<ScNorthboundResponse<NorthboundDeviceView>> getDevice(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("deviceId") String deviceId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getDevice(habitatId, deviceId));
    }

    @GetMapping("/devices/{deviceId}/health")
    public ResponseEntity<ScNorthboundResponse<NorthboundDeviceHealthView>> getDeviceHealth(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("deviceId") String deviceId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getDeviceHealth(habitatId, deviceId));
    }

    @GetMapping("/devices/{deviceId}/runtime-state")
    public ResponseEntity<ScNorthboundResponse<NorthboundRuntimeStateView>> getDeviceRuntimeState(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("deviceId") String deviceId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getDeviceRuntimeState(habitatId, deviceId));
    }

    @GetMapping("/endpoints")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundEndpointView>>> listEndpoints(
        @PathVariable("habitatId") String habitatId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.listEndpoints(habitatId));
    }

    @GetMapping("/endpoints/{endpointId}")
    public ResponseEntity<ScNorthboundResponse<NorthboundEndpointView>> getEndpoint(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("endpointId") String endpointId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getEndpoint(habitatId, endpointId));
    }

    @GetMapping("/endpoints/{endpointId}/health")
    public ResponseEntity<ScNorthboundResponse<NorthboundEndpointHealthView>> getEndpointHealth(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("endpointId") String endpointId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getEndpointHealth(habitatId, endpointId));
    }

    @GetMapping("/endpoints/{endpointId}/runtime-state")
    public ResponseEntity<ScNorthboundResponse<NorthboundRuntimeStateView>> getEndpointRuntimeState(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("endpointId") String endpointId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.getEndpointRuntimeState(habitatId, endpointId));
    }

    @GetMapping("/locations/{roomOrZoneId}/devices")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundDeviceView>>> listDevicesLocatedIn(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("roomOrZoneId") String roomOrZoneId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.listDevicesLocatedIn(habitatId, roomOrZoneId));
    }

    @GetMapping("/locations/{roomOrZoneId}/endpoints")
    public ResponseEntity<ScNorthboundResponse<List<NorthboundEndpointView>>> listEndpointsLocatedIn(
        @PathVariable("habitatId") String habitatId,
        @PathVariable("roomOrZoneId") String roomOrZoneId
    ) {
        return ScNorthboundHttpResponseMapper.toResponseEntity(facade.listEndpointsLocatedIn(habitatId, roomOrZoneId));
    }
}
