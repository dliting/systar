package com.systar.ops.inspection.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.inspection.entity.InspectionTaskEntity;
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
class InspectionTaskMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private InspectionTaskMapper mapper;

    private InspectionTaskEntity buildEntity() {
        InspectionTaskEntity entity = new InspectionTaskEntity();
        entity.setPlanId(NONEXISTENT_FK_ID);
        entity.setTaskNo("INS-" + System.nanoTime());
        entity.setStatus("PENDING");
        entity.setAssigneeId(1L);
        entity.setScheduledTime(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        InspectionTaskEntity entity = buildEntity();
        mapper.insert(entity);

        InspectionTaskEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void update() {
        InspectionTaskEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setStatus("COMPLETED");
        entity.setCompletedAt(LocalDateTime.now());
        mapper.updateById(entity);

        InspectionTaskEntity found = mapper.selectById(entity.getId());
        assertThat(found.getStatus()).isEqualTo("COMPLETED");
        assertThat(found.getCompletedAt()).isNotNull();
    }

    @Test
    void delete() {
        InspectionTaskEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        InspectionTaskEntity e1 = buildEntity();
        mapper.insert(e1);

        InspectionTaskEntity e2 = buildEntity();
        e2.setScheduledTime(e1.getScheduledTime().plusSeconds(1));
        mapper.insert(e2);

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
