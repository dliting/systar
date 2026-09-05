-- ============================================================================
-- Simulator Integration Data for H2
-- 设备/监测器/控制器连接到 IoT Simulator 进行端到端测试
-- 前置条件: 01-init.sql 已执行 (空间、字典、类型配置)
-- ============================================================================

-- ============================================================================
-- 1. 监控服务 (每个 Modbus unitId 一个服务实例)
-- ============================================================================
MERGE INTO t_service (id, name, caption, parent, mode, driver_class, max_connections, type_name) KEY (id)
    VALUES (110, 'modbus_svc_hvac', 'Modbus TCP-HVAC模拟',  1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    5, 'ModbusTcpMaster'),
           (111, 'modbus_svc_ups',  'Modbus TCP-UPS模拟',   1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    5, 'ModbusTcpMaster'),
           (112, 'modbus_svc_pdu',  'Modbus TCP-PDU模拟',   1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    5, 'ModbusTcpMaster'),
           (113, 'opcua_svc_sim',   'OPC UA模拟服务',       1, 0, 'com.systar.monitor.drivers.opcua.OpcUaService',     5,  'OpcUaService');

-- ============================================================================
-- 2. 服务属性 (t_asset_attribute)
-- Modbus 使用小写键名，因为 ModbusService.resolveConfig() 用 getMetadata("host") 读取
-- OPC UA 使用 Property 名，通过 bindProperties() → setEndpointUrl() 绑定
-- ============================================================================
MERGE INTO t_asset_attribute (id, asset_id, attr_key, attr_value, attr_type) KEY (id)
    VALUES (200, 110, 'host',      'localhost',                'STRING'),
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
           (212, 113, 'EndpointUrl', 'opc.tcp://localhost:55503/systar-simulator', 'STRING');

-- ============================================================================
-- 3. 设备
-- ============================================================================
MERGE INTO t_device (id, name, caption, parent, catalog, vendor, purchase_date, warranty_date, health_index) KEY (id)
    VALUES (1101, 'ahu_sim_01',     'AHU空调机组模拟',  15, 206, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00),
           (1102, 'ups_sim_01',     'UPS电源模拟',      15, 208, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00),
           (1103, 'pdu_sim_01',     'PDU配电柜模拟',    15, 202, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00),
           (1104, 'weather_sim_01', '气象站模拟',        10, 201, 'Simulator', '2026-01-01 00:00:00', '2030-01-01', 1.00);

-- ============================================================================
-- 4. 监测器 (Modbus)
-- ============================================================================
MERGE INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name) KEY (id)
    VALUES -- HVAC (AHU)
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
           (1215, 'sim_power_total',     '总有功功率',     1103, 112, 'kW',   '10s', '1m', NULL,        NULL, 305, 1,   0.0, 9999.0, 'ModbusFloatFC3');

-- ============================================================================
-- 4b. 监测器 (OPC UA)
-- ============================================================================
MERGE INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name) KEY (id)
    VALUES (1216, 'sim_outdoor_temp',  '室外温度',  1104, 113, 'C',    '10s', '1m', 'value>35',  NULL, 301, 1, -20.0, 50.0,  'OpcUaFloat'),
           (1217, 'sim_outdoor_humi',  '室外湿度',  1104, 113, '%RH',  '10s', '1m', NULL,        NULL, 302, 1,   0.0, 100.0, 'OpcUaFloat'),
           (1218, 'sim_wind_speed',    '风速',      1104, 113, 'm/s',  '10s', '1m', NULL,        NULL, 301, 1,   0.0, 50.0,  'OpcUaFloat'),
           (1219, 'sim_rainfall',      '降雨量',    1104, 113, 'mm',   '10s', '1m', NULL,        NULL, 301, 1,   0.0, 50.0,  'OpcUaFloat');

-- ============================================================================
-- 4c. 监测器属性 (RegisterAddr / NodeId)
-- ============================================================================
MERGE INTO t_asset_attribute (id, asset_id, attr_key, attr_value, attr_type) KEY (id)
    VALUES -- HVAC
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
           (318, 1219, 'NodeId', 'ns=2;s=Rainfall',            'STRING');

-- ============================================================================
-- 5. 控制器 (Modbus)
-- ============================================================================
MERGE INTO t_control (id, name, caption, parent, source, unit, time_interval, saving_interval, catalog, transform, warn_cond, refresh_delay, min_value, max_value, type_name) KEY (id)
    VALUES (3201, 'sim_fan_switch',  '风机开关',     1101, 110, NULL, '10s', '30s', 401, NULL, NULL, 2000, NULL, NULL,  'ModbusBoolFC3FC6'),
           (3202, 'sim_damper_set',  '风门设定',     1101, 110, '%',  '10s', '30s', 404, NULL, NULL, 2000, 0.0,  100.0, 'ModbusInt16FC3FC6'),
           (3203, 'sim_ups_switch',  'UPS开关',      1102, 111, NULL, '10s', '30s', 401, NULL, NULL, 2000, NULL, NULL,  'ModbusBoolFC3FC6');

-- 控制器属性
MERGE INTO t_asset_attribute (id, asset_id, attr_key, attr_value, attr_type) KEY (id)
    VALUES (319, 3201, 'InRegisterAddr',  '0', 'INT'),
           (320, 3201, 'OutRegisterAddr', '0', 'INT'),
           (321, 3202, 'InRegisterAddr',  '6', 'INT'),
           (322, 3202, 'OutRegisterAddr', '6', 'INT'),
           (323, 3203, 'InRegisterAddr',  '10', 'INT'),
           (324, 3203, 'OutRegisterAddr', '10', 'INT');

-- ============================================================================
-- 6. 报警规则
-- rule: 0=高报警(value>threshold), 1=低报警(value<threshold)
-- way: 7=UI告警, warn_id: 2=警告, 3=重要, 4=紧急
-- ============================================================================
MERGE INTO t_alarm_rule (id, asset_id, rule, way, warn_id, message_template, enabled, start) KEY (id)
    VALUES (20, 1201, 0, 7, 2, 'AHU送风温度过高: {value}C',     1, 1),
           (21, 1203, 0, 7, 2, 'AHU送风湿度异常: {value}%RH',   1, 1),
           (22, 1206, 0, 7, 3, 'UPS电池电量低: {value}%',       1, 1),
           (23, 1208, 1, 7, 3, 'UPS输入电压异常: {value}V',     1, 1),
           (24, 1211, 1, 7, 3, 'PDU L1电压偏低: {value}V',      1, 1),
           (25, 1216, 0, 7, 2, '室外温度过高: {value}C',        1, 1),
           (26, 1210, 0, 7, 4, 'UPS切换到电池供电',              1, 1),
           (27, 1205, 0, 7, 2, 'AHU风门位置异常: {value}%',     1, 1);

-- ============================================================================
-- 7. 联动规则 (UPS电池供电 → 关闭空调风机)
-- ============================================================================
MERGE INTO t_linkage_rule (id, name, cause_type, enabled, caption) KEY (id)
    VALUES (2, 'UPS电池供电-空调保护', 'MONITOR', 1, 'UPS切换到电池供电时关闭空调风机');

MERGE INTO t_linkage_rule_cause (id, rule_id, asset_id, trigger_value) KEY (id)
    VALUES (2, 2, 1210, '1');

MERGE INTO t_linkage_rule_effect (id, rule_id, asset_id, command) KEY (id)
    VALUES (2, 2, 3201, '0');

-- ============================================================================
-- 8. 统一资产视图 (t_asset)
-- ============================================================================
MERGE INTO t_asset (id, name, caption, kind, type_id, parent_id, state, enabled, sort, space_id, device_id, service_id, probe_id, control_id) KEY (id)
    VALUES -- Services
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
           (92,  'sim_ups_switch',      'UPS开关',      4, 105, 61, 0, 1, 1, NULL, NULL, NULL, NULL, 3203);

-- ============================================================================
-- 9. 历史采样数据 (用于趋势图测试)
-- 为 sim_supply_temp (monitor=1201) 生成2小时正弦数据，每分钟一个点
-- 温度范围: 18-28C，周期120分钟
-- ============================================================================
MERGE INTO t_sample_float (monitor, "value", moment) KEY (monitor, moment) VALUES
(1201, 23.00, DATEADD('MINUTE', -120, NOW())),
(1201, 22.09, DATEADD('MINUTE', -119, NOW())),
(1201, 21.21, DATEADD('MINUTE', -118, NOW())),
(1201, 20.38, DATEADD('MINUTE', -117, NOW())),
(1201, 19.61, DATEADD('MINUTE', -116, NOW())),
(1201, 18.91, DATEADD('MINUTE', -115, NOW())),
(1201, 18.31, DATEADD('MINUTE', -114, NOW())),
(1201, 17.79, DATEADD('MINUTE', -113, NOW())),
(1201, 17.39, DATEADD('MINUTE', -112, NOW())),
(1201, 17.09, DATEADD('MINUTE', -111, NOW())),
(1201, 16.91, DATEADD('MINUTE', -110, NOW())),
(1201, 16.85, DATEADD('MINUTE', -109, NOW())),
(1201, 16.91, DATEADD('MINUTE', -108, NOW())),
(1201, 17.09, DATEADD('MINUTE', -107, NOW())),
(1201, 17.39, DATEADD('MINUTE', -106, NOW())),
(1201, 17.79, DATEADD('MINUTE', -105, NOW())),
(1201, 18.31, DATEADD('MINUTE', -104, NOW())),
(1201, 18.91, DATEADD('MINUTE', -103, NOW())),
(1201, 19.61, DATEADD('MINUTE', -102, NOW())),
(1201, 20.38, DATEADD('MINUTE', -101, NOW())),
(1201, 21.21, DATEADD('MINUTE', -100, NOW())),
(1201, 22.09, DATEADD('MINUTE', -99, NOW())),
(1201, 23.00, DATEADD('MINUTE', -98, NOW())),
(1201, 23.91, DATEADD('MINUTE', -97, NOW())),
(1201, 24.79, DATEADD('MINUTE', -96, NOW())),
(1201, 25.62, DATEADD('MINUTE', -95, NOW())),
(1201, 26.39, DATEADD('MINUTE', -94, NOW())),
(1201, 27.09, DATEADD('MINUTE', -93, NOW())),
(1201, 27.69, DATEADD('MINUTE', -92, NOW())),
(1201, 28.21, DATEADD('MINUTE', -91, NOW())),
(1201, 28.61, DATEADD('MINUTE', -90, NOW())),
(1201, 28.91, DATEADD('MINUTE', -89, NOW())),
(1201, 29.09, DATEADD('MINUTE', -88, NOW())),
(1201, 29.15, DATEADD('MINUTE', -87, NOW())),
(1201, 29.09, DATEADD('MINUTE', -86, NOW())),
(1201, 28.91, DATEADD('MINUTE', -85, NOW())),
(1201, 28.61, DATEADD('MINUTE', -84, NOW())),
(1201, 28.21, DATEADD('MINUTE', -83, NOW())),
(1201, 27.69, DATEADD('MINUTE', -82, NOW())),
(1201, 27.09, DATEADD('MINUTE', -81, NOW())),
(1201, 26.39, DATEADD('MINUTE', -80, NOW())),
(1201, 25.62, DATEADD('MINUTE', -79, NOW())),
(1201, 24.79, DATEADD('MINUTE', -78, NOW())),
(1201, 23.91, DATEADD('MINUTE', -77, NOW())),
(1201, 23.00, DATEADD('MINUTE', -76, NOW())),
(1201, 22.09, DATEADD('MINUTE', -75, NOW())),
(1201, 21.21, DATEADD('MINUTE', -74, NOW())),
(1201, 20.38, DATEADD('MINUTE', -73, NOW())),
(1201, 19.61, DATEADD('MINUTE', -72, NOW())),
(1201, 18.91, DATEADD('MINUTE', -71, NOW())),
(1201, 18.31, DATEADD('MINUTE', -70, NOW())),
(1201, 17.79, DATEADD('MINUTE', -69, NOW())),
(1201, 17.39, DATEADD('MINUTE', -68, NOW())),
(1201, 17.09, DATEADD('MINUTE', -67, NOW())),
(1201, 16.91, DATEADD('MINUTE', -66, NOW())),
(1201, 16.85, DATEADD('MINUTE', -65, NOW())),
(1201, 16.91, DATEADD('MINUTE', -64, NOW())),
(1201, 17.09, DATEADD('MINUTE', -63, NOW())),
(1201, 17.39, DATEADD('MINUTE', -62, NOW())),
(1201, 17.79, DATEADD('MINUTE', -61, NOW())),
(1201, 18.31, DATEADD('MINUTE', -60, NOW())),
(1201, 18.91, DATEADD('MINUTE', -59, NOW())),
(1201, 19.61, DATEADD('MINUTE', -58, NOW())),
(1201, 20.38, DATEADD('MINUTE', -57, NOW())),
(1201, 21.21, DATEADD('MINUTE', -56, NOW())),
(1201, 22.09, DATEADD('MINUTE', -55, NOW())),
(1201, 23.00, DATEADD('MINUTE', -54, NOW())),
(1201, 23.91, DATEADD('MINUTE', -53, NOW())),
(1201, 24.79, DATEADD('MINUTE', -52, NOW())),
(1201, 25.62, DATEADD('MINUTE', -51, NOW())),
(1201, 26.39, DATEADD('MINUTE', -50, NOW())),
(1201, 27.09, DATEADD('MINUTE', -49, NOW())),
(1201, 27.69, DATEADD('MINUTE', -48, NOW())),
(1201, 28.21, DATEADD('MINUTE', -47, NOW())),
(1201, 28.61, DATEADD('MINUTE', -46, NOW())),
(1201, 28.91, DATEADD('MINUTE', -45, NOW())),
(1201, 29.09, DATEADD('MINUTE', -44, NOW())),
(1201, 29.15, DATEADD('MINUTE', -43, NOW())),
(1201, 29.09, DATEADD('MINUTE', -42, NOW())),
(1201, 28.91, DATEADD('MINUTE', -41, NOW())),
(1201, 28.61, DATEADD('MINUTE', -40, NOW())),
(1201, 28.21, DATEADD('MINUTE', -39, NOW())),
(1201, 27.69, DATEADD('MINUTE', -38, NOW())),
(1201, 27.09, DATEADD('MINUTE', -37, NOW())),
(1201, 26.39, DATEADD('MINUTE', -36, NOW())),
(1201, 25.62, DATEADD('MINUTE', -35, NOW())),
(1201, 24.79, DATEADD('MINUTE', -34, NOW())),
(1201, 23.91, DATEADD('MINUTE', -33, NOW())),
(1201, 23.00, DATEADD('MINUTE', -32, NOW())),
(1201, 22.09, DATEADD('MINUTE', -31, NOW())),
(1201, 21.21, DATEADD('MINUTE', -30, NOW())),
(1201, 20.38, DATEADD('MINUTE', -29, NOW())),
(1201, 19.61, DATEADD('MINUTE', -28, NOW())),
(1201, 18.91, DATEADD('MINUTE', -27, NOW())),
(1201, 18.31, DATEADD('MINUTE', -26, NOW())),
(1201, 17.79, DATEADD('MINUTE', -25, NOW())),
(1201, 17.39, DATEADD('MINUTE', -24, NOW())),
(1201, 17.09, DATEADD('MINUTE', -23, NOW())),
(1201, 16.91, DATEADD('MINUTE', -22, NOW())),
(1201, 16.85, DATEADD('MINUTE', -21, NOW())),
(1201, 16.91, DATEADD('MINUTE', -20, NOW())),
(1201, 17.09, DATEADD('MINUTE', -19, NOW())),
(1201, 17.39, DATEADD('MINUTE', -18, NOW())),
(1201, 17.79, DATEADD('MINUTE', -17, NOW())),
(1201, 18.31, DATEADD('MINUTE', -16, NOW())),
(1201, 18.91, DATEADD('MINUTE', -15, NOW())),
(1201, 19.61, DATEADD('MINUTE', -14, NOW())),
(1201, 20.38, DATEADD('MINUTE', -13, NOW())),
(1201, 21.21, DATEADD('MINUTE', -12, NOW())),
(1201, 22.09, DATEADD('MINUTE', -11, NOW())),
(1201, 23.00, DATEADD('MINUTE', -10, NOW())),
(1201, 23.91, DATEADD('MINUTE', -9, NOW())),
(1201, 24.79, DATEADD('MINUTE', -8, NOW())),
(1201, 25.62, DATEADD('MINUTE', -7, NOW())),
(1201, 26.39, DATEADD('MINUTE', -6, NOW())),
(1201, 27.09, DATEADD('MINUTE', -5, NOW())),
(1201, 27.69, DATEADD('MINUTE', -4, NOW())),
(1201, 28.21, DATEADD('MINUTE', -3, NOW())),
(1201, 28.61, DATEADD('MINUTE', -2, NOW())),
(1201, 28.91, DATEADD('MINUTE', -1, NOW())),
(1201, 29.09, NOW());

-- sim_outdoor_temp (monitor=1216): 12-28C sine, 2 hours
MERGE INTO t_sample_float (monitor, "value", moment) KEY (monitor, moment) VALUES
(1216, 20.00, DATEADD('MINUTE', -120, NOW())),
(1216, 18.80, DATEADD('MINUTE', -110, NOW())),
(1216, 17.30, DATEADD('MINUTE', -100, NOW())),
(1216, 15.50, DATEADD('MINUTE', -90, NOW())),
(1216, 14.00, DATEADD('MINUTE', -80, NOW())),
(1216, 13.10, DATEADD('MINUTE', -70, NOW())),
(1216, 12.80, DATEADD('MINUTE', -60, NOW())),
(1216, 13.10, DATEADD('MINUTE', -50, NOW())),
(1216, 15.00, DATEADD('MINUTE', -40, NOW())),
(1216, 18.50, DATEADD('MINUTE', -30, NOW())),
(1216, 22.80, DATEADD('MINUTE', -20, NOW())),
(1216, 26.50, DATEADD('MINUTE', -10, NOW())),
(1216, 28.00, NOW());

-- sim_battery_level (monitor=1206): ramp 100->85%, 2 hours
MERGE INTO t_sample_float (monitor, "value", moment) KEY (monitor, moment) VALUES
(1206, 100.0, DATEADD('MINUTE', -120, NOW())),
(1206, 99.0,  DATEADD('MINUTE', -110, NOW())),
(1206, 97.5,  DATEADD('MINUTE', -100, NOW())),
(1206, 96.0,  DATEADD('MINUTE', -90, NOW())),
(1206, 94.5,  DATEADD('MINUTE', -80, NOW())),
(1206, 93.0,  DATEADD('MINUTE', -70, NOW())),
(1206, 91.5,  DATEADD('MINUTE', -60, NOW())),
(1206, 90.0,  DATEADD('MINUTE', -50, NOW())),
(1206, 88.5,  DATEADD('MINUTE', -40, NOW())),
(1206, 87.0,  DATEADD('MINUTE', -30, NOW())),
(1206, 86.0,  DATEADD('MINUTE', -20, NOW())),
(1206, 85.5,  DATEADD('MINUTE', -10, NOW())),
(1206, 85.0,  NOW());

-- sim_voltage_l1 (monitor=1211): 217-223V sine, 2 hours
MERGE INTO t_sample_float (monitor, "value", moment) KEY (monitor, moment) VALUES
(1211, 220.0, DATEADD('MINUTE', -120, NOW())),
(1211, 221.5, DATEADD('MINUTE', -110, NOW())),
(1211, 222.5, DATEADD('MINUTE', -100, NOW())),
(1211, 223.0, DATEADD('MINUTE', -90, NOW())),
(1211, 222.5, DATEADD('MINUTE', -80, NOW())),
(1211, 221.5, DATEADD('MINUTE', -70, NOW())),
(1211, 220.0, DATEADD('MINUTE', -60, NOW())),
(1211, 218.5, DATEADD('MINUTE', -50, NOW())),
(1211, 217.5, DATEADD('MINUTE', -40, NOW())),
(1211, 217.0, DATEADD('MINUTE', -30, NOW())),
(1211, 217.5, DATEADD('MINUTE', -20, NOW())),
(1211, 218.5, DATEADD('MINUTE', -10, NOW())),
(1211, 220.0, NOW());

-- ============================================================================
-- 10. 告警历史 (近7天，用于看板/统计演示)
-- asset_id 存监测器运行时 ID (t_probe.id)；
-- t_error_message_log.state: 1=ERROR 2=WARNING；t_alarm_message.state: 1=待处理 2=已处理
-- ============================================================================
MERGE INTO t_error_message_log (id, asset_id, monitor_name, error_message, "value", state, warn_id, time) KEY (id) VALUES
    -- 今日
    (901, 1201, '送风温度', 'AHU送风温度偏高',   '32.4',  2, 2, DATEADD('MINUTE', -420, NOW())),
    (902, 1208, '输入电压', 'UPS输入电压偏低',   '195.4', 1, 4, DATEADD('MINUTE', -330, NOW())),
    (903, 1211, 'L1电压',   'PDU L1电压偏低',    '196.8', 1, 3, DATEADD('MINUTE', -240, NOW())),
    (904, 1206, '电池电量', 'UPS电池电量偏低',   '23.5',  1, 3, DATEADD('MINUTE', -150, NOW())),
    (905, 1211, 'L1电压',   'PDU L1电压偏低',    '195.9', 1, 3, DATEADD('MINUTE',  -90, NOW())),
    -- 昨日
    (906, 1208, '输入电压', 'UPS输入电压偏低',   '194.1', 1, 4, DATEADD('MINUTE', -1700, NOW())),
    (907, 1201, '送风温度', 'AHU送风温度偏高',   '32.8',  2, 2, DATEADD('MINUTE', -1650, NOW())),
    (908, 1206, '电池电量', 'UPS电池电量偏低',   '24.0',  1, 3, DATEADD('MINUTE', -1560, NOW())),
    (909, 1211, 'L1电压',   'PDU L1电压偏低',    '197.3', 1, 3, DATEADD('MINUTE', -1500, NOW())),
    -- 前两天
    (910, 1201, '送风温度', 'AHU送风温度偏高',   '31.9',  2, 2, DATEADD('MINUTE', -3160, NOW())),
    (911, 1216, '室外温度', '室外温度超限',      '36.2',  2, 2, DATEADD('MINUTE', -3100, NOW())),
    (912, 1206, '电池电量', 'UPS电池电量偏低',   '24.8',  1, 3, DATEADD('MINUTE', -3000, NOW())),
    (913, 1211, 'L1电压',   'PDU L1电压偏低',    '197.8', 1, 3, DATEADD('MINUTE', -2950, NOW())),
    -- 前三天
    (914, 1201, '送风温度', 'AHU送风温度偏高',   '32.1',  2, 2, DATEADD('MINUTE', -4600, NOW())),
    (915, 1206, '电池电量', 'UPS电池电量偏低',   '24.3',  1, 3, DATEADD('MINUTE', -4520, NOW())),
    (916, 1206, '电池电量', 'UPS电池电量偏低',   '23.9',  1, 3, DATEADD('MINUTE', -4460, NOW())),
    (917, 1211, 'L1电压',   'PDU L1电压偏低',    '196.2', 1, 3, DATEADD('MINUTE', -4400, NOW())),
    -- 前四天
    (918, 1201, '送风温度', 'AHU送风温度偏高',   '31.6',  2, 2, DATEADD('MINUTE', -5980, NOW())),
    (919, 1208, '输入电压', 'UPS输入电压偏低',   '193.8', 1, 4, DATEADD('MINUTE', -5910, NOW())),
    (920, 1211, 'L1电压',   'PDU L1电压偏低',    '195.4', 1, 3, DATEADD('MINUTE', -5850, NOW())),
    -- 前五天
    (921, 1206, '电池电量', 'UPS电池电量偏低',   '25.1',  1, 3, DATEADD('MINUTE', -7440, NOW())),
    (922, 1216, '室外温度', '室外温度超限',      '35.8',  2, 2, DATEADD('MINUTE', -7360, NOW())),
    (923, 1211, 'L1电压',   'PDU L1电压偏低',    '198.2', 1, 3, DATEADD('MINUTE', -7300, NOW())),
    -- 前六天
    (924, 1206, '电池电量', 'UPS电池电量偏低',   '24.6',  1, 3, DATEADD('MINUTE', -8820, NOW())),
    (925, 1201, '送风温度', 'AHU送风温度偏高',   '32.5',  2, 2, DATEADD('MINUTE', -8760, NOW())),
    (926, 1211, 'L1电压',   'PDU L1电压偏低',    '196.5', 1, 3, DATEADD('MINUTE', -8700, NOW()));

MERGE INTO t_alarm_message (id, log_id, caption, state, auto, alarm_time, recovered, warn_id, device_id) KEY (id) VALUES
    -- 今日：904/905 待处理，其余已处理
    (901, 901, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE',  -420, NOW()), 1, 2, 1101),
    (902, 902, 'UPS输入电压偏低', 2, 1, DATEADD('MINUTE',  -330, NOW()), 1, 4, 1102),
    (903, 903, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE',  -240, NOW()), 1, 3, 1103),
    (904, 904, 'UPS电池电量偏低', 1, 1, DATEADD('MINUTE',  -150, NOW()), 0, 3, 1102),
    (905, 905, 'PDU L1电压偏低',  1, 1, DATEADD('MINUTE',   -90, NOW()), 0, 3, 1103),
    (906, 906, 'UPS输入电压偏低', 2, 1, DATEADD('MINUTE', -1700, NOW()), 1, 4, 1102),
    (907, 907, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -1650, NOW()), 1, 2, 1101),
    (908, 908, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -1560, NOW()), 1, 3, 1102),
    (909, 909, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -1500, NOW()), 1, 3, 1103),
    (910, 910, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -3160, NOW()), 1, 2, 1101),
    (911, 911, '室外温度超限',    2, 1, DATEADD('MINUTE', -3100, NOW()), 1, 2, 1104),
    (912, 912, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -3000, NOW()), 1, 3, 1102),
    (913, 913, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -2950, NOW()), 1, 3, 1103),
    (914, 914, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -4600, NOW()), 1, 2, 1101),
    (915, 915, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -4520, NOW()), 1, 3, 1102),
    (916, 916, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -4460, NOW()), 1, 3, 1102),
    (917, 917, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -4400, NOW()), 1, 3, 1103),
    (918, 918, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -5980, NOW()), 1, 2, 1101),
    (919, 919, 'UPS输入电压偏低', 2, 1, DATEADD('MINUTE', -5910, NOW()), 1, 4, 1102),
    (920, 920, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -5850, NOW()), 1, 3, 1103),
    (921, 921, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -7440, NOW()), 1, 3, 1102),
    (922, 922, '室外温度超限',    2, 1, DATEADD('MINUTE', -7360, NOW()), 1, 2, 1104),
    (923, 923, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -7300, NOW()), 1, 3, 1103),
    (924, 924, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -8820, NOW()), 1, 3, 1102),
    (925, 925, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -8760, NOW()), 1, 2, 1101),
    (926, 926, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -8700, NOW()), 1, 3, 1103);

-- 上一期（第8~13天前）：统计"环比对比"面板的上期数据，全部已处理
MERGE INTO t_error_message_log (id, asset_id, monitor_name, error_message, "value", state, warn_id, time) KEY (id) VALUES
    (927, 1206, '电池电量', 'UPS电池电量偏低',   '24.9',  1, 3, DATEADD('MINUTE', -11680, NOW())),
    (928, 1211, 'L1电压',   'PDU L1电压偏低',    '196.9', 1, 3, DATEADD('MINUTE', -11640, NOW())),
    (929, 1201, '送风温度', 'AHU送风温度偏高',   '32.2',  2, 2, DATEADD('MINUTE', -11600, NOW())),
    (930, 1208, '输入电压', 'UPS输入电压偏低',   '194.6', 1, 4, DATEADD('MINUTE', -12960, NOW())),
    (931, 1216, '室外温度', '室外温度超限',      '36.5',  2, 2, DATEADD('MINUTE', -12920, NOW())),
    (932, 1206, '电池电量', 'UPS电池电量偏低',   '25.3',  1, 3, DATEADD('MINUTE', -14440, NOW())),
    (933, 1211, 'L1电压',   'PDU L1电压偏低',    '197.1', 1, 3, DATEADD('MINUTE', -14400, NOW())),
    (934, 1201, '送风温度', 'AHU送风温度偏高',   '31.7',  2, 2, DATEADD('MINUTE', -14360, NOW())),
    (935, 1208, '输入电压', 'UPS输入电压偏低',   '194.3', 1, 4, DATEADD('MINUTE', -15840, NOW())),
    (936, 1206, '电池电量', 'UPS电池电量偏低',   '24.4',  1, 3, DATEADD('MINUTE', -15800, NOW())),
    (937, 1211, 'L1电压',   'PDU L1电压偏低',    '197.5', 1, 3, DATEADD('MINUTE', -17280, NOW())),
    (938, 1201, '送风温度', 'AHU送风温度偏高',   '31.4',  2, 2, DATEADD('MINUTE', -17240, NOW())),
    (939, 1206, '电池电量', 'UPS电池电量偏低',   '25.6',  1, 3, DATEADD('MINUTE', -18760, NOW())),
    (940, 1208, '输入电压', 'UPS输入电压偏低',   '193.6', 1, 4, DATEADD('MINUTE', -18720, NOW())),
    (941, 1216, '室外温度', '室外温度超限',      '35.2',  2, 2, DATEADD('MINUTE', -18680, NOW()));

MERGE INTO t_alarm_message (id, log_id, caption, state, auto, alarm_time, recovered, warn_id, device_id) KEY (id) VALUES
    (927, 927, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -11680, NOW()), 1, 3, 1102),
    (928, 928, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -11640, NOW()), 1, 3, 1103),
    (929, 929, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -11600, NOW()), 1, 2, 1101),
    (930, 930, 'UPS输入电压偏低', 2, 1, DATEADD('MINUTE', -12960, NOW()), 1, 4, 1102),
    (931, 931, '室外温度超限',    2, 1, DATEADD('MINUTE', -12920, NOW()), 1, 2, 1104),
    (932, 932, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -14440, NOW()), 1, 3, 1102),
    (933, 933, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -14400, NOW()), 1, 3, 1103),
    (934, 934, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -14360, NOW()), 1, 2, 1101),
    (935, 935, 'UPS输入电压偏低', 2, 1, DATEADD('MINUTE', -15840, NOW()), 1, 4, 1102),
    (936, 936, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -15800, NOW()), 1, 3, 1102),
    (937, 937, 'PDU L1电压偏低',  2, 1, DATEADD('MINUTE', -17280, NOW()), 1, 3, 1103),
    (938, 938, 'AHU送风温度偏高', 2, 1, DATEADD('MINUTE', -17240, NOW()), 1, 2, 1101),
    (939, 939, 'UPS电池电量偏低', 2, 1, DATEADD('MINUTE', -18760, NOW()), 1, 3, 1102),
    (940, 940, 'UPS输入电压偏低', 2, 1, DATEADD('MINUTE', -18720, NOW()), 1, 4, 1102),
    (941, 941, '室外温度超限',    2, 1, DATEADD('MINUTE', -18680, NOW()), 1, 2, 1104);

-- ============================================================================
-- 11. 工单历史 (近7天，用于看板/统计演示)
-- ============================================================================
MERGE INTO t_work_order (id, order_no, title, description, type, source, device_id, priority, status, assignee_id, creator_id, due_time, resolution, created_at, updated_at, closed_at) KEY (id) VALUES
    (901, 'WO-2026-0901', 'PDU配电柜L1电压偏低处理', 'PDU L1相电压低于告警阈值，需要现场排查供电回路', 'REPAIR', 'ALARM_AUTO', 1103, 1, 'CREATED',    NULL, 1, DATEADD('HOUR', 22, NOW()), NULL, DATEADD('MINUTE',  -90, NOW()), DATEADD('MINUTE',  -90, NOW()), NULL),
    (902, 'WO-2026-0902', 'UPS电池电量告警核查',     'UPS电池电量持续偏低，核查电池组状态并安排充电', 'MAINTENANCE', 'ALARM_AUTO', 1102, 2, 'ASSIGNED',   1, 1, DATEADD('HOUR', 8, NOW()),  NULL, DATEADD('MINUTE', -1500, NOW()), DATEADD('MINUTE', -1400, NOW()), NULL),
    (903, 'WO-2026-0903', 'AHU空调机组过滤网更换',   '按季度保养计划更换AHU过滤网并清洗表冷器', 'MAINTENANCE', 'MANUAL', 1101, 3, 'PROCESSING', 1, 1, DATEADD('DAY', 1, NOW()),   NULL, DATEADD('MINUTE', -1740, NOW()), DATEADD('MINUTE', -1200, NOW()), NULL),
    (904, 'WO-2026-0904', 'UPS输入电压异常检修',     'UPS输入电压低于下限，检修输入回路', 'REPAIR', 'ALARM_AUTO', 1102, 2, 'CLOSED', 1, 1, DATEADD('MINUTE', -1460, NOW()), '更换输入滤波电容，电压恢复正常', DATEADD('MINUTE', -2900, NOW()), DATEADD('MINUTE', -2660, NOW()), DATEADD('MINUTE', -2660, NOW())),
    (905, 'WO-2026-0905', 'AHU送风温度偏高处理',     'AHU送风温度超上限，排查冷媒与水阀', 'REPAIR', 'ALARM_AUTO', 1101, 2, 'CLOSED', 1, 1, DATEADD('MINUTE', -2910, NOW()), '清洗表冷器并调整水阀开度', DATEADD('MINUTE', -4350, NOW()), DATEADD('MINUTE', -4170, NOW()), DATEADD('MINUTE', -4170, NOW())),
    (906, 'WO-2026-0906', 'PDU配电柜月度保养',       'PDU月度保养：紧固接线、除尘、测温', 'MAINTENANCE', 'MANUAL', 1103, 3, 'CLOSED', 1, 1, DATEADD('MINUTE', -4360, NOW()), '完成紧固与除尘，温升正常', DATEADD('MINUTE', -5800, NOW()), DATEADD('MINUTE', -5600, NOW()), DATEADD('MINUTE', -5600, NOW())),
    (907, 'WO-2026-0907', '气象站传感器数据校准',     '室外温度读数偏差校准', 'INSPECTION', 'MANUAL', 1104, 4, 'CLOSED', 1, 1, DATEADD('MINUTE', -5860, NOW()), '校准完成，比对误差0.3℃', DATEADD('MINUTE', -7300, NOW()), DATEADD('MINUTE', -7180, NOW()), DATEADD('MINUTE', -7180, NOW())),
    (908, 'WO-2026-0908', 'UPS电池组老化更换',       'UPS电池组容量衰减，整体更换并做充放电测试', 'REPAIR', 'MANUAL', 1102, 1, 'CLOSED', 1, 1, DATEADD('MINUTE', -7360, NOW()), '更换电池组，放电测试通过', DATEADD('MINUTE', -8800, NOW()), DATEADD('MINUTE', -7000, NOW()), DATEADD('MINUTE', -7000, NOW())),
    (909, 'WO-2026-0909', 'PDU重复告警误报复核',     '经复核为电压采样抖动，无需处理', 'OTHER', 'MANUAL', 1103, 4, 'CANCELLED', NULL, 1, DATEADD('MINUTE', -3060, NOW()), NULL, DATEADD('MINUTE', -4500, NOW()), DATEADD('MINUTE', -4300, NOW()), NULL);

-- ============================================================================
-- 12. 巡检计划与任务 (用于看板/统计演示；时间相对 NOW() 生成)
-- ============================================================================
MERGE INTO t_inspection_plan (id, name, description, cron_expression, enabled, default_assignee_id, creator_id) KEY (id) VALUES
    (901, '机房设备例行巡检', '对机房主要设备进行例行巡视检查', '0 8 * * *',    1, 1, 1),
    (902, '配电系统专项巡检', 'UPS/PDU配电系统负载与温度专项巡检', '0 8,14 * * *', 1, 1, 1);

MERGE INTO t_inspection_plan_device (id, plan_id, device_id) KEY (id) VALUES
    (951, 901, 1101),
    (952, 901, 1104),
    (953, 902, 1102),
    (954, 902, 1103);

MERGE INTO t_inspection_task (id, plan_id, task_no, status, assignee_id, scheduled_time, started_at, completed_at, created_at) KEY (id) VALUES
    -- 今日：4单，2完成 → 完成率50%
    (911, 901, 'INS-0911', 'COMPLETED',   1, DATEADD('MINUTE',  -300, NOW()), NULL, DATEADD('MINUTE', -240, NOW()), DATEADD('DAY', -7, NOW())),
    (912, 902, 'INS-0912', 'COMPLETED',   1, DATEADD('MINUTE',  -180, NOW()), NULL, DATEADD('MINUTE', -120, NOW()), DATEADD('DAY', -7, NOW())),
    (913, 902, 'INS-0913', 'IN_PROGRESS', 1, DATEADD('MINUTE',   -60, NOW()), DATEADD('MINUTE', -50, NOW()), NULL, DATEADD('DAY', -7, NOW())),
    (914, 901, 'INS-0914', 'PENDING',  NULL, DATEADD('MINUTE',   180, NOW()), NULL, NULL, DATEADD('DAY', -7, NOW())),
    -- 历史已完成
    (915, 901, 'INS-0915', 'COMPLETED', 1, DATEADD('MINUTE', -1740, NOW()), NULL, DATEADD('MINUTE', -1680, NOW()), DATEADD('DAY', -7, NOW())),
    (916, 902, 'INS-0916', 'COMPLETED', 1, DATEADD('MINUTE', -1560, NOW()), NULL, DATEADD('MINUTE', -1500, NOW()), DATEADD('DAY', -7, NOW())),
    (917, 902, 'INS-0917', 'COMPLETED', 1, DATEADD('MINUTE', -1380, NOW()), NULL, DATEADD('MINUTE', -1320, NOW()), DATEADD('DAY', -7, NOW())),
    (918, 901, 'INS-0918', 'COMPLETED', 1, DATEADD('MINUTE', -3180, NOW()), NULL, DATEADD('MINUTE', -3120, NOW()), DATEADD('DAY', -7, NOW())),
    (919, 902, 'INS-0919', 'COMPLETED', 1, DATEADD('MINUTE', -3000, NOW()), NULL, DATEADD('MINUTE', -2940, NOW()), DATEADD('DAY', -7, NOW()));

-- ============================================================================
-- 13. 维保记录 (近7天，用于看板/统计演示；与工单 904-908 的处理动作对应)
-- ============================================================================
MERGE INTO t_maintenance_record (id, device_id, type, title, description, performer_id, creator_id, performed_at, cost, result, next_maintenance_date, created_at, updated_at) KEY (id) VALUES
    (961, 1102, 'REPAIR',      'UPS输入回路检修',   '更换输入滤波电容，电压恢复正常', 1, 1, DATEADD('MINUTE',  -2660, NOW()),  850.00, '电压稳定在220V', NULL, DATEADD('MINUTE',  -2660, NOW()), DATEADD('MINUTE',  -2660, NOW())),
    (962, 1101, 'MAINTENANCE', 'AHU表冷器清洗',     '清洗表冷器并调整水阀开度',       1, 1, DATEADD('MINUTE',  -4170, NOW()),  600.00, '送风温度回落至26℃', NULL, DATEADD('MINUTE',  -4170, NOW()), DATEADD('MINUTE',  -4170, NOW())),
    (963, 1103, 'MAINTENANCE', 'PDU月度保养',       '紧固接线、除尘、红外测温',       1, 1, DATEADD('MINUTE',  -5600, NOW()),  300.00, '温升正常', NULL, DATEADD('MINUTE',  -5600, NOW()), DATEADD('MINUTE',  -5600, NOW())),
    (964, 1104, 'INSPECTION',  '气象站传感器校准',  '室外温度传感器偏差校准',         1, 1, DATEADD('MINUTE',  -7180, NOW()),  200.00, '比对误差0.3℃', NULL, DATEADD('MINUTE',  -7180, NOW()), DATEADD('MINUTE',  -7180, NOW())),
    (965, 1102, 'REPAIR',      'UPS电池组更换',     '整组更换并做充放电测试',         1, 1, DATEADD('MINUTE',  -7000, NOW()), 5200.00, '放电测试通过', NULL, DATEADD('MINUTE',  -7000, NOW()), DATEADD('MINUTE',  -7000, NOW())),
    (966, 1101, 'MAINTENANCE', 'AHU过滤网更换',     '季度保养：更换过滤网',           1, 1, DATEADD('MINUTE',  -1200, NOW()),  260.00, '压差恢复正常', NULL, DATEADD('MINUTE',  -1200, NOW()), DATEADD('MINUTE',  -1200, NOW())),
    (967, 1103, 'INSPECTION',  '配电柜红外测温',    'PDU配电柜端子红外测温巡检',      1, 1, DATEADD('MINUTE',  -3000, NOW()),  150.00, '无过热点', NULL, DATEADD('MINUTE',  -3000, NOW()), DATEADD('MINUTE',  -3000, NOW())),
    (968, 1102, 'MAINTENANCE', 'UPS电池组巡检',     '电池组季度巡检与端电压记录',     1, 1, DATEADD('MINUTE',  -4460, NOW()),  180.00, '端电压一致', NULL, DATEADD('MINUTE',  -4460, NOW()), DATEADD('MINUTE',  -4460, NOW()));

-- ============================================================================
-- 14. 巡检结果 (含异常项，用于巡检统计/异常分析演示)
-- check_result: NORMAL=正常 ABNORMAL=异常；created_at 对应任务完成时间
-- ============================================================================
MERGE INTO t_inspection_result (id, task_id, device_id, template_id, item_name, expected_value, check_result, actual_value, remark, created_at) KEY (id) VALUES
    (981, 911, 1101, 1, '机房环境温度检查',   '18~28℃',   'NORMAL',   '24.1℃',          NULL,           DATEADD('MINUTE',  -240, NOW())),
    (982, 911, 1104, 2, '室外气象传感器检查', '误差≤0.5℃', 'ABNORMAL', '偏差0.8℃',        '需重新校准',   DATEADD('MINUTE',  -240, NOW())),
    (983, 912, 1102, 1, 'UPS输出电压检查',   '220V±3%',   'NORMAL',   '221.4V',          NULL,           DATEADD('MINUTE',  -120, NOW())),
    (984, 912, 1103, 2, 'PDU负载不平衡检查', '≤15%',      'ABNORMAL', 'L1负载率62%',     '建议调相',     DATEADD('MINUTE',  -120, NOW())),
    (985, 915, 1101, 1, 'AHU送风温度检查',   '≤26℃',     'NORMAL',   '25.2℃',          NULL,           DATEADD('MINUTE', -1680, NOW())),
    (986, 916, 1102, 2, 'UPS电池组外观检查', '无漏液',    'ABNORMAL', '2号电池端子漏液', '已转维保',     DATEADD('MINUTE', -1500, NOW())),
    (987, 917, 1103, 1, '接线端子紧固检查',  '无松动',    'NORMAL',   '全部紧固',        NULL,           DATEADD('MINUTE', -1320, NOW())),
    (988, 918, 1104, 2, '防雷模块状态检查',  '正常',      'NORMAL',   '显示正常',        NULL,           DATEADD('MINUTE', -3120, NOW())),
    (989, 919, 1102, 3, 'UPS风扇滤网检查',   '清洁',      'NORMAL',   '轻微积尘已清理',  NULL,           DATEADD('MINUTE', -2940, NOW()));
