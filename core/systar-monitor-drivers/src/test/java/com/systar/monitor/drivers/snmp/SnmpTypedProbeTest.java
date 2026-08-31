package com.systar.monitor.drivers.snmp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.snmp4j.smi.Variable;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
class SnmpTypedProbeTest {

    private Variable mockVar(String value) {
        Variable var = mock(Variable.class);
        when(var.toString()).thenReturn(value);
        return var;
    }

    @Test
    @DisplayName("SnmpFloatProbe converts Variable to Float")
    void floatProbeConverts() {
        SnmpFloatProbe probe = new SnmpFloatProbe();
        assertThat(probe.convertValue(mockVar("23.5"))).isEqualTo(23.5f);
    }

    @Test
    @DisplayName("SnmpFloatProbe throws on non-numeric input")
    void floatProbeThrowsOnNonNumeric() {
        SnmpFloatProbe probe = new SnmpFloatProbe();
        assertThatThrownBy(() -> probe.convertValue(mockVar("abc")))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("SnmpBoolProbe converts 'true' to true")
    void boolProbeConvertsTrue() {
        SnmpBoolProbe probe = new SnmpBoolProbe();
        assertThat(probe.convertValue(mockVar("true"))).isEqualTo(true);
    }

    @Test
    @DisplayName("SnmpBoolProbe converts '1' to true")
    void boolProbeConvertsOne() {
        SnmpBoolProbe probe = new SnmpBoolProbe();
        assertThat(probe.convertValue(mockVar("1"))).isEqualTo(true);
    }

    @Test
    @DisplayName("SnmpBoolProbe converts '0' to false")
    void boolProbeConvertsZero() {
        SnmpBoolProbe probe = new SnmpBoolProbe();
        assertThat(probe.convertValue(mockVar("0"))).isEqualTo(false);
    }

    @Test
    @DisplayName("SnmpBoolProbe converts 'false' to false")
    void boolProbeConvertsFalse() {
        SnmpBoolProbe probe = new SnmpBoolProbe();
        assertThat(probe.convertValue(mockVar("false"))).isEqualTo(false);
    }

    @Test
    @DisplayName("SnmpIntProbe converts Variable to Integer")
    void intProbeConverts() {
        SnmpIntProbe probe = new SnmpIntProbe();
        assertThat(probe.convertValue(mockVar("42"))).isEqualTo(42);
    }

    @Test
    @DisplayName("SnmpIntProbe throws on non-integer input")
    void intProbeThrowsOnNonInteger() {
        SnmpIntProbe probe = new SnmpIntProbe();
        assertThatThrownBy(() -> probe.convertValue(mockVar("abc")))
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("Base SnmpProbe converts Variable via toString")
    void baseProbeConvertsToString() {
        SnmpProbe probe = new SnmpProbe();
        assertThat(probe.convertValue(mockVar("hello"))).isEqualTo("hello");
    }
}
