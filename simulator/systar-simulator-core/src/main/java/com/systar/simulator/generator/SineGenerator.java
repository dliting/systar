package com.systar.simulator.generator;

/**
 * Generates sinusoidal values oscillating around an offset with optional Gaussian noise.
 * <p>
 * The output follows: {@code amplitude * sin(2*PI*t / periodSeconds) + offset + noise},
 * where {@code t} is elapsed seconds and {@code noise} is drawn from a Gaussian
 * distribution with standard deviation {@code noiseStdDev} (zero when not configured).
 */
public class SineGenerator implements DataGenerator {

    private static final double DEFAULT_AMPLITUDE     = 1.0;
    private static final double DEFAULT_OFFSET        = 0.0;
    private static final double DEFAULT_PERIOD_SECONDS = 60.0;

    private double amplitude     = DEFAULT_AMPLITUDE;
    private double offset        = DEFAULT_OFFSET;
    private double periodSeconds = DEFAULT_PERIOD_SECONDS;
    private double noiseStdDev   = 0.0;

    public SineGenerator() {}

    public SineGenerator(double amplitude, double offset, double periodSeconds) {
        this.amplitude     = amplitude;
        this.offset        = offset;
        this.periodSeconds = periodSeconds;
    }

    @Override
    public Object generate(GenerationContext context) {
        double elapsed = context.elapsedMillis() / 1000.0;
        double phase   = 2.0 * Math.PI * elapsed / periodSeconds;
        double value   = amplitude * Math.sin(phase) + offset;
        if (noiseStdDev > 0) {
            value += context.random().nextGaussian() * noiseStdDev;
        }
        return value;
    }

    public double getAmplitude()                        { return amplitude; }
    public void setAmplitude(double amplitude)          { this.amplitude = amplitude; }
    public double getOffset()                           { return offset; }
    public void setOffset(double offset)                { this.offset = offset; }
    public double getPeriodSeconds()                    { return periodSeconds; }
    public void setPeriodSeconds(double periodSeconds)  { this.periodSeconds = periodSeconds; }
    public double getNoiseStdDev()                      { return noiseStdDev; }
    public void setNoiseStdDev(double noiseStdDev)      { this.noiseStdDev = noiseStdDev; }
}
