package com.systar.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.systar.system.entity.SysOperLogEntity;
import com.systar.system.mapper.SysOperLogMapper;
import com.systar.system.service.SysOperLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SysOperLogServiceImpl extends ServiceImpl<SysOperLogMapper, SysOperLogEntity> implements SysOperLogService {

    private static final Logger log = LoggerFactory.getLogger(SysOperLogServiceImpl.class);

    @Override
    public Page<SysOperLogEntity> listLogs(int page, int size, String username, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<SysOperLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.eq(SysOperLogEntity::getUsername, username);
        }
        if (startTime != null) {
            wrapper.ge(SysOperLogEntity::getOperTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SysOperLogEntity::getOperTime, endTime);
        }
        wrapper.orderByDesc(SysOperLogEntity::getOperTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public SysOperLogEntity getLogById(Long id) {
        return getById(id);
    }

    @Override
    public void saveLog(SysOperLogEntity entity) {
        entity.setOperTime(LocalDateTime.now());
        save(entity);
        log.info("Saved operation log for user: {}", entity.getUsername());
    }
}
