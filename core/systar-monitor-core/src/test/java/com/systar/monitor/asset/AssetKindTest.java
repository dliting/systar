package com.systar.monitor.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetKindTest {

    @Test
    @DisplayName("SPACE is compound and not monitor")
    void spaceIsCompound() {
        assertThat(AssetKind.SPACE.isCompound()).isTrue();
        assertThat(AssetKind.SPACE.isMonitor()).isFalse();
    }

    @Test
    @DisplayName("DEVICE is compound and not monitor")
    void deviceIsCompound() {
        assertThat(AssetKind.DEVICE.isCompound()).isTrue();
        assertThat(AssetKind.DEVICE.isMonitor()).isFalse();
    }

    @Test
    @DisplayName("SERVICE is neither compound nor monitor — it manages monitors")
    void serviceIsNeither() {
        assertThat(AssetKind.SERVICE.isCompound()).isFalse();
        assertThat(AssetKind.SERVICE.isMonitor()).isFalse();
    }

    @Test
    @DisplayName("PROBE is not compound and is monitor")
    void probeIsMonitor() {
        assertThat(AssetKind.PROBE.isCompound()).isFalse();
        assertThat(AssetKind.PROBE.isMonitor()).isTrue();
    }

    @Test
    @DisplayName("CONTROL is not compound and is monitor")
    void controlIsMonitor() {
        assertThat(AssetKind.CONTROL.isCompound()).isFalse();
        assertThat(AssetKind.CONTROL.isMonitor()).isTrue();
    }

    @Test
    @DisplayName("All five kinds exist")
    void allKindsPresent() {
        assertThat(AssetKind.values()).hasSize(5);
        assertThat(AssetKind.values()).containsExactly(
                AssetKind.SPACE, AssetKind.DEVICE,
                AssetKind.SERVICE, AssetKind.PROBE, AssetKind.CONTROL);
    }
}
