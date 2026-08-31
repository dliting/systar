# 监控曲线易用性完善 — 设计与实施计划

## Context

**为什么做**：监控曲线（`TrendChart.vue` + `trendOptions.js` + `TrendService.java`）是 Systar IoT 平台的核心可视化组件，当前功能完整但缺少对比/智能标注能力，可读性细节也有改进空间。本次工作为后续 SCADA/HMI 可视化（`docs/design/system-improvement-proposal.md` 中的 P1）做铺垫，让曲线更接近工业 HMI 风格。

**目标**：在保持现有 5 档粒度、下钻、实时推送、紧凑模式、独立窗口等能力的前提下，分三阶段交付：
1. **可读性 + 配套健壮性**（基础）：让单图更准、更清楚，且为多请求并发打基础
2. **智能标注**：阈值线 + 异常点高亮，让运维人员一眼看出问题
3. **对比能力**：多监测器叠加，跨监测器分析

**关键决策（已与用户确认）**：
- 多单位对比策略：**同单位共用 Y 轴叠加；不同单位用多 Y 轴（≤2 种）或小多图（≥3 种）**
- 标注视觉形式：**markLine 阈值水平线 + markPoint 异常圆点**
- 实施顺序：**分 3 阶段，每阶段独立验证、独立提交**

**用户偏好**：
- 测试驱动开发（项目开发约定）
- 单元测试覆盖率 > 90%
- 简洁可维护，避免无用功能
- 提交前执行验证-审核-完善循环
- 中文术语对应：监测器 Probe / 操控器 Control / 监控器 Monitor

---

## 当前实现基线

### 前端
- `frontend/src/views/statistics/components/TrendChart.vue` — 图表组件（单 monitorId）
- `frontend/src/views/statistics/composables/trendOptions.js` — ECharts option 构建器（`lineOption` / `intradayOption` / `emptyFrameOption`）
- `frontend/src/views/statistics/composables/useChart.js` — ECharts 实例生命周期（仅 `window.resize`，无 ResizeObserver）
- `frontend/src/views/statistics/composables/useTrendDrillDown.js` — 点击下钻
- `frontend/src/views/statistics/pages/MonitorTrend.vue` — 统计页内的趋势页（单选下拉框 + 工具栏）
- `frontend/src/views/statistics/pages/TrendStandalone.vue` — 独立窗口全屏
- `frontend/src/api/iot/trend.js` — Trend REST 客户端（data / default / metadata）
- `frontend/src/api/iot/asset.js` — `listAssets({kind})` 用于下拉框
- `frontend/src/config/monitor.js` — `formatMonitorValue` (精度全局 3 位)、`formatMonitorAxisTime` (仅 HH:mm)、`DEFAULT_PERIOD_WINDOW=60`
- `frontend/src/stores/websocket.js` — WS store，`probeValues` 字典无限增长，无重连重订阅

### 后端
- `extensions/systar-ops/src/main/java/com/systar/ops/statistics/controller/TrendController.java` — `/api/ops/trend/{data,default,metadata}`
- `extensions/systar-ops/src/main/java/com/systar/ops/statistics/service/TrendService.java` — 核心服务，`getMetadata` 返回 `name/caption/unit/dataType/detectInterval`（**未返回 min/max/warn_cond**）
- `extensions/systar-ops/src/main/java/com/systar/ops/analysis/service/AnalysisService.java` — `detectAnomalies(monitorId, start, end)` 已实现（滑动均值 + 标准差）
- `extensions/systar-ops/src/main/java/com/systar/ops/analysis/controller/AnalysisController.java` — `GET /api/ops/analysis/anomaly/{monitorId}` 已暴露（**前端无 API 客户端**）

### 阈值数据存储（重要：不在 `t_alarm_rule`）
- `t_probe.min_value` / `t_probe.max_value` (FLOAT) — 期望范围
- `t_probe.warn_cond` (VARCHAR(255)) — 表达式字符串如 `"value > 80"`，被 `ExpressionEvaluatorHolder` 编译执行
- `t_control.min_value` / `t_control.max_value` / `t_control.warn_cond` — 同上
- `t_alarm_rule` 表只有 `rule/way/warn_id/message_template/enabled/start/dedup_window_seconds` — **没有数值阈值字段**

**Schema NULL 约束已验证**（第五轮 C1 修复）：
- `sql/h2/ddl/01-core.sql:85,89,90` — `warn_cond/min_value/max_value` 均 `NULL`
- `sql/mysql/ddl/01-core.sql:85,89,90,114,116,117` — 同上
- 现有 `createProbe(dataType, interval)` 的 ~10 处调用点 **不会** 因扩展列 SQL 而失败：`createProbe` 函数体内部 `jdbc.update(INSERT_PROBE, ...)` 调用新增 3 个 `null` 默认值参数，函数签名保持不变，调用点无需修改（第六轮 NM1 精确化）
- 测试 helper 扩展策略：`INSERT_PROBE` SQL 加 3 列，`createProbe` 函数体同步加 3 个 null 参数；新增 `createProbeWithThresholds(dataType, interval, min, max, warnCond)` helper 用相同 SQL 但传实际值

---

## 范围

### 在范围内
- Tooltip / 时间轴 / 精度 / 均线一致性 / dataZoom 提示
- 阈值线（min/max 来源）、异常点高亮（复用现有后端）
- 多监测器对比（同单位共用 Y 轴叠加；不同单位多 Y 轴或小多图，最多 4 个）
- 请求竞态保护、ResizeObserver、WS LRU、WS 重连重订阅

### 不在范围内
- 预测曲线叠加（用户明确排除）
- 用户临时阈值输入（用户明确排除）
- 统计正常区间背景带（用户明确排除）
- 导出 PNG/CSV、注释、自动刷新指示（用户未选）
- SCADA SVG 画布编辑器（P1 长期规划，与本计划无关）
- i18n 国际化（保持硬编码中文）

---

## 架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                       MonitorTrend.vue (容器)                       │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  Toolbar: monitor select / granularity / daterange /        │    │
│  │           mode=单图|对比 / showThresholds / showAnomalies    │    │
│  └─────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │             TrendChart.vue (single mode)                    │    │
│  │  ┌───────────────────────────────────────────────────────┐  │    │
│  │  │  Summary bar + ECharts instance (line/intraday/empty) │  │    │
│  │  └───────────────────────────────────────────────────────┘  │    │
│  └─────────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │   ComparisonChart.vue (对比模式，阶段 3 新增)               │    │
│  │   - 按 unit 分组：1 组→单 Y 轴，2 组→双 Y 轴，3+ 组→小多图  │    │
│  │   - 内部使用 trendOptions.js 的 series builder              │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘

数据源（容器加载）:
  - getTrendData(monitorId, kind, range, granularity) × N
  - getTrendMetadata(monitorId, kind)  ← 阶段 2 扩展返回 min/max/warnCond
  - detectAnomalies(monitorId, start, end)  ← 阶段 2 新增前端客户端
```

### 阶段 1 修改清单（D + A 可读性 + 配套健壮性）

| 文件 | 变更 |
|------|------|
| `frontend/src/config/monitor.js` | 新增 `UNIT_PRECISION_MAP`；`formatMonitorValue(val, unit?)` 支持按 unit 精度（**所有调用点必须显式传 unit**）；新增 `formatAxisTimeAdaptive(timeStr, granularity, rangeDays)` **替换** 现有 `formatLineAxisTime`；新增 `computeRangeDays(dateRange)` 工具 |
| `frontend/src/views/statistics/composables/trendOptions.js` | Tooltip 单位仅首行（header 显示 unit）；时间轴用 adaptive 格式（**xAxis.data 与 markPoint.coord 必须用同一格式化函数**）；dataZoom 起始位置加"← 更早"提示；均线全 null 时 title 元素提示"数据不足" |
| `frontend/src/views/statistics/composables/useChart.js` | 新增 `ResizeObserver`，**与 window.resize 共用 rAF 去抖**；容器尺寸变化时 `resize()` |
| `frontend/src/views/statistics/components/TrendChart.vue` | summary bar 的 `formatMonitorValue` 调用必须传 `props.unit`（4 处：line 11/16/20/88）；**`formatMA(arr)` 改签名为 `formatMA(arr, unit)`**（NI2 修复），3 处模板调用都传 `props.unit`；**onUnmounted 替换 `chartInstance.dispose()` 为 useChart 返回的 dispose**（NI7/M1 修复，幂等）；**buildOption 同步两处调用**（第五轮 I1 修复）：阶段 1 lineOption 分支加 `computeRangeDays(dateRange.value)` 作为第 8 参数；阶段 2 lineOption 分支继续加 `props.thresholds, props.anomalyPoints, props.showThresholds, props.showAnomalies`；阶段 2 intradayOption 分支同步加 thresholds/anomalyPoints/showThresholds/showAnomalies（I4 修复，详见行 654） |
| `frontend/src/views/statistics/pages/MonitorTrend.vue` | 引入组件作用域 `loadToken` 序号防止竞态（每次自增，回调里校验是否最新） |
| `frontend/src/stores/websocket.js` | `probeValues` LRU（**last-access 时间戳淘汰**，最多 `MAX_PROBE_VALUES = 200` keys，常量定义带注释）；`onopen` 时遍历 `subscribedMonitorIds` 重订阅 |

### 阶段 2 修改清单（B 智能标注）

| 文件 | 变更 |
|------|------|
| `extensions/systar-ops/.../service/TrendService.java` | `queryProbeById` / `queryControlById` SQL 增列 `min_value, max_value, warn_cond`；**RowMapper 内部 `map.put("minValue", rs.getFloat("min_value"))` 等显式取列**（必须按列名取，禁止按索引）；返回 Map 增加 `minValue/maxValue/warnCond` 键。这两个方法同时被 `resolveDetectIntervalSeconds` / `resolveDataType` 调用，扩展列不能影响这两个调用路径 |
| `extensions/systar-ops/.../service/TrendServiceTest.java` | 新增 `getMetadataReturnsThresholds`、`getMetadataStillResolvesDetectIntervalAfterExtension`（回归）、`getMetadataStillResolvesDataTypeAfterExtension`（回归）、`getMetadataEmptyMapForMissingMonitor`（空 Map 场景）；**扩展测试 helper `INSERT_PROBE` / `INSERT_CONTROL` SQL 加入 `min_value/max_value/warn_cond` 列（可为 NULL）**；新增 `createProbeWithThresholds(dataType, interval, min, max, warnCond)` helper（N4 + NI1 修复：**与现有 `createProbe(dataType, interval)` 参数顺序兼容**，新参数放后面） |
| `frontend/src/api/iot/analysis.js` (新建) | `detectAnomalies(monitorId, start, end)` REST 客户端；**内部做字段映射 `timestamp→time`, `actualValue→actual`, `expectedValue→expected`**（N2 修复）；**响应解构用 `Array.isArray(res) ? res : (res?.data || [])`**（NC3 修复，因为 `systarApi` 拦截器已返回 `res.data`，再 `.data` 是错的） |
| `frontend/src/api/iot/trend.js` | （无改动，metadata 复用） |
| `frontend/src/views/statistics/components/TrendChart.vue` | 新增 props `thresholds: {min, max, warnCond}`、`anomalyPoints: [{time, actual, expected, deviation, severity}]`、`showThresholds`、`showAnomalies`；**warnCond 文本展示时必须走 escapeHtml**（formatMA 签名改动已在阶段 1 完成，本阶段无需重做）；**watch deps 数组必须同步扩展**（第七轮 I1 修复）：行 140 数组新增 `props.thresholds, props.anomalyPoints, props.showThresholds, props.showAnomalies`，否则开关切换不触发重渲染 |
| `frontend/src/views/statistics/composables/trendOptions.js` | `lineOption` 接收 thresholds + anomalyPoints；输出 `markLine` + `markPoint`；**markPoint 用 bucket 匹配 + coord 字面值**（NC1/NC4 修复）；**markLine label 走 `formatMonitorValue(thresholds.max, unit)`**（N5 修复）；关闭时返回空数组。**`lineOption` 最终签名**（第六轮 NM2）：`lineOption(dataPoints, avg5, avg10, avg20, unit, granularity, compact, rangeDays, thresholds, anomalyPoints, showThresholds, showAnomalies)` — 12 参数。**`intradayOption` 同步扩展签名**（I4 修复）：`intradayOption(points, unit, displayCount, detectIntervalSeconds, compact, thresholds, anomalyPoints, showThresholds, showAnomalies)` — 后 5 个参数运行时恒为默认值（thresholds=null, anomalyPoints=[]），仅保持 API 形态一致，便于 TrendChart.vue buildOption 统一调用 |
| `frontend/src/views/statistics/pages/MonitorTrend.vue` | 工具栏增加"显示阈值" / "显示异常"开关（默认开）；**仅 HOUR/DAY 粒度**调 `detectAnomalies`；metadata 拿到 thresholds；thresholds/anomalyPoints 容错（null/空数组都不渲染） |

### 阶段 3 修改清单（C 对比能力）

| 文件 | 变更 |
|------|------|
| `frontend/src/views/statistics/composables/palette.js` (新建) | 8 色高对比度调色板 + `pickColor(index)` |
| `frontend/src/views/statistics/composables/trendOptions.js` | 新增 `multiSeriesOption({series: [{caption, unit, color, dataPoints, anomalies, thresholds}], unitGroups})` — 同 unit 单 Y 轴 / 不同 unit 双 Y 轴；**每个 series 自带 thresholds/anomalies，叠加各自的 markLine/markPoint**（N10：明确阈值/异常来源） |
| `frontend/src/views/statistics/composables/smallMultiples.js` (新建) | `smallMultiplesOption({series, granularity})` — ECharts grid 数组，每监测器一个子图 |
| `frontend/src/views/statistics/components/ComparisonChart.vue` (新建) | 按 unit 分组：1 组→`multiSeriesOption`，2 组→`multiSeriesOption`（双 Y 轴），3+ 组→`smallMultiplesOption`；**部分监测器加载失败时显示降级占位**（在子图位置显示"加载失败"） |
| `frontend/src/views/statistics/pages/MonitorTrend.vue` | 顶部增加模式切换 `单图 | 对比`；**模式切换时强制重置 `monitorId = null` + `clearChartData()`**；用 `v-if="mode==='single'"` 和 `v-if="mode==='compare'"` 渲染两个独立 `el-select`（避免 `:multiple` 动态切换的 props 警告）；对比模式 max=4；**并发请求用 `Promise.allSettled` 而非 `Promise.all`**，部分失败时只显示成功的；**每个监测器单独调 `getTrendMetadata` 拿 thresholds、`detectAnomalies` 拿 anomalyPoints** |
| `frontend/src/views/statistics/pages/TrendStandalone.vue` | 同步支持 `monitorIds=1,2,3` 多 ID 参数；URL 解析时 `Array.isArray` 守卫；**单 ID URL `?monitorId=X` 保持向后兼容**（N11：验证清单必须覆盖） |

---

## 数据流

### 阶段 1：可读性
- 单图模式数据流不变（getTrendDefault / getTrendData），仅 option 构建和图表 resize 改善
- WS 实时推送链路不变，但 store 加 LRU + 重连重订阅

### 阶段 2：智能标注
```
MonitorTrend.vue
  ├── getTrendMetadata(monitorId, kind)  → 拿到 thresholds {min, max, warnCond}
  ├── getTrendData / getTrendDefault     → 主曲线数据
  └── detectAnomalies(monitorId, start, end)  → anomalyPoints[]
        ↓
  TrendChart.vue 接收 thresholds / anomalyPoints / showThresholds / showAnomalies
        ↓
  trendOptions.js lineOption/intradayOption
    series.markLine = showThresholds ? [{yAxis: max}, {yAxis: min}] : []
    series.markPoint = showAnomalies ? anomalyPoints.map(...) : []
```

### 阶段 3：对比模式
```
MonitorTrend.vue (对比模式)
  monitorIds = [1, 2, 3, 4]  (max 4)
    ↓
  并发 Promise.allSettled(monitorIds.map(id => Promise.all([
    getTrendData(id, kind, range, gran),
    getTrendMetadata(id, kind),
    detectAnomalies(id, start, end).catch(() => [])  // 异常检测失败容忍，不阻塞主流程
  ])))
    ↓
  过滤掉 rejected 的结果，对 fulfilled 的结果构造 trendSeries
  trendSeries = [{ id, caption, unit, color, dataPoints, avg5/10/20, thresholds, anomalies }]
    ↓
  若 trendSeries.length === 0：显示"所有监测器加载失败"
  若 trendSeries.length < monitorIds.length：顶部显示"N 个监测器加载失败"
    ↓
  groupedByUnit = groupBy(trendSeries, s => s.unit || '(无单位)')
    ↓
  switch (Object.keys(groupedByUnit).length) {
    case 1: ComparisonChart → multiSeriesOption(single Y)
    case 2: ComparisonChart → multiSeriesOption(dual Y)
    default: ComparisonChart → smallMultiplesOption
  }
```

---

## 关键实现细节

### A1 Tooltip 单位去重（`trendOptions.js`）
当前 `formatter(params)` 每个 series 追加 `unitLabel`。改造为：
- Tooltip header 行显示时间 + unit（如 `2026-06-15 14:00 (°C)`）
- Series 行仅显示 `name: value`，不再追加 unit
- `formatter` 内的 `value` 显示用 `formatMonitorValue(p.value, unit)`（unit 来自外层闭包）
- 减少视觉冗余，多曲线时尤其明显

### A2 时间轴智适应（`monitor.js` 新增 `formatAxisTimeAdaptive` + `computeRangeDays`）
```js
/**
 * Compute the range in days between two datetime strings.
 * @param {[string, string] | null} dateRange - ISO-like datetime strings
 * @returns {number} 0 if range invalid
 */
export function computeRangeDays(dateRange) {
  if (!Array.isArray(dateRange) || dateRange.length !== 2) return 0
  const start = new Date(dateRange[0].replace(' ', 'T'))
  const end   = new Date(dateRange[1].replace(' ', 'T'))
  if (isNaN(start.getTime()) || isNaN(end.getTime())) return 0
  return Math.max(0, (end.getTime() - start.getTime()) / 86_400_000)
}

/**
 * Adaptive axis time formatter.
 * Replaces formatLineAxisTime (used by lineOption). intradayOption keeps
 * formatMonitorAxisTime (HH:mm) because INTRADAY range is always < 1 day.
 *
 * @param {string} timeStr - "YYYY-MM-DDTHH:mm:ss" or "YYYY-MM-DD HH:mm:ss"
 *                           (no timezone offset; backend Jackson default for LocalDateTime)
 * @param {string} granularity - one of INTRADAY/HOUR/DAY/WEEK/MONTH
 * @param {number} rangeDays - from computeRangeDays(dateRange)
 *
 * Threshold notes: rangeDays thresholds use 1.5 and 365.5 to avoid
 * boundary jitter (a 1.0-day range flipping between "same day" and
 * "cross day" modes at 1.01 days would cause UI flicker).
 */
export function formatAxisTimeAdaptive(timeStr, granularity, rangeDays) {
  if (!timeStr) return ''
  const clean = timeStr.replace('T', ' ').split('.')[0]
  if (granularity === 'MONTH') return clean.substring(0, 7)           // 2026-01
  if (granularity === 'WEEK' || granularity === 'DAY') {
    return rangeDays > 365.5 ? clean.substring(2, 10) : clean.substring(5, 10)  // 跨年显示 YY-MM-DD
  }
  if (granularity === 'HOUR') {
    return rangeDays > 1.5 ? clean.substring(5, 13) : clean.substring(11, 16)   // 跨日 MM-DD HH:mm
  }
  return clean.substring(11, 16)  // INTRADAY: HH:mm
}
```
**替换关系**：
- `formatAxisTimeAdaptive` **替换** `trendOptions.js:25` 的 `formatLineAxisTime`，仅被 `lineOption` 使用
- `intradayOption` 继续用 `formatMonitorAxisTime`（INTRADAY 时间窗 < 1 日，不需要智适应）
- `lineOption` 把 `rangeDays` 加入函数签名：`lineOption(dataPoints, avg5, avg10, avg20, unit, granularity, compact, rangeDays)`
- **阈值用 1.5 / 365.5 而非 1 / 365**，避免边界抖动（N8 修复）

### A3 数字精度按 unit（`monitor.js`）
```js
export const UNIT_PRECISION_MAP = {
  '°C': 1, C: 1, '℃': 1,
  '%': 0, '%RH': 0,
  V: 2, mV: 0,
  A: 2, mA: 2,
  kPa: 2, Pa: 0, MPa: 3,
  'm³/h': 1, 'L/min': 1,
  rpm: 0,
  Wh: 0, kWh: 2,
}

export function formatMonitorValue(val, unit) {
  if (val === null || val === undefined) return '--'
  if (typeof val === 'number') {
    if (Number.isInteger(val)) return val.toString()
    const precision = (unit && UNIT_PRECISION_MAP[unit]) ?? MONITOR_VALUE_PRECISION
    return val.toFixed(precision)
  }
  return String(val)
}
```
**所有调用点必须显式传 unit**（搜索 `formatMonitorValue(` 全部审查）：
- `trendOptions.js` `lineOption.formatter` / `intradayOption.formatter`：从外层闭包取 `unit`
- `TrendChart.vue:11/16/20` summary bar：`formatMonitorValue(summary?.currentValue, props.unit)` 等
- `TrendChart.vue:88` `formatMA(arr, unit)` 内部调用：`formatMonitorValue(arr[i], unit)`（NI2 修复，归阶段 1）
- **例外**：纯工具函数（无 unit 上下文）保持 1 参数签名（向后兼容），但精度回退到全局 3 位，需在 JSDoc 中标明（第五轮 M5 修复，避免与"必须传 unit"冲突）
- **未在 map 中的 unit 回退到 3 位**；新增 unit 时应在 UNIT_PRECISION_MAP 中显式登记精度（NM3）

### A4 均线数据不足提示 + 均线显示精度按 unit（**NI2 修复：归阶段 1**）
- `lineOption` 在所有 MA 数组全为 null 时，通过 ECharts `title` 元素在图表右上角注入一行小字提示 "均线数据不足（需 ≥ 5 个数据点）"（仅 compact=false 显示）
- `TrendChart.vue` 的 `formatMA(arr, unit)` **必须改签名**：接收 unit 参数，内部 `formatMonitorValue(arr[i], unit)` 让精度按 unit
- 模板 3 处调用点（avg5/avg10/avg20）都要传 `props.unit`：
  ```vue
  <span style="color: #ffab00">{{ formatMA(avg5, unit) }}</span> /
  <span style="color: #ce93d8">{{ formatMA(avg10, unit) }}</span> /
  <span style="color: rgba(255,82,82,0.7)">{{ formatMA(avg20, unit) }}</span>
  ```
- 数据不足时仍返回 '--'，行为与曲线 null 一致
- **归阶段 1**：formatMA 改动与 summary bar formatMonitorValue 改动强相关，必须同时改，否则阶段 1 实施后 summary bar 精度不一致（最高 80.0 vs 均线 80.000）

### A5 dataZoom "← 更早"提示
- `lineOption` 当 `zoomStartForWindow(periodCount) > 0` 时，在 dataZoom slider 起始位置上方插入 `graphic` 文本 `"← 拖动查看更早"`，仅显示一次
- `graphic.z = 100` 确保在 dataZoom slider 之上但不遮挡交互；位置 `x: '5%', y: '85%'`（与 slider 错开）

### D1 请求竞态保护（`MonitorTrend.vue`）
不用 AbortController（一些 HTTP 库和后端集成不便），改用序号校验：
```js
// 在 <script setup> 内部声明（组件作用域，多实例互不干扰）
let loadToken = 0
async function loadTrendData() {
  const myToken = ++loadToken
  // ... await ...
  if (myToken !== loadToken) return  // 已被新请求取代，丢弃结果
  applyResponseData(payload)
}
```
**注意**：`let loadToken = 0` 必须在 `<script setup>` 函数体内部声明，**不能放模块级**。否则同一页有多个 MonitorTrend 实例时会共享 token 互相干扰。

**loadToken 适用范围**（第五轮 I3 修复）：
- ✅ `loadTrendData` 和 `loadDefaultView`：会改变 `dataPoints/avg5/avg10/avg20/intradayPoints/summary` 等图表数据，必须 token 保护
- ❌ `loadMonitorList` 和 `loadMetadata`：前者只填下拉框（list ref），后者只填 `unit`/`detectIntervalSeconds`，无图表数据竞争，**不需要 token 保护**
- 若都加 token：metadata 完成后会被后续 trendData 取消，逻辑混乱；且串行化导致性能下降

### D2 ResizeObserver（`useChart.js`）— 与 window.resize 共用 rAF 去抖
```js
import { ref, onUnmounted } from 'vue'
export function useChart() {
  const chartRef      = ref(null)
  let   chartInstance = null
  let   ro            = null
  let   rafId         = null

  function scheduleResize() {
    if (rafId !== null) return  // 已排队
    rafId = requestAnimationFrame(() => {
      rafId = null
      chartInstance?.resize()
    })
  }

  function initChart(theme = 'dark') {
    if (!chartRef.value) return null
    dispose()
    chartInstance = echarts.init(chartRef.value, theme)
    if (typeof ResizeObserver !== 'undefined') {
      ro = new ResizeObserver(scheduleResize)  // 共用去抖
      ro.observe(chartRef.value)
    }
    return chartInstance
  }

  function dispose() {
    if (rafId !== null) { cancelAnimationFrame(rafId); rafId = null }
    ro?.disconnect(); ro = null
    chartInstance?.dispose(); chartInstance = null
  }

  // window resize 走相同的去抖路径，避免与 ResizeObserver 双触发
  window.addEventListener('resize', scheduleResize)
  onUnmounted(() => {
    window.removeEventListener('resize', scheduleResize)
    dispose()
  })

  return { chartRef, initChart, setOption, resize: scheduleResize, dispose }
}
```
**为什么用 rAF 而不是 debounce**：浏览器一帧内多次触发只在下一帧重绘一次 resize，足够顺滑且无需配置 delay；resize 本身就是浏览器布局变更，rAF 自然对齐帧。

### D3 WS LRU + 重连重订阅（`websocket.js`）— last-access 淘汰（**write + subscribe 两路 touch**）
```js
/**
 * Max probe-value keys kept in memory; further entries evict least-recently-accessed.
 * Based on: single-page monitors usually ≤ 50, operations list pagination ≤ 100,
 * leaving headroom of 200. If raised to 1000+, evictIfNeeded should switch to a
 * linked-list to avoid O(n log n) sort per write.
 */
const MAX_PROBE_VALUES = 200

/**
 * Internal last-access timestamp map. Intentionally NOT reactive — it's pure
 * LRU bookkeeping, no consumer needs to re-render when access times update.
 * Exposing it would cause unnecessary reactivity churn.
 */
const _lastAccess = new Map()  // monitorId → last-access timestamp (ms)

const probeValues    = ref({})  // { [monitorId]: latestMsg } (reactive — consumed by watchers)

function touchProbeAccess(id) {
  _lastAccess.set(id, Date.now())
}

function evictIfNeeded() {
  if (_lastAccess.size <= MAX_PROBE_VALUES) return
  const sorted = [..._lastAccess.entries()].sort((a, b) => a[1] - b[1])
  const evictCount = sorted.length - MAX_PROBE_VALUES
  for (let i = 0; i < evictCount; i++) {
    const id = sorted[i][0]
    delete probeValues.value[id]
    _lastAccess.delete(id)
  }
}

function writeProbeValue(id, msg) {
  probeValues.value[id] = msg
  touchProbeAccess(id)         // ① 写入时 touch（后端在推 = 数据流活跃）
  evictIfNeeded()
}

function subscribe(monitorIds) {
  // ... ws.send 原订阅逻辑（受 readyState === OPEN 守卫）...
  // 注：即使 WS 未 open（断网期间），touch 仍发生（M5）；
  //     用户表达订阅意图即视为活跃，网络恢复后 onopen 会重订阅 subscribedMonitorIds
  for (const id of monitorIds) {
    touchProbeAccess(id)       // ② 订阅时 touch（用户主动订阅 = 用户在看，强信号）
    if (!subscribedMonitorIds.value.includes(id)) {
      subscribedMonitorIds.value.push(id)
    }
  }
}

function unsubscribe(monitorIds) {
  // ... ws.send 原退订逻辑 ...
  // 注意：unsubscribe 时不删除 _lastAccess 条目，让 LRU 自然淘汰（避免订阅-退订-订阅抖动）
  subscribedMonitorIds.value = subscribedMonitorIds.value.filter(
    id => !monitorIds.includes(id)
  )
}

// ws.onopen 中：
ws.onopen = () => {
  connected.value = true
  reconnectDelay = 2000
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  if (subscribedMonitorIds.value.length > 0) {
    ws.send(JSON.stringify({ action: 'subscribe', monitorIds: subscribedMonitorIds.value }))
    // 重订阅后立即 touch（重连间隙可能漏掉推送，重订阅即视为活跃）
    for (const id of subscribedMonitorIds.value) touchProbeAccess(id)
  }
}

return {
  connected,
  probeValues,         // 暴露 reactive ref，现有 watch (() => store.probeValues[id]) 继续工作
  subscribe,
  unsubscribe,
  // 不暴露 readProbeValue（NC2 修复：读取不应触发 touch，避免 watch getter 重算噪声）
  // 不暴露 _lastAccess / touchProbeAccess / evictIfNeeded / writeProbeValue（私有 helper）
  // ...
}
```

**为什么只有 write + subscribe 两路 touch（不含 read）**：
- **write** = 后端在推（数据流活跃）
- **subscribe** = 用户主动订阅（用户在看，强信号）
- ~~read = watcher 在响应~~ — **不能作为 touch 来源**（NC2 修复）：
  - Vue 3 watch getter 在依赖收集时会重算，每次重算都触发 touch
  - 即使 watcher callback 进入 `if (!msg) return` 防御分支（如非 INTRADAY 粒度），touch 已经发生
  - 用户切到 HOUR 粒度但停留时，HOUR 数据不消费 INTRADAY 推送，但 watch getter 重算仍 touch INTRADAY key — 语义错误
- 订阅即代表用户主动查看，已是强信号；read 是被动响应，不算用户主动行为

**所有下游消费者保持现有写法**（无需迁移）：
| 文件 | 行 | watch 表达式 | 防御 |
|------|----|---|---|
| `MonitorTrend.vue` | 256 | `watch(() => wsStore.probeValues[monitorId.value], ...)` | 已有 `if (!msg) return` |
| `TrendStandalone.vue` | 151 | `watch(() => wsStore.probeValues[monitorId.value], ...)` | 已有 `if (!msg) return` |
| `operations/index.vue` | 1094 | `watch(() => wsStore.probeValues[detail.value?.id], ...)` | 已有 `if (!msg || ...) return` |

**注意**：
- 现有 watch 都用 `wsStore.probeValues[id]` 直接访问，**不需要改为 readProbeValue**
- 被淘汰的 key 下次推送到达时会重新创建（`writeProbeValue` 自动 touch），watch 自动接续
- `subscribedMonitorIds` 包含的 key 不会被淘汰（subscribe 时已 touch，且只要用户不退订就持续活跃）

**测试要求**：
- `probeValues LRU evicts least-recently-accessed beyond MAX`
- `probeValues hot key not evicted under continuous writes`（高频推送场景）
- `probeValues subscribed-but-quiet key not evicted`（**N1 关键测试**：subscribe 后无推送，模拟 200+ 其他 key 推送，验证订阅 key 仍在）
- `reconnect re-subscribes monitored ids on open`
- `evicted key receives next push normally`（被淘汰的 key 下次推送能重建）

### B1 阈值线（后端 `TrendService.getMetadata`）
```java
// 修改 queryProbeById SQL（queryControlById 同步对称改）：
"SELECT name, caption, unit, monitor_kind, time_interval, min_value, max_value, warn_cond FROM t_probe WHERE id = ?"
// RowMapper 内部必须显式 map.put（按列名取，禁止按索引）：
map.put("name",           rs.getString("name"));
map.put("caption",        rs.getString("caption"));
map.put("unit",           rs.getString("unit"));
// ... monitor_kind/time_interval 略 ...
map.put("minValue",       rs.getObject("min_value", Float.class));   // 用 Float.class 容忍 NULL
map.put("maxValue",       rs.getObject("max_value", Float.class));
map.put("warnCond",       rs.getString("warn_cond"));

// getMetadata(...) 内：
meta.put("minValue", probe.get("minValue"));
meta.put("maxValue", probe.get("maxValue"));
meta.put("warnCond", probe.get("warnCond"));
```
**关键约束**：
- `queryProbeById` 同时被 `resolveDetectIntervalSeconds` (TrendService.java:353) 和 `resolveDataType` (:369) 调用；这两个方法只读 `detectInterval / dataType`，扩展列**不会破坏**它们（JdbcTemplate RowMapper 显式按列名取值）
- **禁止把 RowMapper 改成按列索引取值**（如 `rs.getFloat(5)`），列顺序变更会引入隐蔽 bug
- 阶段 2 测试必须包含 `getMetadataStillResolvesDetectIntervalAfterExtension` / `getMetadataStillResolvesDataTypeAfterExtension` 回归

### B1 阈值线（前端 `trendOptions.js`）— label 走 formatMonitorValue 保持精度一致（N5 修复）
```js
// trendOptions.js 顶部已有 import（无需新增）：
//   import { formatMonitorValue, formatMonitorAxisTime, DEFAULT_PERIOD_WINDOW } from '@/config/monitor'
// trendOptions.js:2 已 import formatMonitorValue，直接使用即可

function buildThresholdMarkLines(thresholds, showThresholds, unit) {
  if (!showThresholds || !thresholds) return []
  const lines = []
  if (thresholds.max != null) {
    lines.push({
      yAxis   : thresholds.max,
      name    : '上限',
      lineStyle: { color: '#ff5252', type: 'dashed' },
      // label.formatter 是静态字符串（已包含 formatMonitorValue 结果）
      // 不要用 {c} 占位符（ECharts 会用原始数字替换，绕过精度控制）
      label   : { formatter: `上限 ${formatMonitorValue(thresholds.max, unit)}`, position: 'end' },
    })
  }
  if (thresholds.min != null) {
    lines.push({
      yAxis   : thresholds.min,
      name    : '下限',
      lineStyle: { color: '#69f0ae', type: 'dashed' },
      label   : { formatter: `下限 ${formatMonitorValue(thresholds.min, unit)}`, position: 'end' },
    })
  }
  return lines
}
// series[1] (均值) 增加 markLine: { symbol: 'none', silent: false, data: buildThresholdMarkLines(thresholds, showThresholds, unit) }
```

**字段映射约定**：
- 后端 `getMetadata` 返回 JSON：`{ ..., minValue: 0, maxValue: 100, warnCond: "value > 80" }`（camelCase 直接序列化 Map）
- 前端 `MonitorTrend.vue` 把 `meta.minValue/meta.maxValue/meta.warnCond` 映射为 `thresholds = { min: meta.minValue, max: meta.maxValue, warnCond: meta.warnCond }`
- `warnCond` 仅作为 tooltip/侧栏文本展示，**展示前必须走 `escapeHtml`**（已有同名工具 `trendOptions.js:18`）防止 `<` `>` `&&` 等字符破坏 HTML
- `warnCond` 不解析为水平线（表达式可能是 `value > 80 && value < 120` 等复杂形式）
- **markLine label formatter 必须用 `formatMonitorValue(thresholds.max, unit)`**（N5 修复），避免温度阈值 80 显示成 80.00001
- **formatter 是静态字符串**（NI3 修复）：用 JS 模板插值 `${...}` 一次性计算，不要用 ECharts `{c}` 占位符（会用原始数字替换，绕过精度控制）

### B2 异常点（**字段映射** + **markPoint.coord 用 dataIndex**）— 修复 N2 字段错配 + N6 稳健性

#### 字段映射（N2 + NC3 修复）

后端 `AnomalyPoint` record 字段为 `timestamp/actualValue/expectedValue/deviation/severity`（Spring Jackson 默认按字段名序列化）。前端 `api/iot/analysis.js` 必须做字段映射，避免 plan 各章节用 `time/actual/expected` 时拿到 undefined。

**关键约束**：`systarApi` 响应拦截器已 `return Promise.resolve(res.data)`（`request.js:37`），调用方拿到的就是 HTTP body。后端 `AnalysisController.detectAnomalies` 直接返回 `List<AnomalyPoint>`（无 `{code, data}` 包装），所以 body 直接是数组。

```js
// frontend/src/api/iot/analysis.js
import { systarApi } from '@/api/request'

/**
 * Detect anomalies for a monitor within a time range.
 *
 * Backend returns AnomalyPoint records with Java field names
 * (timestamp/actualValue/expectedValue/deviation/severity);
 * this client normalizes to frontend-friendly names (time/actual/expected).
 *
 * Note: systarApi interceptor already unwraps to res.data (HTTP body),
 * so the resolved value IS the array (or {data: [...]} if backend wraps).
 *
 * @returns {Promise<Array<{time: string, actual: number, expected: number, deviation: number, severity: string}>>}
 */
export function detectAnomalies(monitorId, startIso, endIso) {
  return systarApi({
    url   : `/api/ops/analysis/anomaly/${monitorId}`,
    method: 'get',
    params: { start: startIso, end: endIso },
  }).then(res => {
    // res 已经是 HTTP body（systarApi 拦截器已 unwrap）
    // 后端直接返回 List<AnomalyPoint>，所以 res 通常是数组
    // Array.isArray 防御：若后端将来加 {code, data} 包装，也能容错
    const list = Array.isArray(res) ? res : (res?.data || [])
    return list.map(p => ({
      time     : p.timestamp,         // 后端 timestamp → 前端 time
      actual   : p.actualValue,
      expected : p.expectedValue,
      deviation: p.deviation,
      severity : p.severity,
    }))
  })
}
```

**为什么不改后端字段名**：后端 record 是稳定 API，可能有其他消费者（如未来 SCADA 模块、外部 API）。前端适配层做映射成本最低。

#### markPoint.coord 用 bucket 匹配 + 严格字面值（NC1/NC4 修复）

**重要事实更正**：ECharts 5 的 markPoint.data **不支持 `dataIndex` 属性**（参考官方文档 https://echarts.apache.org/en/option.html#series-line.markPoint.data）。支持的属性是 `coord`（坐标系坐标 [x, y]）、`xAxis`/`yAxis`（值轴值）、`type`（min/max/average）、`name`/`value`。第二轮 plan 的 N6 修复方案是错误的。

**真正的问题**：在 category 轴下，`coord[0]` 必须严格等于 xAxis.data 中的某个字符串字面值。而：
- 后端 anomaly.timestamp 来自 `t_sample_float.moment`（精度通常到秒，落在 bucket 内任意位置）
- 前端 dataPoints[i].time 来自 `t_monitor_stats.bucket_start`（按粒度截断到 bucket 起点）
- 用 `formatAxisTimeAdaptive` 格式化时：
  - DAY 粒度：anomaly "2026-06-15 14:23:45" → "06-15"，bucket "2026-06-15 00:00:00" → "06-15" ✓ 匹配
  - HOUR 粒度 rangeDays > 1.5：anomaly "2026-06-15 14:23:45" → "06-15 14"，bucket "2026-06-15 14:00:00" → "06-15 14" ✓ 匹配
  - HOUR 粒度 rangeDays ≤ 1.5：anomaly → "14:23"，bucket → "14:00" ✗ 不匹配

**正确实现**：先用"按 bucket 时间窗匹配"找到对应的 dataPoint，再用其 xAxis.data 标签作为 coord[0]：

```js
/**
 * Find the index of the dataPoint whose time bucket contains anomalyTime.
 * HOUR bucket = same hour (Math.floor(ts / 3_600_000))
 * DAY bucket  = same day  (Math.floor(ts / 86_400_000))
 */
function findBucketIndex(dataPoints, anomalyTime, granularity) {
  if (!anomalyTime || !dataPoints || dataPoints.length === 0) return -1
  // 后端 LocalDateTime 无时区序列化（"2026-06-15T14:00:00" 或 "YYYY-MM-DD HH:mm:ss"）；
  // 前端 Date 按本地时区解析；anomaly 与 bucket 同行为，比较安全（M8）
  const a = new Date(anomalyTime.replace(' ', 'T')).getTime()
  if (!Number.isFinite(a)) return -1
  const divisor = granularity === 'HOUR' ? 3_600_000 : 86_400_000  // DAY/WEEK/MONTH 都按日对齐
  // 注：WEEK/MONTH 粒度因前端策略不调用 detectAnomalies（plan 行 854），不会进入此路径；
  // 即便误传，WEEK bucket 起点是周一 0:00，MONTH 是月初 0:00，anomaly 实际时间戳 divisor 截断后
  // 通常 ≠ 周一/月初 → 返回 -1 → 跳过。这是预期健壮行为（M3）
  const anomalyBucket = Math.floor(a / divisor)
  for (let i = 0; i < dataPoints.length; i++) {
    const dpTs = new Date((dataPoints[i].time || '').replace(' ', 'T')).getTime()
    if (Number.isFinite(dpTs) && Math.floor(dpTs / divisor) === anomalyBucket) return i
  }
  return -1
}

function buildAnomalyMarkPoints(anomalyPoints, showAnomalies, dataPoints, granularity, formatX) {
  if (!showAnomalies || !anomalyPoints || anomalyPoints.length === 0) return []
  const result = []
  for (const p of anomalyPoints) {
    const idx = findBucketIndex(dataPoints, p.time, granularity)
    if (idx < 0) continue  // 异常点不在当前数据集（如时间范围之外），跳过不报错
    // coord[0] 必须用 dataPoints[idx] 经 formatX 处理后的字符串，与 xAxis.data 严格一致
    result.push({
      coord   : [formatX(dataPoints[idx].time), p.actual],
      value   : p.deviation.toFixed(1) + 'σ',
      itemStyle: { color: p.severity === 'high' ? '#ff5252' : '#ffab00' },
      label   : { show: true, fontSize: 9 },
    })
  }
  return result
}

// lineOption 内部：
const formatX = (t) => formatAxisTimeAdaptive(t, granularity, rangeDays)
const times = dataPoints.map(c => formatX(c.time))   // xAxis.data 用 formatX
markPoint: {
  data      : buildAnomalyMarkPoints(anomalyPoints, showAnomalies, dataPoints, granularity, formatX),
  symbolSize: 30,
}
```

**为什么这样实现稳健**：
- coord[0] 永远是 `formatX(dataPoints[idx].time)`，**与 xAxis.data[idx] 是同一字面值**（同一个 formatX 调用）
- 即使 HOUR 粒度 rangeDays ≤ 1.5 时 formatX 返回 "HH:mm"，anomaly 也对齐到 bucket 起点的 "HH:00"，coord 与 xAxis.data 严格匹配
- 不依赖 ECharts 字符串模糊匹配，直接字面值相等
- 找不到 bucket（异常时间在数据集外）时跳过，不报错

**性能**：`findBucketIndex` 是 O(n)，N 个 anomaly × n 个 dataPoints = O(n²)。HOUR 粒度 dataPoints 通常 ≤ 168（7 日 × 24 小时），DAY ≤ 30，n² ≤ 28224，可接受。**如果后续支持 WEEK/MONTH 大范围（>1000 个 dataPoints），可改用 Map<bucketKey, idx> 优化到 O(n+m)**。

**测试要求**：
- `lineOption markPoint coord matches xAxis.data format`（NC1：coord 字面值 = xAxis.data 某项）
- `lineOption markPoint HOUR anomaly matches bucket by hour`（NC4：HOUR 粒度 anomaly 落在 bucket 中间能匹配）
- `lineOption markPoint DAY anomaly matches bucket by day`
- `lineOption markPoint skips anomalies outside dataPoints time range`
- `lineOption markPoint empty when showAnomalies=false`
- `lineOption markPoint empty when anomalyPoints null/[]`
- `analysis.js maps backend timestamp to frontend time`（N2 关键测试）
- **不写 `markPoint uses dataIndex` 测试**（ECharts 不支持）

**intradayOption 与 markPoint 的关系**（I4 修复）：
- intradayOption 内部 series 是 `series[0]`（仅监测值，没有 max/mean/min）
- 阶段 2 明确"INTRADAY 粒度不调用 detectAnomalies"，所以 intradayOption 收到的 anomalyPoints **恒为空数组**
- intradayOption 同步扩展签名为 `intradayOption(points, unit, displayCount, detectIntervalSeconds, compact, thresholds, anomalyPoints, showThresholds, showAnomalies)` — 后 5 个参数默认值（null / [] / false）让 TrendChart.vue buildOption 统一传参，避免条件分支
- `TrendChart.vue:102-104` buildOption 调用必须同步改：`intradayOption(props.intradayPoints, props.unit, DEFAULT_PERIOD_WINDOW, props.detectIntervalSeconds, props.compact, props.thresholds, props.anomalyPoints, props.showThresholds, props.showAnomalies)`
- 运行时 `buildAnomalyMarkPoints([], ...)` 返回空数组，不渲染
- 测试用例无需覆盖"intradayOption markPoint"，改为"lineOption markPoint from anomalies"

### B3 工具栏开关
```vue
<el-switch v-model="showThresholds" active-text="阈值" inactive-text="" size="small" />
<el-switch v-model="showAnomalies" active-text="异常" inactive-text="" size="small" />
```
- `showThresholds` / `showAnomalies` 默认 `true`
- 切换时 `TrendChart` watch 触发重渲染 — **但需要在阶段 2 同步扩展 TrendChart.vue 行 140 的 watch deps 数组**（第七轮 I1 修复）：加入 `props.thresholds, props.anomalyPoints, props.showThresholds, props.showAnomalies`。当前 watch deps 只含 `dataPoints/intradayPoints/granularity/avg5/avg10/avg20/unit/compact/loading` 9 项，不感知新 props 变化，必须扩展否则开关失效。
- **warnCond 文本展示（若有）必须用 Vue `{{ }}` 默认 HTML 转义**，禁止 `v-html`（第五轮 M6 修复，XSS 防御）

### C1 模式切换（`MonitorTrend.vue`）— 两个独立 el-select 避免 :multiple 动态切换问题
```vue
<el-radio-group v-model="mode" size="small" @change="onModeChange">
  <el-radio-button label="single">单图</el-radio-button>
  <el-radio-button label="compare">对比</el-radio-button>
</el-radio-group>

<!-- 单图模式：number v-model -->
<el-select
  v-if="mode === 'single'"
  v-model="monitorId"
  placeholder="选择监测点"
  filterable
  size="small"
  style="width: 200px"
  @change="onMonitorChange"
>
  <el-option v-for="m in monitorList" :key="m.id" :label="m.caption || m.name" :value="m.id" />
</el-select>

<!-- 对比模式：number[] v-model，max=4 -->
<el-select
  v-if="mode === 'compare'"
  v-model="monitorIds"
  multiple
  :multiple-limit="4"
  placeholder="选择监测点（最多 4 个）"
  filterable
  size="small"
  style="width: 280px"
  @change="onMonitorListChange"
>
  <el-option v-for="m in monitorList" :key="m.id" :label="m.caption || m.name" :value="m.id" />
</el-select>
```

### C2 TrendStandalone.vue 改造伪代码（NI3/I3 修复：实施风险最大的缺口）

URL 解析优先级与模式强制策略：

```js
// frontend/src/views/statistics/pages/TrendStandalone.vue
const route = useRoute()

// URL 解析：monitorIds 优先；缺则回退 monitorId（单 ID 向后兼容）
function parseMonitorIdsFromQuery() {
  const rawIds = String(route.query.monitorIds || '')
  if (rawIds) {
    const ids = rawIds.split(',').map(s => Number(s.trim())).filter(Number.isFinite)
    if (ids.length > 0) return ids
  }
  // 单 ID 向后兼容（旧 URL ?monitorId=X）
  const singleId = Number(route.query.monitorId)
  if (Number.isFinite(singleId) && singleId > 0) return [singleId]
  return []
}

const monitorIds   = ref(parseMonitorIdsFromQuery())
const monitorKind  = ref(route.query.monitorKind || 'PROBE')
// 模式由 URL 决定，独立窗口不允许切换（避免 URL 与状态不一致）
const mode         = ref(monitorIds.value.length > 1 ? 'compare' : 'single')

if (monitorIds.value.length === 0) {
  window.close()  // URL 无效
}

// 模板：
// <TrendChart v-if="mode === 'single'" :monitor-id="monitorIds[0]" ... />
// <ComparisonChart v-else :monitor-ids="monitorIds" ... />
// 独立窗口不显示"模式切换"radio（用户应在主页面切换，独立窗口专注查看）

// unsubscribe 改为批量
onUnmounted(() => {
  if (monitorIds.value.length > 0) {
    wsStore.unsubscribe(monitorIds.value)
  }
})
```

**关键约束**：
- 独立窗口不暴露模式切换 UI（URL 是入口，状态固化）
- 单 ID URL `?monitorId=X` 自动包装为 `[X]` 并强制 `mode='single'`（向后兼容）
- 多 ID URL `?monitorIds=1,2,3` 强制 `mode='compare'`
- 解析失败（无数值）→ `window.close()`，行为与现有 `TrendStandalone.vue:57-60` 一致
- **ComparisonChart 设计为纯渲染组件**（接收 `series` prop），数据加载由父组件（MonitorTrend.vue / TrendStandalone.vue）负责；对比模式数据加载逻辑提取到 composable `useTrendSeriesLoader(monitorIds, kind, range, gran)`，**两个父组件共用**，避免 DRY 违反（第五轮 I2 修复）；**返回值结构**（第六轮 NM3）：`{ series: Ref<TrendSeries[]>, loading: Ref<boolean>, failedCount: Ref<number> }`，其中 `TrendSeries = { id, caption, unit, color, dataPoints, avg5/10/20, thresholds, anomalies }`
- `Number(s.trim())` 解析时 filter 加 `> 0` 守卫：`.filter(n => Number.isFinite(n) && n > 0)` 防止 monitorId=0 漏过（第五轮 M1 修复）

```js
// 切换模式时强制清空选中，避免类型不一致（C1 修复）
// 不接收 newMode 参数：无论切到 single 还是 compare，都清空两个 ref（避免 ESLint unused-param 警告）
function onModeChange() {
  monitorId.value   = null
  monitorIds.value  = []
  clearChartData()   // 复用现有 clearChartData（MonitorTrend.vue:214）
}

// 下游所有使用 monitorId 的逻辑必须做 Array.isArray 守卫
function onMonitorChange() { /* single 模式，monitorId 是 number */ }
function onMonitorListChange() { /* compare 模式，monitorIds 是 number[] */ }
```

**关键约束**：
- 用 `v-if` 渲染两个独立 `el-select`，**不要**用 `:multiple="mode === 'compare'"` 动态切换（Element Plus 在 multiple 由 false→true 切换时，已绑定的 v-model 类型不匹配会触发 props 警告，且 selected label 缓存可能未清空）
- `monitorId`（number）和 `monitorIds`（number[]）是**两个独立 ref**，避免类型混用
- 切换模式时必须清空，**否则若不清空**，切回单图时 `monitorId` 可能残留 number[] 破坏 `loadMetadata(monitorId.value)`（说明为何要清空）
- 单图 ↔ 对比切换不会保留选中监测器（避免歧义）

**对比模式与单图模式的行为差异**：
- 单图模式：用 `TrendChart.vue` 渲染；阈值/异常开关对当前监测器生效
- 对比模式：用 `ComparisonChart.vue` 渲染；阈值/异常开关对**所有选中监测器同时生效**（每个监测器各自的 min/max/anomaly 都叠加），暂不支持单个监测器单独控制（避免 UI 复杂度）
- 对比模式当前仅支持单一 `monitorKind`（PROBE 或 CONTROL），不支持混合 kind（前端下拉框 list 仍按 kind 过滤）
- 对比模式下钻点击（`data-point-click`）暂时禁用（避免歧义——下钻到哪个监测器？）；可在后续迭代加监测器选择器

### C3 分组策略（`ComparisonChart.vue`）
```js
const groupedByUnit = computed(() => {
  const groups = new Map()
  for (const s of props.series) {
    const key = s.unit || '(无单位)'
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(s)
  }
  return groups
})

const layoutMode = computed(() => {
  const n = groupedByUnit.value.size
  if (n <= 1) return 'single-axis'
  if (n === 2) return 'dual-axis'
  return 'small-multiples'
})
```

### C5 颜色调色板（`composables/palette.js`）
```js
export const COMPARISON_PALETTE = [
  '#00d4ff', '#ff5252', '#69f0ae', '#ffab00',
  '#ce93d8', '#7fff7f', '#ff79c6', '#f1fa8c',
]
export function pickColor(index) {
  return COMPARISON_PALETTE[index % COMPARISON_PALETTE.length]
}
```

---

## 测试策略

### 后端单元测试
- `TrendServiceTest.java`：
  - `getMetadataReturnsThresholds()` — 验证 min/max/warn_cond 字段从 t_probe / t_control 读取（用 `createProbeWithThresholds` helper）
  - `getMetadataProbeNotFoundReturnsEmptyMap()` — 不存在的 monitorId 返回空 Map
  - `getMetadataStillResolvesDetectIntervalAfterExtension()` — 回归：扩展 SQL 后 detectInterval 仍正确（C2 防御）
  - `getMetadataStillResolvesDataTypeAfterExtension()` — 回归：扩展 SQL 后 dataType 仍正确（C2 防御）
  - **NULL 容错合并到 `getMetadataReturnsThresholds`**：同一测试同时覆盖"有值字段读出值，无值字段（min/max/warn_cond 为 NULL）读出 null"（合并 N9 避免无意义测试）
  - **扩展测试 helper SQL**：`INSERT_PROBE` / `INSERT_CONTROL` 加 `min_value/max_value/warn_cond` 列；新增 `createProbeWithThresholds(dataType, interval, min, max, warnCond)` helper（**NI1 修复：前两个参数与现有 `createProbe(dataType, interval)` 兼容**）
- 已有 69 个测试需通过（回归）

### 前端单元测试（Vitest + Vue Test Utils）
- `config/monitor.test.js`（新建）：
  - `formatMonitorValue with unit` (温度 → 1 位、百分比 → 0 位、未知 unit → 默认 3 位)
  - `formatMonitorValue integer unchanged` (整数不补小数)
  - `formatMonitorValue null returns dash` (null/undefined 返回 '--')
  - `formatAxisTimeAdaptive` 各 granularity × rangeDays 组合（含跨年/跨日边界）
  - `computeRangeDays valid/invalid input` (含 null / 非法日期)
- `composables/trendOptions.test.js`（新建）：
  - `lineOption tooltip has unit in header only`（series 行不重复显示 unit）
  - `lineOption tooltip uses unit precision for values`（I1：精度按 unit）
  - `lineOption markLine from thresholds`
  - `lineOption markLine empty when showThresholds=false`
  - `lineOption markLine empty when thresholds null`（空 metadata 容错）
  - `lineOption markPoint from anomalies`
  - `lineOption markPoint coord matches xAxis.data format`（I5 关键测试：coord 字面值严格等于 xAxis.data 某项）
  - `lineOption markPoint empty when showAnomalies=false`
  - `lineOption warnCond escaped in tooltip`（I6：HTML 字符转义）
  - `lineOption warnCond with html chars`（含 `<` `>` `&&` 字符）
  - `intradayOption accepts anomalyPoints prop but never renders`（API 一致性，运行时空）
- `composables/useChart.test.js`（新建）：
  - `ResizeObserver triggers resize` (mock ResizeObserver)
  - `window resize and ResizeObserver share rAF debounce`（I3：去抖验证）
  - `ResizeObserver undefined falls back to window resize`（兼容性）
- `stores/websocket.test.js`（新建）：
  - `probeValues LRU evicts least-recently-accessed beyond MAX`（C3：last-access 而非插入顺序）
  - `probeValues hot key not evicted under continuous writes`（C3：热点 key 不被淘汰）
  - `reconnect re-subscribes monitored ids on open`
  - `evicted key receives next push normally`（被淘汰的 key 下次推送能重建）
- `pages/MonitorTrend.test.js`（新建）：
  - `rapid granularity switch drops stale response` (mock API + flush-promises)
  - `compare mode loads N series in parallel with allSettled`（I7：部分失败容错）
  - `compare mode partial failure shows degradation message`
  - `mode switch clears monitorId and monitorIds`（C1：切换模式清空）
  - `single mode select accepts number v-model`
  - `compare mode select accepts number[] v-model with limit 4`
- `components/TrendChart.test.js`（新建）：
  - `summary bar uses unit precision`（I1：summary bar 与曲线 tooltip 精度一致）
  - `thresholds null renders no markLine`
  - `anomalyPoints empty renders no markPoint`
  - `warnCond text escaped in side panel`
  - `thresholds toggle triggers re-render`（第七轮 I1 关键测试：mock watch + 切换 showThresholds prop + 断言 setOption 被再次调用）
  - `anomalies toggle triggers re-render`（同上，针对 showAnomalies）

### 集成测试
- `TrendControllerTest.java`：`/metadata` 端点返回字段验证
- 全量回归 `./mvnw clean test -o`（项目开发约定强制要求 clean）

### 前端组件测试覆盖目标
- TrendChart.vue：buildOption / formatMA / registerClickHandler 覆盖
- 当前 `statistics/__tests__/index.test.js` 仅 stub 测试，本次新增对 TrendChart 的真实单测

### 手动验证（每阶段）
- 启动 `./mvnw spring-boot:run -pl extensions/systar-server -Dspring-boot.run.profiles=dev`
- 启动前端 `cd frontend && npm run dev`
- **阶段 1 验证**：
  - 单图：tooltip 仅首行 unit；跨年 axis 显示年份；不同 unit 精度不同；窗口缩放图表跟随；快速切粒度无闪烁
  - WS：断网再连，INTRADAY 仍持续刷新
- **阶段 2 验证**：
  - 阈值开关：min/max 水平线显示，关闭后隐藏
  - 异常开关：异常点圆点显示，悬停看 σ 倍数
  - metadata 返回 min/max/warn_cond
- **阶段 3 验证**：
  - 模式切换无报错；选 4 个同单位监测器，单 Y 轴叠加，4 种颜色
  - 选 2 种单位（如 °C 和 %），双 Y 轴
  - 选 3+ 种单位，小多图布局
  - 独立窗口 `/trend-standalone?monitorIds=1,2,3` 工作

---

## 三阶段实施分解

### 阶段 1：可读性 + 配套健壮性（D + A）
**预计**：~2 天

1. 写测试：`config/monitor.test.js`、`composables/trendOptions.test.js` (基础 tooltip/precision)、`composables/useChart.test.js`、`stores/websocket.test.js`
2. 实现 `UNIT_PRECISION_MAP` + `formatMonitorValue(val, unit)` + `computeRangeDays`
3. 实现 `formatAxisTimeAdaptive` **替换** `formatLineAxisTime`
4. 改造 `trendOptions.js` Tooltip 单位去重（含 `formatMonitorValue(val, unit)` 传 unit）+ 时间轴 adaptive（xAxis.data 与未来 markPoint.coord 用同一函数）+ dataZoom 提示
5. 改造 `TrendChart.vue` summary bar 4 处 `formatMonitorValue` 显式传 `props.unit`；**改 `formatMA(arr, unit)` 签名**，3 处模板调用传 `props.unit`（NI2 修复）；**onUnmounted 改用 useChart 返回的 dispose**（NI7 修复）
6. 改造 `useChart.js` 增加 ResizeObserver + rAF 去抖
7. 改造 `MonitorTrend.vue` loadToken 竞态保护（**组件作用域内**声明）
8. 改造 `websocket.js` probeValues LRU（**write + subscribe 两路 touch**，NC2 修复） + 重连重订阅 + `MAX_PROBE_VALUES = 200` 常量
9. 全量回归：`./mvnw clean test -o` + `cd frontend && npm run test`
10. 提交（commit message: `feat: improve trend chart readability and robustness`）

### 阶段 2：智能标注（B）
**预计**：~2 天

1. 写测试：`TrendServiceTest.getMetadataReturnsThresholds` + 3 个回归 + null 容错（合并到主测试）；`composables/trendOptions.test.js` 补充 markLine/markPoint/escapeHtml 测试；`api/iot/analysis.test.js` 测试字段映射
2. 后端：扩展 `TrendService.queryProbeById` / `queryControlById` SQL + RowMapper map.put（按列名取）
3. 后端：`getMetadata` Map 增加 minValue/maxValue/warnCond
4. 后端：扩展测试 helper SQL（`INSERT_PROBE` / `INSERT_CONTROL` 加 `min_value/max_value/warn_cond` 列），新增 `createProbeWithThresholds(dataType, interval, min, max, warnCond)` helper（参数顺序与 `createProbe` 兼容）
5. 后端：回归测试
6. 前端：新建 `api/iot/analysis.js`（字段映射 `timestamp→time, actualValue→actual, expectedValue→expected`；响应解构 `Array.isArray(res) ? res : (res?.data || [])`）
7. 前端：`TrendChart.vue` 增加 thresholds/anomalyPoints/showThresholds/showAnomalies props；warnCond 走 escapeHtml
8. 前端：`trendOptions.js` 增加 markLine + markPoint；**markPoint 用 bucket 匹配 + coord 字面值**（NC1/NC4 修复，不用 dataIndex）；markLine label 走 formatMonitorValue（N5 修复）
9. 前端：`MonitorTrend.vue` 增加开关 + 异常 API 调用
   - 异常点查询粒度策略：**仅 HOUR/DAY 粒度调用 detectAnomalies**
   - INTRADAY（点密集，markPoint 互相覆盖）、WEEK/MONTH（聚合周期长，单点异常已无意义）不调用
   - 时间范围用当前 dateRange；无 dateRange 时跳过
   - 时间参数格式：ISO 8601（`YYYY-MM-DDTHH:mm:ss`），匹配后端 `@DateTimeFormat(iso=DATE_TIME)`
10. 全量回归
11. 提交（commit message: `feat: add threshold lines and anomaly markers to trend chart`）

### 阶段 3：对比能力（C）
**预计**：~3 天

1. 写测试：`composables/palette.test.js`、`composables/trendOptions.test.js` (multiSeries)、`composables/smallMultiples.test.js`、`pages/MonitorTrend.test.js` (compare mode + allSettled + partial failure)、`components/ComparisonChart.test.js`
2. 新建 `composables/palette.js`
3. 扩展 `trendOptions.js` 增加 `multiSeriesOption`
4. 新建 `composables/smallMultiples.js`
5. 新建 `components/ComparisonChart.vue`（按 unit 分组 + 降级占位）
6. 改造 `MonitorTrend.vue` 模式切换（**两个独立 el-select + 切换清空**）+ 多选 + 并行加载（`Promise.allSettled`）
7. 改造 `TrendStandalone.vue` 支持 `monitorIds=1,2,3`（`Array.isArray` 守卫，单 ID 向后兼容）
8. 全量回归 + 浏览器手动验证（覆盖 4 个同单位、2 种单位、3+ 种单位场景）
9. 提交（commit message: `feat: add multi-monitor comparison mode to trend chart`）

---

## 关键文件路径速查

**前端核心**：
- `frontend/src/views/statistics/components/TrendChart.vue`
- `frontend/src/views/statistics/composables/trendOptions.js`
- `frontend/src/views/statistics/composables/useChart.js`
- `frontend/src/views/statistics/pages/MonitorTrend.vue`
- `frontend/src/views/statistics/pages/TrendStandalone.vue`
- `frontend/src/config/monitor.js`
- `frontend/src/stores/websocket.js`
- `frontend/src/api/iot/trend.js`

**前端新建**：
- `frontend/src/api/iot/analysis.js` (阶段 2)
- `frontend/src/views/statistics/composables/palette.js` (阶段 3)
- `frontend/src/views/statistics/composables/smallMultiples.js` (阶段 3)
- `frontend/src/views/statistics/components/ComparisonChart.vue` (阶段 3)

**后端**：
- `extensions/systar-ops/src/main/java/com/systar/ops/statistics/service/TrendService.java` (阶段 2 扩展 getMetadata)
- `extensions/systar-ops/src/test/java/com/systar/ops/statistics/service/TrendServiceTest.java`

**已存在可复用**：
- `AnalysisService.detectAnomalies()` — `extensions/systar-ops/.../service/AnalysisService.java:50`
- `AnalysisController` `/api/ops/analysis/anomaly/{id}` — `extensions/systar-ops/.../controller/AnalysisController.java:29`
- ECharts markLine / markPoint 原生支持

---

## 验证清单（每阶段必须）

### 阶段 1 验证
- [ ] `./mvnw clean test -o` 全绿（含新增测试 + 69 个 TrendService 回归）
- [ ] `cd frontend && npm run test` 全绿
- [ ] `cd frontend && npm run lint` 无新增 warning
- [ ] 启动 dev 服务器，访问 `/statistics`：
  - tooltip 仅首行显示 unit
  - 时间轴跨年显示 `YY-MM-DD`
  - 温度监测器显示 1 位小数，百分比 0 位
  - 侧栏折叠时图表自动 resize
  - 快速切换粒度按钮，无数据闪烁

### 阶段 2 验证
- [ ] 后端测试覆盖 `getMetadata` 字段（minValue/maxValue/warnCond 含 NULL 容错）
- [ ] 前端测试覆盖 markLine/markPoint 输出 + coord 字面值与 xAxis.data 严格匹配
- [ ] 前端测试覆盖 HOUR 粒度 anomaly 落在 bucket 中间能匹配（NC4 关键场景）
- [ ] 前端测试覆盖 `api/iot/analysis.js` 字段映射（N2）
- [ ] `/api/ops/trend/metadata?monitorId=X&monitorKind=PROBE` 返回包含 minValue/maxValue
- [ ] dev 服务器：阈值开关切换，水平线显隐正确
- [ ] dev 服务器：异常点圆点显示，颜色按 severity 区分
- [ ] dev 服务器：HOUR 粒度异常点（anomaly 落在 bucket 中间）能正确渲染在对应 bucket 位置（NC1/NC4 验证）
- [ ] 无阈值/无异常的监测器，无 markLine/markPoint 渲染

### 阶段 3 验证
- [ ] 前端测试覆盖 multiSeriesOption 和 smallMultiplesOption
- [ ] dev 服务器：单图 ↔ 对比切换无报错
- [ ] 4 个同单位监测器叠加，4 种颜色清晰可分
- [ ] 2 种单位（如 °C、%）双 Y 轴
- [ ] 3+ 种单位小多图布局
- [ ] **无单位的监测器在对比模式下归入"(无单位)"分组**（NI6 验证）
- [ ] 独立窗口 `/trend-standalone?monitorIds=1,2,3` 正常加载
- [ ] **独立窗口 `/trend-standalone?monitorId=1`（旧 URL）仍正常加载单图**（N11 向后兼容）
- [ ] 对比模式下，每个监测器各自的阈值线/异常点正常叠加（N10 验证）
- [ ] 对比模式下，1 个监测器加载失败，其他正常显示，顶部提示"1 个加载失败"

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| `warn_cond` 表达式复杂无法解析画线 | 仅解析 `min_value/max_value` 数值；`warn_cond` 作为 metadata 文本展示（走 escapeHtml）但不画线 |
| `warn_cond` 含 `<`/`>`/`&&` 等字符破坏 HTML | 展示前必须走 `escapeHtml`（`trendOptions.js:18`）；测试覆盖 |
| 多监测器并发请求过慢 | 限制 max 4 个监测器；前端并行调用；可后续加批量端点 |
| 多监测器部分加载失败 | 用 `Promise.allSettled` 而非 `Promise.all`；失败项显示降级占位；顶部提示"N 个加载失败" |
| ResizeObserver 在老浏览器不支持 | 加 `typeof ResizeObserver !== 'undefined'` 守卫，回退到 window.resize |
| ResizeObserver 与 window.resize 双触发 | 共用 rAF 去抖（一帧内多次只触发一次 resize） |
| 异常点过多压垮视觉（如 5000+ 采样周期） | markPoint 仅在 HOUR/DAY 粒度叠加；INTRADAY 不叠加（点太密）；WEEK/MONTH 不叠加（无意义） |
| 多 Y 轴刻度冲突 | ECharts 自动对齐左右轴刻度，必要时用 `yAxis.splitNumber` 控制 |
| 后端 metadata 字段命名歧义 | 保持 `minValue/maxValue` (camelCase) 与现有 `detectInterval` 一致 |
| `queryProbeById` 共享 RowMapper 影响 `resolveDetectIntervalSeconds`/`resolveDataType` | RowMapper 必须按列名取值；阶段 2 补两个回归测试 |
| `probeValues` LRU 淘汰热点 key | **write + subscribe 两路 touch**（NC2 修复：read 不 touch，避免 watch getter 噪声）；测试覆盖"订阅但安静 key 不被淘汰" |
| `probeValues` 下游消费者（MonitorTrend / TrendStandalone / operations/index）受 LRU 影响 | 保持现有 `wsStore.probeValues[id]` 直接访问；subscribe 时已 touch 保护；现有 `if (!msg) return` 防御保留 |
| `el-select` 动态 `:multiple` 切换引发 props 警告 | 用 `v-if` 渲染两个独立 select；切换模式时清空 monitorId/monitorIds |
| `Promise.all` 任一失败导致整图挂掉 | 用 `Promise.allSettled`，detectAnomalies 内部 `catch(() => [])` 容忍 |
| **AnomalyPoint 后端字段 `timestamp/actualValue/expectedValue` 与前端 `time/actual/expected` 错配** | **`api/iot/analysis.js` 内部做字段映射**（N2 修复）；测试覆盖映射逻辑 |
| **`systarApi` 拦截器已返回 `res.data`，analysis.js 不能再 `.data`** | **`Array.isArray(res) ? res : (res?.data || [])`**（NC3 修复） |
| **ECharts 5 markPoint 不支持 `dataIndex`**（N6 修复方案错误） | **改回 coord + bucket 匹配**（NC1 修复）；coord[0] 用 `formatX(dataPoints[idx].time)` 与 xAxis.data 严格字面值 |
| **HOUR 粒度 anomaly.timestamp 落在 bucket 中间，与 bucket_start 不相等** | **`findBucketIndex` 按 HOUR/DAY 截断匹配**（NC4 修复），不依赖时间戳严格相等 |
| **markLine label 直接拼接数字导致精度与曲线不一致** | **label formatter 走 `formatMonitorValue(thresholds.max, unit)`**（N5 修复） |
| **markLine formatter 用 `{c}` 占位符会被原始数字替换绕过精度** | 用 JS 模板字符串静态拼接（NI3 修复） |
| **formatMA 内部 formatMonitorValue 漏传 unit** | **改 `formatMA(arr, unit)` 签名**，3 处调用都传 `props.unit`（NI2 修复，归阶段 1） |
| **TrendServiceTest helper SQL 不含阈值列** | **扩展 INSERT_PROBE/INSERT_CONTROL SQL**，新增 `createProbeWithThresholds(dataType, interval, min, max, warnCond)` helper（参数顺序兼容 N4/NI1） |
| **TrendStandalone 旧 URL `?monitorId=X` 失效** | URL 解析 `Array.isArray` 守卫，单 ID 走原逻辑（N11 验证清单覆盖） |
| **rangeDays 阈值边界抖动** | 用 1.5 / 365.5 而非 1 / 365，避免 1.0 天在分支间抖动（N8） |
| 单元测试 ECharts markLine/markPoint 难以断言 | 直接断言 `option.series[i].markLine.data` / `markPoint.data` 数组结构（不渲染，只看 option 对象） |
| **MAX_PROBE_VALUES 后续调大导致 evictIfNeeded 排序成本** | 200 keys 性能足够（~1us/次）；常量注释明示"调到 1000+ 时需改用 LinkedList"（N7） |
| **`_lastAccess` 用 `new Map()` 是否需要 reactive** | 故意不用 reactive（NI5：纯 LRU 内部状态，无消费方需要响应） |
| **useChart ResizeObserver 未 disconnect 导致内存泄漏** | TrendChart.vue onUnmounted 必须改用 useChart 返回的 dispose（NI7 修复） |
| ~~trendOptions.js 顶部新增 formatMonitorValue import 是冗余指令~~ | ~~trendOptions.js:2 已 import~~（第七轮 M2：此项为自指残留，已删除；NC5 修复在主体行 494-496 已说明） |

---

## 备注

- 三阶段均独立提交（`3be3a2d7` 阶段 1、`ed1de0ed` 阶段 2、`90563cae` 阶段 3），每个 commit 可独立验证和回退
- 阶段 1 提交前运行了 `./mvnw clean test -o` 全量回归（项目开发约定强制要求 clean，避免 surefire forkCount=0 跨模块污染）
- 阶段间无强依赖（阶段 2/3 各自包含所需测试），实际按顺序执行
- `useTrendDrillDown.js` 本次未改，`getDrillDownRange` 返回的 time 字符串作为 `formatAxisTimeAdaptive` 输入兼容

## 实施完成状态（2026-06-21）

- **阶段 1**（可读性 + 配套健壮性）：✅ 提交 `3be3a2d7`
  - 55 个 monitor.js 测试 + 7 个 useChart.js 测试 + 22 个 trendOptions.js 测试（阶段 1 部分）+ 20 个 websocket.js 测试（扩展）
- **阶段 2**（智能标注：阈值线 + 异常点）：✅ 提交 `ed1de0ed`
  - 5 个后端 TrendServiceTest 测试（含 RowMapper 共享回归）+ 6 个 analysis.js 字段映射测试 + 9 个 trendOptions markLine/markPoint 测试 + 4 个 TrendChart.test.js 测试
- **阶段 3**（对比能力：多监测器）：✅ 提交 `90563cae`
  - 15 个 palette.js 测试 + 11 个 multiSeriesOption 测试 + 11 个 smallMultiples.js 测试 + 8 个 useTrendSeriesLoader.js 测试
- **测试总计**：前端 482/482 + 后端 systar-ops 240/240 + 后端 simulator 45/45 全部通过

## 7 轮审核-完善循环

plan 经 7 轮审核-修复-再审核循环收敛，共修复 11 Critical + 26 Important + 36 Minor = **73 个问题**。Critical 收敛轨迹：3 → 2 → 5 → 0 → 1 → 0 → 0 → 0。关键修复亮点：
- C3 WS LRU write+subscribe 两路 touch（保证安静但被查看的 key 不被淘汰）
- N2 AnomalyPoint 字段映射（后端 `timestamp/actualValue/expectedValue` → 前端 `time/actual/expected`）
- NC1 ECharts 5 markPoint 不支持 dataIndex（改用 coord + bucket 匹配，coord[0] 严格等于 xAxis.data 字面值）
- NC4 findBucketIndex 用本地时区（避免 UTC 边界跳日）
- NC2 readProbeValue 不 touch（避免 watch getter 重算噪声）
- NI2 formatMA 改签名归阶段 1（避免阶段 1 实施后精度不一致窗口）
- 第七轮 I1 watch deps 同步扩展（开关切换触发重渲染）
