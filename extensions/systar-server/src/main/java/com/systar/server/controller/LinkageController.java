package com.systar.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import com.systar.data.entity.*;
import com.systar.data.service.LinkageRuleCauseService;
import com.systar.data.service.LinkageRuleEffectService;
import com.systar.data.service.LinkageRuleService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitor")
public class LinkageController {

    private final LinkageRuleService       linkageRuleService;
    private final LinkageRuleCauseService  linkageRuleCauseService;
    private final LinkageRuleEffectService linkageRuleEffectService;

    public LinkageController(LinkageRuleService linkageRuleService,
                             LinkageRuleCauseService linkageRuleCauseService,
                             LinkageRuleEffectService linkageRuleEffectService) {
        this.linkageRuleService       = linkageRuleService;
        this.linkageRuleCauseService  = linkageRuleCauseService;
        this.linkageRuleEffectService = linkageRuleEffectService;
    }

    @RequirePermission("iot:linkage:query")
    @GetMapping("/linkage-rules")
    public Result<List<LinkageRuleEntity>> listRules() {
        return Result.success(linkageRuleService.list());
    }

    @RequirePermission("iot:linkage:query")
    @GetMapping("/linkage-rules/detail")
    public Result<List<Map<String, Object>>> listRulesWithDetails() {
        List<LinkageRuleEntity> rules = linkageRuleService.list();
        List<LinkageRuleCauseEntity> allCauses = linkageRuleCauseService.list();
        List<LinkageRuleEffectEntity> allEffects = linkageRuleEffectService.list();

        Map<Integer, List<LinkageRuleCauseEntity>> causeMap = allCauses.stream()
                .collect(Collectors.groupingBy(LinkageRuleCauseEntity::getRuleId));
        Map<Integer, List<LinkageRuleEffectEntity>> effectMap = allEffects.stream()
                .collect(Collectors.groupingBy(LinkageRuleEffectEntity::getRuleId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LinkageRuleEntity rule : rules) {
            Map<String, Object> item = new HashMap<>();
            item.put("rule", rule);
            item.put("causes", causeMap.getOrDefault(rule.getId(), Collections.emptyList()));
            item.put("effects", effectMap.getOrDefault(rule.getId(), Collections.emptyList()));
            result.add(item);
        }
        return Result.success(result);
    }

    @RequirePermission("iot:linkage:query")
    @GetMapping("/linkage-rules/{id}")
    public Result<Map<String, Object>> getRuleDetail(@PathVariable int id) {
        LinkageRuleEntity rule = linkageRuleService.getById(id);
        if (rule == null) {
            return Result.error(Result.CODE_NOT_FOUND, "Linkage rule not found: " + id);
        }

        List<LinkageRuleCauseEntity> causes = linkageRuleCauseService.list(
                new LambdaQueryWrapper<LinkageRuleCauseEntity>()
                        .eq(LinkageRuleCauseEntity::getRuleId, id));
        List<LinkageRuleEffectEntity> effects = linkageRuleEffectService.list(
                new LambdaQueryWrapper<LinkageRuleEffectEntity>()
                        .eq(LinkageRuleEffectEntity::getRuleId, id));

        Map<String, Object> data = new HashMap<>();
        data.put("rule", rule);
        data.put("causes", causes);
        data.put("effects", effects);
        return Result.success(data);
    }

    @RequirePermission("iot:linkage:add")
    @PostMapping("/linkage-rules")
    @Transactional
    public Result<Integer> createRule(@RequestBody LinkageRuleCreateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return Result.error("Rule name is required");
        }
        if (dto.getCauseType() == null || dto.getCauseType().isBlank()) {
            return Result.error("Cause type is required");
        }

        LinkageRuleEntity rule = new LinkageRuleEntity();
        rule.setName(dto.getName());
        rule.setCauseType(dto.getCauseType());
        rule.setEnabled(dto.getEnabled() != null ? dto.getEnabled() : true);
        rule.setCaption(dto.getCaption());
        linkageRuleService.save(rule);
        int ruleId = rule.getId();

        saveCauses(ruleId, dto.getCauses());
        saveEffects(ruleId, dto.getEffects());

        return Result.success(ruleId);
    }

    @RequirePermission("iot:linkage:edit")
    @PutMapping("/linkage-rules/{id}")
    @Transactional
    public Result<Void> updateRule(@PathVariable int id, @RequestBody LinkageRuleCreateDTO dto) {
        LinkageRuleEntity rule = linkageRuleService.getById(id);
        if (rule == null) {
            return Result.error(Result.CODE_NOT_FOUND, "Linkage rule not found: " + id);
        }

        if (dto.getName() != null)      rule.setName(dto.getName());
        if (dto.getCauseType() != null)  rule.setCauseType(dto.getCauseType());
        if (dto.getEnabled() != null)    rule.setEnabled(dto.getEnabled());
        rule.setCaption(dto.getCaption());
        linkageRuleService.updateById(rule);

        linkageRuleCauseService.remove(
                new LambdaQueryWrapper<LinkageRuleCauseEntity>()
                        .eq(LinkageRuleCauseEntity::getRuleId, id));
        linkageRuleEffectService.remove(
                new LambdaQueryWrapper<LinkageRuleEffectEntity>()
                        .eq(LinkageRuleEffectEntity::getRuleId, id));

        saveCauses(id, dto.getCauses());
        saveEffects(id, dto.getEffects());

        return Result.success(null);
    }

    @RequirePermission("iot:linkage:delete")
    @DeleteMapping("/linkage-rules/{id}")
    @Transactional
    public Result<Void> deleteRule(@PathVariable int id) {
        linkageRuleCauseService.remove(
                new LambdaQueryWrapper<LinkageRuleCauseEntity>()
                        .eq(LinkageRuleCauseEntity::getRuleId, id));
        linkageRuleEffectService.remove(
                new LambdaQueryWrapper<LinkageRuleEffectEntity>()
                        .eq(LinkageRuleEffectEntity::getRuleId, id));
        linkageRuleService.removeById(id);
        return Result.success(null);
    }

    @RequirePermission("iot:linkage:edit")
    @PutMapping("/linkage-rules/{id}/toggle")
    public Result<Void> toggleEnabled(@PathVariable int id) {
        LinkageRuleEntity rule = linkageRuleService.getById(id);
        if (rule == null) {
            return Result.error(Result.CODE_NOT_FOUND, "Linkage rule not found: " + id);
        }
        rule.setEnabled(!rule.getEnabled());
        linkageRuleService.updateById(rule);
        return Result.success(null);
    }

    private void saveCauses(int ruleId, List<LinkageRuleCauseEntity> causes) {
        if (causes == null) return;
        for (LinkageRuleCauseEntity cause : causes) {
            cause.setId(null);
            cause.setRuleId(ruleId);
            linkageRuleCauseService.save(cause);
        }
    }

    private void saveEffects(int ruleId, List<LinkageRuleEffectEntity> effects) {
        if (effects == null) return;
        for (LinkageRuleEffectEntity effect : effects) {
            effect.setId(null);
            effect.setRuleId(ruleId);
            linkageRuleEffectService.save(effect);
        }
    }

    @lombok.Data
    public static class LinkageRuleCreateDTO {
        private String                        name;
        private String                        causeType;
        private Boolean                       enabled;
        private String                        caption;
        private List<LinkageRuleCauseEntity>  causes;
        private List<LinkageRuleEffectEntity> effects;
    }
}
