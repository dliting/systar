package com.systar.simulator.fleet;

import com.systar.simulator.generator.CorrelatedGenerator;
import com.systar.simulator.model.DataPoint;
import com.systar.simulator.model.SimulatedDevice;

import java.util.*;

/**
 * Computes topological ordering of data points so that correlated generators
 * can safely reference already-computed peer values.
 * <p>
 * Uses depth-first search with cycle detection. Independent data points retain
 * their original declaration order within the sorted result.
 */
public class DependencyResolver {

    /**
     * Resolve data points in dependency order for the given device.
     *
     * @param device the device whose data points should be ordered
     * @return a topologically-sorted list of data points
     * @throws IllegalStateException if a circular dependency is detected
     */
    public List<DataPoint> resolveOrder(SimulatedDevice device) {
        List<DataPoint> points = device.getDataPoints();

        Map<String, DataPoint> byId = new LinkedHashMap<>();
        for (DataPoint dp : points) {
            byId.put(dp.getId(), dp);
        }

        Map<String, Set<String>> deps = new HashMap<>();
        for (DataPoint dp : points) {
            Set<String> dep = new HashSet<>();
            if (dp.getGenerator() instanceof CorrelatedGenerator cg) {
                dep.addAll(cg.getReferences().values());
            }
            deps.put(dp.getId(), dep);
        }

        List<DataPoint> sorted   = new ArrayList<>();
        Set<String> visited      = new HashSet<>();
        Set<String> visiting     = new HashSet<>();

        for (DataPoint dp : points) {
            visit(dp.getId(), byId, deps, sorted, visited, visiting);
        }
        return sorted;
    }

    private void visit(String id, Map<String, DataPoint> byId,
                       Map<String, Set<String>> deps,
                       List<DataPoint> sorted,
                       Set<String> visited, Set<String> visiting) {
        if (visited.contains(id)) {
            return;
        }
        if (visiting.contains(id)) {
            throw new IllegalStateException("Circular dependency detected at: " + id);
        }
        visiting.add(id);
        for (String depId : deps.getOrDefault(id, Set.of())) {
            visit(depId, byId, deps, sorted, visited, visiting);
        }
        visiting.remove(id);
        visited.add(id);
        sorted.add(byId.get(id));
    }
}
