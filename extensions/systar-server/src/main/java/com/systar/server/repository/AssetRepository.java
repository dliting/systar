package com.systar.server.repository;

import com.systar.common.util.TimeSpan;
import com.systar.monitor.asset.*;
import com.systar.monitor.asset.type.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Unified asset repository using {@link JdbcTemplate} for all DB access
 * and {@link AssetStore} for type resolution.
 * <p>
 * Replaces the older {@code AssetEntityConverter} + Mapper-interface design.
 */
@Repository
public class AssetRepository {

    private static final Logger log = LoggerFactory.getLogger(AssetRepository.class);

    private final JdbcTemplate jdbc;
    private final AssetStore store;
    private final TypeResolver typeResolver;

    public AssetRepository(JdbcTemplate jdbc, AssetStore store) {
        this.jdbc = jdbc;
        this.store = store;
        this.typeResolver = new TypeResolver(store);
    }

    // ======================== Row Records ========================

    public record SpaceRow(int id, String name, String caption, int parentId,
                            Integer area, int sequence, int showInClient, String typeName) {}

    public record DeviceRow(int id, String name, String caption, int parentId,
                             Short catalog, String vendor,
                             LocalDateTime purchaseDate, LocalDate warrantyDate,
                             Float healthIndex, String model, String serialNumber,
                             LocalDate installDate, String lifecycleStatus,
                             String responsiblePerson, String department,
                             String supplierContact, Integer maintenanceCycle,
                             LocalDate lastMaintenanceDate, String remark,
                             String typeName) {}

    public record ServiceRow(int id, String name, String caption, int parentId,
                              Integer mode, String driverClass,
                              Integer maxConnections, String typeName) {}

    public record ProbeRow(int id, String name, String caption, int parentId,
                            Integer serviceId, String unit,
                            String detectInterval, String savingInterval,
                            String warnCondition, String transform,
                            Short catalog, String dataType,
                            Float minValue, Float maxValue, String typeName,
                            Integer isVirtual, String expression, String dependsOn) {}

    public record ControlRow(int id, String name, String caption, int parentId,
                              Integer serviceId, String unit,
                              String detectInterval, String savingInterval,
                              String warnCondition, String transform,
                              Short catalog, Integer refreshDelay,
                              Float minValue, Float maxValue, String typeName) {}

    // ======================== UpdateFields Records ========================

    public record SpaceUpdateFields(String name, String caption, Integer parentId,
                                    Integer area, Integer sequence,
                                    Integer showInClient, String typeName) {}

    public record DeviceUpdateFields(String name, String caption, Integer parentId,
                                     Short catalog, String vendor,
                                     LocalDateTime purchaseDate, LocalDate warrantyDate,
                                     Float healthIndex, String model, String serialNumber,
                                     LocalDate installDate, String lifecycleStatus,
                                     String responsiblePerson, String department,
                                     String supplierContact, Integer maintenanceCycle,
                                     LocalDate lastMaintenanceDate, String remark,
                                     String typeName) {}

    public record ServiceUpdateFields(String name, String caption, Integer parentId,
                                      Integer mode, String driverClass,
                                      Integer maxConnections, String typeName) {}

    public record ProbeUpdateFields(String name, String caption, Integer parentId,
                                    Integer serviceId, String unit,
                                    String detectInterval, String savingInterval,
                                    String warnCondition, String transform,
                                    Short catalog, String dataType,
                                    Float minValue, Float maxValue, String typeName,
                                    Integer isVirtual, String expression, String dependsOn) {}

    public record ControlUpdateFields(String name, String caption, Integer parentId,
                                      Integer serviceId, String unit,
                                      String detectInterval, String savingInterval,
                                      String warnCondition, String transform,
                                      Short catalog, Integer refreshDelay,
                                      Float minValue, Float maxValue, String typeName) {}

    // ======================== TypeResolver ========================

    static class TypeResolver {
        private final AssetStore store;

        TypeResolver(AssetStore store) {
            this.store = store;
        }

        SpaceType resolveSpaceType(String typeName, int id, String name) {
            SpaceType type = store.getSpaceTypes().find(typeName);
            if (type != null) {
                if (type.isAbstractType()) {
                    throw new AssetException("Space '%s' (id=%d) references abstract type '%s'.",
                            name, id, typeName);
                }
                return type;
            }
            if (!isFallback(typeName)) {
                throw new AssetException("Space '%s' (id=%d) references type '%s' which is not registered.",
                        name, id, typeName);
            }
            return new SpaceType("space-" + id);
        }

        DeviceType resolveDeviceType(String typeName, int id, String name) {
            DeviceType type = store.getDeviceTypes().find(typeName);
            if (type != null) {
                if (type.isAbstractType()) {
                    throw new AssetException("Device '%s' (id=%d) references abstract type '%s'.",
                            name, id, typeName);
                }
                return type;
            }
            if (!isFallback(typeName)) {
                throw new AssetException("Device '%s' (id=%d) references type '%s' which is not registered.",
                        name, id, typeName);
            }
            return new DeviceType("device-" + id);
        }

        ServiceType resolveServiceType(String typeName, int id, String name) {
            ServiceType type = store.getServiceTypes().find(typeName);
            if (type != null) {
                if (type.isAbstractType()) {
                    throw new AssetException("Service '%s' (id=%d) references abstract type '%s'.",
                            name, id, typeName);
                }
                return type;
            }
            if (!isFallback(typeName)) {
                throw new AssetException("Service '%s' (id=%d) references type '%s' which is not registered.",
                        name, id, typeName);
            }
            return new ServiceType("service-" + id);
        }

        ProbeType resolveProbeType(String typeName, int id, String name) {
            ProbeType type = store.getProbeTypes().find(typeName);
            if (type != null) {
                if (type.isAbstractType()) {
                    throw new AssetException("Probe '%s' (id=%d) references abstract type '%s'.",
                            name, id, typeName);
                }
                return type;
            }
            if (!isFallback(typeName)) {
                throw new AssetException("Probe '%s' (id=%d) references type '%s' which is not registered.",
                        name, id, typeName);
            }
            return new ProbeType("probe-" + id);
        }

        VirtualProbeType resolveVirtualProbeType(String typeName, int id, String name,
                                                  String expression, String dependsOn) {
            VirtualProbeType type;
            ProbeType base = store.getProbeTypes().find(typeName);
            if (base != null) {
                type = new VirtualProbeType(base.getName());
                type.setDetectInterval(base.getDetectInterval());
                type.setSavingInterval(base.getSavingInterval());
                type.setUnit(base.getUnit());
                type.setWarnCondition(base.getWarnCondition());
                type.setTransform(base.getTransform());
                type.setSource(base.getSource());
                type.setDataType(base.getDataType());
            } else if (!isFallback(typeName)) {
                throw new AssetException("VirtualProbe '%s' (id=%d) references type '%s' which is not registered.",
                        name, id, typeName);
            } else {
                type = new VirtualProbeType("vprobe-" + id);
            }
            type.setExpression(expression);
            type.setDependsOn(dependsOn);
            return type;
        }

        ControlType resolveControlType(String typeName, int id, String name) {
            ControlType type = store.getControlTypes().find(typeName);
            if (type != null) {
                if (type.isAbstractType()) {
                    throw new AssetException("Control '%s' (id=%d) references abstract type '%s'.",
                            name, id, typeName);
                }
                return type;
            }
            if (!isFallback(typeName)) {
                throw new AssetException("Control '%s' (id=%d) references type '%s' which is not registered.",
                        name, id, typeName);
            }
            return new ControlType("ctrl-" + id);
        }

        boolean matchesSourceType(String expected, AssetType actualType) {
            AssetType current = actualType;
            while (current != null) {
                if (expected.equals(current.getName())) {
                    return true;
                }
                current = current.getSuperType();
            }
            return false;
        }

        private static boolean isFallback(String typeName) {
            return typeName == null || typeName.isBlank();
        }
    }

    // ======================== SQL Fragments ========================

    private static final String SELECT_SPACE =
            "SELECT id, name, caption, parent, area, sequence, show_in_client, type_name FROM t_space";
    private static final String SELECT_DEVICE =
            "SELECT id, name, caption, parent, catalog, vendor, purchase_date, warranty_date, "
                    + "health_index, model, serial_number, install_date, lifecycle_status, "
                    + "responsible_person, department, supplier_contact, maintenance_cycle, "
                    + "last_maintenance_date, remark, type_name FROM t_device";
    private static final String SELECT_SERVICE =
            "SELECT id, name, caption, parent, mode, driver_class, max_connections, type_name FROM t_service";
    private static final String SELECT_PROBE =
            "SELECT id, name, caption, parent, source, unit, time_interval, saving_interval, "
                    + "warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name, "
                    + "is_virtual, expression, depends_on FROM t_probe";
    private static final String SELECT_CONTROL =
            "SELECT id, name, caption, parent, source, unit, time_interval, saving_interval, "
                    + "catalog, transform, warn_cond, refresh_delay, min_value, max_value, type_name FROM t_control";

    // ======================== Row Mappers (ResultSet → Record) ========================

    private SpaceRow mapSpaceRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SpaceRow(
                rs.getInt("id"), rs.getString("name"), rs.getString("caption"),
                rs.getInt("parent"), (Integer) rs.getObject("area"),
                rs.getInt("sequence"), rs.getInt("show_in_client"),
                rs.getString("type_name"));
    }

    private DeviceRow mapDeviceRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DeviceRow(
                rs.getInt("id"), rs.getString("name"), rs.getString("caption"),
                rs.getInt("parent"), shortOrNull(rs, "catalog"),
                rs.getString("vendor"),
                rs.getObject("purchase_date", LocalDateTime.class),
                rs.getObject("warranty_date", LocalDate.class),
                floatOrNull(rs, "health_index"), rs.getString("model"),
                rs.getString("serial_number"),
                rs.getObject("install_date", LocalDate.class),
                rs.getString("lifecycle_status"), rs.getString("responsible_person"),
                rs.getString("department"), rs.getString("supplier_contact"),
                (Integer) rs.getObject("maintenance_cycle"),
                rs.getObject("last_maintenance_date", LocalDate.class),
                rs.getString("remark"), rs.getString("type_name"));
    }

    private ServiceRow mapServiceRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ServiceRow(
                rs.getInt("id"), rs.getString("name"), rs.getString("caption"),
                rs.getInt("parent"), (Integer) rs.getObject("mode"),
                rs.getString("driver_class"), (Integer) rs.getObject("max_connections"),
                rs.getString("type_name"));
    }

    private ProbeRow mapProbeRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProbeRow(
                rs.getInt("id"), rs.getString("name"), rs.getString("caption"),
                rs.getInt("parent"), (Integer) rs.getObject("source"),
                rs.getString("unit"), rs.getString("time_interval"),
                rs.getString("saving_interval"), rs.getString("warn_cond"),
                rs.getString("transform"), shortOrNull(rs, "catalog"),
                dataTypeOrdinalToName((Integer) rs.getObject("monitor_kind")),
                floatOrNull(rs, "min_value"), floatOrNull(rs, "max_value"),
                rs.getString("type_name"),
                (Integer) rs.getObject("is_virtual"),
                rs.getString("expression"),
                rs.getString("depends_on"));
    }

    private ControlRow mapControlRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ControlRow(
                rs.getInt("id"), rs.getString("name"), rs.getString("caption"),
                rs.getInt("parent"), (Integer) rs.getObject("source"),
                rs.getString("unit"), rs.getString("time_interval"),
                rs.getString("saving_interval"), rs.getString("warn_cond"),
                rs.getString("transform"), shortOrNull(rs, "catalog"),
                (Integer) rs.getObject("refresh_delay"),
                floatOrNull(rs, "min_value"), floatOrNull(rs, "max_value"),
                rs.getString("type_name"));
    }

    // ======================== Batch Loading Methods ========================

    public List<Space> findAllSpaces() {
        return jdbc.query(SELECT_SPACE, (rs, i) -> toSpace(mapSpaceRow(rs)));
    }

    public List<Device> findAllDevices() {
        return jdbc.query(SELECT_DEVICE, (rs, i) -> toDevice(mapDeviceRow(rs)));
    }

    public List<MonitorService> findAllServices(Map<Integer, MonitorService> serviceIndex) {
        return jdbc.query(SELECT_SERVICE,
                (rs, i) -> toService(mapServiceRow(rs), serviceIndex));
    }

    public List<Probe> findAllProbes(Map<Integer, MonitorService> serviceIndex) {
        return jdbc.query(SELECT_PROBE,
                (rs, i) -> toProbe(mapProbeRow(rs), serviceIndex));
    }

    public List<Control> findAllControls(Map<Integer, MonitorService> serviceIndex) {
        return jdbc.query(SELECT_CONTROL,
                (rs, i) -> toControl(mapControlRow(rs), serviceIndex));
    }

    // ======================== Assembly Methods ========================

    private Space toSpace(SpaceRow row) {
        SpaceType type = typeResolver.resolveSpaceType(row.typeName(), row.id(), row.name());
        boolean isFallback = isFallbackTypeName(row.typeName());

        if (isFallback) {
            if (row.area() != null) type.setArea(row.area().doubleValue());
            type.setSequence(row.sequence());
        }

        Space space = new Space();
        space.init(type, row.id(), row.name());
        setCommonProps(space, row.caption(), row.parentId());

        if (!isFallback) {
            if (row.area() != null) space.setMetadata("area", row.area().doubleValue());
            space.setMetadata("sequence", row.sequence());
        }

        applyDefaults(space);
        return space;
    }

    private Device toDevice(DeviceRow row) {
        DeviceType type = typeResolver.resolveDeviceType(row.typeName(), row.id(), row.name());
        boolean isFallback = isFallbackTypeName(row.typeName());

        if (isFallback) {
            if (row.catalog() != null) type.setCatalog(row.catalog());
            if (row.vendor() != null) type.setVendor(row.vendor());
        }

        Device device = new Device();
        device.init(type, row.id(), row.name());
        setCommonProps(device, row.caption(), row.parentId());

        if (!isFallback) {
            if (row.catalog() != null) device.setMetadata("catalog", row.catalog());
            if (row.vendor() != null) device.setMetadata("vendor", row.vendor());
        }

        if (row.purchaseDate() != null) device.setMetadata("purchaseDate", row.purchaseDate());
        if (row.warrantyDate() != null) device.setMetadata("warrantyDate", row.warrantyDate());
        if (row.model() != null) device.setMetadata("model", row.model());
        if (row.serialNumber() != null) device.setMetadata("serialNumber", row.serialNumber());
        if (row.installDate() != null) device.setMetadata("installDate", row.installDate());
        if (row.lifecycleStatus() != null) device.setMetadata("lifecycleStatus", row.lifecycleStatus());
        if (row.responsiblePerson() != null) device.setMetadata("responsiblePerson", row.responsiblePerson());
        if (row.department() != null) device.setMetadata("department", row.department());
        if (row.supplierContact() != null) device.setMetadata("supplierContact", row.supplierContact());
        if (row.maintenanceCycle() != null) device.setMetadata("maintenanceCycle", row.maintenanceCycle());
        if (row.lastMaintenanceDate() != null) device.setMetadata("lastMaintenanceDate", row.lastMaintenanceDate());
        if (row.remark() != null) device.setMetadata("remark", row.remark());
        if (row.healthIndex() != null) device.setHealthIndex(row.healthIndex());

        applyDefaults(device);
        return device;
    }

    private MonitorService toService(ServiceRow row, Map<Integer, MonitorService> serviceIndex) {
        ServiceType type = typeResolver.resolveServiceType(row.typeName(), row.id(), row.name());
        MonitorService service = createServiceInstance(row, type);
        service.init(type, row.id(), row.name());
        setCommonProps(service, row.caption(), row.parentId());
        if (row.maxConnections() != null && service instanceof ActiveService active) {
            active.setMaxConnections(row.maxConnections());
        }
        serviceIndex.put(row.id(), service);
        applyDefaults(service);
        return service;
    }

    private Probe toProbe(ProbeRow row, Map<Integer, MonitorService> serviceIndex) {
        Probe probe = buildProbeForFind(row);
        resolveSourceFromMap(probe, row.serviceId(), serviceIndex, "Probe", row.name());
        validateServiceTypeMatch(probe, "Probe", row.id(), row.name());
        applyDefaults(probe);
        return probe;
    }

    private Control toControl(ControlRow row, Map<Integer, MonitorService> serviceIndex) {
        Control control = buildControlForFind(row);
        resolveSourceFromMap(control, row.serviceId(), serviceIndex, "Control", row.name());
        validateServiceTypeMatch(control, "Control", row.id(), row.name());
        applyDefaults(control);
        return control;
    }

    /**
     * Shared Monitor property application — handles isFallback logic,
     * interval parsing, and metadata storage.
     */
    private void applyMonitorCommon(Monitor<?> monitor, String caption, int parentId,
                                    String detectIntervalRaw, String savingIntervalRaw,
                                    String warnCondition, String transform,
                                    String typeName, Float minValue, Float maxValue, String unit) {
        setCommonProps(monitor, caption, parentId);

        if (detectIntervalRaw != null) {
            monitor.setDetectInterval(parseInterval(detectIntervalRaw));
        }
        if (savingIntervalRaw != null) {
            monitor.setSavingInterval(parseInterval(savingIntervalRaw));
        }

        boolean isFallback = isFallbackTypeName(typeName);
        MonitorType mType = (MonitorType) monitor.getType();
        if (isFallback) {
            if (warnCondition != null) mType.setWarnCondition(warnCondition);
            if (transform != null) mType.setTransform(transform);
        }

        if (minValue != null) monitor.setMetadata("minValue", minValue);
        if (maxValue != null) monitor.setMetadata("maxValue", maxValue);
        if (unit != null) monitor.setMetadata("unit", unit);

        if (!isFallback) {
            if (warnCondition != null) monitor.setMetadata("warnCondition", warnCondition);
            if (transform != null) monitor.setMetadata("transform", transform);
        }
    }

    // ======================== Driver Instantiation ========================

    private Probe createProbeInstance(ProbeType type, String name) {
        String driverClass = type.getRelatedClass();
        if (driverClass != null && !driverClass.isBlank()) {
            try {
                Class<?> cls = Class.forName(driverClass);
                return (Probe) cls.getDeclaredConstructor().newInstance();
            } catch (AssetException e) {
                throw e;
            } catch (Exception e) {
                throw new AssetException(e,
                        "Cannot instantiate probe driver class '%s' for probe '%s'.",
                        driverClass, name);
            }
        }
        return new Probe();
    }

    private Control createControlInstance(ControlType type, String name) {
        String driverClass = type.getRelatedClass();
        if (driverClass != null && !driverClass.isBlank()) {
            try {
                Class<?> cls = Class.forName(driverClass);
                return (Control) cls.getDeclaredConstructor().newInstance();
            } catch (AssetException e) {
                throw e;
            } catch (Exception e) {
                throw new AssetException(e,
                        "Cannot instantiate control driver class '%s' for control '%s'.",
                        driverClass, name);
            }
        }
        return new Control() {
            @Override
            public void execute(String command) {
            }
        };
    }

    private MonitorService createServiceInstance(ServiceRow row, ServiceType type) {
        if (row.mode() == null) {
            throw new AssetException("Service '%s' (id=%d) has null mode; ACTIVE or PASSIVE is required.",
                    row.name(), row.id());
        }
        MonitorMode mode = MonitorMode.fromCode(row.mode());

        String driverClass = type.getRelatedClass();
        if (driverClass == null || driverClass.isBlank()) {
            driverClass = row.driverClass();
        }

        if (driverClass != null && !driverClass.isBlank()) {
            try {
                Class<?> cls = Class.forName(driverClass);
                MonitorService service = (MonitorService) cls.getDeclaredConstructor().newInstance();
                if (service.getMode() != mode) {
                    throw new AssetException(
                            "Driver class '%s' for service '%s' has mode %s but entity specifies %s.",
                            driverClass, row.name(), service.getMode(), mode);
                }
                return service;
            } catch (AssetException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AssetException(ex,
                        "Cannot instantiate driver class '%s' for service '%s'.",
                        driverClass, row.name());
            }
        }

        if (mode == MonitorMode.PASSIVE) {
            return new PassiveService() {
                @Override
                public void start() {
                }

                @Override
                public void stop() {
                }
            };
        } else {
            return new ActiveService() {
                @Override
                public void start() {
                }

                @Override
                public void stop() {
                }

                @Override
                public MonitorConnection createConnection() {
                    return null;
                }
            };
        }
    }

    private void validateServiceTypeMatch(Monitor<?> monitor, String kind, int entityId, String entityName) {
        MonitorType type = (MonitorType) monitor.getType();
        String expectedSourceType = type.getSource();
        if (expectedSourceType == null || expectedSourceType.isBlank()) return;

        MonitorService source = monitor.getSource();
        if (source == null) return;

        if (!typeResolver.matchesSourceType(expectedSourceType, source.getType())) {
            throw new AssetException(
                    "%s '%s' (id=%d) has type '%s' which requires source service type '%s', "
                            + "but the assigned source '%s' (id=%d) is of type '%s'.",
                    kind, entityName, entityId,
                    type.getName(), expectedSourceType,
                    source.getName(), source.getId(), source.getType().getName());
        }
    }

    // ======================== Helper Methods ========================

    private void setCommonProps(Asset<?> asset, String caption, int parentId) {
        if (caption != null && !caption.isBlank()) asset.setCaption(caption);
        asset.setParentId(parentId);
    }

    private void applyDefaults(Asset<?> asset) {
        AssetType type = asset.getType();
        if (type == null || type.getProperties() == null) return;
        for (AssetTypeProperty prop : type.getProperties()) {
            if (prop.getDefaultValue() != null && asset.getMetadata(prop.getName()) == null) {
                asset.setMetadata(prop.getName(), prop.getDefaultValue());
            }
        }
    }

    private static boolean isFallbackTypeName(String typeName) {
        return typeName == null || typeName.isBlank();
    }

    /**
     * Parses a time interval string. Throws on unparseable input --
     * a configured interval that cannot be parsed indicates a data error.
     */
    public static TimeSpan parseInterval(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return TimeSpan.parse(text.trim());
        } catch (IllegalArgumentException e) {
            String[] parts = text.trim().split(":");
            if (parts.length == 3) {
                try {
                    long hours = Long.parseLong(parts[0]);
                    long minutes = Long.parseLong(parts[1]);
                    long seconds = Long.parseLong(parts[2]);
                    return TimeSpan.ofSeconds(hours * 3600 + minutes * 60 + seconds);
                } catch (NumberFormatException e2) {
                    throw new AssetException("Cannot parse interval '%s': not a valid HH:mm:ss format.", text);
                }
            }
            throw new AssetException("Cannot parse interval '%s': expected format like '10s', '5m', or 'HH:mm:ss'.", text);
        }
    }

    private static String dataTypeOrdinalToName(Integer ordinal) {
        if (ordinal == null) return null;
        if (ordinal >= 0 && ordinal < DataType.values().length) {
            return DataType.values()[ordinal].name();
        }
        return null;
    }

    private static Integer dataTypeNameToOrdinal(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return DataType.valueOf(name).ordinal();
        } catch (IllegalArgumentException e) {
            log.warn("Unknown DataType '{}' — saving as NULL in database.", name);
            return null;
        }
    }

    private void resolveSourceFromMap(Monitor<?> monitor, Integer serviceId,
                                      Map<Integer, MonitorService> serviceIndex,
                                      String kind, String name) {
        if (serviceId == null) return;
        MonitorService source = serviceIndex.get(serviceId);
        if (source == null) {
            throw new AssetException("%s '%s' references service id %d, but no service with that id was loaded.",
                    kind, name, serviceId);
        }
        monitor.setSource(source);
    }

    // ======================== CRUD: Insert ========================

    public void insertSpace(SpaceRow row) {
        jdbc.update(
                "INSERT INTO t_space (id, name, caption, parent, area, sequence, show_in_client, type_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                row.id(), row.name(), row.caption(), row.parentId(),
                row.area(), row.sequence(), row.showInClient(), row.typeName());
    }

    public void insertDevice(DeviceRow row) {
        jdbc.update(
                "INSERT INTO t_device (id, name, caption, parent, catalog, vendor, purchase_date, warranty_date, "
                        + "health_index, model, serial_number, install_date, lifecycle_status, "
                        + "responsible_person, department, supplier_contact, maintenance_cycle, "
                        + "last_maintenance_date, remark, type_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.id(), row.name(), row.caption(), row.parentId(),
                row.catalog(), row.vendor(), row.purchaseDate(), row.warrantyDate(),
                row.healthIndex(), row.model(), row.serialNumber(), row.installDate(),
                row.lifecycleStatus(), row.responsiblePerson(), row.department(),
                row.supplierContact(), row.maintenanceCycle(), row.lastMaintenanceDate(),
                row.remark(), row.typeName());
    }

    public void insertService(ServiceRow row) {
        jdbc.update(
                "INSERT INTO t_service (id, name, caption, parent, mode, driver_class, max_connections, type_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                row.id(), row.name(), row.caption(), row.parentId(),
                row.mode(), row.driverClass(), row.maxConnections(), row.typeName());
    }

    public void insertProbe(ProbeRow row) {
        jdbc.update(
                "INSERT INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, "
                        + "warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name, "
                        + "is_virtual, expression, depends_on) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.id(), row.name(), row.caption(), row.parentId(),
                row.serviceId(), row.unit(), row.detectInterval(), row.savingInterval(),
                row.warnCondition(), row.transform(), row.catalog(),
                dataTypeNameToOrdinal(row.dataType()),
                row.minValue(), row.maxValue(), row.typeName(),
                row.isVirtual(), row.expression(), row.dependsOn());
    }

    public void insertControl(ControlRow row) {
        jdbc.update(
                "INSERT INTO t_control (id, name, caption, parent, source, unit, time_interval, saving_interval, "
                        + "catalog, transform, warn_cond, refresh_delay, min_value, max_value, type_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                row.id(), row.name(), row.caption(), row.parentId(),
                row.serviceId(), row.unit(), row.detectInterval(), row.savingInterval(),
                row.catalog(), row.transform(), row.warnCondition(),
                row.refreshDelay(), row.minValue(), row.maxValue(), row.typeName());
    }

    // ======================== CRUD: Update ========================

    public void updateSpace(int id, SpaceUpdateFields f) {
        SpaceRow old = findSpaceRowById(id);
        if (old == null) throw new AssetException("Space not found: " + id);
        jdbc.update(
                "UPDATE t_space SET name=?, caption=?, parent=?, area=?, sequence=?, show_in_client=?, type_name=? WHERE id=?",
                coalesce(f.name(), old.name()), coalesce(f.caption(), old.caption()),
                coalesce(f.parentId(), old.parentId()), coalesce(f.area(), old.area()),
                coalesce(f.sequence(), old.sequence()), coalesce(f.showInClient(), old.showInClient()),
                coalesce(f.typeName(), old.typeName()), id);
    }

    public void updateDevice(int id, DeviceUpdateFields f) {
        DeviceRow old = findDeviceRowById(id);
        if (old == null) throw new AssetException("Device not found: " + id);
        jdbc.update(
                "UPDATE t_device SET name=?, caption=?, parent=?, catalog=?, vendor=?, purchase_date=?, "
                        + "warranty_date=?, health_index=?, model=?, serial_number=?, install_date=?, "
                        + "lifecycle_status=?, responsible_person=?, department=?, supplier_contact=?, "
                        + "maintenance_cycle=?, last_maintenance_date=?, remark=?, type_name=? WHERE id=?",
                coalesce(f.name(), old.name()), coalesce(f.caption(), old.caption()),
                coalesce(f.parentId(), old.parentId()), coalesce(f.catalog(), old.catalog()),
                coalesce(f.vendor(), old.vendor()), coalesce(f.purchaseDate(), old.purchaseDate()),
                coalesce(f.warrantyDate(), old.warrantyDate()), coalesce(f.healthIndex(), old.healthIndex()),
                coalesce(f.model(), old.model()), coalesce(f.serialNumber(), old.serialNumber()),
                coalesce(f.installDate(), old.installDate()), coalesce(f.lifecycleStatus(), old.lifecycleStatus()),
                coalesce(f.responsiblePerson(), old.responsiblePerson()), coalesce(f.department(), old.department()),
                coalesce(f.supplierContact(), old.supplierContact()), coalesce(f.maintenanceCycle(), old.maintenanceCycle()),
                coalesce(f.lastMaintenanceDate(), old.lastMaintenanceDate()), coalesce(f.remark(), old.remark()),
                coalesce(f.typeName(), old.typeName()), id);
    }

    public void updateService(int id, ServiceUpdateFields f) {
        ServiceRow old = findServiceRowById(id);
        if (old == null) throw new AssetException("Service not found: " + id);
        jdbc.update(
                "UPDATE t_service SET name=?, caption=?, parent=?, mode=?, driver_class=?, max_connections=?, type_name=? WHERE id=?",
                coalesce(f.name(), old.name()), coalesce(f.caption(), old.caption()),
                coalesce(f.parentId(), old.parentId()), coalesce(f.mode(), old.mode()),
                coalesce(f.driverClass(), old.driverClass()), coalesce(f.maxConnections(), old.maxConnections()),
                coalesce(f.typeName(), old.typeName()), id);
    }

    public void updateProbe(int id, ProbeUpdateFields f) {
        ProbeRow old = findProbeRowById(id);
        if (old == null) throw new AssetException("Probe not found: " + id);
        jdbc.update(
                "UPDATE t_probe SET name=?, caption=?, parent=?, source=?, unit=?, time_interval=?, "
                        + "saving_interval=?, warn_cond=?, transform=?, catalog=?, monitor_kind=?, "
                        + "min_value=?, max_value=?, type_name=?, "
                        + "is_virtual=?, expression=?, depends_on=? WHERE id=?",
                coalesce(f.name(), old.name()), coalesce(f.caption(), old.caption()),
                coalesce(f.parentId(), old.parentId()), coalesce(f.serviceId(), old.serviceId()),
                coalesce(f.unit(), old.unit()), coalesce(f.detectInterval(), old.detectInterval()),
                coalesce(f.savingInterval(), old.savingInterval()), coalesce(f.warnCondition(), old.warnCondition()),
                coalesce(f.transform(), old.transform()), coalesce(f.catalog(), old.catalog()),
                coalesce(dataTypeNameToOrdinal(f.dataType()), dataTypeNameToOrdinal(old.dataType())),
                coalesce(f.minValue(), old.minValue()), coalesce(f.maxValue(), old.maxValue()),
                coalesce(f.typeName(), old.typeName()),
                coalesce(f.isVirtual(), old.isVirtual()),
                coalesce(f.expression(), old.expression()),
                coalesce(f.dependsOn(), old.dependsOn()),
                id);
    }

    public void updateControl(int id, ControlUpdateFields f) {
        ControlRow old = findControlRowById(id);
        if (old == null) throw new AssetException("Control not found: " + id);
        jdbc.update(
                "UPDATE t_control SET name=?, caption=?, parent=?, source=?, unit=?, time_interval=?, "
                        + "saving_interval=?, catalog=?, transform=?, warn_cond=?, refresh_delay=?, "
                        + "min_value=?, max_value=?, type_name=? WHERE id=?",
                coalesce(f.name(), old.name()), coalesce(f.caption(), old.caption()),
                coalesce(f.parentId(), old.parentId()), coalesce(f.serviceId(), old.serviceId()),
                coalesce(f.unit(), old.unit()), coalesce(f.detectInterval(), old.detectInterval()),
                coalesce(f.savingInterval(), old.savingInterval()), coalesce(f.catalog(), old.catalog()),
                coalesce(f.transform(), old.transform()), coalesce(f.warnCondition(), old.warnCondition()),
                coalesce(f.refreshDelay(), old.refreshDelay()), coalesce(f.minValue(), old.minValue()),
                coalesce(f.maxValue(), old.maxValue()), coalesce(f.typeName(), old.typeName()), id);
    }

    /** Coalesce: return the new value if non-null, otherwise the old value. */
    @SafeVarargs
    private static <T> T coalesce(T... values) {
        for (T v : values) {
            if (v != null) return v;
        }
        return null;
    }

    /** JDBC returns Integer for SMALLINT/TINYINT — safely narrow to Short. */
    private static Short shortOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Number val = (Number) rs.getObject(column);
        if (val == null) return null;
        if (val instanceof Short s) return s;
        return val.shortValue();
    }

    /** JDBC returns Double for FLOAT columns — safely narrow to Float. */
    private static Float floatOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Number val = (Number) rs.getObject(column);
        if (val == null) return null;
        if (val instanceof Float f) return f;
        return val.floatValue();
    }

    // ======================== CRUD: Delete ========================

    public void deleteSpace(int id) {
        jdbc.update("DELETE FROM t_asset_attribute WHERE asset_id=?", id);
        jdbc.update("DELETE FROM t_asset WHERE space_id=?", id);
        jdbc.update("DELETE FROM t_space WHERE id=?", id);
    }

    public void deleteDevice(int id) {
        jdbc.update("DELETE FROM t_asset_attribute WHERE asset_id=?", id);
        jdbc.update("DELETE FROM t_asset WHERE device_id=?", id);
        jdbc.update("DELETE FROM t_device WHERE id=?", id);
    }

    public void deleteService(int id) {
        jdbc.update("DELETE FROM t_asset_attribute WHERE asset_id=?", id);
        jdbc.update("DELETE FROM t_asset WHERE service_id=?", id);
        jdbc.update("DELETE FROM t_service WHERE id=?", id);
    }

    public void deleteProbe(int id) {
        jdbc.update("DELETE FROM t_asset_attribute WHERE asset_id=?", id);
        jdbc.update("DELETE FROM t_asset WHERE probe_id=?", id);
        jdbc.update("DELETE FROM t_probe WHERE id=?", id);
    }

    public void deleteControl(int id) {
        jdbc.update("DELETE FROM t_asset_attribute WHERE asset_id=?", id);
        jdbc.update("DELETE FROM t_asset WHERE control_id=?", id);
        jdbc.update("DELETE FROM t_control WHERE id=?", id);
    }

    // ======================== CRUD: Find by ID ========================

    public Space findSpaceById(int id) {
        var rows = jdbc.query(SELECT_SPACE + " WHERE id=?",
                (rs, i) -> toSpace(mapSpaceRow(rs)), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Device findDeviceById(int id) {
        var rows = jdbc.query(SELECT_DEVICE + " WHERE id=?",
                (rs, i) -> toDevice(mapDeviceRow(rs)), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public MonitorService findServiceById(int id) {
        Map<Integer, MonitorService> index = new HashMap<>();
        var rows = jdbc.query(SELECT_SERVICE + " WHERE id=?",
                (rs, i) -> toService(mapServiceRow(rs), index), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Probe findProbeById(int id) {
        var rows = jdbc.query(SELECT_PROBE + " WHERE id=?",
                (rs, i) -> {
                    ProbeRow row = mapProbeRow(rs);
                    Probe probe = buildProbeForFind(row);
                    resolveSourceForFind(probe, row.serviceId());
                    validateServiceTypeMatch(probe, "Probe", row.id(), row.name());
                    applyDefaults(probe);
                    return probe;
                }, id);
        if (rows.isEmpty()) return null;
        Probe probe = rows.get(0);
        loadAttributes(probe);
        probe.bindProperties();
        return probe;
    }

    public Control findControlById(int id) {
        var rows = jdbc.query(SELECT_CONTROL + " WHERE id=?",
                (rs, i) -> {
                    ControlRow row = mapControlRow(rs);
                    Control control = buildControlForFind(row);
                    resolveSourceForFind(control, row.serviceId());
                    validateServiceTypeMatch(control, "Control", row.id(), row.name());
                    applyDefaults(control);
                    return control;
                }, id);
        if (rows.isEmpty()) return null;
        Control control = rows.get(0);
        loadAttributes(control);
        control.bindProperties();
        return control;
    }

    private Probe buildProbeForFind(ProbeRow row) {
        boolean isVirtual = row.isVirtual() != null && row.isVirtual() == 1;
        ProbeType type;
        if (isVirtual) {
            type = typeResolver.resolveVirtualProbeType(row.typeName(), row.id(), row.name(),
                    row.expression(), row.dependsOn());
        } else {
            type = typeResolver.resolveProbeType(row.typeName(), row.id(), row.name());
        }
        boolean isFallback = isFallbackTypeName(row.typeName());
        if (isFallback && row.unit() != null) type.setUnit(row.unit());
        Probe probe = isVirtual ? new VirtualProbe() : createProbeInstance(type, row.name());
        probe.init(type, row.id(), row.name());
        applyMonitorCommon(probe, row.caption(), row.parentId(),
                row.detectInterval(), row.savingInterval(),
                row.warnCondition(), row.transform(),
                row.typeName(), row.minValue(), row.maxValue(), row.unit());

        if (probe instanceof VirtualProbe vp) {
            vp.setAssetStore(store);
            vp.parseDependsOn();
            vp.compileExpression();
        }
        return probe;
    }

    private Control buildControlForFind(ControlRow row) {
        ControlType type = typeResolver.resolveControlType(row.typeName(), row.id(), row.name());
        boolean isFallback = isFallbackTypeName(row.typeName());
        if (isFallback && row.unit() != null) type.setUnit(row.unit());
        Control control = createControlInstance(type, row.name());
        control.init(type, row.id(), row.name());
        applyMonitorCommon(control, row.caption(), row.parentId(),
                row.detectInterval(), row.savingInterval(),
                row.warnCondition(), row.transform(),
                row.typeName(), row.minValue(), row.maxValue(), row.unit());
        return control;
    }

    private void resolveSourceForFind(Monitor<?> monitor, Integer serviceId) {
        if (serviceId == null) return;
        Asset<?> asset = store.findAsset(serviceId);
        if (asset == null) {
            log.warn("Monitor '{}' (id={}) references service id={} which is not present in the store.",
                    monitor.getName(), monitor.getId(), serviceId);
            return;
        }
        if (asset instanceof MonitorService service) {
            monitor.setSource(service);
        } else {
            log.warn("Monitor '{}' (id={}) references service id={} but asset {} is a {}, not a MonitorService.",
                    monitor.getName(), monitor.getId(), serviceId, asset.getName(), asset.getKind());
        }
    }

    // ======================== Row finders (for update merge) ========================

    private SpaceRow findSpaceRowById(int id) {
        var rows = jdbc.query(SELECT_SPACE + " WHERE id=?",
                (rs, i) -> mapSpaceRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DeviceRow findDeviceRowById(int id) {
        var rows = jdbc.query(SELECT_DEVICE + " WHERE id=?",
                (rs, i) -> mapDeviceRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ServiceRow findServiceRowById(int id) {
        var rows = jdbc.query(SELECT_SERVICE + " WHERE id=?",
                (rs, i) -> mapServiceRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ProbeRow findProbeRowById(int id) {
        var rows = jdbc.query(SELECT_PROBE + " WHERE id=?",
                (rs, i) -> mapProbeRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ControlRow findControlRowById(int id) {
        var rows = jdbc.query(SELECT_CONTROL + " WHERE id=?",
                (rs, i) -> mapControlRow(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ======================== t_asset Unified View ========================

    public void insertAssetView(String name, String caption, AssetKind kind, int parentId, int perKindId) {
        String kindCol = getKindIdColumn(kind);
        jdbc.update(
                "INSERT INTO t_asset (name, caption, kind, parent_id, enabled, " + kindCol + ") VALUES (?, ?, ?, ?, 1, ?)",
                name, caption, kind.ordinal(), (long) parentId, (long) perKindId);
    }

    public void updateAssetView(int perKindId, AssetKind kind, String name, String caption) {
        String kindCol = getKindIdColumn(kind);
        jdbc.update(
                "UPDATE t_asset SET name=?, caption=? WHERE kind=? AND " + kindCol + "=?",
                name, caption, kind.ordinal(), perKindId);
    }

    /** Set enabled/disabled flag on the unified asset view. */
    public void setEnabled(AssetKind kind, int perKindId, boolean enabled) {
        String kindCol = getKindIdColumn(kind);
        jdbc.update(
                "UPDATE t_asset SET enabled=? WHERE kind=? AND " + kindCol + "=?",
                enabled ? 1 : 0, kind.ordinal(), perKindId);
    }

    private static String getKindIdColumn(AssetKind kind) {
        return switch (kind) {
            case SPACE -> "space_id";
            case DEVICE -> "device_id";
            case SERVICE -> "service_id";
            case PROBE -> "probe_id";
            case CONTROL -> "control_id";
        };
    }

    // ======================== ID Generation ========================

    public int nextId(AssetKind kind) {
        String table = switch (kind) {
            case SPACE -> "t_space";
            case DEVICE -> "t_device";
            case SERVICE -> "t_service";
            case PROBE -> "t_probe";
            case CONTROL -> "t_control";
        };
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM " + table, Integer.class);
        return (max != null ? max : 0) + 1;
    }

    // ======================== Attribute Methods ========================

    public void saveAttributes(int assetId, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) return;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            jdbc.update(
                    "INSERT INTO t_asset_attribute (asset_id, attr_key, attr_value, attr_type) VALUES (?, ?, ?, ?)",
                    assetId, entry.getKey(), entry.getValue(), "STRING");
        }
    }

    public void deleteAttributes(int assetId) {
        jdbc.update("DELETE FROM t_asset_attribute WHERE asset_id=?", assetId);
    }

    public void loadAttributes(Asset<?> asset) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT attr_key, attr_value FROM t_asset_attribute WHERE asset_id=?", asset.getId());
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("attr_key");
            Object value = row.get("attr_value");
            if (key != null && value != null) {
                asset.setMetadata(key, value);
            }
        }
    }

    public void loadAllAttributes(Collection<Asset<?>> assets) {
        for (Asset<?> asset : assets) {
            loadAttributes(asset);
        }
    }

    /**
     * Returns the raw KV attribute map for a single asset from {@code t_asset_attribute},
     * without modifying the asset's metadata. Used by the controller layer to build the VO.
     */
    public Map<String, String> findAttributes(int assetId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT attr_key, attr_value FROM t_asset_attribute WHERE asset_id=?", assetId);
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = (String) row.get("attr_key");
            Object value = row.get("attr_value");
            if (key != null && value != null) {
                result.put(key, value.toString());
            }
        }
        return result;
    }

    // ======================== Delete Constraint Queries ========================

    public long countAlarmRulesForMonitor(int monitorId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_alarm_rule WHERE asset_id = ?", Long.class, monitorId);
        return count != null ? count : 0;
    }

    public long countLinkageCausesForMonitor(int monitorId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_linkage_rule_cause WHERE asset_id = ?", Long.class, monitorId);
        return count != null ? count : 0;
    }

    public long countLinkageEffectsForMonitor(int monitorId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_linkage_rule_effect WHERE asset_id = ?", Long.class, monitorId);
        return count != null ? count : 0;
    }
}
