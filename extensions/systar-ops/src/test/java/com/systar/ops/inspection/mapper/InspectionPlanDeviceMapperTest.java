package com.systar.ops.inspection.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.inspection.entity.InspectionPlanDeviceEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class InspectionPlanDeviceMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private InspectionPlanDeviceMapper mapper;

    private InspectionPlanDeviceEntity buildEntity() {
        InspectionPlanDeviceEntity entity = new InspectionPlanDeviceEntity();
        entity.setPlanId(NONEXISTENT_FK_ID);
        entity.setDeviceId((int) NONEXISTENT_FK_ID);
        return entity;
    }

    @Test
    void insertAndFindById() {
        InspectionPlanDeviceEntity entity = buildEntity();
        mapper.insert(entity);

        InspectionPlanDeviceEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getPlanId()).isEqualTo(NONEXISTENT_FK_ID);
        assertThat(found.getDeviceId()).isEqualTo((int) NONEXISTENT_FK_ID);
    }

    @Test
    void update() {
        InspectionPlanDeviceEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setDeviceId(8888);
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getDeviceId()).isEqualTo(8888);
    }

    @Test
    void delete() {
        InspectionPlanDeviceEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        InspectionPlanDeviceEntity e1 = buildEntity();
        mapper.insert(e1);

        InspectionPlanDeviceEntity e2 = buildEntity();
        e2.setPlanId(9998L);
        e2.setDeviceId(9998);
        mapper.insert(e2);

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
