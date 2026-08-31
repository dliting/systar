# Systar 概要设计文档

> **版本**: 1.1
> **日期**: 2026-06-07
> **定位**: 概要级设计文档

---

## 1. 系统整体架构

### 1.1 架构策略

采用 **独立单体 + 独立前端** 的简洁架构（详见 [技术概览](../tech_overview.md)）：

- **Systar 后端**：单一 Spring Boot 应用，自包含 IoT 核心、运维业务、系统管理全部能力
- **Vue3 前端**：独立前端项目，直连 Systar 后端 API

后端内部通过模块化和 DIP 原则保持核心引擎与业务扩展的解耦，前端独立部署，通过 REST API + WebSocket 交互。

### 1.2 当前状态

v1.1.0 开发中。IoT 核心系统框架完整，14 个协议驱动全部实现，VirtualProbe 派生字段引擎已完成。运维业务（工单/台账/巡检/统计）和系统管理（用户/角色/菜单）模块已实现。前端为独立 Vue3 项目（11 个页面），已完成 Phase 1 UX 改进（导航/通知/面包屑/快捷键），Phase 2 EnhancedTable 进行中。支持离线/内网部署。

---

## 2. 模块职责与依赖

### 2.1 模块职责

| 模块 | 目录 | 职责 | 关键类 |
|------|------|------|--------|
| **systar-common** | `core/` | 通用工具：ID 生成、代码字典、系统配置、TimeSpan | AssetIdGenerator, CodeDictManager, SystemConfigManager, TimeSpan |
| **systar-monitor-core** | `core/` | 核心监控引擎，无 Web 依赖，可嵌入 | Asset, Monitor, Probe, Control, ResultDispatcher, MonitorScheduler, AlarmHandler, LinkageHandler |
| **systar-data** | `core/` | 核心IoT数据访问层（MyBatis-Plus，DIP模式） | Entity, Mapper, Service（alarm/sample/linkage/scheduled_task 等） |
| **systar-monitor-drivers** | `core/` | 协议驱动集合（14 个完整实现） | ModbusConnection, BacnetConnection, Iec104Connection, MqttService 等 |
| **systar-server** | `extensions/` | Spring Boot 启动入口、REST API、配置、资产 CRUD | SystarApplication, AssetController, AssetOrchestrator, ServerLifeCycle, DatabaseAssetLoader, XmlAssetTypeLoader |
| **systar-ops** | `extensions/` | 运维业务（巡检/台账/工单/统计分析），自有数据层 | InspectionTaskService, DeviceLedgerService, WorkOrderService, StatisticsService, MonitorStatsAggregator |
| **systar-system** | `extensions/` | 系统管理（用户/角色/菜单/日志），自有数据层 | SysUserService, SysRoleService, SysMenuService, MybatisPlusConfig |
| **systar-websocket** | `extensions/` | WebSocket 实时推送 | MonitorWebSocketHandler, MonitorResultPusher |
| **systar-frontend** | `frontend/` | Vue3 + Element Plus 前端（独立项目） | TreePanel, EnhancedTable, NotificationBell, CronWizard, Breadcrumb, Dashboard |

### 2.2 依赖关系

```
                    systar-server（启动入口）
               /    |    \       \          \
              /     |     \       \          \
    systar-data  systar-  systar-  systar-  systar-
    (MyBatis-+)  websocket drivers  ops      system
          \        |       /       |          |
           \       |      /        |          |
          systar-monitor-core     systar-data  systar-data
                      |           systar-common systar-common
               systar-common（工具）
```

### 2.3 关键设计决策

**DIP 隔离（依赖倒置）**：monitor-core 定义 Repository 接口（SampleRepository, AlarmRepository, LinkageRepository, ScheduledTaskRepository），systar-data 提供实现。monitor-core 不依赖 systar-data，确保核心引擎可独立使用。

**数据访问模式**：项目采用三种数据访问方式，各模块按职责选择：

| 模式 | 使用者 | 适用场景 |
|------|--------|---------|
| MyBatis-Plus (DIP) | systar-data 实现 monitor-core 接口 | 核心IoT数据（alarm/sample/linkage），通过 DIP 与核心引擎解耦 |
| 自有 MyBatis-Plus | systar-ops, systar-system | 各自管理自己的 entity/mapper，模块独立演进 |
| 原始 JdbcTemplate | systar-server 的 AssetRepository | 资产 CRUD 需要跨表事务和批量操作，不适合 MyBatis-Plus 单表映射 |

**两阶段分发**：ResultDispatcher 先同步预处理（归一化、SpEL 转换、告警条件评估、状态更新），再通过 Spring ApplicationEvent 异步通知下游处理器。

**适配器模式**：DatabaseDialect 接口 + MySQLDialect/H2Dialect 实现，通过 `systar.database.type` 配置切换。

---

## 2B. 数据访问层策略

### 2B.1 双轨数据访问层

项目存在两种数据访问方式，各有适用范围：

| 方式 | 适用模块 | 何时选用 |
|------|----------|----------|
| **MyBatis-Plus**（Entity + Mapper + Service） | systar-data、systar-ops、systar-system | 标准 CRUD、单表操作、查询条件简单 |
| **JdbcTemplate**（手写 SQL + RowMapper） | systar-server/AssetRepository | 多表联查、复杂映射、需要精确控制 SQL |

### 2B.2 边界规则

**核心原则：让每种数据访问方式待在该待的地方。**

```
                        systar-server（启动入口、控制器）
                       /           |              \
            ServerLifeCycle    AssetRepository    Controllers
            XmlAssetTypeLoader  (JdbcTemplate)    (→ Service 层)
                   |                                    |
          必须通过 Service/Repository 接口        必须通过 Service 层
                   |                                    |
                   ▼                                    ▼
              systar-data（MyBatis-Plus 的主场）
              Entity → Mapper → Service/Repository
```

**必须遵守：**

1. **systar-server 不直接注入 Mapper**：ServerLifeCycle、XmlAssetTypeLoader 等需要数据库访问时，必须通过 systar-data 提供的 Service 或 Repository 接口。如果现有接口不满足需求，在 systar-data 中新增方法，而非绕过。

2. **systar-data 内部使用 MyBatis-Plus**：Entity、Mapper、QueryWrapper 是数据层的标准工具。Repository 实现封装 Mapper，向上暴露领域接口。

3. **systar-ops 和 systar-system 保持 Service→Mapper 模式**：这两个扩展模块是独立的垂直切片（巡检/台账/工单、用户/角色/菜单），内部使用 MyBatis-Plus 的标准 CRUD 模式是正确且高效的。

4. **复杂查询可用 JdbcTemplate**：AssetRepository 处理多表联查（资产树加载涉及 space + device + service + probe + control 五表），使用 JdbcTemplate 的手写 SQL 比 MyBatis-Plus 的 QueryWrapper 更清晰可控。

### 2B.3 Schema 管理规则

DDL 和种子数据按数据库方言组织在 `sql/` 目录下，通过 `DatabaseDialect` 适配器加载：

```
sql/
├── h2/
│   ├── ddl/01-core.sql      — IoT 核心表
│   ├── ddl/02-ops.sql       — 运维表
│   ├── ddl/03-system.sql    — 系统表
│   ├── data/01-init.sql     — 种子数据
│   └── data/02-demo.sql     — 演示数据
├── mysql/
│   ├── ddl/01-core.sql
│   ├── ddl/02-ops.sql
│   ├── ddl/03-system.sql
│   ├── data/01-init.sql
│   ├── data/02-demo.sql
│   ├── init.bat             — 生产环境初始化脚本
│   └── init.sh
```

每次修改表结构时，必须同步 H2 和 MySQL 两个方言的对应文件。

> 各模块测试通过 `file:` 前缀引用 `sql/` 下的文件，无需维护子集副本。

---

## 3. 核心数据流

```
                    ┌──────────────────────────────────────────┐
                    │            MonitorScheduler              │
                    │  为每个 Active Monitor 创建 DetectTask    │
                    └──────────────┬───────────────────────────┘
                                   │ 按采集间隔调度
                                   ▼
                    ┌──────────────────────────────────────────┐
                    │              DetectTask                   │
                    │  1. monitor.detect(result)  采集数据      │
                    │  2. resultDispatcher.dispatch(result)     │
                    └──────────────┬───────────────────────────┘
                                   │
                    ┌──────────────▼───────────────────────────┐
                    │          ResultDispatcher                  │
                    │  同步阶段:                                │
                    │   · 值归一化                              │
                    │   · SpEL 表达式转换                       │
                    │   · 告警条件评估                          │
                    │   · 资产状态更新 + 父节点传播              │
                    │  异步阶段:                                │
                    │   · 发布 MonitorResultEvent               │
                    └─────┬──────┬───────┬──────┬───────────────┘
                          │      │       │      │
              ┌───────────▼┐  ┌──▼────┐ ┌▼────┐ ┌▼──────────────┐
              │AlarmHandler│  │Linkage│ │持久化│ │ResultPusher   │
              │ 告警匹配    │  │Handler│ │Handler│ │WebSocket推送 │
              │ 告警持久化  │  │联动匹配│ │数据库│ │浏览器客户端  │
              └────────────┘  │命令下发│ └─────┘ └──────────────┘
                              └───────┘
```

被动模式数据流：外部数据源 → PassiveService.receive() → ResultDispatcher.dispatch() → 后续同上。

---

## 4. 数据库设计概览

### 4.1 表分组

**资产配置表：**
- `t_space` — 空间（支持无限层级嵌套）
- `t_device` — 设备（挂载在空间下）
- `t_service` — 服务/协议驱动连接
- `t_probe` — 监测点（只读数据采集）
- `t_control` — 控制点（可执行命令）
- `t_asset` — 统一资产视图（联表，kind 区分类型，含 space_id/device_id/service_id/probe_id/control_id）
- `t_asset_attribute` — 通用 KV 扩展属性表（替代旧的 t_device_attribute）
- `t_asset_type_config` — 驱动 XML 类型定义的数据库投影

**采样数据表（按数据类型分表）：**
- `t_sample_float` — 浮点型采样值
- `t_sample_int` — 整型采样值
- `t_sample_boolean` — 布尔型采样值
- `t_sample_exception` — 异常信息

**告警表：**
- `t_alarm_rule` — 告警规则配置
- `t_alarm_message` — 告警消息记录
- `t_alarm_correlation_rule` — 告警关联规则
- `t_alarm_escalation_policy` — 告警升级策略
- `t_alarm_silence_window` — 告警静默窗口
- `t_error_message_log` — 告警错误日志
- `t_event_rank` — 告警等级定义

**联动表：**
- `t_linkage_rule` — 联动规则主表（名称、触发类型 MONITOR/ALARM、启用状态）
- `t_linkage_rule_cause` — 联动原因（触发条件：监测器ID + 触发值）
- `t_linkage_rule_effect` — 联动效果（执行动作：目标控制器ID + 命令）
- `t_linkage_log` — 联动执行日志（规则ID、原因/效果监测器、执行结果）

**运维表：**
- `t_work_order` — 工单（告警关联、状态流转、处理记录）
- `t_work_order_log` — 工单处理日志
- `t_work_order_attachment` — 工单附件
- `t_device_attribute` — 设备扩展属性（台账）
- `t_maintenance_record` — 维护记录
- `t_maintenance_attachment` — 维护附件
- `t_inspection_plan` — 巡检计划
- `t_inspection_plan_device` — 巡检计划关联设备
- `t_inspection_item_template` — 巡检项模板
- `t_inspection_task` — 巡检任务
- `t_inspection_result` — 巡检结果

**系统管理表：**
- `t_sys_user` — 用户
- `t_sys_role` — 角色
- `t_sys_menu` — 菜单/权限
- `t_sys_dept` — 部门
- `t_sys_notice` — 通知公告
- `t_sys_oper_log` — 操作日志
- `t_sys_user_role` — 用户-角色关联
- `t_sys_role_menu` — 角色-菜单关联

**定时任务表：**
- `t_scheduled_task` — 定时控制任务定义（cron 表达式 + 目标 Control + 命令）
- `t_scheduled_task_log` — 任务执行日志（成功/失败、耗时、错误信息）

**系统配置表：**
- `t_system_setting` — 系统配置（键值对）
- `t_code_catalog` / `t_code_dict` — 代码字典

### 4.2 核心关系

```
t_space (parent→self)
  └── t_device (parent→t_space)
        └── t_service (parent→t_device)
              ├── t_probe (source→t_service)
              └── t_control (source→t_service)

t_probe/t_control
  ├── t_sample_float/int/boolean/exception (monitor→probe/control)
  └── t_alarm_rule (assetId→probe/control)

t_linkage_rule (causeType: MONITOR/ALARM)
  ├── t_linkage_rule_cause (ruleId→t_linkage_rule, assetId→probe/control)
  └── t_linkage_rule_effect (ruleId→t_linkage_rule, assetId→control)
```

### 4.3 数据库适配

MySQL 和 H2 使用独立 SQL 脚本文件，按方言和模块组织在 `sql/` 目录下：
- `sql/{h2,mysql}/ddl/01-core.sql`、`02-ops.sql`、`03-system.sql` — DDL
- `sql/{h2,mysql}/data/01-init.sql`、`02-demo.sql` — 种子数据
- `sql/mysql/init.sh`、`sql/mysql/init.bat` — 生产环境初始化脚本

Spring Boot dev profile 通过 `DatabaseDialect` 适配器（H2Dialect/MySQLDialect）加载，测试配置通过 `file:` 前缀引用。
H2 脚本需去除 `ENGINE=InnoDB`、`COLLATE`、`COMMENT`，用 `MERGE INTO` 替代 `ON DUPLICATE KEY UPDATE`。

---

## 5. API 设计概览

### 5.1 REST API

统一响应格式 `Result<T>`，API 按模块分前缀：

**`/api/monitor` — IoT 监控**

| 分组 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 资产树 | `/tree` | GET | 完整资产树 |
| 资产 | `/assets` | GET/POST | 资产列表/新增 |
| 资产 | `/assets/{id}` | GET/PUT/DELETE | 资产详情/更新/删除 |
| 资产 | `/assets/{id}/start\|stop\|enable\|disable` | PUT | 运行时启停控制 |
| 类型 | `/asset-types` | GET | 资产类型定义列表 |
| 实时数据 | `/probe-values` | GET | 监测点实时值（ids 参数） |
| 历史数据 | `/probe-history` | GET | 历史数据（分页） |
| 控制 | `/control/{id}/execute` | POST | 执行控制命令 |
| 告警 | `/alarm-rules` | GET | 告警规则列表 |
| 告警 | `/alarm-messages` | GET | 告警消息（分页） |
| 告警关联 | `/correlation-rules` | GET/POST/PUT/DELETE | 告警关联规则 CRUD |
| 告警升级 | `/escalation-policies` | GET/POST/PUT/DELETE | 告警升级策略 CRUD |
| 联动 | `/linkage-rules` | GET/POST | 联动规则列表/新增 |
| 联动 | `/linkage-rules/{id}` | GET/PUT/DELETE | 联动规则详情/更新/删除 |
| 联动 | `/linkage-rules/{id}/toggle` | PUT | 启用/禁用联动规则 |
| 定时任务 | `/scheduled-tasks` | GET/POST | 定时任务列表/新增 |
| 定时任务 | `/scheduled-tasks/{id}` | GET/PUT/DELETE | 定时任务详情/更新/删除 |
| 看板 | `/dashboard` | GET | 数据看板聚合数据 |

**`/api/ops` — 运维业务**

| 分组 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 工单 | `/work-orders` | GET/POST/PUT/DELETE | 工单 CRUD |
| 台账 | `/device-ledger` | GET/POST/PUT/DELETE | 设备台账 CRUD |
| 巡检 | `/inspection` | GET/POST/PUT/DELETE | 巡检管理 CRUD |
| 统计 | `/statistics` | GET | 多维度统计报表 |
| 趋势 | `/trend` | GET | 趋势图数据（自适应粒度） |
| 分析 | `/analysis` | GET | 异常检测与健康评估 |

**`/api/sys` — 系统管理**

| 分组 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 用户 | `/user` | GET/POST/PUT/DELETE | 用户管理 |
| 角色 | `/role` | GET/POST/PUT/DELETE | 角色管理 |
| 菜单 | `/menu` | GET/POST/PUT/DELETE | 菜单管理 |
| 部门 | `/dept` | GET/POST/PUT/DELETE | 部门管理 |
| 通知 | `/notice` | GET/PUT | 通知公告 |
| 日志 | `/log` | GET | 操作日志 |
| 数据保留 | `/data-retention` | GET/PUT | 数据保留策略 |

**`/api/auth` — 认证**

| 分组 | 端点 | 方法 | 说明 |
|------|------|------|------|
| 登录 | `/login` | POST | JWT 登录认证 |

### 5.2 WebSocket

端点：`ws://localhost:8081/ws/monitor`

**订阅：**
```json
{"action": "subscribe", "monitorIds": [1, 2, 3]}
```

**推送（监测值）：**
```json
{"monitorId": 1, "value": 25.3, "status": "NORMAL", "sampleTime": 1715347200000}
```

**推送（告警消息）：**
```json
{"type": "alarm", "id": 123, "monitorId": 1, "level": "WARNING", "message": "温度过高", "state": "ACTIVE"}
```

---

## 6. 启动生命周期

### 6.1 PID 文件

在 `SystarApplication.main()` 第一步就写入 `temp/backend.pid`（通过 `ProcessHandle.current().pid()`），确保无论从脚本还是 IDE 启动，PID 文件都立即可用。JVM 关闭时通过 `@PreDestroy` 自动删除。

### 6.2 服务启动

由 `ServerLifeCycle` 编排，有序执行：

```
1. 加载系统配置（t_system_setting → SystemConfigManager）
2. 加载代码字典（t_code_dict/t_code_catalog → CodeDictManager）
3. 加载资产类型定义（AssetTypeLoader → AssetTypeManager → t_asset_type_config）
4. 加载资产树（数据库 → DatabaseAssetLoader → AssetStore）
   顺序：Space → Device → Service → Probe → Control
   加载后执行 bindProperties() 将 metadata 绑定到驱动 setter
5. 加载告警规则（t_alarm_rule → AlarmHandler）
6. 加载联动规则（t_linkage_rule_cause/effect → LinkageHandler）
7. 加载定时控制任务（t_scheduled_task → TimeControlService）
8. 启动数据保留策略调度（DataRetentionService）
9. 启动 MonitorServer
   a. 启动被动服务（PassiveService）
   b. 注册被动监测点路由
   c. 启动调度器（MonitorScheduler）
   d. 启动告警关联服务（AlarmCorrelationService）
```

关闭时反向：停止调度 → 排空告警/联动队列 → 释放连接。

---

## 7. 协议驱动扩展指南

### 7.1 驱动架构

每个协议驱动实现 `MonitorService`（主动）或 `IPassiveMonitor`（被动）接口：

- **主动驱动**：继承 `ActiveService`，实现 `createConnection()` 创建 `MonitorConnection`
- **被动驱动**：继承 `PassiveService`，实现数据接收和解析

### 7.2 添加新驱动步骤

1. **创建驱动类**：在 `systar-monitor-drivers` 的对应包下创建
2. **实现接口**：
   - 主动驱动：继承 `ActiveService`，实现 `createConnection()`
   - 被动驱动：继承 `PassiveService`，实现 `receive()` 数据处理
3. **连接管理**：实现 `MonitorConnection` 接口（connect/disconnect/detect）
4. **配置数据**：在 `t_service` 表中注册 `driver_class` 全限定类名
5. **单元测试**：编写驱动测试

### 7.3 现有驱动参考

| 驱动 | 类型 | 协议/库 | 模式 | 参考实现 |
|------|------|---------|------|----------|
| Modbus TCP | 工业协议 | jamod | 主动 | ModbusConnection |
| OPC UA | 工业协议 | Eclipse Milo 0.6.8 | 主动 | OpcUaDriver |
| BACnet | 建筑自动化 | BACnet4J 4.1.6 | 主动 | BacnetConnection |
| SNMP | 网络协议 | SNMP4J 3.7.7 | 主动 | SnmpDriver |
| Siemens S7 | PLC | S7Connector 2.1 | 主动 | S7Connection |
| IEC 104 | 电力协议 | j60870 1.2.0 | 主动 | Iec104Connection |
| Weather | HTTP API | java.net.http | 主动 | WeatherService |
| UPS | SNMP | SNMP4J 3.7.7 | 主动 | UpsConnection |
| Environmental | 环境监测 | ServerSocket | 被动 | EnvironmentalDriver |
| WebSocket | 通用 | Java-WebSocket 1.5.4 | 被动 | WebSocketDriver |
| TCP/IP | 通用 | Socket | 主动 | TcpIpConnection |
| MQTT | IoT 消息 | Eclipse Paho 1.2.5 | 被动 | MqttService |
| Simulate | 模拟数据 | — | 主动 | SimulateDriver |
| Manual Input | 手动输入 | — | 被动 | InputService |

---

## 8. 设计模式应用

| 模式 | 应用场景 |
|------|----------|
| Composite | Asset → CompoundAsset(容器) / Monitor(叶子) 树形结构 |
| Visitor | AssetVisitor<T> 类型安全的资产树遍历 |
| Strategy | AlarmStrategy 三种告警策略 |
| Observer | Spring ApplicationEvent 结果分发 |
| Template Method | Monitor.detect(), MonitorService.start()/stop() |
| Facade | MonitorServer 统一入口 |
| Adapter | DatabaseDialect 数据库适配 |
| Factory Method | ActiveService.createConnection() |
| Registry | AssetStore, AssetTypeManager |

详细技术选型和架构策略参见 [技术概览](../tech_overview.md)。
