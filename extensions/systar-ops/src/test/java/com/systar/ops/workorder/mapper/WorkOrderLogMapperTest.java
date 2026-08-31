package com.systar.ops.workorder.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.workorder.entity.WorkOrderLogEntity;
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
class WorkOrderLogMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private WorkOrderLogMapper mapper;

    private WorkOrderLogEntity buildEntity() {
        WorkOrderLogEntity entity = new WorkOrderLogEntity();
        entity.setWorkOrderId(NONEXISTENT_FK_ID);
        entity.setOperatorId(1L);
        entity.setAction("STATUS_CHANGE");
        entity.setComment("Changed status");
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        WorkOrderLogEntity entity = buildEntity();
        mapper.insert(entity);

        WorkOrderLogEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getAction()).isEqualTo("STATUS_CHANGE");
    }

    @Test
    void update() {
        WorkOrderLogEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setComment("Updated comment");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getComment()).isEqualTo("Updated comment");
    }

    @Test
    void delete() {
        WorkOrderLogEntity entity = buildEntity();
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
