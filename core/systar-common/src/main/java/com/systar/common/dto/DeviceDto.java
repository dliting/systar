package com.systar.common.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Device data transfer object for cross-module communication.
 * Field names match {@code DeviceEntity} getters (minus "get") for JSON compatibility.
 */
public record DeviceDto(
        Integer id,
        String name,
        String caption,
        Integer parentId,
        Short catalog,
        String vendor,
        LocalDateTime purchaseDate,
        LocalDate warrantyDate,
        Float healthIndex,
        String model,
        String serialNumber,
        LocalDate installDate,
        String lifecycleStatus,
        String responsiblePerson,
        String department,
        String supplierContact,
        Integer maintenanceCycle,
        LocalDate lastMaintenanceDate,
        String remark
) {
}
