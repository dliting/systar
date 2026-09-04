-- ============================================================================
-- Simulator Integration Data for MySQL
-- 设备/监测器/控制器连接到 IoT Simulator 进行端到端测试
-- 前置条件: 01-init.sql 已执行 (空间、字典、类型配置)
-- ============================================================================

-- ============================================================================
-- 1. 监控服务 (每个 Modbus unitId 一个服务实例)
-- ============================================================================
INSERT INTO t_service (id, name, caption, parent, mode, driver_class, max_connections, type_name) VALUES
    (110, 'modbus_svc_hvac', 'Modbus TCP-HVAC模拟',  1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    5, 'ModbusTcpMaster'),
    (111, 'modbus_svc_ups',  'Modbus TCP-UPS模拟',   1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    5, 'ModbusTcpMaster'),
    (112, 'modbus_svc_pdu',  'Modbus TCP-PDU模拟',   1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    5, 'ModbusTcpMaster'),
    (113, 'opcua_svc_sim',   'OPC UA模拟服务',       1, 0, 'com.systar.monitor.drivers.opcua.OpcUaService',     5,  'OpcUaService')
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 2. 服务属性 (t_asset_attribute)
-- Modbus 使用小写键名，因为 ModbusService.resolveConfig() 用 getMetadata("host") 读取
-- OPC UA 使用 Property 名，通过 bindProperties() → setEndpointUrl() 绑定
-- ============================================================================
INSERT INTO t_asset_attribute (id, asset_id, attr_key, attr_value, attr_type) VALUES
    (200, 110, 'host',      'localhost',                'STRING'),
    (201, 110, 'port',      '55502',                    'INT'),
    (202, 110, 'unitId',    '1',                        'INT'),
    (203, 110, 'timeout',   '5000',                     'INT'),
    (204, 111, 'host',      'localhost',                'STRING'),
    (205, 111, 'port',      '55502',                    'INT'),
    (206, 111, 'unitId',    '2',                        'INT'),
    (207, 111, 'timeout',   '5000',                     'INT'),
    (208, 112, 'host',      'localhost',                'STRING'),
    (209, 112, 'port',      '55502',                    'INT'),
    (210, 112, 'unitId',    '3',                        'INT'),
    (211, 112, 'timeout',   '5000',                     'INT'),
    (212, 113, 'EndpointUrl', 'opc.tcp://localhost:55503/systar-simulator', 'STRING')
ON DUPLICATE KEY UPDATE attr_value = VALUES(attr_value);

-- ============================================================================
-- 3. 设备
-- ============================================================================
INSERT INTO t_device (id, name, caption, parent, catalog, vendor, purchase_date, warranty_date, health_index) VALUES
    (1101, 'ahu_sim_01',     'AHU空调机组模拟',  15, 206, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00),
    (1102, 'ups_sim_01',     'UPS电源模拟',      15, 208, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00),
    (1103, 'pdu_sim_01',     'PDU配电柜模拟',    15, 202, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00),
    (1104, 'weather_sim_01', '气象站模拟',        10, 201, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00)
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 4. 监测器 (Modbus)
-- ============================================================================
INSERT INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name) VALUES
    -- HVAC (AHU)
    (1201, 'sim_supply_temp',     '送风温度',       1101, 110, 'C',    '10s', '1m', 'value>30',  NULL, 301, 1, -10.0, 50.0,   'ModbusFloatFC3'),
    (1202, 'sim_return_temp',     '回风温度',       1101, 110, 'C',    '10s', '1m', NULL,        NULL, 301, 1, -10.0, 50.0,   'ModbusFloatFC3'),
    (1203, 'sim_supply_humidity', '送风湿度',       1101, 110, '%RH',  '10s', '1m', 'value>80',  NULL, 302, 1,   0.0, 100.0,  'ModbusFloatFC3'),
    (1204, 'sim_fan_running',     '风机运行',       1101, 110, NULL,   '10s', '1m', NULL,        NULL, 309, 2,   NULL, NULL,   'ModbusBoolFC1'),
    (1205, 'sim_damper_pos',      '风门位置',       1101, 110, '%',    '10s', '1m', 'value>95',  NULL, 404, 1,   0.0, 100.0,  'ModbusFloatFC3'),
    -- UPS
    (1206, 'sim_battery_level',   '电池电量',       1102, 111, '%',    '30s', '1m', 'value<25',  NULL, 301, 1,   0.0, 100.0,  'ModbusFloatFC3'),
    (1207, 'sim_load_percent',    '负载百分比',     1102, 111, '%',    '30s', '1m', NULL,        NULL, 305, 1,   0.0, 100.0,  'ModbusFloatFC3'),
    (1208, 'sim_input_voltage',   '输入电压',       1102, 111, 'V',    '10s', '1m', 'value<200', NULL, 304, 1,   0.0, 300.0,  'ModbusFloatFC3'),
    (1209, 'sim_output_voltage',  '输出电压',       1102, 111, 'V',    '10s', '1m', NULL,        NULL, 304, 1,   0.0, 300.0,  'ModbusFloatFC3'),
    (1210, 'sim_on_battery',      '电池供电状态',   1102, 111, NULL,   '30s', '1m', 'value==1',  NULL, 309, 2,   NULL, NULL,   'ModbusBoolFC1'),
    -- PDU
    (1211, 'sim_voltage_l1',      'L1电压',         1103, 112, 'V',    '10s', '1m', 'value<200', NULL, 304, 1,   0.0, 300.0,  'ModbusFloatFC3'),
    (1212, 'sim_voltage_l2',      'L2电压',         1103, 112, 'V',    '10s', '1m', NULL,        NULL, 304, 1,   0.0, 300.0,  'ModbusFloatFC3'),
    (1213, 'sim_voltage_l3',      'L3电压',         1103, 112, 'V',    '10s', '1m', NULL,        NULL, 304, 1,   0.0, 300.0,  'ModbusFloatFC3'),
    (1214, 'sim_current_total',   '总电流',         1103, 112, 'A',    '10s', '1m', NULL,        NULL, 303, 1,   0.0, 100.0,  'ModbusFloatFC3'),
    (1215, 'sim_power_total',     '总有功功率',     1103, 112, 'kW',   '10s', '1m', NULL,        NULL, 305, 1,   0.0, 9999.0, 'ModbusFloatFC3')
ON DUPLICATE KEY UPDATE caption = VALUES(caption), type_name = VALUES(type_name);

-- ============================================================================
-- 4b. 监测器 (OPC UA)
-- ============================================================================
INSERT INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name) VALUES
    (1216, 'sim_outdoor_temp',  '室外温度',  1104, 113, 'C',    '10s', '1m', 'value>35',  NULL, 301, 1, -20.0, 50.0,  'OpcUaFloat'),
    (1217, 'sim_outdoor_humi',  '室外湿度',  1104, 113, '%RH',  '10s', '1m', NULL,        NULL, 302, 1,   0.0, 100.0, 'OpcUaFloat'),
    (1218, 'sim_wind_speed',    '风速',      1104, 113, 'm/s',  '10s', '1m', NULL,        NULL, 301, 1,   0.0, 50.0,  'OpcUaFloat'),
    (1219, 'sim_rainfall',      '降雨量',    1104, 113, 'mm',   '10s', '1m', NULL,        NULL, 301, 1,   0.0, 50.0,  'OpcUaFloat')
ON DUPLICATE KEY UPDATE caption = VALUES(caption), type_name = VALUES(type_name);

-- ============================================================================
-- 4c. 监测器属性 (RegisterAddr / NodeId)
-- ============================================================================
INSERT INTO t_asset_attribute (id, asset_id, attr_key, attr_value, attr_type) VALUES
    -- HVAC
    (300, 1201, 'RegisterAddr', '0',  'INT'),
    (301, 1202, 'RegisterAddr', '2',  'INT'),
    (302, 1203, 'RegisterAddr', '4',  'INT'),
    (303, 1204, 'RegisterAddr', '0',  'INT'),
    (304, 1205, 'RegisterAddr', '6',  'INT'),
    -- UPS
    (305, 1206, 'RegisterAddr', '10', 'INT'),
    (306, 1207, 'RegisterAddr', '12', 'INT'),
    (307, 1208, 'RegisterAddr', '14', 'INT'),
    (308, 1209, 'RegisterAddr', '16', 'INT'),
    (309, 1210, 'RegisterAddr', '10', 'INT'),
    -- PDU
    (310, 1211, 'RegisterAddr', '20', 'INT'),
    (311, 1212, 'RegisterAddr', '22', 'INT'),
    (312, 1213, 'RegisterAddr', '24', 'INT'),
    (313, 1214, 'RegisterAddr', '26', 'INT'),
    (314, 1215, 'RegisterAddr', '28', 'INT'),
    -- OPC UA Weather Station
    (315, 1216, 'NodeId', 'ns=2;s=OutdoorTemperature', 'STRING'),
    (316, 1217, 'NodeId', 'ns=2;s=OutdoorHumidity',    'STRING'),
    (317, 1218, 'NodeId', 'ns=2;s=WindSpeed',           'STRING'),
    (318, 1219, 'NodeId', 'ns=2;s=Rainfall',            'STRING')
ON DUPLICATE KEY UPDATE attr_value = VALUES(attr_value);

-- ============================================================================
-- 5. 控制器 (Modbus)
-- ============================================================================
INSERT INTO t_control (id, name, caption, parent, source, unit, time_interval, saving_interval, catalog, transform, warn_cond, refresh_delay, min_value, max_value, type_name) VALUES
    (3201, 'sim_fan_switch',  '风机开关',     1101, 110, NULL, '10s', '30s', 401, NULL, NULL, 2000, NULL, NULL,  'ModbusBoolFC3FC6'),
    (3202, 'sim_damper_set',  '风门设定',     1101, 110, '%',  '10s', '30s', 404, NULL, NULL, 2000, 0.0,  100.0, 'ModbusInt16FC3FC6'),
    (3203, 'sim_ups_switch',  'UPS开关',      1102, 111, NULL, '10s', '30s', 401, NULL, NULL, 2000, NULL, NULL,  'ModbusBoolFC3FC6')
ON DUPLICATE KEY UPDATE caption = VALUES(caption), type_name = VALUES(type_name);

-- 控制器属性
INSERT INTO t_asset_attribute (id, asset_id, attr_key, attr_value, attr_type) VALUES
    (319, 3201, 'InRegisterAddr',  '0', 'INT'),
    (320, 3201, 'OutRegisterAddr', '0', 'INT'),
    (321, 3202, 'InRegisterAddr',  '6', 'INT'),
    (322, 3202, 'OutRegisterAddr', '6', 'INT'),
    (323, 3203, 'InRegisterAddr',  '10', 'INT'),
    (324, 3203, 'OutRegisterAddr', '10', 'INT')
ON DUPLICATE KEY UPDATE attr_value = VALUES(attr_value);

-- ============================================================================
-- 6. 报警规则
-- rule: 0=高报警(value>threshold), 1=低报警(value<threshold)
-- way: 7=UI告警, warn_id: 2=警告, 3=重要, 4=紧急
-- ============================================================================
INSERT INTO t_alarm_rule (id, asset_id, rule, way, warn_id, message_template, enabled, start) VALUES
    (20, 1201, 0, 7, 2, 'AHU送风温度过高: {value}C',     1, 1),
    (21, 1203, 0, 7, 2, 'AHU送风湿度异常: {value}%RH',   1, 1),
    (22, 1206, 0, 7, 3, 'UPS电池电量低: {value}%',       1, 1),
    (23, 1208, 1, 7, 3, 'UPS输入电压异常: {value}V',     1, 1),
    (24, 1211, 1, 7, 3, 'PDU L1电压偏低: {value}V',      1, 1),
    (25, 1216, 0, 7, 2, '室外温度过高: {value}C',        1, 1),
    (26, 1210, 0, 7, 4, 'UPS切换到电池供电',              1, 1),
    (27, 1205, 0, 7, 2, 'AHU风门位置异常: {value}%',     1, 1)
ON DUPLICATE KEY UPDATE message_template = VALUES(message_template);

-- ============================================================================
-- 7. 联动规则 (UPS电池供电 → 关闭空调风机)
-- ============================================================================
INSERT INTO t_linkage_rule (id, name, cause_type, enabled, caption) VALUES
    (2, 'UPS电池供电-空调保护', 'MONITOR', 1, 'UPS切换到电池供电时关闭空调风机')
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

INSERT INTO t_linkage_rule_cause (id, rule_id, asset_id, trigger_value) VALUES
    (2, 2, 1210, '1')
ON DUPLICATE KEY UPDATE trigger_value = VALUES(trigger_value);

INSERT INTO t_linkage_rule_effect (id, rule_id, asset_id, command) VALUES
    (2, 2, 3201, '0')
ON DUPLICATE KEY UPDATE command = VALUES(command);

-- ============================================================================
-- 8. 统一资产视图 (t_asset)
-- ============================================================================
INSERT INTO t_asset (id, name, caption, kind, type_id, parent_id, state, enabled, sort, space_id, device_id, service_id, probe_id, control_id) VALUES
    -- Services
    (50,  'modbus_svc_hvac', 'Modbus TCP-HVAC模拟',  2, 103, 1,  0, 1, 7,  NULL, NULL, 110, NULL,  NULL),
    (51,  'modbus_svc_ups',  'Modbus TCP-UPS模拟',   2, 103, 1,  0, 1, 8,  NULL, NULL, 111, NULL,  NULL),
    (52,  'modbus_svc_pdu',  'Modbus TCP-PDU模拟',   2, 103, 1,  0, 1, 9,  NULL, NULL, 112, NULL,  NULL),
    (53,  'opcua_svc_sim',   'OPC UA模拟服务',       2, 103, 1,  0, 1, 10, NULL, NULL, 113, NULL,  NULL),
    -- Devices
    (60,  'ahu_sim_01',      'AHU空调机组模拟',      1, 102, 7,  0, 1, 8,  NULL, 1101, NULL, NULL, NULL),
    (61,  'ups_sim_01',      'UPS电源模拟',          1, 102, 7,  0, 1, 9,  NULL, 1102, NULL, NULL, NULL),
    (62,  'pdu_sim_01',      'PDU配电柜模拟',        1, 102, 7,  0, 1, 10, NULL, 1103, NULL, NULL, NULL),
    (63,  'weather_sim_01',  '气象站模拟',           1, 102, 2,  0, 1, 4,  NULL, 1104, NULL, NULL, NULL),
    -- HVAC Probes
    (70,  'sim_supply_temp',     '送风温度',     3, 104, 60, 0, 1, 1, NULL, NULL, NULL, 1201, NULL),
    (71,  'sim_return_temp',     '回风温度',     3, 104, 60, 0, 1, 2, NULL, NULL, NULL, 1202, NULL),
    (72,  'sim_supply_humidity', '送风湿度',     3, 104, 60, 0, 1, 3, NULL, NULL, NULL, 1203, NULL),
    (73,  'sim_fan_running',     '风机运行',     3, 104, 60, 0, 1, 4, NULL, NULL, NULL, 1204, NULL),
    (74,  'sim_damper_pos',      '风门位置',     3, 104, 60, 0, 1, 5, NULL, NULL, NULL, 1205, NULL),
    -- UPS Probes
    (75,  'sim_battery_level',   '电池电量',     3, 104, 61, 0, 1, 1, NULL, NULL, NULL, 1206, NULL),
    (76,  'sim_load_percent',    '负载百分比',   3, 104, 61, 0, 1, 2, NULL, NULL, NULL, 1207, NULL),
    (77,  'sim_input_voltage',   '输入电压',     3, 104, 61, 0, 1, 3, NULL, NULL, NULL, 1208, NULL),
    (78,  'sim_output_voltage',  '输出电压',     3, 104, 61, 0, 1, 4, NULL, NULL, NULL, 1209, NULL),
    (79,  'sim_on_battery',      '电池供电状态', 3, 104, 61, 0, 1, 5, NULL, NULL, NULL, 1210, NULL),
    -- PDU Probes
    (80,  'sim_voltage_l1',      'L1电压',       3, 104, 62, 0, 1, 1, NULL, NULL, NULL, 1211, NULL),
    (81,  'sim_voltage_l2',      'L2电压',       3, 104, 62, 0, 1, 2, NULL, NULL, NULL, 1212, NULL),
    (82,  'sim_voltage_l3',      'L3电压',       3, 104, 62, 0, 1, 3, NULL, NULL, NULL, 1213, NULL),
    (83,  'sim_current_total',   '总电流',       3, 104, 62, 0, 1, 4, NULL, NULL, NULL, 1214, NULL),
    (84,  'sim_power_total',     '总有功功率',   3, 104, 62, 0, 1, 5, NULL, NULL, NULL, 1215, NULL),
    -- OPC UA Probes
    (85,  'sim_outdoor_temp',    '室外温度',     3, 104, 63, 0, 1, 1, NULL, NULL, NULL, 1216, NULL),
    (86,  'sim_outdoor_humi',    '室外湿度',     3, 104, 63, 0, 1, 2, NULL, NULL, NULL, 1217, NULL),
    (87,  'sim_wind_speed',      '风速',         3, 104, 63, 0, 1, 3, NULL, NULL, NULL, 1218, NULL),
    (88,  'sim_rainfall',        '降雨量',       3, 104, 63, 0, 1, 4, NULL, NULL, NULL, 1219, NULL),
    -- Controls
    (90,  'sim_fan_switch',      '风机开关',     4, 105, 60, 0, 1, 1, NULL, NULL, NULL, NULL, 3201),
    (91,  'sim_damper_set',      '风门设定',     4, 105, 60, 0, 1, 2, NULL, NULL, NULL, NULL, 3202),
    (92,  'sim_ups_switch',      'UPS开关',      4, 105, 61, 0, 1, 1, NULL, NULL, NULL, NULL, 3203)
ON DUPLICATE KEY UPDATE caption = VALUES(caption), probe_id = VALUES(probe_id), control_id = VALUES(control_id);

-- ============================================================================
-- 9. 历史采样数据 (用于趋势图测试)
-- 为 sim_supply_temp (monitor=1201) 生成2小时正弦数据，每分钟一个点
-- 温度范围: 18-28C，周期120分钟
-- ============================================================================
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(1201, 23.00, DATE_SUB(NOW(), INTERVAL 120 MINUTE)),
(1201, 22.09, DATE_SUB(NOW(), INTERVAL 119 MINUTE)),
(1201, 21.21, DATE_SUB(NOW(), INTERVAL 118 MINUTE)),
(1201, 20.38, DATE_SUB(NOW(), INTERVAL 117 MINUTE)),
(1201, 19.61, DATE_SUB(NOW(), INTERVAL 116 MINUTE)),
(1201, 18.91, DATE_SUB(NOW(), INTERVAL 115 MINUTE)),
(1201, 18.31, DATE_SUB(NOW(), INTERVAL 114 MINUTE)),
(1201, 17.79, DATE_SUB(NOW(), INTERVAL 113 MINUTE)),
(1201, 17.39, DATE_SUB(NOW(), INTERVAL 112 MINUTE)),
(1201, 17.09, DATE_SUB(NOW(), INTERVAL 111 MINUTE)),
(1201, 16.91, DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(1201, 16.85, DATE_SUB(NOW(), INTERVAL 109 MINUTE)),
(1201, 16.91, DATE_SUB(NOW(), INTERVAL 108 MINUTE)),
(1201, 17.09, DATE_SUB(NOW(), INTERVAL 107 MINUTE)),
(1201, 17.39, DATE_SUB(NOW(), INTERVAL 106 MINUTE)),
(1201, 17.79, DATE_SUB(NOW(), INTERVAL 105 MINUTE)),
(1201, 18.31, DATE_SUB(NOW(), INTERVAL 104 MINUTE)),
(1201, 18.91, DATE_SUB(NOW(), INTERVAL 103 MINUTE)),
(1201, 19.61, DATE_SUB(NOW(), INTERVAL 102 MINUTE)),
(1201, 20.38, DATE_SUB(NOW(), INTERVAL 101 MINUTE)),
(1201, 21.21, DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1201, 22.09, DATE_SUB(NOW(), INTERVAL 99 MINUTE)),
(1201, 23.00, DATE_SUB(NOW(), INTERVAL 98 MINUTE)),
(1201, 23.91, DATE_SUB(NOW(), INTERVAL 97 MINUTE)),
(1201, 24.79, DATE_SUB(NOW(), INTERVAL 96 MINUTE)),
(1201, 25.62, DATE_SUB(NOW(), INTERVAL 95 MINUTE)),
(1201, 26.39, DATE_SUB(NOW(), INTERVAL 94 MINUTE)),
(1201, 27.09, DATE_SUB(NOW(), INTERVAL 93 MINUTE)),
(1201, 27.69, DATE_SUB(NOW(), INTERVAL 92 MINUTE)),
(1201, 28.21, DATE_SUB(NOW(), INTERVAL 91 MINUTE)),
(1201, 28.61, DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(1201, 28.91, DATE_SUB(NOW(), INTERVAL 89 MINUTE)),
(1201, 29.09, DATE_SUB(NOW(), INTERVAL 88 MINUTE)),
(1201, 29.15, DATE_SUB(NOW(), INTERVAL 87 MINUTE)),
(1201, 29.09, DATE_SUB(NOW(), INTERVAL 86 MINUTE)),
(1201, 28.91, DATE_SUB(NOW(), INTERVAL 85 MINUTE)),
(1201, 28.61, DATE_SUB(NOW(), INTERVAL 84 MINUTE)),
(1201, 28.21, DATE_SUB(NOW(), INTERVAL 83 MINUTE)),
(1201, 27.69, DATE_SUB(NOW(), INTERVAL 82 MINUTE)),
(1201, 27.09, DATE_SUB(NOW(), INTERVAL 81 MINUTE)),
(1201, 26.39, DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(1201, 25.62, DATE_SUB(NOW(), INTERVAL 79 MINUTE)),
(1201, 24.79, DATE_SUB(NOW(), INTERVAL 78 MINUTE)),
(1201, 23.91, DATE_SUB(NOW(), INTERVAL 77 MINUTE)),
(1201, 23.00, DATE_SUB(NOW(), INTERVAL 76 MINUTE)),
(1201, 22.09, DATE_SUB(NOW(), INTERVAL 75 MINUTE)),
(1201, 21.21, DATE_SUB(NOW(), INTERVAL 74 MINUTE)),
(1201, 20.38, DATE_SUB(NOW(), INTERVAL 73 MINUTE)),
(1201, 19.61, DATE_SUB(NOW(), INTERVAL 72 MINUTE)),
(1201, 18.91, DATE_SUB(NOW(), INTERVAL 71 MINUTE)),
(1201, 18.31, DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(1201, 17.79, DATE_SUB(NOW(), INTERVAL 69 MINUTE)),
(1201, 17.39, DATE_SUB(NOW(), INTERVAL 68 MINUTE)),
(1201, 17.09, DATE_SUB(NOW(), INTERVAL 67 MINUTE)),
(1201, 16.91, DATE_SUB(NOW(), INTERVAL 66 MINUTE)),
(1201, 16.85, DATE_SUB(NOW(), INTERVAL 65 MINUTE)),
(1201, 16.91, DATE_SUB(NOW(), INTERVAL 64 MINUTE)),
(1201, 17.09, DATE_SUB(NOW(), INTERVAL 63 MINUTE)),
(1201, 17.39, DATE_SUB(NOW(), INTERVAL 62 MINUTE)),
(1201, 17.79, DATE_SUB(NOW(), INTERVAL 61 MINUTE)),
(1201, 18.31, DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
(1201, 18.91, DATE_SUB(NOW(), INTERVAL 59 MINUTE)),
(1201, 19.61, DATE_SUB(NOW(), INTERVAL 58 MINUTE)),
(1201, 20.38, DATE_SUB(NOW(), INTERVAL 57 MINUTE)),
(1201, 21.21, DATE_SUB(NOW(), INTERVAL 56 MINUTE)),
(1201, 22.09, DATE_SUB(NOW(), INTERVAL 55 MINUTE)),
(1201, 23.00, DATE_SUB(NOW(), INTERVAL 54 MINUTE)),
(1201, 23.91, DATE_SUB(NOW(), INTERVAL 53 MINUTE)),
(1201, 24.79, DATE_SUB(NOW(), INTERVAL 52 MINUTE)),
(1201, 25.62, DATE_SUB(NOW(), INTERVAL 51 MINUTE)),
(1201, 26.39, DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
(1201, 27.09, DATE_SUB(NOW(), INTERVAL 49 MINUTE)),
(1201, 27.69, DATE_SUB(NOW(), INTERVAL 48 MINUTE)),
(1201, 28.21, DATE_SUB(NOW(), INTERVAL 47 MINUTE)),
(1201, 28.61, DATE_SUB(NOW(), INTERVAL 46 MINUTE)),
(1201, 28.91, DATE_SUB(NOW(), INTERVAL 45 MINUTE)),
(1201, 29.09, DATE_SUB(NOW(), INTERVAL 44 MINUTE)),
(1201, 29.15, DATE_SUB(NOW(), INTERVAL 43 MINUTE)),
(1201, 29.09, DATE_SUB(NOW(), INTERVAL 42 MINUTE)),
(1201, 28.91, DATE_SUB(NOW(), INTERVAL 41 MINUTE)),
(1201, 28.61, DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(1201, 28.21, DATE_SUB(NOW(), INTERVAL 39 MINUTE)),
(1201, 27.69, DATE_SUB(NOW(), INTERVAL 38 MINUTE)),
(1201, 27.09, DATE_SUB(NOW(), INTERVAL 37 MINUTE)),
(1201, 26.39, DATE_SUB(NOW(), INTERVAL 36 MINUTE)),
(1201, 25.62, DATE_SUB(NOW(), INTERVAL 35 MINUTE)),
(1201, 24.79, DATE_SUB(NOW(), INTERVAL 34 MINUTE)),
(1201, 23.91, DATE_SUB(NOW(), INTERVAL 33 MINUTE)),
(1201, 23.00, DATE_SUB(NOW(), INTERVAL 32 MINUTE)),
(1201, 22.09, DATE_SUB(NOW(), INTERVAL 31 MINUTE)),
(1201, 21.21, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1201, 20.38, DATE_SUB(NOW(), INTERVAL 29 MINUTE)),
(1201, 19.61, DATE_SUB(NOW(), INTERVAL 28 MINUTE)),
(1201, 18.91, DATE_SUB(NOW(), INTERVAL 27 MINUTE)),
(1201, 18.31, DATE_SUB(NOW(), INTERVAL 26 MINUTE)),
(1201, 17.79, DATE_SUB(NOW(), INTERVAL 25 MINUTE)),
(1201, 17.39, DATE_SUB(NOW(), INTERVAL 24 MINUTE)),
(1201, 17.09, DATE_SUB(NOW(), INTERVAL 23 MINUTE)),
(1201, 16.91, DATE_SUB(NOW(), INTERVAL 22 MINUTE)),
(1201, 16.85, DATE_SUB(NOW(), INTERVAL 21 MINUTE)),
(1201, 16.91, DATE_SUB(NOW(), INTERVAL 20 MINUTE)),
(1201, 17.09, DATE_SUB(NOW(), INTERVAL 19 MINUTE)),
(1201, 17.39, DATE_SUB(NOW(), INTERVAL 18 MINUTE)),
(1201, 17.79, DATE_SUB(NOW(), INTERVAL 17 MINUTE)),
(1201, 18.31, DATE_SUB(NOW(), INTERVAL 16 MINUTE)),
(1201, 18.91, DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
(1201, 19.61, DATE_SUB(NOW(), INTERVAL 14 MINUTE)),
(1201, 20.38, DATE_SUB(NOW(), INTERVAL 13 MINUTE)),
(1201, 21.21, DATE_SUB(NOW(), INTERVAL 12 MINUTE)),
(1201, 22.09, DATE_SUB(NOW(), INTERVAL 11 MINUTE)),
(1201, 23.00, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(1201, 23.91, DATE_SUB(NOW(), INTERVAL 9 MINUTE)),
(1201, 24.79, DATE_SUB(NOW(), INTERVAL 8 MINUTE)),
(1201, 25.62, DATE_SUB(NOW(), INTERVAL 7 MINUTE)),
(1201, 26.39, DATE_SUB(NOW(), INTERVAL 6 MINUTE)),
(1201, 27.09, DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
(1201, 27.69, DATE_SUB(NOW(), INTERVAL 4 MINUTE)),
(1201, 28.21, DATE_SUB(NOW(), INTERVAL 3 MINUTE)),
(1201, 28.61, DATE_SUB(NOW(), INTERVAL 2 MINUTE)),
(1201, 28.91, DATE_SUB(NOW(), INTERVAL 1 MINUTE)),
(1201, 29.09, NOW());

-- sim_outdoor_temp (monitor=1216): 12-28C sine, 2 hours
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(1216, 20.00, DATE_SUB(NOW(), INTERVAL 120 MINUTE)),
(1216, 18.80, DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(1216, 17.30, DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1216, 15.50, DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(1216, 14.00, DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(1216, 13.10, DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(1216, 12.80, DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
(1216, 13.10, DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
(1216, 15.00, DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(1216, 18.50, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1216, 22.80, DATE_SUB(NOW(), INTERVAL 20 MINUTE)),
(1216, 26.50, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(1216, 28.00, NOW());

-- sim_battery_level (monitor=1206): ramp 100->85%, 2 hours
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(1206, 100.0, DATE_SUB(NOW(), INTERVAL 120 MINUTE)),
(1206, 99.0,  DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(1206, 97.5,  DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1206, 96.0,  DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(1206, 94.5,  DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(1206, 93.0,  DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(1206, 91.5,  DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
(1206, 90.0,  DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
(1206, 88.5,  DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(1206, 87.0,  DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1206, 86.0,  DATE_SUB(NOW(), INTERVAL 20 MINUTE)),
(1206, 85.5,  DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(1206, 85.0,  NOW());

-- sim_voltage_l1 (monitor=1211): 217-223V sine, 2 hours
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(1211, 220.0, DATE_SUB(NOW(), INTERVAL 120 MINUTE)),
(1211, 221.5, DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(1211, 222.5, DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1211, 223.0, DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
(1211, 222.5, DATE_SUB(NOW(), INTERVAL 80 MINUTE)),
(1211, 221.5, DATE_SUB(NOW(), INTERVAL 70 MINUTE)),
(1211, 220.0, DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
(1211, 218.5, DATE_SUB(NOW(), INTERVAL 50 MINUTE)),
(1211, 217.5, DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(1211, 217.0, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1211, 217.5, DATE_SUB(NOW(), INTERVAL 20 MINUTE)),
(1211, 218.5, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(1211, 220.0, NOW());

-- ============================================================================
-- 10. 告警历史 (近7天，用于看板/统计演示)
-- asset_id 存监测器运行时 ID (t_probe.id)；
-- t_error_message_log.state: 1=ERROR 2=WARNING；t_alarm_message.state: 1=待处理 2=已处理
-- ============================================================================
INSERT INTO t_error_message_log (id, asset_id, monitor_name, error_message, `value`, state, warn_id, time) VALUES
    -- 今日
    (901, 1201, '送风温度', 'AHU送风温度偏高',   '32.4',  2, 2, DATE_SUB(NOW(), INTERVAL 420 MINUTE)),
    (902, 1208, '输入电压', 'UPS输入电压偏低',   '195.4', 1, 4, DATE_SUB(NOW(), INTERVAL 330 MINUTE)),
    (903, 1211, 'L1电压',   'PDU L1电压偏低',    '196.8', 1, 3, DATE_SUB(NOW(), INTERVAL 240 MINUTE)),
    (904, 1206, '电池电量', 'UPS电池电量偏低',   '23.5',  1, 3, DATE_SUB(NOW(), INTERVAL 150 MINUTE)),
    (905, 1211, 'L1电压',   'PDU L1电压偏低',    '195.9', 1, 3, DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
    -- 昨日
    (906, 1208, '输入电压', 'UPS输入电压偏低',   '194.1', 1, 4, DATE_SUB(NOW(), INTERVAL 1700 MINUTE)),
    (907, 1201, '送风温度', 'AHU送风温度偏高',   '32.8',  2, 2, DATE_SUB(NOW(), INTERVAL 1650 MINUTE)),
    (908, 1206, '电池电量', 'UPS电池电量偏低',   '24.0',  1, 3, DATE_SUB(NOW(), INTERVAL 1560 MINUTE)),
    (909, 1211, 'L1电压',   'PDU L1电压偏低',    '197.3', 1, 3, DATE_SUB(NOW(), INTERVAL 1500 MINUTE)),
    -- 前两天
    (910, 1201, '送风温度', 'AHU送风温度偏高',   '31.9',  2, 2, DATE_SUB(NOW(), INTERVAL 3160 MINUTE)),
    (911, 1216, '室外温度', '室外温度超限',      '36.2',  2, 2, DATE_SUB(NOW(), INTERVAL 3100 MINUTE)),
    (912, 1206, '电池电量', 'UPS电池电量偏低',   '24.8',  1, 3, DATE_SUB(NOW(), INTERVAL 3000 MINUTE)),
    (913, 1211, 'L1电压',   'PDU L1电压偏低',    '197.8', 1, 3, DATE_SUB(NOW(), INTERVAL 2950 MINUTE)),
    -- 前三天
    (914, 1201, '送风温度', 'AHU送风温度偏高',   '32.1',  2, 2, DATE_SUB(NOW(), INTERVAL 4600 MINUTE)),
    (915, 1206, '电池电量', 'UPS电池电量偏低',   '24.3',  1, 3, DATE_SUB(NOW(), INTERVAL 4520 MINUTE)),
    (916, 1206, '电池电量', 'UPS电池电量偏低',   '23.9',  1, 3, DATE_SUB(NOW(), INTERVAL 4460 MINUTE)),
    (917, 1211, 'L1电压',   'PDU L1电压偏低',    '196.2', 1, 3, DATE_SUB(NOW(), INTERVAL 4400 MINUTE)),
    -- 前四天
    (918, 1201, '送风温度', 'AHU送风温度偏高',   '31.6',  2, 2, DATE_SUB(NOW(), INTERVAL 5980 MINUTE)),
    (919, 1208, '输入电压', 'UPS输入电压偏低',   '193.8', 1, 4, DATE_SUB(NOW(), INTERVAL 5910 MINUTE)),
    (920, 1211, 'L1电压',   'PDU L1电压偏低',    '195.4', 1, 3, DATE_SUB(NOW(), INTERVAL 5850 MINUTE)),
    -- 前五天
    (921, 1206, '电池电量', 'UPS电池电量偏低',   '25.1',  1, 3, DATE_SUB(NOW(), INTERVAL 7440 MINUTE)),
    (922, 1216, '室外温度', '室外温度超限',      '35.8',  2, 2, DATE_SUB(NOW(), INTERVAL 7360 MINUTE)),
    (923, 1211, 'L1电压',   'PDU L1电压偏低',    '198.2', 1, 3, DATE_SUB(NOW(), INTERVAL 7300 MINUTE)),
    -- 前六天
    (924, 1206, '电池电量', 'UPS电池电量偏低',   '24.6',  1, 3, DATE_SUB(NOW(), INTERVAL 8820 MINUTE)),
    (925, 1201, '送风温度', 'AHU送风温度偏高',   '32.5',  2, 2, DATE_SUB(NOW(), INTERVAL 8760 MINUTE)),
    (926, 1211, 'L1电压',   'PDU L1电压偏低',    '196.5', 1, 3, DATE_SUB(NOW(), INTERVAL 8700 MINUTE));

INSERT INTO t_alarm_message (id, log_id, caption, state, auto, alarm_time, recovered, warn_id, device_id) VALUES
    -- 今日：904/905 待处理，其余已处理
    (901, 901, 'AHU送风温度偏高', 2, 1, DATE_SUB(NOW(), INTERVAL 420 MINUTE), 1, 2, 1101),
    (902, 902, 'UPS输入电压偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 330 MINUTE), 1, 4, 1102),
    (903, 903, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 240 MINUTE), 1, 3, 1103),
    (904, 904, 'UPS电池电量偏低', 1, 1, DATE_SUB(NOW(), INTERVAL 150 MINUTE), 0, 3, 1102),
    (905, 905, 'PDU L1电压偏低',  1, 1, DATE_SUB(NOW(), INTERVAL 90 MINUTE), 0, 3, 1103),
    (906, 906, 'UPS输入电压偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 1700 MINUTE), 1, 4, 1102),
    (907, 907, 'AHU送风温度偏高', 2, 1, DATE_SUB(NOW(), INTERVAL 1650 MINUTE), 1, 2, 1101),
    (908, 908, 'UPS电池电量偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 1560 MINUTE), 1, 3, 1102),
    (909, 909, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 1500 MINUTE), 1, 3, 1103),
    (910, 910, 'AHU送风温度偏高', 2, 1, DATE_SUB(NOW(), INTERVAL 3160 MINUTE), 1, 2, 1101),
    (911, 911, '室外温度超限',    2, 1, DATE_SUB(NOW(), INTERVAL 3100 MINUTE), 1, 2, 1104),
    (912, 912, 'UPS电池电量偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 3000 MINUTE), 1, 3, 1102),
    (913, 913, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 2950 MINUTE), 1, 3, 1103),
    (914, 914, 'AHU送风温度偏高', 2, 1, DATE_SUB(NOW(), INTERVAL 4600 MINUTE), 1, 2, 1101),
    (915, 915, 'UPS电池电量偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 4520 MINUTE), 1, 3, 1102),
    (916, 916, 'UPS电池电量偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 4460 MINUTE), 1, 3, 1102),
    (917, 917, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 4400 MINUTE), 1, 3, 1103),
    (918, 918, 'AHU送风温度偏高', 2, 1, DATE_SUB(NOW(), INTERVAL 5980 MINUTE), 1, 2, 1101),
    (919, 919, 'UPS输入电压偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 5910 MINUTE), 1, 4, 1102),
    (920, 920, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 5850 MINUTE), 1, 3, 1103),
    (921, 921, 'UPS电池电量偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 7440 MINUTE), 1, 3, 1102),
    (922, 922, '室外温度超限',    2, 1, DATE_SUB(NOW(), INTERVAL 7360 MINUTE), 1, 2, 1104),
    (923, 923, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 7300 MINUTE), 1, 3, 1103),
    (924, 924, 'UPS电池电量偏低', 2, 1, DATE_SUB(NOW(), INTERVAL 8820 MINUTE), 1, 3, 1102),
    (925, 925, 'AHU送风温度偏高', 2, 1, DATE_SUB(NOW(), INTERVAL 8760 MINUTE), 1, 2, 1101),
    (926, 926, 'PDU L1电压偏低',  2, 1, DATE_SUB(NOW(), INTERVAL 8700 MINUTE), 1, 3, 1103);

-- ============================================================================
-- 11. 工单历史 (近7天，用于看板/统计演示)
-- ============================================================================
INSERT INTO t_work_order (id, order_no, title, description, type, source, device_id, priority, status, assignee_id, creator_id, due_time, resolution, created_at, updated_at, closed_at) VALUES
    (901, 'WO-2026-0901', 'PDU配电柜L1电压偏低处理', 'PDU L1相电压低于告警阈值，需要现场排查供电回路', 'REPAIR', 'ALARM_AUTO', 1103, 1, 'CREATED',    NULL, 1, DATE_ADD(NOW(), INTERVAL 22 HOUR), NULL, DATE_SUB(NOW(), INTERVAL 90 MINUTE), DATE_SUB(NOW(), INTERVAL 90 MINUTE), NULL),
    (902, 'WO-2026-0902', 'UPS电池电量告警核查',     'UPS电池电量持续偏低，核查电池组状态并安排充电', 'MAINTENANCE', 'ALARM_AUTO', 1102, 2, 'ASSIGNED',   1, 1, DATE_ADD(NOW(), INTERVAL 8 HOUR),  NULL, DATE_SUB(NOW(), INTERVAL 1500 MINUTE), DATE_SUB(NOW(), INTERVAL 1400 MINUTE), NULL),
    (903, 'WO-2026-0903', 'AHU空调机组过滤网更换',   '按季度保养计划更换AHU过滤网并清洗表冷器', 'MAINTENANCE', 'MANUAL', 1101, 3, 'PROCESSING', 1, 1, DATE_ADD(NOW(), INTERVAL 1 DAY),   NULL, DATE_SUB(NOW(), INTERVAL 1740 MINUTE), DATE_SUB(NOW(), INTERVAL 1200 MINUTE), NULL),
    (904, 'WO-2026-0904', 'UPS输入电压异常检修',     'UPS输入电压低于下限，检修输入回路', 'REPAIR', 'ALARM_AUTO', 1102, 2, 'CLOSED', 1, 1, DATE_SUB(NOW(), INTERVAL 1460 MINUTE), '更换输入滤波电容，电压恢复正常', DATE_SUB(NOW(), INTERVAL 2900 MINUTE), DATE_SUB(NOW(), INTERVAL 2660 MINUTE), DATE_SUB(NOW(), INTERVAL 2660 MINUTE)),
    (905, 'WO-2026-0905', 'AHU送风温度偏高处理',     'AHU送风温度超上限，排查冷媒与水阀', 'REPAIR', 'ALARM_AUTO', 1101, 2, 'CLOSED', 1, 1, DATE_SUB(NOW(), INTERVAL 2910 MINUTE), '清洗表冷器并调整水阀开度', DATE_SUB(NOW(), INTERVAL 4350 MINUTE), DATE_SUB(NOW(), INTERVAL 4170 MINUTE), DATE_SUB(NOW(), INTERVAL 4170 MINUTE)),
    (906, 'WO-2026-0906', 'PDU配电柜月度保养',       'PDU月度保养：紧固接线、除尘、测温', 'MAINTENANCE', 'MANUAL', 1103, 3, 'CLOSED', 1, 1, DATE_SUB(NOW(), INTERVAL 4360 MINUTE), '完成紧固与除尘，温升正常', DATE_SUB(NOW(), INTERVAL 5800 MINUTE), DATE_SUB(NOW(), INTERVAL 5600 MINUTE), DATE_SUB(NOW(), INTERVAL 5600 MINUTE)),
    (907, 'WO-2026-0907', '气象站传感器数据校准',     '室外温度读数偏差校准', 'INSPECTION', 'MANUAL', 1104, 4, 'CLOSED', 1, 1, DATE_SUB(NOW(), INTERVAL 5860 MINUTE), '校准完成，比对误差0.3℃', DATE_SUB(NOW(), INTERVAL 7300 MINUTE), DATE_SUB(NOW(), INTERVAL 7180 MINUTE), DATE_SUB(NOW(), INTERVAL 7180 MINUTE)),
    (908, 'WO-2026-0908', 'UPS电池组老化更换',       'UPS电池组容量衰减，整体更换并做充放电测试', 'REPAIR', 'MANUAL', 1102, 1, 'CLOSED', 1, 1, DATE_SUB(NOW(), INTERVAL 7360 MINUTE), '更换电池组，放电测试通过', DATE_SUB(NOW(), INTERVAL 8800 MINUTE), DATE_SUB(NOW(), INTERVAL 7000 MINUTE), DATE_SUB(NOW(), INTERVAL 7000 MINUTE)),
    (909, 'WO-2026-0909', 'PDU重复告警误报复核',     '经复核为电压采样抖动，无需处理', 'OTHER', 'MANUAL', 1103, 4, 'CANCELLED', NULL, 1, DATE_SUB(NOW(), INTERVAL 3060 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 4500 MINUTE), DATE_SUB(NOW(), INTERVAL 4300 MINUTE), NULL);

-- ============================================================================
-- 12. 巡检计划与任务 (用于看板/统计演示；时间相对 NOW() 生成)
-- ============================================================================
INSERT INTO t_inspection_plan (id, name, description, cron_expression, enabled, default_assignee_id, creator_id) VALUES
    (901, '机房设备例行巡检', '对机房主要设备进行例行巡视检查', '0 8 * * *',    1, 1, 1),
    (902, '配电系统专项巡检', 'UPS/PDU配电系统负载与温度专项巡检', '0 8,14 * * *', 1, 1, 1);

INSERT INTO t_inspection_plan_device (id, plan_id, device_id) VALUES
    (951, 901, 1101),
    (952, 901, 1104),
    (953, 902, 1102),
    (954, 902, 1103);

INSERT INTO t_inspection_task (id, plan_id, task_no, status, assignee_id, scheduled_time, started_at, completed_at, created_at) VALUES
    -- 今日：4单，2完成 → 完成率50%
    (911, 901, 'INS-0911', 'COMPLETED',   1, DATE_SUB(NOW(), INTERVAL 300 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 240 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (912, 902, 'INS-0912', 'COMPLETED',   1, DATE_SUB(NOW(), INTERVAL 180 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 120 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (913, 902, 'INS-0913', 'IN_PROGRESS', 1, DATE_SUB(NOW(), INTERVAL 60 MINUTE), DATE_SUB(NOW(), INTERVAL 50 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (914, 901, 'INS-0914', 'PENDING',  NULL, DATE_ADD(NOW(), INTERVAL 180 MINUTE), NULL, NULL, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    -- 历史已完成
    (915, 901, 'INS-0915', 'COMPLETED', 1, DATE_SUB(NOW(), INTERVAL 1740 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 1680 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (916, 902, 'INS-0916', 'COMPLETED', 1, DATE_SUB(NOW(), INTERVAL 1560 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 1500 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (917, 902, 'INS-0917', 'COMPLETED', 1, DATE_SUB(NOW(), INTERVAL 1380 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 1320 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (918, 901, 'INS-0918', 'COMPLETED', 1, DATE_SUB(NOW(), INTERVAL 3180 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 3120 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (919, 902, 'INS-0919', 'COMPLETED', 1, DATE_SUB(NOW(), INTERVAL 3000 MINUTE), NULL, DATE_SUB(NOW(), INTERVAL 2940 MINUTE), DATE_SUB(NOW(), INTERVAL 7 DAY));

-- ============================================================================
-- 13. 维保记录 (近7天，用于看板/统计演示；与工单 904-908 的处理动作对应)
-- ============================================================================
INSERT INTO t_maintenance_record (id, device_id, type, title, description, performer_id, creator_id, performed_at, cost, result, next_maintenance_date, created_at, updated_at) VALUES
    (961, 1102, 'REPAIR',      'UPS输入回路检修',   '更换输入滤波电容，电压恢复正常', 1, 1, DATE_SUB(NOW(), INTERVAL  2660 MINUTE),  850.00, '电压稳定在220V', NULL, DATE_SUB(NOW(), INTERVAL  2660 MINUTE), DATE_SUB(NOW(), INTERVAL  2660 MINUTE)),
    (962, 1101, 'MAINTENANCE', 'AHU表冷器清洗',     '清洗表冷器并调整水阀开度',       1, 1, DATE_SUB(NOW(), INTERVAL  4170 MINUTE),  600.00, '送风温度回落至26℃', NULL, DATE_SUB(NOW(), INTERVAL  4170 MINUTE), DATE_SUB(NOW(), INTERVAL  4170 MINUTE)),
    (963, 1103, 'MAINTENANCE', 'PDU月度保养',       '紧固接线、除尘、红外测温',       1, 1, DATE_SUB(NOW(), INTERVAL  5600 MINUTE),  300.00, '温升正常', NULL, DATE_SUB(NOW(), INTERVAL  5600 MINUTE), DATE_SUB(NOW(), INTERVAL  5600 MINUTE)),
    (964, 1104, 'INSPECTION',  '气象站传感器校准',  '室外温度传感器偏差校准',         1, 1, DATE_SUB(NOW(), INTERVAL  7180 MINUTE),  200.00, '比对误差0.3℃', NULL, DATE_SUB(NOW(), INTERVAL  7180 MINUTE), DATE_SUB(NOW(), INTERVAL  7180 MINUTE)),
    (965, 1102, 'REPAIR',      'UPS电池组更换',     '整组更换并做充放电测试',         1, 1, DATE_SUB(NOW(), INTERVAL  7000 MINUTE), 5200.00, '放电测试通过', NULL, DATE_SUB(NOW(), INTERVAL  7000 MINUTE), DATE_SUB(NOW(), INTERVAL  7000 MINUTE)),
    (966, 1101, 'MAINTENANCE', 'AHU过滤网更换',     '季度保养：更换过滤网',           1, 1, DATE_SUB(NOW(), INTERVAL  1200 MINUTE),  260.00, '压差恢复正常', NULL, DATE_SUB(NOW(), INTERVAL  1200 MINUTE), DATE_SUB(NOW(), INTERVAL  1200 MINUTE)),
    (967, 1103, 'INSPECTION',  '配电柜红外测温',    'PDU配电柜端子红外测温巡检',      1, 1, DATE_SUB(NOW(), INTERVAL  3000 MINUTE),  150.00, '无过热点', NULL, DATE_SUB(NOW(), INTERVAL  3000 MINUTE), DATE_SUB(NOW(), INTERVAL  3000 MINUTE)),
    (968, 1102, 'MAINTENANCE', 'UPS电池组巡检',     '电池组季度巡检与端电压记录',     1, 1, DATE_SUB(NOW(), INTERVAL  4460 MINUTE),  180.00, '端电压一致', NULL, DATE_SUB(NOW(), INTERVAL  4460 MINUTE), DATE_SUB(NOW(), INTERVAL  4460 MINUTE));

-- ============================================================================
-- 14. 巡检结果 (含异常项，用于巡检统计/异常分析演示)
-- check_result: NORMAL=正常 ABNORMAL=异常；created_at 对应任务完成时间
-- ============================================================================
INSERT INTO t_inspection_result (id, task_id, device_id, template_id, item_name, expected_value, check_result, actual_value, remark, created_at) VALUES
    (981, 911, 1101, 1, '机房环境温度检查',   '18~28℃',   'NORMAL',   '24.1℃',          NULL,           DATE_SUB(NOW(), INTERVAL   240 MINUTE)),
    (982, 911, 1104, 2, '室外气象传感器检查', '误差≤0.5℃', 'ABNORMAL', '偏差0.8℃',        '需重新校准',   DATE_SUB(NOW(), INTERVAL   240 MINUTE)),
    (983, 912, 1102, 1, 'UPS输出电压检查',   '220V±3%',   'NORMAL',   '221.4V',          NULL,           DATE_SUB(NOW(), INTERVAL   120 MINUTE)),
    (984, 912, 1103, 2, 'PDU负载不平衡检查', '≤15%',      'ABNORMAL', 'L1负载率62%',     '建议调相',     DATE_SUB(NOW(), INTERVAL   120 MINUTE)),
    (985, 915, 1101, 1, 'AHU送风温度检查',   '≤26℃',     'NORMAL',   '25.2℃',          NULL,           DATE_SUB(NOW(), INTERVAL  1680 MINUTE)),
    (986, 916, 1102, 2, 'UPS电池组外观检查', '无漏液',    'ABNORMAL', '2号电池端子漏液', '已转维保',     DATE_SUB(NOW(), INTERVAL  1500 MINUTE)),
    (987, 917, 1103, 1, '接线端子紧固检查',  '无松动',    'NORMAL',   '全部紧固',        NULL,           DATE_SUB(NOW(), INTERVAL  1320 MINUTE)),
    (988, 918, 1104, 2, '防雷模块状态检查',  '正常',      'NORMAL',   '显示正常',        NULL,           DATE_SUB(NOW(), INTERVAL  3120 MINUTE)),
    (989, 919, 1102, 3, 'UPS风扇滤网检查',   '清洁',      'NORMAL',   '轻微积尘已清理',  NULL,           DATE_SUB(NOW(), INTERVAL  2940 MINUTE));
