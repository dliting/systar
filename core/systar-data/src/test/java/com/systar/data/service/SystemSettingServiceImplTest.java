package com.systar.data.service;

import com.systar.data.entity.SystemSettingEntity;
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
class SystemSettingServiceImplTest {

    @Autowired
    private SystemSettingService service;

    @Test
    void saveAndRetrieve() {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey("test.key");
        entity.setValue("test_value");
        entity.setDescription("Test description");

        assertThat(service.save(entity)).isTrue();

        SystemSettingEntity found = service.getById(entity.getId());
        assertThat(found.getConfigKey()).isEqualTo("test.key");
        assertThat(found.getValue()).isEqualTo("test_value");
    }

    @Test
    void update() {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey("update.key");
        entity.setValue("old");
        service.save(entity);

        entity.setValue("new");
        assertThat(service.updateById(entity)).isTrue();

        assertThat(service.getById(entity.getId()).getValue()).isEqualTo("new");
    }

    @Test
    void remove() {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey("delete.key");
        entity.setValue("value");
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }
}
