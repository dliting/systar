package com.systar.server.common;

import com.systar.common.api.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("IllegalArgumentException")
    class BadRequest {

        @Test
        @DisplayName("returns 400 code with message")
        void mapsToBadRequest() {
            Result<Void> result = handler.handleBadRequest(new IllegalArgumentException("bad input"));

            assertThat(result.getCode()).isEqualTo(Result.CODE_BAD_REQUEST);
            assertThat(result.getMessage()).isEqualTo("bad input");
        }
    }

    @Nested
    @DisplayName("IllegalStateException")
    class Conflict {

        @Test
        @DisplayName("returns 409 code with message")
        void mapsToConflict() {
            Result<Void> result = handler.handleConflict(new IllegalStateException("duplicate"));

            assertThat(result.getCode()).isEqualTo(Result.CODE_CONFLICT);
            assertThat(result.getMessage()).isEqualTo("duplicate");
        }
    }

    @Nested
    @DisplayName("generic Exception")
    class Unexpected {

        @Test
        @DisplayName("returns 500 with generic message")
        void mapsToInternalError() {
            Result<Void> result = handler.handleUnexpected(new RuntimeException("boom"));

            assertThat(result.getCode()).isEqualTo(Result.CODE_INTERNAL_ERROR);
            assertThat(result.getMessage()).isEqualTo("Internal server error");
        }
    }
}
