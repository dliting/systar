package com.systar.common.service;

import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Read-only device information provider for cross-module access.
 * Implemented by systar-data; consumed by systar-ops.
 */
public interface DeviceInfoProvider {

    /**
     * Get device by ID.
     *
     * @return device info, or null if not found
     */
    DeviceDto getById(Integer deviceId);

    /**
     * List devices with optional filters, paginated.
     */
    PagedResult<DeviceDto> listDevices(Short catalog,
                                        String lifecycleStatus, int page, int size);

    /**
     * Find devices whose warranty expires before the given date.
     */
    List<DeviceDto> findWarrantyExpiring(LocalDate before);

    /**
     * Count devices grouped by lifecycle status.
     *
     * @return map from lifecycle status to device count; rows with NULL status
     *         are keyed null and still contribute to the total when summing values
     */
    Map<String, Long> countByLifecycleStatus();
}
