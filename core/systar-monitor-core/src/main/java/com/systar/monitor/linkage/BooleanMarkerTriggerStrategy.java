package com.systar.monitor.linkage;

import com.systar.monitor.asset.AssetState;
import com.systar.monitor.asset.Monitor;
import com.systar.monitor.asset.type.MonitorType;
import com.systar.monitor.result.MonitorResult;

/**
 * Default linkage trigger strategy that matches the legacy project behavior.
 * <p>
 * A monitor result triggers linkage when all of the following are true:
 * <ol>
 *   <li>The monitor's state is {@link AssetState#NORMAL}.</li>
 *   <li>The monitor's type unit contains the pipe character {@code "|"}
 *       (the convention for boolean / binary probes).</li>
 * </ol>
 */
public class BooleanMarkerTriggerStrategy implements LinkageTriggerStrategy {

    private static final String UNIT_BOOLEAN_MARKER = "|";

    @Override
    public boolean shouldTrigger(Monitor<?> monitor, MonitorResult result) {
        if (monitor.getState() != AssetState.NORMAL) {
            return false;
        }

        MonitorType type = monitor.getType();
        return type != null
                && type.getUnit() != null
                && type.getUnit().contains(UNIT_BOOLEAN_MARKER);
    }
}
