package com.sovereign.eib.northbound;

import com.sovereign.eib.northbound.dto.NorthboundCancelTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundCreateSignalTemporalActRequestDto;
import com.sovereign.eib.northbound.dto.NorthboundDeviceHealthViewDto;
import com.sovereign.eib.northbound.dto.NorthboundDiagnosticsViewDto;
import com.sovereign.eib.northbound.dto.NorthboundEndpointHealthViewDto;
import com.sovereign.eib.northbound.dto.NorthboundRuntimeStateViewDto;
import com.sovereign.eib.northbound.dto.NorthboundTemporalActViewDto;
import com.sovereign.eib.northbound.dto.NorthboundTopologySnapshotDto;
import com.sovereign.eib.northbound.dto.NorthboundTopologyVersionViewDto;
import com.sovereign.eib.northbound.dto.ScEnvelope;

import java.util.List;

public interface EibScNorthboundClient {

    ScEnvelope<NorthboundTopologySnapshotDto> getTopologySnapshot(String habitatId);

    ScEnvelope<NorthboundTopologyVersionViewDto> getTopologyVersion(String habitatId);

    ScEnvelope<NorthboundDeviceHealthViewDto> getDeviceHealth(String habitatId, String deviceId);

    ScEnvelope<NorthboundEndpointHealthViewDto> getEndpointHealth(String habitatId, String endpointId);

    ScEnvelope<NorthboundRuntimeStateViewDto> getDeviceRuntimeState(String habitatId, String deviceId);

    ScEnvelope<NorthboundRuntimeStateViewDto> getEndpointRuntimeState(String habitatId, String endpointId);

    ScEnvelope<NorthboundDiagnosticsViewDto> getDiagnostics(String habitatId);

    ScEnvelope<List<NorthboundTemporalActViewDto>> listTemporalActs(String habitatId, String mode, Integer maxResults);

    ScEnvelope<NorthboundTemporalActViewDto> getTemporalAct(String habitatId, String temporalActId);

    ScEnvelope<NorthboundTemporalActViewDto> createSignalTemporalAct(
            String habitatId,
            NorthboundCreateSignalTemporalActRequestDto request
    );

    ScEnvelope<NorthboundTemporalActViewDto> cancelTemporalAct(
            String habitatId,
            String temporalActId,
            NorthboundCancelTemporalActRequestDto request
    );
}
