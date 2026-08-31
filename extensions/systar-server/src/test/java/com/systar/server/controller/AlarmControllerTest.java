package com.systar.server.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.systar.data.entity.AlarmMessageEntity;
import com.systar.data.entity.AlarmRuleEntity;
import com.systar.data.service.AlarmMessageService;
import com.systar.data.service.AlarmRuleService;
import com.systar.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AlarmControllerTest {

    private AlarmController controller;
    private AlarmRuleService alarmRuleService;
    private AlarmMessageService alarmMessageService;

    @BeforeEach
    void setUp() {
        alarmRuleService = mock(AlarmRuleService.class);
        alarmMessageService = mock(AlarmMessageService.class);
        controller = new AlarmController(alarmRuleService, alarmMessageService);
    }

    @Test
    @DisplayName("returns alarm rules list")
    void getAlarmRules() {
        AlarmRuleEntity rule = new AlarmRuleEntity();
        rule.setId(1);
        when(alarmRuleService.list()).thenReturn(List.of(rule));

        Result<List<AlarmRuleEntity>> result = controller.getAlarmRules();

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("returns paginated alarm messages")
    @SuppressWarnings("unchecked")
    void getAlarmMessages() {
        when(alarmMessageService.page(any(Page.class), any())).thenReturn(new Page<>(1, 20));

        Result<Map<String, Object>> result = controller.getAlarmMessages(1, 20, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData()).containsKeys("total", "page", "size", "records");
    }
}
