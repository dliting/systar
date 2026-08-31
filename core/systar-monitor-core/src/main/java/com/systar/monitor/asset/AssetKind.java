package com.systar.monitor.asset;

/**
 * Enumeration of asset kinds.
 * <p>
 * SPACE and DEVICE are compound assets (containers);
 * PROBE and CONTROL are monitor assets (leaf data-collection points);
 * SERVICE is a special kind — it manages monitors but is neither compound nor a monitor itself.
 */
public enum AssetKind {

    SPACE(true, false),
    DEVICE(true, false),
    SERVICE(false, false),
    PROBE(false, true),
    CONTROL(false, true);

    private final boolean compound;
    private final boolean monitor;

    AssetKind(boolean compound, boolean monitor) {
        this.compound = compound;
        this.monitor = monitor;
    }

    /** Whether this kind represents a compound (container) asset. */
    public boolean isCompound() {
        return compound;
    }

    /** Whether this kind represents a monitor (leaf) asset. */
    public boolean isMonitor() {
        return monitor;
    }
}
