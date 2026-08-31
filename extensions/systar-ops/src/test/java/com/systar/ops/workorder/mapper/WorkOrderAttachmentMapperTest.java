package com.systar.ops.workorder.mapper;

import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.workorder.entity.WorkOrderAttachmentEntity;
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
class WorkOrderAttachmentMapperTest {

    private static final long NONEXISTENT_FK_ID = 9999L;

    @Autowired
    private WorkOrderAttachmentMapper mapper;

    private WorkOrderAttachmentEntity buildEntity() {
        WorkOrderAttachmentEntity entity = new WorkOrderAttachmentEntity();
        entity.setWorkOrderId(NONEXISTENT_FK_ID);
        entity.setFileName("test.pdf");
        entity.setFilePath("/uploads/test.pdf");
        entity.setFileSize(1024L);
        entity.setUploadedBy(1L);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    @Test
    void insertAndFindById() {
        WorkOrderAttachmentEntity entity = buildEntity();
        mapper.insert(entity);

        WorkOrderAttachmentEntity found = mapper.selectById(entity.getId());
        assertThat(found).isNotNull();
        assertThat(found.getFileName()).isEqualTo("test.pdf");
    }

    @Test
    void update() {
        WorkOrderAttachmentEntity entity = buildEntity();
        mapper.insert(entity);

        entity.setFileName("updated.pdf");
        mapper.updateById(entity);

        assertThat(mapper.selectById(entity.getId()).getFileName()).isEqualTo("updated.pdf");
    }

    @Test
    void delete() {
        WorkOrderAttachmentEntity entity = buildEntity();
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
