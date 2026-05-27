package com.sovereign.connect.adapter.northbound.http;

import com.sovereign.connect.core.northbound.ScCoreNorthboundFacade;
import com.sovereign.connect.core.northbound.ScNorthboundResponse;
import com.sovereign.connect.core.northbound.runtime.NorthboundDeviceHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundEndpointHealthView;
import com.sovereign.connect.core.northbound.runtime.NorthboundRuntimeStateView;
import com.sovereign.connect.core.northbound.topology.NorthboundCapabilityView;
import com.sovereign.connect.core.northbound.topology.NorthboundDeviceView;
import com.sovereign.connect.core.northbound.topology.NorthboundEndpointView;
import com.sovereign.connect.core.northbound.topology.NorthboundRoomView;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologySnapshot;
import com.sovereign.connect.core.northbound.topology.NorthboundTopologyVersionView;
import com.sovereign.connect.core.northbound.topology.NorthboundZoneView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ScNorthboundTopologyHttpController.class)
class ScNorthboundTopologyHttpControllerTest {

    private static final String HABITAT = "habitat-001";
    private static final String DEVICE_ID = "device.tuya.light-1";
    private static final String ENDPOINT_ID = "endpoint.tuya.light-1.main";
    private static final Instant NOW = Instant.parse("2026-05-26T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ScCoreNorthboundFacade facade;

    @Test
    void getTopology_returns200_whenFacadeReturnsOk() throws Exception {
        when(facade.getTopologySnapshot(HABITAT)).thenReturn(ScNorthboundResponse.ok(snapshot()));

        mockMvc.perform(get("/sc/v1/habitats/{h}/topology", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OK"))
            .andExpect(jsonPath("$.payload.habitatId").value(HABITAT));
    }

    @Test
    void getTopologyVersion_returns200() throws Exception {
        when(facade.getTopologyVersion(HABITAT))
            .thenReturn(ScNorthboundResponse.ok(new NorthboundTopologyVersionView(HABITAT, "7", "HABITAT", HABITAT)));

        mockMvc.perform(get("/sc/v1/habitats/{h}/topology/version", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.value").value("7"));
    }

    @Test
    void listRooms_returns200() throws Exception {
        when(facade.listRooms(HABITAT)).thenReturn(ScNorthboundResponse.ok(List.of(room())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/rooms", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].roomId").value("room.living"));
    }

    @Test
    void listZones_returns200() throws Exception {
        when(facade.listZones(HABITAT)).thenReturn(ScNorthboundResponse.ok(List.of(zone())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/zones", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].zoneId").value("zone.living.main"));
    }

    @Test
    void listDevices_returns200() throws Exception {
        when(facade.listDevices(HABITAT)).thenReturn(ScNorthboundResponse.ok(List.of(device())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].deviceId").value(DEVICE_ID));
    }

    @Test
    void getDevice_returns404_whenFacadeReturnsNotFound() throws Exception {
        when(facade.getDevice(HABITAT, DEVICE_ID))
            .thenReturn(ScNorthboundResponse.notFound("DEVICE_NOT_FOUND", "device not found"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices/{d}", HABITAT, DEVICE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("NOT_FOUND"))
            .andExpect(jsonPath("$.error.source").value("northbound.query"));
    }

    @Test
    void getDeviceHealth_returns200_withPayload() throws Exception {
        when(facade.getDeviceHealth(HABITAT, DEVICE_ID))
            .thenReturn(ScNorthboundResponse.ok(new NorthboundDeviceHealthView(
                DEVICE_ID, "HEALTHY", "DERIVED_FROM_ENDPOINTS", 1, NOW, List.of())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices/{d}/health", HABITAT, DEVICE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.source").value("DERIVED_FROM_ENDPOINTS"));
    }

    @Test
    void getDeviceRuntimeState_returns404_whenFacadeReturnsNotFound() throws Exception {
        when(facade.getDeviceRuntimeState(HABITAT, DEVICE_ID))
            .thenReturn(ScNorthboundResponse.notFound("no runtime state recorded"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices/{d}/runtime-state", HABITAT, DEVICE_ID))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    void listEndpoints_returns200() throws Exception {
        when(facade.listEndpoints(HABITAT)).thenReturn(ScNorthboundResponse.ok(List.of(endpoint())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/endpoints", HABITAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].endpointId").value(ENDPOINT_ID));
    }

    @Test
    void getEndpoint_returns200() throws Exception {
        when(facade.getEndpoint(HABITAT, ENDPOINT_ID)).thenReturn(ScNorthboundResponse.ok(endpoint()));

        mockMvc.perform(get("/sc/v1/habitats/{h}/endpoints/{e}", HABITAT, ENDPOINT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.endpointId").value(ENDPOINT_ID));
    }

    @Test
    void getEndpointHealth_returns200() throws Exception {
        when(facade.getEndpointHealth(HABITAT, ENDPOINT_ID))
            .thenReturn(ScNorthboundResponse.ok(new NorthboundEndpointHealthView(ENDPOINT_ID, "HEALTHY", NOW, "ok")));

        mockMvc.perform(get("/sc/v1/habitats/{h}/endpoints/{e}/health", HABITAT, ENDPOINT_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.status").value("HEALTHY"));
    }

    @Test
    void getEndpointRuntimeState_returns501_whenFacadeReturnsUnsupportedProfile() throws Exception {
        when(facade.getEndpointRuntimeState(HABITAT, ENDPOINT_ID))
            .thenReturn(ScNorthboundResponse.unsupportedProfile("endpoint runtime state not supported"));

        mockMvc.perform(get("/sc/v1/habitats/{h}/endpoints/{e}/runtime-state", HABITAT, ENDPOINT_ID))
            .andExpect(status().isNotImplemented())
            .andExpect(jsonPath("$.status").value("UNSUPPORTED_PROFILE"));
    }

    @Test
    void listDevicesLocatedIn_returns200() throws Exception {
        when(facade.listDevicesLocatedIn(HABITAT, "room.living")).thenReturn(ScNorthboundResponse.ok(List.of(device())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/locations/{location}/devices", HABITAT, "room.living"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].deviceId").value(DEVICE_ID));
    }

    @Test
    void listEndpointsLocatedIn_returns200() throws Exception {
        when(facade.listEndpointsLocatedIn(HABITAT, "room.living"))
            .thenReturn(ScNorthboundResponse.ok(List.of(endpoint())));

        mockMvc.perform(get("/sc/v1/habitats/{h}/locations/{location}/endpoints", HABITAT, "room.living"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload[0].endpointId").value(ENDPOINT_ID));
    }

    @Test
    void getDeviceRuntimeState_returns200_whenFacadeReturnsOk() throws Exception {
        when(facade.getDeviceRuntimeState(HABITAT, DEVICE_ID))
            .thenReturn(ScNorthboundResponse.ok(new NorthboundRuntimeStateView(
                HABITAT, DEVICE_ID, "DEVICE", Map.of("power", true), NOW)));

        mockMvc.perform(get("/sc/v1/habitats/{h}/devices/{d}/runtime-state", HABITAT, DEVICE_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.state.power").value(true));
    }

    private NorthboundTopologySnapshot snapshot() {
        return new NorthboundTopologySnapshot(
            HABITAT, "7", HABITAT, List.of(room()), List.of(zone()), List.of(device()), List.of(endpoint()), NOW);
    }

    private NorthboundRoomView room() {
        return new NorthboundRoomView("room.living", "Living", List.of("zone.living.main"), List.of(DEVICE_ID),
            List.of(ENDPOINT_ID));
    }

    private NorthboundZoneView zone() {
        return new NorthboundZoneView("zone.living.main", "Main", "room.living", List.of(DEVICE_ID),
            List.of(ENDPOINT_ID));
    }

    private NorthboundDeviceView device() {
        return new NorthboundDeviceView(DEVICE_ID, "light-1", "Light", "room.living", "zone.living.main",
            "LIGHT", "TUYA", List.of(ENDPOINT_ID), List.of(capability()), "light-1");
    }

    private NorthboundEndpointView endpoint() {
        return new NorthboundEndpointView(ENDPOINT_ID, DEVICE_ID, "main", "Main", "SWITCH", "room.living",
            "zone.living.main", List.of(capability()), "main");
    }

    private NorthboundCapabilityView capability() {
        return new NorthboundCapabilityView("capability.tuya.light-1.power", "Power", "BINARY_SWITCH");
    }
}
