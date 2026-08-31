package com.systar.data.mapper;

import com.systar.data.entity.ErrorMessageLogEntity;
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
class ErrorMessageLogMapperTest {

    @Autowired
    private ErrorMessageLogMapper mapper;

    @Test
    void insertAndFindById() {
        ErrorMessageLogEntity entity = new ErrorMessageLogEntity();
        entity.setAlarmRuleId(1);
        entity.setMonitorId(100);
        entity.setMonitorName("TempSensor");
        entity.setError("Over threshold");
        entity.setValue("99.5");
        entity.setState(1);
        entity.setEventRankId(2);
        entity.setLogTime(LocalDateTime.now());

        assertThat(mapper.insert(entity)).isEqualTo(1);

        ErrorMessageLogEntity found = mapper.selectById(entity.getId());
        assertThat(found.getMonitorId()).isEqualTo(100);
        assertThat(found.getError()).isEqualTo("Over threshold");
        assertThat(found.getValue()).isEqualTo("99.5");
    }

    @Test
    void delete() {
        ErrorMessageLogEntity entity = new ErrorMessageLogEntity();
        entity.setAlarmRuleId(0);
        entity.setMonitorId(200);
        entity.setMonitorName("ToDelete");
        entity.setError("Error");
        entity.setState(1);
        entity.setEventRankId(1);
        entity.setLogTime(LocalDateTime.now());
        mapper.insert(entity);

        assertThat(mapper.deleteById(entity.getId())).isEqualTo(1);
        assertThat(mapper.selectById(entity.getId())).isNull();
    }
}
