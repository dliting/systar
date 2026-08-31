package com.systar.data.repository;

import com.systar.data.entity.LinkageLogEntity;
import com.systar.data.mapper.LinkageLogMapper;
import com.systar.data.test.DataTestApplication;
import com.systar.monitor.linkage.LinkageRepository;
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
class LinkageRepositoryImplTest {

    @Autowired
    private LinkageRepository linkageRepository;

    @Autowired
    private LinkageLogMapper linkageLogMapper;

    @Test
    void saveLinkageLog_success_insertsRecord() {
        linkageRepository.saveLinkageLog(1, 10, 20, "1", true);

        assertThat(linkageLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getRuleId()).isEqualTo(1);
                    assertThat(entity.getCauseMonitorId()).isEqualTo(10);
                    assertThat(entity.getEffectMonitorId()).isEqualTo(20);
                    assertThat(entity.getEffectCommand()).isEqualTo("1");
                    assertThat(entity.getSuccess()).isTrue();
                    assertThat(entity.getTriggerTime()).isNotNull();
                });
    }

    @Test
    void saveLinkageLog_failure_insertsRecord() {
        linkageRepository.saveLinkageLog(2, 30, 40, "0", false);

        assertThat(linkageLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getRuleId()).isEqualTo(2);
                    assertThat(entity.getCauseMonitorId()).isEqualTo(30);
                    assertThat(entity.getEffectMonitorId()).isEqualTo(40);
                    assertThat(entity.getEffectCommand()).isEqualTo("0");
                    assertThat(entity.getSuccess()).isFalse();
                });
    }

    @Test
    void saveLinkageLog_nonNumericCommand() {
        linkageRepository.saveLinkageLog(1, 10, 20, "toggle", true);

        assertThat(linkageLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getEffectCommand()).isEqualTo("toggle");
                });
    }

    @Test
    void saveLinkageLog_nullCommand() {
        linkageRepository.saveLinkageLog(1, 10, 20, null, true);

        assertThat(linkageLogMapper.selectList(null))
                .hasSize(1)
                .first()
                .satisfies(entity -> {
                    assertThat(entity.getEffectCommand()).isNull();
                });
    }

    @Test
    void saveLinkageLog_multipleRecords_allPersisted() {
        linkageRepository.saveLinkageLog(1, 10, 20, "1", true);
        linkageRepository.saveLinkageLog(2, 30, 40, "0", false);
        linkageRepository.saveLinkageLog(3, 50, 60, "-1", true);

        assertThat(linkageLogMapper.selectList(null)).hasSize(3);
    }
}
