# Systar 系统改进建议

> 日期：2026-06-01 | 基于 2025-2026 主流 IoT/工业监控平台调研
> 修订：2026-06-01 | 经代码库验证后修正

## 调研范围

对比平台：ThingsBoard 4.0-4.3、Ignition 8.3、Zabbix 7.x/8.0、EMQX 5.x、Prometheus 3.0 + Grafana 12、Apache IoTDB、TDengine 3.3.x、Home Assistant 2025

---

## 一、高优先级改进（竞争力差距）

### 1. SCADA/HMI 可视化层

**现状**：Systar 有科技风 Dashboard（ECharts 环形图 + 统计卡），但缺少工业流程图可视化。前端仅有 `getDashboardStats()` API 和 ECharts 图表组件，无 SVG/Canvas 编辑器。

**行业趋势**：ThingsBoard 4.0 引入 60+ SCADA SVG 符号库（管道、泵、阀门、仪表），Ignition 8.3 新增低代码 HMI 绘图工具。工业用户期望拖拽式流程图 + 实时数据绑定。

**建议**：
- 添加基于 SVG 的 SCADA 符号库（60-100 个工业符号，按行业分类：HVAC、制造、能源）
- 支持自定义 SVG 导入，数据点驱动动画（颜色、旋转、液位填充）
- Vue3 画布编辑器（拖拽布局 + 属性面板绑定 Probe/Control 数据点）
- 预置行业模板（冷站监控、配电系统、空调系统）

**实现路径**：
1. 定义 SVG 符号规范（数据绑定属性 `data-bind-monitor-id`、动画规则 `data-animate-type`）
2. 构建符号库 + 画布编辑器组件（Vue3 + SVG DOM 操作，考虑 Konva.js 或 Fabric.js）
3. 后端添加 `t_dashboard_layout` 表存储 JSON schema（含 SVG 节点、绑定关系、动画配置）
4. 实时数据 WebSocket 推送到 SVG 动画（复用现有 `systar-websocket` 模块）

**验收标准**：
- 可拖拽放置 SCADA 符号到画布
- 符号属性面板可绑定 Probe/Control 数据点
- 数据变化时 SVG 动画实时响应（颜色变化、旋转、液位填充）
- 画布布局保存为 JSON schema 并可加载恢复
- 至少提供 HVAC 行业模板（冷站监控）
- 全量回归测试通过

**工期**：4-6 周（前端为主，后端改动较小）

### 2. 计算/派生字段引擎

**现状**：Systar 的 `Monitor` 已有 `ExpressionEvaluator`（SpEL 实现）用于 `applyTransform()` 和 `evaluateWarnCondition()`，但表达式只能引用自身 `#value`，无法跨 Probe 引用其他 Probe 的实时值。`ResultDispatcher` 的 dispatch 流程是单 Probe 独立处理，无跨 Probe 计算链。

**行业趋势**：ThingsBoard 4.0 的 Calculated Fields、Ignition 的 Expression Tags 都支持无代码定义计算指标。运维工程师需要自行配置 KPI，不依赖开发人员。

**建议**：
- 添加"虚拟探针"（Virtual Probe）概念：基于表达式计算派生值
- 扩展 `ExpressionEvaluator` 上下文：支持引用其他 Probe 的实时值（如 `#probe[101].value / #probe[102].value * 100`）
- 计算频率：可配置（每次依赖 Probe 更新时触发，或定时周期计算）
- 计算结果与普通 Probe 一样参与告警、联动、统计

**实现路径**：
1. 定义 `VirtualProbe` 类（继承 `Probe`，持有表达式和依赖 Probe ID 列表）
2. 扩展 `SpelExpressionEvaluator`：添加 `probeValues` 变量到评估上下文（`Map<Integer, Object>`）
3. 在 `ResultDispatcher.dispatch()` 后添加虚拟探针计算链：当 dispatch 的 Probe 是某 VirtualProbe 的依赖时，触发 VirtualProbe 重新计算
4. 数据库：`t_probe` 表添加 `is_virtual TINYINT DEFAULT 0`、`expression TEXT NULL`、`depends_on VARCHAR(255) NULL`（逗号分隔的 Probe ID 列表）字段。注：`expression` 与现有 `transform`/`warn_cond` 字段不同——`transform` 引用自身 `#value` 做单值变换，`expression` 引用其他 Probe 值做跨 Probe 计算，是 VirtualProbe 的核心语义
5. 前端添加表达式编辑器 + 依赖 Probe 选择器 + 实时预览

**验收标准**：
- VirtualProbe 可引用其他 Probe 的实时值（如 `#probe[101].value / #probe[102].value * 100`）
- 依赖 Probe 更新时自动触发 VirtualProbe 重计算
- 计算结果可参与告警、联动、统计
- 表达式语法错误时给出明确的错误提示
- 全量回归测试通过

**工期**：2-3 周

### 3. 告警关联与疲劳治理

**现状**：Systar 的 `AlarmHandler` 已有基础告警策略（`ONLY_ONCE`/`CONTINUOUS`/`SELECTIVE`），通过 `alarmedSet` 实现同 Probe 告警去重。但缺少跨设备告警关联、根因抑制、升级策略、静默窗口。`t_alarm_message` 表仅有 `state`（1=未处理/2=已处理）和 `recovered` 字段，无关联/抑制/升级相关字段。

**行业趋势**：Zabbix 8.0 的企业告警控制台 + 高级事件关联、Grafana 的 AI 驱动事件响应 + 自适应告警。告警疲劳是运维团队最大痛点。

**建议**：
- **告警关联**：同一设备的多个告警自动聚合为事件组（如"温度过高" + "电流异常" → "设备过热事件"）
- **告警抑制**：根因告警确认后，自动抑制下游衍生告警
- **告警去重**：相同告警在指定时间窗口内不重复触发（已有 `ONLY_ONCE`/`SELECTIVE` 基础，需扩展为可配置时间窗口）
- **升级策略**：告警未处理超过阈值时间，自动升级通知级别（短信 → 电话 → 上级）
- **静默窗口**：维护期间可配置告警静默

**与联动引擎的关系**：现有 `LinkageHandler` 已支持 `CauseType.ALARM` 触发（告警→联动执行），告警关联侧重**事件聚合和抑制**（同组告警合并展示、根因确认后抑制衍生告警），与联动是不同关注点。告警关联完成后，关联事件组可作为新的 `CauseType.CORRELATION_GROUP` 触发联动，实现"一组关联告警→联动"的场景。

**实现路径**：
1. 扩展 `t_alarm_message` 表添加字段：`correlation_group VARCHAR(50) NULL`（关联组ID）、`root_cause_id INT NULL`（根因告警ID）、`silenced_until DATETIME NULL`（静默截止时间）、`escalation_level TINYINT DEFAULT 0`（升级级别）、`device_id INT NULL`（冗余设备ID，加速关联查询）
2. 新增 `t_alarm_correlation_rule` 表：定义关联规则（同设备 + 时间窗口 → 归入同一组）
3. 新增 `t_alarm_escalation_policy` 表：定义升级策略（超时阈值 + 通知方式）
4. 实现 `AlarmCorrelationService`（基于 device_id + 时间窗口关联，归入 correlation_group）
5. 实现 `AlarmSuppressionService`（根因抑制 + 可配置去重窗口 + 静默）
6. 实现 `AlarmEscalationService`（超时升级策略，定时扫描未处理告警）
7. 前端添加告警事件组视图 + 静默管理 + 升级策略配置

**验收标准**：
- 同设备 5 分钟内多个告警自动归入同一事件组
- 根因告警确认后，其关联的衍生告警自动标记为已抑制
- 相同告警在可配置时间窗口内不重复触发
- 告警未处理超过阈值时间，自动升级通知级别
- 维护期间静默窗口生效，静默期结束后恢复
- 全量回归测试通过

**工期**：2-3 周

### 4. MQTT Sparkplug B 支持

**现状**：Systar 的 `MqttService` 使用 Eclipse Paho v3 客户端，支持 topic routing + JSON path 提取。但不支持 Sparkplug B 规范（Protobuf 负载、BIRTH/DEATH/DATA 消息、自动设备发现），也不支持 MQTT 5.0 共享订阅。

**行业趋势**：Sparkplug B 正在成为工业 MQTT 标准——为纯 MQTT 添加状态感知、标准化负载和自动设备发现。OPC UA + MQTT + Sparkplug B 是 2026 年工业通信的共识架构。

**建议**：
- 实现 Sparkplug B 编解码（Protobuf 负载解析 + BIRTH/DEATH/DATA 消息处理）
- 自动设备发现：收到 BIRTH 消息时自动注册设备 + 创建 Probe/Control
- 状态感知：DEATH 消息触发设备离线告警
- MQTT 5.0 共享订阅支持（水平扩展）

**实现路径**：
1. 添加 Sparkplug B 依赖（Eclipse Tahu 或自实现编解码，需评估 Protobuf 依赖对离线部署的影响）
2. 扩展 `MqttService`：添加 Sparkplug B topic 过滤（`spBv1.0/...`）和消息处理分支
3. 实现 `SparkplugBDiscoveryService`：BIRTH → 自动创建 Space/Device/Probe 资产
4. 升级 Paho 客户端到 v5 或切换到 HiveMQ 客户端以支持 MQTT 5.0 共享订阅
5. 前端添加 Sparkplug B 设备自动发现配置页面

**验收标准**：
- BIRTH 消息自动创建 Space/Device/Probe 资产树
- DEATH 消息触发设备离线告警
- DATA 消息正常解析并存储采样数据
- Protobuf 负载正确解码（metrics 数值、属性、时间戳）
- 不影响现有 MQTT 驱动的正常功能
- 全量回归测试通过

**工期**：2-3 周

---

## 二、中优先级改进（现代化与对等）

### 5. 边缘代理架构

**现状**：Systar 是集中式部署，远程站点断网时数据丢失。无边缘代理或离线缓冲代码。

**行业趋势**：边缘云协同是 2026 年普遍期望（IoTDB Edge、ThingsBoard Edge、Ignition Edge）。边缘代理负责本地数据缓冲、离线操作、断网续传。

**建议**：
- Systar Edge Agent：轻量级 Java 进程，部署在远程站点
- 本地数据缓冲（SQLite/嵌入式 H2）+ 断网续传
- 本地告警/联动执行（不依赖云端）
- 边缘-云端配置同步

**实现路径**：
1. 提取 `systar-monitor-core` + `systar-monitor-drivers` 为独立可运行模块
2. 添加嵌入式 H2 本地存储 + 断网续传队列
3. 实现配置同步协议（REST + 增量 diff）
4. 本地告警/联动引擎（复用 `AlarmHandler` + `LinkageHandler`）

**工期**：4-6 周

### 6. OpenTelemetry 集成

**现状**：Systar 数据封闭在自有系统中，无法与 Prometheus/Grafana 生态对接。采样数据存储在 `t_sample_float`/`t_sample_int` 等表中，仅通过自有 API 暴露。

**行业趋势**：Prometheus 3.0 原生支持 OTel，Grafana Alloy 统一收集器。IT 和 OT 监控融合是趋势。

**建议**：
- OTel Metrics 导出：Systar 采样数据可通过 OTLP 推送到 Prometheus/Grafana
- OTel Metrics 摄入：统一 IT 基础设施监控和 OT 工业监控
- Trace 集成：联动执行链路追踪

**实现路径**：
1. 添加 `opentelemetry-sdk` 依赖
2. 实现 `OtelMetricsExporter`：订阅 `MonitorResultEvent`，转换为 OTel Gauge/Counter 推送
3. 实现 `OtelMetricsIngestor`：接收 OTLP HTTP 推送，写入 `t_sample_*` 表
4. 联动执行添加 Trace span（OpenTelemetry API 注解）

**工期**：2-3 周

### 7. AI/ML 异常检测钩子

**现状**：Systar 的 `AnalysisService` 已实现基于移动平均偏差的异常检测（`detectAnomalies()`，使用标准差归一化偏差值，类似 Z-Score 方法）和移动平均趋势预测（`predictTrend()`），但接口不可插拔——所有检测逻辑硬编码在单一 Service 类中，无法扩展外部 ML 模型。

**行业趋势**：ThingsBoard 4.2 的 AI 请求规则节点、IoTDB 的 TSFM 集成。平台不一定要自建 ML，但必须提供集成接口。

**建议**：
- 定义 `AnomalyDetector` 扩展接口（输入：时序数据窗口，输出：异常分数 + 置信度）
- 内置统计检测器（Z-Score、IQR、滑动窗口偏差）— 从 `AnalysisService` 重构提取
- 外部 ML 模型集成接口（HTTP/gRPC 调用 Python 服务或 ONNX Runtime）
- 异常事件自动标记 + 告警

**实现路径**：
1. 定义 `AnomalyDetector` 接口：`AnomalyResult detect(List<DataPoint> window)`
2. 将 `AnalysisService.detectAnomalies()` 重构为 `ZScoreAnomalyDetector implements AnomalyDetector`
3. 添加 `IQRAnomalyDetector` 和 `DeviationAnomalyDetector` 内置实现
4. 实现 `HttpAnomalyDetector`：调用外部 ML 服务（可配置 URL + 请求模板）
5. 实现 `AnomalyDetectorRegistry`：按 monitorId 配置检测器
6. 异常结果触发告警（复用 `AlarmHandler`）

**工期**：1-2 周

### 8. 时序预测入门

**现状**：Systar 的 `AnalysisService.predictTrend()` 已实现移动平均趋势外推，但缺少预测性维护闭环（预测违反阈值时无自动告警/工单）。

**行业趋势**：IoTDB 内置 TSFM 预测，工业平台普遍添加预测性维护。

**建议**：
- 滑动窗口趋势外推（已有基础，需增强置信区间计算）
- 可配置预测区间 + 趋势违反阈值告警
- 设备健康评分（加权多指标评估）— `assessDeviceHealth()` 已有基础
- 后期考虑 TSFM 集成

**实现路径**：
1. 增强 `TrendPrediction`：添加置信区间（基于历史波动率）
2. 添加趋势违反检测：预测值超过阈值时触发告警
3. 健康评分与告警/工单联动：`poor` 级别自动生成维护工单
4. 后期：定义 `TimeSeriesForecaster` 接口，支持 TSFM 模型替换

**工期**：1-2 周

---

## 三、低优先级改进（前瞻性）

### 9. 数字孪生数据模型

**现状**：Roadmap 中有"三维数字孪生"待办，但尚未启动。`t_asset` 表已有统一资产视图（kind + type_id + parent_id 层级），可作为数字孪生数据模型基础。

**行业趋势**：数字孪生从 3D 设计可视化向实时运营智能演进。2D 拓扑 + 实时状态叠加已有价值。

**建议**：
- 先实现 2D 拓扑可视化（设备关系图 + 实时状态叠加，复用 SCADA 画布编辑器）
- 数据模型：每个物理设备可有虚拟对应物 + 实时状态同步（`t_asset.state` 已有）
- 后期再考虑 3D 渲染

**工期**：3-4 周（2D 拓扑部分，与 SCADA 可视化共享画布编辑器）

### 10. 配置即代码 / GitOps

**现状**：Systar 配置通过数据库管理，无法版本控制。`t_alarm_rule`、`t_linkage_rule`、`t_scheduled_task` 等配置表无变更审计。

**行业趋势**：Grafana 12 的 Git Sync、Ignition 8.3 的 GIT 集成。

**建议**：
- 告警规则、仪表盘布局、设备配置导出为 YAML/JSON
- Git 版本控制 + 变更审计
- 环境间配置迁移（开发 → 测试 → 生产）

**工期**：2-3 周

### 11. MCP 服务器接口

**现状**：无 AI 助手集成。

**行业趋势**：ThingsBoard MCP 2.0 允许 AI 助手直接查询和操作平台数据。

**建议**：
- 实现 Systar MCP Server（查询设备状态、告警、统计数据）
- 支持自然语言交互："显示所有温度超过 80°C 的设备"
- 前瞻性功能，随 AI 助手普及而增值

**工期**：1-2 周

---

## 四、实施优先级排序

| 优先级 | 改进项 | 预估工期 | 价值 | 依赖 |
|--------|--------|----------|------|------|
| P0 | 告警关联与疲劳治理 | 2-3 周 | 运维效率直接提升，用户最迫切需求 | 无 |
| P1 | 计算/派生字段引擎 | 2-3 周 | 运维自主性，减少开发依赖 | 无 |
| P1 | SCADA/HMI 可视化 | 4-6 周 | 工业场景核心差异化 | WebSocket 实时推送 |
| P2 | MQTT Sparkplug B | 2-3 周 | 工业通信标准化 | 评估 Protobuf 离线部署影响 |
| P2 | AI/ML 异常检测钩子 | 1-2 周 | 已有基础，扩展接口 | 无 |
| P3 | 边缘代理架构 | 4-6 周 | 远程站点刚需 | 核心模块独立化 |
| P3 | OpenTelemetry 集成 | 2-3 周 | IT/OT 融合 | 无 |
| P3 | 时序预测入门 | 1-2 周 | 已有基础，增强闭环 | 无 |
| P4 | 数字孪生数据模型 | 3-4 周 | 前瞻性 | SCADA 画布编辑器 |
| P4 | 配置即代码 | 2-3 周 | DevOps 成熟度 | 无 |
| P4 | MCP 服务器 | 1-2 周 | AI 助手集成 | 无 |

---

## 五、与现有 Roadmap 的对齐

当前 Roadmap Phase 8-12 的规划与本建议的对齐关系：

| Roadmap 阶段 | 原规划 | 建议调整 |
|-------------|--------|----------|
| Phase 8 数据基础设施 | 数据保留策略、时序DB、PG/SQLite | 保留，但增加"计算/派生字段引擎"（VirtualProbe 数据模型与数据库适配同属数据层） |
| Phase 9 协议驱动扩展 | HTTP Pull、DNP3、LonWorks | 保留，但增加"MQTT Sparkplug B"（优先级高于 DNP3/LonWorks，与现有 MQTT 驱动自然扩展） |
| Phase 10 运维智能化 | 智能告警关联 | 扩展为"告警关联 + 疲劳治理 + AI 异常检测钩子" |
| Phase 11 部署与扩展性 | 物理分库、Redis、多实例 | 增加"边缘代理架构" + "OpenTelemetry 集成" |
| Phase 12 资产业务扩展 | 生命周期、价值跟踪 | 保留 |
| **新增** | — | SCADA/HMI 可视化（Phase 8.5 或 Phase 9.5，与数据基础设施并行） |
| **新增** | — | MCP 服务器（Phase 12+） |

---

## 六、建议实施顺序

基于价值/工期比和依赖关系，建议按以下顺序实施：

1. **P0 告警关联与疲劳治理**（2-3 周）— 用户最迫切需求，无外部依赖
2. **P1 计算/派生字段引擎**（2-3 周）— 运维自主性，与 Phase 8 数据基础设施对齐
3. **P2 AI/ML 异常检测钩子**（1-2 周）— 工期短，已有基础，与 Phase 10 对齐
4. **P1 SCADA/HMI 可视化**（4-6 周）— 核心差异化，工期较长可与其他任务并行
5. **P2 MQTT Sparkplug B**（2-3 周）— 工业通信标准化
6. **P3 时序预测入门**（1-2 周）— 增强闭环
7. **P3 OpenTelemetry 集成**（2-3 周）— IT/OT 融合
8. **P3 边缘代理架构**（4-6 周）— 远程站点刚需
9. **P4 数字孪生 / 配置即代码 / MCP** — 前瞻性

---

## 七、现有代码基础与改进点对照

| 改进项 | 现有代码基础 | 需新增/扩展 |
|--------|-------------|------------|
| 告警关联 | `AlarmHandler`（策略引擎 + `alarmedSet` 去重）+ `LinkageHandler`（`CauseType.ALARM` 告警触发联动） | `AlarmCorrelationService`、`AlarmSuppressionService`、`AlarmEscalationService`、DB 字段扩展、`CauseType.CORRELATION_GROUP` |
| 派生字段 | `ExpressionEvaluator` + `SpelExpressionEvaluator`（SpEL 表达式） | `VirtualProbe` 类、跨 Probe 引用上下文、`ResultDispatcher` 计算链 |
| SCADA/HMI | ECharts Dashboard + `systar-websocket` 实时推送 | SVG 符号库、画布编辑器、`t_dashboard_layout` 表 |
| Sparkplug B | `MqttService`（Paho v3 + topic routing） | Protobuf 编解码、BIRTH/DEATH 处理、MQTT 5.0 客户端 |
| AI/ML 钩子 | `AnalysisService`（Z-Score + 移动平均） | `AnomalyDetector` 接口、检测器重构、外部模型集成 |
| 时序预测 | `AnalysisService.predictTrend()` + `assessDeviceHealth()` | 置信区间、趋势违反告警、健康评分联动 |
| 边缘代理 | `systar-monitor-core` + `systar-monitor-drivers` | 独立可运行模块、嵌入式存储、配置同步 |
| OpenTelemetry | 无 | OTel SDK 集成、Metrics 导出/摄入 |

---

## 八、模块架构考量

### 告警-联动模块重构分析

当前告警和联动都订阅 `MonitorResultEvent`，但关注点不同：

| 模块 | 关注点 | 输入 | 输出 |
|------|--------|------|------|
| `AlarmHandler` | 告警触发与去重 | `MonitorResultEvent`（WARNING/ERROR） | `ErrorMessageLog` → 持久化 |
| `LinkageHandler` | 触发联动动作 | `MonitorResultEvent`（所有状态） | `Control.execute()` + 聯动日志 |

告警关联新增的三个 Service 关注的是**事件后处理**（聚合、抑制、升级），与现有 `AlarmHandler` 的**触发逻辑**是上下游关系：

```
MonitorResultEvent
    → AlarmHandler（触发告警）
        → AlarmCorrelationService（关联聚合）
        → AlarmSuppressionService（抑制去重）
        → AlarmEscalationService（升级通知）
    → LinkageHandler（触发联动，已有 CauseType.ALARM）
```

**重构建议**：不合并告警和联动模块，保持现有独立 `alarm` 和 `linkage` 包结构。理由：
1. 告警和联动的关注点不同（通知 vs 执行动作），合并违反 SRP
2. `LinkageHandler` 已有 `CauseType.ALARM` 扩展点，告警关联可通过新增 `CauseType.CORRELATION_GROUP` 自然融入
3. 告警关联的新 Service 放在 `alarm` 包下，保持包内聚合度

**新增 `CauseType.CORRELATION_GROUP` 的设计**：
- 关联事件组形成后，`AlarmCorrelationService` 发布 `CorrelationGroupEvent`
- `LinkageHandler` 监听该事件，当 `CauseType.CORRELATION_GROUP` 规则匹配时触发联动
- 这允许"一组关联告警→联动"场景，如"设备过热事件组→自动停机"

### 监控核心包结构规划

```
com.systar.monitor.alarm/
    AlarmHandler             — 告警触发引擎
    AlarmRule                — 告警规则模型
    AlarmStrategy            — 告警策略枚举
    ErrorMessageLog          — 告警消息模型
    AlarmCorrelationService  — 告警关联（新增）
    AlarmSuppressionService  — 告警抑制（新增）
    AlarmEscalationService   — 告警升级（新增）
    CorrelationGroupEvent    — 关联事件组事件（新增）

com.systar.monitor.linkage/
    LinkageHandler           — 联动引擎
    LinkageTriggerStrategy   — 触发策略接口
    CauseType                — 触发类型枚举（新增 CORRELATION_GROUP）
    ...
```

### 派生字段引擎架构

`VirtualProbe` 应放在 `com.systar.monitor.asset` 包下（与 `Probe`/`Control` 同级），因为它是一种资产类型。计算引擎放在 `com.systar.monitor.result` 包下，与 `ResultDispatcher` 同级：

```
com.systar.monitor.asset/
    Probe
    Control
    VirtualProbe             — 虚拟探针（新增）

com.systar.monitor.result/
    ResultDispatcher         — 现有分发引擎
    VirtualProbeCalculator   — 虚拟探针计算器（新增）
```
