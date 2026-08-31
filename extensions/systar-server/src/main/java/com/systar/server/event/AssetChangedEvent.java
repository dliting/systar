package com.systar.server.event;

import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetKind;

/**
 * Published when an asset is created, updated, deleted, or has its runtime
 * state changed (started/stopped/disabled/enabled).
 */
public record AssetChangedEvent(
        Action action,
        int assetId,
        AssetKind kind,
        Asset<?> asset
) {
    public enum Action {
        CREATED,
        UPDATED,
        DELETED,
        STARTED,
        STOPPED,
        DISABLED,
        ENABLED
    }
}
