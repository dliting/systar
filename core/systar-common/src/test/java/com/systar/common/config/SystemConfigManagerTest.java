package com.systar.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SystemConfigManagerTest {

    private SystemConfigManager manager;

    @BeforeEach
    void setUp() {
        manager = new SystemConfigManager();
    }

    // ========== loadConfigs ==========

    @Nested
    @DisplayName("loadConfigs()")
    class LoadConfigs {

        @Test
        @DisplayName("loadConfigs with valid data")
        void loadValidData() {
            Map<String, String> data = Map.of("key1", "val1", "key2", "val2");
            manager.loadConfigs(data);
            assertThat(manager.getValue("key1")).isEqualTo("val1");
            assertThat(manager.getValue("key2")).isEqualTo("val2");
        }

        @Test
        @DisplayName("loadConfigs with null clears existing data")
        void loadNull() {
            manager.loadConfigs(Map.of("key1", "val1"));
            assertThat(manager.containsKey("key1")).isTrue();

            manager.loadConfigs(null);
            assertThat(manager.containsKey("key1")).isFalse();
        }

        @Test
        @DisplayName("loadConfigs replaces previous data")
        void loadReplaces() {
            manager.loadConfigs(Map.of("old", "data"));
            manager.loadConfigs(Map.of("new", "data"));
            assertThat(manager.containsKey("old")).isFalse();
            assertThat(manager.containsKey("new")).isTrue();
        }

        @Test
        @DisplayName("loadConfigs with empty map clears data")
        void loadEmpty() {
            manager.loadConfigs(Map.of("key1", "val1"));
            manager.loadConfigs(new HashMap<>());
            assertThat(manager.containsKey("key1")).isFalse();
        }
    }

    // ========== getValue (single arg) ==========

    @Nested
    @DisplayName("getValue(key)")
    class GetValueSingle {

        @Test
        @DisplayName("returns value for existing key")
        void existingKey() {
            manager.loadConfigs(Map.of("host", "localhost"));
            assertThat(manager.getValue("host")).isEqualTo("localhost");
        }

        @Test
        @DisplayName("returns null for missing key")
        void missingKey() {
            assertThat(manager.getValue("nonexistent")).isNull();
        }

        @Test
        @DisplayName("returns null before any load")
        void beforeLoad() {
            assertThat(manager.getValue("key")).isNull();
        }
    }

    // ========== getValue (with default) ==========

    @Nested
    @DisplayName("getValue(key, default)")
    class GetValueWithDefault {

        @Test
        @DisplayName("returns value for existing key")
        void existingKey() {
            manager.loadConfigs(Map.of("port", "8080"));
            assertThat(manager.getValue("port", "3000")).isEqualTo("8080");
        }

        @Test
        @DisplayName("returns default for missing key")
        void missingKey() {
            assertThat(manager.getValue("missing", "fallback")).isEqualTo("fallback");
        }
    }

    // ========== getIntValue ==========

    @Nested
    @DisplayName("getIntValue()")
    class GetIntValue {

        @Test
        @DisplayName("parses valid integer")
        void validInt() {
            manager.loadConfigs(Map.of("port", "8080"));
            assertThat(manager.getIntValue("port", 0)).isEqualTo(8080);
        }

        @Test
        @DisplayName("returns default for missing key")
        void missingKey() {
            assertThat(manager.getIntValue("missing", 42)).isEqualTo(42);
        }

        @Test
        @DisplayName("returns default for non-numeric value")
        void nonNumeric() {
            manager.loadConfigs(Map.of("port", "abc"));
            assertThat(manager.getIntValue("port", 3000)).isEqualTo(3000);
        }

        @Test
        @DisplayName("trims whitespace before parsing")
        void trimsWhitespace() {
            manager.loadConfigs(Map.of("port", "  8080  "));
            assertThat(manager.getIntValue("port", 0)).isEqualTo(8080);
        }

        @Test
        @DisplayName("returns default for decimal string")
        void decimalString() {
            manager.loadConfigs(Map.of("num", "3.14"));
            assertThat(manager.getIntValue("num", 0)).isEqualTo(0);
        }

        @Test
        @DisplayName("parses negative integer")
        void negativeInt() {
            manager.loadConfigs(Map.of("offset", "-10"));
            assertThat(manager.getIntValue("offset", 0)).isEqualTo(-10);
        }
    }

    // ========== getLongValue ==========

    @Nested
    @DisplayName("getLongValue()")
    class GetLongValue {

        @Test
        @DisplayName("parses valid long")
        void validLong() {
            manager.loadConfigs(Map.of("maxSize", "9999999999"));
            assertThat(manager.getLongValue("maxSize", 0L)).isEqualTo(9_999_999_999L);
        }

        @Test
        @DisplayName("returns default for missing key")
        void missingKey() {
            assertThat(manager.getLongValue("missing", 42L)).isEqualTo(42L);
        }

        @Test
        @DisplayName("returns default for non-numeric value")
        void nonNumeric() {
            manager.loadConfigs(Map.of("maxSize", "big"));
            assertThat(manager.getLongValue("maxSize", 100L)).isEqualTo(100L);
        }

        @Test
        @DisplayName("trims whitespace before parsing")
        void trimsWhitespace() {
            manager.loadConfigs(Map.of("maxSize", "  12345  "));
            assertThat(manager.getLongValue("maxSize", 0L)).isEqualTo(12345L);
        }
    }

    // ========== getBoolValue ==========

    @Nested
    @DisplayName("getBoolValue()")
    class GetBoolValue {

        @Test
        @DisplayName("'true' returns true")
        void trueLowercase() {
            manager.loadConfigs(Map.of("flag", "true"));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }

        @Test
        @DisplayName("'TRUE' returns true (case-insensitive)")
        void trueUppercase() {
            manager.loadConfigs(Map.of("flag", "TRUE"));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }

        @Test
        @DisplayName("'True' returns true (mixed case)")
        void trueMixedCase() {
            manager.loadConfigs(Map.of("flag", "True"));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }

        @Test
        @DisplayName("'1' returns true")
        void one() {
            manager.loadConfigs(Map.of("flag", "1"));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }

        @Test
        @DisplayName("'yes' returns true")
        void yes() {
            manager.loadConfigs(Map.of("flag", "yes"));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }

        @Test
        @DisplayName("'YES' returns true (case-insensitive)")
        void yesUppercase() {
            manager.loadConfigs(Map.of("flag", "YES"));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }

        @Test
        @DisplayName("'false' returns default (false)")
        void falseString() {
            manager.loadConfigs(Map.of("flag", "false"));
            assertThat(manager.getBoolValue("flag", false)).isFalse();
        }

        @Test
        @DisplayName("'0' returns default (false)")
        void zero() {
            manager.loadConfigs(Map.of("flag", "0"));
            assertThat(manager.getBoolValue("flag", true)).isFalse();
        }

        @Test
        @DisplayName("missing key returns default")
        void missingKey() {
            assertThat(manager.getBoolValue("missing", true)).isTrue();
            assertThat(manager.getBoolValue("missing", false)).isFalse();
        }

        @Test
        @DisplayName("random string returns default (false)")
        void randomString() {
            manager.loadConfigs(Map.of("flag", "maybe"));
            assertThat(manager.getBoolValue("flag", false)).isFalse();
        }

        @Test
        @DisplayName("trims whitespace")
        void trimsWhitespace() {
            manager.loadConfigs(Map.of("flag", "  true  "));
            assertThat(manager.getBoolValue("flag", false)).isTrue();
        }
    }

    // ========== getDoubleValue ==========

    @Nested
    @DisplayName("getDoubleValue()")
    class GetDoubleValue {

        @Test
        @DisplayName("parses valid double")
        void validDouble() {
            manager.loadConfigs(Map.of("threshold", "2.0"));
            assertThat(manager.getDoubleValue("threshold", 0.0)).isEqualTo(2.0);
        }

        @Test
        @DisplayName("returns default for missing key")
        void missingKey() {
            assertThat(manager.getDoubleValue("nonexistent", 1.5)).isEqualTo(1.5);
        }

        @Test
        @DisplayName("returns default for non-numeric value")
        void nonNumeric() {
            manager.loadConfigs(Map.of("threshold", "abc"));
            assertThat(manager.getDoubleValue("threshold", 1.5)).isEqualTo(1.5);
        }

        @Test
        @DisplayName("returns default for empty value")
        void emptyValue() {
            manager.loadConfigs(Map.of("threshold", ""));
            assertThat(manager.getDoubleValue("threshold", 1.5)).isEqualTo(1.5);
        }

        @Test
        @DisplayName("trims whitespace before parsing")
        void trimsWhitespace() {
            manager.loadConfigs(Map.of("threshold", "  3.14  "));
            assertThat(manager.getDoubleValue("threshold", 0.0)).isEqualTo(3.14);
        }

        @Test
        @DisplayName("parses negative double")
        void negativeDouble() {
            manager.loadConfigs(Map.of("offset", "-0.5"));
            assertThat(manager.getDoubleValue("offset", 0.0)).isEqualTo(-0.5);
        }
    }

    // ========== containsKey ==========

    @Nested
    @DisplayName("containsKey()")
    class ContainsKey {

        @Test
        @DisplayName("returns true for existing key")
        void existingKey() {
            manager.loadConfigs(Map.of("host", "localhost"));
            assertThat(manager.containsKey("host")).isTrue();
        }

        @Test
        @DisplayName("returns false for missing key")
        void missingKey() {
            manager.loadConfigs(Map.of("host", "localhost"));
            assertThat(manager.containsKey("port")).isFalse();
        }

        @Test
        @DisplayName("returns false before any load")
        void beforeLoad() {
            assertThat(manager.containsKey("key")).isFalse();
        }

        @Test
        @DisplayName("returns false after loadConfigs(null)")
        void afterNullLoad() {
            manager.loadConfigs(Map.of("key", "val"));
            manager.loadConfigs(null);
            assertThat(manager.containsKey("key")).isFalse();
        }
    }
}
