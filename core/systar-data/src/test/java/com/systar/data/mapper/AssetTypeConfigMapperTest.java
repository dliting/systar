package com.systar.data.mapper;

import com.systar.data.entity.AssetTypeConfigEntity;
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
class AssetTypeConfigMapperTest {

    @Autowired
    private AssetTypeConfigMapper mapper;

    @Test
    void insertAndFindById() {
        AssetTypeConfigEntity entity = new AssetTypeConfigEntity();
        entity.setKind("PROBE");
        entity.setTypeName("ModbusFloatProbe");
        entity.setCaption("Modbus Float Probe");
        entity.setDriverClass("com.systar.monitor.drivers.modbus.ModbusFloatProbe");
        entity.setProperties("<PropertyList/>");
        entity.setVersion(1);
        entity.setContent("<ProbeConfig/>");

        assertThat(mapper.insert(entity)).isEqualTo(1);
        assertThat(entity.getId()).isNotNull().isPositive();

        AssetTypeConfigEntity found = mapper.selectById(entity.getId());
        assertThat(found.getKind()).isEqualTo("PROBE");
        assertThat(found.getTypeName()).isEqualTo("ModbusFloatProbe");
        assertThat(found.getVersion()).isEqualTo(1);
    }

    @Test
    void update() {
        AssetTypeConfigEntity entity = new AssetTypeConfigEntity();
        entity.setKind("CONTROL");
        entity.setTypeName("TestControl");
        entity.setCaption("Test");
        entity.setVersion(1);
        mapper.insert(entity);

        entity.setVersion(2);
        entity.setCaption("Updated");
        mapper.updateById(entity);

        AssetTypeConfigEntity updated = mapper.selectById(entity.getId());
        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getCaption()).isEqualTo("Updated");
    }

    @Test
    void delete() {
        AssetTypeConfigEntity entity = new AssetTypeConfigEntity();
        entity.setKind("SERVICE");
        entity.setTypeName("ToDelete");
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }
}
