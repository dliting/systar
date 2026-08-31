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
class FixedGeneratorTest {

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
    private FixedGenerator generator;

    @BeforeEach
    void setUp() {
        context   = new StubContext();
        generator = new FixedGenerator();
    }

    @Nested
    @DisplayName("default value")
    class DefaultValue {

        @Test
        @DisplayName("default value is 0.0")
        void defaultValueIsZero() {
            Object result = generator.generate(context);

            assertThat(result).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("constant value")
    class ConstantValue {

        @Test
        @DisplayName("returns the configured constant value (42.5)")
        void returnsConstantValue() {
            generator.setValue(42.5);

            for (int i = 0; i < 50; i++) {
                Object result = generator.generate(context);
                assertThat(result).isEqualTo(42.5);
            }
        }
    }

    @Nested
    @DisplayName("constructor with value")
    class ConstructorWithValue {

        @Test
        @DisplayName("constructor sets value correctly")
        void constructorSetsValue() {
            FixedGenerator gen = new FixedGenerator("hello");

            assertThat(gen.getValue()).isEqualTo("hello");
            assertThat(gen.generate(context)).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("setter and getter")
    class SetterGetter {

        @Test
        @DisplayName("can change value via setter")
        void canChangeValue() {
            generator.setValue(100);
            assertThat(generator.generate(context)).isEqualTo(100);

            generator.setValue("text");
            assertThat(generator.generate(context)).isEqualTo("text");
        }
    }
}
