package com.systar.data.service;

import com.systar.common.service.DeviceLifecycleManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class DeviceLifecycleManagerImpl implements DeviceLifecycleManager {

    private final JdbcTemplate jdbc;

    public DeviceLifecycleManagerImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void updateLifecycleStatus(Integer deviceId, String status) {
        if (deviceId == null) return;
        jdbc.update("UPDATE t_device SET lifecycle_status = ? WHERE id = ?", status, deviceId);
    }

    @Override
    public void updateLastMaintenanceDate(Integer deviceId, LocalDate date) {
        if (deviceId == null) return;
        jdbc.update("UPDATE t_device SET last_maintenance_date = ? WHERE id = ?", date, deviceId);
    }
}
