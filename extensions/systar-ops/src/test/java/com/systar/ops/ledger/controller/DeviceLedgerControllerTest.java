package com.systar.ops.ledger.controller;

import com.systar.ops.ledger.mapper.MaintenanceAttachmentMapper;
import com.systar.ops.ledger.service.DeviceLedgerService;
import com.systar.ops.ledger.service.MaintenanceRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeviceLedgerController#stats()} with plain Mockito
 * (no Spring context): stats must aggregate the grouped lifecycle-status
 * counts instead of fetching the full device list.
 */
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class DeviceLedgerControllerTest {

    private DeviceLedgerService deviceLedgerService;
    private DeviceLedgerController controller;

    @BeforeEach
    void setUp() {
        deviceLedgerService = mock(DeviceLedgerService.class);
        controller = new DeviceLedgerController(deviceLedgerService,
                mock(MaintenanceRecordService.class), mock(MaintenanceAttachmentMapper.class));
    }

    @Test
    void stats_sumsAllGroupsAndMapsLifecycleStatuses() {
        Map<String, Long> grouped = new HashMap<>();
        grouped.put("IN_SERVICE", 3L);
        grouped.put("UNDER_REPAIR", 2L);
        grouped.put("RETIRED", 1L);
        grouped.put("IN_STORAGE", 4L);
        grouped.put(null, 5L);
        when(deviceLedgerService.countByLifecycleStatus()).thenReturn(grouped);

        Map<String, Object> stats = controller.stats();

        assertThat(stats).hasSize(4);
        assertThat(stats.get("total")).isEqualTo(15L);
        assertThat(stats.get("inService")).isEqualTo(3L);
        assertThat(stats.get("underRepair")).isEqualTo(2L);
        assertThat(stats.get("retired")).isEqualTo(1L);
        verify(deviceLedgerService, never()).getDeviceLedger(anyInt(), anyInt(), any(), any());
    }

    @Test
    void stats_defaultsMissingGroupsToZero() {
        when(deviceLedgerService.countByLifecycleStatus()).thenReturn(new HashMap<>());

        Map<String, Object> stats = controller.stats();

        assertThat(stats.get("total")).isEqualTo(0L);
        assertThat(stats.get("inService")).isEqualTo(0L);
        assertThat(stats.get("underRepair")).isEqualTo(0L);
        assertThat(stats.get("retired")).isEqualTo(0L);
        verify(deviceLedgerService, never()).getDeviceLedger(anyInt(), anyInt(), any(), any());
    }
}
