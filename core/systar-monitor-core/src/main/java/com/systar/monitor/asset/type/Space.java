package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import com.systar.monitor.asset.AssetVisitor;
import com.systar.monitor.asset.CompoundAsset;

/**
 * A space asset representing a physical or logical area.
 * <p>
 * Spaces are compound assets that can contain devices, sub-spaces, or monitor
 * assets. Properties such as area and sequence are managed via {@link SpaceType}.
 */
public class Space extends CompoundAsset<SpaceType> {

    public Space() {
        super();
    }

    @Override
    public AssetKind getKind() {
        return AssetKind.SPACE;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
