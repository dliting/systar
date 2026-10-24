package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Asset type for probe monitors.
 * <p>
 * Inherits all monitoring metadata from {@link MonitorType} with no additional fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProbeType extends MonitorType {

    public ProbeType() {
    }

    public ProbeType(String name) {
        super(name, AssetKind.PROBE);
    }
}
