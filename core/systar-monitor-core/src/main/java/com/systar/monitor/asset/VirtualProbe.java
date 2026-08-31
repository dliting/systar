package com.systar.monitor.asset;

import com.systar.monitor.asset.type.VirtualProbeType;
import com.systar.monitor.expression.CompiledExpression;
import com.systar.monitor.expression.ExpressionEvaluatorHolder;
import com.systar.monitor.expression.ProbeRef;
import com.systar.monitor.result.IMonitorResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A probe whose value is derived from an expression referencing other probes.
 * <p>
 * VirtualProbes do not sample physical data sources. Instead, they compute
 * their value from a SpEL expression that can reference other probes via
 * {@code #probe[id].value}. When a dependency probe updates, the
 * {@link VirtualProbeEngine} triggers recalculation.
 * <p>
 * Example expression: {@code #probe[101].value / #probe[102].value * 100}
 */
@Slf4j
public class VirtualProbe extends Probe {

    /** IDs of probes whose values this expression depends on. */
    private List<Integer> dependsOn = Collections.emptyList();

    /** Compiled form of the expression for efficient repeated evaluation. */
    private CompiledExpression compiledExpression;

    /** The asset store used to resolve dependency probe values. */
    private AssetStore assetStore;

    // ======================== Asset ========================

    @Override
    public AssetKind getKind() {
        return AssetKind.PROBE;
    }

    @Override
    public <R> R accept(AssetVisitor<R> visitor) {
        return visitor.visit(this);
    }

    // ======================== detection ========================

    /**
     * Computes the derived value by evaluating the expression against
     * the current values of dependency probes.
     * <p>
     * If any dependency probe has a null value, the result is not computed
     * and an error is set instead.
     */
    @Override
    public void detect(IMonitorResult result) throws Exception {
        if (compiledExpression == null) {
            log.warn("VirtualProbe detect skipped: no compiled expression, id={}", getId());
            result.setError("Virtual probe has no compiled expression");
            return;
        }

        Map<String, Object> variables = buildSpelVariables();
        if (variables == null) {
            log.warn("VirtualProbe detect skipped: dependency value not available, id={}, dependsOn={}",
                    getId(), dependsOn);
            result.setError("Virtual probe dependency value not available");
            return;
        }

        Object computed = compiledExpression.evaluate(variables);
        this.setValue(computed);
        result.setValue(computed);
        result.setSampleTime(System.currentTimeMillis());
        log.debug("VirtualProbe computed: id={}, value={}, dependsOn={}", getId(), computed, dependsOn);
    }

    /**
     * Builds the SpEL variable map for expression evaluation.
     * <p>
     * The {@code #probe} variable maps probeId → ProbeRef(value),
     * so SpEL expressions like {@code #probe[101].value} resolve naturally.
     *
     * @return variable map, or null if any dependency is unavailable
     */
    private Map<String, Object> buildSpelVariables() {
        if (assetStore == null || dependsOn.isEmpty()) {
            return null;
        }

        Map<Integer, ProbeRef> probeRefs = new HashMap<>();
        for (int depId : dependsOn) {
            Asset<?> asset = assetStore.findAsset(depId);
            if (!(asset instanceof Monitor<?> monitor)) {
                return null;
            }
            Object val = monitor.getValue();
            if (val == null) {
                return null;
            }
            probeRefs.put(depId, new ProbeRef(val));
        }
        return Map.of("probe", probeRefs);
    }

    // ======================== expression compilation ========================

    /**
     * Returns the type cast to {@link VirtualProbeType}, or null if not set.
     */
    private VirtualProbeType getVirtualProbeType() {
        return getType() instanceof VirtualProbeType vpt ? vpt : null;
    }

    /**
     * Compiles the expression from the type definition.
     * Should be called after {@link #init} and {@link #setAssetStore}.
     */
    public void compileExpression() {
        VirtualProbeType t = getVirtualProbeType();
        if (t == null || t.getExpression() == null || t.getExpression().isBlank()) {
            return;
        }
        try {
            this.compiledExpression = ExpressionEvaluatorHolder.getInstance()
                    .compile(t.getExpression());
        } catch (Exception e) {
            log.warn("VirtualProbe expression compilation failed: id={}, expr='{}', error={}",
                    getId(), t.getExpression(), e.getMessage());
            setMetadata("expressionError", e.getMessage());
            this.compiledExpression = null;
        }
    }

    /**
     * Parses the dependsOn string from the type definition into a list of probe IDs.
     */
    public void parseDependsOn() {
        VirtualProbeType t = getVirtualProbeType();
        if (t == null || t.getDependsOn() == null || t.getDependsOn().isBlank()) {
            this.dependsOn = Collections.emptyList();
            return;
        }
        this.dependsOn = java.util.Arrays.stream(t.getDependsOn().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        throw new AssetException("Invalid probe ID in dependsOn: '" + s + "'");
                    }
                })
                .distinct()
                .toList();
    }

    // ======================== accessors ========================

    public List<Integer> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<Integer> dependsOn) {
        this.dependsOn = dependsOn != null ? dependsOn : Collections.emptyList();
    }

    public CompiledExpression getCompiledExpression() {
        return compiledExpression;
    }

    public AssetStore getAssetStore() {
        return assetStore;
    }

    public void setAssetStore(AssetStore assetStore) {
        this.assetStore = assetStore;
    }
}
