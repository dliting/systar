package com.systar.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class TimeSpanTest {

    // ========== Factory methods ==========

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("ofSeconds creates correct TimeSpan")
        void ofSeconds() {
            TimeSpan ts = TimeSpan.ofSeconds(30);
            assertThat(ts.toSeconds()).isEqualTo(30);
            assertThat(ts.toMillis()).isEqualTo(30_000);
        }

        @Test
        @DisplayName("ofMinutes creates correct TimeSpan")
        void ofMinutes() {
            TimeSpan ts = TimeSpan.ofMinutes(5);
            assertThat(ts.toMinutes()).isEqualTo(5);
            assertThat(ts.toSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("ofHours creates correct TimeSpan")
        void ofHours() {
            TimeSpan ts = TimeSpan.ofHours(2);
            assertThat(ts.toHours()).isEqualTo(2);
            assertThat(ts.toMinutes()).isEqualTo(120);
        }

        @Test
        @DisplayName("ofDays creates correct TimeSpan")
        void ofDays() {
            TimeSpan ts = TimeSpan.ofDays(3);
            assertThat(ts.toDays()).isEqualTo(3);
            assertThat(ts.toHours()).isEqualTo(72);
        }

        @Test
        @DisplayName("ofSeconds with zero")
        void ofSecondsZero() {
            TimeSpan ts = TimeSpan.ofSeconds(0);
            assertThat(ts.toSeconds()).isZero();
            assertThat(ts.toMillis()).isZero();
        }
    }

    // ========== Parse ==========

    @Nested
    @DisplayName("parse()")
    class Parse {

        @Test
        @DisplayName("parse seconds")
        void parseSeconds() {
            TimeSpan ts = TimeSpan.parse("10s");
            assertThat(ts.toSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("parse minutes")
        void parseMinutes() {
            TimeSpan ts = TimeSpan.parse("5m");
            assertThat(ts.toMinutes()).isEqualTo(5);
        }

        @Test
        @DisplayName("parse hours")
        void parseHours() {
            TimeSpan ts = TimeSpan.parse("2h");
            assertThat(ts.toHours()).isEqualTo(2);
        }

        @Test
        @DisplayName("parse days")
        void parseDays() {
            TimeSpan ts = TimeSpan.parse("1d");
            assertThat(ts.toDays()).isEqualTo(1);
        }

        @Test
        @DisplayName("parse zero")
        void parseZero() {
            TimeSpan ts = TimeSpan.parse("0");
            assertThat(ts.toSeconds()).isZero();
            assertThat(ts.toMillis()).isZero();
        }

        @Test
        @DisplayName("parse large value")
        void parseLargeValue() {
            TimeSpan ts = TimeSpan.parse("99999s");
            assertThat(ts.toSeconds()).isEqualTo(99_999);
        }

        @Test
        @DisplayName("parse trims whitespace")
        void parseTrimsWhitespace() {
            TimeSpan ts = TimeSpan.parse("  10s  ");
            assertThat(ts.toSeconds()).isEqualTo(10);
        }

        @Test
        @DisplayName("parse null throws IllegalArgumentException")
        void parseNull() {
            assertThatThrownBy(() -> TimeSpan.parse(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        @DisplayName("parse blank string throws IllegalArgumentException")
        void parseBlank() {
            assertThatThrownBy(() -> TimeSpan.parse("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        @DisplayName("parse empty string throws IllegalArgumentException")
        void parseEmpty() {
            assertThatThrownBy(() -> TimeSpan.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("parse invalid format throws IllegalArgumentException")
        void parseInvalidFormat() {
            assertThatThrownBy(() -> TimeSpan.parse("abc"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid time span format");
        }

        @Test
        @DisplayName("parse unsupported unit throws IllegalArgumentException")
        void parseUnsupportedUnit() {
            assertThatThrownBy(() -> TimeSpan.parse("10w"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("parse number without unit throws IllegalArgumentException")
        void parseNumberWithoutUnit() {
            assertThatThrownBy(() -> TimeSpan.parse("10"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("parse negative-like string throws IllegalArgumentException")
        void parseNegativeString() {
            assertThatThrownBy(() -> TimeSpan.parse("-5s"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========== toDuration ==========

    @Test
    @DisplayName("toDuration returns underlying Duration")
    void toDuration() {
        TimeSpan ts = TimeSpan.ofSeconds(45);
        assertThat(ts.toDuration()).isEqualTo(Duration.ofSeconds(45));
    }

    // ========== toString ==========

    @Nested
    @DisplayName("toString()")
    class ToString {

        @Test
        @DisplayName("zero produces '0'")
        void zeroToString() {
            assertThat(TimeSpan.ofSeconds(0).toString()).isEqualTo("0");
        }

        @Test
        @DisplayName("seconds produce 'Ns'")
        void secondsToString() {
            assertThat(TimeSpan.ofSeconds(30).toString()).isEqualTo("30s");
        }

        @Test
        @DisplayName("minutes produce 'Nm'")
        void minutesToString() {
            assertThat(TimeSpan.ofMinutes(5).toString()).isEqualTo("5m");
        }

        @Test
        @DisplayName("hours produce 'Nh'")
        void hoursToString() {
            assertThat(TimeSpan.ofHours(2).toString()).isEqualTo("2h");
        }

        @Test
        @DisplayName("days produce 'Nd'")
        void daysToString() {
            assertThat(TimeSpan.ofDays(1).toString()).isEqualTo("1d");
        }

        @Test
        @DisplayName("120 seconds renders as '2m'")
        void compositeMinutes() {
            assertThat(TimeSpan.ofSeconds(120).toString()).isEqualTo("2m");
        }

        @Test
        @DisplayName("3600 seconds renders as '1h'")
        void compositeHours() {
            assertThat(TimeSpan.ofSeconds(3600).toString()).isEqualTo("1h");
        }

        @Test
        @DisplayName("86400 seconds renders as '1d'")
        void compositeDays() {
            assertThat(TimeSpan.ofSeconds(86400).toString()).isEqualTo("1d");
        }

        @Test
        @DisplayName("90 seconds renders as '90s' (not a clean minute)")
        void nonCompositeStaysAsSeconds() {
            assertThat(TimeSpan.ofSeconds(90).toString()).isEqualTo("90s");
        }
    }

    // ========== equals / hashCode ==========

    @Nested
    @DisplayName("equals() and hashCode()")
    class EqualsHashCode {

        @Test
        @DisplayName("equal TimeSpan instances")
        void equalInstances() {
            TimeSpan a = TimeSpan.ofSeconds(30);
            TimeSpan b = TimeSpan.ofSeconds(30);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("different TimeSpan instances")
        void differentInstances() {
            TimeSpan a = TimeSpan.ofSeconds(10);
            TimeSpan b = TimeSpan.ofSeconds(20);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        @DisplayName("not equal to null")
        void notEqualToNull() {
            TimeSpan ts = TimeSpan.ofSeconds(5);
            assertThat(ts).isNotEqualTo(null);
        }

        @Test
        @DisplayName("not equal to different type")
        void notEqualToDifferentType() {
            TimeSpan ts = TimeSpan.ofSeconds(5);
            assertThat(ts).isNotEqualTo("5s");
        }

        @Test
        @DisplayName("reflexive: equal to itself")
        void reflexive() {
            TimeSpan ts = TimeSpan.ofMinutes(1);
            assertThat(ts).isEqualTo(ts);
        }

        @Test
        @DisplayName("parse and factory produce equal instances")
        void parseVsFactory() {
            TimeSpan fromParse = TimeSpan.parse("5m");
            TimeSpan fromFactory = TimeSpan.ofMinutes(5);
            assertThat(fromParse).isEqualTo(fromFactory);
            assertThat(fromParse.hashCode()).isEqualTo(fromFactory.hashCode());
        }
    }

    // ========== Comparable ==========

    @Nested
    @DisplayName("compareTo()")
    class CompareTo {

        @Test
        @DisplayName("less than")
        void lessThan() {
            TimeSpan a = TimeSpan.ofSeconds(5);
            TimeSpan b = TimeSpan.ofSeconds(10);
            assertThat(a.compareTo(b)).isNegative();
        }

        @Test
        @DisplayName("greater than")
        void greaterThan() {
            TimeSpan a = TimeSpan.ofSeconds(10);
            TimeSpan b = TimeSpan.ofSeconds(5);
            assertThat(a.compareTo(b)).isPositive();
        }

        @Test
        @DisplayName("equal")
        void equal() {
            TimeSpan a = TimeSpan.ofSeconds(10);
            TimeSpan b = TimeSpan.ofSeconds(10);
            assertThat(a.compareTo(b)).isZero();
        }

        @Test
        @DisplayName("cross-unit comparison")
        void crossUnit() {
            TimeSpan oneMinute = TimeSpan.ofMinutes(1);
            TimeSpan sixtySeconds = TimeSpan.ofSeconds(60);
            assertThat(oneMinute.compareTo(sixtySeconds)).isZero();
        }
    }
}
