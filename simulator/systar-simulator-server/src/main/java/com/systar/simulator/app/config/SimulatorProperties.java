package com.systar.simulator.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration properties for the simulator, bound from
 * the {@code simulator.*} prefix in {@code application.yml} (or environment
 * variables).
 */
@ConfigurationProperties(prefix = "simulator")
public class SimulatorProperties {

    /** Interval in milliseconds between data-generation ticks. */
    private long   tickIntervalMs   = 1000;

    /** Optional fixed seed for reproducible random generators; {@code null} for non-deterministic. */
    private Long   randomSeed;

    /** Classpath (or file-system) location pattern for YAML device profiles. */
    private String profilesLocation = "classpath:profiles/";

    public long getTickIntervalMs()           { return tickIntervalMs; }
    public void setTickIntervalMs(long v)     { this.tickIntervalMs = v; }

    public Long getRandomSeed()               { return randomSeed; }
    public void setRandomSeed(Long v)         { this.randomSeed = v; }

    public String getProfilesLocation()       { return profilesLocation; }
    public void setProfilesLocation(String v) { this.profilesLocation = v; }
}
