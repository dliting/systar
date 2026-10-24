package com.systar.server.listener;

import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.VirtualProbeType;
import com.systar.monitor.server.MonitorEngine;
import com.systar.server.event.AssetChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Bridges {@link AssetChangedEvent} to {@link MonitorEngine} runtime operations.
 * <p>
 * Listens for CRUD and lifecycle events published by
 * {@link com.systar.server.service.AssetOrchestrator} and applies the
 * corresponding in-memory store and scheduler changes. The engine is
 * optional: with it absent, CRUD events are applied to the
 * {@link AssetStore} directly (store mode — no scheduler to register
 * with) and runtime operations are WARN-skipped once.
 * <p>
 * All cascade logic (disable children, smart-enable) lives here so that
 * {@code AssetOrchestrator} remains focused on DB persistence.
 */
@Component
public class AssetCrudListener {

    private static final Logger log = LoggerFactory.getLogger(AssetCrudListener.class);

    private final ObjectProvider<MonitorEngine> engine;
    private final VirtualProbeEngine virtualProbeEngine;
    private final AssetStore assetStore;
    private final AtomicBoolean engineWarned = new AtomicBoolean();

    public AssetCrudListener(ObjectProvider<MonitorEngine> engine,
                             VirtualProbeEngine virtualProbeEngine,
                             AssetStore assetStore) {
        this.engine             = engine;
        this.virtualProbeEngine = virtualProbeEngine;
        this.assetStore         = assetStore;
    }

    /** Runs a runtime operation on the engine, WARN-skipping once when absent. */
    private void withEngine(String operation, Consumer<MonitorEngine> op) {
        MonitorEngine e = engine.getIfAvailable();
        if (e == null) {
            if (engineWarned.compareAndSet(false, true)) {
                log.warn("Monitor engine not present; {} skipped.", operation);
            }
            return;
        }
        op.accept(e);
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
    private void handleRuntime(AssetChangedEvent event, Consumer<AssetChangedEvent> handler) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.error("Failed to handle runtime asset event {}: id={} kind={}",
                    event.action(), event.assetId(), event.kind(), e);
        }
    }

    private void handleCreated(AssetChangedEvent event) {
        if (event.asset() != null) {
            MonitorEngine e = engine.getIfAvailable();
            if (e == null) {
                assetStore.addAsset(event.asset());
            } else {
                e.addAsset(event.asset());
            }
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
        MonitorEngine e = engine.getIfAvailable();
        if (e == null) {
            assetStore.replaceAsset(asset.getId(), asset);
        } else {
            e.updateAsset(asset);
        }

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
        MonitorEngine e = engine.getIfAvailable();
        if (e == null) {
            assetStore.removeAsset(id);
        } else {
            e.removeAsset(id);
        }
    }

    private void handleStarted(AssetChangedEvent event) {
        withEngine("monitor start", e -> e.startMonitor(event.assetId()));
    }

    private void handleStopped(AssetChangedEvent event) {
        withEngine("monitor stop", e -> e.stopMonitor(event.assetId()));
    }

    private void handleDisabled(AssetChangedEvent event) {
        int id = event.assetId();
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) return;

        // Unschedule if monitor
        if (asset instanceof Monitor<?>) {
            withEngine("monitor stop", e -> e.stopMonitor(id));
        }
        asset.setEnabled(false);

        // Cascade disable to children of compound assets
        if (asset instanceof CompoundAsset<?> compound) {
            cascadeDisable(compound);
        }
    }

    private void handleEnabled(AssetChangedEvent event) {
        int id = event.assetId();
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) return;

        asset.setEnabled(true);

        // Smart cascade: re-start monitors that were not explicitly disabled
        if (asset instanceof CompoundAsset<?> compound) {
            cascadeEnable(compound);
        }

        // If the asset itself is a monitor, start it
        if (asset instanceof Monitor<?>) {
            withEngine("monitor start", e -> e.startMonitor(id));
        }
    }

    private void cascadeDisable(CompoundAsset<?> compound) {
        for (Asset<?> child : compound.children()) {
            // Stop monitors but do NOT change child's enabled flag.
            // Parent disable is a runtime operation; individual child
            // enabled/disabled state is a config-level concern.
            if (child instanceof Monitor<?>) {
                withEngine("monitor stop", e -> e.stopMonitor(child.getId()));
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
                withEngine("monitor start", e -> e.startMonitor(child.getId()));
            }

            if (child instanceof CompoundAsset<?> childCompound) {
                cascadeEnable(childCompound);
            }
        }
    }
}
