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
import static org.assertj.core.data.Offset.offset;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SineGeneratorTest {

    private static class StubContext implements GenerationContext {
        private static final Random RANDOM = new Random(42);
        private long elapsedMillis;

        StubContext(long elapsedMillis) {
            this.elapsedMillis = elapsedMillis;
        }

        @Override
        public long elapsedMillis() {
            return elapsedMillis;
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

    private SineGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SineGenerator();
    }

    @Nested
    @DisplayName("at quarter period")
    class AtQuarterPeriod {

        @Test
        @DisplayName("at t=15s (quarter of period=60s), sine is near amplitude + offset (15.0)")
        void atQuarterPeriodReturnsNearAmplitudePlusOffset() {
            // amplitude=10, offset=5, periodSeconds=60
            // t=15s => phase = 2*PI*15/60 = PI/2
            // sin(PI/2) = 1.0 => value = 10*1.0 + 5 = 15.0
            generator.setAmplitude(10.0);
            generator.setOffset(5.0);
            generator.setPeriodSeconds(60.0);
            generator.setNoiseStdDev(0.0);

            GenerationContext context = new StubContext(15_000L);

            double value = (Double) generator.generate(context);
            assertThat(value).isCloseTo(15.0, offset(1e-9));
        }
    }

    @Nested
    @DisplayName("at half period")
    class AtHalfPeriod {

        @Test
        @DisplayName("at t=30s (half of period=60s), sine is near offset (5.0)")
        void atHalfPeriodReturnsNearOffset() {
            // amplitude=10, offset=5, periodSeconds=60
            // t=30s => phase = 2*PI*30/60 = PI
            // sin(PI) = 0.0 => value = 10*0.0 + 5 = 5.0
            generator.setAmplitude(10.0);
            generator.setOffset(5.0);
            generator.setPeriodSeconds(60.0);
            generator.setNoiseStdDev(0.0);

            GenerationContext context = new StubContext(30_000L);

            double value = (Double) generator.generate(context);
            assertThat(value).isCloseTo(5.0, offset(1e-9));
        }
    }

    @Nested
    @DisplayName("with noise")
    class WithNoise {

        @Test
        @DisplayName("noiseStdDev > 0 adds Gaussian noise to the signal")
        void noiseAddsVariability() {
            generator.setAmplitude(0.0);
            generator.setOffset(50.0);
            generator.setPeriodSeconds(60.0);
            generator.setNoiseStdDev(10.0);

            // At t=0, sin(0)=0, so base value = 0.0*0 + 50.0 = 50.0
            // With noise, values should scatter around 50.0
            boolean foundDeviation = false;
            for (int i = 0; i < 200; i++) {
                double value = (Double) generator.generate(new StubContext(0L));
                if (Math.abs(value - 50.0) > 0.01) {
                    foundDeviation = true;
                    break;
                }
            }
            assertThat(foundDeviation)
                .as("noise should produce values deviating from offset")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("constructor sets amplitude, offset, and periodSeconds")
        void constructorSetsFields() {
            SineGenerator gen = new SineGenerator(20.0, 10.0, 30.0);

            assertThat(gen.getAmplitude()).isEqualTo(20.0);
            assertThat(gen.getOffset()).isEqualTo(10.0);
            assertThat(gen.getPeriodSeconds()).isEqualTo(30.0);
        }
    }
}
