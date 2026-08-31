package com.systar.monitor.server;

import com.systar.monitor.alarm.AlarmHandler;
import com.systar.monitor.alarm.AlarmRule;
import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.AssetTypeLoader;
import com.systar.monitor.linkage.CauseType;
import com.systar.monitor.linkage.LinkageHandler;
import com.systar.monitor.linkage.LinkageRuleBean;
import com.systar.monitor.linkage.LinkageRuleCauseBean;
import com.systar.monitor.linkage.LinkageRuleEffectBean;
import com.systar.monitor.result.MonitorResult;
import com.systar.monitor.result.ResultDispatcher;
import com.systar.monitor.schedule.MonitorScheduler;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Facade that integrates all core monitoring components.
 * <p>
 * Central entry-point for the monitoring subsystem. Delegates to specialised
 * components for asset management, result dispatching, scheduling, alarm
 * processing, and linkage execution.
 * <p>
 * Managed by Spring; lifecycle methods ({@link #startUp()}, {@link #shutDown()})
 * should be called by the application bootstrap code or a dedicated runner.
 */
@Component
public class MonitorServer {

    private static final Logger log = LoggerFactory.getLogger(MonitorServer.class);

    // ======================== injected collaborators ========================

    private final AssetStore assetStore;
    private final ResultDispatcher resultDispatcher;
    private final MonitorScheduler scheduler;
    private final AlarmHandler alarmHandler;
    private final LinkageHandler linkageHandler;
    private final List<AssetTypeLoader> typeLoaders;

    public MonitorServer(AssetStore assetStore,
                         ResultDispatcher resultDispatcher,
                         MonitorScheduler scheduler,
                         AlarmHandler alarmHandler,
                         LinkageHandler linkageHandler,
                         List<AssetTypeLoader> typeLoaders) {
        this.assetStore = assetStore;
        this.resultDispatcher = resultDispatcher;
        this.scheduler = scheduler;
        this.alarmHandler = alarmHandler;
        this.linkageHandler = linkageHandler;
        this.typeLoaders = typeLoaders != null ? typeLoaders : List.of();
    }

    // ======================== lifecycle ========================

    /**
     * Loads asset definitions from an external source into the {@link AssetStore}.
     * <p>
     * First runs all registered {@link AssetTypeLoader}s to populate type registries,
     * then delegates to the given {@link AssetLoader} for instance loading.
     *
     * @param loader the loader responsible for populating the asset store
     */
    public void loadAssets(AssetLoader loader) {
        log.info("Loading assets into AssetStore...");

        // Phase 1: load type definitions
        for (AssetTypeLoader typeLoader : typeLoaders) {
            typeLoader.load(assetStore);
        }

        // Phase 2: load asset instances
        if (loader != null) {
            loader.load(assetStore);
        }
        log.info("Assets loaded. Total count: {}", assetStore.getAssets().size());
    }

    /**
     * Loads alarm rules into the {@link AlarmHandler}.
     *
     * @param rules the alarm rules to load
     */
    public void loadAlarmRules(List<AlarmRule> rules) {
        log.info("Loading alarm rules...");
        alarmHandler.loadRules(rules);
    }

    /**
     * Loads linkage rules (causes and effects) into the {@link LinkageHandler}.
     *
     * @param causes  the linkage cause conditions
     * @param effects the linkage effect actions
     */
    public void loadLinkageRules(List<LinkageRuleBean> rules,
                                 List<LinkageRuleCauseBean> causes,
                                 List<LinkageRuleEffectBean> effects) {
        log.info("Loading linkage rules...");
        linkageHandler.loadRules(rules, causes, effects);
    }

    /**
     * Starts the monitor server.
     * <p>
     * Starts passive services and the scheduler so that active monitors begin
     * their detection cycles.
     */
    public void startUp() {
        log.info("Starting MonitorServer...");

        // Start passive services
        startPassiveServices();

        // The MonitorScheduler is a @Component with @PostConstruct/@PreDestroy,
        // so its start() is called automatically by Spring. However, we still
        // trigger it here for explicit lifecycle control (e.g. when the scheduler
        // was stopped and needs to be restarted).
        scheduler.start();

        log.info("MonitorServer started.");
    }

    /**
     * Shuts down the monitor server.
     * <p>
     * Stops passive services, then delegates to the scheduler for clean
     * termination of all scheduled tasks.
     */
    @PreDestroy
    public void shutDown() {
        log.info("Shutting down MonitorServer...");

        // Stop passive services first
        stopPassiveServices();

        // Stop the scheduler (cancels all tasks, shuts down thread pools)
        scheduler.stop();

        log.info("MonitorServer shut down.");
    }

    // ======================== query operations ========================

    /**
     * Finds an asset by id.
     *
     * @param id the asset id
     * @return the asset, or {@code null} if not found
     */
    public Asset<?> findAsset(int id) {
        return assetStore.findAsset(id);
    }

    /**
     * Returns all assets in the store.
     *
     * @return unmodifiable collection of all assets
     */
    public Collection<Asset<?>> getAssets() {
        return assetStore.getAssets();
    }

    /**
     * Returns all assets matching the given kind.
     *
     * @param kind the asset kind to filter by
     * @return list of matching assets
     */
    public List<Asset<?>> getAssetsByKind(AssetKind kind) {
        return assetStore.getAssetsByKind(kind);
    }

    /**
     * Returns the full path of the asset identified by the given id.
     *
     * @param id the asset id
     * @return the full path string, or an empty string if not found
     */
    public String getAssetPath(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        return asset != null ? assetStore.getFullPath(asset) : "";
    }

    // ======================== monitor operations ========================

    /**
     * Adds an asset to the store.
     * <p>
     * If the asset is a monitor belonging to an active service, it is also
     * scheduled for detection.
     *
     * @param asset the asset to add
     */
    public void addAsset(Asset<?> asset) {
        assetStore.addAsset(asset);

        // If the asset is a monitor, schedule it for detection
        if (asset instanceof Monitor<?> monitor) {
            MonitorService source = monitor.getSource();
            if (source != null && source.getMode() == MonitorMode.ACTIVE) {
                scheduler.scheduleMonitor(monitor);
            }
        }
    }

    /**
     * Removes an asset from the store.
     * <p>
     * If the asset is a scheduled monitor, it is unscheduled first.
     *
     * @param id the asset id to remove
     */
    public void removeAsset(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset instanceof Monitor<?>) {
            scheduler.unscheduleMonitor(id);
        }
        assetStore.removeAsset(id);
    }

    /**
     * Updates an asset in the store by atomically replacing the old version.
     * <p>
     * Unschedules the old monitor (if any), replaces the asset via
     * {@link AssetStore#replaceAsset}, and reschedules if the new asset
     * is a monitor belonging to an active service.
     *
     * @param asset the updated asset instance
     */
    public void updateAsset(Asset<?> asset) {
        Asset<?> old = assetStore.findAsset(asset.getId());
        if (old instanceof Monitor<?>) {
            scheduler.unscheduleMonitor(asset.getId());
        }
        assetStore.replaceAsset(asset.getId(), asset);

        if (asset instanceof Monitor<?> monitor) {
            MonitorService source = monitor.getSource();
            if (source != null && source.getMode() == MonitorMode.ACTIVE) {
                scheduler.scheduleMonitor(monitor);
            }
        }
    }

    /**
     * Starts a monitor at runtime (schedule only, no DB write).
     *
     * @param id the monitor asset id
     * @throws AssetException if the asset is not found or is not a monitor
     */
    public void startMonitor(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) {
            throw new AssetException("Asset %d not found; cannot start.".formatted(id));
        }
        if (!(asset instanceof Monitor<?> monitor)) {
            throw new AssetException("Asset %d is not a monitor; cannot start.".formatted(id));
        }
        asset.setEnabled(true);
        scheduler.scheduleMonitor(monitor);
        log.info("Started monitor: id={} name={}", id, asset.getName());
    }

    /**
     * Stops a monitor at runtime (unschedule only, no DB write).
     *
     * @param id the monitor asset id
     * @throws AssetException if the asset is not found or is not a monitor
     */
    public void stopMonitor(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) {
            throw new AssetException("Asset %d not found; cannot stop.".formatted(id));
        }
        if (!(asset instanceof Monitor<?>)) {
            throw new AssetException("Asset %d is not a monitor; cannot stop.".formatted(id));
        }
        scheduler.unscheduleMonitor(id);
        log.info("Stopped monitor: id={} name={}", id, asset.getName());
    }

    /**
     * Triggers a one-shot detect on a monitor.
     * The result flows through the full {@link ResultDispatcher} pipeline:
     * normalization → persistence → alarm evaluation → WebSocket push.
     * Only works for active monitors; passive monitors rely on external data push.
     */
    public void detectOnce(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) {
            throw new AssetException("Asset %d not found; cannot detect.".formatted(id));
        }
        if (!(asset instanceof Monitor<?> monitor)) {
            throw new AssetException("Asset %d is not a monitor; cannot detect.".formatted(id));
        }
        if (monitor.getMode() == MonitorMode.PASSIVE) {
            throw new AssetException(
                    "Asset %d is a passive monitor; cannot detect manually.".formatted(id));
        }
        MonitorResult result = new MonitorResult(monitor);
        try {
            monitor.detect(result);
        } catch (Exception e) {
            log.error("Manual detect failed: id={} name={}", id, asset.getName(), e);
            throw new AssetException("Manual detect failed for asset %d: %s".formatted(id, e.getMessage()));
        }
        resultDispatcher.dispatch(result);
        log.info("Manual detect: id={} name={}", id, asset.getName());
    }

    /**
     * Submits a manual detection for the given asset to the scheduler for immediate
     * execution. Unlike {@link #detectOnce}, this method returns immediately — the
     * HTTP thread is not blocked on network I/O.
     * <p>
     * The scheduler cancels any existing scheduled task, creates a manual
     * {@code DetectTask}, and submits it with zero delay. After completion the
     * {@code CompletionHandler} re-schedules the next periodic detection, so the
     * next auto-detect is postponed from now.
     * <p>
     * Results are pushed to the frontend via the existing WebSocket
     * {@code MonitorResultPusher} (triggered automatically by the dispatch pipeline).
     *
     * @param id the asset id (must be an active Monitor)
     * @throws AssetException if the asset does not exist, is not a Monitor, or is passive
     * @throws IllegalStateException if the monitor is already detecting (409)
     */
    public void detectImmediately(int id) {
        Asset<?> asset = assetStore.findAsset(id);
        if (asset == null) {
            throw new AssetException("Asset %d not found; cannot detect.".formatted(id));
        }
        if (!(asset instanceof Monitor<?> monitor)) {
            throw new AssetException("Asset %d is not a monitor; cannot detect.".formatted(id));
        }
        if (monitor.getMode() == MonitorMode.PASSIVE) {
            throw new AssetException(
                    "Asset %d is a passive monitor; cannot detect manually.".formatted(id));
        }
        // Delegates to the scheduler for dedup, queuing, and reschedule-after-completion.
        scheduler.detectImmediately(monitor);
    }

    /**
     * Executes a control command on the asset identified by the given id.
     * <p>
     * The result (success or failure) is dispatched through the
     * {@link ResultDispatcher} so that downstream handlers (alarm, linkage,
     * persistence) can react.
     *
     * @param controlId the id of the control asset
     * @param command   the command to execute
     * @return "success" or an error description
     */
    public String controlExecute(int controlId, String command) {
        Asset<?> asset = assetStore.findAsset(controlId);
        if (asset == null) {
            String err = "Asset " + controlId + " not found.";
            log.error(err);
            return err;
        }
        if (!(asset instanceof Control control)) {
            String err = "Asset " + controlId + " is not a control.";
            log.error(err);
            return err;
        }
        try {
            control.execute(command);
            return "success";
        } catch (Exception e) {
            String err = String.format(
                    "Failed to execute control(%s) command '%s': %s",
                    control, command, e.getMessage());
            log.error(err, e);
            resultDispatcher.dispatch(new MonitorResult(control, err));
            return err;
        }
    }

    /**
     * Submits an async control command for immediate execution via the scheduler.
     * Unlike {@link #controlExecute}, this method returns immediately — the HTTP
     * thread is not blocked on device I/O.
     * <p>
     * After the command executes, the control's state is re-detected and the result
     * dispatched. The next periodic detection is postponed from now.
     *
     * @param controlId the id of the control asset
     * @param command   the command to execute
     * @throws AssetException if the asset is not found or not a control
     * @throws IllegalStateException if the control is already executing
     */
    public void controlImmediately(int controlId, String command) {
        Asset<?> asset = assetStore.findAsset(controlId);
        if (asset == null) {
            throw new AssetException(
                    "Asset %d not found; cannot execute control.".formatted(controlId));
        }
        if (!(asset instanceof Control control)) {
            throw new AssetException(
                    "Asset %d is not a control; cannot execute command.".formatted(controlId));
        }
        scheduler.controlImmediately(control, command);
    }

    // ======================== passive data ingestion ========================

    /**
     * Entry-point for passive (push) data.
     * <p>
     * Looks up the monitor registered under the given key across all passive
     * services, then dispatches the value through the result pipeline.
     *
     * @param registerKey the routing key that identifies the target monitor
     * @param value       the data value received from the external source
     */
    public void receivePassiveData(String registerKey, Object value) {
        if (registerKey == null || registerKey.isBlank()) {
            log.warn("receivePassiveData called with null/blank registerKey.");
            return;
        }

        // Find the monitor across all passive services
        Monitor<?> monitor = findPassiveMonitor(registerKey);
        if (monitor == null) {
            log.warn("No passive monitor found for registerKey: {}", registerKey);
            return;
        }

        MonitorResult result = new MonitorResult(monitor, value);
        resultDispatcher.dispatch(result);
    }

    // ======================== configuration hot-reload ========================

    /**
     * Hot-reloads alarm rules without restarting the server.
     *
     * @param rules the new set of alarm rules
     */
    public void reloadAlarmRules(List<AlarmRule> rules) {
        log.info("Reloading alarm rules...");
        alarmHandler.loadRules(rules);
        log.info("Alarm rules reloaded.");
    }

    /**
     * Hot-reloads linkage rules without restarting the server.
     *
     * @param rules   the new set of linkage rules
     * @param causes  the new set of linkage cause conditions
     * @param effects the new set of linkage effect actions
     */
    public void reloadLinkageRules(List<LinkageRuleBean> rules,
                                   List<LinkageRuleCauseBean> causes,
                                   List<LinkageRuleEffectBean> effects) {
        log.info("Reloading linkage rules...");
        linkageHandler.loadRules(rules, causes, effects);
        log.info("Linkage rules reloaded.");
    }

    // ======================== private helpers ========================

    /**
     * Iterates all SERVICE assets and starts those with PASSIVE mode.
     */
    private void startPassiveServices() {
        List<Asset<?>> services = assetStore.getAssetsByKind(AssetKind.SERVICE);
        for (Asset<?> asset : services) {
            if (!(asset instanceof MonitorService service)) {
                continue;
            }
            if (service.getMode() == MonitorMode.PASSIVE && service instanceof PassiveService passiveSvc) {
                try {
                    passiveSvc.setResultDispatcher(resultDispatcher);
                    passiveSvc.start();
                    registerPassiveMonitors(passiveSvc, service);
                    log.info("Started passive service: {}", service.getName());
                } catch (Exception e) {
                    log.error("Failed to start passive service: {}", service.getName(), e);
                }
            }
        }
    }

    /**
     * Registers all monitors belonging to the given service with the
     * passive service's routing dictionary.
     */
    private void registerPassiveMonitors(PassiveService passiveSvc, MonitorService service) {
        for (AssetKind kind : new AssetKind[]{AssetKind.PROBE, AssetKind.CONTROL}) {
            for (Asset<?> a : assetStore.getAssetsByKind(kind)) {
                if (a instanceof Monitor<?> m && m.getSource() == service) {
                    String key = String.valueOf(m.getId());
                    passiveSvc.registerMonitor(key, m);
                }
            }
        }
    }

    /**
     * Iterates all SERVICE assets and stops those with PASSIVE mode.
     */
    private void stopPassiveServices() {
        List<Asset<?>> services = assetStore.getAssetsByKind(AssetKind.SERVICE);
        for (Asset<?> asset : services) {
            if (!(asset instanceof MonitorService service)) {
                continue;
            }
            if (service.getMode() == MonitorMode.PASSIVE && service instanceof PassiveService passiveSvc) {
                try {
                    passiveSvc.stop();
                    log.info("Stopped passive service: {}", service.getName());
                } catch (Exception e) {
                    log.error("Failed to stop passive service: {}", service.getName(), e);
                }
            }
        }
    }

    /**
     * Finds a monitor by its register key across all passive services.
     *
     * @param registerKey the routing key
     * @return the monitor, or {@code null} if not found
     */
    private Monitor<?> findPassiveMonitor(String registerKey) {
        List<Asset<?>> services = assetStore.getAssetsByKind(AssetKind.SERVICE);
        for (Asset<?> asset : services) {
            if (asset instanceof PassiveService passiveSvc) {
                Monitor<?> monitor = passiveSvc.getMonitor(registerKey);
                if (monitor != null) {
                    return monitor;
                }
            }
        }
        return null;
    }
}
