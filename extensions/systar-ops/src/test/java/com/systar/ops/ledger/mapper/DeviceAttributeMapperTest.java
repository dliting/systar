package com.systar.ops.ledger.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.ledger.entity.DeviceAttributeEntity;
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
class DeviceAttributeMapperTest {

    private static final int NONEXISTENT_FK_ID = 9999;

    @Autowired
    private DeviceAttributeMapper mapper;

    private DeviceAttributeEntity buildEntity() {
        DeviceAttributeEntity entity = new DeviceAttributeEntity();
        entity.setDeviceId(NONEXISTENT_FK_ID);
        entity.setAttrKey("test_attr_" + System.nanoTime());
        entity.setAttrValue("Test Value");
        entity.setAttrType("STRING");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        DeviceAttributeEntity entity = buildEntity();
        mapper.insert(entity);

        DeviceAttributeEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getAttrKey()).startsWith("test_attr_");
    }

    @Test
    void update() {
        DeviceAttributeEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setAttrValue("Building B");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getAttrValue()).isEqualTo("Building B");
    }

    @Test
    void delete() {
        DeviceAttributeEntity entity = buildEntity();
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
