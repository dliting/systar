package com.systar.monitor.expression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ExpressionEvaluatorHolderTest {

    @BeforeEach
    void resetHolder() {
        ExpressionEvaluatorHolder.reset();
    }

    @AfterEach
    void tearDown() {
        ExpressionEvaluatorHolder.reset();
    }

    @Test
    @DisplayName("default instance is SpelExpressionEvaluator")
    void defaultInstance() {
        assertThat(ExpressionEvaluatorHolder.getInstance())
                .isInstanceOf(SpelExpressionEvaluator.class);
    }

    @Test
    @DisplayName("setInstance replaces the evaluator")
    void setInstance() {
        ExpressionEvaluator mock = expr -> null;
        ExpressionEvaluatorHolder.setInstance(mock);
        assertThat(ExpressionEvaluatorHolder.getInstance()).isSameAs(mock);
    }

    @Test
    @DisplayName("setInstance rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> ExpressionEvaluatorHolder.setInstance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("reset restores SpelExpressionEvaluator")
    void reset() {
        ExpressionEvaluatorHolder.setInstance(expr -> null);
        ExpressionEvaluatorHolder.reset();
        assertThat(ExpressionEvaluatorHolder.getInstance())
                .isInstanceOf(SpelExpressionEvaluator.class);
    }
}
