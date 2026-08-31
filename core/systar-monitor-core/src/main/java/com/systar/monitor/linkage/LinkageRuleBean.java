package com.systar.monitor.linkage;

import lombok.Data;

import java.util.List;

/**
 * Represents a complete linkage rule with its causes and effects.
 * Used for in-memory rule evaluation in {@link LinkageHandler}.
 */
@Data
public class LinkageRuleBean {

    private int       id;
    private String    name;
    private CauseType causeType;
    private boolean   enabled;
    private String    caption;

    private List<LinkageRuleCauseBean> causes;
    private List<LinkageRuleEffectBean> effects;
}
