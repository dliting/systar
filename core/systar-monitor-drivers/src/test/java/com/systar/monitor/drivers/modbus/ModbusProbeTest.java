package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetVisitor;
import com.systar.monitor.asset.type.DataType;
import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.Space;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ModbusProbeTest {

    private ModbusProbe probe;

    @BeforeEach
    void setUp() {
        probe = new ModbusProbe();
    }

    private ProbeType createType(DataType dataType) {
        ProbeType type = new ProbeType("modbus-probe");
        type.setDataType(dataType);
        type.setSource("ModbusMaster");
        return type;
    }

    private void initProbe(ProbeType type) {
        probe.init(type, 1, "modbus-probe");
    }

    // ======================== source parsing (shared utility) ========================

    @Nested
    @DisplayName("parseSource - valid source strings")
    class ParseSourceValid {

        @Test
        @DisplayName("holding:40001:float converts 4xxxx address to 0-based")
        void holdingTraditionalAddress() {
            SourceDescriptor desc = ModbusAddressParser.parse("holding:40001:float");

            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("holding:40010:int converts 4xxxx address correctly")
        void holdingTraditionalAddressInt() {
            SourceDescriptor desc = ModbusAddressParser.parse("holding:40010:int");

            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(9);
            assertThat(desc.dataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("holding:5:short uses 0-based addressing for small addresses")
        void holdingZeroBasedAddress() {
            SourceDescriptor desc = ModbusAddressParser.parse("holding:5:short");

            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(5);
            assertThat(desc.dataType()).isEqualTo("short");
        }

        @Test
        @DisplayName("input:30001:float converts 3xxxx address to 0-based")
        void inputTraditionalAddress() {
            SourceDescriptor desc = ModbusAddressParser.parse("input:30001:float");

            assertThat(desc.registerType()).isEqualTo("input");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("input:30005:int converts correctly")
        void inputTraditionalAddressInt() {
            SourceDescriptor desc = ModbusAddressParser.parse("input:30005:int");

            assertThat(desc.registerType()).isEqualTo("input");
            assertThat(desc.address()).isEqualTo(4);
            assertThat(desc.dataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("input:10:short uses 0-based addressing for small addresses")
        void inputZeroBasedAddress() {
            SourceDescriptor desc = ModbusAddressParser.parse("input:10:short");

            assertThat(desc.registerType()).isEqualTo("input");
            assertThat(desc.address()).isEqualTo(10);
            assertThat(desc.dataType()).isEqualTo("short");
        }

        @Test
        @DisplayName("coil:1:bool converts 0xxxx address (subtract 1)")
        void coilTraditionalAddress() {
            SourceDescriptor desc = ModbusAddressParser.parse("coil:1:bool");

            assertThat(desc.registerType()).isEqualTo("coil");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("coil:5:bool converts correctly")
        void coilTraditionalAddressHigher() {
            SourceDescriptor desc = ModbusAddressParser.parse("coil:5:bool");

            assertThat(desc.registerType()).isEqualTo("coil");
            assertThat(desc.address()).isEqualTo(4);
            assertThat(desc.dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("coil:0:bool uses 0-based for address 0")
        void coilZeroBased() {
            SourceDescriptor desc = ModbusAddressParser.parse("coil:0:bool");

            assertThat(desc.registerType()).isEqualTo("coil");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("discrete:10001:bool converts 1xxxx address to 0-based")
        void discreteTraditionalAddress() {
            SourceDescriptor desc = ModbusAddressParser.parse("discrete:10001:bool");

            assertThat(desc.registerType()).isEqualTo("discrete");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("discrete:10010:bool converts correctly")
        void discreteTraditionalAddressHigher() {
            SourceDescriptor desc = ModbusAddressParser.parse("discrete:10010:bool");

            assertThat(desc.registerType()).isEqualTo("discrete");
            assertThat(desc.address()).isEqualTo(9);
            assertThat(desc.dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("discrete:5:bool uses 0-based for small addresses")
        void discreteZeroBased() {
            SourceDescriptor desc = ModbusAddressParser.parse("discrete:5:bool");

            assertThat(desc.registerType()).isEqualTo("discrete");
            assertThat(desc.address()).isEqualTo(5);
            assertThat(desc.dataType()).isEqualTo("bool");
        }
    }

    @Nested
    @DisplayName("parseSource - whitespace handling")
    class ParseSourceWhitespace {

        @Test
        @DisplayName("trims whitespace around parts")
        void trimsWhitespace() {
            SourceDescriptor desc = ModbusAddressParser.parse(" holding : 40001 : float ");

            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(0);
            assertThat(desc.dataType()).isEqualTo("float");
        }
    }

    @Nested
    @DisplayName("parseSource - invalid source strings")
    class ParseSourceInvalid {

        @Test
        @DisplayName("missing parts throws IllegalArgumentException")
        void missingParts() {
            assertThatThrownBy(() -> ModbusAddressParser.parse("holding:40001"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Modbus source format");
        }

        @Test
        @DisplayName("empty string throws IllegalArgumentException")
        void emptyString() {
            assertThatThrownBy(() -> ModbusAddressParser.parse("a:b"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("non-numeric address throws NumberFormatException")
        void nonNumericAddress() {
            assertThatThrownBy(() -> ModbusAddressParser.parse("holding:abc:float"))
                    .isInstanceOf(NumberFormatException.class);
        }
    }

    @Nested
    @DisplayName("parseSource - case insensitivity")
    class ParseSourceCaseInsensitive {

        @Test
        @DisplayName("register type is case-insensitive")
        void registerTypeCaseInsensitive() {
            SourceDescriptor desc = ModbusAddressParser.parse("HOLDING:40001:FLOAT");

            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.dataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("mixed case works")
        void mixedCase() {
            SourceDescriptor desc = ModbusAddressParser.parse("Holding:40001:Float");

            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.dataType()).isEqualTo("float");
        }
    }

    // ======================== descriptor resolution from metadata ========================

    @Nested
    @DisplayName("descriptor resolution from metadata + DataType")
    class DescriptorResolution {

        @Test
        @DisplayName("INT DataType + RegisterAddr metadata → holding/int descriptor")
        void intHoldingDescriptor() {
            ProbeType type = createType(DataType.INT);
            initProbe(type);
            probe.setMetadata("RegisterAddr", 10);

            var result = new com.systar.monitor.result.MonitorResult(probe);
            assertThatThrownBy(() -> probe.detect(result))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(probe.getCachedDescriptor()).isNotNull();
            assertThat(probe.getCachedDescriptor().registerType()).isEqualTo("holding");
            assertThat(probe.getCachedDescriptor().address()).isEqualTo(10);
            assertThat(probe.getCachedDescriptor().dataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("FLOAT DataType + RegisterAddr → holding/float descriptor")
        void floatHoldingDescriptor() {
            ProbeType type = createType(DataType.FLOAT);
            initProbe(type);
            probe.setMetadata("RegisterAddr", 5);

            var result = new com.systar.monitor.result.MonitorResult(probe);
            assertThatThrownBy(() -> probe.detect(result))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(probe.getCachedDescriptor()).isNotNull();
            assertThat(probe.getCachedDescriptor().registerType()).isEqualTo("holding");
            assertThat(probe.getCachedDescriptor().address()).isEqualTo(5);
            assertThat(probe.getCachedDescriptor().dataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("BOOLEAN DataType + RegisterAddr → coil/bool descriptor")
        void boolCoilDescriptor() {
            ProbeType type = createType(DataType.BOOLEAN);
            initProbe(type);
            probe.setMetadata("RegisterAddr", 3);

            var result = new com.systar.monitor.result.MonitorResult(probe);
            assertThatThrownBy(() -> probe.detect(result))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(probe.getCachedDescriptor()).isNotNull();
            assertThat(probe.getCachedDescriptor().registerType()).isEqualTo("coil");
            assertThat(probe.getCachedDescriptor().address()).isEqualTo(3);
            assertThat(probe.getCachedDescriptor().dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("cached descriptor is null before init")
        void noCacheBeforeInit() {
            assertThat(probe.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("cached descriptor is null when no RegisterAddr metadata")
        void noCacheWithNoMetadata() {
            ProbeType type = createType(DataType.INT);
            initProbe(type);

            assertThat(probe.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("cached descriptor is null when no DataType on type")
        void noCacheWithNoDataType() {
            ProbeType type = new ProbeType("modbus-probe");
            type.setSource("ModbusMaster");
            initProbe(type);
            probe.setMetadata("RegisterAddr", 10);

            assertThat(probe.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("init does NOT crash with service-reference Source")
        void initDoesNotParseServiceRef() {
            ProbeType type = createType(DataType.INT);
            // Source="ModbusMaster" should NOT throw during init
            initProbe(type);

            assertThat(probe.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("STRING DataType produces null descriptor (unsupported)")
        void stringDataTypeNotSupported() {
            ProbeType type = createType(DataType.STRING);
            initProbe(type);
            probe.setMetadata("RegisterAddr", 10);

            assertThat(probe.getCachedDescriptor()).isNull();
        }

        @Test
        @DisplayName("TIMESPAN DataType produces null descriptor (unsupported)")
        void timespanDataTypeNotSupported() {
            ProbeType type = createType(DataType.TIMESPAN);
            initProbe(type);
            probe.setMetadata("RegisterAddr", 10);

            assertThat(probe.getCachedDescriptor()).isNull();
        }
    }

    // ======================== kind and visitor ========================

    @Test
    @DisplayName("ModbusProbe is of kind PROBE")
    void probeKindIsProbe() {
        assertThat(probe.getKind()).isEqualTo(AssetKind.PROBE);
    }

    @Test
    @DisplayName("ModbusProbe accepts visitor with correct visit method")
    void probeAcceptsVisitor() {
        AssetVisitor<String> visitor = new AssetVisitor<>() {
            @Override public String visit(Space space) { return "space"; }
            @Override public String visit(Device device) { return "device"; }
            @Override public String visit(com.systar.monitor.asset.Probe probe) { return "probe"; }
            @Override public String visit(com.systar.monitor.asset.VirtualProbe vp) { return "virtualProbe"; }
            @Override public String visit(com.systar.monitor.asset.Control control) { return "control"; }
            @Override public String visit(com.systar.monitor.asset.MonitorService service) { return "service"; }
        };

        assertThat(probe.accept(visitor)).isEqualTo("probe");
    }
}