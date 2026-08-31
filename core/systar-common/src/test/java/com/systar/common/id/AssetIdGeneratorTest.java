package com.systar.common.id;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AssetIdGeneratorTest {

    // ========== Constructor validation ==========

    @Nested
    @DisplayName("Constructor validation")
    class Constructor {

        @Test
        @DisplayName("siteId 0 is valid")
        void siteIdZero() {
            assertThatCode(() -> new AssetIdGenerator(0)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("siteId 4095 is valid (max)")
        void siteIdMax() {
            assertThatCode(() -> new AssetIdGenerator(4095)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("siteId -1 is invalid")
        void siteIdNegative() {
            assertThatThrownBy(() -> new AssetIdGenerator(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("siteId must be between 0 and 4095");
        }

        @Test
        @DisplayName("siteId 4096 is invalid")
        void siteIdTooLarge() {
            assertThatThrownBy(() -> new AssetIdGenerator(4096))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("siteId must be between 0 and 4095");
        }

        @Test
        @DisplayName("siteId 9999 is invalid")
        void siteIdWayTooLarge() {
            assertThatThrownBy(() -> new AssetIdGenerator(9999))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ========== Sequential ID generation ==========

    @Nested
    @DisplayName("Sequential ID generation")
    class SequentialIdGeneration {

        @Test
        @DisplayName("IDs are sequential")
        void sequential() {
            AssetIdGenerator gen = new AssetIdGenerator(1);
            int first = gen.generateId();
            int second = gen.generateId();
            assertThat(second).isGreaterThan(first);
        }

        @Test
        @DisplayName("first generated ID has sequence 1")
        void firstIdSequenceIsOne() {
            AssetIdGenerator gen = new AssetIdGenerator(5);
            int id = gen.generateId();
            assertThat(AssetIdGenerator.parseSequence(id)).isEqualTo(1);
        }

        @Test
        @DisplayName("getSiteId returns configured site ID")
        void getSiteId() {
            AssetIdGenerator gen = new AssetIdGenerator(42);
            assertThat(gen.getSiteId()).isEqualTo(42);
        }

        @Test
        @DisplayName("currentSequence starts at 0")
        void currentSequenceStartsAtZero() {
            AssetIdGenerator gen = new AssetIdGenerator(0);
            assertThat(gen.currentSequence()).isZero();
        }

        @Test
        @DisplayName("currentSequence increments after generateId")
        void currentSequenceAfterGenerate() {
            AssetIdGenerator gen = new AssetIdGenerator(0);
            gen.generateId();
            gen.generateId();
            gen.generateId();
            assertThat(gen.currentSequence()).isEqualTo(3);
        }
    }

    // ========== Site ID encoding ==========

    @Nested
    @DisplayName("Site ID encoding and parsing")
    class SiteIdEncoding {

        @Test
        @DisplayName("siteId is encoded in high bits")
        void siteIdInHighBits() {
            AssetIdGenerator gen = new AssetIdGenerator(7);
            int id = gen.generateId();
            assertThat(AssetIdGenerator.parseSiteId(id)).isEqualTo(7);
        }

        @Test
        @DisplayName("different sites produce different high bits")
        void differentSitesDifferentHighBits() {
            AssetIdGenerator genA = new AssetIdGenerator(1);
            AssetIdGenerator genB = new AssetIdGenerator(2);
            int idA = genA.generateId();
            int idB = genB.generateId();
            assertThat(AssetIdGenerator.parseSiteId(idA)).isNotEqualTo(AssetIdGenerator.parseSiteId(idB));
        }

        @Test
        @DisplayName("parseSiteId static method works independently")
        void parseSiteIdStatic() {
            AssetIdGenerator gen = new AssetIdGenerator(123);
            int id = gen.generateId();
            assertThat(AssetIdGenerator.parseSiteId(id)).isEqualTo(123);
        }

        @Test
        @DisplayName("parseSequence static method works independently")
        void parseSequenceStatic() {
            AssetIdGenerator gen = new AssetIdGenerator(0);
            gen.generateId(); // seq=1
            int id = gen.generateId(); // seq=2
            assertThat(AssetIdGenerator.parseSequence(id)).isEqualTo(2);
        }

        @Test
        @DisplayName("multiple IDs from same generator share same siteId")
        void sameSiteMultipleIds() {
            AssetIdGenerator gen = new AssetIdGenerator(99);
            int id1 = gen.generateId();
            int id2 = gen.generateId();
            int id3 = gen.generateId();
            assertThat(AssetIdGenerator.parseSiteId(id1)).isEqualTo(99);
            assertThat(AssetIdGenerator.parseSiteId(id2)).isEqualTo(99);
            assertThat(AssetIdGenerator.parseSiteId(id3)).isEqualTo(99);
        }

        @Test
        @DisplayName("sequences from same generator are unique")
        void sequencesAreUnique() {
            AssetIdGenerator gen = new AssetIdGenerator(0);
            Set<Integer> sequences = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                int id = gen.generateId();
                sequences.add(AssetIdGenerator.parseSequence(id));
            }
            assertThat(sequences).hasSize(100);
        }
    }

    // ========== Overflow ==========

    @Test
    @DisplayName("sequence overflow throws IllegalStateException")
    void sequenceOverflow() throws Exception {
        AssetIdGenerator gen = new AssetIdGenerator(0);
        // Use reflection to set sequence to SEQUENCE_MASK to trigger overflow
        java.lang.reflect.Field seqField = AssetIdGenerator.class.getDeclaredField("sequence");
        seqField.setAccessible(true);
        java.util.concurrent.atomic.AtomicInteger seq = (java.util.concurrent.atomic.AtomicInteger) seqField.get(gen);
        seq.set(1048575); // SEQUENCE_MASK value

        assertThatThrownBy(gen::generateId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sequence overflow");
    }

    // ========== Concurrent safety ==========

    @Test
    @DisplayName("concurrent generation produces unique IDs")
    void concurrentGeneration() throws InterruptedException {
        final int threadCount = 8;
        final int idsPerThread = 1000;
        AssetIdGenerator gen = new AssetIdGenerator(1);
        Set<Integer> allIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            IntStream.range(0, threadCount).forEach(i ->
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            for (int j = 0; j < idsPerThread; j++) {
                                allIds.add(gen.generateId());
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    })
            );

            startLatch.countDown(); // fire all threads simultaneously
            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
            assertThat(completed).isTrue();

            // All IDs must be unique
            assertThat(allIds).hasSize(threadCount * idsPerThread);

            // All IDs must have the correct site
            assertThat(allIds).allMatch(id -> AssetIdGenerator.parseSiteId(id) == 1);
        } finally {
            executor.shutdownNow();
        }
    }
}
