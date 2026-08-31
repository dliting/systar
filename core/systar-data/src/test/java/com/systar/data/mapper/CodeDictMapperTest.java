package com.systar.data.mapper;

import com.systar.data.entity.CodeCatalogEntity;
import com.systar.data.entity.CodeDictEntity;
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
class CodeDictMapperTest {

    @Autowired
    private CodeDictMapper mapper;

    @Autowired
    private CodeCatalogMapper catalogMapper;

    private int insertCatalog() {
        CodeCatalogEntity catalog = new CodeCatalogEntity();
        catalog.setName("test_catalog");
        catalogMapper.insert(catalog);
        return catalog.getId();
    }

    @Test
    void insertAndFindById() {
        int catalogId = insertCatalog();
        CodeDictEntity entity = new CodeDictEntity();
        entity.setCatalogId(catalogId);
        entity.setName("modbus_tcp");
        entity.setCaption("Modbus TCP");
        entity.setParentId(0);
        assertThat(mapper.insert(entity)).isEqualTo(1);

        CodeDictEntity found = mapper.selectById(entity.getId());
        assertThat(found.getCatalogId()).isEqualTo(catalogId);
        assertThat(found.getName()).isEqualTo("modbus_tcp");
        assertThat(found.getCaption()).isEqualTo("Modbus TCP");
    }

    @Test
    void update() {
        int catalogId = insertCatalog();
        CodeDictEntity entity = new CodeDictEntity();
        entity.setCatalogId(catalogId);
        entity.setName("opc_ua");
        entity.setCaption("OPC UA Old");
        entity.setParentId(0);
        mapper.insert(entity);

        entity.setCaption("OPC UA Updated");
        mapper.updateById(entity);

        CodeDictEntity updated = mapper.selectById(entity.getId());
        assertThat(updated.getCaption()).isEqualTo("OPC UA Updated");
    }

    @Test
    void delete() {
        int catalogId = insertCatalog();
        CodeDictEntity entity = new CodeDictEntity();
        entity.setCatalogId(catalogId);
        entity.setName("to_delete");
        entity.setCaption("Delete Me");
        entity.setParentId(0);
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }
}
