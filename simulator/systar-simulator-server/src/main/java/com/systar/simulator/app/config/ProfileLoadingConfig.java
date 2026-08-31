package com.systar.simulator.app.config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.systar.simulator.app.protocol.ModbusTcpProtocolServer;
import com.systar.simulator.app.protocol.OpcUaProtocolServer;
import com.systar.simulator.app.scheduler.DataUpdateScheduler;
import com.systar.simulator.config.ProfileParser;
import com.systar.simulator.config.ProfileValidator;
import com.systar.simulator.fleet.DefaultFleetManager;
import com.systar.simulator.fleet.FleetManager;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;
import com.systar.simulator.protocol.ProtocolServer;

/**
 * Spring {@code @Configuration} that wires together the simulator components:
 * <ol>
 *   <li>Protocol server beans (Modbus TCP, OPC UA)</li>
 *   <li>{@link FleetManager} backed by those servers</li>
 *   <li>YAML device profile loading and validation</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(SimulatorProperties.class)
public class ProfileLoadingConfig {

    private static final Logger log = LoggerFactory.getLogger(ProfileLoadingConfig.class);

    @Bean
    public ModbusTcpProtocolServer modbusTcpProtocolServer() {
        return new ModbusTcpProtocolServer();
    }

    @Bean
    public OpcUaProtocolServer opcUaProtocolServer() {
        return new OpcUaProtocolServer();
    }

    /**
     * Creates the {@link FleetManager}, loads YAML device profiles from the
     * configured location, validates them, and registers the devices.
     *
     * @throws Exception if profile loading or validation fails
     */
    @Bean
    public FleetManager fleetManager(
            ModbusTcpProtocolServer modbusServer,
            OpcUaProtocolServer opcuaServer,
            SimulatorProperties properties,
            ResourcePatternResolver resourceResolver
    ) throws Exception {
        Map<ProtocolType, ProtocolServer> servers = new EnumMap<>(ProtocolType.class);
        servers.put(ProtocolType.MODBUS_TCP, modbusServer);
        servers.put(ProtocolType.OPC_UA, opcuaServer);

        DefaultFleetManager fleetManager = new DefaultFleetManager(servers);

        ProfileParser   parser   = new ProfileParser();
        ProfileValidator validator = new ProfileValidator();

        String locationPattern = properties.getProfilesLocation() + "*.yml";
        Resource[] resources   = resourceResolver.getResources(locationPattern);

        if (resources.length > 0) {
            log.info("Loading {} profile(s) from: {}", resources.length, locationPattern);
            List<InputStream> streams = new ArrayList<>();
            try {
                for (Resource r : resources) {
                    streams.add(r.getInputStream());
                }
                List<SimulatedDevice> devices = parser.parseAll(streams);
                validator.validate(devices);
                fleetManager.loadProfiles(devices);
                log.info("Loaded {} device(s) into fleet manager.", devices.size());
            } finally {
                for (InputStream s : streams) {
                    try { s.close(); } catch (Exception ignored) { /* best-effort */ }
                }
            }
        } else {
            log.warn("No YAML profiles found at: {}", locationPattern);
        }

        return fleetManager;
    }

    /**
     * Creates the {@link DataUpdateScheduler} that drives periodic data
     * generation for all running devices.
     */
    @Bean
    public DataUpdateScheduler dataUpdateScheduler(
            FleetManager fleetManager,
            SimulatorProperties properties
    ) {
        Map<ProtocolType, ProtocolServer> servers = new EnumMap<>(ProtocolType.class);
        servers.put(ProtocolType.MODBUS_TCP, modbusTcpProtocolServer());
        servers.put(ProtocolType.OPC_UA, opcUaProtocolServer());

        return new DataUpdateScheduler(fleetManager, servers,
                properties.getTickIntervalMs(), properties.getRandomSeed());
    }
}
