package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetVisitor;
import com.systar.monitor.asset.CompoundAsset;

/**
 * A device asset representing a piece of equipment or hardware.
 * <p>
 * Devices are compound assets that can contain probes, controls, or services.
 * Properties such as catalog and vendor are managed via {@link DeviceType}.
 */
public class Device extends CompoundAsset<DeviceType> {

    public Device() {
        super();
    }

    @Override
    public AssetKind getKind() {
        return AssetKind.DEVICE;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
