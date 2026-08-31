package com.systar.simulator.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.systar.simulator.generator.CorrelatedGenerator;
import com.systar.simulator.generator.DataGenerator;
import com.systar.simulator.generator.FixedGenerator;
import com.systar.simulator.generator.ProfileGenerator;
import com.systar.simulator.generator.RandomGenerator;
import com.systar.simulator.generator.RampGenerator;
import com.systar.simulator.generator.SineGenerator;
import com.systar.simulator.generator.StepGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.DataPointAddress;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.OpcUaAddress;
import com.systar.simulator.model.OpcUaEndpoint;
import com.systar.simulator.model.ProtocolEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

/**
 * Parses YAML device profile definitions into {@link SimulatedDevice} instances.
 * <p>
 * The expected YAML structure is:
 * <pre>
 * devices:
 *   - id: "dev-1"
 *     name: "My Device"
 *     protocol: MODBUS_TCP
 *     endpoint:
 *       host: "0.0.0.0"
 *       port: 502
 *       unitId: 1
 *     dataPoints:
 *       - id: "temp"
 *         name: "Temperature"
 *         address:
 *           registerType: holding
 *           offset: 0
 *           dataType: float
 *         generator:
 *           type: sine
 *           amplitude: 10
 *           offset: 20
 *           periodSeconds: 60
 * </pre>
 */
public class ProfileParser {

    private static final Logger log = LoggerFactory.getLogger(ProfileParser.class);

    private final ObjectMapper objectMapper;

    public ProfileParser() {
        this.objectMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Parse a single YAML input stream into a list of simulated devices.
     *
     * @param yamlStream the YAML input
     * @return parsed devices
     * @throws IllegalArgumentException if the YAML is malformed
     */
    public List<SimulatedDevice> parse(InputStream yamlStream) {
        Map<String, Object> root = readYaml(yamlStream);
        return parseDevices(root);
    }

    /**
     * Parse multiple YAML streams and merge the results.
     *
     * @param streams collection of YAML input streams
     * @return all parsed devices merged into one list
     */
    public List<SimulatedDevice> parseAll(Collection<InputStream> streams) {
        List<SimulatedDevice> allDevices = new ArrayList<>();
        for (InputStream stream : streams) {
            allDevices.addAll(parse(stream));
        }
        return allDevices;
    }

    private Map<String, Object> readYaml(InputStream yamlStream) {
        try {
            return objectMapper.readValue(yamlStream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse YAML profile", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<SimulatedDevice> parseDevices(Map<String, Object> root) {
        Object devicesRaw = root.get("devices");
        if (!(devicesRaw instanceof List<?> deviceList)) {
            throw new IllegalArgumentException(
                    "YAML must contain a top-level 'devices' list");
        }
        List<SimulatedDevice> devices = new ArrayList<>(deviceList.size());
        for (Object item : deviceList) {
            if (!(item instanceof Map<?, ?> deviceMap)) {
                throw new IllegalArgumentException(
                        "Each device entry must be a mapping");
            }
            devices.add(parseDevice((Map<String, Object>) deviceMap));
        }
        return devices;
    }

    private SimulatedDevice parseDevice(Map<String, Object> deviceMap) {
        String id       = requireString(deviceMap, "id");
        String name     = requireString(deviceMap, "name");
        String protocol = requireString(deviceMap, "protocol");

        ProtocolType protocolType = parseProtocolType(protocol);
        boolean enabled = getBool(deviceMap, "enabled", true);

        SimulatedDevice device = new SimulatedDevice();
        device.setId(id);
        device.setName(name);
        device.setProtocol(protocolType);
        device.setEnabled(enabled);

        Object endpointRaw = deviceMap.get("endpoint");
        if (endpointRaw == null) {
            throw new IllegalArgumentException(
                    "Device '" + id + "' is missing required 'endpoint'");
        }
        if (!(endpointRaw instanceof Map<?, ?> endpointMap)) {
            throw new IllegalArgumentException(
                    "Device '" + id + "' endpoint must be a mapping");
        }
        device.setEndpoint(parseEndpoint(protocolType, (Map<String, Object>) endpointMap));

        Object dpsRaw = deviceMap.get("dataPoints");
        if (dpsRaw instanceof List<?> dpList) {
            List<DataPoint> dataPoints = new ArrayList<>(dpList.size());
            for (Object dpItem : dpList) {
                if (!(dpItem instanceof Map<?, ?> dpMap)) {
                    throw new IllegalArgumentException(
                            "Each dataPoint in device '" + id + "' must be a mapping");
                }
                dataPoints.add(parseDataPoint((Map<String, Object>) dpMap));
            }
            device.setDataPoints(dataPoints);
        }

        log.debug("Parsed device: id={}, name={}, protocol={}, dataPoints={}",
                  id, name, protocolType, device.getDataPoints().size());
        return device;
    }

    private ProtocolEndpoint parseEndpoint(ProtocolType protocol, Map<String, Object> endpointMap) {
        return switch (protocol) {
            case MODBUS_TCP -> parseModbusEndpoint(endpointMap);
            case OPC_UA -> parseOpcUaEndpoint(endpointMap);
        };
    }

    private ModbusTcpEndpoint parseModbusEndpoint(Map<String, Object> endpointMap) {
        ModbusTcpEndpoint endpoint = new ModbusTcpEndpoint();
        String host = getString(endpointMap, "host", "0.0.0.0");
        int port    = getInt(endpointMap, "port", 502);
        int unitId  = getInt(endpointMap, "unitId", 1);

        endpoint.setHost(host);
        endpoint.setPort(port);
        endpoint.setUnitId(unitId);
        return endpoint;
    }

    private OpcUaEndpoint parseOpcUaEndpoint(Map<String, Object> endpointMap) {
        OpcUaEndpoint endpoint = new OpcUaEndpoint();
        String host            = getString(endpointMap, "host", "0.0.0.0");
        int port               = getInt(endpointMap, "port", 4840);
        String securityPolicy  = getString(endpointMap, "securityPolicy", "None");
        String serverName      = getString(endpointMap, "serverName", "systar-simulator");

        endpoint.setHost(host);
        endpoint.setPort(port);
        endpoint.setSecurityPolicy(securityPolicy);
        endpoint.setServerName(serverName);
        return endpoint;
    }

    private DataPoint parseDataPoint(Map<String, Object> dpMap) {
        String id   = requireString(dpMap, "id");
        String name = requireString(dpMap, "name");

        DataPoint dp = new DataPoint();
        dp.setId(id);
        dp.setName(name);

        Object addrRaw = dpMap.get("address");
        if (addrRaw == null) {
            throw new IllegalArgumentException(
                    "DataPoint '" + id + "' is missing required 'address'");
        }
        if (!(addrRaw instanceof Map<?, ?> addrMap)) {
            throw new IllegalArgumentException(
                    "DataPoint '" + id + "' address must be a mapping");
        }

        // We need the protocol type to parse addresses, but it is not directly available
        // in the dataPoint map. Use registerType presence as a heuristic for Modbus.
        // This is acceptable because ProfileValidator will verify consistency.
        dp.setAddress(parseAddress((Map<String, Object>) addrMap));

        Object genRaw = dpMap.get("generator");
        if (genRaw == null) {
            throw new IllegalArgumentException(
                    "DataPoint '" + id + "' is missing required 'generator'");
        }
        if (!(genRaw instanceof Map<?, ?> genMap)) {
            throw new IllegalArgumentException(
                    "DataPoint '" + id + "' generator must be a mapping");
        }
        dp.setGenerator(parseGenerator((Map<String, Object>) genMap));

        return dp;
    }

    private DataPointAddress parseAddress(Map<String, Object> addrMap) {
        // Heuristic: if registerType exists, it is a Modbus address; otherwise OPC-UA.
        if (addrMap.containsKey("registerType")) {
            return parseModbusAddress(addrMap);
        }
        return parseOpcUaAddress(addrMap);
    }

    private ModbusAddress parseModbusAddress(Map<String, Object> addrMap) {
        String registerType = requireString(addrMap, "registerType");
        int offset          = requireInt(addrMap, "offset");
        String dataType     = getString(addrMap, "dataType", "int");
        return new ModbusAddress(registerType, offset, dataType);
    }

    private OpcUaAddress parseOpcUaAddress(Map<String, Object> addrMap) {
        int     namespaceIndex = getInt(addrMap, "namespaceIndex", 2);
        String  identifier     = requireString(addrMap, "identifier");
        boolean integerId     = getBool(addrMap, "integerId", false);
        return new OpcUaAddress(namespaceIndex, identifier, integerId);
    }

    private DataGenerator parseGenerator(Map<String, Object> genMap) {
        String type = requireString(genMap, "type");
        return switch (type) {
            case "random"     -> parseRandomGenerator(genMap);
            case "fixed"      -> parseFixedGenerator(genMap);
            case "sine"       -> parseSineGenerator(genMap);
            case "step"       -> parseStepGenerator(genMap);
            case "ramp"       -> parseRampGenerator(genMap);
            case "profile"    -> parseProfileGenerator(genMap);
            case "correlated" -> parseCorrelatedGenerator(genMap);
            default -> throw new IllegalArgumentException(
                    "Unknown generator type: '" + type + "'");
        };
    }

    private DataGenerator parseRandomGenerator(Map<String, Object> genMap) {
        double min = getDouble(genMap, "min", 0.0);
        double max = getDouble(genMap, "max", 100.0);
        return new RandomGenerator(min, max);
    }

    private DataGenerator parseFixedGenerator(Map<String, Object> genMap) {
        Object value = genMap.getOrDefault("value", 0.0);
        return new FixedGenerator(value);
    }

    private DataGenerator parseSineGenerator(Map<String, Object> genMap) {
        double amplitude     = getDouble(genMap, "amplitude", 1.0);
        double offset        = getDouble(genMap, "offset", 0.0);
        double periodSeconds = getDouble(genMap, "periodSeconds", 60.0);
        double noiseStdDev   = getDouble(genMap, "noiseStdDev", 0.0);

        SineGenerator gen = new SineGenerator(amplitude, offset, periodSeconds);
        gen.setNoiseStdDev(noiseStdDev);
        return gen;
    }

    @SuppressWarnings("unchecked")
    private DataGenerator parseStepGenerator(Map<String, Object> genMap) {
        StepGenerator gen = new StepGenerator();
        gen.setValues(getList(genMap, "values", List.of(0.0)));
        gen.setIntervalSeconds(getDouble(genMap, "intervalSeconds", 1.0));
        return gen;
    }

    private DataGenerator parseRampGenerator(Map<String, Object> genMap) {
        double  start           = getDouble(genMap, "start", 0.0);
        double  end             = getDouble(genMap, "end", 100.0);
        double  durationSeconds = getDouble(genMap, "durationSeconds", 60.0);
        boolean loop            = getBool(genMap, "loop", true);

        RampGenerator gen = new RampGenerator();
        gen.setStart(start);
        gen.setEnd(end);
        gen.setDurationSeconds(durationSeconds);
        gen.setLoop(loop);
        return gen;
    }

    @SuppressWarnings("unchecked")
    private DataGenerator parseProfileGenerator(Map<String, Object> genMap) {
        ProfileGenerator gen = new ProfileGenerator();
        gen.setNoiseStdDev(getDouble(genMap, "noiseStdDev", 0.0));
        gen.setInterpolation(getString(genMap, "interpolation", "linear"));

        Object segmentsRaw = genMap.get("segments");
        if (segmentsRaw instanceof List<?> segmentsList) {
            gen.setSegmentsFromMaps((List<Map<String, Object>>) segmentsList);
        }
        return gen;
    }

    @SuppressWarnings("unchecked")
    private DataGenerator parseCorrelatedGenerator(Map<String, Object> genMap) {
        String expression = requireString(genMap, "expression");

        CorrelatedGenerator gen = new CorrelatedGenerator();
        gen.setExpression(expression);

        Object refsRaw = genMap.get("references");
        if (refsRaw instanceof Map<?, ?> refsMap) {
            Map<String, String> refs = new java.util.HashMap<>();
            for (Map.Entry<?, ?> entry : refsMap.entrySet()) {
                refs.put(String.valueOf(entry.getKey()),
                         String.valueOf(entry.getValue()));
            }
            gen.setReferences(refs);
        }
        return gen;
    }

    // --- Helper methods ---

    private ProtocolType parseProtocolType(String value) {
        try {
            return ProtocolType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown protocol type: '" + value
                    + "'. Valid values: MODBUS_TCP, OPC_UA");
        }
    }

    private static String requireString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required field '" + key + "'");
        }
        return String.valueOf(value);
    }

    private static int requireInt(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Missing required field '" + key + "'");
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a number, got: " + value.getClass().getSimpleName());
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a number, got: " + value.getClass().getSimpleName());
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a number, got: " + value.getClass().getSimpleName());
    }

    private static boolean getBool(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a boolean, got: " + value.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getList(Map<String, Object> map, String key, List<Object> defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException(
                "Field '" + key + "' must be a list, got: " + value.getClass().getSimpleName());
    }
}
