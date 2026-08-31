package com.systar.monitor.asset;

import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;

/**
 * A probe monitor that samples a read-only data point.
 * <p>
 * Probes represent sensors or read-only registers. In ACTIVE mode the
 * scheduler polls them; in PASSIVE mode they receive pushed data routed
 * by their source key via {@link IPassiveMonitor#makeRegisterKey()}.
 */
public class Probe extends Monitor<ProbeType> implements IPassiveMonitor {

    public Probe() {
    }

    // ======================== Asset ========================

    @Override
    public AssetKind getKind() {
        return AssetKind.PROBE;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // ======================== detection ========================

    /**
     * Default detection implementation for active probes.
     * <p>
     * In ACTIVE mode, the driver layer should override this method to
     * read from the data source specified by the type's {@code source} field.
     * In PASSIVE mode, data is pushed externally; this method may be a no-op.
     * <p>
     * Subclasses are expected to override this with driver-specific logic.
     */
    @Override
    public void detect(IMonitorResult result) throws Exception {
        // Default no-op; driver layer should override for active probes.
        // Passive probes receive data via PassiveService routing.
    }

    // ======================== IPassiveMonitor ========================

    /**
     * Returns the source field from the type as the routing key.
     * Only meaningful when {@code mode == PASSIVE}.
     */
    @Override
    public String makeRegisterKey() {
        ProbeType t = getType();
        return t != null ? t.getSource() : null;
    }
}
