package com.systar.server.listener;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.VirtualProbeType;
import com.systar.monitor.server.MonitorServer;
import com.systar.server.event.AssetChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bridges {@link AssetChangedEvent} to {@link MonitorServer} runtime operations.
 * <p>
 * Listens for CRUD and lifecycle events published by
 * {@link com.systar.server.service.AssetOrchestrator} and applies the
 * corresponding in-memory store and scheduler changes.
 * <p>
 * All cascade logic (disable children, smart-enable) lives here so that
 * {@code AssetOrchestrator} remains focused on DB persistence.
 */
@Component
public class AssetCrudListener {

    private static final Logger log = LoggerFactory.getLogger(AssetCrudListener.class);

    private final MonitorServer monitorServer;
    private final VirtualProbeEngine virtualProbeEngine;
    private final AssetStore assetStore;

    public AssetCrudListener(MonitorServer monitorServer, VirtualProbeEngine virtualProbeEngine,
                             AssetStore assetStore) {
        this.monitorServer    = monitorServer;
        this.virtualProbeEngine = virtualProbeEngine;
        this.assetStore       = assetStore;
    }

    @EventListener
    public void onAssetChanged(AssetChangedEvent event) {
        switch (event.action()) {
            case CREATED -> handleCreated(event);
            case UPDATED -> handleUpdated(event);
            case DELETED -> handleDeleted(event);
            case STARTED -> handleRuntime(event, this::handleStarted);
            case STOPPED -> handleRuntime(event, this::handleStopped);
            case DISABLED -> handleRuntime(event, this::handleDisabled);
            case ENABLED -> handleRuntime(event, this::handleEnabled);
        }
    }

    /**
     * Runtime operations (start/stop/disable/enable) are non-transactional.
     * Log failures instead of propagating to avoid misleading error responses.
     */
    private void handleRuntime(AssetChangedEvent event, java.util.function.Consumer<AssetChangedEvent> handler) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.error("Failed to handle runtime asset event {}: id={} kind={}",
                    event.action(), event.assetId(), event.kind(), e);
        }
    }

    private void handleCreated(AssetChangedEvent event) {
        if (event.asset() != null) {
            monitorServer.addAsset(event.asset());
            if (event.asset() instanceof VirtualProbe vp
                    && vp.getType() instanceof VirtualProbeType) {
                vp.setAssetStore(assetStore);
                virtualProbeEngine.register(vp);
            }
        }
    }

    private void handleUpdated(AssetChangedEvent event) {
        if (event.asset() == null) return;
        Asset<?> asset = event.asset();
        monitorServer.updateAsset(asset);

        // Always unregister for PROBE updates to handle VP→non-VP type conversion
        if (event.kind() == AssetKind.PROBE) {
            virtualProbeEngine.unregister(event.assetId());
        }
        if (asset instanceof VirtualProbe vp && vp.getType() instanceof VirtualProbeType) {
            vp.setAssetStore(assetStore);
            vp.parseDependsOn();
            vp.compileExpression();
            virtualProbeEngine.register(vp);
        }
    }

    private void handleDeleted(AssetChangedEvent event) {
        int id = event.assetId();
        if (event.kind() == AssetKind.PROBE) {
            virtualProbeEngine.unregister(id);
        }
        monitorServer.removeAsset(id);
    }

    private void handleStarted(AssetChangedEvent event) {
        monitorServer.startMonitor(event.assetId());
    }

    private void handleStopped(AssetChangedEvent event) {
        monitorServer.stopMonitor(event.assetId());
    }

    private void handleDisabled(AssetChangedEvent event) {
        int id = event.assetId();
        Asset<?> asset = monitorServer.findAsset(id);
        if (asset == null) return;

        // Unschedule if monitor
        if (asset instanceof Monitor<?>) {
            monitorServer.stopMonitor(id);
        }
        asset.setEnabled(false);

        // Cascade disable to children of compound assets
        if (asset instanceof CompoundAsset<?> compound) {
            cascadeDisable(compound);
        }
    }

    private void handleEnabled(AssetChangedEvent event) {
        int id = event.assetId();
        Asset<?> asset = monitorServer.findAsset(id);
        if (asset == null) return;

        asset.setEnabled(true);

        // Smart cascade: re-start monitors that were not explicitly disabled
        if (asset instanceof CompoundAsset<?> compound) {
            cascadeEnable(compound);
        }

        // If the asset itself is a monitor, start it
        if (asset instanceof Monitor<?>) {
            monitorServer.startMonitor(id);
        }
    }

    private void cascadeDisable(CompoundAsset<?> compound) {
        for (Asset<?> child : compound.children()) {
            // Stop monitors but do NOT change child's enabled flag.
            // Parent disable is a runtime operation; individual child
            // enabled/disabled state is a config-level concern.
            if (child instanceof Monitor<?>) {
                monitorServer.stopMonitor(child.getId());
            }

            if (child instanceof CompoundAsset<?> childCompound) {
                cascadeDisable(childCompound);
            }
        }
    }

    private void cascadeEnable(CompoundAsset<?> compound) {
        for (Asset<?> child : compound.children()) {
            // Skip children that were explicitly disabled (not by parent cascade)
            if (!child.isEnabled()) {
                continue;
            }

            if (child instanceof Monitor<?>) {
                monitorServer.startMonitor(child.getId());
            }

            if (child instanceof CompoundAsset<?> childCompound) {
                cascadeEnable(childCompound);
            }
        }
    }
}
