package com.systar.simulator.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Spring Boot entry point for the Systar device simulator.
 * <p>
 * Runs protocol servers (Modbus TCP, OPC UA) and provides a REST API
 * for controlling the simulated device fleet.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }
}
