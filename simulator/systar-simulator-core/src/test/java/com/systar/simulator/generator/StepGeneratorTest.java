package com.systar.simulator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class StepGeneratorTest {

    private static class StubContext implements GenerationContext {
        private final long elapsed;
        StubContext(long elapsed) { this.elapsed = elapsed; }
        @Override public long elapsedMillis()              { return elapsed; }
        @Override public java.util.Optional<Object> getPeerValue(String id) { return java.util.Optional.empty(); }
        @Override public java.util.Random random()         { return new java.util.Random(42); }
    }

    @Test
    @DisplayName("cycles through values at interval")
    void cyclesThroughValues() {
        StepGenerator gen = new StepGenerator();
        gen.setValues(List.of(10.0, 20.0, 30.0));
        gen.setIntervalSeconds(1.0);

        assertThat(gen.generate(new StubContext(0))).isEqualTo(10.0);
        assertThat(gen.generate(new StubContext(1000))).isEqualTo(20.0);
        assertThat(gen.generate(new StubContext(2000))).isEqualTo(30.0);
        assertThat(gen.generate(new StubContext(3000))).isEqualTo(10.0); // wraps
    }
}
