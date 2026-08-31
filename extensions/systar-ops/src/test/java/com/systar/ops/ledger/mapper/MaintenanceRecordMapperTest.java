package com.systar.ops.ledger.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.ledger.entity.MaintenanceRecordEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class MaintenanceRecordMapperTest {

    private static final int NONEXISTENT_FK_ID = 9999;

    @Autowired
    private MaintenanceRecordMapper mapper;

    private MaintenanceRecordEntity buildEntity() {
        MaintenanceRecordEntity entity = new MaintenanceRecordEntity();
        entity.setDeviceId(NONEXISTENT_FK_ID);
        entity.setType("REPAIR");
        entity.setTitle("Test maintenance");
        entity.setDescription("Test description");
        entity.setPerformerId(1L);
        entity.setCreatorId(1L);
        entity.setCost(new BigDecimal("100.00"));
        entity.setPerformedAt(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        MaintenanceRecordEntity entity = buildEntity();
        mapper.insert(entity);

        MaintenanceRecordEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getType()).isEqualTo("REPAIR");
        assertThat(found.getCost()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void update() {
        MaintenanceRecordEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setTitle("Updated maintenance");
        entity.setCost(new BigDecimal("200.00"));
        mapper.updateById(entity);

        MaintenanceRecordEntity found = mapper.selectById(entity.getId());
        assertThat(found.getTitle()).isEqualTo("Updated maintenance");
        assertThat(found.getCost()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void delete() {
        MaintenanceRecordEntity entity = buildEntity();
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
