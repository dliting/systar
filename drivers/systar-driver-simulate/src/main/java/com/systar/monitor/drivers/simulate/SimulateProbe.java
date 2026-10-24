package com.systar.monitor.drivers.simulate;

import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.result.IMonitorResult;

/**
 * Simulated probe that generates data based on a pattern defined in the
 * {@code source} field of its {@link ProbeType}.
 * <p>
 * Supported source patterns:
 * <ul>
 *   <li>{@code random:min:max} -- random value in [min, max)</li>
 *   <li>{@code sin:amplitude:offset} -- sine wave using system time</li>
 *   <li>{@code fixed:value} -- constant value</li>
 *   <li>{@code increment:start:step} -- monotonically increasing value</li>
 * </ul>
 * If the source is null or unrecognised, a random float in [0, 100) is generated.
 */
public class SimulateProbe extends Probe {

    /** Counter used by the {@code increment} mode. */
    private double incrementValue;

    public SimulateProbe() {
    }

    // ======================== detection ========================

    @Override
    public void detect(IMonitorResult result) throws Exception {
        ProbeType type = getType();
        String source = type != null ? type.getSource() : null;

        Object value;
        if (source == null || source.isBlank()) {
            value = Math.random() * 100;
        } else {
            value = parseAndGenerate(source.trim());
        }

        result.setValue(value);
        result.setSampleTime(System.currentTimeMillis());
    }

    // ======================== pattern parsing ========================

    /**
     * Parses the source pattern and generates the corresponding simulated value.
     *
     * @param source the pattern string (e.g. "random:10:30")
     * @return the generated value
     */
    private Object parseAndGenerate(String source) {
        String[] parts = source.split(":");
        String mode = parts[0].toLowerCase();

        return switch (mode) {
            case "random" -> generateRandom(parts);
            case "sin"    -> generateSin(parts);
            case "fixed"  -> generateFixed(parts);
            case "increment" -> generateIncrement(parts);
            default -> Math.random() * 100; // fallback
        };
    }

    /**
     * {@code random:min:max} -- uniform random in [min, max).
     */
    private double generateRandom(String[] parts) {
        double min = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
        double max = parts.length > 2 ? Double.parseDouble(parts[2]) : 100;
        return Math.random() * (max - min) + min;
    }

    /**
     * {@code sin:amplitude:offset} -- sine wave based on system time.
     * Period is fixed at 60 seconds for simplicity.
     */
    private double generateSin(String[] parts) {
        double amplitude = parts.length > 1 ? Double.parseDouble(parts[1]) : 1.0;
        double offset    = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0;
        double phase = (System.currentTimeMillis() % 60_000) / 60_000.0 * 2 * Math.PI;
        return amplitude * Math.sin(phase) + offset;
    }

    /**
     * {@code fixed:value} -- returns the constant value.
     */
    private double generateFixed(String[] parts) {
        return parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
    }

    /**
     * {@code increment:start:step} -- monotonically increasing by step each call.
     */
    private synchronized double generateIncrement(String[] parts) {
        double start = parts.length > 1 ? Double.parseDouble(parts[1]) : 0;
        double step  = parts.length > 2 ? Double.parseDouble(parts[2]) : 1;

        if (incrementValue == 0 && start != 0) {
            incrementValue = start;
        }
        incrementValue += step;
        return incrementValue;
    }
}
