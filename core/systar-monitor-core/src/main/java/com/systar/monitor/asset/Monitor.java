package com.systar.monitor.asset;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.type.MonitorType;
import com.systar.monitor.expression.CompiledExpression;
import com.systar.monitor.expression.ExpressionEvaluatorHolder;
import com.systar.monitor.result.IMonitorResult;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base class for all monitors (probes and controls).
 * <p>
 * A monitor represents a single data point that can be sampled (detected),
 * optionally transformed via an expression, and evaluated for warning conditions.
 * Monitors belong to a {@link MonitorService} which manages their lifecycle
 * and connection pool.
 *
 * @param <T> the concrete {@link MonitorType} of this monitor
 */
public abstract class Monitor<T extends MonitorType> extends Asset<T> {

    // ---- core fields ----
    private volatile Object value;
    private final AtomicLong lastDetectTimeMs = new AtomicLong(0);
    private final AtomicLong lastSavingTimeMs = new AtomicLong(0);
    private volatile MonitorMode mode = MonitorMode.ACTIVE;
    /** True while a manual detect is executing; prevents duplicate submissions. */
    private final AtomicBoolean detecting = new AtomicBoolean(false);

    // ---- intervals (override from type, or use defaults) ----
    private TimeSpan detectInterval;
    private TimeSpan savingInterval;

    // ---- compiled expressions ----
    private CompiledExpression warnExpr;
    private CompiledExpression transformExpr;

    // ---- runtime error description ----
    private volatile String runtimeDesc;

    /** Framework-level detect timeout in ms. Defaults to 30 s. */
    public static final long DEFAULT_DETECT_TIMEOUT_MS = 30_000L;
    private volatile long detectTimeoutMs = DEFAULT_DETECT_TIMEOUT_MS;

    // ---- the service this monitor belongs to ----
    private MonitorService source;

    // ======================== lifecycle ========================

    @Override
    public void init(T type, int id, String name) {
        super.init(type, id, name);
        if (type != null) {
            this.detectInterval = type.getDetectInterval();
            this.savingInterval = type.getSavingInterval();
            compileExpressions(type);
        }
    }

    /**
     * Compiles warning condition and transform expressions from the type definition
     * via the configured {@link com.systar.monitor.expression.ExpressionEvaluator}.
     */
    private void compileExpressions(T type) {
        String warnCondition = type.getWarnCondition();
        if (warnCondition != null && !warnCondition.isBlank()) {
            try {
                this.warnExpr = ExpressionEvaluatorHolder.getInstance().compile(warnCondition);
            } catch (Exception e) {
                setMetadata("warnExprError", e.toString());
                this.warnExpr = null;
            }
        }

        String transform = type.getTransform();
        if (transform != null && !transform.isBlank()) {
            try {
                this.transformExpr = ExpressionEvaluatorHolder.getInstance().compile(transform);
            } catch (Exception e) {
                setMetadata("transformExprError", e.toString());
                this.transformExpr = null;
            }
        }
    }

    // ======================== abstract detection ========================

    /**
     * Performs the actual data detection/sampling.
     * <p>
     * Subclasses implement the concrete mechanism for reading a value
     * (e.g., querying a register, reading a sensor).
     *
     * @param result the result carrier to populate
     * @throws Exception if detection fails
     */
    public abstract void detect(IMonitorResult result) throws Exception;

    // ======================== timing checks ========================

    /**
     * Returns true if enough time has elapsed since the last detection.
     *
     * @param now current time in milliseconds
     */
    public boolean shouldDetect(long now) {
        TimeSpan interval = detectInterval != null ? detectInterval : MonitorType.DEFAULT_DETECT_INTERVAL;
        return (now - lastDetectTimeMs.get()) >= interval.toMillis();
    }

    /**
     * Returns true if enough time has elapsed since the last save (throttle check).
     *
     * @param now current time in milliseconds
     */
    public boolean shouldSave(long now) {
        TimeSpan interval = savingInterval != null ? savingInterval : MonitorType.DEFAULT_SAVING_INTERVAL;
        return (now - lastSavingTimeMs.get()) >= interval.toMillis();
    }

    // ======================== expression evaluation ========================

    /**
     * Applies the value transformation expression to the raw value.
     * <p>
     * The raw value is available as {@code #value} in the expression context.
     * If no transform expression is configured, the raw value is returned as-is.
     *
     * @param rawValue the raw sampled value
     * @return the transformed value, or rawValue if no transform is configured
     */
    public Object applyTransform(Object rawValue) {
        if (transformExpr == null) {
            return rawValue;
        }
        try {
            return transformExpr.evaluate(Map.of("value", rawValue));
        } catch (Exception e) {
            setMetadata("transformError", e.toString());
            return rawValue;
        }
    }

    /**
     * Evaluates the warning condition against the given value.
     * <p>
     * The value is available as {@code #value} in the expression context.
     * If no warning expression is configured, returns false.
     *
     * @param value the value to evaluate
     * @return true if the warning condition is triggered
     */
    public boolean evaluateWarnCondition(Object value) {
        if (warnExpr == null) {
            return false;
        }
        try {
            Object result = warnExpr.evaluate(Map.of("value", value));
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            setMetadata("warnError", e.toString());
            return false;
        }
    }

    // ======================== Asset overrides ========================

    @Override
    public boolean isCompound() {
        return false;
    }

    @Override
    public boolean isMonitor() {
        return true;
    }

    // ======================== getters / setters ========================

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public long getLastDetectTimeMs() {
        return lastDetectTimeMs.get();
    }

    public void setLastDetectTimeMs(long timeMillis) {
        lastDetectTimeMs.set(timeMillis);
    }

    public long getLastSavingTimeMs() {
        return lastSavingTimeMs.get();
    }

    public void setLastSavingTimeMs(long timeMillis) {
        lastSavingTimeMs.set(timeMillis);
    }

    public MonitorMode getMode() {
        return mode;
    }

    public void setMode(MonitorMode mode) {
        this.mode = mode;
    }

    public boolean isDetecting() {
        return detecting.get();
    }

    /** Clears the detecting flag. Only for use by DetectTask completion (finally block). */
    public void setDetecting(boolean detecting) {
        this.detecting.set(detecting);
    }

    /**
     * Atomically sets detecting from {@code false} to {@code true}.
     * Use this instead of {@code setDetecting(true)} to prevent concurrent
     * manual-detect submissions from both passing the check.
     *
     * @return true if the flag was set (was false), false if it was already true
     */
    public boolean trySetDetecting() {
        return this.detecting.compareAndSet(false, true);
    }

    public TimeSpan getDetectInterval() {
        return detectInterval;
    }

    public void setDetectInterval(TimeSpan detectInterval) {
        if (detectInterval != null && detectInterval.compareTo(MonitorType.MIN_INTERVAL) < 0) {
            detectInterval = MonitorType.MIN_INTERVAL;
        }
        this.detectInterval = detectInterval;
    }

    public TimeSpan getSavingInterval() {
        return savingInterval;
    }

    public void setSavingInterval(TimeSpan savingInterval) {
        if (savingInterval != null && savingInterval.compareTo(MonitorType.MIN_INTERVAL) < 0) {
            savingInterval = MonitorType.MIN_INTERVAL;
        }
        this.savingInterval = savingInterval;
    }

    public CompiledExpression getWarnExpr() {
        return warnExpr;
    }

    public CompiledExpression getTransformExpr() {
        return transformExpr;
    }

    public MonitorService getSource() {
        return source;
    }

    /**
     * Sets the parent service for this monitor.
     * Handles bidirectional association: adds to the new service and
     * removes from the old service.
     *
     * @param newSource the new parent service
     * @return the old source service, or null
     */
    public String getRuntimeDesc() {
        return runtimeDesc;
    }

    public void setRuntimeDesc(String runtimeDesc) {
        this.runtimeDesc = runtimeDesc;
    }

    public long getDetectTimeoutMs() {
        return detectTimeoutMs;
    }

    public void setDetectTimeoutMs(long detectTimeoutMs) {
        if (detectTimeoutMs < MonitorType.MIN_INTERVAL.toMillis()) {
            detectTimeoutMs = MonitorType.MIN_INTERVAL.toMillis();
        }
        this.detectTimeoutMs = detectTimeoutMs;
    }

    public MonitorService setSource(MonitorService newSource) {
        MonitorService oldSource = this.source;
        if (oldSource == newSource) {
            return oldSource;
        }
        if (newSource != null) {
            newSource.addMonitor(this);
            this.mode = newSource.getMode();
        }
        this.source = newSource;
        if (oldSource != null) {
            oldSource.removeMonitor(this);
        }
        return oldSource;
    }
}
