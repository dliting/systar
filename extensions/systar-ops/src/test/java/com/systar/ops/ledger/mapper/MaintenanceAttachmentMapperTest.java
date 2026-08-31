package com.systar.ops.ledger.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.ledger.entity.MaintenanceAttachmentEntity;
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
class MaintenanceAttachmentMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private MaintenanceAttachmentMapper mapper;

    private MaintenanceAttachmentEntity buildEntity() {
        MaintenanceAttachmentEntity entity = new MaintenanceAttachmentEntity();
        entity.setMaintenanceId(NONEXISTENT_FK_ID);
        entity.setFileName("photo.jpg");
        entity.setFilePath("/uploads/photo.jpg");
        entity.setFileSize(2048L);
        entity.setUploadedBy(1L);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        MaintenanceAttachmentEntity entity = buildEntity();
        mapper.insert(entity);

        MaintenanceAttachmentEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getFileName()).isEqualTo("photo.jpg");
    }

    @Test
    void update() {
        MaintenanceAttachmentEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setFileName("updated.jpg");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getFileName()).isEqualTo("updated.jpg");
    }

    @Test
    void delete() {
        MaintenanceAttachmentEntity entity = buildEntity();
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
