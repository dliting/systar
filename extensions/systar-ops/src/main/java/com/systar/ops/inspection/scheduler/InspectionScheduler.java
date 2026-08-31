package com.systar.ops.inspection.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.common.config.SystemConfigManager;
import com.systar.ops.inspection.InspectionTaskStatus;
import com.systar.ops.inspection.entity.InspectionPlanEntity;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
import com.systar.ops.inspection.mapper.InspectionTaskMapper;
import com.systar.ops.inspection.service.InspectionPlanService;
import com.systar.ops.inspection.service.InspectionTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class InspectionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(InspectionScheduler.class);
    private static final int GENERATION_WINDOW_SECONDS = 60;
    private static final int DEFAULT_TIMEOUT_HOURS = 72;
    private static final String CONFIG_TASK_TIMEOUT_HOURS = "ops.inspection.task_timeout_hours";

    private final InspectionPlanService planService;
    private final InspectionTaskService taskService;
    private final InspectionTaskMapper taskMapper;
    private final SystemConfigManager configManager;

    public InspectionScheduler(InspectionPlanService planService,
                               InspectionTaskService taskService,
                               InspectionTaskMapper taskMapper,
                               SystemConfigManager configManager) {
        this.planService = planService;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.configManager = configManager;
    }

    @Scheduled(fixedRate = 60000)
    public void generateTasks() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        for (InspectionPlanEntity plan : planService.findEnabledPlans()) {
            try {
                if (shouldGenerateNow(plan.getCronExpression(), now)) {
                    taskService.generateFromPlan(plan, now.truncatedTo(ChronoUnit.MINUTES));
                }
            } catch (IllegalArgumentException ex) {
                LOGGER.warn("Invalid inspection cron expression, planId={}, cron={}", plan.getId(), plan.getCronExpression(), ex);
            }
        }
        checkTimeoutTasks(now);
    }

    private boolean shouldGenerateNow(String cronExpression, LocalDateTime now) {
        CronExpression cron = CronExpression.parse(cronExpression);
        LocalDateTime previous = now.minusSeconds(GENERATION_WINDOW_SECONDS);
        LocalDateTime next = cron.next(previous);
        return next != null && !next.isAfter(now);
    }

    private void checkTimeoutTasks(LocalDateTime now) {
        int timeoutHours = configManager.getIntValue(CONFIG_TASK_TIMEOUT_HOURS, DEFAULT_TIMEOUT_HOURS);
        List<InspectionTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<InspectionTaskEntity>()
                .in(InspectionTaskEntity::getStatus,
                        InspectionTaskStatus.PENDING.name(),
                        InspectionTaskStatus.IN_PROGRESS.name())
                .lt(InspectionTaskEntity::getScheduledTime, now.minusHours(timeoutHours)));
        for (InspectionTaskEntity task : tasks) {
            LOGGER.warn("Inspection task timed out, taskId={}, status={}, scheduledTime={}",
                    task.getId(), task.getStatus(), task.getScheduledTime());
        }
    }
}
