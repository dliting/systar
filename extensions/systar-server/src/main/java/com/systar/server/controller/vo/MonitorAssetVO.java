package com.systar.server.controller.vo;

/**
 * VO for Monitor assets (Probe, Control).
 * Extends {@link AssetVO} with Monitor-specific runtime and configuration fields.
 * Jackson serializes all fields (base + monitor) into a flat JSON object,
 * so the frontend sees the same shape as before.
 */
public class MonitorAssetVO extends AssetVO {

    private Object  value;
    private Long    lastDetectTime;
    /** Monitor mode: ACTIVE or PASSIVE. */
    private String  mode;
    /** Error description when state is ERROR. Null otherwise. */
    private String  runtimeDesc;
    /** True while a manual detect is in progress. */
    private Boolean detecting;
    /** Engineering unit (e.g. "℃", "%RH", "kWh"). */
    private String  unit;
    /** Min value for Probe/Control. */
    private Float   minValue;
    /** Max value for Probe/Control. */
    private Float   maxValue;
    /** Monitor primary data type from type definition. */
    private String  dataType;
    /** Monitor value presentation type from type definition. */
    private String  viewType;
    /** Framework-level detect timeout in ms. */
    private Long    detectTimeoutMs;
    /** True if this probe derives its value from a SpEL expression. */
    private Boolean isVirtual;
    /** SpEL expression for virtual probes (e.g. "#probe[101].value / #probe[102].value * 100"). */
    private String  expression;
    /** Comma-separated probe IDs that this virtual probe depends on. */
    private String  dependsOn;

    public Object  getValue()                    { return value; }
    public void    setValue(Object value)        { this.value = value; }
    public Long    getLastDetectTime()           { return lastDetectTime; }
    public void    setLastDetectTime(Long lastDetectTime) { this.lastDetectTime = lastDetectTime; }
    public String  getMode()                     { return mode; }
    public void    setMode(String mode)          { this.mode = mode; }
    public String  getRuntimeDesc()              { return runtimeDesc; }
    public void    setRuntimeDesc(String runtimeDesc) { this.runtimeDesc = runtimeDesc; }
    public Boolean getDetecting()                { return detecting; }
    public void    setDetecting(Boolean detecting) { this.detecting = detecting; }
    public String  getUnit()                     { return unit; }
    public void    setUnit(String unit)          { this.unit = unit; }
    public Float   getMinValue()                 { return minValue; }
    public void    setMinValue(Float minValue)   { this.minValue = minValue; }
    public Float   getMaxValue()                 { return maxValue; }
    public void    setMaxValue(Float maxValue)   { this.maxValue = maxValue; }
    public String  getDataType()                 { return dataType; }
    public void    setDataType(String dataType)  { this.dataType = dataType; }
    public String  getViewType()                 { return viewType; }
    public void    setViewType(String viewType)  { this.viewType = viewType; }
    public Long    getDetectTimeoutMs()          { return detectTimeoutMs; }
    public void    setDetectTimeoutMs(Long detectTimeoutMs) { this.detectTimeoutMs = detectTimeoutMs; }
    public Boolean getIsVirtual()               { return isVirtual; }
    public void    setIsVirtual(Boolean isVirtual) { this.isVirtual = isVirtual; }
    public String  getExpression()              { return expression; }
    public void    setExpression(String expression) { this.expression = expression; }
    public String  getDependsOn()               { return dependsOn; }
    public void    setDependsOn(String dependsOn) { this.dependsOn = dependsOn; }
}
