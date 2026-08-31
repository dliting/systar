package com.systar.data.mapper;

import com.systar.data.entity.ScheduledTaskEntity;
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
class ScheduledTaskMapperTest {

    @Autowired
    private ScheduledTaskMapper mapper;

    @Test
    void insertAndFindById() {
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setName("Daily relay on");
        entity.setControlId(3001);
        entity.setCommand("ON");
        entity.setCronExpression("0 0 8 * * ?");
        entity.setEnabled(true);
        entity.setDescription("Turn on relay at 8am");

        int rows = mapper.insert(entity);
        assertThat(rows).isEqualTo(1);

        ScheduledTaskEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Daily relay on");
        assertThat(found.getControlId()).isEqualTo(3001);
        assertThat(found.getCommand()).isEqualTo("ON");
        assertThat(found.getCronExpression()).isEqualTo("0 0 8 * * ?");
        assertThat(found.getEnabled()).isTrue();
        assertThat(found.getDescription()).isEqualTo("Turn on relay at 8am");
    }

    @Test
    void insertWithoutDescription() {
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setName("Nightly off");
        entity.setControlId(3002);
        entity.setCommand("OFF");
        entity.setCronExpression("0 0 22 * * ?");
        entity.setEnabled(false);
        mapper.insert(entity);

        ScheduledTaskEntity found = mapper.selectById(entity.getId());
        assertThat(found.getDescription()).isNull();
        assertThat(found.getEnabled()).isFalse();
    }

    @Test
    void delete() {
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setName("Tmp");
        entity.setControlId(3003);
        entity.setCommand("PING");
        entity.setCronExpression("0 0 0 * * ?");
        entity.setEnabled(true);
        mapper.insert(entity);
        int id = entity.getId();

        mapper.deleteById(id);
        assertThat(mapper.selectById(id)).isNull();
    }
}
