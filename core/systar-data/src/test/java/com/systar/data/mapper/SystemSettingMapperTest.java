package com.systar.data.mapper;

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
class SystemSettingMapperTest {

    @Autowired
    private SystemSettingMapper mapper;

    @Test
    void insertAndFindById() {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey("server.port");
        entity.setValue("8080");
        entity.setDescription("Server listen port");

        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);

        Integer generatedId = entity.getId();
        assertThat(generatedId).isNotNull().isPositive();

        SystemSettingEntity found = mapper.selectById(generatedId);
        assertThat(found).isNotNull();
        assertThat(found.getConfigKey()).isEqualTo("server.port");
        assertThat(found.getValue()).isEqualTo("8080");
        assertThat(found.getDescription()).isEqualTo("Server listen port");
    }

    @Test
    void update() {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey("alarm.enabled");
        entity.setValue("true");
        mapper.insert(entity);

        Integer id = entity.getId();
        entity.setValue("false");
        entity.setDescription("Alarm disabled");
        int rows = mapper.updateById(entity);
        assertThat(rows).isEqualTo(1);

        SystemSettingEntity updated = mapper.selectById(id);
        assertThat(updated.getValue()).isEqualTo("false");
        assertThat(updated.getDescription()).isEqualTo("Alarm disabled");
    }

    @Test
    void delete() {
        SystemSettingEntity entity = new SystemSettingEntity();
        entity.setConfigKey("temp.delete");
        entity.setValue("test");
        mapper.insert(entity);

        Integer id = entity.getId();
        int rows = mapper.deleteById(id);
        assertThat(rows).isEqualTo(1);

        SystemSettingEntity deleted = mapper.selectById(id);
        assertThat(deleted).isNull();
    }
}
