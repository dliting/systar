package com.systar.simulator.generator;

/**
 * Strategy interface for generating simulated sensor data values.
 * <p>
 * Implementations produce a single value per call based on the provided
 * {@link GenerationContext}, which supplies elapsed time, a random source,
 * and access to peer data-point values for correlated generation.
 */
public interface DataGenerator {

    /**
     * Generate the next simulated value.
     *
     * @param context provides elapsed time, random source, and peer values
     * @return the generated value (typically {@link Number} but may be any type)
     */
    Object generate(GenerationContext context);
}
