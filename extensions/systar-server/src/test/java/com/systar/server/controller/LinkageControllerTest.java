package com.systar.server.controller;

import com.systar.data.entity.LinkageRuleCauseEntity;
import com.systar.data.entity.LinkageRuleEffectEntity;
import com.systar.data.entity.LinkageRuleEntity;
import com.systar.data.service.LinkageRuleCauseService;
import com.systar.data.service.LinkageRuleEffectService;
import com.systar.data.service.LinkageRuleService;
import com.systar.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class LinkageControllerTest {

    private LinkageController    controller;
    private LinkageRuleService   ruleService;
    private LinkageRuleCauseService  causeService;
    private LinkageRuleEffectService effectService;

    @BeforeEach
    void setUp() {
        ruleService   = mock(LinkageRuleService.class);
        causeService  = mock(LinkageRuleCauseService.class);
        effectService = mock(LinkageRuleEffectService.class);
        controller    = new LinkageController(ruleService, causeService, effectService);
    }

    @Test
    @DisplayName("listRules returns rule list")
    void listRules() {
        LinkageRuleEntity rule = new LinkageRuleEntity();
        rule.setId(1);
        rule.setName("test");
        rule.setCauseType("MONITOR");

        when(ruleService.list()).thenReturn(List.of(rule));

        Result<List<LinkageRuleEntity>> result = controller.listRules();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("listRulesWithDetails groups causes and effects by rule")
    void listRulesWithDetails() {
        LinkageRuleEntity rule = new LinkageRuleEntity();
        rule.setId(1);
        rule.setName("test");
        rule.setCauseType("MONITOR");

        LinkageRuleCauseEntity cause = new LinkageRuleCauseEntity();
        cause.setId(1);
        cause.setRuleId(1);

        LinkageRuleEffectEntity effect = new LinkageRuleEffectEntity();
        effect.setId(1);
        effect.setRuleId(1);

        when(ruleService.list()).thenReturn(List.of(rule));
        when(causeService.list()).thenReturn(List.of(cause));
        when(effectService.list()).thenReturn(List.of(effect));

        var result = controller.listRulesWithDetails();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
        var item = result.getData().get(0);
        assertThat(item).containsKey("rule");
        assertThat(item).containsKey("causes");
        assertThat(item).containsKey("effects");
    }

    @Test
    @DisplayName("createRule validates name is required")
    void createRuleValidation() {
        LinkageController.LinkageRuleCreateDTO dto = new LinkageController.LinkageRuleCreateDTO();
        dto.setName("");
        dto.setCauseType("MONITOR");

        Result<Integer> result = controller.createRule(dto);
        assertThat(result.getCode()).isNotEqualTo(0);
    }

    @Test
    @DisplayName("deleteRule removes rule, causes, and effects")
    void deleteRule() {
        when(ruleService.getById(1)).thenReturn(new LinkageRuleEntity());

        Result<Void> result = controller.deleteRule(1);
        assertThat(result.getCode()).isEqualTo(0);
        verify(ruleService).removeById(1);
    }
}
