package com.systar.common.code;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CodeItemTest {

    @Test
    @DisplayName("default constructor creates item with default values")
    void defaultConstructor() {
        CodeItem item = new CodeItem();
        assertThat(item.getId()).isZero();
        assertThat(item.getName()).isNull();
        assertThat(item.getCaption()).isNull();
        assertThat(item.getParentId()).isNull();
    }

    @Test
    @DisplayName("getters and setters work correctly")
    void gettersAndSetters() {
        CodeItem item = new CodeItem();
        item.setId(10);
        item.setName("STATUS_ACTIVE");
        item.setCaption("Active");
        item.setParentId(1);

        assertThat(item.getId()).isEqualTo(10);
        assertThat(item.getName()).isEqualTo("STATUS_ACTIVE");
        assertThat(item.getCaption()).isEqualTo("Active");
        assertThat(item.getParentId()).isEqualTo(1);
    }

    @Test
    @DisplayName("equals returns true for same field values")
    void equalsSameValues() {
        CodeItem a = new CodeItem();
        a.setId(1);
        a.setName("test");
        a.setCaption("Test");
        a.setParentId(0);

        CodeItem b = new CodeItem();
        b.setId(1);
        b.setName("test");
        b.setCaption("Test");
        b.setParentId(0);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("equals returns false for different field values")
    void equalsDifferentValues() {
        CodeItem a = new CodeItem();
        a.setId(1);
        a.setName("alpha");

        CodeItem b = new CodeItem();
        b.setId(2);
        b.setName("beta");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals is reflexive")
    void equalsReflexive() {
        CodeItem item = new CodeItem();
        assertThat(item).isEqualTo(item);
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsNull() {
        CodeItem item = new CodeItem();
        assertThat(item).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals returns false for different type")
    void equalsDifferentType() {
        CodeItem item = new CodeItem();
        assertThat(item).isNotEqualTo("not a CodeItem");
    }

    @Test
    @DisplayName("toString contains field values")
    void toStringContainsFields() {
        CodeItem item = new CodeItem();
        item.setId(5);
        item.setName("testName");
        String str = item.toString();
        assertThat(str).contains("5");
        assertThat(str).contains("testName");
    }

    @Test
    @DisplayName("parentId can be null for top-level items")
    void parentIdNull() {
        CodeItem item = new CodeItem();
        item.setParentId(null);
        assertThat(item.getParentId()).isNull();
    }
}
