package com.systar.data.service;

import com.systar.data.entity.CodeCatalogEntity;
import com.systar.data.entity.CodeDictEntity;
import com.systar.data.mapper.CodeCatalogMapper;
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
class CodeDictServiceImplTest {

    @Autowired
    private CodeDictService service;

    @Autowired
    private CodeCatalogMapper catalogMapper;

    private int insertCatalog() {
        CodeCatalogEntity catalog = new CodeCatalogEntity();
        catalog.setName("service_test_catalog");
        catalogMapper.insert(catalog);
        return catalog.getId();
    }

    @Test
    void saveAndRetrieve() {
        int catalogId = insertCatalog();
        CodeDictEntity entity = new CodeDictEntity();
        entity.setCatalogId(catalogId);
        entity.setName("snmp");
        entity.setCaption("SNMP");
        entity.setParentId(0);

        assertThat(service.save(entity)).isTrue();

        CodeDictEntity found = service.getById(entity.getId());
        assertThat(found.getName()).isEqualTo("snmp");
        assertThat(found.getCaption()).isEqualTo("SNMP");
    }

    @Test
    void remove() {
        int catalogId = insertCatalog();
        CodeDictEntity entity = new CodeDictEntity();
        entity.setCatalogId(catalogId);
        entity.setName("del");
        entity.setCaption("Delete");
        entity.setParentId(0);
        service.save(entity);

        assertThat(service.removeById(entity.getId())).isTrue();
        assertThat(service.getById(entity.getId())).isNull();
    }
}
