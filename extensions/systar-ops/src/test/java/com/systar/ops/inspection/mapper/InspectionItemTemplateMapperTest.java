package com.systar.ops.inspection.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.inspection.entity.InspectionItemTemplateEntity;
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
class InspectionItemTemplateMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private InspectionItemTemplateMapper mapper;

    private InspectionItemTemplateEntity buildEntity() {
        InspectionItemTemplateEntity entity = new InspectionItemTemplateEntity();
        entity.setPlanId(NONEXISTENT_FK_ID);
        entity.setItemName("Check temperature");
        entity.setItemType("NUMERIC");
        entity.setExpectedValue("20-25");
        entity.setSortOrder(1);
        return entity;
    }

    @Test
    void insertAndFindById() {
        InspectionItemTemplateEntity entity = buildEntity();
        mapper.insert(entity);

        InspectionItemTemplateEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getItemName()).isEqualTo("Check temperature");
    }

    @Test
    void update() {
        InspectionItemTemplateEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setItemName("Check humidity");
        entity.setExpectedValue("40-60");
        mapper.updateById(entity);

        InspectionItemTemplateEntity found = mapper.selectById(entity.getId());
        assertThat(found.getItemName()).isEqualTo("Check humidity");
        assertThat(found.getExpectedValue()).isEqualTo("40-60");
    }

    @Test
    void delete() {
        InspectionItemTemplateEntity entity = buildEntity();
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
