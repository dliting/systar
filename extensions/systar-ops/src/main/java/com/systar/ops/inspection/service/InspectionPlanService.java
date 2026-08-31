package com.systar.ops.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.ops.inspection.InspectionTaskStatus;
import com.systar.ops.inspection.entity.InspectionItemTemplateEntity;
import com.systar.ops.inspection.entity.InspectionPlanDeviceEntity;
import com.systar.ops.inspection.entity.InspectionPlanEntity;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
import com.systar.ops.inspection.mapper.InspectionItemTemplateMapper;
import com.systar.ops.inspection.mapper.InspectionPlanDeviceMapper;
import com.systar.ops.inspection.mapper.InspectionPlanMapper;
import com.systar.ops.inspection.mapper.InspectionTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class InspectionPlanService {

    private final InspectionPlanMapper planMapper;
    private final InspectionPlanDeviceMapper planDeviceMapper;
    private final InspectionItemTemplateMapper templateMapper;
    private final InspectionTaskMapper taskMapper;

    public InspectionPlanService(InspectionPlanMapper planMapper,
                                 InspectionPlanDeviceMapper planDeviceMapper,
                                 InspectionItemTemplateMapper templateMapper,
                                 InspectionTaskMapper taskMapper) {
        this.planMapper = planMapper;
        this.planDeviceMapper = planDeviceMapper;
        this.templateMapper = templateMapper;
        this.taskMapper = taskMapper;
    }

    @Transactional
    public InspectionPlanEntity create(InspectionPlanEntity plan,
                                       List<Integer> deviceIds,
                                       List<InspectionItemTemplateEntity> templates) {
        LocalDateTime now = LocalDateTime.now();
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        if (plan.getEnabled() == null) {
            plan.setEnabled(1);
        }
        if (plan.getAutoCreateWorkorder() == null) {
            plan.setAutoCreateWorkorder(0);
        }
        planMapper.insert(plan);
        replaceDevices(plan.getId(), deviceIds);
        replaceTemplates(plan.getId(), templates);
        return plan;
    }

    @Transactional
    public InspectionPlanEntity update(InspectionPlanEntity plan,
                                       List<Integer> deviceIds,
                                       List<InspectionItemTemplateEntity> templates) {
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
        replaceDevices(plan.getId(), deviceIds);
        replaceTemplates(plan.getId(), templates);
        return plan;
    }

    public InspectionPlanEntity getById(Long planId) {
        return planMapper.selectById(planId);
    }

    public InspectionPlanEntity getRequiredPlan(Long planId) {
        InspectionPlanEntity plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Inspection plan not found: " + planId);
        }
        return plan;
    }

    public Page<InspectionPlanEntity> list(int page, int size, Boolean enabled) {
        LambdaQueryWrapper<InspectionPlanEntity> wrapper = new LambdaQueryWrapper<>();
        if (enabled != null) {
            wrapper.eq(InspectionPlanEntity::getEnabled, enabled ? 1 : 0);
        }
        wrapper.orderByDesc(InspectionPlanEntity::getCreatedAt);
        return planMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public List<InspectionPlanEntity> findEnabledPlans() {
        return planMapper.selectList(new LambdaQueryWrapper<InspectionPlanEntity>()
                .eq(InspectionPlanEntity::getEnabled, 1));
    }

    public List<InspectionPlanDeviceEntity> getPlanDevices(Long planId) {
        return planDeviceMapper.selectList(new LambdaQueryWrapper<InspectionPlanDeviceEntity>()
                .eq(InspectionPlanDeviceEntity::getPlanId, planId));
    }

    public List<InspectionItemTemplateEntity> getTemplates(Long planId) {
        return templateMapper.selectList(new LambdaQueryWrapper<InspectionItemTemplateEntity>()
                .eq(InspectionItemTemplateEntity::getPlanId, planId)
                .orderByAsc(InspectionItemTemplateEntity::getSortOrder));
    }

    @Transactional
    public InspectionPlanDeviceEntity addDevice(Long planId, Integer deviceId) {
        getRequiredPlan(planId);
        InspectionPlanDeviceEntity existing = planDeviceMapper.selectOne(new LambdaQueryWrapper<InspectionPlanDeviceEntity>()
                .eq(InspectionPlanDeviceEntity::getPlanId, planId)
                .eq(InspectionPlanDeviceEntity::getDeviceId, deviceId));
        if (existing != null) {
            return existing;
        }
        InspectionPlanDeviceEntity planDevice = new InspectionPlanDeviceEntity();
        planDevice.setPlanId(planId);
        planDevice.setDeviceId(deviceId);
        planDeviceMapper.insert(planDevice);
        touchPlan(planId);
        return planDevice;
    }

    @Transactional
    public void removeDevice(Long planId, Integer deviceId) {
        getRequiredPlan(planId);
        planDeviceMapper.delete(new LambdaQueryWrapper<InspectionPlanDeviceEntity>()
                .eq(InspectionPlanDeviceEntity::getPlanId, planId)
                .eq(InspectionPlanDeviceEntity::getDeviceId, deviceId));
        touchPlan(planId);
    }

    @Transactional
    public InspectionItemTemplateEntity addTemplate(Long planId, InspectionItemTemplateEntity template) {
        getRequiredPlan(planId);
        template.setId(null);
        template.setPlanId(planId);
        templateMapper.insert(template);
        touchPlan(planId);
        return template;
    }

    @Transactional
    public InspectionItemTemplateEntity updateTemplate(Long planId, Long itemId, InspectionItemTemplateEntity template) {
        getRequiredPlan(planId);
        InspectionItemTemplateEntity existing = getRequiredTemplate(planId, itemId);
        template.setId(existing.getId());
        template.setPlanId(planId);
        templateMapper.updateById(template);
        touchPlan(planId);
        return template;
    }

    @Transactional
    public void deleteTemplate(Long planId, Long itemId) {
        getRequiredPlan(planId);
        InspectionItemTemplateEntity template = getRequiredTemplate(planId, itemId);
        templateMapper.deleteById(template.getId());
        touchPlan(planId);
    }

    @Transactional
    public void delete(Long planId) {
        Long activeTasks = taskMapper.selectCount(new LambdaQueryWrapper<InspectionTaskEntity>()
                .eq(InspectionTaskEntity::getPlanId, planId)
                .in(InspectionTaskEntity::getStatus,
                        InspectionTaskStatus.PENDING.name(),
                        InspectionTaskStatus.IN_PROGRESS.name()));
        if (activeTasks > 0) {
            throw new IllegalStateException("Cannot delete inspection plan with active tasks: " + planId);
        }
        planMapper.deleteById(planId);
        planDeviceMapper.delete(new LambdaQueryWrapper<InspectionPlanDeviceEntity>()
                .eq(InspectionPlanDeviceEntity::getPlanId, planId));
        templateMapper.delete(new LambdaQueryWrapper<InspectionItemTemplateEntity>()
                .eq(InspectionItemTemplateEntity::getPlanId, planId));
    }

    private InspectionItemTemplateEntity getRequiredTemplate(Long planId, Long itemId) {
        InspectionItemTemplateEntity template = templateMapper.selectById(itemId);
        if (template == null || !Objects.equals(template.getPlanId(), planId)) {
            throw new IllegalArgumentException("Inspection item template not found: " + itemId);
        }
        return template;
    }

    private void touchPlan(Long planId) {
        InspectionPlanEntity plan = new InspectionPlanEntity();
        plan.setId(planId);
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    private void replaceDevices(Long planId, List<Integer> deviceIds) {
        planDeviceMapper.delete(new LambdaQueryWrapper<InspectionPlanDeviceEntity>()
                .eq(InspectionPlanDeviceEntity::getPlanId, planId));
        if (deviceIds == null) {
            return;
        }
        for (Integer deviceId : deviceIds) {
            InspectionPlanDeviceEntity planDevice = new InspectionPlanDeviceEntity();
            planDevice.setPlanId(planId);
            planDevice.setDeviceId(deviceId);
            planDeviceMapper.insert(planDevice);
        }
    }

    private void replaceTemplates(Long planId, List<InspectionItemTemplateEntity> templates) {
        templateMapper.delete(new LambdaQueryWrapper<InspectionItemTemplateEntity>()
                .eq(InspectionItemTemplateEntity::getPlanId, planId));
        if (templates == null) {
            return;
        }
        for (InspectionItemTemplateEntity template : templates) {
            template.setId(null);
            template.setPlanId(planId);
            templateMapper.insert(template);
        }
    }
}
