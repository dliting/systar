package com.systar.monitor.asset;

import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.Space;

/**
 * Visitor interface for the asset hierarchy.
 * <p>
 * Implementations can process different asset kinds without down-casting.
 *
 * @param <T> the return type of visit methods
 */
public interface AssetVisitor<T> {

    T visit(Space space);

    T visit(Device device);

    T visit(Probe probe);

    T visit(VirtualProbe vp);

    T visit(Control control);

    T visit(MonitorService service);
}
