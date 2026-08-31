package com.systar.simulator.app.controller.dto;

/**
 * Lightweight representation of a data point for list views.
 *
 * @param id           unique data point identifier
 * @param name         human-readable data point name
 * @param currentValue current generated or overridden value
 * @param overridden   whether an override is currently active
 */
public record DataPointSummary(String id, String name, Object currentValue, boolean overridden) {}
