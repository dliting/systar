package com.systar.monitor.linkage;

import lombok.Data;

/**
 * Represents a single <em>cause</em> condition in a linkage rule.
 * <p>
 * When the monitor identified by {@code causeMonitorId} produces a value that
 * matches {@code causeValue}, the linkage rule (identified by {@code ruleId})
 * fires all of its associated {@link LinkageRuleEffectBean} actions.
 */
@Data
public class LinkageRuleCauseBean {

    /** Primary key. */
    private int id;

    /** The linkage rule this cause belongs to. */
    private int ruleId;

    /** The monitor whose value change triggers this linkage. */
    private int causeMonitorId;

    /**
     * The expected value (as a string) that triggers the linkage.
     * Matched against the string representation of the monitor's sampled value.
     */
    private String triggerValue;
}
