package com.systar.data.mapper;

import com.systar.data.entity.LinkageRuleCauseEntity;
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
class LinkageRuleCauseMapperTest {

    @Autowired
    private LinkageRuleCauseMapper mapper;

    @Autowired
    private LinkageRuleMapper ruleMapper;

    private int insertRule() {
        var rule = new com.systar.data.entity.LinkageRuleEntity();
        rule.setName("TestRule");
        rule.setCauseType("MONITOR");
        rule.setEnabled(true);
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Test
    void insertAndFindById() {
        int ruleId = insertRule();
        LinkageRuleCauseEntity entity = new LinkageRuleCauseEntity();
        entity.setRuleId(ruleId);
        entity.setCauseMonitorId(100);
        entity.setTriggerValue("WARNING");

        assertThat(mapper.insert(entity)).isEqualTo(1);

        LinkageRuleCauseEntity found = mapper.selectById(entity.getId());
        assertThat(found.getRuleId()).isEqualTo(ruleId);
        assertThat(found.getCauseMonitorId()).isEqualTo(100);
        assertThat(found.getTriggerValue()).isEqualTo("WARNING");
    }

    @Test
    void delete() {
        int ruleId = insertRule();
        LinkageRuleCauseEntity entity = new LinkageRuleCauseEntity();
        entity.setRuleId(ruleId);
        entity.setCauseMonitorId(200);
        entity.setTriggerValue("ERROR");
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }
}
