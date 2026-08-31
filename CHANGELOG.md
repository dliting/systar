# 更新日志

本项目的所有重要变更均记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

---

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
