package com.systar.common.id;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asset ID generator that encodes a site ID in the high bits and an
 * auto-incrementing sequence in the low bits.
 *
 * <p>ID layout (32-bit int): bits 20..31 = siteId (up to 4096 sites),
 * bits 0..19 = sequence (up to 1,048,576 IDs per site).
 *
 * <p>Thread-safe.
 */
public class AssetIdGenerator {

    /** Number of bits reserved for the sequence. */
    private static final int SEQUENCE_BITS = 20;

    private static final int SEQUENCE_MASK = (1 << SEQUENCE_BITS) - 1;

    private final int siteId;
    private final int siteIdShifted;

    private final AtomicInteger sequence = new AtomicInteger(0);

    /**
     * Create a generator for the given site.
     *
     * @param siteId site identifier (0 .. 4095)
     * @throws IllegalArgumentException if siteId is out of range
     */
    public AssetIdGenerator(int siteId) {
        if (siteId < 0 || siteId > SEQUENCE_MASK >> 8) {
            throw new IllegalArgumentException("siteId must be between 0 and 4095, got: " + siteId);
        }
        this.siteId = siteId;
        this.siteIdShifted = siteId << SEQUENCE_BITS;
    }

    /**
     * Generate the next unique ID for this site.
     *
     * @return newly generated ID
     */
    public int generateId() {
        int seq = sequence.incrementAndGet();
        if (seq > SEQUENCE_MASK) {
            throw new IllegalStateException(
                    "Sequence overflow for site " + siteId + ". Max IDs per site: " + SEQUENCE_MASK);
        }
        return siteIdShifted | seq;
    }

    /**
     * Extract the site ID that was encoded into a generated ID.
     *
     * @param id generated ID
     * @return the site ID portion
     */
    public static int parseSiteId(int id) {
        return id >>> SEQUENCE_BITS;
    }

    /**
     * Extract the sequence number that was encoded into a generated ID.
     *
     * @param id generated ID
     * @return the sequence portion
     */
    public static int parseSequence(int id) {
        return id & SEQUENCE_MASK;
    }

    /**
     * Return the site ID this generator is bound to.
     *
     * @return site ID
     */
    public int getSiteId() {
        return siteId;
    }

    /**
     * Return the last generated sequence value.
     *
     * @return current sequence counter
     */
    public int currentSequence() {
        return sequence.get();
    }
}
