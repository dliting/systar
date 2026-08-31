package com.systar.data.service;

import com.systar.data.entity.LinkageRuleCauseEntity;
import com.systar.data.entity.LinkageRuleEntity;
import com.systar.data.test.DataTestApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class LinkageRuleCauseServiceImplTest {

    @Autowired
    private LinkageRuleCauseService service;

    @Autowired
    private LinkageRuleService ruleService;

    private int insertRule() {
        LinkageRuleEntity rule = new LinkageRuleEntity();
        rule.setName("CauseSvcTest");
        rule.setCauseType("MONITOR");
        rule.setEnabled(true);
        ruleService.save(rule);
        return rule.getId();
    }

    @Test
    void saveAndRetrieve() {
        int ruleId = insertRule();
        LinkageRuleCauseEntity entity = new LinkageRuleCauseEntity();
        entity.setRuleId(ruleId);
        entity.setCauseMonitorId(500);
        entity.setTriggerValue("NORMAL");

        assertThat(service.save(entity)).isTrue();

        LinkageRuleCauseEntity found = service.getById(entity.getId());
        assertThat(found.getRuleId()).isEqualTo(ruleId);
        assertThat(found.getTriggerValue()).isEqualTo("NORMAL");
    }

    @Test
    void remove() {
        int ruleId = insertRule();
        LinkageRuleCauseEntity entity = new LinkageRuleCauseEntity();
        entity.setRuleId(ruleId);
        entity.setCauseMonitorId(600);
        entity.setTriggerValue("ERROR");
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }
}
