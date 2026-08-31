package com.systar.simulator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class ProfileGeneratorTest {

    private static class StubContext implements GenerationContext {
        private final long elapsed;
        private final java.util.Random random;
        StubContext(long elapsed) { this.elapsed = elapsed; this.random = new java.util.Random(42); }
        @Override public long elapsedMillis()              { return elapsed; }
        @Override public java.util.Optional<Object> getPeerValue(String id) { return java.util.Optional.empty(); }
        @Override public java.util.Random random()         { return random; }
    }

    @Test
    @DisplayName("interpolates between segments")
    void interpolatesBetweenSegments() {
        ProfileGenerator gen = new ProfileGenerator();
        gen.setSegmentsFromMaps(java.util.List.of(
            java.util.Map.of("time", "00:00", "value", 10.0),
            java.util.Map.of("time", "12:00", "value", 20.0)
        ));
        // At 6 hours (21600s = halfway), should be 15.0
        double value = (double) gen.generate(new StubContext(6 * 3600 * 1000L));
        assertThat(value).isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
