package com.systar.data.service;

import com.systar.common.dto.DeviceDto;
import com.systar.common.dto.PagedResult;
import com.systar.common.service.DeviceInfoProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeviceInfoProviderImpl implements DeviceInfoProvider {

    private final JdbcTemplate jdbc;

    private static final String SELECT_DEVICE =
        "SELECT id, name, caption, parent, catalog, vendor, purchase_date, " +
        "warranty_date, health_index, model, serial_number, install_date, " +
        "lifecycle_status, responsible_person, department, supplier_contact, " +
        "maintenance_cycle, last_maintenance_date, remark " +
        "FROM t_device";

    public DeviceInfoProviderImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DeviceDto getById(Integer deviceId) {
        var list = jdbc.query(
            SELECT_DEVICE + " WHERE id = ?",
            (rs, i) -> mapDevice(rs), deviceId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public PagedResult<DeviceDto> listDevices(Integer spaceId, Short catalog,
            String lifecycleStatus, int page, int size) {
        var where = new StringBuilder(" WHERE 1=1");
        var params = new ArrayList<>();
        if (spaceId != null) { where.append(" AND parent = ?"); params.add(spaceId); }
        if (catalog != null) { where.append(" AND catalog = ?"); params.add(catalog); }
        if (lifecycleStatus != null) { where.append(" AND lifecycle_status = ?"); params.add(lifecycleStatus); }

        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_device" + where, Long.class, params.toArray());

        var dataParams = new ArrayList<>(params);
        dataParams.add(size);
        dataParams.add((page - 1) * size);
        var records = jdbc.query(
            SELECT_DEVICE + where + " ORDER BY id LIMIT ? OFFSET ?",
            (rs, i) -> mapDevice(rs), dataParams.toArray());

        return new PagedResult<>(records, total, page, size);
    }

    @Override
    public List<DeviceDto> findWarrantyExpiring(LocalDate before) {
        return jdbc.query(
            SELECT_DEVICE + " WHERE warranty_date >= ? AND warranty_date <= ? ORDER BY warranty_date",
            (rs, i) -> mapDevice(rs), LocalDate.now(), before);
    }

    @Override
    public Integer resolveSpaceId(Integer deviceId) {
        if (deviceId == null) return null;
        Integer currentId = deviceId;
        while (currentId != null && currentId > 0) {
            var spaces = jdbc.query(
                "SELECT id FROM t_space WHERE id = ?",
                (rs, i) -> rs.getInt("id"), currentId);
            if (!spaces.isEmpty()) return spaces.get(0);

            var parents = jdbc.query(
                "SELECT parent FROM t_device WHERE id = ?",
                (rs, i) -> {
                    Object v = rs.getObject("parent");
                    return v != null ? ((Number) v).intValue() : null;
                }, currentId);
            currentId = parents.isEmpty() ? null : parents.get(0);
        }
        return null;
    }

    private DeviceDto mapDevice(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeviceDto(
            rs.getInt("id"), rs.getString("name"), rs.getString("caption"),
            (Integer) rs.getObject("parent"),
            safeShort(rs.getObject("catalog")), rs.getString("vendor"),
            rs.getTimestamp("purchase_date") != null
                ? rs.getTimestamp("purchase_date").toLocalDateTime() : null,
            rs.getDate("warranty_date") != null
                ? rs.getDate("warranty_date").toLocalDate() : null,
            safeFloat(rs.getObject("health_index")), rs.getString("model"),
            rs.getString("serial_number"),
            rs.getDate("install_date") != null
                ? rs.getDate("install_date").toLocalDate() : null,
            rs.getString("lifecycle_status"), rs.getString("responsible_person"),
            rs.getString("department"), rs.getString("supplier_contact"),
            (Integer) rs.getObject("maintenance_cycle"),
            rs.getDate("last_maintenance_date") != null
                ? rs.getDate("last_maintenance_date").toLocalDate() : null,
            rs.getString("remark")
        );
    }

    private static Short safeShort(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.shortValue();
        return null;
    }

    private static Float safeFloat(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.floatValue();
        return null;
    }
}
