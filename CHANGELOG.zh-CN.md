# 更新日志

[English](CHANGELOG.md) | 简体中文

本项目的所有重要变更均记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

---

## [1.3.0] - 2026-10-25

监控曲线组件重构——统一 OHLC 趋势契约、可重用 `TrendChart` 组件、SCADA 术语。

### 新增

- **监控曲线组件** — `frontend/src/components/TrendChart/` 成为自包含可重用组件：聚合粒度缺省以趋势折线渲染、可勾选 箱体（蜡烛图）形态（期初值/期末值/最高值/最低值/波动幅度）、分时折线缺口断开并可选极值带、可选采样数子图、客户端 5/10/20 期平均、十字光标读数，以及统一工具栏（自动/分时/小时/日/周/月直接选择 + 近1小时…全部预设 + 自定义区间 + 刷新/重置视图）
- 后端趋势 API 返回统一点契约 `{granularity, mode: RAW|BUCKETED|OHLC, points[{t,avg,max,min,open,close,count}], bucketSeconds, summary}`；粒度 `AUTO` 由服务端解析，超大时间窗自动钳制 / 降采样至约 1200 桶
- Playwright 交互测试套件（`frontend/e2e/`）覆盖粒度切换、时间预设、图层开关、十字光标、滚轮缩放、下钻、错误重试、自定义区间
- **开发持久化数据库** — dev 配置改用 `temp/data/` 下的文件型 H2 数据库，趋势历史跨重启保留（删除该目录即重置）

### 变更

- **破坏性：** 趋势响应移除 `dataPoints`/`intradayPoints`/`avg5`/`avg10`/`avg20`；`t_monitor_stats` 新增可空 `open_val`/`close_val`（启动时自动 ALTER；历史行 open/close 保持 NULL，图表退化为均值处的平头十字——不回填）
- 图表术语统一为工业 SCADA 用语（期初值/期末值/波动幅度/上升/下降），替换金融市场措辞
- **缺省折线渲染：** 所有聚合粒度缺省渲染普通趋势折线，蜡烛图形态改由 箱体 开关按需启用。折线只是视觉形态——十字光标仍报告完整 OHLC。
- 十字光标读数随数据形态自适应：原始采样显示 数值（open≡close 使 OHLC 措辞在此无意义），聚合桶显示 上升/下降 与 期初值/期末值；图表每次重渲染即清除读数。
- 图例与阈值线跟随实际可绘制内容：仅当存在多于一条可见序列时显示图例（全空的平均线被省略），超出数据跨度两倍的阈值不再绘制 markLine，而非钉在坐标轴边缘。
- 选择粗于当前窗口所能容纳的粒度时，窗口自动扩展到该粒度的缺省区间——此前后端会静默把数据沿阶梯降一级，而工具栏仍显示用户所选。
- 内嵌趋势面板：监测点下拉按 id 排序（缺省选中确定地落在有数据的监测点），图表高度填满视口。

---

## [1.2.0] - 2026-10-25

闭源监测引擎——调度 / 告警 / 联动 / 定时控制运行时移入免费二进制的闭源模块；框架保持 MIT。

### 新增

**闭源监测引擎**
- 新增 `commercial/systar-monitor-engine` 模块（闭源、二进制免费）：资产调度、告警判定、联动执行、定时控制。所有功能完整可用——无功能门控、无运行时许可校验；商业许可提供引擎源码访问与技术支持（`commercial/systar-monitor-engine/LICENSE-ENGINE.txt`；设计见 `docs/design/closed-engine.md`）
- Releases 新增 `systar-server-<版本>-full.jar`：内嵌引擎，下载即用全功能；不含引擎的构建（公开 MIT 仓库）照常运行——框架启动、资产管理完整可用，仅运行时监控操作告警跳过一次
- 引擎经 `EngineAutoConfiguration`（`AutoConfiguration.imports`）自动装配；server 代码只依赖 MIT API，经 `ObjectProvider` 可选注入引擎
- 引擎缺席时由 server 接管运行时存储：`EngineAbsentAssetBootstrap` 启动时加载类型注册表与种子资产，资产 CRUD 事件直接落到 `AssetStore`——开源形态下资产管理完整可用

### 变更

- `systar-monitor-core` 更名为 `systar-monitor-api`——仅含 MIT 契约（资产模型、`AssetStore`、`MonitorEngine` 端口、告警 / 联动 / 定时任务契约）；引擎内部实现移入闭源模块
- server 控制器、监听器与生命周期只引用 `systar-monitor-api`；引擎缺席时探测 / 控制接口返回冲突结果（"Monitor engine not present; … skipped."）而非报错

### 修复

- 引擎装配：server 组件扫描显式排除 `com.systar.engine.*`，`EngineAutoConfiguration` 成为引擎 Web 层的唯一注册点——定时任务 API 仅在引擎存在时返回 200（引擎缺席构建返回 404）
- 运维 API 模块（工单 / 巡检 / 台账）缺少 `/api` 前缀，对应仪表盘页面对打包后端渲染为空
- 工单状态图表与后端状态枚举对齐（CREATED / ASSIGNED / PROCESSING / CLOSED / CANCELLED）
- 随包提供 SVG favicon——登录页不再产生 `/favicon.ico` 404
- 巡检计划种子 cron 改为 Spring 6 段格式（H2 与 MySQL 双方言）——两个演示计划恢复生成任务，不再每分钟以 "Invalid inspection cron expression" 跳过
- `frontend/package.json` 版本对齐 1.2.0 发布

---

## [1.1.1] - 2026-10-24

驱动插件目录架构——驱动从核心构建中拆出、成为拷入即用的自包含插件目录——以及 MIT 重新授权。

### 新增

**驱动插件目录架构**
- 每个协议驱动现在是一个自包含插件目录——`driver.jar` + `lib/` + 可选 `seed/` CSV——启动时在 `systar.drivers.path`（默认 `./drivers`，按工作目录解析）下发现。拷入插件目录重启即生效：不改服务器代码、不重新打包。删除插件也从不导致启动失败——受影响的数据行按名 WARN 跳过，并级联到依赖它们的行
- 每插件独立类加载器隔离私有协议库；核心 API 类按 parent-first 共享
- XML-only 插件目录（目录根放 loose 类型 XML）无需写 Java 即可扩展类型目录，可引用其他插件提供的类
- 插件种子 CSV：白名单表（`dictionary` / `service` / `asset` / `asset_attribute` / `probe` / `control`），列头 = 数据库列名，按主键 insert-if-absent，执行条件与主 SQL 种子一致
- 格式错误或重名的类型定义在启动时快速失败，并指明问题插件

**协议驱动**
- 新增协议驱动二次开发指南（`docs/driver-development-guide.md`）：插件目录布局、类型 XML 自注册、种子 CSV 规范、私有驱动部署配方

### 变更

**驱动插件目录架构（drivers/）**
- `core/systar-monitor-drivers` 拆分为 14 个 `drivers/systar-driver-*` 插件模块（每协议一个）；每个模块在 `target/plugin/` 下构建自己的插件目录，聚合到 `drivers/dist/` 供开发运行时使用
- 服务器 fat jar 瘦身：不再捆绑驱动类与私有协议库——运行期经驱动搜索路径加载，`systar-server` 与驱动无编译期依赖
- `gpl-drivers` Maven profile 及其 `settings.xml` 激活机制退役——GPL 驱动就是普通插件模块；导出公开仓库时删除其两个目录及 `drivers/pom.xml` 中对应两行 `<module>`
- GPL 演示种子行（字典 803、服务 103/104、资产 44/45）从共享 SQL 种子脚本迁入 GPL 插件自带的 `seed/` CSV，沿用原 id 使已播种的存量库保持幂等
- `systar.asset-type.scan-paths`（本周期早前新增）在发布前退役——外置类型 XML 改用 XML-only 插件目录

**许可证**
- 许可证由 GPL-3.0-only 改为 **MIT**。BACnet/IP 与 IEC 60870-5-104 驱动以 GPL-3.0 驱动插件目录提供（`drivers/systar-driver-bacnet`、`drivers/systar-driver-iec104`，**不在本仓库**）——其底层库 BACnet4J、j60870 均为 GPL-3.0-only；包含这些插件目录的分发物整体遵循 GPL-3.0。重新授权前已播种的数据库仍带有 GPL 演示行（服务 103/104、资产 44/45）：改用纯 MIT 构建时，这些行在启动加载时被 WARN 跳过（行残留于库中、资产树不再显示），不再导致启动失败；重装 GPL 插件目录重启即恢复。新部署如需这两个协议，将 GPL 插件目录拷入驱动搜索路径即可——其演示种子行以种子 CSV 随插件自带
- org.json 替换为 Jackson（Apache-2.0）：JSON License（"Good, not Evil"）非 OSI
  认可。有意丢弃 org.json 的两个怪癖——MQTT jsonPath 命中 JSON null 时返回 `null`
  （原来是 `JSONObject.NULL` 哨兵）；路径穿过标量节点继续取值时返回 `null`
  （原来返回该标量）

**协议驱动（drivers/ / systar-server）**
- 驱动模块化：协议类型定义 XML 从 server `config/assets/` 迁入驱动插件模块（与驱动类同包），加载器改为目录扫描自注册，删除全局索引 `Assets.xml`——新增/删除驱动不再需要维护索引
- ModbusService 手写配置解析链（metadata > 类型默认 > 内置默认）统一到框架属性绑定 `bindProperties`
- 类型配置版本号仅在内容实际变化时递增，修复每次启动无条件 version+1 的版本膨胀
- Timeout/MaxConnections 支持按实例配置（类型默认值兜底）

**资产模型（systar-monitor-core / systar-server）**
- AssetKind 与 ordinal 解耦：`t_asset.kind` 改存显式 code（1/2/3/4 = 设备/服务/监测器/操控器），枚举重排序不再影响存量数据
- 删除 Space（空间）资产种类：设备与服务成为顶层资产；AssetStore 以中性树锚点（不入扁平索引）挂载顶层资产，锚点不会出现在 `/assets` 与统计中；资产树接口 `/tree` 过渡期返回首个顶层子树（后续将替换为分组森林接口 `/asset-tree`）
- 创建设备/服务的请求携带 parentId 时明确拒绝（提示 must be top-level）

### 移除

- ops 侧 spaceId 全链路死代码：统计 5 个端点的 spaceId 参数、设备台账过滤、工单回填与 `space_id` 实体字段、`DeviceInfoProvider.resolveSpaceId`、`DeviceDto.parentId` 死字段（全链路无读者，已逐一核实）
- 数据库结构清理（MySQL + H2 双份脚本）：删除 `t_space` 表及其索引、`t_asset.space_id`、`t_device.parent`、`t_service.parent`、`t_work_order.space_id` 列（含工单 `i_wo_space` 索引）；种子改写——kind=0 行删除、设备/服务以顶层（parent_id=0）播种、无读者的空间类型字典项移除

### 修复

**资产与统计**
- 修复 UI 创建资产被阻断：类型下拉 name 过滤丢失、SERVICE 模式缺省推导错误
- 统计读取未知/损坏的 `t_asset.kind` 码时记录 warn 日志（含 code 与行 id），不再无感知跳过

**统计前端（frontend）**
- 修复统计报表全部 6 个页面的图表从未渲染：页面模板此前使用未声明的字符串 ref（如 `ref="pieRef"`），`useChart()` 内部的 `chartRef` 从未绑定到 DOM，`initChart()` 静默返回 null，图表区域一直空白（仅标题/图例）。`useChart` 新增 `bindChart(el)` 绑定入口（多图页面统一 `:ref="xxx.bindChart"`），且未绑定时 `initChart()` 明确报错不再静默
- 演示种子（03-simulator.sql，双方言）补充上一期（第 8~13 天前）告警历史 15 条，使"环比对比"面板双柱可见
- 统计页 fetch 失败不再静默吞掉：6 个概览页与趋势页的加载异常现在先记录 `console.error` 再回退空态（此前后端 500/断网只显示"暂无数据"，控制台无任何线索）

**资产视图（systar-server）**
- 修复 UI 创建资产的统计漏计：`t_asset.parent_id` 此前误存父资产的运行时 id（`t_device.id` 等），现统一存父资产行 id，与种子数据及统计 SQL 的 parent 链对齐；存量库修复 SQL 见 `docs/design/ops-statistics-design.md` 第 5 节

## [1.1.0] - 2026-06-07

运维业务扩展、技术债清理、数据保留策略、前端 UX 改进。

### 新增

**运维业务（systar-ops）**
- 工单系统：告警自动生成工单、派发、处理、关闭/取消闭环
- 设备台账：全生命周期管理（采购→安装→维护→退役）
- 巡检管理：计划、任务、结果、调度、异常联动工单
- 统计分析：告警/工单/巡检/设备运行/维护 5 维度聚合 + Dashboard 缓存 + ECharts 可视化
- 异常检测：Z-Score 异常检测 + 移动平均趋势预测 + 加权健康评估

**系统管理（systar-system）**
- 用户/角色/菜单/部门/通知/日志 6 个 Controller + RequirePermission AOP
- 前端系统管理页面（用户/角色/菜单/部门/日志/通知）

**虚拟监测点引擎（P1.5）**
- VirtualProbeType / ProbeRef：SpEL 表达式类型 + `#probe[id].value` 引用解析
- VirtualProbe 类：继承 Probe，detect() 通过 SpEL 表达式计算派生值
- VirtualProbeEngine：依赖索引 + @EventListener 监听 MonitorResultEvent 触发重计算
- 循环依赖检测：computing Set 防止无限递归
- SpEL 安全沙箱：RestrictedSpelContext + 属性白名单
- 前端 UI：探针多选下拉 + 表达式提取按钮 + isVirtual 开关

**数据保留策略（Phase 8）**
- DataRetentionService：按配置天数每月分批删除过期数据
- 定时调度 + REST API（GET/PUT）+ 前端配置页
- 安全校验：最小保留天数限制

**告警关联**
- AlarmCorrelationService：时间窗口聚合 + 告警抑制
- AlarmPusher：WS 告警广播，monitor result 增加 type 字段

**前端 UX 改进（Phase 1）**
- 导航分组菜单：子菜单分组 + 告警页签路由
- NotificationBell 组件：未读计数 + 告警列表弹窗
- Breadcrumb 组件：全局面包屑导航
- useKeyboard 组合式函数：Esc/Enter 快捷键支持

**前端组件**
- EnhancedTable 组件：排序/筛选/导出（告警页面已迁移）
- CronWizard 组件：Cron 表达式可视化编辑器
- DurationInput 组件：时间间隔编辑
- Dashboard 科技风大屏：ECharts 环形图、设备在线率、KPI 统计条、告警趋势、工单分布

**MQTT 协议驱动**
- Eclipse Paho 1.2.5，MqttClient + topic routing + JSON path 提取

**协议驱动完善**
- BACnet：补全 APDU 编码，从部分实现升级为完整实现
- IEC 104：补全 ASDU 编码，从部分实现升级为完整实现

### 变更

- 前端重构为独立 Vue3 项目（`frontend/`）
- 移除第三方承载目录与代理模块，前端直连后端 API
- 移除 integrations 目录
- 前端页面扩展至 11 个（新增 dashboard/inspection/ledger/login/operations/system/workorder）
- 联动引擎扩展：CauseType(ALARM/MONITOR) + LinkageRuleBean + CRUD API + 双树 UI
- 定时控制：CRUD API（8 端点）+ 启停持久化 + 前端管理页面 + CronWizard
- 告警去重：相同 monitorId+level 的告警不重复插入
- WebSocket 推送扩展：同时推送监测值变化和告警消息

### 测试

- systar-data：覆盖率 20%→90%（109 测试用例）
- systar-ops：覆盖率 18%→80%+（17 个测试文件）
- systar-server：覆盖率 39%→80%+
- systar-system：关键 Service 测试
- 前端：28 测试文件，含 Vitest 单元测试和页面级测试

---

## [1.0.0] - 2026-05-13

目录结构重组 + 前端集成完善 + 告警过滤。

### 变更

**目录重组**
- 核心模块移入 `core/`（systar-common、systar-monitor-core、systar-monitor-drivers、systar-data）
- 扩展模块移入 `extensions/`（systar-server、systar-websocket）
- 版本统一为 1.0.0（修复根 POM 与子模块不一致）

**前端与集成完善**
- IoT 前端 7 页面功能交互测试通过（资产 CRUD、监控数据、告警筛选、控制执行）
- 告警消息过滤（state、recovered 参数穿透前端→API→QueryWrapper）
- 启动脚本健康检查、stop-before-build、`--force`/`--skip-build` 标志

---

## [0.1.0] - 2026-05-10

初始开发版本。完成基础监控引擎、协议驱动骨架、MySQL/H2 双数据库适配，初步跑通端到端流程（采集→存储→告警→联动）。核心引擎和数据库 Schema 仍在调整中。

> **注意**：本版本为开发中间状态，接口和配置可能随移植进度变化。

### 新增

**核心监控引擎（systar-monitor-core）**
- 资产模型体系：Asset/CompoundAsset/Monitor 抽象层级，Space/Device/Service/Probe/Control 具体实现
- AssetStore 内存资产仓库，支持资产树的增删查和路径计算
- 两阶段结果分发：ResultDispatcher 同步预处理 + MonitorResultEvent 异步分发
- 采集调度器：MonitorScheduler + DetectTask + TaskDispatcher，支持按服务类型并发限制
- 告警引擎：3 种策略（ONLY_ONCE/CONTINUOUS/SELECTIVE），多级告警，自动恢复检测
- 联动引擎：因果规则匹配，监测值变化触发控制命令
- 定时控制：Cron 表达式驱动的计划任务执行器
- MonitorServer 门面：集成所有子系统，提供统一入口

**通用工具（systar-common）**
- AssetIdGenerator：高 16 位站点编码的 ID 生成器
- CodeDictManager/CodeCatalog/CodeItem：代码字典管理
- SystemConfigManager/SystemConfigItem：系统配置键值存储
- TimeSpan：不可变时间间隔工具，支持 "10s"/"5m"/"2h"/"1d" 格式

**数据访问层（systar-data）**
- 16 个 MyBatis-Plus 实体类和映射器
- 7 个 Service 接口和实现
- SampleRepository/AlarmRepository/LinkageRepository 持久化仓库
- MySQL/H2 双数据库适配（DatabaseDialect 适配器模式）

**协议驱动（systar-monitor-drivers）**
- Modbus TCP：功能码 01-06，寄存器读写（已实现）
- OPC UA：Eclipse Milo 客户端，NodeId 读取（已实现）
- BACnet：BACnet4J，对象/属性解析器（部分实现，缺少 APDU 编码）
- SNMP：SNMP4J，CommunityTarget GET/GETNEXT（已实现）
- Siemens S7：S7Connector，DB/标记读取 + 字节类型转换（已实现）
- IEC 104：j60870，TCP 连接 + YC/YX 解析（部分实现，缺少 ASDU 编码）
- Weather：HTTP API，5 分钟缓存 + JSON 解析（已实现）
- UPS：SNMP，RFC 1628 UPS-MIB 标准 OID（已实现）
- Environmental：TCP 服务器，25 字节帧解码器（已实现，被动模式）
- WebSocket：Java-WebSocket 客户端 + JSON 路由（已实现，被动模式）
- TCP/IP：原始 Socket + 连接性检查（已实现）
- Simulate：随机/正弦/固定/递增模拟数据
- Input：手动数据输入（被动模式）

**服务启动（systar-server）**
- Spring Boot 应用入口 + 启动生命周期编排
- REST API：资产树、实时/历史数据、控制命令、告警和联动规则
- DatabaseAssetLoader：从数据库加载资产树
- DatabaseDialect + MySQLDialect + H2Dialect 适配器
- DatabaseInitializer：DDL 和种子数据初始化
- 统一响应封装（Result<T>）

**WebSocket（systar-websocket）**
- MonitorWebSocketHandler：按会话订阅 + 变更检测推送
- MonitorResultPusher：桥接 MonitorResultEvent 到 WebSocket

**测试**
- systar-common：140 个单元测试
- systar-monitor-core：237 个单元测试
- systar-data：14 个 H2 集成测试
- systar-monitor-drivers/websocket/server：104 个测试
- JaCoCo 覆盖率插件集成

**基础设施**
- Maven 多模块项目结构
- 数据库初始化脚本（sql/mysql/init.sh, sql/mysql/init.bat）
- MySQL/H2 独立 schema 和 seed data 文件

### 修复

- Asset.metadata 线程安全：改用 ConcurrentHashMap
- Asset.setState 原子性：添加 volatile + synchronized
- Monitor.mode 可见性：添加 volatile
- WebSocket CORS：收紧为 localhost
- REST API 输入校验：添加 MAX_IDS_LENGTH 限制
- Modbus 模块日志：统一改用 SLF4J
- LinkageRuleCauseEntity 移除不存在的 ruleId 列映射
- PassiveService.resultDispatcher 类型：替换为 ResultDispatcher
- 枚举类型数据库映射：MonitorMode.getCode/fromCode + EnumOrdinalTypeHandler
- seed data TimeSpan 格式统一为短格式
- seed data driver_class 路径修正
- 非数值 effectCommand 存储为 Integer 时记录警告
- 联动日志补充 effectCommand 字段
- 优雅关闭时排空告警和联动队列

---

## 项目起源 — 2026-05-09

确立项目目标：独立的 IoT 通用工业监控运维核心框架。
