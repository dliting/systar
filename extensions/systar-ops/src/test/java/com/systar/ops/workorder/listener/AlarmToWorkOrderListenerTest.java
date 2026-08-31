package com.systar.ops.workorder.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.systar.data.event.AlarmPersistedEvent;
import com.systar.monitor.asset.Asset;
import com.systar.monitor.asset.AssetStore;
import com.systar.monitor.asset.Probe;
import com.systar.monitor.asset.type.Device;
import com.systar.monitor.asset.type.DeviceType;
import com.systar.monitor.asset.type.ProbeType;
import com.systar.monitor.asset.type.SpaceType;
import com.systar.ops.test.OpsTestApplication;
import com.systar.ops.workorder.entity.WorkOrderEntity;
import com.systar.ops.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpsTestApplication.class)
@ActiveProfiles("test")
@Transactional
@Rollback
@Timeout(value = 3, unit = TimeUnit.MINUTES)
class AlarmToWorkOrderListenerTest {

    @Autowired
    private AlarmToWorkOrderListener listener;

    @Autowired
    private AssetStore assetStore;

    @Autowired
    @Qualifier("mainJdbcTemplate")
    private JdbcTemplate jdbc;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @BeforeEach
    void setUp() {
        assetStore.clear();
        assetStore.createRoot(new SpaceType("root"), "root");
    }

    @Test
    void onAlarmPersisted_resolvesProbeAssetToDevice() {
        addDeviceAsset(3101);
        addProbeAsset(4101, 3101);
        insertDeviceRow(3101);

        listener.onAlarmPersisted(new AlarmPersistedEvent(this, 5101, 3, 4101));

        List<WorkOrderEntity> orders = workOrderMapper.selectList(null);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getDeviceId()).isEqualTo(3101);
        assertThat(orders.get(0).getPriority()).isEqualTo(3);
    }

    @Test
    void onAlarmPersisted_usesDeviceAssetDirectly() {
        addDeviceAsset(3102);
        insertDeviceRow(3102);

        listener.onAlarmPersisted(new AlarmPersistedEvent(this, 5102, 2, 3102));

        List<WorkOrderEntity> orders = workOrderMapper.selectList(null);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getDeviceId()).isEqualTo(3102);
    }

    @Test
    void onAlarmPersisted_skipsNonTriggerLevel() {
        addDeviceAsset(3103);
        insertDeviceRow(3103);

        listener.onAlarmPersisted(new AlarmPersistedEvent(this, 5103, 1, 3103));

        assertThat(workOrderMapper.selectCount(null)).isZero();
    }

    @Test
    void onAlarmPersisted_skipsDuplicateAlarmMessage() {
        addDeviceAsset(3104);
        insertDeviceRow(3104);

        listener.onAlarmPersisted(new AlarmPersistedEvent(this, 5104, 3, 3104));
        listener.onAlarmPersisted(new AlarmPersistedEvent(this, 5104, 3, 3104));

        assertThat(workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getAlarmMessageId, 5104))).isEqualTo(1);
    }

    @Test
    void onAlarmPersisted_skipsUnresolvedAsset() {
        listener.onAlarmPersisted(new AlarmPersistedEvent(this, 5105, 3, 9999));

        assertThat(workOrderMapper.selectCount(null)).isZero();
    }

    private void addDeviceAsset(int id) {
        com.systar.monitor.asset.type.Device device = new Device();
        device.init(new DeviceType("deviceType"), id, "device_" + id);
        assetStore.addAsset(device);
    }

    private void addProbeAsset(int id, int parentId) {
        Probe probe = new Probe();
        probe.init(new ProbeType("probeType"), id, "probe_" + id);
        probe.setParentId(parentId);
        assetStore.addAsset(probe);
    }

    private void insertDeviceRow(int id) {
        jdbc.update(
                "INSERT INTO t_device (id, name, parent, lifecycle_status, warranty_date) VALUES (?, ?, ?, ?, ?)",
                id, "device_" + id, 10, "IN_SERVICE", LocalDate.now().plusYears(1));
    }
}
