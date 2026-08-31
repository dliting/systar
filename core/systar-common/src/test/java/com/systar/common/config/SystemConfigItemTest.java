package com.systar.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SystemConfigItemTest {

    @Test
    @DisplayName("default constructor creates item with default values")
    void defaultConstructor() {
        SystemConfigItem item = new SystemConfigItem();
        assertThat(item.getId()).isZero();
        assertThat(item.getKey()).isNull();
        assertThat(item.getValue()).isNull();
        assertThat(item.getDescription()).isNull();
    }

    @Test
    @DisplayName("getters and setters work correctly")
    void gettersAndSetters() {
        SystemConfigItem item = new SystemConfigItem();
        item.setId(1);
        item.setKey("db.host");
        item.setValue("localhost");
        item.setDescription("Database host");

        assertThat(item.getId()).isEqualTo(1);
        assertThat(item.getKey()).isEqualTo("db.host");
        assertThat(item.getValue()).isEqualTo("localhost");
        assertThat(item.getDescription()).isEqualTo("Database host");
    }

    @Test
    @DisplayName("equals returns true for same field values")
    void equalsSameValues() {
        SystemConfigItem a = new SystemConfigItem();
        a.setId(1);
        a.setKey("key");
        a.setValue("val");
        a.setDescription("desc");

        SystemConfigItem b = new SystemConfigItem();
        b.setId(1);
        b.setKey("key");
        b.setValue("val");
        b.setDescription("desc");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different field values")
    void equalsDifferentValues() {
        SystemConfigItem a = new SystemConfigItem();
        a.setId(1);
        a.setKey("key1");

        SystemConfigItem b = new SystemConfigItem();
        b.setId(2);
        b.setKey("key2");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals is reflexive")
    void equalsReflexive() {
        SystemConfigItem item = new SystemConfigItem();
        assertThat(item).isEqualTo(item);
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsNull() {
        SystemConfigItem item = new SystemConfigItem();
        assertThat(item).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsDifferentType() {
        SystemConfigItem item = new SystemConfigItem();
        assertThat(item).isNotEqualTo("not a SystemConfigItem");
    }

    @Test
    @DisplayName("toString contains field values")
    void toStringContainsFields() {
        SystemConfigItem item = new SystemConfigItem();
        item.setId(5);
        item.setKey("testKey");
        String str = item.toString();
        assertThat(str).contains("5");
        assertThat(str).contains("testKey");
    }

    @Test
    @DisplayName("description can be null")
    void descriptionNull() {
        SystemConfigItem item = new SystemConfigItem();
        item.setDescription(null);
        assertThat(item.getDescription()).isNull();
    }
}
