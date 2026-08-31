package com.systar.simulator.app.controller.dto;

/**
 * Lightweight representation of a device for list views.
 *
 * @param id       unique device identifier
 * @param name     human-readable device name
 * @param protocol protocol type (e.g. MODBUS_TCP, OPC_UA)
 * @param status   current device status (e.g. RUNNING, STOPPED)
 */
public record DeviceSummary(String id, String name, String protocol, String status) {}
