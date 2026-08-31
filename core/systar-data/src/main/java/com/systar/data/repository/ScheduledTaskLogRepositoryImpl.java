package com.systar.data.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.systar.data.entity.ScheduledTaskLogEntity;
import com.systar.data.mapper.ScheduledTaskLogMapper;
import com.systar.monitor.control.ScheduledTaskLog;
import com.systar.monitor.control.ScheduledTaskLogRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository implementation that persists {@link ScheduledTaskLog} records
 * to the {@code t_scheduled_task_log} table via MyBatis-Plus.
 */
@Component
public class ScheduledTaskLogRepositoryImpl implements ScheduledTaskLogRepository {

    private final ScheduledTaskLogMapper mapper;

    public ScheduledTaskLogRepositoryImpl(ScheduledTaskLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void saveLog(ScheduledTaskLog log) {
        ScheduledTaskLogEntity entity = new ScheduledTaskLogEntity();
        entity.setTaskId(log.getTaskId());
        entity.setTaskName(log.getTaskName());
        entity.setControlId(log.getControlId());
        entity.setCommand(log.getCommand());
        entity.setExecuteTime(log.getExecuteTime());
        entity.setSuccess(log.isSuccess());
        entity.setErrorMessage(log.getErrorMessage());
        mapper.insert(entity);
        log.setId(entity.getId());
    }

    @Override
    public List<ScheduledTaskLog> findRecent(int taskId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive (got " + limit + ").");
        }
        QueryWrapper<ScheduledTaskLogEntity> qw = new QueryWrapper<>();
        qw.eq("task_id", taskId)
          .orderByDesc("execute_time")
          .last("LIMIT " + limit);
        List<ScheduledTaskLogEntity> entities = mapper.selectList(qw);
        List<ScheduledTaskLog> logs = new ArrayList<>(entities.size());
        for (ScheduledTaskLogEntity e : entities) {
            logs.add(toModel(e));
        }
        return logs;
    }

    private static ScheduledTaskLog toModel(ScheduledTaskLogEntity e) {
        ScheduledTaskLog log = new ScheduledTaskLog();
        log.setId(e.getId() == null ? 0L : e.getId());
        log.setTaskId(e.getTaskId() == null ? 0 : e.getTaskId());
        log.setTaskName(e.getTaskName());
        log.setControlId(e.getControlId() == null ? 0 : e.getControlId());
        log.setCommand(e.getCommand());
        log.setExecuteTime(e.getExecuteTime() == null ? 0L : e.getExecuteTime());
        log.setSuccess(e.getSuccess() != null && e.getSuccess());
        log.setErrorMessage(e.getErrorMessage());
        return log;
    }
}
