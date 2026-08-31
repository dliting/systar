package com.systar.simulator.generator;

/**
 * Generates uniformly distributed random values within a configurable range.
 * <p>
 * The default range is {@code [0.0, 100.0)}.
 */
public class RandomGenerator implements DataGenerator {

    private static final double DEFAULT_MIN = 0.0;
    private static final double DEFAULT_MAX = 100.0;

    private double min = DEFAULT_MIN;
    private double max = DEFAULT_MAX;

    public RandomGenerator() {}

    public RandomGenerator(double min, double max) {
        this.min = min;
        this.max = max;
    }

    @Override
    public Object generate(GenerationContext context) {
        return context.random().nextDouble() * (max - min) + min;
    }

    public double getMin()              { return min; }
    public void setMin(double min)      { this.min = min; }
    public double getMax()              { return max; }
    public void setMax(double max)      { this.max = max; }
}
