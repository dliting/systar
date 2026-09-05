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
5. `t_asset.parent_id` 统一存**父资产行的 `t_asset.id`**。种子脚本直接
   写行 id；运行时创建路径（`AssetRepository.insertAssetView`）收到的
   是资产树使用的 per-kind 运行时 id，写入前必须翻译成父资产行 id，
   否则 UI 创建的资产在规则 2 的 parent 链上断裂、统计静默漏计。翻译
   依赖运行时 id 跨 kind 全局唯一这一既有约定（资产树本身也依赖它）；
   违反时降级为日志告警 + `parent_id=0`，不会错挂设备。

## 4. 参考实现

- `StatisticsMapper.topAlarmAssets` —— 告警聚合按 `COALESCE(probe_id,
  control_id) = e.asset_id` 桥接到资产行。
- `StatisticsMapper.getDeviceRuntimeHistory` —— 三标量子查询版设备运行
  统计（规则 4 的落地）。
- `AnalysisMapper.countAlarmsForDevice` / `findProbeIdsForDevice`、
  `StatisticsMapper.findProbeIdsByDevice` —— 规则 2/3 的落地。
- `AssetRepository.insertAssetView` / `parentAssetViewRowId` —— 规则 5 的
  落地（创建路径写入前把运行时父 id 翻译为父资产行 id）。

各 mapper 方法注释即规范；修改这些 SQL 前先对照本文第 3 节。

## 5. 存量数据修复（MySQL）

规则 5 生效前经 UI 创建的 `t_asset` 行，其 `parent_id` 存的是运行时
id，需按下述方式修复（经运行时父表解析，语义无歧义；**不能**用旧
`parent_id` 值反查——行 id 与运行时 id 数值区间重叠，正是原缺陷的
成因）。dev 环境 H2 为内存库，重启即重建，无需修复；以下
`UPDATE ... JOIN` 语法仅适用于 MySQL，文件型 H2 部署需改写。

```sql
-- PROBE 行：父为设备（t_probe.parent=t_device.id）或服务
UPDATE t_asset c
  JOIN t_probe p ON c.probe_id = p.id
  LEFT JOIN t_asset da ON da.kind = 1 AND da.device_id = p.parent
  LEFT JOIN t_asset sa ON sa.kind = 2 AND sa.service_id = p.parent
  SET c.parent_id = COALESCE(da.id, sa.id)
 WHERE c.kind = 3
   AND COALESCE(da.id, sa.id) IS NOT NULL
   AND c.parent_id <> COALESCE(da.id, sa.id);

-- CONTROL 行：同 PROBE，改用 t_control / control_id / kind=4

-- DEVICE 行：父为空间（t_device.parent=t_space.id）
UPDATE t_asset c
  JOIN t_device d  ON c.device_id = d.id
  JOIN t_asset spa ON spa.kind = 0 AND spa.space_id = d.parent
  SET c.parent_id = spa.id
 WHERE c.kind = 1 AND c.parent_id <> spa.id;

-- SERVICE / SPACE 行：同 DEVICE，按 t_service.parent / t_space.parent 解析到 kind=0 行
```

**修复后核查**：父资产已删除等原因会被上面的语句跳过（保留旧值），
执行下述查询确认无残留的断裂行；有结果则说明该行需人工处理：

```sql
-- PROBE/CONTROL 行的 parent 未解析到设备(kind=1)或服务(kind=2)资产行
SELECT c.id, c.kind, c.name, c.parent_id
  FROM t_asset c
 WHERE c.kind IN (3, 4)
   AND NOT EXISTS (
     SELECT 1 FROM t_asset p
      WHERE p.id = c.parent_id AND p.kind IN (1, 2));
```
