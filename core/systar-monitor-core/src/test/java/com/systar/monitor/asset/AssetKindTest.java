package com.systar.monitor.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetKindTest {

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
    @DisplayName("All four kinds exist")
    void allKindsPresent() {
        assertThat(AssetKind.values()).hasSize(4);
        assertThat(AssetKind.values()).containsExactly(
                AssetKind.DEVICE, AssetKind.SERVICE, AssetKind.PROBE, AssetKind.CONTROL);
    }

    @Test
    @DisplayName("codes are stable storage values matching t_asset.kind")
    void codesMatchStorageValues() {
        assertThat(AssetKind.DEVICE.getCode()).isEqualTo(1);
        assertThat(AssetKind.SERVICE.getCode()).isEqualTo(2);
        assertThat(AssetKind.PROBE.getCode()).isEqualTo(3);
        assertThat(AssetKind.CONTROL.getCode()).isEqualTo(4);
    }

    @Test
    @DisplayName("fromCode round-trips every kind and returns null for unknown codes")
    void fromCodeRoundTrip() {
        for (AssetKind kind : AssetKind.values()) {
            assertThat(AssetKind.fromCode(kind.getCode())).isSameAs(kind);
        }
        assertThat(AssetKind.fromCode(99)).isNull();
        assertThat(AssetKind.fromCode(-1)).isNull();
    }
}
