package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Asset type for monitor services.
 * <p>
 * Services are the top-level containers that manage groups of monitors.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ServiceType extends AssetType {

    public ServiceType() {
    }

    public ServiceType(String name) {
        super(name, AssetKind.SERVICE);
    }
}
