package com.systar.monitor.result;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorResultTest {

    private Probe monitor;

    @BeforeEach
    void setUp() {
        monitor = new Probe();
        monitor.init(new ProbeType("pt"), 1, "probe1");
    }

    @Test
    @DisplayName("Value constructor sets value and sampleTime")
    void valueConstructor() {
        MonitorResult result = new MonitorResult(monitor, 42.5);

        assertThat(result.getMonitor()).isSameAs(monitor);
        assertThat(result.getValue()).isEqualTo(42.5);
        assertThat(result.getError()).isNull();
        assertThat(result.hasError()).isFalse();
        assertThat(result.getSampleTime()).isGreaterThan(0);
        assertThat(result.isChanged()).isFalse();
        assertThat(result.getStatus()).isNull();
    }

    @Test
    @DisplayName("Error constructor sets error")
    void errorConstructor() {
        MonitorResult result = new MonitorResult(monitor, "connection failed");

        assertThat(result.getMonitor()).isSameAs(monitor);
        assertThat(result.getValue()).isNull();
        assertThat(result.getError()).isEqualTo("connection failed");
        assertThat(result.hasError()).isTrue();
    }

    @Test
    @DisplayName("Empty constructor sets only monitor and timestamp")
    void emptyConstructor() {
        MonitorResult result = new MonitorResult(monitor);

        assertThat(result.getMonitor()).isSameAs(monitor);
        assertThat(result.getValue()).isNull();
        assertThat(result.getError()).isNull();
        assertThat(result.hasError()).isFalse();
        assertThat(result.getSampleTime()).isGreaterThan(0);
    }

    @Test
    @DisplayName("setValue and getValue round-trip")
    void valueSetterGetter() {
        MonitorResult result = new MonitorResult(monitor);
        result.setValue("newVal");
        assertThat(result.getValue()).isEqualTo("newVal");
    }

    @Test
    @DisplayName("setError and getError round-trip")
    void errorSetterGetter() {
        MonitorResult result = new MonitorResult(monitor);
        result.setError("oops");
        assertThat(result.getError()).isEqualTo("oops");
        assertThat(result.hasError()).isTrue();
    }

    @Test
    @DisplayName("setSampleTime and getSampleTime round-trip")
    void sampleTimeSetterGetter() {
        MonitorResult result = new MonitorResult(monitor);
        result.setSampleTime(123456789L);
        assertThat(result.getSampleTime()).isEqualTo(123456789L);
    }

    @Test
    @DisplayName("changed flag setter/getter")
    void changedFlag() {
        MonitorResult result = new MonitorResult(monitor);
        assertThat(result.isChanged()).isFalse();
        result.setChanged(true);
        assertThat(result.isChanged()).isTrue();
    }

    @Test
    @DisplayName("status setter/getter")
    void statusSetterGetter() {
        MonitorResult result = new MonitorResult(monitor);
        result.setStatus(AssetState.WARNING);
        assertThat(result.getStatus()).isEqualTo(AssetState.WARNING);
    }

    @Test
    @DisplayName("toString contains key fields")
    void toStringContent() {
        MonitorResult result = new MonitorResult(monitor, 42);
        String str = result.toString();
        assertThat(str).contains("MonitorResult")
                .contains("probe1")
                .contains("42");
    }
}
