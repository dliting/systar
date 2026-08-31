package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Type definition for device assets.
 * <p>
 * Extends {@link AssetType} with device-specific attributes such as catalog
 * classification and vendor information.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceType extends AssetType {

    /** Device classification code. */
    private int catalog;

    /** Vendor / manufacturer name. */
    private String vendor;

    public DeviceType() {
        super();
    }

    public DeviceType(String name) {
        super(name, AssetKind.DEVICE);
    }
}
