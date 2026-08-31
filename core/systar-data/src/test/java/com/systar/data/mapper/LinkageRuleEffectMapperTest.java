package com.systar.data.mapper;

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
class LinkageRuleEffectMapperTest {

    @Autowired
    private LinkageRuleEffectMapper mapper;

    @Autowired
    private LinkageRuleMapper ruleMapper;

    private int insertRule() {
        LinkageRuleEntity rule = new LinkageRuleEntity();
        rule.setName("EffectTestRule");
        rule.setCauseType("MONITOR");
        rule.setEnabled(true);
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Test
    void insertAndFindById() {
        int ruleId = insertRule();
        LinkageRuleEffectEntity entity = new LinkageRuleEffectEntity();
        entity.setRuleId(ruleId);
        entity.setEffectMonitorId(300);
        entity.setEffectCommand("ON");

        assertThat(mapper.insert(entity)).isEqualTo(1);

        LinkageRuleEffectEntity found = mapper.selectById(entity.getId());
        assertThat(found.getRuleId()).isEqualTo(ruleId);
        assertThat(found.getEffectMonitorId()).isEqualTo(300);
        assertThat(found.getEffectCommand()).isEqualTo("ON");
    }

    @Test
    void delete() {
        int ruleId = insertRule();
        LinkageRuleEffectEntity entity = new LinkageRuleEffectEntity();
        entity.setRuleId(ruleId);
        entity.setEffectMonitorId(400);
        entity.setEffectCommand("OFF");
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }
}
