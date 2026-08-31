package com.systar.data.mapper;

import com.systar.data.entity.CodeCatalogEntity;
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
class CodeCatalogMapperTest {

    @Autowired
    private CodeCatalogMapper mapper;

    @Test
    void insertAndFindById() {
        CodeCatalogEntity entity = new CodeCatalogEntity();
        entity.setName("device_type");
        assertThat(mapper.insert(entity)).isEqualTo(1);

        CodeCatalogEntity found = mapper.selectById(entity.getId());
        assertThat(found.getName()).isEqualTo("device_type");
    }

    @Test
    void delete() {
        CodeCatalogEntity entity = new CodeCatalogEntity();
        entity.setName("to_delete");
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }
}
