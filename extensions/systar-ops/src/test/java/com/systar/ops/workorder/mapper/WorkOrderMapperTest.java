package com.systar.ops.workorder.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class WorkOrderMapperTest {

    private static final int NONEXISTENT_FK_ID = 9999;

    @Autowired
    private WorkOrderMapper mapper;

    private WorkOrderEntity buildEntity() {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setOrderNo("WO-" + System.nanoTime());
        entity.setTitle("Test work order");
        entity.setType("REPAIR");
        entity.setSource("MANUAL");
        entity.setDeviceId(NONEXISTENT_FK_ID);
        entity.setPriority(2);
        entity.setStatus("CREATED");
        entity.setCreatorId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setDueTime(LocalDateTime.now().plusDays(1));
        return entity;
    }

    @Test
    void insertAndFindById() {
        WorkOrderEntity entity = buildEntity();
        mapper.insert(entity);

        WorkOrderEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getOrderNo()).isEqualTo(entity.getOrderNo());
        assertThat(found.getTitle()).isEqualTo("Test work order");
    }

    @Test
    void update() {
        WorkOrderEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setTitle("Updated title");
        entity.setStatus("PROCESSING");
        mapper.updateById(entity);

        WorkOrderEntity found = mapper.selectById(entity.getId());
        assertThat(found.getTitle()).isEqualTo("Updated title");
        assertThat(found.getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void delete() {
        WorkOrderEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        mapper.insert(buildEntity());
        mapper.insert(buildEntity());

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
