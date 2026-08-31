package com.systar.simulator.generator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class CorrelatedGeneratorTest {

    private static class StubContext implements GenerationContext {
        private final Map<String, Object> peerValues;
        StubContext(Map<String, Object> peerValues) { this.peerValues = peerValues; }
        @Override public long elapsedMillis()              { return 0; }
        @Override public Optional<Object> getPeerValue(String id) { return Optional.ofNullable(peerValues.get(id)); }
        @Override public Random random()                   { return new Random(42); }
    }

    @Test
    @DisplayName("evaluates expression with peer reference")
    void evaluatesWithPeer() {
        CorrelatedGenerator gen = new CorrelatedGenerator();
        gen.setExpression("baseTemp + 2.0");
        gen.setReferences(Map.of("baseTemp", "supply-temp"));

        Map<String, Object> peers = Map.of("supply-temp", 20.0);
        double result = (double) gen.generate(new StubContext(peers));
        assertThat(result).isCloseTo(22.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
