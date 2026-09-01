# 运维统计 ID 域设计说明

## 1. 背景

2026-09 修复了一类统计缺陷：多个统计 SQL 把 `t_error_message_log.asset_id`
当作 `t_asset.id` 使用，导致 TOP 告警设备恒为空、按设备统计的告警数恒为 0。
根因是系统存在多个并行的 id 域，而告警日志的写入方与统计查询方对
`asset_id` 的语义理解不一致。本文档固化各 id 域的语义与桥接规则，避免
同类缺陷复发。

## 2. id 域对照

| 域 | 主键 | 说明 |
|---|---|---|
| 资产树 `t_asset` | `t_asset.id` | 统计与展示用的资产视图行；kind：0空间 / 1设备 / 2服务 / 3监测器 / 4操控器 |
| 监测器运行时 `t_probe` / `t_control` | 各自表的 `id` | 采集与控制运行时对象；id 由配置显式分配，两表 id 空间按约定不重叠 |
| 设备运行时 `t_device` | `t_device.id` | 设备台账与生命周期（`lifecycle_status`） |

## 3. 桥接规则（统计 SQL 必须遵守）

1. `t_error_message_log.asset_id` 存的是**监测器运行时 id**
   （`t_probe.id` / `t_control.id`），**不是** `t_asset.id`。
2. `t_asset` 的监测器/操控器行通过 `probe_id` / `control_id` 列引用运行时
   id；这些行的 `device_id` 为 NULL，归属设备须沿 `parent_id` 上溯到
   kind=1 的设备资产行，再取其 `device_id`。
3. `t_probe.parent` 在标准模型中直挂 `t_device.id`（服务经 `source` 列
   挂接）；需兼容 probe→service→device 布局时用 `LEFT JOIN` + `OR`，
   不能只按 service 链 INNER JOIN。
4. 聚合规则：告警与维护记录是设备的两条**独立一对多分支**，各自的
   COUNT/SUM 必须放在独立的标量子查询中；平铺 JOIN 会产生笛卡尔扇出，
   把 `SUM(cost)` 放大告警行数倍（COUNT DISTINCT 只能保护计数，保护不了
   SUM）。

## 4. 参考实现

- `StatisticsMapper.topAlarmAssets` —— 告警聚合按 `COALESCE(probe_id,
  control_id) = e.asset_id` 桥接到资产行。
- `StatisticsMapper.getDeviceRuntimeHistory` —— 三标量子查询版设备运行
  统计（规则 4 的落地）。
- `AnalysisMapper.countAlarmsForDevice` / `findProbeIdsForDevice`、
  `StatisticsMapper.findProbeIdsByDevice` —— 规则 2/3 的落地。

各 mapper 方法注释即规范；修改这些 SQL 前先对照本文第 3 节。
