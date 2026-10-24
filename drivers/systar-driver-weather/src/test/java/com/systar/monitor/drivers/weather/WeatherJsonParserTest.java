package com.systar.monitor.drivers.weather;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class WeatherJsonParserTest {

    @Nested
    @DisplayName("parseAttribute - valid JSON responses")
    class ParseAttributeValid {

        @Test
        @DisplayName("extracts temperature from successful response")
        void extractsTemperature() throws Exception {
            String json = """
                {"Code":200,"Content":{"temperature":25.3}}
                """;

            float value = WeatherJsonParser.parseAttribute(json, "temperature");

            assertThat(value).isEqualTo(25.3f);
        }

        @Test
        @DisplayName("extracts humidity from successful response")
        void extractsHumidity() throws Exception {
            String json = """
                {"Code":200,"Content":{"humidity":65.0}}
                """;

            float value = WeatherJsonParser.parseAttribute(json, "humidity");

            assertThat(value).isEqualTo(65.0f);
        }
    }

    @Nested
    @DisplayName("parseAttribute - error cases")
    class ParseAttributeErrors {

        @Test
        @DisplayName("null JSON throws exception")
        void nullJson() {
            assertThatThrownBy(() -> WeatherJsonParser.parseAttribute(null, "temperature"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("failed");
        }

        @Test
        @DisplayName("non-200 status code throws exception")
        void non200Status() {
            String json = """
                {"Code":500,"Content":null}
                """;

            assertThatThrownBy(() -> WeatherJsonParser.parseAttribute(json, "temperature"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("500");
        }

        @Test
        @DisplayName("missing Code field reports a parse failure, not a bogus status code")
        void missingCodeReportsParseFailure() {
            String json = """
                {"Content":{"temperature":25.3}}
                """;

            assertThatThrownBy(() -> WeatherJsonParser.parseAttribute(json, "temperature"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("parse failed");
        }

        @Test
        @DisplayName("Content not an object reports a parse failure")
        void contentNotObjectReportsParseFailure() {
            String json = """
                {"Code":200,"Content":42}
                """;

            assertThatThrownBy(() -> WeatherJsonParser.parseAttribute(json, "temperature"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("parse failed");
        }

        @Test
        @DisplayName("missing attribute in Content reports a parse failure")
        void missingAttributeReportsParseFailure() {
            String json = """
                {"Code":200,"Content":{"humidity":65.0}}
                """;

            assertThatThrownBy(() -> WeatherJsonParser.parseAttribute(json, "temperature"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("parse failed");
        }

        @Test
        @DisplayName("non-numeric attribute value reports a parse failure")
        void nonNumericAttributeReportsParseFailure() {
            String json = """
                {"Code":200,"Content":{"temperature":"warm"}}
                """;

            assertThatThrownBy(() -> WeatherJsonParser.parseAttribute(json, "temperature"))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("parse failed");
        }
    }
}
