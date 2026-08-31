package com.systar.ops.inspection.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.inspection.entity.InspectionPlanEntity;
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
class InspectionPlanMapperTest {

    @Autowired
    private InspectionPlanMapper mapper;

    private InspectionPlanEntity buildEntity() {
        InspectionPlanEntity entity = new InspectionPlanEntity();
        entity.setName("Test plan");
        entity.setDescription("Test description");
        entity.setCronExpression("0 0 8 * * ?");
        entity.setEnabled(1);
        entity.setDefaultAssigneeId(1L);
        entity.setAutoCreateWorkorder(0);
        entity.setCreatorId(1L);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        InspectionPlanEntity entity = buildEntity();
        mapper.insert(entity);

        InspectionPlanEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Test plan");
        assertThat(found.getCronExpression()).isEqualTo("0 0 8 * * ?");
    }

    @Test
    void update() {
        InspectionPlanEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setName("Updated plan");
        entity.setEnabled(0);
        mapper.updateById(entity);

        InspectionPlanEntity found = mapper.selectById(entity.getId());
        assertThat(found.getName()).isEqualTo("Updated plan");
        assertThat(found.getEnabled()).isEqualTo(0);
    }

    @Test
    void delete() {
        InspectionPlanEntity entity = buildEntity();
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
