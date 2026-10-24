package com.systar.monitor.asset.type;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.AssetKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Extended asset type for monitors (probes and controls).
 * <p>
 * Adds monitoring-specific metadata such as detect/saving intervals,
 * unit of measurement, warning conditions, and value transformations.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MonitorType extends AssetType {

    /** Minimum allowed interval for detect and saving operations. */
    public static final TimeSpan MIN_INTERVAL = TimeSpan.ofSeconds(1);

    /** Default detect interval. */
    public static final TimeSpan DEFAULT_DETECT_INTERVAL = TimeSpan.ofMinutes(10);

    /** Default saving (throttle) interval. */
    public static final TimeSpan DEFAULT_SAVING_INTERVAL = TimeSpan.ofMinutes(10);

    /** Data acquisition interval. */
    private TimeSpan detectInterval = DEFAULT_DETECT_INTERVAL;

    /** Data persistence throttle interval. */
    private TimeSpan savingInterval = DEFAULT_SAVING_INTERVAL;

    /** Unit of measurement (e.g., "C", "%", "Pa"). */
    private String unit;

    /** Warning condition expressed as a SpEL expression. */
    private String warnCondition;

    /** Value transformation expressed as a SpEL expression. */
    private String transform;

    /** Data source description (e.g., register address, MQTT topic). */
    private String source;

    /** Primary data type for this monitor type (from XML DataType element). */
    private DataType dataType;

    /** UI presentation type for this monitor type (from XML ViewType element). */
    private ViewType viewType;

    public MonitorType() {
    }

    public MonitorType(String name, AssetKind kind) {
        super(name, kind);
    }
}
