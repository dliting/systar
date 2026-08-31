package com.systar.ops.ledger.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;
import com.systar.ops.ledger.entity.DeviceAttributeEntity;
import com.systar.ops.ledger.entity.MaintenanceAttachmentEntity;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import com.systar.ops.ledger.mapper.MaintenanceAttachmentMapper;
import com.systar.ops.ledger.service.DeviceLedgerService;
import com.systar.ops.ledger.service.MaintenanceRecordService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/device-ledger")
public class DeviceLedgerController {

    private final DeviceLedgerService deviceLedgerService;
    private final MaintenanceRecordService maintenanceRecordService;
    private final MaintenanceAttachmentMapper maintenanceAttachmentMapper;

    public DeviceLedgerController(DeviceLedgerService deviceLedgerService,
                                  MaintenanceRecordService maintenanceRecordService,
                                  MaintenanceAttachmentMapper maintenanceAttachmentMapper) {
        this.deviceLedgerService = deviceLedgerService;
        this.maintenanceRecordService = maintenanceRecordService;
        this.maintenanceAttachmentMapper = maintenanceAttachmentMapper;
    }

    @GetMapping
    public PagedResult<DeviceDto> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer spaceId,
            @RequestParam(required = false) Short catalog,
            @RequestParam(required = false) String lifecycleStatus) {
        return deviceLedgerService.getDeviceLedger(page, size, spaceId, catalog, lifecycleStatus);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        PagedResult<DeviceDto> devices = deviceLedgerService.getDeviceLedger(1, Integer.MAX_VALUE, null, null, null);
        long inService = devices.records().stream()
                .filter(device -> "IN_SERVICE".equals(device.lifecycleStatus()))
                .count();
        long underRepair = devices.records().stream()
                .filter(device -> "UNDER_REPAIR".equals(device.lifecycleStatus()))
                .count();
        long retired = devices.records().stream()
                .filter(device -> "RETIRED".equals(device.lifecycleStatus()))
                .count();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", (long) devices.records().size());
        stats.put("inService", inService);
        stats.put("underRepair", underRepair);
        stats.put("retired", retired);
        return stats;
    }

    @GetMapping("/{deviceId}")
    public Map<String, Object> detail(@PathVariable Integer deviceId) {
        Map<String, Object> detail = deviceLedgerService.getDeviceDetail(deviceId);
        if (detail.get("device") == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found: " + deviceId);
        }
        return detail;
    }

    @GetMapping("/{deviceId}/attributes")
    public List<DeviceAttributeEntity> getAttributes(@PathVariable Integer deviceId) {
        return deviceLedgerService.getAttributes(deviceId);
    }

    @PostMapping("/{deviceId}/attributes")
    public void createOrUpdateAttributes(@PathVariable Integer deviceId, @RequestBody List<DeviceAttributeEntity> attributes) {
        deviceLedgerService.batchSetAttributeEntities(deviceId, attributes);
    }

    @PutMapping("/{deviceId}/attributes")
    public void setAttributes(@PathVariable Integer deviceId, @RequestBody List<DeviceAttributeEntity> attributes) {
        deviceLedgerService.batchSetAttributeEntities(deviceId, attributes);
    }

    @DeleteMapping("/{deviceId}/attributes/{attrKey}")
    public void deleteAttribute(@PathVariable Integer deviceId, @PathVariable String attrKey) {
        deviceLedgerService.deleteAttribute(deviceId, attrKey);
    }

    @GetMapping("/warranty-expiring")
    public List<DeviceDto> warrantyExpiring(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before) {
        return deviceLedgerService.getWarrantyExpiringDevices(before);
    }

    @GetMapping({"/{deviceId}/maintenance", "/{deviceId}/maintenance-records"})
    public Page<MaintenanceRecordEntity> listMaintenanceRecords(
            @PathVariable Integer deviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return maintenanceRecordService.listByDevice(deviceId, page, size);
    }

    @PostMapping({"/{deviceId}/maintenance", "/{deviceId}/maintenance-records"})
    public MaintenanceRecordEntity createMaintenanceRecord(@PathVariable Integer deviceId,
                                                           @RequestBody MaintenanceRecordEntity record) {
        record.setDeviceId(deviceId);
        return maintenanceRecordService.create(record);
    }

    @PostMapping("/{deviceId}/maintenance/{recordId}/attachments")
    public MaintenanceAttachmentEntity uploadMaintenanceAttachment(@PathVariable Integer deviceId,
                                                                   @PathVariable Long recordId,
                                                                   @RequestParam Long uploadedBy,
                                                                   @RequestParam MultipartFile file) throws IOException {
        MaintenanceRecordEntity record = maintenanceRecordService.getById(recordId);
        if (record == null || !deviceId.equals(record.getDeviceId())) {
            throw new IllegalArgumentException("Maintenance record not found for device: " + recordId);
        }
        Path directory = Path.of("uploads", "maintenance", String.valueOf(recordId));
        Files.createDirectories(directory);
        Path target = directory.resolve(Path.of(file.getOriginalFilename()).getFileName());
        file.transferTo(target);

        MaintenanceAttachmentEntity attachment = new MaintenanceAttachmentEntity();
        attachment.setMaintenanceId(recordId);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setFilePath(target.toString());
        attachment.setFileSize(file.getSize());
        attachment.setUploadedBy(uploadedBy);
        attachment.setCreatedAt(LocalDateTime.now());
        maintenanceAttachmentMapper.insert(attachment);
        return attachment;
    }

    @GetMapping("/maintenance-records/{id}")
    public MaintenanceRecordEntity getMaintenanceRecord(@PathVariable Long id) {
        return maintenanceRecordService.getById(id);
    }
}
