package com.systar.data.service;

import com.systar.data.entity.LinkageRuleEffectEntity;
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
class LinkageRuleEffectServiceImplTest {

    @Autowired
    private LinkageRuleEffectService service;

    @Autowired
    private LinkageRuleService ruleService;

    private int insertRule() {
        LinkageRuleEntity rule = new LinkageRuleEntity();
        rule.setName("EffectSvcTest");
        rule.setCauseType("ALARM");
        rule.setEnabled(true);
        ruleService.save(rule);
        return rule.getId();
    }

    @Test
    void saveAndRetrieve() {
        int ruleId = insertRule();
        LinkageRuleEffectEntity entity = new LinkageRuleEffectEntity();
        entity.setRuleId(ruleId);
        entity.setEffectMonitorId(700);
        entity.setEffectCommand("START");

        assertThat(service.save(entity)).isTrue();

        LinkageRuleEffectEntity found = service.getById(entity.getId());
        assertThat(found.getRuleId()).isEqualTo(ruleId);
        assertThat(found.getEffectMonitorId()).isEqualTo(700);
        assertThat(found.getEffectCommand()).isEqualTo("START");
    }

    @Test
    void remove() {
        int ruleId = insertRule();
        LinkageRuleEffectEntity entity = new LinkageRuleEffectEntity();
        entity.setRuleId(ruleId);
        entity.setEffectMonitorId(800);
        entity.setEffectCommand("STOP");
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }
}
