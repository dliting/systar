package com.systar.data.service;

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
class LinkageRuleServiceImplTest {

    @Autowired
    private LinkageRuleService service;

    @Test
    void saveAndRetrieve() {
        LinkageRuleEntity entity = createEntity("SvcTest", "MONITOR");
        assertThat(service.save(entity)).isTrue();

        LinkageRuleEntity found = service.getById(entity.getId());
        assertThat(found.getName()).isEqualTo("SvcTest");
        assertThat(found.getCauseType()).isEqualTo("MONITOR");
        assertThat(found.getEnabled()).isTrue();
    }

    @Test
    void update() {
        LinkageRuleEntity entity = createEntity("UpdateTest", "ALARM");
        service.save(entity);

        entity.setEnabled(false);
        assertThat(service.updateById(entity)).isTrue();

        assertThat(service.getById(entity.getId()).getEnabled()).isFalse();
    }

    @Test
    void remove() {
        LinkageRuleEntity entity = createEntity("DeleteTest", "MONITOR");
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }

    private static LinkageRuleEntity createEntity(String name, String causeType) {
        LinkageRuleEntity entity = new LinkageRuleEntity();
        entity.setName(name);
        entity.setCauseType(causeType);
        entity.setEnabled(true);
        return entity;
    }
}
