package com.systar.simulator.generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class RandomGeneratorTest {

    private static class StubContext implements GenerationContext {
        private static final Random RANDOM = new Random(42);

        @Override
        public long elapsedMillis() {
            return 0;
        }

        @Override
        public Optional<Object> getPeerValue(String dataPointId) {
            return Optional.empty();
        }

        @Override
        public Random random() {
            return RANDOM;
        }
    }

    private GenerationContext context;
    private RandomGenerator generator;

    @BeforeEach
    void setUp() {
        context   = new StubContext();
        generator = new RandomGenerator();
    }

    @Nested
    @DisplayName("default range [0, 100)")
    class DefaultRange {

        @Test
        @DisplayName("generates values within default range over 100 iterations")
        void generatesWithinDefaultRange() {
            double min = 0.0;
            double max = 100.0;

            for (int i = 0; i < 100; i++) {
                double value = (Double) generator.generate(context);
                assertThat(value)
                    .as("iteration %d", i)
                    .isGreaterThanOrEqualTo(min)
                    .isLessThan(max);
            }
        }
    }

    @Nested
    @DisplayName("custom range [min, max)")
    class CustomRange {

        @Test
        @DisplayName("generates values within custom range over 100 iterations")
        void generatesWithinCustomRange() {
            double customMin = 25.0;
            double customMax = 75.0;
            generator.setMin(customMin);
            generator.setMax(customMax);

            for (int i = 0; i < 100; i++) {
                double value = (Double) generator.generate(context);
                assertThat(value)
                    .as("iteration %d", i)
                    .isGreaterThanOrEqualTo(customMin)
                    .isLessThan(customMax);
            }
        }
    }

    @Nested
    @DisplayName("constructor with range")
    class ConstructorWithRange {

        @Test
        @DisplayName("constructor sets min and max correctly")
        void constructorSetsRange() {
            RandomGenerator gen = new RandomGenerator(10.0, 20.0);

            assertThat(gen.getMin()).isEqualTo(10.0);
            assertThat(gen.getMax()).isEqualTo(20.0);

            double value = (Double) gen.generate(context);
            assertThat(value).isBetween(10.0, 20.0);
        }
    }
}
