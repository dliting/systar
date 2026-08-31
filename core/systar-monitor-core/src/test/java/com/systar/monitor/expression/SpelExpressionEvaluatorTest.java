package com.systar.monitor.expression;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SpelExpressionEvaluatorTest {

    private SpelExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelExpressionEvaluator();
    }

    @Nested
    @DisplayName("compile")
    class Compile {

        @Test
        @DisplayName("compiles valid expression")
        void validExpression() {
            CompiledExpression expr = evaluator.compile("#value > 30");
            assertThat(expr).isNotNull();
        }

        @Test
        @DisplayName("returns null for null input")
        void nullInput() {
            assertThat(evaluator.compile(null)).isNull();
        }

        @Test
        @DisplayName("returns null for blank input")
        void blankInput() {
            assertThat(evaluator.compile("   ")).isNull();
        }

        @Test
        @DisplayName("throws for invalid expression syntax")
        void invalidSyntax() {
            assertThatThrownBy(() -> evaluator.compile("##invalid##"))
                    .isInstanceOf(ExpressionEvaluationException.class);
        }
    }

    @Nested
    @DisplayName("probe reference")
    class ProbeReference {

        @Test
        @DisplayName("#probe[id].value resolves from variable map")
        void probeRefSyntax() {
            CompiledExpression expr = evaluator.compile("#probe[101].value + #probe[102].value");
            Map<Integer, ProbeRef> probeRefs = Map.of(
                    101, new ProbeRef(100),
                    102, new ProbeRef(50));
            Object result = expr.evaluate(Map.of("probe", probeRefs));
            assertThat(result).isEqualTo(150);
        }

        @Test
        @DisplayName("#probe[id].value supports arithmetic expressions")
        void probeRefArithmetic() {
            CompiledExpression expr = evaluator.compile("#probe[101].value / #probe[102].value * 100");
            Map<Integer, ProbeRef> probeRefs = Map.of(
                    101, new ProbeRef(200.0),
                    102, new ProbeRef(50.0));
            Object result = expr.evaluate(Map.of("probe", probeRefs));
            assertThat(result).isEqualTo(400.0);
        }

        @Test
        @DisplayName("#probe[id].value with integer values")
        void probeRefIntegerValues() {
            CompiledExpression expr = evaluator.compile("#probe[101].value + 10");
            Map<Integer, ProbeRef> probeRefs = Map.of(101, new ProbeRef(5));
            Object result = expr.evaluate(Map.of("probe", probeRefs));
            assertThat(result).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("security restrictions")
    class Security {

        @Test
        @DisplayName("blocks type reference T(java.lang.Runtime)")
        void blocksTypeReference() {
            CompiledExpression expr = evaluator.compile("T(java.lang.Runtime)");
            assertThatThrownBy(() -> expr.evaluate(Map.of()))
                    .isInstanceOf(ExpressionEvaluationException.class)
                    .hasMessageContaining("Type reference not allowed");
        }

        @Test
        @DisplayName("blocks constructor call new java.lang.StringBuilder")
        void blocksConstructor() {
            // Constructor resolvers are empty, so new ... will fail
            CompiledExpression expr = evaluator.compile("new java.lang.StringBuilder()");
            assertThatThrownBy(() -> expr.evaluate(Map.of()))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("blocks arbitrary method call on String")
        void blocksArbitraryMethod() {
            CompiledExpression expr = evaluator.compile("#val.substring(1)");
            assertThatThrownBy(() -> expr.evaluate(Map.of("val", "hello")))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("blocks ProbeRef.getClass() - property name not whitelisted")
        void blocksProbeRefGetClass() {
            CompiledExpression expr = evaluator.compile("#probe[101].value.getClass()");
            ProbeRef ref = new ProbeRef(42.0);
            assertThatThrownBy(() -> expr.evaluate(Map.of("probe", Map.of(101, ref))))
                    .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("blocks ProbeRef.hashCode() - property name not whitelisted")
        void blocksProbeRefHashCode() {
            CompiledExpression expr = evaluator.compile("#probe[101].hashCode");
            ProbeRef ref = new ProbeRef(42.0);
            assertThatThrownBy(() -> expr.evaluate(Map.of("probe", Map.of(101, ref))))
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("evaluate")
    class Evaluate {

        @Test
        @DisplayName("arithmetic transformation")
        void arithmeticTransform() {
            CompiledExpression expr = evaluator.compile("#value * 0.1");
            Object result = expr.evaluate(Map.of("value", 100));
            assertThat(result).isEqualTo(10.0);
        }

        @Test
        @DisplayName("comparison returns Boolean")
        void comparison() {
            CompiledExpression expr = evaluator.compile("#value > 30");
            Object result = expr.evaluate(Map.of("value", 50));
            assertThat(result).isEqualTo(true);
        }

        @Test
        @DisplayName("comparison false")
        void comparisonFalse() {
            CompiledExpression expr = evaluator.compile("#value > 30");
            Object result = expr.evaluate(Map.of("value", 10));
            assertThat(result).isEqualTo(false);
        }

        @Test
        @DisplayName("string value comparison")
        void stringValueComparison() {
            CompiledExpression expr = evaluator.compile("#value == 'on'");
            Object result = expr.evaluate(Map.of("value", "on"));
            assertThat(result).isEqualTo(true);
        }
    }
}
