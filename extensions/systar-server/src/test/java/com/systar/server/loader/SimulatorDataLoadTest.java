package com.systar.server.loader;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Timeout(value = 3, unit = TimeUnit.MINUTES)
@DisplayName("Simulator Integration Seed Data")
class SimulatorDataLoadTest {

    @Autowired
    private AssetStore store;

    @Test
    @DisplayName("all 4 simulator services are loaded")
    void servicesLoaded() {
        assertThat(store.findAsset(110)).isNotNull();
        assertThat(store.findAsset(111)).isNotNull();
        assertThat(store.findAsset(112)).isNotNull();
        assertThat(store.findAsset(113)).isNotNull();
    }

    @Test
    @DisplayName("all 4 simulator devices are loaded")
    void devicesLoaded() {
        assertThat(store.findAsset(1101)).isNotNull();
        assertThat(store.findAsset(1102)).isNotNull();
        assertThat(store.findAsset(1103)).isNotNull();
        assertThat(store.findAsset(1104)).isNotNull();
    }

    @Test
    @DisplayName("19 simulator probes are loaded (15 Modbus + 4 OPC UA)")
    void probesLoaded() {
        for (int i = 1201; i <= 1219; i++) {
            assertThat(store.findAsset(i))
                    .as("probe id=%d", i)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("3 simulator controls are loaded")
    void controlsLoaded() {
        assertThat(store.findAsset(3201)).isNotNull();
        assertThat(store.findAsset(3202)).isNotNull();
        assertThat(store.findAsset(3203)).isNotNull();
    }

    @Test
    @DisplayName("Modbus probes have RegisterAddr attribute")
    void modbusProbesHaveRegisterAddr() {
        Asset<?> probe = store.findAsset(1201);
        assertThat(probe).isNotNull();
        assertThat((String) probe.getMetadata("RegisterAddr")).isNotNull();
    }

    @Test
    @DisplayName("OPC UA probes have NodeId attribute")
    void opcuaProbesHaveNodeId() {
        Asset<?> probe = store.findAsset(1216);
        assertThat(probe).isNotNull();
        assertThat((String) probe.getMetadata("NodeId")).isEqualTo("ns=2;s=OutdoorTemperature");
    }

    @Test
    @DisplayName("Modbus services have host/port attributes")
    void modbusServiceHasHostPort() {
        Asset<?> svc = store.findAsset(110);
        assertThat(svc).isNotNull();
        assertThat((String) svc.getMetadata("host")).isEqualTo("localhost");
        assertThat((String) svc.getMetadata("port")).isEqualTo("55502");
        assertThat((String) svc.getMetadata("unitId")).isEqualTo("1");
    }

    @Test
    @DisplayName("OPC UA service has EndpointUrl attribute")
    void opcuaServiceHasEndpointUrl() {
        Asset<?> svc = store.findAsset(113);
        assertThat(svc).isNotNull();
        assertThat((String) svc.getMetadata("EndpointUrl"))
                .isEqualTo("opc.tcp://localhost:55503/systar-simulator");
    }

    @Test
    @DisplayName("probes reference correct service as source")
    void probesReferenceCorrectService() {
        Asset<?> hvacProbe = store.findAsset(1201);
        assertThat(hvacProbe).isInstanceOf(Probe.class);
        Probe probe = (Probe) hvacProbe;
        assertThat(probe.getSource()).isNotNull();
        assertThat(probe.getSource().getId()).isEqualTo(110);

        Asset<?> upsProbe = store.findAsset(1206);
        assertThat(upsProbe).isInstanceOf(Probe.class);
        assertThat(((Probe) upsProbe).getSource().getId()).isEqualTo(111);

        Asset<?> opcuaProbe = store.findAsset(1216);
        assertThat(opcuaProbe).isInstanceOf(Probe.class);
        assertThat(((Probe) opcuaProbe).getSource().getId()).isEqualTo(113);
    }

    @Test
    @DisplayName("controls reference correct service and parent device")
    void controlsReferenceCorrectService() {
        Asset<?> fanSwitch = store.findAsset(3201);
        assertThat(fanSwitch).isInstanceOf(Control.class);
        Control ctrl = (Control) fanSwitch;
        assertThat(ctrl.getSource().getId()).isEqualTo(110);
        assertThat(ctrl.getParentId()).isEqualTo(1101);

        Asset<?> upsSwitch = store.findAsset(3203);
        assertThat(upsSwitch).isInstanceOf(Control.class);
        assertThat(((Control) upsSwitch).getSource().getId()).isEqualTo(111);
        assertThat(((Control) upsSwitch).getParentId()).isEqualTo(1102);
    }
}
