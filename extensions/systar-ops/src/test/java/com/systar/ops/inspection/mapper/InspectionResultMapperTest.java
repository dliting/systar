package com.systar.ops.inspection.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.inspection.entity.InspectionResultEntity;
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
class InspectionResultMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private InspectionResultMapper mapper;

    private InspectionResultEntity buildEntity() {
        InspectionResultEntity entity = new InspectionResultEntity();
        entity.setTaskId(NONEXISTENT_FK_ID);
        entity.setDeviceId((int) NONEXISTENT_FK_ID);
        entity.setTemplateId(NONEXISTENT_FK_ID);
        entity.setItemName("Check temperature");
        entity.setExpectedValue("20-25");
        entity.setCheckResult("NORMAL");
        entity.setActualValue("22");
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        InspectionResultEntity entity = buildEntity();
        mapper.insert(entity);

        InspectionResultEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getCheckResult()).isEqualTo("NORMAL");
    }

    @Test
    void update() {
        InspectionResultEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setCheckResult("ABNORMAL");
        entity.setActualValue("30");
        mapper.updateById(entity);

        InspectionResultEntity found = mapper.selectById(entity.getId());
        assertThat(found.getCheckResult()).isEqualTo("ABNORMAL");
        assertThat(found.getActualValue()).isEqualTo("30");
    }

    @Test
    void delete() {
        InspectionResultEntity entity = buildEntity();
        mapper.insert(entity);

        mapper.deleteById(entity.getId());
        assertThat(mapper.selectById(entity.getId())).isNull();
    }

    @Test
    void selectList() {
        InspectionResultEntity e1 = buildEntity();
        mapper.insert(e1);

        InspectionResultEntity e2 = buildEntity();
        e2.setTaskId(9998L);
        e2.setDeviceId(9998);
        e2.setTemplateId(9998L);
        mapper.insert(e2);

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
