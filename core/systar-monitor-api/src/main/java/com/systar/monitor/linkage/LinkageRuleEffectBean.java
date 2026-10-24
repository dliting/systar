package com.systar.monitor.linkage;

import lombok.Data;

/**
 * Represents a single <em>effect</em> (action) in a linkage rule.
 * <p>
 * When the parent linkage rule fires, each effect causes the
 * {@link #effectCommand} to be executed on the control identified by
 * {@link #effectMonitorId}.
 */
@Data
public class LinkageRuleEffectBean {

    /** Primary key. */
    private int id;

    /** The linkage rule this effect belongs to. */
    private int ruleId;

    /**
     * The control monitor on which the command will be executed
     * via {@link com.systar.monitor.asset.Control#execute(String)}.
     */
    private int effectMonitorId;

    /**
     * The command string to send to the target control.
     * Interpretation is driver-specific (e.g., "on"/"off", "true"/"false",
     * a numeric value, etc.).
     */
    private String effectCommand;
}
