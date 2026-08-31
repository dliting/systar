package com.systar.monitor.asset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MonitorModeTest {

    @Test
    @DisplayName("ACTIVE has code 0")
    void activeCode() {
        assertThat(MonitorMode.ACTIVE.getCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("PASSIVE has code 1")
    void passiveCode() {
        assertThat(MonitorMode.PASSIVE.getCode()).isEqualTo(1);
    }

    @Test
    @DisplayName("fromCode returns correct enum value")
    void fromCode() {
        assertThat(MonitorMode.fromCode(0)).isEqualTo(MonitorMode.ACTIVE);
        assertThat(MonitorMode.fromCode(1)).isEqualTo(MonitorMode.PASSIVE);
    }

    @Test
    @DisplayName("fromCode throws for unknown code")
    void fromCodeUnknown() {
        assertThatThrownBy(() -> MonitorMode.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("Ordinal matches code for database mapping")
    void ordinalMatchesCode() {
        for (MonitorMode mode : MonitorMode.values()) {
            assertThat(mode.ordinal()).isEqualTo(mode.getCode());
        }
    }

    @Test
    @DisplayName("Exactly two modes exist")
    void allModes() {
        assertThat(MonitorMode.values()).containsExactly(MonitorMode.ACTIVE, MonitorMode.PASSIVE);
    }
}
