-- ============================================================================
-- Demo Data for H2 — Asset instances and sample monitoring data
-- 用于开发测试，创建真实的资产树和监测采样数据
-- ============================================================================

-- 先清除已有的 demo 数据，避免主键冲突
DELETE FROM t_sample_float WHERE monitor IN (2001, 2002, 2004);
DELETE FROM t_probe WHERE id BETWEEN 2001 AND 2999;
DELETE FROM t_device WHERE id BETWEEN 1001 AND 1999;
DELETE FROM t_service WHERE id BETWEEN 100 AND 199;
DELETE FROM t_space WHERE id BETWEEN 1 AND 99;

-- ============================================================================
-- 空间 (Spaces)
-- ============================================================================
INSERT INTO t_space (id, name, caption, parent, area, sequence, show_in_client, type_name) VALUES
(1,  'smart_park',   '智慧园区',   0,   50000, 0, 1, 'GenericSpace'),
(2,  'building_A',   'A栋办公楼',  1,   8000,  0, 1, 'GenericSpace'),
(3,  'floor_1A',     'A栋1层',     2,   2000,  0, 1, 'GenericSpace'),
(4,  'room_101',     '101会议室',   3,   120,   0, 1, 'GenericSpace'),
(5,  'room_102',     '102办公室',   3,   80,    0, 1, 'GenericSpace'),
(6,  'floor_2A',     'A栋2层',     2,   2000,  0, 1, 'GenericSpace');

-- ============================================================================
-- 服务 (Services)
-- ============================================================================
INSERT INTO t_service (id, name, caption, parent, mode, driver_class, max_connections, type_name) VALUES
(100, 'sim_svc',   '模拟数据服务', 1, 0, 'com.systar.monitor.drivers.simulate.SimulateDriver', 10, 'SimulateService');

-- ============================================================================
-- 设备 (Devices)
-- ============================================================================
INSERT INTO t_device (id, name, caption, parent, catalog, vendor, model, serial_number, lifecycle_status, health_index, type_name) VALUES
(1001, 'th_sensor_001', '温湿度传感器-001', 4, 1, 'SIEMENS', 'QFA3160', 'SN-TH-001', 'IN_SERVICE', 95.0, 'GenericDevice'),
(1002, 'ac_ctrl_001',   '中央空调-001',     4, 2, 'DAIKIN',  'VRV-X',   'SN-AC-001', 'IN_SERVICE', 88.0, 'GenericDevice'),
(1003, 'th_sensor_002', '温湿度传感器-002', 5, 1, 'SIEMENS', 'QFA3160', 'SN-TH-002', 'IN_SERVICE', 92.0, 'GenericDevice');

-- ============================================================================
-- 监测器 (Probes)
-- ============================================================================
INSERT INTO t_probe (id, name, caption, parent, source, unit, time_interval, saving_interval, catalog, monitor_kind, min_value, max_value, type_name) VALUES
(2001, 'temp_101',    '101温度',      1001, 100, 'C',      '10s', '60s',  1, 1, 0.0,   100.0,  'SimulateFloat'),
(2002, 'humidity_101','101湿度',     1001, 100, '%RH',    '30s', '60s',  1, 1, 0.0,   100.0,  'SimulateFloat'),
(2003, 'co2_101',     '101 CO2浓度', 1001, 100, 'ppm',    '60s', '300s',  1, 1, 300.0, 2000.0, 'SimulateFloat'),
(2004, 'temp_102',    '102温度',      1003, 100, 'C',      '10s', '60s',  1, 1, 0.0,   100.0,  'SimulateFloat'),
(2005, 'humidity_102','102湿度',     1003, 100, '%RH',    '30s', '60s',  1, 1, 0.0,   100.0,  'SimulateFloat');

-- ============================================================================
-- 采样数据 (Sample data for trend chart testing)
-- 为 101温度 (monitor=2001) 生成最近2小时的模拟数据，1分钟一个点
-- 模拟温度在20-80°C之间正弦波动
-- ============================================================================
INSERT INTO t_sample_float (monitor, "value", moment) VALUES
(2001, 45.0,  DATEADD('MINUTE', -120, NOW())),
(2001, 46.2,  DATEADD('MINUTE', -119, NOW())),
(2001, 47.8,  DATEADD('MINUTE', -118, NOW())),
(2001, 49.5,  DATEADD('MINUTE', -117, NOW())),
(2001, 51.3,  DATEADD('MINUTE', -116, NOW())),
(2001, 53.0,  DATEADD('MINUTE', -115, NOW())),
(2001, 54.6,  DATEADD('MINUTE', -114, NOW())),
(2001, 56.1,  DATEADD('MINUTE', -113, NOW())),
(2001, 57.5,  DATEADD('MINUTE', -112, NOW())),
(2001, 58.8,  DATEADD('MINUTE', -111, NOW())),
(2001, 60.0,  DATEADD('MINUTE', -110, NOW())),
(2001, 61.2,  DATEADD('MINUTE', -109, NOW())),
(2001, 62.5,  DATEADD('MINUTE', -108, NOW())),
(2001, 63.8,  DATEADD('MINUTE', -107, NOW())),
(2001, 65.0,  DATEADD('MINUTE', -106, NOW())),
(2001, 66.2,  DATEADD('MINUTE', -105, NOW())),
(2001, 67.3,  DATEADD('MINUTE', -104, NOW())),
(2001, 68.1,  DATEADD('MINUTE', -103, NOW())),
(2001, 68.8,  DATEADD('MINUTE', -102, NOW())),
(2001, 69.5,  DATEADD('MINUTE', -101, NOW())),
(2001, 70.0,  DATEADD('MINUTE', -100, NOW())),
(2001, 70.5,  DATEADD('MINUTE', -99, NOW())),
(2001, 71.0,  DATEADD('MINUTE', -98, NOW())),
(2001, 71.5,  DATEADD('MINUTE', -97, NOW())),
(2001, 72.0,  DATEADD('MINUTE', -96, NOW())),
(2001, 72.5,  DATEADD('MINUTE', -95, NOW())),
(2001, 73.0,  DATEADD('MINUTE', -94, NOW())),
(2001, 73.5,  DATEADD('MINUTE', -93, NOW())),
(2001, 73.9,  DATEADD('MINUTE', -92, NOW())),
(2001, 74.2,  DATEADD('MINUTE', -91, NOW())),
(2001, 74.5,  DATEADD('MINUTE', -90, NOW())),
(2001, 74.8,  DATEADD('MINUTE', -89, NOW())),
(2001, 74.9,  DATEADD('MINUTE', -88, NOW())),
(2001, 75.0,  DATEADD('MINUTE', -87, NOW())),
(2001, 74.9,  DATEADD('MINUTE', -86, NOW())),
(2001, 74.5,  DATEADD('MINUTE', -85, NOW())),
(2001, 73.8,  DATEADD('MINUTE', -84, NOW())),
(2001, 72.5,  DATEADD('MINUTE', -83, NOW())),
(2001, 70.8,  DATEADD('MINUTE', -82, NOW())),
(2001, 68.5,  DATEADD('MINUTE', -81, NOW())),
(2001, 65.8,  DATEADD('MINUTE', -80, NOW())),
(2001, 62.5,  DATEADD('MINUTE', -79, NOW())),
(2001, 58.8,  DATEADD('MINUTE', -78, NOW())),
(2001, 55.0,  DATEADD('MINUTE', -77, NOW())),
(2001, 51.2,  DATEADD('MINUTE', -76, NOW())),
(2001, 47.5,  DATEADD('MINUTE', -75, NOW())),
(2001, 44.0,  DATEADD('MINUTE', -74, NOW())),
(2001, 40.8,  DATEADD('MINUTE', -73, NOW())),
(2001, 38.0,  DATEADD('MINUTE', -72, NOW())),
(2001, 35.5,  DATEADD('MINUTE', -71, NOW())),
(2001, 33.2,  DATEADD('MINUTE', -70, NOW())),
(2001, 31.0,  DATEADD('MINUTE', -69, NOW())),
(2001, 28.8,  DATEADD('MINUTE', -68, NOW())),
(2001, 26.5,  DATEADD('MINUTE', -67, NOW())),
(2001, 24.2,  DATEADD('MINUTE', -66, NOW())),
(2001, 22.0,  DATEADD('MINUTE', -65, NOW())),
(2001, 20.0,  DATEADD('MINUTE', -64, NOW())),
(2001, 18.2,  DATEADD('MINUTE', -63, NOW())),
(2001, 16.8,  DATEADD('MINUTE', -62, NOW())),
(2001, 15.5,  DATEADD('MINUTE', -61, NOW())),
(2001, 14.5,  DATEADD('MINUTE', -60, NOW())),
(2001, 13.8,  DATEADD('MINUTE', -59, NOW())),
(2001, 13.2,  DATEADD('MINUTE', -58, NOW())),
(2001, 12.8,  DATEADD('MINUTE', -57, NOW())),
(2001, 12.5,  DATEADD('MINUTE', -56, NOW())),
(2001, 12.8,  DATEADD('MINUTE', -55, NOW())),
(2001, 13.5,  DATEADD('MINUTE', -54, NOW())),
(2001, 14.8,  DATEADD('MINUTE', -53, NOW())),
(2001, 16.5,  DATEADD('MINUTE', -52, NOW())),
(2001, 18.8,  DATEADD('MINUTE', -51, NOW())),
(2001, 21.5,  DATEADD('MINUTE', -50, NOW())),
(2001, 24.5,  DATEADD('MINUTE', -49, NOW())),
(2001, 27.8,  DATEADD('MINUTE', -48, NOW())),
(2001, 31.2,  DATEADD('MINUTE', -47, NOW())),
(2001, 34.8,  DATEADD('MINUTE', -46, NOW())),
(2001, 38.5,  DATEADD('MINUTE', -45, NOW())),
(2001, 42.0,  DATEADD('MINUTE', -44, NOW())),
(2001, 45.5,  DATEADD('MINUTE', -43, NOW())),
(2001, 48.8,  DATEADD('MINUTE', -42, NOW())),
(2001, 51.5,  DATEADD('MINUTE', -41, NOW())),
(2001, 53.8,  DATEADD('MINUTE', -40, NOW())),
(2001, 55.5,  DATEADD('MINUTE', -39, NOW())),
(2001, 56.8,  DATEADD('MINUTE', -38, NOW())),
(2001, 57.5,  DATEADD('MINUTE', -37, NOW())),
(2001, 57.8,  DATEADD('MINUTE', -36, NOW())),
(2001, 58.0,  DATEADD('MINUTE', -35, NOW())),
(2001, 58.2,  DATEADD('MINUTE', -34, NOW())),
(2001, 58.5,  DATEADD('MINUTE', -33, NOW())),
(2001, 58.8,  DATEADD('MINUTE', -32, NOW())),
(2001, 59.0,  DATEADD('MINUTE', -31, NOW())),
(2001, 59.2,  DATEADD('MINUTE', -30, NOW())),
(2001, 59.5,  DATEADD('MINUTE', -29, NOW())),
(2001, 59.8,  DATEADD('MINUTE', -28, NOW())),
(2001, 60.0,  DATEADD('MINUTE', -27, NOW())),
(2001, 60.2,  DATEADD('MINUTE', -26, NOW())),
(2001, 60.5,  DATEADD('MINUTE', -25, NOW())),
(2001, 60.8,  DATEADD('MINUTE', -24, NOW())),
(2001, 61.0,  DATEADD('MINUTE', -23, NOW())),
(2001, 61.3,  DATEADD('MINUTE', -22, NOW())),
(2001, 61.8,  DATEADD('MINUTE', -21, NOW())),
(2001, 62.5,  DATEADD('MINUTE', -20, NOW())),
(2001, 63.5,  DATEADD('MINUTE', -19, NOW())),
(2001, 64.8,  DATEADD('MINUTE', -18, NOW())),
(2001, 66.2,  DATEADD('MINUTE', -17, NOW())),
(2001, 67.8,  DATEADD('MINUTE', -16, NOW())),
(2001, 69.5,  DATEADD('MINUTE', -15, NOW())),
(2001, 71.2,  DATEADD('MINUTE', -14, NOW())),
(2001, 72.8,  DATEADD('MINUTE', -13, NOW())),
(2001, 74.5,  DATEADD('MINUTE', -12, NOW())),
(2001, 76.0,  DATEADD('MINUTE', -11, NOW())),
(2001, 77.2,  DATEADD('MINUTE', -10, NOW())),
(2001, 78.0,  DATEADD('MINUTE',  -9, NOW())),
(2001, 78.5,  DATEADD('MINUTE',  -8, NOW())),
(2001, 78.8,  DATEADD('MINUTE',  -7, NOW())),
(2001, 78.5,  DATEADD('MINUTE',  -6, NOW())),
(2001, 77.8,  DATEADD('MINUTE',  -5, NOW())),
(2001, 76.5,  DATEADD('MINUTE',  -4, NOW())),
(2001, 74.8,  DATEADD('MINUTE',  -3, NOW())),
(2001, 72.5,  DATEADD('MINUTE',  -2, NOW())),
(2001, 69.8,  DATEADD('MINUTE',  -1, NOW())),
(2001, 66.5,  NOW());

-- 为 101湿度 (monitor=2002) 生成少量样本
INSERT INTO t_sample_float (monitor, "value", moment) VALUES
(2002, 55.0, DATEADD('MINUTE', -60, NOW())),
(2002, 56.2, DATEADD('MINUTE', -50, NOW())),
(2002, 54.8, DATEADD('MINUTE', -40, NOW())),
(2002, 53.5, DATEADD('MINUTE', -30, NOW())),
(2002, 52.0, DATEADD('MINUTE', -20, NOW())),
(2002, 51.5, DATEADD('MINUTE', -10, NOW())),
(2002, 50.8, NOW());

-- 为 102温度 (monitor=2004) 生成少量样本
INSERT INTO t_sample_float (monitor, "value", moment) VALUES
(2004, 28.0, DATEADD('MINUTE', -60, NOW())),
(2004, 28.5, DATEADD('MINUTE', -50, NOW())),
(2004, 29.2, DATEADD('MINUTE', -40, NOW())),
(2004, 28.8, DATEADD('MINUTE', -30, NOW())),
(2004, 27.5, DATEADD('MINUTE', -20, NOW())),
(2004, 26.8, DATEADD('MINUTE', -10, NOW())),
(2004, 26.2, NOW());
