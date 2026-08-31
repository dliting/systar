package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Asset type for virtual (calculated) probes.
 * <p>
 * A VirtualProbe derives its value from an expression that references
 * other probes' real-time values, rather than sampling a physical data source.
 * The expression uses SpEL syntax with {@code #probe[id].value} references.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VirtualProbeType extends ProbeType {

    /** Expression that computes the derived value (e.g., "#probe[101].value / #probe[102].value * 100"). */
    private String expression;

    /** Comma-separated list of probe IDs this virtual probe depends on (e.g., "101,102"). */
    private String dependsOn;

    public VirtualProbeType() {
    }

    public VirtualProbeType(String name) {
        super(name);
    }
}