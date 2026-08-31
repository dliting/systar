package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Asset type for control monitors.
 * <p>
 * Inherits all monitoring metadata from {@link MonitorType} with no additional fields.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ControlType extends MonitorType {

    public ControlType() {
    }

    public ControlType(String name) {
        super(name, AssetKind.CONTROL);
    }
}
