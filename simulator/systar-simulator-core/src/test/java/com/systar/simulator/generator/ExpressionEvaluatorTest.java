package com.systar.simulator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ExpressionEvaluatorTest {

    private final Random random = new Random(42);

    @Test
    @DisplayName("basic arithmetic")
    void basicArithmetic() {
        assertThat(new ExpressionEvaluator("2.0 + 3.0", Map.of()).evaluate(Map.of(), random)).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(new ExpressionEvaluator("10.0 - 4.0", Map.of()).evaluate(Map.of(), random)).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(new ExpressionEvaluator("3.0 * 4.0", Map.of()).evaluate(Map.of(), random)).isCloseTo(12.0, org.assertj.core.data.Offset.offset(0.001));
        assertThat(new ExpressionEvaluator("10.0 / 4.0", Map.of()).evaluate(Map.of(), random)).isCloseTo(2.5, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("parentheses")
    void parentheses() {
        assertThat(new ExpressionEvaluator("(2.0 + 3.0) * 4.0", Map.of()).evaluate(Map.of(), random)).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("reference substitution")
    void referenceSubstitution() {
        Map<String, String> refs  = Map.of("x", "point-1");
        Map<String, Double> peers = Map.of("point-1", 10.0);
        assertThat(new ExpressionEvaluator("x + 5.0", refs).evaluate(peers, random)).isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.001));
    }
}
