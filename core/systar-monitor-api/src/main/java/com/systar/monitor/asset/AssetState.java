package com.systar.monitor.asset;

/**
 * Enumeration of asset states, ordered by severity (ascending).
 * <p>
 * NORMAL(0) &lt; WARNING(1) &lt; ERROR(2) &lt; OFFLINE(3)
 */
public enum AssetState {

    NORMAL(0, "正常"),
    WARNING(1, "警告"),
    ERROR(2, "错误"),
    OFFLINE(3, "离线");

    private final int severity;
    private final String caption;

    AssetState(int severity, String caption) {
        this.severity = severity;
        this.caption = caption;
    }

    /** Returns the numeric severity level. */
    public int getSeverity() {
        return severity;
    }

    /** Returns the human-readable Chinese caption. */
    public String getCaption() {
        return caption;
    }

    /** Returns true if this state is more severe than the other. */
    public boolean isMoreSevereThan(AssetState other) {
        return this.severity > other.severity;
    }

    /** Returns the more severe of the two states. */
    public static AssetState max(AssetState a, AssetState b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.severity >= b.severity ? a : b;
    }
}
