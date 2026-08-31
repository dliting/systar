package com.systar.data.mapper;

import com.systar.data.entity.SampleExceptionEntity;
import com.systar.data.test.DataTestApplication;
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

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SampleExceptionMapperTest {

    @Autowired
    private SampleExceptionMapper mapper;

    @Test
    void insertAndFindById() {
        SampleExceptionEntity entity = new SampleExceptionEntity();
        entity.setMonitorId(10);
        entity.setError("Connection timeout");
        entity.setSampleTime(LocalDateTime.now());

        assertThat(mapper.insert(entity)).isEqualTo(1);

        SampleExceptionEntity found = mapper.selectById(entity.getId());
        assertThat(found.getMonitorId()).isEqualTo(10);
        assertThat(found.getError()).isEqualTo("Connection timeout");
    }

    @Test
    void selectList_returnsInsertedRecords() {
        SampleExceptionEntity e1 = new SampleExceptionEntity();
        e1.setMonitorId(1);
        e1.setError("Error A");
        e1.setSampleTime(LocalDateTime.now());
        mapper.insert(e1);

        SampleExceptionEntity e2 = new SampleExceptionEntity();
        e2.setMonitorId(2);
        e2.setError("Error B");
        e2.setSampleTime(LocalDateTime.now());
        mapper.insert(e2);

        assertThat(mapper.selectList(null)).hasSizeGreaterThanOrEqualTo(2);
    }
}
