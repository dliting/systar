package com.systar.ops.workorder.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.ops.workorder.entity.WorkOrderAttachmentEntity;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.mapper.WorkOrderAttachmentMapper;
import com.systar.ops.workorder.mapper.WorkOrderMapper;
import com.systar.ops.workorder.service.WorkOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/work-orders")
public class WorkOrderController {

    private static final String UPLOAD_DIR = "uploads/work-order";

    private final WorkOrderService workOrderService;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderAttachmentMapper attachmentMapper;

    public WorkOrderController(WorkOrderService workOrderService,
                               WorkOrderMapper workOrderMapper,
                               WorkOrderAttachmentMapper attachmentMapper) {
        this.workOrderService = workOrderService;
        this.workOrderMapper = workOrderMapper;
        this.attachmentMapper = attachmentMapper;
    }

    @GetMapping
    public Page<WorkOrderEntity> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer deviceId) {
        LambdaQueryWrapper<WorkOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (status != null) wrapper.eq(WorkOrderEntity::getStatus, status);
        if (type != null) wrapper.eq(WorkOrderEntity::getType, type);
        if (deviceId != null) wrapper.eq(WorkOrderEntity::getDeviceId, deviceId);
        wrapper.orderByDesc(WorkOrderEntity::getCreatedAt);
        return workOrderMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        long total = workOrderMapper.selectCount(new LambdaQueryWrapper<>());
        long open = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                .in(WorkOrderEntity::getStatus, "CREATED", "ASSIGNED", "PROCESSING"));
        long closed = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getStatus, "CLOSED"));
        long cancelled = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getStatus, "CANCELLED"));
        return Map.of("total", total, "open", open, "closed", closed, "cancelled", cancelled);
    }

    @GetMapping("/{id}")
    public WorkOrderEntity getById(@PathVariable Long id) {
        WorkOrderEntity order = workOrderService.getById(id);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Work order not found: " + id);
        }
        return order;
    }

    @PostMapping
    public WorkOrderEntity create(@RequestBody WorkOrderEntity order) {
        return workOrderService.createWorkOrder(order);
    }

    @PutMapping("/{id}/assign")
    public void assign(@PathVariable Long id, @RequestBody OperatorRequest body) {
        workOrderService.assign(id, body.assigneeId(), body.operatorId());
    }

    @PutMapping("/{id}/process")
    public void process(@PathVariable Long id, @RequestBody OperatorRequest body) {
        workOrderService.startProcessing(id, body.operatorId());
    }

    @PutMapping("/{id}/close")
    public void close(@PathVariable Long id, @RequestBody CloseRequest body) {
        workOrderService.close(id, body.resolution(), body.operatorId());
    }

    @PutMapping("/{id}/cancel")
    public void cancel(@PathVariable Long id, @RequestBody CancelRequest body) {
        workOrderService.cancel(id, body.comment(), body.operatorId());
    }

    @PostMapping("/{id}/attachments")
    public WorkOrderAttachmentEntity uploadAttachment(@PathVariable Long id,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "uploadedBy", defaultValue = "0") Long uploadedBy) throws IOException {
        String safeFileName = Path.of(file.getOriginalFilename()).getFileName().toString();
        Path dir = Path.of(UPLOAD_DIR, String.valueOf(id));
        Files.createDirectories(dir);
        Path target = dir.resolve(safeFileName);
        file.transferTo(target);

        WorkOrderAttachmentEntity attachment = new WorkOrderAttachmentEntity();
        attachment.setWorkOrderId(id);
        attachment.setFileName(safeFileName);
        attachment.setFilePath(target.toString());
        attachment.setFileSize(file.getSize());
        attachment.setUploadedBy(uploadedBy);
        attachment.setCreatedAt(LocalDateTime.now());
        attachmentMapper.insert(attachment);
        return attachment;
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public void deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) throws IOException {
        WorkOrderAttachmentEntity attachment = attachmentMapper.selectOne(new LambdaQueryWrapper<WorkOrderAttachmentEntity>()
                .eq(WorkOrderAttachmentEntity::getWorkOrderId, id)
                .eq(WorkOrderAttachmentEntity::getId, attachmentId));
        if (attachment != null) {
            Files.deleteIfExists(Path.of(attachment.getFilePath()));
            attachmentMapper.deleteById(attachmentId);
        }
    }

    public record OperatorRequest(Long operatorId, Long assigneeId) {
    }

    public record CloseRequest(String resolution, Long operatorId) {
    }

    public record CancelRequest(String comment, Long operatorId) {
    }
}
