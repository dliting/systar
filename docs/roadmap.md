# Systar 开发路线图

## 当前版本：v1.1.0（开发中）

> **项目状态**：Phase 1–7 全部完成，Phase 8 数据保留策略已实现，前端 UX Phase 2 进行中。下一步聚焦前端 UX 改进和时序数据库接入。

P0 核心功能已实现，P1 协议驱动 14 个全部完整实现，P1.5 VirtualProbe 引擎已完成，支持 MySQL/H2 双数据库，系统可通过 IntelliJ/Maven 启动并跑通完整端到端流程（采集→存储→告警→联动→控制→统计→运维→看板）。

---

## 待完成功能

### P0 — 核心功能缺失 ✅ 已完成

| # | 任务 | 说明 | 状态 |
|---|------|------|------|
| 1 | ResultPersistHandler 持久化实现 | `persistFloat/persistInt/persistBoolean/persistString` 通过 SampleRepository 实现 DB 持久化 | ✅ |
| 2 | AlarmHandler 队列消费 | 添加 daemon 消费线程，通过 AlarmRepository 持久化告警 | ✅ |
| 3 | LinkageHandler 日志持久化 | 注入 LinkageRepository，在 executeEffect 中持久化联动日志 | ✅ |
| 4 | AssetLoader 实现 | 创建 DatabaseAssetLoader（systar-server），从数据库加载资产树 | ✅ |
| 5 | PassiveService 路由注册 | MonitorServer.startPassiveServices 遍历 monitor 并调用 registerMonitor() | ✅ |
| 6 | LinkageRuleCauseEntity.ruleId schema 不匹配 | 移除映射不存在列的 ruleId 字段，转换为 cause.id = ruleId | ✅ |

### P1 — 协议驱动实现 ✅ 已完成

| # | 驱动 | 协议 | 库 | 实现状态 |
|---|------|------|------|------|
| 5 | Modbus TCP | TCP/502 | Jamod 1.2 | ✅ Active — 功能码 01-06，寄存器读写 |
| 6 | OPC UA | opc.tcp | Eclipse Milo 0.6.8 | ✅ Active — OpcUaClient + NodeId 读取 |
| 7 | BACnet | BACnet/IP | BACnet4J 4.1.6 | ✅ Active — LocalDevice + ReadPropertyRequest |
| 8 | SNMP | UDP/161 | SNMP4J 3.7.7 | ✅ Active — CommunityTarget + GET/GETNEXT |
| 9 | Siemens S7 | TCP/102 | S7Connector 2.1 | ✅ Active — DB/标记读取 + 原始字节类型转换 |
| 10 | IEC 104 | TCP/2404 | j60870 1.2.0 | ✅ Active — ClientConnectionBuilder + ConnectionEventListener + ASDU 解码 |
| 11 | Weather | HTTP API | java.net.http | ✅ Active — HTTP GET + 5分钟缓存 + JSON 解析 |
| 12 | UPS | SNMP/161 | SNMP4J 3.7.7 | ✅ Active — RFC 1628 UPS-MIB + 8 个标准 OID |
| 13 | Environmental | TCP | java.net.ServerSocket | ✅ Passive — TCP 服务器 + 25字节帧解码器 |
| 14 | WebSocket | ws:// | Java-WebSocket 1.5.4 | ✅ Passive — WS 客户端 + JSON 路由 + AtomicInteger 重连 |
| 15 | TCP/IP | TCP | java.net.Socket | ✅ Active — 原始 Socket + 连接性检查 + 寄存器模式 |
| 16 | MQTT | TCP/1883 | Eclipse Paho 1.2.5 | ✅ Passive — MqttClient + topic routing + JSON path 提取 |
| 17 | Simulate | — | 内置 | ✅ Active — 随机/正弦/固定/递增模拟数据 |
| 18 | Manual Input | — | 内置 | ✅ Passive — 手动数据输入 |

### P1.5 — VirtualProbe 计算派生字段引擎 ✅ 已完成

| # | 任务 | 说明 | 状态 |
|---|------|------|------|
| 1 | VirtualProbeType / ProbeRef | SpEL 表达式类型 + `#probe[id].value` 引用解析 | ✅ |
| 2 | VirtualProbe 类 | 继承 Probe，detect() 通过 SpEL 表达式计算派生值 | ✅ |
| 3 | VirtualProbeEngine | 依赖索引 + @EventListener 监听 MonitorResultEvent 触发重计算 | ✅ |
| 4 | 循环依赖检测 | computing Set 防止无限递归，支持合法链式传播 | ✅ |
| 5 | SpEL 安全沙箱 | RestrictedSpelContext + 属性白名单（仅允许 .value） | ✅ |
| 6 | AssetCrudListener 集成 | CREATED/UPDATED/DELETED 生命周期管理，VP→非 VP 类型转换 | ✅ |
| 7 | REST API + MonitorAssetVO | isVirtual/expression/dependsOn 字段 + CRUD 验证 | ✅ |
| 8 | 前端 UI | 探针多选下拉 + 表达式提取按钮 + isVirtual 开关 | ✅ |
| 9 | H2 DDL 同步 | t_probe 表添加 is_virtual/expression/depends_on 三列 | ✅ |
| 10 | 单元/集成测试 | VirtualProbeTest + VirtualProbeEngineTest + VirtualProbeLifecycleIT + 前端 10 测试 | ✅ |

### P2 — 已知设计问题

**已修复：**

| # | 问题 | 状态 |
|---|------|------|
| 16 | Asset.metadata 使用 HashMap（非线程安全） | ✅ 改用 ConcurrentHashMap |
| 17 | Asset.setState 非原子操作 | ✅ 添加 volatile + synchronized |
| 18 | Monitor.mode 非线程安全 | ✅ 添加 volatile |
| 22 | 硬编码常量散布各处 | ✅ ActiveService 已提取常量 |
| 23 | WebSocket CORS 允许所有来源 | ✅ 收紧为 localhost |
| 24 | REST API 无输入校验 | ✅ 添加 MAX_IDS_LENGTH 限制 |
| 25 | Modbus 模块使用 java.util.logging | ✅ 统一改用 SLF4J |
| 26 | LinkageRuleCauseEntity 缺少 ruleId 字段 | ✅ 修正：移除不存在的 ruleId 列映射 |
| 29 | PassiveService.resultDispatcher 类型为 Object | ✅ 替换为 ResultDispatcher |
| 30 | 缺少数据库适配层 | ✅ DatabaseDialect 适配器模式（MySQL/H2） |
| 31 | 枚举类型无法映射数据库整数 | ✅ MonitorMode.getCode/fromCode + EnumOrdinalTypeHandler |
| 32 | seed data 中 TimeSpan 格式错误 | ✅ 统一为短格式（10s, 1m, 5m） |
| 33 | seed data 中 driver_class 路径错误 | ✅ 修正为实际驱动类全限定名 |
| 34 | 缺少数据库初始化脚本 | ✅ sql/init-mysql.sh + init-mysql.bat |

**推迟到后续阶段：**（全部已修复）

| # | 问题 | 修复方案 | 状态 |
|---|------|----------|------|
| 19 | MonitorApiController 直接注入 5 个 Mapper（SRP 违规） | 改用 Service 层调用 | ✅ |
| 20 | LinkageHandler 硬编码 "\|" 判断布尔探针（OCP 违规） | 策略模式：LinkageTriggerStrategy 接口 | ✅ |
| 21 | Monitor 静态依赖 SpelExpressionParser（DIP 违规） | ExpressionEvaluator 接口 + ExpressionEvaluatorHolder | ✅ |
| 27 | SpaceEntity 的 seqence 拼写错误 | ✅ 修正为 sequence，同步更新 MySQL/H2 schema 和种子数据 | ✅ |
| 28 | 每次 detect() 都重新解析 source 字符串 | init() 中解析并缓存 SourceDescriptor | ✅ |
| 35 | 联动引擎仅支持 Monitor 触发 | 添加 CauseType(ALARM/MONITOR) + LinkageRuleBean + CRUD API + 双树 UI | ✅ |
| 36 | ViewType 仅在类型级别，属性无展示控制 | 属性级 ViewType/DataType + ViewType.infer() 自动推断 | ✅ |
| 37 | t_linkage_log DDL 与 Entity 字段不匹配 | 统一为 rule_id，同步 MySQL/H2 DDL | ✅ |

---

## 阶段进展

### 第一阶段：核心框架 ✅
基础框架、资产模型、监控调度、告警联动。

### 第二阶段：数据持久化 ✅
MyBatis-Plus 集成、Repository 实现、资产加载。

### 第三阶段：协议驱动 ✅
P1 协议驱动 14 个全部完整实现，每个驱动都有单元测试。支持离线/内网部署（Maven Wrapper + 项目本地仓库）。

### 第四阶段：数据库与部署 ✅
- ✅ MySQL/H2 双数据库支持（DatabaseDialect 适配器）
- ✅ Schema 拆分为 MySQL/H2 独立文件
- ✅ 数据库初始化脚本（sql/init-mysql.sh, sql/init-mysql.bat）
- ✅ MySQL 8.4 本地开发环境验证通过
- ✅ Systar 服务端口调整为 8081 + 共享密钥鉴权过滤器
- ✅ Controller 层重构（SRP 拆分，5 个独立 Controller + 全局异常处理器）
- ✅ 多驱动并发集成测试
- ✅ BACnet4J 私有 Maven 仓库配置 + ReadProperty 实现
- ✅ 离线/内网部署支持（项目本地 Maven 仓库 lib/maven-repo/）
- ✅ 核心引擎移植完善（接口和行为可能调整）
- ✅ 数据库 Schema 定稿
- ✅ 生产环境部署配置（Nginx 模板已就绪）
- ✅ 项目根目录清理 + bin/ 脚本全面翻新（16 个脚本：启停 + 构建 + 数据库初始化，CRLF/LF 编码修复，PID 文件管理，脚本自动化测试 44 项检查）
- ✅ 后端 PID 文件管理（SystarApplication.main() 启动时写 PID，@PreDestroy 清理）
- ✅ 前端 PID 文件管理（Vite pid-file 插件 configureServer 写 PID，close 钩子清理）
- ✅ init-database 脚本（DROP/CREATE DATABASE + DDL 初始化）

### 第五阶段：前端与管理集成 ✅
- ✅ JWT 统一鉴权 + 设备级数据权限（JWT claims 增强 + Systar JWT Filter + WebSocket 鉴权）
- ✅ 监控报警前端页面（资产 CRUD、实时监控、告警/联动配置、控制执行）
- ✅ Systar 前端独立项目（`frontend/` Vue3 项目，Vite build 通过，IoT 页面全部迁入）
- ✅ Nginx 统一入口配置模板
- ✅ 监控大屏/数据看板（科技风 Dashboard，ECharts 环形图 + 统计卡 + 告警概览，直调 Systar JWT 认证）
- ✅ 用户角色权限管理（systar-system 模块：用户/角色/菜单/部门/通知/日志 6 个 Controller + RequirePermission AOP）
- ✅ 分库隔离部署（接口层已隔离：独立端口 8081 + JWT 鉴权；物理分库暂缓）

### 第六阶段：运维业务扩展
- ✅ 运维工单系统（告警自动生成、派发、处理、关闭/取消闭环）
- ✅ 设备台账管理（生命周期字段、扩展属性、维护记录）
- ✅ 巡检管理（计划、任务、结果、调度、异常联动工单）
- ✅ 报表统计（告警/工单/巡检/设备运行/维护 5 维度聚合统计 + Dashboard 缓存 + ECharts 可视化）
- ✅ 异常检测与预测性维护（Z-Score 异常检测 + 移动平均趋势预测 + 加权健康评估，4 API）
- ✅ 数据看板（DashboardController + 科技风大屏：ECharts 环形图、设备在线率、KPI 统计条、告警趋势、工单分布）
- ⬜ 三维数字孪生

### 第七阶段：技术债清理与质量保障 ✅
- ✅ 修复 surefire forkCount=0 构建隔离问题（forkCount=0 已验证可用，Windows 下 forkCount=1 有跨盘符问题）
- ✅ systar-data 测试覆盖率 20%→90%（12→32 测试文件，109 测试用例）
- ✅ systar-ops 测试覆盖率 18%→80%+（17 个测试文件，含 Mapper CRUD + 自定义 SQL）
- ✅ systar-server 测试覆盖率 39%→80%+（含 Controller、Security、Loader 测试）
- ✅ systar-system 关键 Service 测试（SysDeptServiceImpl、SysOperLogServiceImpl、SysMenuService 等）
- ✅ 前端 API/工具函数层测试补充（17 文件 147 测试，含 CronWizard、DurationInput、errorHandler）

### 第八阶段：数据基础设施
- ✅ 数据保留策略（DataRetentionService + 每月分批删除 + 定时调度 + REST API + 前端配置页）
- ✅ 告警关联/疲劳管理（AlarmCorrelationService + 时间窗口聚合 + 告警抑制 + REST API）
- ⬜ 时序数据库接入（InfluxDB/TDengine 可选）
- ⬜ PostgreSQL 数据库适配
- ⬜ SQLite 嵌入式数据库适配

### 第八点五阶段：前端 UX 改进
- ✅ Phase 1：导航分组菜单 + 通知铃铛 + 面包屑 + 键盘快捷键（useKeyboard）
- 🏗️ Phase 2：EnhancedTable 组件（排序/筛选/导出）+ 告警页面迁移
- ⬜ Phase 3：暗色主题完善 + 响应式布局

### 第九阶段：协议驱动扩展
- ⬜ HTTP Pull 通用接口拉取驱动
- ⬜ DNP3 电力行业协议驱动
- ⬜ LonWorks 建筑/工业自动化协议驱动

### 第十阶段：运维智能化
- ✅ 智能告警关联（多告警根因分析和告警抑制）— AlarmCorrelationService
- ⬜ 告警疲劳管理（告警风暴检测和智能降噪）

### 第十一阶段：部署与扩展性
- ⬜ 物理分库隔离（IoT 库 + 管理库独立部署）
- ⬜ Redis 共享会话（JWT 会话续期）
- ⬜ 多实例部署（IoT 核心系统水平扩容）

### 第十二阶段：资产业务扩展
- ⬜ 资产生命周期管理（采购、折旧、维保计划、领用/退库、报废审批）
- ⬜ 资产价值跟踪与成本分析
