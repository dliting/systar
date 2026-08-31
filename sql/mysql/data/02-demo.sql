-- ============================================================================
-- Demo Data for MySQL — Asset instances and sample monitoring data
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
-- 模拟温度在12-79°C之间正弦波动
-- ============================================================================
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(2001, 45.0,  DATE_ADD(NOW(), INTERVAL -120 MINUTE)),
(2001, 46.2,  DATE_ADD(NOW(), INTERVAL -119 MINUTE)),
(2001, 47.8,  DATE_ADD(NOW(), INTERVAL -118 MINUTE)),
(2001, 49.5,  DATE_ADD(NOW(), INTERVAL -117 MINUTE)),
(2001, 51.3,  DATE_ADD(NOW(), INTERVAL -116 MINUTE)),
(2001, 53.0,  DATE_ADD(NOW(), INTERVAL -115 MINUTE)),
(2001, 54.6,  DATE_ADD(NOW(), INTERVAL -114 MINUTE)),
(2001, 56.1,  DATE_ADD(NOW(), INTERVAL -113 MINUTE)),
(2001, 57.5,  DATE_ADD(NOW(), INTERVAL -112 MINUTE)),
(2001, 58.8,  DATE_ADD(NOW(), INTERVAL -111 MINUTE)),
(2001, 60.0,  DATE_ADD(NOW(), INTERVAL -110 MINUTE)),
(2001, 61.2,  DATE_ADD(NOW(), INTERVAL -109 MINUTE)),
(2001, 62.5,  DATE_ADD(NOW(), INTERVAL -108 MINUTE)),
(2001, 63.8,  DATE_ADD(NOW(), INTERVAL -107 MINUTE)),
(2001, 65.0,  DATE_ADD(NOW(), INTERVAL -106 MINUTE)),
(2001, 66.2,  DATE_ADD(NOW(), INTERVAL -105 MINUTE)),
(2001, 67.3,  DATE_ADD(NOW(), INTERVAL -104 MINUTE)),
(2001, 68.1,  DATE_ADD(NOW(), INTERVAL -103 MINUTE)),
(2001, 68.8,  DATE_ADD(NOW(), INTERVAL -102 MINUTE)),
(2001, 69.5,  DATE_ADD(NOW(), INTERVAL -101 MINUTE)),
(2001, 70.0,  DATE_ADD(NOW(), INTERVAL -100 MINUTE)),
(2001, 70.5,  DATE_ADD(NOW(), INTERVAL -99 MINUTE)),
(2001, 71.0,  DATE_ADD(NOW(), INTERVAL -98 MINUTE)),
(2001, 71.5,  DATE_ADD(NOW(), INTERVAL -97 MINUTE)),
(2001, 72.0,  DATE_ADD(NOW(), INTERVAL -96 MINUTE)),
(2001, 72.5,  DATE_ADD(NOW(), INTERVAL -95 MINUTE)),
(2001, 73.0,  DATE_ADD(NOW(), INTERVAL -94 MINUTE)),
(2001, 73.5,  DATE_ADD(NOW(), INTERVAL -93 MINUTE)),
(2001, 73.9,  DATE_ADD(NOW(), INTERVAL -92 MINUTE)),
(2001, 74.2,  DATE_ADD(NOW(), INTERVAL -91 MINUTE)),
(2001, 74.5,  DATE_ADD(NOW(), INTERVAL -90 MINUTE)),
(2001, 74.8,  DATE_ADD(NOW(), INTERVAL -89 MINUTE)),
(2001, 74.9,  DATE_ADD(NOW(), INTERVAL -88 MINUTE)),
(2001, 75.0,  DATE_ADD(NOW(), INTERVAL -87 MINUTE)),
(2001, 74.9,  DATE_ADD(NOW(), INTERVAL -86 MINUTE)),
(2001, 74.5,  DATE_ADD(NOW(), INTERVAL -85 MINUTE)),
(2001, 73.8,  DATE_ADD(NOW(), INTERVAL -84 MINUTE)),
(2001, 72.5,  DATE_ADD(NOW(), INTERVAL -83 MINUTE)),
(2001, 70.8,  DATE_ADD(NOW(), INTERVAL -82 MINUTE)),
(2001, 68.5,  DATE_ADD(NOW(), INTERVAL -81 MINUTE)),
(2001, 65.8,  DATE_ADD(NOW(), INTERVAL -80 MINUTE)),
(2001, 62.5,  DATE_ADD(NOW(), INTERVAL -79 MINUTE)),
(2001, 58.8,  DATE_ADD(NOW(), INTERVAL -78 MINUTE)),
(2001, 55.0,  DATE_ADD(NOW(), INTERVAL -77 MINUTE)),
(2001, 51.2,  DATE_ADD(NOW(), INTERVAL -76 MINUTE)),
(2001, 47.5,  DATE_ADD(NOW(), INTERVAL -75 MINUTE)),
(2001, 44.0,  DATE_ADD(NOW(), INTERVAL -74 MINUTE)),
(2001, 40.8,  DATE_ADD(NOW(), INTERVAL -73 MINUTE)),
(2001, 38.0,  DATE_ADD(NOW(), INTERVAL -72 MINUTE)),
(2001, 35.5,  DATE_ADD(NOW(), INTERVAL -71 MINUTE)),
(2001, 33.2,  DATE_ADD(NOW(), INTERVAL -70 MINUTE)),
(2001, 31.0,  DATE_ADD(NOW(), INTERVAL -69 MINUTE)),
(2001, 28.8,  DATE_ADD(NOW(), INTERVAL -68 MINUTE)),
(2001, 26.5,  DATE_ADD(NOW(), INTERVAL -67 MINUTE)),
(2001, 24.2,  DATE_ADD(NOW(), INTERVAL -66 MINUTE)),
(2001, 22.0,  DATE_ADD(NOW(), INTERVAL -65 MINUTE)),
(2001, 20.0,  DATE_ADD(NOW(), INTERVAL -64 MINUTE)),
(2001, 18.2,  DATE_ADD(NOW(), INTERVAL -63 MINUTE)),
(2001, 16.8,  DATE_ADD(NOW(), INTERVAL -62 MINUTE)),
(2001, 15.5,  DATE_ADD(NOW(), INTERVAL -61 MINUTE)),
(2001, 14.5,  DATE_ADD(NOW(), INTERVAL -60 MINUTE)),
(2001, 13.8,  DATE_ADD(NOW(), INTERVAL -59 MINUTE)),
(2001, 13.2,  DATE_ADD(NOW(), INTERVAL -58 MINUTE)),
(2001, 12.8,  DATE_ADD(NOW(), INTERVAL -57 MINUTE)),
(2001, 12.5,  DATE_ADD(NOW(), INTERVAL -56 MINUTE)),
(2001, 12.8,  DATE_ADD(NOW(), INTERVAL -55 MINUTE)),
(2001, 13.5,  DATE_ADD(NOW(), INTERVAL -54 MINUTE)),
(2001, 14.8,  DATE_ADD(NOW(), INTERVAL -53 MINUTE)),
(2001, 16.5,  DATE_ADD(NOW(), INTERVAL -52 MINUTE)),
(2001, 18.8,  DATE_ADD(NOW(), INTERVAL -51 MINUTE)),
(2001, 21.5,  DATE_ADD(NOW(), INTERVAL -50 MINUTE)),
(2001, 24.5,  DATE_ADD(NOW(), INTERVAL -49 MINUTE)),
(2001, 27.8,  DATE_ADD(NOW(), INTERVAL -48 MINUTE)),
(2001, 31.2,  DATE_ADD(NOW(), INTERVAL -47 MINUTE)),
(2001, 34.8,  DATE_ADD(NOW(), INTERVAL -46 MINUTE)),
(2001, 38.5,  DATE_ADD(NOW(), INTERVAL -45 MINUTE)),
(2001, 42.0,  DATE_ADD(NOW(), INTERVAL -44 MINUTE)),
(2001, 45.5,  DATE_ADD(NOW(), INTERVAL -43 MINUTE)),
(2001, 48.8,  DATE_ADD(NOW(), INTERVAL -42 MINUTE)),
(2001, 51.5,  DATE_ADD(NOW(), INTERVAL -41 MINUTE)),
(2001, 53.8,  DATE_ADD(NOW(), INTERVAL -40 MINUTE)),
(2001, 55.5,  DATE_ADD(NOW(), INTERVAL -39 MINUTE)),
(2001, 56.8,  DATE_ADD(NOW(), INTERVAL -38 MINUTE)),
(2001, 57.5,  DATE_ADD(NOW(), INTERVAL -37 MINUTE)),
(2001, 57.8,  DATE_ADD(NOW(), INTERVAL -36 MINUTE)),
(2001, 58.0,  DATE_ADD(NOW(), INTERVAL -35 MINUTE)),
(2001, 58.2,  DATE_ADD(NOW(), INTERVAL -34 MINUTE)),
(2001, 58.5,  DATE_ADD(NOW(), INTERVAL -33 MINUTE)),
(2001, 58.8,  DATE_ADD(NOW(), INTERVAL -32 MINUTE)),
(2001, 59.0,  DATE_ADD(NOW(), INTERVAL -31 MINUTE)),
(2001, 59.2,  DATE_ADD(NOW(), INTERVAL -30 MINUTE)),
(2001, 59.5,  DATE_ADD(NOW(), INTERVAL -29 MINUTE)),
(2001, 59.8,  DATE_ADD(NOW(), INTERVAL -28 MINUTE)),
(2001, 60.0,  DATE_ADD(NOW(), INTERVAL -27 MINUTE)),
(2001, 60.2,  DATE_ADD(NOW(), INTERVAL -26 MINUTE)),
(2001, 60.5,  DATE_ADD(NOW(), INTERVAL -25 MINUTE)),
(2001, 60.8,  DATE_ADD(NOW(), INTERVAL -24 MINUTE)),
(2001, 61.0,  DATE_ADD(NOW(), INTERVAL -23 MINUTE)),
(2001, 61.3,  DATE_ADD(NOW(), INTERVAL -22 MINUTE)),
(2001, 61.8,  DATE_ADD(NOW(), INTERVAL -21 MINUTE)),
(2001, 62.5,  DATE_ADD(NOW(), INTERVAL -20 MINUTE)),
(2001, 63.5,  DATE_ADD(NOW(), INTERVAL -19 MINUTE)),
(2001, 64.8,  DATE_ADD(NOW(), INTERVAL -18 MINUTE)),
(2001, 66.2,  DATE_ADD(NOW(), INTERVAL -17 MINUTE)),
(2001, 67.8,  DATE_ADD(NOW(), INTERVAL -16 MINUTE)),
(2001, 69.5,  DATE_ADD(NOW(), INTERVAL -15 MINUTE)),
(2001, 71.2,  DATE_ADD(NOW(), INTERVAL -14 MINUTE)),
(2001, 72.8,  DATE_ADD(NOW(), INTERVAL -13 MINUTE)),
(2001, 74.5,  DATE_ADD(NOW(), INTERVAL -12 MINUTE)),
(2001, 76.0,  DATE_ADD(NOW(), INTERVAL -11 MINUTE)),
(2001, 77.2,  DATE_ADD(NOW(), INTERVAL -10 MINUTE)),
(2001, 78.0,  DATE_ADD(NOW(), INTERVAL  -9 MINUTE)),
(2001, 78.5,  DATE_ADD(NOW(), INTERVAL  -8 MINUTE)),
(2001, 78.8,  DATE_ADD(NOW(), INTERVAL  -7 MINUTE)),
(2001, 78.5,  DATE_ADD(NOW(), INTERVAL  -6 MINUTE)),
(2001, 77.8,  DATE_ADD(NOW(), INTERVAL  -5 MINUTE)),
(2001, 76.5,  DATE_ADD(NOW(), INTERVAL  -4 MINUTE)),
(2001, 74.8,  DATE_ADD(NOW(), INTERVAL  -3 MINUTE)),
(2001, 72.5,  DATE_ADD(NOW(), INTERVAL  -2 MINUTE)),
(2001, 69.8,  DATE_ADD(NOW(), INTERVAL  -1 MINUTE)),
(2001, 66.5,  NOW());

-- 为 101湿度 (monitor=2002) 生成少量样本
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(2002, 55.0, DATE_ADD(NOW(), INTERVAL -60 MINUTE)),
(2002, 56.2, DATE_ADD(NOW(), INTERVAL -50 MINUTE)),
(2002, 54.8, DATE_ADD(NOW(), INTERVAL -40 MINUTE)),
(2002, 53.5, DATE_ADD(NOW(), INTERVAL -30 MINUTE)),
(2002, 52.0, DATE_ADD(NOW(), INTERVAL -20 MINUTE)),
(2002, 51.5, DATE_ADD(NOW(), INTERVAL -10 MINUTE)),
(2002, 50.8, NOW());

-- 为 102温度 (monitor=2004) 生成少量样本
INSERT INTO t_sample_float (monitor, `value`, moment) VALUES
(2004, 28.0, DATE_ADD(NOW(), INTERVAL -60 MINUTE)),
(2004, 28.5, DATE_ADD(NOW(), INTERVAL -50 MINUTE)),
(2004, 29.2, DATE_ADD(NOW(), INTERVAL -40 MINUTE)),
(2004, 28.8, DATE_ADD(NOW(), INTERVAL -30 MINUTE)),
(2004, 27.5, DATE_ADD(NOW(), INTERVAL -20 MINUTE)),
(2004, 26.8, DATE_ADD(NOW(), INTERVAL -10 MINUTE)),
(2004, 26.2, NOW());
