package com.systar.common.service;

import java.time.LocalDate;

/**
 * Device lifecycle modification interface for cross-module writes.
 * Implemented by systar-data; consumed by systar-ops.
 */
public interface DeviceLifecycleManager {

    /**
     * Update the lifecycle status of a device.
     * Silently skips if the device does not exist.
     */
    void updateLifecycleStatus(Integer deviceId, String status);

    /**
     * Update the last maintenance date of a device.
     * Silently skips if the device does not exist.
     */
    void updateLastMaintenanceDate(Integer deviceId, LocalDate date);
}
