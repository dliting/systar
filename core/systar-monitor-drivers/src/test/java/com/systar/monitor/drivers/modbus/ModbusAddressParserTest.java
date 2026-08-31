package com.systar.monitor.drivers.modbus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ModbusAddressParserTest {

    @Nested
    @DisplayName("parse()")
    class Parse {

        @Test
        void parsesHoldingRegister() {
            SourceDescriptor desc = ModbusAddressParser.parse("holding:40001:float");
            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("float");
        }

        @Test
        void parsesInputRegister() {
            SourceDescriptor desc = ModbusAddressParser.parse("input:30005:int");
            assertThat(desc.registerType()).isEqualTo("input");
            assertThat(desc.address()).isEqualTo(4);
            assertThat(desc.dataType()).isEqualTo("int");
        }

        @Test
        void parsesCoil() {
            SourceDescriptor desc = ModbusAddressParser.parse("coil:1:boolean");
            assertThat(desc.registerType()).isEqualTo("coil");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("boolean");
        }

        @Test
        void parsesDiscrete() {
            SourceDescriptor desc = ModbusAddressParser.parse("discrete:10001:boolean");
            assertThat(desc.registerType()).isEqualTo("discrete");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("boolean");
        }

        @Test
        void trimsWhitespaceAndIsCaseInsensitive() {
            SourceDescriptor desc = ModbusAddressParser.parse("  HOLDING : 40001 : FLOAT ");
            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.dataType()).isEqualTo("float");
        }

        @Test
        void throwsOnMissingParts() {
            assertThatThrownBy(() -> ModbusAddressParser.parse("holding:40001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Modbus source format");
        }

        @Test
        void throwsOnTooFewParts() {
            assertThatThrownBy(() -> ModbusAddressParser.parse("holding"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void throwsOnNonNumericAddress() {
            assertThatThrownBy(() -> ModbusAddressParser.parse("holding:abc:float"))
                    .isInstanceOf(NumberFormatException.class);
        }
    }

    @Nested
    @DisplayName("convertAddress()")
    class ConvertAddress {

        @Test
        void holdingTraditionalAddress() {
            assertThat(ModbusAddressParser.convertAddress("holding", 40001)).isEqualTo(0);
            assertThat(ModbusAddressParser.convertAddress("holding", 40100)).isEqualTo(99);
        }

        @Test
        void holdingZeroBasedPassThrough() {
            assertThat(ModbusAddressParser.convertAddress("holding", 0)).isEqualTo(0);
            assertThat(ModbusAddressParser.convertAddress("holding", 50)).isEqualTo(50);
        }

        @Test
        void inputTraditionalAddress() {
            assertThat(ModbusAddressParser.convertAddress("input", 30001)).isEqualTo(0);
            assertThat(ModbusAddressParser.convertAddress("input", 30010)).isEqualTo(9);
        }

        @Test
        void inputZeroBasedPassThrough() {
            assertThat(ModbusAddressParser.convertAddress("input", 5)).isEqualTo(5);
        }

        @Test
        void coilTraditionalAddress() {
            assertThat(ModbusAddressParser.convertAddress("coil", 1)).isEqualTo(0);
            assertThat(ModbusAddressParser.convertAddress("coil", 10)).isEqualTo(9);
        }

        @Test
        void coilZeroAddressStaysZero() {
            assertThat(ModbusAddressParser.convertAddress("coil", 0)).isEqualTo(0);
        }

        @Test
        void discreteTraditionalAddress() {
            assertThat(ModbusAddressParser.convertAddress("discrete", 10001)).isEqualTo(0);
            assertThat(ModbusAddressParser.convertAddress("discrete", 10050)).isEqualTo(49);
        }

        @Test
        void discreteZeroBasedPassThrough() {
            assertThat(ModbusAddressParser.convertAddress("discrete", 5)).isEqualTo(5);
        }

        @Test
        void unknownTypePassThrough() {
            assertThat(ModbusAddressParser.convertAddress("custom", 12345)).isEqualTo(12345);
        }
    }
}
