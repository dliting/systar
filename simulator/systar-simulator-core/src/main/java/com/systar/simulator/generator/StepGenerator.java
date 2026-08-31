package com.systar.simulator.generator;

import java.util.List;

public class StepGenerator implements DataGenerator {
    private List<Object> values         = List.of(0.0);
    private double       intervalSeconds = 1.0;

    public StepGenerator() {}

    @Override
    public Object generate(GenerationContext context) {
        double elapsed = context.elapsedMillis() / 1000.0;
        int    index   = (int) (elapsed / intervalSeconds) % values.size();
        return values.get(index);
    }

    public List<Object> getValues()                        { return values; }
    public void setValues(List<Object> values)              { this.values = values; }
    public double getIntervalSeconds()                      { return intervalSeconds; }
    public void setIntervalSeconds(double intervalSeconds)  { this.intervalSeconds = intervalSeconds; }
}
