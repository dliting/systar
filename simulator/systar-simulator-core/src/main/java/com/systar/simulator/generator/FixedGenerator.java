package com.systar.simulator.generator;

/**
 * Generates a constant value regardless of time or context.
 * <p>
 * Useful for static configuration parameters or baseline values.
 */
public class FixedGenerator implements DataGenerator {

    private Object value = 0.0;

    public FixedGenerator() {}

    public FixedGenerator(Object value) {
        this.value = value;
    }

    @Override
    public Object generate(GenerationContext context) {
        return value;
    }

    public Object getValue()                { return value; }
    public void setValue(Object value)      { this.value = value; }
}
