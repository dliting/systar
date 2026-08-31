package com.systar.data.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.systar.data.entity.ScheduledTaskEntity;
import com.systar.data.mapper.ScheduledTaskMapper;
import com.systar.monitor.control.ScheduledTask;
import com.systar.monitor.control.ScheduledTaskRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository implementation that persists {@link ScheduledTask} definitions
 * to the {@code t_scheduled_task} table via MyBatis-Plus.
 */
@Component
public class ScheduledTaskRepositoryImpl implements ScheduledTaskRepository {

    private final ScheduledTaskMapper mapper;

    public ScheduledTaskRepositoryImpl(ScheduledTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ScheduledTask> findAll() {
        List<ScheduledTaskEntity> entities = mapper.selectList(new QueryWrapper<>());
        List<ScheduledTask> tasks = new ArrayList<>(entities.size());
        for (ScheduledTaskEntity e : entities) {
            tasks.add(toModel(e));
        }
        return tasks;
    }

    @Override
    public void save(ScheduledTask task) {
        ScheduledTaskEntity entity = toEntity(task);
        entity.setId(null);
        mapper.insert(entity);
        task.setId(entity.getId());
    }

    @Override
    public void update(ScheduledTask task) {
        if (task.getId() <= 0) {
            throw new IllegalArgumentException(
                    "Task id must be positive for update (got " + task.getId() + ").");
        }
        mapper.updateById(toEntity(task));
    }

    @Override
    public void deleteById(int taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException(
                    "Task id must be positive for delete (got " + taskId + ").");
        }
        mapper.deleteById(taskId);
    }

    @Override
    public ScheduledTask findById(int taskId) {
        if (taskId <= 0) {
            return null;
        }
        ScheduledTaskEntity entity = mapper.selectById(taskId);
        return entity != null ? toModel(entity) : null;
    }

    private static ScheduledTask toModel(ScheduledTaskEntity e) {
        ScheduledTask t = new ScheduledTask();
        t.setId(e.getId() == null ? 0 : e.getId());
        t.setName(e.getName());
        t.setControlId(e.getControlId() == null ? 0 : e.getControlId());
        t.setCommand(e.getCommand());
        t.setCronExpression(e.getCronExpression());
        t.setEnabled(e.getEnabled() != null && e.getEnabled());
        t.setDescription(e.getDescription());
        return t;
    }

    private static ScheduledTaskEntity toEntity(ScheduledTask t) {
        ScheduledTaskEntity e = new ScheduledTaskEntity();
        e.setId(t.getId() == 0 ? null : t.getId());
        e.setName(t.getName());
        e.setControlId(t.getControlId());
        e.setCommand(t.getCommand());
        e.setCronExpression(t.getCronExpression());
        e.setEnabled(t.isEnabled());
        e.setDescription(t.getDescription());
        return e;
    }
}
