package com.systar.monitor.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetStateTest {

    @Test
    @DisplayName("Severity ordering: NORMAL < WARNING < ERROR < OFFLINE")
    void severityOrdering() {
        assertThat(AssetState.NORMAL.getSeverity()).isEqualTo(0);
        assertThat(AssetState.WARNING.getSeverity()).isEqualTo(1);
        assertThat(AssetState.ERROR.getSeverity()).isEqualTo(2);
        assertThat(AssetState.OFFLINE.getSeverity()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({
            "WARNING, NORMAL, true",
            "ERROR,   NORMAL, true",
            "OFFLINE, NORMAL, true",
            "ERROR,   WARNING, true",
            "OFFLINE, ERROR,   true",
            "NORMAL,  NORMAL, false",
            "NORMAL,  WARNING, false",
            "WARNING, WARNING, false",
    })
    @DisplayName("isMoreSevereThan works for various combinations")
    void isMoreSevereThan(AssetState a, AssetState b, boolean expected) {
        assertThat(a.isMoreSevereThan(b)).isEqualTo(expected);
    }

    @Test
    @DisplayName("max returns the more severe state")
    void maxReturnsMoreSevere() {
        assertThat(AssetState.max(AssetState.NORMAL, AssetState.WARNING)).isEqualTo(AssetState.WARNING);
        assertThat(AssetState.max(AssetState.ERROR, AssetState.WARNING)).isEqualTo(AssetState.ERROR);
        assertThat(AssetState.max(AssetState.OFFLINE, AssetState.ERROR)).isEqualTo(AssetState.OFFLINE);
    }

    @Test
    @DisplayName("max with equal states returns the same state")
    void maxWithEqualStates() {
        assertThat(AssetState.max(AssetState.WARNING, AssetState.WARNING)).isEqualTo(AssetState.WARNING);
    }

    @Test
    @DisplayName("max returns non-null argument when one is null")
    void maxWithNull() {
        assertThat(AssetState.max(null, AssetState.ERROR)).isEqualTo(AssetState.ERROR);
        assertThat(AssetState.max(AssetState.WARNING, null)).isEqualTo(AssetState.WARNING);
    }

    @Test
    @DisplayName("max with both nulls returns null")
    void maxBothNull() {
        assertThat(AssetState.max(null, null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "NORMAL,  正常",
            "WARNING, 警告",
            "ERROR,   错误",
            "OFFLINE, 离线",
    })
    @DisplayName("getCaption returns Chinese label for each state")
    void captionReturnsChineseLabel(AssetState state, String expected) {
        assertThat(state.getCaption()).isEqualTo(expected);
    }
}
