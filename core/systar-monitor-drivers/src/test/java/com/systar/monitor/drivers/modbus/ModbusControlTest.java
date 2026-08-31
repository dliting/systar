package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.type.ControlType;
import com.systar.monitor.asset.type.DataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ModbusControlTest {

    private ModbusControl control;

    @BeforeEach
    void setUp() {
        control = new ModbusControl();
    }

    private ControlType createType(DataType dataType) {
        ControlType type = new ControlType("modbus-ctrl");
        type.setDataType(dataType);
        type.setSource("ModbusMaster");
        return type;
    }

    private void initControl(ControlType type) {
        control.init(type, 1, "modbus-ctrl");
    }

    // ======================== kind ========================

    @Test
    @DisplayName("ModbusControl is of kind CONTROL")
    void kindIsControl() {
        assertThat(control.getKind()).isEqualTo(AssetKind.CONTROL);
    }

    // ======================== init + descriptor resolution ========================

    @Nested
    @DisplayName("descriptor resolution from metadata + DataType")
    class DescriptorResolution {

        @Test
        @DisplayName("INT DataType + InRegisterAddr metadata → holding/int descriptor")
        void intHoldingDescriptor() {
            ControlType type = createType(DataType.INT);
            initControl(type);
            control.setMetadata("InRegisterAddr", 10);

            assertThat(control.getCachedDescriptor()).isNull(); // not cached until detect

            // Resolve via detect (will fail on service, but descriptor should be cached)
            var result = new com.systar.monitor.result.MonitorResult(control);
            assertThatThrownBy(() -> control.detect(result))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(control.getCachedDescriptor()).isNotNull();
            assertThat(control.getCachedDescriptor().registerType()).isEqualTo("holding");
            assertThat(control.getCachedDescriptor().address()).isEqualTo(10);
            assertThat(control.getCachedDescriptor().dataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("BOOLEAN DataType + InRegisterAddr → coil/bool descriptor")
        void boolCoilDescriptor() {
            ControlType type = createType(DataType.BOOLEAN);
            initControl(type);
            control.setMetadata("InRegisterAddr", 3);

            var result = new com.systar.monitor.result.MonitorResult(control);
            assertThatThrownBy(() -> control.detect(result))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(control.getCachedDescriptor()).isNotNull();
            assertThat(control.getCachedDescriptor().registerType()).isEqualTo("coil");
            assertThat(control.getCachedDescriptor().address()).isEqualTo(3);
            assertThat(control.getCachedDescriptor().dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("FLOAT DataType + InRegisterAddr → holding/float descriptor")
        void floatHoldingDescriptor() {
            ControlType type = createType(DataType.FLOAT);
            initControl(type);
            control.setMetadata("InRegisterAddr", 5);

            var result = new com.systar.monitor.result.MonitorResult(control);
            assertThatThrownBy(() -> control.detect(result))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(control.getCachedDescriptor()).isNotNull();
            assertThat(control.getCachedDescriptor().registerType()).isEqualTo("holding");
            assertThat(control.getCachedDescriptor().address()).isEqualTo(5);
            assertThat(control.getCachedDescriptor().dataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("cached descriptor is null before init and metadata")
        void nullBeforeInit() {
            assertThat(control.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("cached descriptor is null when no InRegisterAddr metadata")
        void nullWhenNoMetadata() {
            ControlType type = createType(DataType.INT);
            initControl(type);

            assertThat(control.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("cached descriptor is null when no DataType on type")
        void nullWhenNoDataType() {
            ControlType type = new ControlType("modbus-ctrl");
            type.setSource("ModbusMaster");
            initControl(type);
            control.setMetadata("InRegisterAddr", 10);

            assertThat(control.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("init does NOT crash with service-reference Source")
        void initDoesNotParseServiceRef() {
            ControlType type = createType(DataType.INT);
            // Source="ModbusMaster" should NOT throw during init
            initControl(type);

            assertThat(control.getCachedDescriptor()).isNull(); // not parsed at init
        }

        @Test
        @DisplayName("STRING DataType produces null descriptor (unsupported)")
        void stringDataTypeNotSupported() {
            ControlType type = createType(DataType.STRING);
            initControl(type);
            control.setMetadata("InRegisterAddr", 10);

            assertThat(control.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("TIMESPAN DataType produces null descriptor (unsupported)")
        void timespanDataTypeNotSupported() {
            ControlType type = createType(DataType.TIMESPAN);
            initControl(type);
            control.setMetadata("InRegisterAddr", 10);

            assertThat(control.getCachedDescriptor()).isNull();
        }
    }

    // ======================== execute command validation ========================

    @Nested
    @DisplayName("execute command validation")
    class ExecuteValidation {

        @Test
        @DisplayName("null command throws IllegalArgumentException")
        void nullCommand() {
            assertThatThrownBy(() -> control.execute(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("blank command throws IllegalArgumentException")
        void blankCommand() {
            assertThatThrownBy(() -> control.execute("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("execute without ModbusService throws IllegalStateException")
        void noService() {
            ControlType type = createType(DataType.INT);
            initControl(type);
            assertThatThrownBy(() -> control.execute("register:40001:value:100"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not attached to a ModbusService");
        }

        @Test
        @DisplayName("unknown command type is validated after service resolution")
        void unknownCommandTypeRequiresService() {
            ControlType type = createType(DataType.INT);
            initControl(type);
            assertThatThrownBy(() -> control.execute("pwm:1:duty:50"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ======================== command address conversion ========================

    @Nested
    @DisplayName("command address conversion delegates to ModbusAddressParser")
    class CommandAddressConversion {

        @Test
        @DisplayName("register command with 4xxxx address converts to 0-based")
        void registerTraditionalAddress() {
            int address = ModbusAddressParser.convertAddress("holding", 40010);
            assertThat(address).isEqualTo(9);
        }

        @Test
        @DisplayName("register command with 0-based address stays as-is")
        void registerZeroBasedAddress() {
            int address = ModbusAddressParser.convertAddress("holding", 5);
            assertThat(address).isEqualTo(5);
        }

        @Test
        @DisplayName("coil command with 0xxxx address converts by subtracting 1")
        void coilTraditionalAddress() {
            int address = ModbusAddressParser.convertAddress("coil", 5);
            assertThat(address).isEqualTo(4);
        }

        @Test
        @DisplayName("coil command with address 0 stays as-is")
        void coilZeroAddress() {
            int address = ModbusAddressParser.convertAddress("coil", 0);
            assertThat(address).isEqualTo(0);
        }
    }

    // ======================== detect error handling ========================

    @Nested
    @DisplayName("detect error handling")
    class DetectErrors {

        @Test
        @DisplayName("detect without DataType sets error")
        void noDataType() throws Exception {
            ControlType type = new ControlType("modbus-ctrl");
            type.setSource("ModbusMaster");
            control.init(type, 1, "modbus-ctrl");

            var result = new com.systar.monitor.result.MonitorResult(control);
            control.detect(result);

            assertThat(result.getError()).isEqualTo("Modbus control source is not configured");
        }

        @Test
        @DisplayName("detect without InRegisterAddr metadata sets error")
        void noMetadata() throws Exception {
            ControlType type = createType(DataType.INT);
            initControl(type);

            var result = new com.systar.monitor.result.MonitorResult(control);
            control.detect(result);

            assertThat(result.getError()).isEqualTo("Modbus control source is not configured");
        }

        @Test
        @DisplayName("detect without ModbusService throws IllegalStateException")
        void noServiceOnDetect() {
            ControlType type = createType(DataType.INT);
            initControl(type);
            control.setMetadata("InRegisterAddr", 5);

            var result = new com.systar.monitor.result.MonitorResult(control);
            assertThatThrownBy(() -> control.detect(result))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not attached to a ModbusService");
        }
    }
}