package com.systar.monitor.asset;

import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.MonitorResultEvent;
import com.systar.monitor.result.ResultDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Engine that recalculates VirtualProbe values when their dependencies update.
 * <p>
 * Maintains a dependency index: when probe X produces a new result, all
 * VirtualProbes that depend on X are triggered for recalculation.
 * <p>
 * Circular dependencies are detected and prevented.
 */
@Slf4j
@Component
public class VirtualProbeEngine {

    private final AssetStore assetStore;
    private final ResultDispatcher resultDispatcher;

    /** Maps probeId → list of VirtualProbes that depend on it. */
    private final Map<Integer, List<VirtualProbe>> dependencyIndex = new ConcurrentHashMap<>();

    /** Tracks which probes are currently being computed, to detect cycles. */
    private final Set<Integer> computing = ConcurrentHashMap.newKeySet();

    public VirtualProbeEngine(AssetStore assetStore, ResultDispatcher resultDispatcher) {
        this.assetStore     = assetStore;
        this.resultDispatcher = resultDispatcher;
    }

    /**
     * Registers a VirtualProbe in the dependency index.
     * Must be called after the VirtualProbe is initialized and its dependsOn list is parsed.
     */
    public void register(VirtualProbe vp) {
        for (int depId : vp.getDependsOn()) {
            List<VirtualProbe> list = dependencyIndex.computeIfAbsent(depId,
                    k -> new CopyOnWriteArrayList<>());
            if (!list.contains(vp)) {
                list.add(vp);
            }
        }
        log.info("VirtualProbe registered: id={}, dependsOn={}", vp.getId(), vp.getDependsOn());
    }

    /**
     * Removes a VirtualProbe from the dependency index.
     */
    public void unregister(int virtualProbeId) {
        dependencyIndex.values().forEach(list -> list.removeIf(vp -> vp.getId() == virtualProbeId));
        dependencyIndex.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Listens for MonitorResultEvents. When a regular probe updates, triggers
     * recalculation of any VirtualProbes that depend on it.
     */
    @EventListener
    public void onMonitorResult(MonitorResultEvent event) {
        if (event.getResult() == null || event.getResult().getMonitor() == null) {
            return;
        }

        int sourceId = event.getResult().getMonitor().getId();
        List<VirtualProbe> dependents = dependencyIndex.get(sourceId);
        if (dependents == null || dependents.isEmpty()) {
            return;
        }

        for (VirtualProbe vp : List.copyOf(dependents)) {
            recalculate(vp);
        }
    }

    /**
     * Recalculates a single VirtualProbe.
     * Prevents circular dependency by tracking which probes are currently computing.
     */
    private void recalculate(VirtualProbe vp) {
        if (!computing.add(vp.getId())) {
            log.warn("Circular dependency detected for VirtualProbe id={}. Skipping recalculation.", vp.getId());
            return;
        }

        try {
            MonitorResult result = new MonitorResult(vp);
            vp.detect(result);
            if (result.hasError()) {
                log.warn("VirtualProbe recalculation error: id={}, error={}", vp.getId(), result.getError());
            }
            resultDispatcher.dispatch(result);
        } catch (Exception e) {
            log.error("VirtualProbe recalculation failed: id={}, error={}", vp.getId(), e.getMessage(), e);
        } finally {
            computing.remove(vp.getId());
        }
    }

    /**
     * Returns the current dependency index (for diagnostics/testing).
     */
    public Map<Integer, List<VirtualProbe>> getDependencyIndex() {
        return Collections.unmodifiableMap(dependencyIndex);
    }
}
