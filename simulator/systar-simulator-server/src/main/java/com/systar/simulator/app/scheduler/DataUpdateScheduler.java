package com.systar.simulator.app.scheduler;

import com.systar.simulator.fleet.DependencyResolver;
import com.systar.simulator.fleet.FleetManager;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.DeviceStatus;
import com.systar.simulator.model.FaultType;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;
import com.systar.simulator.protocol.ProtocolServer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives periodic data generation for all running devices in the fleet.
 * <p>
 * On each tick, the scheduler iterates over every RUNNING device without an
 * active {@link FaultType#STALE_DATA} fault, resolves its data points in
 * topological order (via {@link DependencyResolver}), generates new values,
 * and pushes them to the corresponding {@link ProtocolServer}.
 * <p>
 * Instantiated as a Spring bean via {@code ProfileLoadingConfig}.
 */
public class DataUpdateScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DataUpdateScheduler.class);

    private final FleetManager                      fleetManager;
    private final Map<ProtocolType, ProtocolServer> servers;
    private final ScheduledExecutorService          executor;
    private final long                              tickIntervalMs;
    private final Random                            random;
    private final long                              startMillis;
    private final Map<String, List<DataPoint>>      orderCache      = new ConcurrentHashMap<>();
    private final DependencyResolver                dependencyResolver = new DependencyResolver();

    public DataUpdateScheduler(FleetManager fleetManager,
                               Map<ProtocolType, ProtocolServer> servers,
                               long tickIntervalMs,
                               Long randomSeed) {
        this.fleetManager   = fleetManager;
        this.servers        = servers;
        this.tickIntervalMs = tickIntervalMs;
        this.random         = randomSeed != null ? new Random(randomSeed) : new Random();
        this.startMillis    = System.currentTimeMillis();
        this.executor       = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulator-tick");
            t.setDaemon(true);
            return t;
        });
    }

    @PostConstruct
    public void init() {
        executor.scheduleAtFixedRate(this::tick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
        LOG.info("DataUpdateScheduler started with tick interval {}ms", tickIntervalMs);
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
        LOG.info("DataUpdateScheduler stopped");
    }

    void tick() {
        try {
            for (SimulatedDevice device : fleetManager.listDevices()) {
                if (device.getStatus() != DeviceStatus.RUNNING) continue;
                if (device.getActiveFault() == FaultType.STALE_DATA) continue;
                updateDevice(device);
            }
        } catch (Exception e) {
            LOG.error("Error during tick", e);
        }
    }

    private void updateDevice(SimulatedDevice device) {
        List<DataPoint> ordered = orderCache.computeIfAbsent(device.getId(),
                id -> dependencyResolver.resolveOrder(device));

        TickContext ctx = new TickContext(device, startMillis, random);

        for (DataPoint dp : ordered) {
            Object value = dp.getOverride();
            if (value == null && dp.getGenerator() != null) {
                value = dp.getGenerator().generate(ctx);
            }
            dp.setCurrentValue(value);
            dp.setLastUpdateMillis(System.currentTimeMillis());
        }

        ProtocolServer server = servers.get(device.getProtocol());
        if (server != null) {
            for (DataPoint dp : ordered) {
                if (dp.getAddress() != null && dp.getCurrentValue() != null) {
                    server.updateValue(device.getId(), dp.getAddress(), dp.getCurrentValue());
                }
            }
        }
    }

    // ======================== Inner classes ========================

    private static class TickContext implements com.systar.simulator.generator.GenerationContext {

        private final SimulatedDevice device;
        private final long            startMillis;
        private final Random          random;

        TickContext(SimulatedDevice device, long startMillis, Random random) {
            this.device      = device;
            this.startMillis = startMillis;
            this.random      = random;
        }

        @Override
        public long elapsedMillis() {
            return System.currentTimeMillis() - startMillis;
        }

        @Override
        public Optional<Object> getPeerValue(String dataPointId) {
            for (DataPoint dp : device.getDataPoints()) {
                if (dp.getId().equals(dataPointId)) {
                    return Optional.ofNullable(dp.getCurrentValue());
                }
            }
            return Optional.empty();
        }

        @Override
        public Random random() {
            return random;
        }
    }
}
