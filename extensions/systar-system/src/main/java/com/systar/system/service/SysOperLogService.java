package com.systar.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.system.entity.SysOperLogEntity;

import java.time.LocalDateTime;

/**
 * Operation log service.
 */
public interface SysOperLogService {

    Page<SysOperLogEntity> listLogs(int page, int size, String username, LocalDateTime startTime, LocalDateTime endTime);

    SysOperLogEntity getLogById(Long id);

    void saveLog(SysOperLogEntity entity);
}
