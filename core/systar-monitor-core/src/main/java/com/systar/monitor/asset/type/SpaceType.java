package com.systar.monitor.asset.type;

import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Type definition for space assets.
 * <p>
 * Extends {@link AssetType} with space-specific attributes such as area and
 * display ordering.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SpaceType extends AssetType {

    /** Area of the space in square metres. */
    private double area;

    /** Sort order sequence (lower values appear first). */
    private int sequence;

    public SpaceType() {
        super();
    }

    public SpaceType(String name) {
        super(name, AssetKind.SPACE);
    }
}
