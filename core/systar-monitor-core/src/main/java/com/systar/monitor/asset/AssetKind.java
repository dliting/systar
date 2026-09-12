package com.systar.monitor.asset;

/**
 * Enumeration of asset kinds.
 * <p>
 * DEVICE is the compound asset kind (container);
 * PROBE and CONTROL are monitor assets (leaf data-collection points);
 * SERVICE is a special kind — it manages monitors but is neither compound nor a monitor itself.
 * <p>
 * {@link #getCode()} is the stable storage value written to {@code t_asset.kind}.
 * It is decoupled from {@code ordinal()} so enum reordering never corrupts data
 * (ops SQL hardcodes these literals).
 */
public enum AssetKind {

    DEVICE(1, true, false),
    SERVICE(2, false, false),
    PROBE(3, false, true),
    CONTROL(4, false, true);

    private final int      code;
    private final boolean  compound;
    private final boolean  monitor;

    AssetKind(int code, boolean compound, boolean monitor) {
        this.code     = code;
        this.compound = compound;
        this.monitor  = monitor;
    }

    /** Whether this kind represents a compound (container) asset. */
    public boolean isCompound() {
        return compound;
    }

    /** Whether this kind represents a monitor (leaf) asset. */
    public boolean isMonitor() {
        return monitor;
    }

    /** Stable storage code written to {@code t_asset.kind}. */
    public int getCode() {
        return code;
    }

    /**
     * Resolves the kind for a stored {@code t_asset.kind} value, or {@code null} if unknown.
     * Deliberately null-tolerant (unlike {@link MonitorMode#fromCode},
     * which throws): a corrupt stored code must not break statistics reads.
     */
    public static AssetKind fromCode(int code) {
        for (AssetKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        return null;
    }
}
