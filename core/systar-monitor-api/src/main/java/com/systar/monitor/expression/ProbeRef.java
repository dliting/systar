package com.systar.monitor.expression;

/**
 * Reference to a probe's current value, used in virtual probe expressions.
 * <p>
 * When a VirtualProbe expression references {@code #probe[101].value},
 * the {@code #probe} variable is a {@code Map<Integer, ProbeRef>}.
 * SpEL resolves {@code #probe[101]} to a ProbeRef, then {@code .value}
 * to the actual numeric value.
 *
 * @param value the probe's current value
 */
public record ProbeRef(Object value) {
}
