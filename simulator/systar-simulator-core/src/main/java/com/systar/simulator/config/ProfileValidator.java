package com.systar.simulator.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.systar.simulator.generator.CorrelatedGenerator;
import com.systar.simulator.generator.DataGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.ModbusAddress;
import com.systar.simulator.model.ModbusTcpEndpoint;
import com.systar.simulator.model.OpcUaEndpoint;
import com.systar.simulator.model.ProtocolEndpoint;
import com.systar.simulator.model.ProtocolType;
import com.systar.simulator.model.SimulatedDevice;

/**
 * Validates a list of {@link SimulatedDevice} instances parsed from YAML profiles.
 * <p>
 * Throws {@link ValidationException} on the first detected violation.
 */
public class ProfileValidator {

    private static final Logger log = LoggerFactory.getLogger(ProfileValidator.class);

    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    /**
     * Validate the entire list of devices.
     *
     * @param devices devices to validate
     * @throws ValidationException if any rule is violated
     */
    public void validate(List<SimulatedDevice> devices) {
        log.info("Validating {} device(s)...", devices.size());

        // Phase 1: per-device field completeness
        for (SimulatedDevice device : devices) {
            validateRequiredFields(device);
        }

        // Phase 2: cross-device uniqueness and port constraints
        validateDeviceUniqueness(devices);
        validatePortRanges(devices);
        validateModbusPortUnitId(devices);

        // Phase 3: per-device data point and Modbus overlap checks
        for (SimulatedDevice device : devices) {
            validateDataPointFields(device);
            validateDataPointIdUniqueness(device);
            validateModbusOffsetOverlap(device);
        }

        // Phase 4: correlated reference resolution and cycle detection
        for (SimulatedDevice device : devices) {
            validateCorrelatedReferences(device);
        }

        log.info("All {} device(s) passed validation.", devices.size());
    }

    // --- Phase 1: Required field checks ---

    private void validateRequiredFields(SimulatedDevice device) {
        if (device.getId() == null || device.getId().isBlank()) {
            throw new ValidationException("Device is missing required field 'id'");
        }
        if (device.getName() == null || device.getName().isBlank()) {
            throw new ValidationException(
                    "Device '" + device.getId() + "' is missing required field 'name'");
        }
        if (device.getProtocol() == null) {
            throw new ValidationException(
                    "Device '" + device.getId() + "' is missing required field 'protocol'");
        }
        if (device.getEndpoint() == null) {
            throw new ValidationException(
                    "Device '" + device.getId() + "' is missing required 'endpoint'");
        }
    }

    private void validateDataPointFields(SimulatedDevice device) {
        for (DataPoint dp : device.getDataPoints()) {
            if (dp.getId() == null || dp.getId().isBlank()) {
                throw new ValidationException(
                        "Device '" + device.getId() + "' has a dataPoint missing required field 'id'");
            }
            if (dp.getName() == null || dp.getName().isBlank()) {
                throw new ValidationException(
                        "Device '" + device.getId() + "' dataPoint '"
                        + dp.getId() + "' is missing required field 'name'");
            }
            if (dp.getAddress() == null) {
                throw new ValidationException(
                        "Device '" + device.getId() + "' dataPoint '"
                        + dp.getId() + "' is missing required 'address'");
            }
            if (dp.getGenerator() == null) {
                throw new ValidationException(
                        "Device '" + device.getId() + "' dataPoint '"
                        + dp.getId() + "' is missing required 'generator'");
            }
        }
    }

    // --- Phase 2: Cross-device checks ---

    private void validateDeviceUniqueness(List<SimulatedDevice> devices) {
        Set<String> seenIds = new HashSet<>();
        for (SimulatedDevice device : devices) {
            String id = device.getId();
            if (!seenIds.add(id)) {
                throw new ValidationException("Duplicate device id: '" + id + "'");
            }
        }
    }

    private void validatePortRanges(List<SimulatedDevice> devices) {
        for (SimulatedDevice device : devices) {
            ProtocolEndpoint endpoint = device.getEndpoint();
            if (endpoint instanceof ModbusTcpEndpoint modbus) {
                validatePort(modbus.getPort(), device.getId());
            } else if (endpoint instanceof OpcUaEndpoint opcua) {
                validatePort(opcua.getPort(), device.getId());
            }
        }
    }

    private void validatePort(int port, String deviceId) {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new ValidationException(
                    "Device '" + deviceId + "' has invalid port " + port
                    + ". Must be between " + MIN_PORT + " and " + MAX_PORT);
        }
    }

    /**
     * Multiple Modbus devices on the same port must have different unitIds.
     */
    private void validateModbusPortUnitId(List<SimulatedDevice> devices) {
        // Map: port -> set of unitIds
        Map<Integer, Set<Integer>> portUnitIds = new HashMap<>();
        for (SimulatedDevice device : devices) {
            if (device.getProtocol() != ProtocolType.MODBUS_TCP) {
                continue;
            }
            ProtocolEndpoint ep = device.getEndpoint();
            if (!(ep instanceof ModbusTcpEndpoint modbusEp)) {
                continue;
            }
            int port = modbusEp.getPort();
            int unitId = modbusEp.getUnitId();

            Set<Integer> unitIds = portUnitIds.computeIfAbsent(port, k -> new HashSet<>());
            if (!unitIds.add(unitId)) {
                throw new ValidationException(
                        "Modbus devices on port " + port
                        + " have duplicate unitId " + unitId
                        + " (conflicting device: '" + device.getId() + "')");
            }
        }
    }

    // --- Phase 3: Per-device data point checks ---

    private void validateDataPointIdUniqueness(SimulatedDevice device) {
        Set<String> seenIds = new HashSet<>();
        for (DataPoint dp : device.getDataPoints()) {
            if (!seenIds.add(dp.getId())) {
                throw new ValidationException(
                        "Device '" + device.getId()
                        + "' has duplicate dataPoint id: '" + dp.getId() + "'");
            }
        }
    }

    /**
     * Within a single Modbus device, holding/input register ranges must not overlap.
     * Coil and discrete ranges are checked separately, and registers do not overlap
     * with coils (they are different register spaces in Modbus).
     */
    private void validateModbusOffsetOverlap(SimulatedDevice device) {
        if (device.getProtocol() != ProtocolType.MODBUS_TCP) {
            return;
        }

        // Separate by register space
        List<ModbusAddress> holdingAndInput = new ArrayList<>();
        List<ModbusAddress> coilsAndDiscrete = new ArrayList<>();

        for (DataPoint dp : device.getDataPoints()) {
            if (dp.getAddress() instanceof ModbusAddress addr) {
                String regType = addr.getRegisterType();
                if (ModbusAddress.TYPE_HOLDING.equals(regType)
                        || ModbusAddress.TYPE_INPUT.equals(regType)) {
                    holdingAndInput.add(addr);
                } else {
                    coilsAndDiscrete.add(addr);
                }
            }
        }

        checkOverlap(holdingAndInput, device.getId(), "holding/input register");
        checkOverlap(coilsAndDiscrete, device.getId(), "coil/discrete");
    }

    private void checkOverlap(List<ModbusAddress> addresses, String deviceId, String space) {
        for (int i = 0; i < addresses.size(); i++) {
            ModbusAddress a = addresses.get(i);
            for (int j = i + 1; j < addresses.size(); j++) {
                ModbusAddress b = addresses.get(j);
                int aEnd = a.getOffset() + a.registerCount();
                int bEnd = b.getOffset() + b.registerCount();
                if (a.getOffset() < bEnd && b.getOffset() < aEnd) {
                    throw new ValidationException(
                            "Device '" + deviceId + "' has overlapping " + space
                            + " ranges: offset " + a.getOffset()
                            + " (count " + a.registerCount() + ") overlaps offset "
                            + b.getOffset() + " (count " + b.registerCount() + ")");
                }
            }
        }
    }

    // --- Phase 4: Correlated reference validation ---

    private void validateCorrelatedReferences(SimulatedDevice device) {
        Set<String> allDataPointIds = new HashSet<>();
        for (DataPoint dp : device.getDataPoints()) {
            allDataPointIds.add(dp.getId());
        }

        // Build dependency graph for cycle detection
        Map<String, Set<String>> dependencyGraph = new HashMap<>();
        for (DataPoint dp : device.getDataPoints()) {
            if (dp.getGenerator() instanceof CorrelatedGenerator correlated) {
                Set<String> deps = new HashSet<>();
                for (Map.Entry<String, String> ref : correlated.getReferences().entrySet()) {
                    String targetId = ref.getValue();
                    if (!allDataPointIds.contains(targetId)) {
                        throw new ValidationException(
                                "Device '" + device.getId() + "' dataPoint '"
                                + dp.getId() + "' has unresolved correlated reference: '"
                                + targetId + "'");
                    }
                    deps.add(targetId);
                }
                dependencyGraph.put(dp.getId(), deps);
            }
        }

        // Detect cycles using topological sort (Kahn's algorithm)
        detectCycles(dependencyGraph, device.getId());
    }

    /**
     * Detect circular dependencies using Kahn's topological sort algorithm.
     * <p>
     * The graph maps each node (dp-id) to the set of nodes it depends on
     * (i.e., the targets of its correlated references). An edge from A to B
     * means A depends on B. A cycle exists if we cannot topologically sort
     * all nodes.
     */
    private void detectCycles(Map<String, Set<String>> graph, String deviceId) {
        if (graph.isEmpty()) {
            return;
        }

        // Collect all nodes and initialize in-degrees to 0
        Set<String> allNodes = new HashSet<>(graph.keySet());
        for (Set<String> deps : graph.values()) {
            allNodes.addAll(deps);
        }

        // Compute in-degrees: for each edge source -> target,
        // the target's in-degree increases by 1
        Map<String, Integer> inDegree = new HashMap<>();
        for (String node : allNodes) {
            inDegree.put(node, 0);
        }
        for (Set<String> deps : graph.values()) {
            for (String dep : deps) {
                inDegree.merge(dep, 1, Integer::sum);
            }
        }

        // Start with nodes that have zero in-degree
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int processedCount = 0;
        while (!queue.isEmpty()) {
            String node = queue.remove(0);
            processedCount++;

            // For each node that this node depends on, decrement its in-degree
            Set<String> deps = graph.getOrDefault(node, Set.of());
            for (String dep : deps) {
                int newDegree = inDegree.get(dep) - 1;
                inDegree.put(dep, newDegree);
                if (newDegree == 0) {
                    queue.add(dep);
                }
            }
        }

        if (processedCount < allNodes.size()) {
            // Find the nodes remaining in the cycle
            Set<String> cycleNodes = new HashSet<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    cycleNodes.add(entry.getKey());
                }
            }
            throw new ValidationException(
                    "Device '" + deviceId + "' has circular correlated dependencies"
                    + " involving dataPoint(s): " + cycleNodes);
        }
    }
}
