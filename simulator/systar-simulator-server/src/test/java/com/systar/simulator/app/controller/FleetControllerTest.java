package com.systar.simulator.app.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systar.simulator.app.controller.dto.FaultRequest;
import com.systar.simulator.app.controller.dto.OverrideRequest;
import com.systar.simulator.fleet.FleetManager;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.DeviceStatus;
import com.systar.simulator.model.FaultType;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FleetControllerTest {

    private static final int TIMEOUT_SECONDS = 180;

    @Mock
    private FleetManager fleetManager;

    private MockMvc       mockMvc;
    private ObjectMapper  objectMapper;

    @BeforeEach
    void setUp() {
        FleetController controller = new FleetController(fleetManager);
        mockMvc    = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    // ======================== Helper ========================

    private SimulatedDevice createDevice(String id, String name) {
        SimulatedDevice device = new SimulatedDevice();
        device.setId(id);
        device.setName(name);
        device.setProtocol(ProtocolType.MODBUS_TCP);
        device.setStatus(DeviceStatus.RUNNING);
        return device;
    }

    private DataPoint createDataPoint(String id, String name, Object value) {
        DataPoint dp = new DataPoint();
        dp.setId(id);
        dp.setName(name);
        dp.setCurrentValue(value);
        return dp;
    }

    // ======================== GET /api/fleet ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void listDevices_returnsDeviceSummaries() throws Exception {
        SimulatedDevice dev1 = createDevice("dev-1", "Device One");
        SimulatedDevice dev2 = createDevice("dev-2", "Device Two");
        dev2.setProtocol(ProtocolType.OPC_UA);
        dev2.setStatus(DeviceStatus.STOPPED);
        when(fleetManager.listDevices()).thenReturn(List.of(dev1, dev2));

        mockMvc.perform(get("/api/fleet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("dev-1"))
                .andExpect(jsonPath("$[0].name").value("Device One"))
                .andExpect(jsonPath("$[0].protocol").value("MODBUS_TCP"))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[1].id").value("dev-2"))
                .andExpect(jsonPath("$[1].protocol").value("OPC_UA"))
                .andExpect(jsonPath("$[1].status").value("STOPPED"));

        verify(fleetManager).listDevices();
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void listDevices_returnsEmptyList() throws Exception {
        when(fleetManager.listDevices()).thenReturn(List.of());

        mockMvc.perform(get("/api/fleet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ======================== POST /api/fleet/start ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startAll_callsFleetManager() throws Exception {
        doNothing().when(fleetManager).startAll();

        mockMvc.perform(post("/api/fleet/start"))
                .andExpect(status().isOk());

        verify(fleetManager).startAll();
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startAll_returns500OnException() throws Exception {
        doThrow(new RuntimeException("startup failed")).when(fleetManager).startAll();

        mockMvc.perform(post("/api/fleet/start"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("startup failed"));
    }

    // ======================== POST /api/fleet/stop ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void stopAll_callsFleetManager() throws Exception {
        doNothing().when(fleetManager).stopAll();

        mockMvc.perform(post("/api/fleet/stop"))
                .andExpect(status().isOk());

        verify(fleetManager).stopAll();
    }

    // ======================== GET /api/fleet/{deviceId} ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void getDevice_returnsDevice() throws Exception {
        SimulatedDevice device = createDevice("dev-1", "Device One");
        when(fleetManager.getDevice("dev-1")).thenReturn(device);

        mockMvc.perform(get("/api/fleet/dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("dev-1"))
                .andExpect(jsonPath("$.name").value("Device One"));

        verify(fleetManager).getDevice("dev-1");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void getDevice_returns404WhenNotFound() throws Exception {
        when(fleetManager.getDevice("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/fleet/nonexistent"))
                .andExpect(status().isNotFound());

        verify(fleetManager).getDevice("nonexistent");
    }

    // ======================== POST /api/fleet/{deviceId}/start ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startDevice_callsFleetManager() throws Exception {
        doNothing().when(fleetManager).startDevice("dev-1");

        mockMvc.perform(post("/api/fleet/dev-1/start"))
                .andExpect(status().isOk());

        verify(fleetManager).startDevice("dev-1");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void startDevice_returns500OnException() throws Exception {
        doThrow(new RuntimeException("device start failed")).when(fleetManager).startDevice("dev-1");

        mockMvc.perform(post("/api/fleet/dev-1/start"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("device start failed"));
    }

    // ======================== POST /api/fleet/{deviceId}/stop ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void stopDevice_callsFleetManager() throws Exception {
        doNothing().when(fleetManager).stopDevice("dev-1");

        mockMvc.perform(post("/api/fleet/dev-1/stop"))
                .andExpect(status().isOk());

        verify(fleetManager).stopDevice("dev-1");
    }

    // ======================== GET /api/fleet/{deviceId}/points ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void listPoints_returnsDataPointSummaries() throws Exception {
        SimulatedDevice device = createDevice("dev-1", "Device One");
        DataPoint dp1 = createDataPoint("dp-1", "Temperature", 23.5);
        DataPoint dp2 = createDataPoint("dp-2", "Pressure", 101.3);
        dp2.setOverride(200.0); // has an active override
        device.setDataPoints(List.of(dp1, dp2));
        when(fleetManager.getDevice("dev-1")).thenReturn(device);

        mockMvc.perform(get("/api/fleet/dev-1/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("dp-1"))
                .andExpect(jsonPath("$[0].currentValue").value(23.5))
                .andExpect(jsonPath("$[0].overridden").value(false))
                .andExpect(jsonPath("$[1].id").value("dp-2"))
                .andExpect(jsonPath("$[1].overridden").value(true));

        verify(fleetManager).getDevice("dev-1");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void listPoints_returns404WhenDeviceNotFound() throws Exception {
        when(fleetManager.getDevice("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/fleet/nonexistent/points"))
                .andExpect(status().isNotFound());
    }

    // ======================== PUT /api/fleet/{deviceId}/points/{pointId} ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void overrideValue_callsFleetManager() throws Exception {
        OverrideRequest request = new OverrideRequest();
        request.setValue(42.0);
        request.setDurationSeconds(60);
        doNothing().when(fleetManager).applyOverride("dev-1", "dp-1", 42.0);

        mockMvc.perform(put("/api/fleet/dev-1/points/dp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());

        verify(fleetManager).applyOverride("dev-1", "dp-1", 42.0);
    }

    // ======================== DELETE /api/fleet/{deviceId}/points/{pointId}/override ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clearOverride_callsFleetManager() throws Exception {
        doNothing().when(fleetManager).clearOverride("dev-1", "dp-1");

        mockMvc.perform(delete("/api/fleet/dev-1/points/dp-1/override"))
                .andExpect(status().isOk());

        verify(fleetManager).clearOverride("dev-1", "dp-1");
    }

    // ======================== POST /api/fleet/{deviceId}/fault ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void injectFault_callsFleetManager() throws Exception {
        FaultRequest request = new FaultRequest();
        request.setType(FaultType.DISCONNECT);
        request.setDurationSeconds(30);
        doNothing().when(fleetManager).injectFault("dev-1", FaultType.DISCONNECT);

        mockMvc.perform(post("/api/fleet/dev-1/fault")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());

        verify(fleetManager).injectFault("dev-1", FaultType.DISCONNECT);
    }

    // ======================== DELETE /api/fleet/{deviceId}/fault ========================

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clearFault_callsFleetManager() throws Exception {
        doNothing().when(fleetManager).clearFault("dev-1");

        mockMvc.perform(delete("/api/fleet/dev-1/fault"))
                .andExpect(status().isOk());

        verify(fleetManager).clearFault("dev-1");
    }
}
