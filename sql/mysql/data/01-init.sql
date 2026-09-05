-- ============================================================================
-- 数据库: db_systar
-- 产品: systar
-- 说明:
--   本文件包含系统初始化数据，包括：
--   - 代码字典：资产类型、监测器分类、设备分类等
--   - 系统配置默认值
--   - 事件级别默认值
--   - 演示用资产树（空间/设备/服务/监测器/控制器）
-- ============================================================================

-- ============================================================================
-- 1. 事件级别
-- ============================================================================
INSERT INTO t_event_rank (id, name) VALUES (1, '提示') ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_event_rank (id, name) VALUES (2, '警告') ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_event_rank (id, name) VALUES (3, '重要') ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_event_rank (id, name) VALUES (4, '紧急') ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================================
-- 2. 代码分类目录
-- ============================================================================
INSERT INTO t_code_catalog (id, name) VALUES (1, '资产类型')         ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (2, '设备分类')         ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (3, '监测器分类')       ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (4, '控制器分类')       ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (5, '空间类型')         ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (6, '监测数据类型')     ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (7, '报警方式')         ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO t_code_catalog (id, name) VALUES (8, '服务驱动类型')     ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================================
-- 3. 代码字典 - 资产类型 (catalogId=1)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (101, 1, 'SPACE',   '空间',     NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (102, 1, 'DEVICE',  '设备',     NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (103, 1, 'SERVICE', '监控服务', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (104, 1, 'PROBE',   '监测器',   NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (105, 1, 'CONTROL', '控制器',   NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 4. 代码字典 - 设备分类 (catalogId=2)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (201, 2, 'SENSOR',         '传感器',     NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (202, 2, 'METER',          '仪表',       NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (203, 2, 'CONTROLLER',     '控制器设备', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (204, 2, 'GATEWAY',        '网关',       NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (205, 2, 'CAMERA',         '摄像头',     NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (206, 2, 'AIR_CONDITIONER','空调',       NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (207, 2, 'LIGHTING',       '照明设备',   NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (208, 2, 'UPS',            'UPS电源',    NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (209, 2, 'ELEVATOR',       '电梯',       NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (210, 2, 'PUMP',           '水泵',       NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 5. 代码字典 - 监测器分类 (catalogId=3)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (301, 3, 'TEMPERATURE',      '温度',     201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (302, 3, 'HUMIDITY',         '湿度',     201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (303, 3, 'ELECTRIC_CURRENT', '电流',     201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (304, 3, 'VOLTAGE',          '电压',     201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (305, 3, 'POWER',            '功率',     201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (306, 3, 'ENERGY',           '能耗',     202) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (307, 3, 'WATER_FLOW',       '水流量',   202) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (308, 3, 'AIR_PRESSURE',     '气压',     201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (309, 3, 'STATUS',           '运行状态', 201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (310, 3, 'SWITCH',           '开关量',   201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (311, 3, 'CO2',              'CO2浓度',  201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (312, 3, 'PM25',             'PM2.5',    201) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 6. 代码字典 - 控制器分类 (catalogId=4)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (401, 4, 'SWITCH_CTRL',      '开关控制', 203) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (402, 4, 'DIMMING_CTRL',     '调光控制', 203) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (403, 4, 'VALVE_CTRL',       '阀门控制', 203) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (404, 4, 'TEMP_SET_CTRL',    '温度设定', 203) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (405, 4, 'SPEED_CTRL',       '速度控制', 203) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 7. 代码字典 - 空间类型 (catalogId=5)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (501, 5, 'BUILDING',   '建筑',     NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (502, 5, 'FLOOR',      '楼层',     501) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (503, 5, 'ROOM',       '房间',     502) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (504, 5, 'CORRIDOR',   '走廊',     502) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (505, 5, 'MACHINE_ROOM','机房',    503) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (506, 5, 'OFFICE',     '办公室',   503) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (507, 5, 'WAREHOUSE',  '仓库',     503) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (508, 5, 'PARKING',    '停车场',   502) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 8. 代码字典 - 监测数据类型 (catalogId=6)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (601, 6, 'FLOAT',    '浮点数', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (602, 6, 'INT',      '整数',   NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (603, 6, 'BOOLEAN',  '布尔值', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (604, 6, 'STRING',   '字符串', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (605, 6, 'TIMESPAN', '时间跨度', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 9. 代码字典 - 报警方式 (catalogId=7)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (701, 7, 'SOUND', '声音报警', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (702, 7, 'EMAIL', '邮件报警', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (703, 7, 'UI',    '界面报警', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (704, 7, 'SMS',   '短信报警', NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 10. 代码字典 - 服务驱动类型 (catalogId=8)
-- ============================================================================
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (801, 8, 'MODBUS_TCP',  'Modbus TCP',  NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (802, 8, 'MODBUS_RTU',  'Modbus RTU',  NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (803, 8, 'BACNET',      'BACnet',      NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (804, 8, 'OPC_UA',      'OPC UA',      NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (805, 8, 'MQTT',        'MQTT',        NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (806, 8, 'SNMP',        'SNMP',        NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);
INSERT INTO t_code_dict (id, catalog_id, name, caption, parent) VALUES (807, 8, 'HTTP',        'HTTP',        NULL) ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ============================================================================
-- 11. 系统配置默认值
-- ============================================================================
INSERT INTO t_system_setting (id, `config_key`, value, description) VALUES
    (1, 'system.name',              'Systar Monitor',       '系统名称'),
    (2, 'system.version',           '1.0.0',                '系统版本'),
    (3, 'monitor.default_interval', '10s',                  '默认监测间隔(TimeSpan)'),
    (4, 'monitor.default_saving',   '1m',                   '默认保存间隔(TimeSpan)'),
    (5, 'alarm.max_retry',          '3',                    '报警最大重试次数'),
    (6, 'alarm.sound_enabled',      'true',                 '是否启用声音报警'),
    (7, 'linkage.enabled',          'true',                 '是否启用联动规则'),
    (9,  'ops.workorder.alarm_trigger_levels', '2,3,4',  '自动触发工单的告警等级'),
    (10, 'ops.workorder.sla_hours_urgent',      '4',      '紧急工单SLA(小时)'),
    (11, 'ops.workorder.sla_hours_high',        '8',      '高优先级SLA(小时)'),
    (12, 'ops.workorder.sla_hours_medium',      '24',     '中优先级SLA(小时)'),
    (13, 'ops.workorder.sla_hours_low',         '72',     '低优先级SLA(小时)'),
    (14, 'ops.ledger.warranty_warn_days',       '30',     '保修到期预警天数'),
    (15, 'ops.inspection.task_timeout_hours',   '72',     '巡检任务超时(小时)'),
    (16, 'ops.analysis.anomaly_threshold',      '2.0',    'Z-Score异常阈值'),
    (17, 'ops.analysis.moving_avg_window',      '7',      '移动平均窗口'),
    (18, 'ops.analysis.health_weight_alarm',    '0.4',    '健康评估-告警权重'),
    (19, 'ops.analysis.health_weight_maintenance','0.3',  '健康评估-维护权重'),
    (20, 'ops.analysis.health_weight_availability','0.3', '健康评估-在线率权重'),
    (100, 'data_retention.sample_days',     '90',  '采样数据保留天数'),
    (101, 'data_retention.alarm_log_days',  '180', '告警日志保留天数'),
    (102, 'data_retention.linkage_log_days','180', '联动日志保留天数'),
    (103, 'data_retention.enabled',         'true','是否启用自动数据清理')
ON DUPLICATE KEY UPDATE value = VALUES(value);

-- ============================================================================
-- 12. 演示用资产树
-- ============================================================================

-- ---- 12.1 空间 ----
INSERT INTO t_space (id, name, caption, parent, area, sequence, show_in_client) VALUES
    (1,  'root',           '智慧园区',       0,   NULL, 0,  1),
    (10, 'building_A',     'A栋办公楼',     1,   5000, 1,  1),
    (11, 'floor_1A',       'A栋1层',        10,  1200, 1,  1),
    (12, 'floor_2A',       'A栋2层',        10,  1200, 2,  1),
    (13, 'room_101',       '101会议室',     11,   60,  1,  1),
    (14, 'room_102',       '102办公室',     11,   80,  2,  1),
    (15, 'machine_room',   '机房',          11,  100,  3,  1),
    (16, 'corridor_1a',    '1层走廊',       11,  200,  4,  2)
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ---- 12.2 监控服务 ----
INSERT INTO t_service (id, name, caption, parent, mode, driver_class, max_connections, type_name) VALUES
    (100, 'modbus_tcp_svc',  'Modbus TCP服务',  1, 0, 'com.systar.monitor.drivers.modbus.ModbusService',    10, 'ModbusTcpMaster'),
    (101, 'mqtt_svc',        'MQTT服务',        1, 1, 'com.systar.monitor.drivers.mqtt.MqttService',         5,  NULL),
    (102, 'opcua_svc',       'OPC UA服务',      1, 0, 'com.systar.monitor.drivers.opcua.OpcUaService',      8,  NULL),
    (103, 'iec104_svc',      'IEC 104服务',     1, 0, 'com.systar.monitor.drivers.iec104.Iec104Service',    5,  NULL),
    (104, 'bacnet_svc',      'BACnet服务',      1, 0, 'com.systar.monitor.drivers.bacnet.BacnetService',    5,  NULL),
    (105, 'sim_svc',         '模拟数据服务',     1, 0, 'com.systar.monitor.drivers.simulate.SimulateService', 0, 'SimulateService')
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ---- 12.3 设备 ----
INSERT INTO t_device (id, name, caption, parent, catalog, vendor, purchase_date, warranty_date, health_index) VALUES
    (1001, 'th_sensor_001',   '温湿度传感器-001',   13, 201, 'Systar',   '2025-01-15 00:00:00', '2028-01-15', 0.95),
    (1002, 'th_sensor_002',   '温湿度传感器-002',   14, 201, 'Systar',   '2025-01-15 00:00:00', '2028-01-15', 0.92),
    (1003, 'elec_meter_001',  '电表-001',           15, 202, 'ABB',      '2024-06-01 00:00:00', '2027-06-01', 0.98),
    (1004, 'ups_001',         'UPS电源-001',        15, 208, 'APC',      '2024-03-01 00:00:00', '2027-03-01', 0.90),
    (1005, 'ac_001',          '中央空调-001',       13, 206, 'Daikin',   '2024-08-01 00:00:00', '2027-08-01', 0.88),
    (1006, 'light_ctrl_001',  '照明控制器-001',     11, 203, 'Philips',  '2025-02-01 00:00:00', '2028-02-01', 0.97),
    (1007, 'gateway_001',     '网关-001',           15, 204, 'Systar',   '2025-01-01 00:00:00', '2028-01-01', 0.99)
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

-- ---- 12.4 监测器 ----
INSERT INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name) VALUES
    (2001, 'temp_101',        '101温度',           1001, 105, 'C',    '10s', '1m', 'value>35',  NULL,        301, 1,  -20.0, 60.0,    'SimulateFloat'),
    (2002, 'humi_101',        '101湿度',           1001, 105, '%RH',  '10s', '1m', 'value>80',  NULL,        302, 1,  0.0,   100.0,   'SimulateFloat'),
    (2003, 'temp_102',        '102温度',           1002, 105, 'C',    '10s', '1m', 'value>35',  NULL,        301, 1,  -20.0, 60.0,    'SimulateFloat'),
    (2004, 'humi_102',        '102湿度',           1002, 105, '%RH',  '10s', '1m', 'value>80',  NULL,        302, 1,  0.0,   100.0,   'SimulateFloat'),
    (2005, 'elec_active',     '机房有功电能',      1003, 105, 'kWh',  '30s', '5m', NULL,        NULL,        306, 1,  0.0,   999999.0, 'SimulateFloat'),
    (2006, 'elec_voltage_a',  'A相电压',           1003, 105, 'V',    '10s', '1m', 'value<200', NULL,        304, 1,  0.0,   300.0,    'SimulateFloat'),
    (2007, 'elec_current_a',  'A相电流',           1003, 105, 'A',    '10s', '1m', 'value>50',  NULL,        303, 1,  0.0,   100.0,    'SimulateFloat'),
    (2008, 'ups_status',      'UPS运行状态',       1004, 105, NULL,   '30s', '1m', 'value==0',  NULL,        309, 2,  NULL,  NULL,     'SimulateInt'),
    (2009, 'co2_101',         '101 CO2浓度',       1001, 105, 'ppm',  '1m',  '5m', 'value>1000',NULL,        311, 1,  0.0,   5000.0,   'SimulateFloat'),
    (2010, 'pm25_101',        '101 PM2.5',         1001, 105, 'ug/m3','1m',  '5m', 'value>75',  NULL,        312, 1,  0.0,   500.0,    'SimulateFloat')
ON DUPLICATE KEY UPDATE caption = VALUES(caption), type_name = VALUES(type_name);

-- ---- 12.5 控制器 ----
INSERT INTO t_control (id, name, caption, parent, source, unit, time_interval, saving_interval, catalog, transform, warn_cond, refresh_delay, min_value, max_value, type_name) VALUES
    (3001, 'ac_switch_101',   '空调开关-101',      1005, 100, NULL,   '10s', '30s', 401, NULL, NULL, 2000, NULL, NULL,   'ModbusBoolFC3FC6'),
    (3002, 'ac_temp_set_101', '空调温度设定-101',  1005, 100, 'C',    '10s', '30s', 404, NULL, NULL, 2000, 16.0, 30.0,   'ModbusInt16FC3FC6'),
    (3003, 'light_switch_1a', '1层照明开关',       1006, 100, NULL,   '10s', '30s', 401, NULL, NULL, 1000, NULL, NULL,   'ModbusBoolFC3FC6'),
    (3004, 'light_dim_1a',    '1层照明调光',       1006, 100, '%',    '10s', '30s', 402, NULL, NULL, 1000, 0.0,  100.0,  'ModbusInt16FC3FC6')
ON DUPLICATE KEY UPDATE caption = VALUES(caption), type_name = VALUES(type_name);

-- ---- 12.6 统一资产视图 ----
INSERT INTO t_asset (id, name, caption, kind, type_id, parent_id, state, enabled, sort, space_id, device_id, service_id, probe_id, control_id) VALUES
    -- 根节点
    (1,  'root',             '智慧园区',         0, 101, 0,    0, 1, 0, 1,    NULL, NULL, NULL,  NULL),
    -- 空间节点
    (2,  'building_A',       'A栋办公楼',       0, 501, 1,    0, 1, 1, 10,   NULL, NULL, NULL,  NULL),
    (3,  'floor_1A',         'A栋1层',          0, 502, 2,    0, 1, 1, 11,   NULL, NULL, NULL,  NULL),
    (4,  'floor_2A',         'A栋2层',          0, 502, 2,    0, 1, 2, 12,   NULL, NULL, NULL,  NULL),
    (5,  'room_101',         '101会议室',       0, 503, 3,    0, 1, 1, 13,   NULL, NULL, NULL,  NULL),
    (6,  'room_102',         '102办公室',       0, 503, 3,    0, 1, 2, 14,   NULL, NULL, NULL,  NULL),
    (7,  'machine_room',     '机房',            0, 505, 3,    0, 1, 3, 15,   NULL, NULL, NULL,  NULL),
    -- 监控服务节点
    (10, 'modbus_tcp_svc',   'Modbus TCP服务',  2, 103, 1,    0, 1, 1, NULL, NULL, 100,  NULL,   NULL),
    (11, 'mqtt_svc',         'MQTT服务',        2, 103, 1,    0, 1, 2, NULL, NULL, 101,  NULL,   NULL),
    (12, 'opcua_svc',        'OPC UA服务',      2, 103, 1,    0, 1, 3, NULL, NULL, 102,  NULL,   NULL),
    (44, 'iec104_svc',       'IEC 104服务',     2, 103, 1,    0, 1, 4, NULL, NULL, 103,  NULL,   NULL),
    (45, 'bacnet_svc',       'BACnet服务',      2, 103, 1,    0, 1, 5, NULL, NULL, 104,  NULL,   NULL),
    (46, 'sim_svc',          '模拟数据服务',     2, 103, 1,    0, 1, 6, NULL, NULL, 105,  NULL,   NULL),
    -- 设备节点
    (20, 'th_sensor_001',    '温湿度传感器-001',1, 102, 5,    0, 1, 1, NULL, 1001, NULL, NULL,   NULL),
    (21, 'th_sensor_002',    '温湿度传感器-002',1, 102, 6,    0, 1, 2, NULL, 1002, NULL, NULL,   NULL),
    (22, 'elec_meter_001',   '电表-001',        1, 102, 7,    0, 1, 3, NULL, 1003, NULL, NULL,   NULL),
    (23, 'ups_001',          'UPS电源-001',     1, 102, 7,    0, 1, 4, NULL, 1004, NULL, NULL,   NULL),
    (24, 'ac_001',           '中央空调-001',    1, 102, 5,    0, 1, 5, NULL, 1005, NULL, NULL,   NULL),
    (25, 'light_ctrl_001',   '照明控制器-001',  1, 102, 3,    0, 1, 6, NULL, 1006, NULL, NULL,   NULL),
    (26, 'gateway_001',      '网关-001',        1, 102, 7,    0, 1, 7, NULL, 1007, NULL, NULL,   NULL),
    -- 监测器节点
    (30, 'temp_101',         '101温度',         3, 104, 20,   0, 1, 1, NULL, NULL, NULL, 2001,    NULL),
    (31, 'humi_101',         '101湿度',         3, 104, 20,   0, 1, 2, NULL, NULL, NULL, 2002,    NULL),
    (32, 'temp_102',         '102温度',         3, 104, 21,   0, 1, 1, NULL, NULL, NULL, 2003,    NULL),
    (33, 'humi_102',         '102湿度',         3, 104, 21,   0, 1, 2, NULL, NULL, NULL, 2004,    NULL),
    (34, 'elec_active',      '机房有功电能',    3, 104, 22,   0, 1, 1, NULL, NULL, NULL, 2005,    NULL),
    (35, 'elec_voltage_a',   'A相电压',         3, 104, 22,   0, 1, 2, NULL, NULL, NULL, 2006,    NULL),
    (36, 'elec_current_a',   'A相电流',         3, 104, 22,   0, 1, 3, NULL, NULL, NULL, 2007,    NULL),
    (37, 'ups_status',       'UPS运行状态',     3, 104, 23,   0, 1, 1, NULL, NULL, NULL, 2008,    NULL),
    (38, 'co2_101',          '101 CO2浓度',     3, 104, 20,   0, 1, 3, NULL, NULL, NULL, 2009,    NULL),
    (39, 'pm25_101',         '101 PM2.5',       3, 104, 20,   0, 1, 4, NULL, NULL, NULL, 2010,    NULL),
    -- 控制器节点
    (40, 'ac_switch_101',    '空调开关-101',    4, 105, 24,   0, 1, 1, NULL, NULL, NULL, NULL,    3001),
    (41, 'ac_temp_set_101',  '空调温度设定-101',4, 105, 24,   0, 1, 2, NULL, NULL, NULL, NULL,    3002),
    (42, 'light_switch_1a',  '1层照明开关',     4, 105, 25,   0, 1, 1, NULL, NULL, NULL, NULL,    3003),
    (43, 'light_dim_1a',     '1层照明调光',     4, 105, 25,   0, 1, 2, NULL, NULL, NULL, NULL,    3004),
    (47, 'http_avail',       '前端HTTP服务可用性', 3, 104, 26, 0, 1, 5, NULL, NULL, NULL, 3101,  NULL),
    (48, 'mem_usage',        '系统内存占用率',   3, 104, 26,  0, 1, 6, NULL, NULL, NULL, 3102,  NULL),
    (49, 'db_avail',         '数据库可用性',     3, 104, 26,  0, 1, 7, NULL, NULL, NULL, 3103,  NULL)
ON DUPLICATE KEY UPDATE caption = VALUES(caption), probe_id = VALUES(probe_id), control_id = VALUES(control_id);

INSERT INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, warn_cond, transform, catalog, monitor_kind, min_value, max_value, type_name) VALUES
    (3101, 'http_avail',  '前端HTTP服务可用性', 1007, 105, NULL, '30s', '1m', 'value==0', NULL, 309, 2, NULL, NULL, 'SimulateInt'),
    (3102, 'mem_usage',   '系统内存占用率',     1007, 105, '%',   '30s', '1m', 'value>90', NULL, 301, 1, 0.0,  100.0, 'SimulateFloat'),
    (3103, 'db_avail',    '数据库可用性',       1007, 105, NULL, '30s', '1m', 'value==0', NULL, 309, 2, NULL, NULL, 'SimulateInt')
ON DUPLICATE KEY UPDATE caption = VALUES(caption), type_name = VALUES(type_name);

-- ---- 12.7 演示报警规则 ----
INSERT INTO t_alarm_rule (id, asset_id, rule, way, warn_id, message_template, enabled, start) VALUES
    (1, 2001, 0, 7, 2, '101会议室温度过高: {value}C',    1, 1),
    (2, 2002, 0, 7, 2, '101会议室湿度过高: {value}%RH',  1, 1),
    (3, 2006, 1, 7, 3, 'A相电压异常: {value}V',          1, 1),
    (4, 2008, 0, 7, 4, 'UPS运行状态异常',                1, 1)
ON DUPLICATE KEY UPDATE message_template = VALUES(message_template);

INSERT INTO t_alarm_rule (id, asset_id, rule, way, warn_id, message_template, enabled, start) VALUES
    (5, 3101, 0, 7, 4, 'HTTP service unavailable!', 1, 1),
    (6, 3102, 0, 7, 2, 'Memory usage high: {value}%', 1, 1),
    (7, 3103, 0, 7, 4, 'Database unavailable!', 1, 1)
ON DUPLICATE KEY UPDATE message_template = VALUES(message_template);

-- ---- 12.8 演示联动规则 ----
INSERT INTO t_linkage_rule (id, name, cause_type, enabled, caption) VALUES
    (1, '消防报警联动', 'MONITOR', 1, '烟雾报警时自动开启排风')
ON DUPLICATE KEY UPDATE caption = VALUES(caption);

INSERT INTO t_linkage_rule_cause (id, rule_id, asset_id, trigger_value) VALUES
    (1, 1, 2008, '1')
ON DUPLICATE KEY UPDATE trigger_value = VALUES(trigger_value);

INSERT INTO t_linkage_rule_effect (id, rule_id, asset_id, command) VALUES
    (1, 1, 3001, '1')
ON DUPLICATE KEY UPDATE command = VALUES(command);

-- ---- 12.9 演示定时任务 ----
-- 已清空：示例任务需引用真实存在的 Control id，由用户通过 CRUD API 或手工 INSERT 配置。

-- ============================================================
-- System Management (RBAC) — Seed Data
-- ============================================================

-- Admin user (password: admin123, BCrypt encoded)
INSERT INTO t_sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '管理员', 0);

-- Admin role
INSERT INTO t_sys_role (id, role_name, role_key, status) VALUES
(1, '超级管理员', 'admin', 0);

-- User-role mapping
INSERT INTO t_sys_user_role (user_id, role_id) VALUES (1, 1);

-- Default department
INSERT INTO t_sys_dept (id, dept_name, parent_id, ancestors, order_num, status) VALUES
(1, '总公司', 0, '0', 0, 0);

-- System management menu tree
INSERT INTO t_sys_menu (id, menu_name, parent_id, path, component, icon, perms, menu_type, order_num, status) VALUES
(1, '系统管理', 0, '/system', '', 'Setting', '', 'M', 1, 0),
(100, '用户管理', 1, '/system/user', 'system/user/index', 'User', 'sys:user:list', 'C', 1, 0),
(101, '用户新增', 100, '', '', '', 'sys:user:add', 'F', 1, 0),
(102, '用户编辑', 100, '', '', '', 'sys:user:edit', 'F', 2, 0),
(103, '用户删除', 100, '', '', '', 'sys:user:delete', 'F', 3, 0),
(104, '密码重置', 100, '', '', '', 'sys:user:resetPwd', 'F', 4, 0),
(200, '角色管理', 1, '/system/role', 'system/role/index', 'Avatar', 'sys:role:list', 'C', 2, 0),
(201, '角色新增', 200, '', '', '', 'sys:role:add', 'F', 1, 0),
(202, '角色编辑', 200, '', '', '', 'sys:role:edit', 'F', 2, 0),
(203, '角色删除', 200, '', '', '', 'sys:role:delete', 'F', 3, 0),
(300, '菜单管理', 1, '/system/menu', 'system/menu/index', 'Menu', 'sys:menu:list', 'C', 3, 0),
(301, '菜单新增', 300, '', '', '', 'sys:menu:add', 'F', 1, 0),
(302, '菜单编辑', 300, '', '', '', 'sys:menu:edit', 'F', 2, 0),
(303, '菜单删除', 300, '', '', '', 'sys:menu:delete', 'F', 3, 0),
(400, '部门管理', 1, '/system/dept', 'system/dept/index', 'OfficeBuilding', 'sys:dept:list', 'C', 4, 0),
(401, '部门新增', 400, '', '', '', 'sys:dept:add', 'F', 1, 0),
(402, '部门编辑', 400, '', '', '', 'sys:dept:edit', 'F', 2, 0),
(403, '部门删除', 400, '', '', '', 'sys:dept:delete', 'F', 3, 0),
(500, '通知公告', 1, '/system/notice', 'system/notice/index', 'Notification', 'sys:notice:list', 'C', 5, 0),
(501, '通知新增', 500, '', '', '', 'sys:notice:add', 'F', 1, 0),
(502, '通知编辑', 500, '', '', '', 'sys:notice:edit', 'F', 2, 0),
(503, '通知删除', 500, '', '', '', 'sys:notice:delete', 'F', 3, 0),
(600, '操作日志', 1, '/system/log', 'system/log/index', 'Document', 'sys:log:list', 'C', 6, 0),
(700, '系统监控', 1, '/system/monitor', 'system/monitor/index', 'Monitor', 'sys:monitor:query', 'C', 7, 0),
(800, '数据保留', 1, '/system/retention', 'system/retention', 'Delete', 'iot:monitor:retention', 'C', 8, 0);

-- IoT permissions
INSERT INTO t_sys_menu (id, menu_name, parent_id, path, component, icon, perms, menu_type, order_num, status) VALUES
(2000, 'IoT运维', 0, '/iot', '', 'Cpu', '', 'M', 2, 0),
(2001, '资产管理', 2000, '/asset', 'asset/index', 'Box', 'iot:asset:list', 'C', 1, 0),
(2002, '实时监控', 2000, '/monitor', 'monitor/index', 'Odometer', 'iot:monitor:query', 'C', 2, 0),
(2003, '告警管理', 2000, '/alarm', 'alarm/index', 'Bell', 'iot:alarm:query', 'C', 3, 0),
(2004, '联动规则', 2000, '/linkage', 'linkage/index', 'Connection', 'iot:linkage:query', 'C', 4, 0),
(2005, '控制面板', 2000, '/control', 'control/index', 'Switch', 'iot:control:execute', 'C', 5, 0),
(2006, '监控大屏', 2000, '/dashboard', 'dashboard/index', 'DataBoard', 'iot:dashboard:view', 'C', 6, 0),
(2101, '资产新增', 2001, '', '', '', 'iot:asset:add', 'F', 1, 0),
(2102, '资产编辑', 2001, '', '', '', 'iot:asset:edit', 'F', 2, 0),
(2103, '资产删除', 2001, '', '', '', 'iot:asset:delete', 'F', 3, 0),
(2104, '资产查询', 2001, '', '', '', 'iot:asset:query', 'F', 4, 0),
(2105, '资产启动', 2001, '', '', '', 'iot:asset:start', 'F', 5, 0),
(2106, '资产停止', 2001, '', '', '', 'iot:asset:stop', 'F', 6, 0),
(2107, '资产停用', 2001, '', '', '', 'iot:asset:disable', 'F', 7, 0),
(2108, '资产启用', 2001, '', '', '', 'iot:asset:enable', 'F', 8, 0),
(2301, '告警处理', 2003, '', '', '', 'iot:alarm:handle', 'F', 1, 0),
(2501, '控制执行', 2005, '', '', '', 'iot:control:execute', 'F', 1, 0);

-- Grant all menu permissions to admin role
DELETE FROM t_sys_role_menu;
INSERT INTO t_sys_role_menu (role_id, menu_id)
SELECT 1, id FROM t_sys_menu;
