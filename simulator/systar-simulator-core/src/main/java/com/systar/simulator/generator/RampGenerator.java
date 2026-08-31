package com.systar.simulator.generator;

public class RampGenerator implements DataGenerator {
    private double  start           = 0.0;
    private double  end             = 100.0;
    private double  durationSeconds = 60.0;
    private boolean loop            = true;

    public RampGenerator() {}

    @Override
    public Object generate(GenerationContext context) {
        double elapsed = context.elapsedMillis() / 1000.0;
        double t;
        if (loop) {
            t = (elapsed % durationSeconds) / durationSeconds;
        } else {
            t = Math.min(elapsed / durationSeconds, 1.0);
        }
        return start + (end - start) * t;
    }

    public double getStart()                           { return start; }
    public void setStart(double start)                  { this.start = start; }
    public double getEnd()                              { return end; }
    public void setEnd(double end)                      { this.end = end; }
    public double getDurationSeconds()                  { return durationSeconds; }
    public void setDurationSeconds(double v)             { this.durationSeconds = v; }
    public boolean isLoop()                             { return loop; }
    public void setLoop(boolean loop)                   { this.loop = loop; }
}
