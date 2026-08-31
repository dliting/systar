package com.systar.data.repository;

import com.systar.data.entity.SampleBooleanEntity;
import com.systar.data.entity.SampleExceptionEntity;
import com.systar.data.entity.SampleFloatEntity;
import com.systar.data.entity.SampleIntEntity;
import com.systar.data.mapper.SampleBooleanMapper;
import com.systar.data.mapper.SampleExceptionMapper;
import com.systar.data.mapper.SampleFloatMapper;
import com.systar.data.mapper.SampleIntMapper;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.result.SampleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DataTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class SampleRepositoryImplTest {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private SampleFloatMapper floatMapper;

    @Autowired
    private SampleIntMapper intMapper;

    @Autowired
    private SampleBooleanMapper boolMapper;

    @Autowired
    private SampleExceptionMapper exceptionMapper;

    @Test
    void saveFloat_insertsRecord() {
        long epochMs = System.currentTimeMillis();

        sampleRepository.saveFloat(101, 3.14f, epochMs);

        assertThat(floatMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getMonitorId()).isEqualTo(101);
                    assertThat(entity.getValue()).isEqualTo(3.14f);
                    assertThat(entity.getSampleTime()).isEqualTo(toLocalDateTime(epochMs));
                });
    }

    @Test
    void saveInt_insertsRecord() {
        long epochMs = 1700000000000L;

        sampleRepository.saveInt(202, 42, epochMs);

        assertThat(intMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getMonitorId()).isEqualTo(202);
                    assertThat(entity.getValue()).isEqualTo(42);
                    assertThat(entity.getSampleTime()).isEqualTo(toLocalDateTime(epochMs));
                });
    }

    @Test
    void saveBoolean_insertsRecordTrue() {
        long epochMs = System.currentTimeMillis();

        sampleRepository.saveBoolean(303, true, epochMs);

        assertThat(boolMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getMonitorId()).isEqualTo(303);
                    assertThat(entity.getValue()).isTrue();
                    assertThat(entity.getSampleTime()).isEqualTo(toLocalDateTime(epochMs));
                });
    }

    @Test
    void saveBoolean_insertsRecordFalse() {
        long epochMs = System.currentTimeMillis();

        sampleRepository.saveBoolean(304, false, epochMs);

        assertThat(boolMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getMonitorId()).isEqualTo(304);
                    assertThat(entity.getValue()).isFalse();
                });
    }

    @Test
    void saveException_insertsRecord() {
        long epochMs = System.currentTimeMillis();

        sampleRepository.saveException(404, "Connection timeout", epochMs);

        assertThat(exceptionMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getMonitorId()).isEqualTo(404);
                    assertThat(entity.getError()).isEqualTo("Connection timeout");
                    assertThat(entity.getSampleTime()).isEqualTo(toLocalDateTime(epochMs));
                });
    }

    @Test
    void saveMultipleTypes_independentTables() {
        long now = System.currentTimeMillis();

        sampleRepository.saveFloat(1, 1.0f, now);
        sampleRepository.saveInt(2, 2, now);
        sampleRepository.saveBoolean(3, true, now);
        sampleRepository.saveException(4, "err", now);

        assertThat(floatMapper.selectList(null)).hasSize(1);
        assertThat(intMapper.selectList(null)).hasSize(1);
        assertThat(boolMapper.selectList(null)).hasSize(1);
        assertThat(exceptionMapper.selectList(null)).hasSize(1);
    }

    private static LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(SYSTEM_ZONE).toLocalDateTime();
    }
}
