package com.sovereign.connect.core.northbound;

import com.sovereign.connect.core.northbound.runtime.NorthboundDeviceHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundEndpointHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRecoveryStatusView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRuntimeStateView;
import com.sovereign.connect.core.northbound.runtime.NorthboundTemporalRuntimeStatusView;
import com.sovereign.connect.core.northbound.temporal.NorthboundCancelTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundCreateSignalTemporalActRequest;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActFilter;
import com.sovereign.connect.core.northbound.temporal.NorthboundTemporalActView;
import com.sovereign.connect.core.northbound.topology.NorthboundDeviceView;
import com.sovereign.connect.core.northbound.topology.NorthboundEndpointView;
import com.sovereign.connect.core.northbound.topology.NorthboundRoomView;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologyVersionView;
import com.sovereign.connect.core.northbound.topology.NorthboundZoneView;

import java.util.List;

public interface ScCoreNorthboundFacade {
    ScNorthboundResponse<NorthboundTopologySnapshot> getTopologySnapshot(String habitatId);

    ScNorthboundResponse<NorthboundTopologyVersionView> getTopologyVersion(String habitatId);

    ScNorthboundResponse<NorthboundDeviceView> getDevice(String habitatId, String deviceId);

    ScNorthboundResponse<NorthboundEndpointView> getEndpoint(String habitatId, String endpointId);

    ScNorthboundResponse<List<NorthboundRoomView>> listRooms(String habitatId);

    ScNorthboundResponse<List<NorthboundZoneView>> listZones(String habitatId);

    ScNorthboundResponse<List<NorthboundDeviceView>> listDevices(String habitatId);

    ScNorthboundResponse<List<NorthboundEndpointView>> listEndpoints(String habitatId);

    ScNorthboundResponse<List<NorthboundDeviceView>> listDevicesLocatedIn(String habitatId, String roomOrZoneId);

    ScNorthboundResponse<List<NorthboundEndpointView>> listEndpointsLocatedIn(String habitatId, String roomOrZoneId);

    ScNorthboundResponse<NorthboundEndpointHealthView> getEndpointHealth(String habitatId, String endpointId);

    ScNorthboundResponse<NorthboundDeviceHealthView> getDeviceHealth(String habitatId, String deviceId);

    ScNorthboundResponse<NorthboundRuntimeStateView> getDeviceRuntimeState(String habitatId, String deviceId);

    ScNorthboundResponse<NorthboundRuntimeStateView> getEndpointRuntimeState(String habitatId, String endpointId);

    ScNorthboundResponse<NorthboundRecoveryStatusView> getRecoveryStatus(String habitatId);

    ScNorthboundResponse<NorthboundTemporalRuntimeStatusView> getTemporalRuntimeStatus(String habitatId);

    ScNorthboundResponse<NorthboundTemporalActView> createSignalTemporalAct(
        String habitatId,
        NorthboundCreateSignalTemporalActRequest request
    );

    ScNorthboundResponse<NorthboundTemporalActView> cancelTemporalAct(
        String habitatId,
        NorthboundCancelTemporalActRequest request
    );

    ScNorthboundResponse<NorthboundTemporalActView> getTemporalAct(String habitatId, String temporalActId);

    ScNorthboundResponse<List<NorthboundTemporalActView>> listTemporalActs(
        String habitatId,
        NorthboundTemporalActFilter filter
    );
}
