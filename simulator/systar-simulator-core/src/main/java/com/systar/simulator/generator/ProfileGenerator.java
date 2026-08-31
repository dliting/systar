package com.systar.simulator.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProfileGenerator implements DataGenerator {
    public record Segment(int secondsOfDay, double value) {}

    private List<Segment> segments      = new ArrayList<>();
    private double        noiseStdDev    = 0.0;
    private String        interpolation  = "linear";

    public ProfileGenerator() {}

    @Override
    public Object generate(GenerationContext context) {
        long elapsed      = context.elapsedMillis();
        int  secondsOfDay = (int) ((elapsed / 1000) % 86400);

        if (segments.isEmpty()) {
            return 0.0;
        }

        // Find the segment pair that brackets secondsOfDay.
        // The segments list wraps around midnight: after the last segment
        // comes the first segment of the next day.
        Segment before = segments.get(segments.size() - 1);
        Segment after  = segments.get(0);

        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).secondsOfDay >= secondsOfDay) {
                after = segments.get(i);
                if (i > 0) {
                    before = segments.get(i - 1);
                }
                break;
            }
            // If we reach the end, before = last, after = first (midnight wrap)
            if (i == segments.size() - 1) {
                before = segments.get(i);
                after  = segments.get(0);
            }
        }

        double value;
        if ("step".equals(interpolation)) {
            value = before.value;
        } else {
            int range = after.secondsOfDay - before.secondsOfDay;
            // Handle midnight wrap: if range is negative, the boundary crossed midnight
            if (range <= 0) {
                range += 86400;
            }
            int elapsedSinceBefore = secondsOfDay - before.secondsOfDay;
            if (elapsedSinceBefore < 0) {
                elapsedSinceBefore += 86400;
            }
            double fraction = range > 0 ? (double) elapsedSinceBefore / range : 0.0;
            value = before.value + (after.value - before.value) * fraction;
        }

        if (noiseStdDev > 0) {
            value += context.random().nextGaussian() * noiseStdDev;
        }
        return value;
    }

    public void setSegmentsFromMaps(List<Map<String, Object>> maps) {
        segments.clear();
        for (Map<String, Object> m : maps) {
            String time = (String) m.get("time");
            double val  = ((Number) m.get("value")).doubleValue();
            int sod     = parseTimeToSeconds(time);
            segments.add(new Segment(sod, val));
        }
        segments.sort((a, b) -> Integer.compare(a.secondsOfDay, b.secondsOfDay));
    }

    private static int parseTimeToSeconds(String time) {
        String[] parts = time.split(":");
        int hours   = Integer.parseInt(parts[0]);
        int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return hours * 3600 + minutes * 60;
    }

    public List<Segment> getSegments()                    { return segments; }
    public void setSegments(List<Segment> segments)       { this.segments = segments; }
    public double getNoiseStdDev()                        { return noiseStdDev; }
    public void setNoiseStdDev(double noiseStdDev)        { this.noiseStdDev = noiseStdDev; }
    public String getInterpolation()                      { return interpolation; }
    public void setInterpolation(String interpolation)    { this.interpolation = interpolation; }
}
