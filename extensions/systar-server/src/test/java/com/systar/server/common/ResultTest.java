package com.systar.server.common;

import com.systar.common.api.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ResultTest {

    // ======================== success factory ========================

    @Nested
    @DisplayName("Result.success(data)")
    class SuccessWithData {

        @Test
        @DisplayName("success with data has code 0, message 'success', and the data")
        void successWithData() {
            Result<String> result = Result.success("hello");

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getMessage()).isEqualTo("success");
            assertThat(result.getData()).isEqualTo("hello");
        }

        @Test
        @DisplayName("success with null data payload")
        void successWithNullData() {
            Result<Object> result = Result.success(null);

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getMessage()).isEqualTo("success");
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("success with complex data type")
        void successWithComplexType() {
            Result<java.util.List<Integer>> result = Result.success(java.util.List.of(1, 2, 3));

            assertThat(result.getData()).containsExactly(1, 2, 3);
        }
    }

    @Nested
    @DisplayName("Result.success()")
    class SuccessNoData {

        @Test
        @DisplayName("success with no args has code 0, message 'success', null data")
        void successNoArgs() {
            Result<Void> result = Result.success();

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getMessage()).isEqualTo("success");
            assertThat(result.getData()).isNull();
        }
    }

    // ======================== error factory ========================

    @Nested
    @DisplayName("Result.error(message)")
    class ErrorWithMessage {

        @Test
        @DisplayName("error with message has code 1 and the given message")
        void errorWithMessage() {
            Result<Void> result = Result.error("something went wrong");

            assertThat(result.getCode()).isEqualTo(1);
            assertThat(result.getMessage()).isEqualTo("something went wrong");
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("error with null message")
        void errorWithNullMessage() {
            Result<Void> result = Result.error((String) null);

            assertThat(result.getCode()).isEqualTo(1);
            assertThat(result.getMessage()).isNull();
            assertThat(result.getData()).isNull();
        }
    }

    @Nested
    @DisplayName("Result.error(code, message)")
    class ErrorWithCodeAndMessage {

        @Test
        @DisplayName("error with custom code and message")
        void errorWithCodeAndMessage() {
            Result<Void> result = Result.error(404, "not found");

            assertThat(result.getCode()).isEqualTo(404);
            assertThat(result.getMessage()).isEqualTo("not found");
            assertThat(result.getData()).isNull();
        }

        @Test
        @DisplayName("error with code 0 still reports as error in message")
        void errorWithZeroCode() {
            Result<Void> result = Result.error(0, "zero code error");

            assertThat(result.getCode()).isEqualTo(0);
            assertThat(result.getMessage()).isEqualTo("zero code error");
        }

        @Test
        @DisplayName("error with negative code")
        void errorWithNegativeCode() {
            Result<Void> result = Result.error(-1, "system error");

            assertThat(result.getCode()).isEqualTo(-1);
            assertThat(result.getMessage()).isEqualTo("system error");
        }
    }

    // ======================== data type flexibility ========================

    @Nested
    @DisplayName("Result with various data types")
    class VariousDataTypes {

        @Test
        @DisplayName("Result with Integer data")
        void integerData() {
            Result<Integer> result = Result.success(42);

            assertThat(result.getData()).isEqualTo(42);
        }

        @Test
        @DisplayName("Result with Boolean data")
        void booleanData() {
            Result<Boolean> result = Result.success(true);

            assertThat(result.getData()).isTrue();
        }

        @Test
        @DisplayName("Result with Map data")
        void mapData() {
            java.util.Map<String, Object> map = java.util.Map.of("key", "value", "count", 5);
            Result<java.util.Map<String, Object>> result = Result.success(map);

            assertThat(result.getData()).containsEntry("key", "value");
        }
    }

    // ======================== setters (via Lombok @Data) ========================

    @Nested
    @DisplayName("Result setters")
    class Setters {

        @Test
        @DisplayName("can modify fields via setters")
        void settersWork() {
            Result<String> result = Result.success("original");

            result.setCode(999);
            result.setMessage("modified");
            result.setData("new data");

            assertThat(result.getCode()).isEqualTo(999);
            assertThat(result.getMessage()).isEqualTo("modified");
            assertThat(result.getData()).isEqualTo("new data");
        }
    }
}
