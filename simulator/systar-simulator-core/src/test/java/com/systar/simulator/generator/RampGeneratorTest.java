package com.systar.simulator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class RampGeneratorTest {

    private static class StubContext implements GenerationContext {
        private final long elapsed;
        StubContext(long elapsed) { this.elapsed = elapsed; }
        @Override public long elapsedMillis()              { return elapsed; }
        @Override public java.util.Optional<Object> getPeerValue(String id) { return java.util.Optional.empty(); }
        @Override public java.util.Random random()         { return new java.util.Random(42); }
    }

    @Test
    @DisplayName("ramps from start to end over duration then loops")
    void rampsAndLoops() {
        RampGenerator gen = new RampGenerator();
        gen.setStart(0.0);
        gen.setEnd(100.0);
        gen.setDurationSeconds(10.0);
        gen.setLoop(true);

        assertThat((double) gen.generate(new StubContext(0))).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat((double) gen.generate(new StubContext(5000))).isCloseTo(50.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat((double) gen.generate(new StubContext(10000))).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01)); // wraps
    }

    @Test
    @DisplayName("non-looping ramp clamps at end value")
    void nonLoopClamps() {
        RampGenerator gen = new RampGenerator();
        gen.setStart(0.0);
        gen.setEnd(100.0);
        gen.setDurationSeconds(10.0);
        gen.setLoop(false);

        assertThat((double) gen.generate(new StubContext(15000))).isCloseTo(100.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
