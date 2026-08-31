package com.systar.data.mapper;

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
class LinkageRuleMapperTest {

    @Autowired
    private LinkageRuleMapper mapper;

    @Test
    void insertAndFindById() {
        LinkageRuleEntity entity = createEntity("Auto control", "MONITOR");
        assertThat(mapper.insert(entity)).isEqualTo(1);
        assertThat(entity.getId()).isNotNull().isPositive();

        LinkageRuleEntity found = mapper.selectById(entity.getId());
        assertThat(found.getName()).isEqualTo("Auto control");
        assertThat(found.getCauseType()).isEqualTo("MONITOR");
        assertThat(found.getEnabled()).isTrue();
    }

    @Test
    void update() {
        LinkageRuleEntity entity = createEntity("Rule", "MONITOR");
        mapper.insert(entity);

        entity.setEnabled(false);
        entity.setName("Updated rule");
        mapper.updateById(entity);

        LinkageRuleEntity updated = mapper.selectById(entity.getId());
        assertThat(updated.getName()).isEqualTo("Updated rule");
        assertThat(updated.getEnabled()).isFalse();
    }

    @Test
    void delete() {
        LinkageRuleEntity entity = createEntity("ToDelete", "ALARM");
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    private static LinkageRuleEntity createEntity(String name, String causeType) {
        LinkageRuleEntity entity = new LinkageRuleEntity();
        entity.setName(name);
        entity.setCauseType(causeType);
        entity.setEnabled(true);
        return entity;
    }
}
