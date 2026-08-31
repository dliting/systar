package com.systar.monitor.linkage;

import com.systar.monitor.asset.Monitor;
import com.systar.monitor.result.MonitorResult;

/**
 * Strategy interface for determining whether a monitor result should trigger
 * linkage evaluation.
 * <p>
 * Implementations encapsulate the trigger condition logic, allowing different
 * linkage trigger mechanisms without modifying {@link LinkageHandler}.
 */
public interface LinkageTriggerStrategy {

    /**
     * Evaluates whether the given monitor result should trigger linkage processing.
     *
     * @param monitor the monitor that produced the result
     * @param result  the monitor result
     * @return true if linkage evaluation should proceed
     */
    boolean shouldTrigger(Monitor<?> monitor, MonitorResult result);
}
