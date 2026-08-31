package com.systar.common.service;

import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;

import java.time.LocalDate;
import java.util.List;

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
    PagedResult<DeviceDto> listDevices(Integer spaceId, Short catalog,
                                        String lifecycleStatus, int page, int size);

    /**
     * Find devices whose warranty expires before the given date.
     */
    List<DeviceDto> findWarrantyExpiring(LocalDate before);

    /**
     * Walk up the parent chain to find the owning space ID.
     *
     * @return space ID, or null if not resolvable
     */
    Integer resolveSpaceId(Integer deviceId);
}
