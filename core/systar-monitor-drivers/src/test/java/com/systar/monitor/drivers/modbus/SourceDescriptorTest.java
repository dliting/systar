package com.systar.monitor.drivers.modbus;

import com.systar.monitor.asset.type.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SourceDescriptorTest {

    @Nested
    @DisplayName("fromDataType")
    class FromDataType {

        @Test
        @DisplayName("BOOLEAN → coil/bool descriptor")
        void booleanDescriptor() {
            SourceDescriptor desc = SourceDescriptor.fromDataType(DataType.BOOLEAN, 5);

            assertThat(desc).isNotNull();
            assertThat(desc.registerType()).isEqualTo("coil");
            assertThat(desc.address()).isEqualTo(5);
            assertThat(desc.dataType()).isEqualTo("bool");
        }

        @Test
        @DisplayName("INT → holding/int descriptor")
        void intDescriptor() {
            SourceDescriptor desc = SourceDescriptor.fromDataType(DataType.INT, 10);

            assertThat(desc).isNotNull();
            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(10);
            assertThat(desc.dataType()).isEqualTo("int");
        }

        @Test
        @DisplayName("FLOAT → holding/float descriptor")
        void floatDescriptor() {
            SourceDescriptor desc = SourceDescriptor.fromDataType(DataType.FLOAT, 20);

            assertThat(desc).isNotNull();
            assertThat(desc.registerType()).isEqualTo("holding");
            assertThat(desc.address()).isEqualTo(20);
            assertThat(desc.dataType()).isEqualTo("float");
        }

        @Test
        @DisplayName("STRING returns null (unsupported)")
        void stringNotSupported() {
            assertThat(SourceDescriptor.fromDataType(DataType.STRING, 0)).isNull();
        }

        @Test
        @DisplayName("TIMESPAN returns null (unsupported)")
        void timespanNotSupported() {
            assertThat(SourceDescriptor.fromDataType(DataType.TIMESPAN, 0)).isNull();
        }

        @Test
        @DisplayName("null DataType returns null")
        void nullDataType() {
            assertThat(SourceDescriptor.fromDataType(null, 0)).isNull();
        }
    }

    @Nested
    @DisplayName("deriveRegisterType")
    class DeriveRegisterType {

        @Test
        @DisplayName("BOOLEAN → coil")
        void booleanCoil() {
            assertThat(SourceDescriptor.deriveRegisterType(DataType.BOOLEAN)).isEqualTo("coil");
        }

        @Test
        @DisplayName("INT → holding")
        void intHolding() {
            assertThat(SourceDescriptor.deriveRegisterType(DataType.INT)).isEqualTo("holding");
        }

        @Test
        @DisplayName("STRING → null (unsupported)")
        void stringNull() {
            assertThat(SourceDescriptor.deriveRegisterType(DataType.STRING)).isNull();
        }

        @Test
        @DisplayName("null → null")
        void nullReturnsNull() {
            assertThat(SourceDescriptor.deriveRegisterType(null)).isNull();
        }
    }

    @Nested
    @DisplayName("deriveDataTypeStr")
    class DeriveDataTypeStr {

        @Test
        @DisplayName("BOOLEAN → bool")
        void booleanBool() {
            assertThat(SourceDescriptor.deriveDataTypeStr(DataType.BOOLEAN)).isEqualTo("bool");
        }

        @Test
        @DisplayName("INT → int")
        void intInt() {
            assertThat(SourceDescriptor.deriveDataTypeStr(DataType.INT)).isEqualTo("int");
        }

        @Test
        @DisplayName("FLOAT → float")
        void floatFloat() {
            assertThat(SourceDescriptor.deriveDataTypeStr(DataType.FLOAT)).isEqualTo("float");
        }

        @Test
        @DisplayName("STRING → null")
        void stringNull() {
            assertThat(SourceDescriptor.deriveDataTypeStr(DataType.STRING)).isNull();
        }

        @Test
        @DisplayName("null → null")
        void nullReturnsNull() {
            assertThat(SourceDescriptor.deriveDataTypeStr(null)).isNull();
        }
    }
}
