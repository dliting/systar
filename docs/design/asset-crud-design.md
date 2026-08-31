# 资产管理 CRUD 设计文档

> **版本**: 1.1
> **日期**: 2026-05-23（更新 2026-05-24）
> **状态**: Phase 1/2 已完成，Phase 3（统一页面）设计完成待实施

---

## 1. 概述

在现有只读资产树基础上，补充完整的 CRUD 链路：前端 UI → REST API → DB 持久化 → Spring Event → 内存同步（含调度器管理）。

系统未上线，无需向后兼容，直接修改现有类。

### 1.1 设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| UI 交互 | 现有资产树页面扩展（方案 A） | 改动最小，与 monitor/control 页体验一致 |
| 覆盖类型 | Space/Device/Probe/Control | Service 较复杂，单独迭代 |
| 数据流 | DB 主写 + Spring Event 内存同步 | DB 是真相源，与现有加载方向一致 |
| 运行时状态 | 三态：Started/Stopped/Disabled | 区分运行时暂停和配置级停用 |
| 安全约束 | 硬约束（禁止不安全操作） | 防止运行时误操作 |
| 级联规则 | 禁用向下级联，启用只恢复 Stopped | 安全优先 |
| 扩展属性 | 通用 KV 表 `t_asset_attribute` | 灵活适应不同业务场景，同类资产可携带不同属性 |
| 批量导入 | 支持 CSV/Excel | 配置期批量录入需求 |
| 使用场景 | 配置期批量录入 + 运行时偶尔调整 | 两者兼顾，按场景区分安全级别 |
| AssetType 机制 | 扩展属性通过 AssetType 定义动态渲染 | 不同业务场景下同类型资产属性不同 |
| 扩展属性编辑 | 只能编辑值，不能增删属性 | 属性由驱动 XML 定义，运行时仅改值 |

**类型加载架构（关键机制）**：

加载链路：`XmlAssetTypeLoader.load()`（类型定义）→ `DatabaseAssetLoader.load()`（实例加载）。

具体机制：
1. **驱动 XML 是类型定义源**：`XmlAssetTypeLoader` 从 classpath `/config/assets/Assets.xml` 读取各驱动/类型 XML，解析 Space/Device/Service/Probe/Control 类型定义、Java 实现类、属性名称/数据类型/默认值、version。
2. **XML 解析后注册到内存**：解析出的类型注册到 `AssetStore` 的各类 `AssetTypeManager`，运行时实例加载通过 `type_name` 在内存查找类型，而非从 DB 重建类型。
3. **XML 同步 DB**：解析后的配置内容/version/属性 schema 同步到 `t_asset_type_config`。DB 表是 XML 的投影，用于资产实例 `type_name` 关联、前端 schema 查询、导入校验和版本比对审计，不是人工维护的主数据源。
4. **XML 变更驱动类型变更**：开发人员修改 XML 中 Probe/Control 的属性类型、名称、默认值、实现类或 version 后，系统重启时重新解析 XML 并同步更新 DB `t_asset_type_config`。
5. **实例属性后加载**：`DatabaseAssetLoader` 按 Space→Device→Service→Probe→Control 创建实例，`AssetEntityConverter` 应用主表字段和类型默认属性，再加载 `t_asset_attribute` 实例 KV 属性覆盖默认值，最后构建树。

**实现进度**：

Phase 1 已完成以下工作：
- `AssetTypeLoader` 接口（systar-monitor-core）+ `XmlAssetTypeLoader` 实现（systar-server）
- 类型通过 `AssetTypeManager` 注册到 `AssetStore`，同步到 `t_asset_type_config`
- `DatabaseAssetLoader` 通过 `AssetEntityConverter` 从 `type_name` 查找注册类型
- `MonitorServer.loadAssets()` 注入 `List<AssetTypeLoader>`，先加载类型再加载实例
- 所有 5 个 Entity 新增 `typeName` 字段，schema 同步更新
- 新建 `AssetAttributeEntity`/`AssetAttributeMapper`（KV 属性表）

Phase 2 已完成：CRUD Service、Event、Listener、Controller 扩展、运行时操作。

### 1.2 扩展属性编辑规则

扩展属性由**驱动 XML**（如 `SimulateProbes.xml`）的 `<PropertyList>` 定义，包括属性名、数据类型、默认值、是否必填。运行时：

- ✅ 可以编辑属性的**值**（如将 `Period` 从 `30` 改为 `60`）
- ❌ 不能动态**增删**属性（属性集合由 XML 定义决定）
- 属性值存储在 `t_asset_attribute` KV 表中
- 前端编辑表单根据 `AssetType.getProperties()` 动态渲染，每个属性按其 `dataType` 使用对应的输入控件

**Systar 适配**（接口倒置）：

为避免 `systar-monitor-core` 反向依赖资源层，使用接口倒置：
- `AssetTypeLoader` 接口定义在 `systar-monitor-core`，接收 `AssetStore` 参数
- `XmlAssetTypeLoader` 在 `systar-server` 实现，作为 Spring Bean 注册
- `MonitorServer.loadAssets()` 注入 `List<AssetTypeLoader>`，逐个调用
- 未来驱动模块可提供自己的 `AssetTypeLoader` Bean 和 XML，无需修改核心代码
- XML 路径：`resources/config/assets/Assets.xml`（主入口），各驱动子文件由主入口引用
- CRUD 创建资产时，用户选择 `typeName` → 后端校验 typeName 已注册且 kind 匹配
- 前端根据 typeName 获取属性定义，动态渲染表单
- `DatabaseAssetLoader` 重构：从 `new ProbeType("probe-" + id)` 改为通过 typeName 查找注册的类型

**类型机制关键设计决策**：

| 设计点 | Systar 设计 | 理由 |
|--------|--------|------|
| 属性存储 | `LinkedHashMap<String, AssetTypeProperty>` | 保留插入顺序（继承属性在前、UI 渲染有序），同时 O(1) 按名称查找 |
| 类型加载注入 | `List<AssetTypeLoader>` Spring 自动注入 | 支持多 Loader 插件式扩展 |
| 类型/实例加载 | 分离为 `AssetTypeLoader`（接口）+ `AssetLoader`（接口） | 职责单一，类型定义和实例加载解耦 |
| DB 同步粒度 | 按类型（一行对应一个 type_name） | 细粒度，便于按类型查询和 CRUD |
| 版本跟踪 | 整数计数器递增 | 简化实现；将来可改为内容哈希 |
| Source 引用 | 存储为字符串，实例加载时通过 `serviceId` 外键关联 | 延迟到实例层解析，避免类型加载顺序耦合 |
| 实例化机制 | `AssetEntityConverter` 内按类型分发 | 当前仅在 Service 使用反射，其他类型用匿名类。将来统一工厂方法 |
| 错误处理 | 严格：所有配置错误和引用缺失均抛 `AssetException` 中断启动 | fail-fast，配置错误尽早暴露 |
| 属性约束 | 仅 `DataType` 枚举 + `required` 标志 | Phase 1 简化；Phase 2 按需扩展 |
| 抽象类型保护 | 无 | 待补 |

**已知缺口修复状态**：

| # | 缺口 | 状态 | 说明 |
|---|------|------|------|
| 1 | 属性继承丢失 `required` 字段 | ✅ 已修复 | `AssetTypeProperty` 新增拷贝构造函数，`loadSuperType` 使用它复制全部字段 |
| 2 | 默认属性值未应用到实例 | ✅ 已修复 | `AssetEntityConverter.applyDefaults()` 在每次转换后遍历 `type.getProperties()` 设置默认值到 metadata |
| 3 | 缺少统一资产工厂 | 待 Phase 2 | 当前 Service 通过反射实例化驱动类；Probe/Control 用匿名类；将来统一 |
| 4 | 缺少 `isAbstract()` 保护 | 待 Phase 2 | 需在 `AssetStore.createAsset()` 或 `AssetEntityConverter` 中添加抽象类型检查 |

**错误处理原则**（已实施）：

采用 fail-fast 模式，所有配置错误和引用缺失均抛 `AssetException` 中断启动：
- 主索引文件缺失 → 抛异常
- 引用的配置文件缺失 → 抛异常
- 匿名类型（无 Name 属性）→ 抛异常
- Super type 未注册 → 抛异常
- 驱动类实例化失败 → 抛异常
- typeName 已配置但未注册 → 抛异常
- serviceId 引用不存在的 Service → 抛异常
- 采集间隔无法解析 → 抛异常
- 仅 typeName 为空（未配置）时允许 fallback 到匿名类型

**需求澄清记录**：

- Probe/Control CRUD 涉及 MonitorScheduler 的 schedule/unschedule/reschedule 操作（MonitorServer 已封装）
- 运行时暂停（stop）不写 DB，重启后自动恢复；配置级停用（disable）写 DB，重启后保持停用
- 禁用 Space/Device 时级联到所有子节点；启用时只恢复 Stopped 状态的子节点，跳过 Disabled 的
- 启用/禁用操作前需弹窗确认，展示级联影响范围
- 扩展属性机制采用通用 KV 表 `t_asset_attribute`，同类资产可携带不同属性
- AssetType 的 properties 定义决定了前端动态表单渲染的字段
- 类型定义源是驱动 XML（非 DB），开发人员修改 XML 中 Probe/Control 的属性类型/名称/默认值/version 后，系统重启时自动同步更新 DB 中的 `t_asset_type_config`
- DB `t_asset_type_config` 是 XML 的投影，用于运行时 `type_name` 关联查询、前端 schema 查询、导入校验和版本审计
- 使用接口倒置（`AssetTypeLoader` 接口 + Spring Bean 注册）实现类型加载的插件式扩展，驱动模块可独立提供自己的 Loader

---

## 2. 后端架构

### 2.1 数据流

```
前端 CRUD 操作
    ↓
AssetController（扩展：加 POST/PUT/DELETE）
    ↓
AssetManagementService（新建：编排 DB + Event）
    ├── 写入主表（t_space/t_device/t_probe/t_control）
    ├── 写入统一表（t_asset）
    ├── 写入扩展属性（t_asset_attribute）
    └── 发布 AssetChangedEvent（Spring Event）
              ↓
AssetCrudListener（新建：监听事件）
    ├── 调用 MonitorServer.addAsset/removeAsset/updateAsset
    ├── Probe/Control: schedule/unschedule/reschedule
    └── PassiveService: registerMonitor/unregisterMonitor
```

### 2.2 现有类变更

| 类 | 变更 |
|----|------|
| `AssetController` | 新增 CRUD 端点 + 运行时操作端点 |
| `MonitorServer` | 新增 `updateAsset()`, `startMonitor()`, `stopMonitor()` |
| `DatabaseAssetLoader` | 提取映射逻辑到 `AssetEntityConverter` |
| `AssetService` / `AssetServiceImpl` | 扩展约束校验 + 级联逻辑 |

### 2.3 新建类及模块归属

| 类 | 模块 | 职责 |
|----|------|------|
| `AssetManagementService` | systar-server | 编排 DB 写入 + 事件发布，事务边界 |
| `AssetChangedEvent` | systar-server | Spring Event record（Action + assetId + kind + asset） |
| `AssetCrudListener` | systar-server | 监听事件，调用 MonitorServer 同步内存 |
| `AssetEntityConverter` | systar-server | Entity→内存对象映射（需同时依赖 systar-data 和 systar-monitor-core） |
| `AssetAttributeEntity` | systar-data | KV 属性表 Entity |
| `AssetAttributeMapper` | systar-data | KV 属性 Mapper |
| `AssetAttributeService` | systar-data | KV 属性 UPSERT |
| `AssetTypeConfigEntity` | systar-data | 类型配置表 Entity |
| `AssetTypeConfigMapper` | systar-data | 类型配置 Mapper |

**说明**：`AssetEntityConverter`、`AssetCrudListener`、`AssetManagementService` 放在 systar-server 模块，因为它们需要同时访问数据层（Entity/Mapper）和监控核心层（AssetStore/MonitorServer）。纯数据层的类放在 systar-data。

### 2.4 事务与事件机制

`AssetManagementService` 使用 `@Transactional`，在事务内同步发布 `AssetChangedEvent`（`ApplicationEventPublisher.publishEvent()`）。`AssetCrudListener` 使用 `@EventListener`（非 `@TransactionalEventListener`），在同一事务内同步执行内存同步。

**为什么用同步而非 AFTER_COMMIT**：
- DB 写入和内存同步要么全成功，要么全回滚
- 避免出现"DB 已提交但内存同步失败"的不一致状态
- 如果内存同步失败（如 `MonitorServer.addAsset()` 抛异常），事务回滚，DB 也不写入

### 2.5 写入顺序与 ID 关联

新增资产时，写入顺序（两阶段加载）：
1. 写入子表（`t_space`/`t_device`/`t_probe`/`t_control`）获取子表 ID，`type` 列引用 `t_asset_type_config.type_name`
2. 写入统一表 `t_asset`，`kind` 标识类型，`spaceId`/`deviceId`/`serviceId` 关联子表
3. 写入扩展属性 `t_asset_attribute`（如有）
4. 发布 `AssetChangedEvent`

**更新资产时**：
1. 更新子表字段
2. 更新 `t_asset` 的通用字段（caption、enabled 等）
3. UPSERT 扩展属性
4. **重新从 DB 读取 Entity** → `AssetEntityConverter` 重建内存对象（通过 typeName 查找 AssetType → 确定驱动类）
5. 发布 `AssetChangedEvent`（携带重建后的 Asset）

**`DatabaseAssetLoader` 重构要点（Phase 1 已完成）**：
- `AssetEntityConverter` 通过 `store.getXxxTypes().find(typeName)` 查找类型，找不到时 fallback 到匿名类型
- `DatabaseAssetLoader` 委托 `AssetEntityConverter` 执行所有转换，加载完成后调用 `loadAllAttributes()` 应用 KV 属性
- 驱动类实例化：Service 通过 `type.getRelatedClass()` 或 `entity.getDriverClass()` 反射创建；Probe/Control 当前用匿名类（待统一工厂方法）

### 2.6 Probe 扩展属性加载链路

对于某种特定 Probe 类型，扩展属性分两层加载：

1. **类型定义层**：启动时通过 `AssetTypeLoader`（如 `XmlAssetTypeLoader`）从驱动 XML 解析 `kind = 'PROBE'` 的类型定义，包含 `type_name`、`driver_class`、`properties`（属性名称/数据类型/默认值/说明）和 `version`。解析后注册到 `AssetTypeManager` 中的对应 `ProbeType`，并同步到 `t_asset_type_config`（DB 投影）。
2. **实例取值层**：加载 `t_probe` 时读取该行的 `type_name`，通过 `AssetTypeManager.find(typeName)` 找到对应 `ProbeType`，创建 Probe 内存对象并应用主表字段。所有资产创建完成后，再读取 `t_asset_attribute`，按 `asset_id` 将实例级 KV 属性写入该 Probe 的 metadata/property map。

最终取值优先级为：`t_asset_attribute` 实例值 > XML 类型定义的 `properties` 默认值 > 驱动内置默认值。驱动代码只消费最终合并后的属性，不应硬编码某个业务场景的扩展字段。

### 2.7 AssetChangedEvent

```java
public record AssetChangedEvent(
    Action action,       // CREATED, UPDATED, DELETED, STARTED, STOPPED, DISABLED, ENABLED
    int assetId,
    AssetKind kind,
    Asset<?> asset       // DELETED 时为 null
) {}
```

### 2.8 MonitorServer 新增方法

```java
// 更新资产：unschedule → 更新内存对象 → reschedule
public void updateAsset(Asset<?> asset);

// 运行时启动（不写 DB，仅 schedule）
public void startMonitor(int id);

// 运行时停止（不写 DB，仅 unschedule）
public void stopMonitor(int id);
```

---

## 3. REST API

所有端点在现有 `/api/monitor` 下。

### 3.1 CRUD 端点

| 端点 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/monitor/assets` | POST | 新增资产 | iot:asset:add |
| `/api/monitor/assets/{id}` | PUT | 修改资产 | iot:asset:edit |
| `/api/monitor/assets/{id}` | DELETE | 删除资产 | iot:asset:delete |
| `/api/monitor/assets/import` | POST | 批量导入（CSV/Excel） | iot:asset:add |

### 3.2 运行时操作端点

| 端点 | 方法 | 说明 | 权限 |
|------|------|------|------|
| `/api/monitor/assets/{id}/start` | PUT | 运行时启动 | iot:asset:start |
| `/api/monitor/assets/{id}/stop` | PUT | 运行时停止 | iot:asset:stop |
| `/api/monitor/assets/{id}/disable` | PUT | 配置级停用（级联） | iot:asset:disable |
| `/api/monitor/assets/{id}/enable` | PUT | 配置级启用（智能级联） | iot:asset:enable |

### 3.3 类型定义端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/monitor/asset-types` | GET | 获取所有资产类型定义 |
| `/api/monitor/asset-types/{kind}` | GET | 获取指定类型的属性定义 |

### 3.4 请求体

```json
POST /api/monitor/assets
{
  "kind": "DEVICE",
  "parentId": 10,
  "name": "th_sensor_003",
  "caption": "温湿度传感器-003",
  "attributes": {
    "model": "SHT-40",
    "serialNumber": "SN-2025-0003",
    "customProp": "扩展属性值"
  }
}
```

`attributes` 中的 key：
- **主表字段**（model, serialNumber, vendor...）→ 写入 `t_device` 等主表
- **扩展属性**（不在主表中的 key）→ 写入 `t_asset_attribute` KV 表

---

## 4. 三态模型（Probe/Control/Service）

| 状态 | 调度器 | DB enabled | 重启后 | 含义 |
|------|--------|-----------|--------|------|
| **Started** | 运行中 | true | 自动恢复 | 正常运行 |
| **Stopped** | 已暂停 | true | 自动恢复 | 运行时暂停，不持久化 |
| **Disabled** | 已停用 | false | 保持停用 | 配置级禁用 |

状态转换：
```
Started ──stop──→ Stopped ──start──→ Started
   │                                      ↑
   └──disable──→ Disabled ──start──→ Started
                  Stopped ──disable──→ Disabled
```

Space/Device 不涉及调度器，只有 enabled 开关 + 级联传导。

---

## 5. 级联规则

**禁用（向下级联全部）**：
- 禁用 Space → 递归所有子节点 → Disabled
- 禁用 Device → 递归其下 Service/Probe/Control → Disabled
- 确认弹窗展示影响范围

**启用（智能级联）**：
- 递归向下，Stopped 的 Probe/Control → Started
- Disabled 的子节点跳过
- 确认弹窗："将恢复 N 个，跳过 M 个已停用的"

---

## 6. 硬约束校验

**删除前检查**：
1. 有子资产 → 禁止
2. Probe/Control 正在调度 → 禁止，提示先停止
3. 关联告警规则 → 禁止
4. 关联联动规则 → 禁止
5. 关联工单 → 禁止

**修改约束**：
- Probe/Control 改采集间隔 → 自动 reschedule
- 运行中 Probe 改 dataType → 禁止
- 改 serviceId → 校验目标 service 存在

---

## 7. 数据库变更

### 7.1 t_asset_attribute（通用 KV 扩展属性）

**与 `t_device_attribute`（ops 模块）的关系**：系统未上线，用 `t_asset_attribute` 替代 `t_device_attribute`。`t_device_attribute` 和 `DeviceAttributeEntity` 删除，ops 模块的设备属性操作改用 `AssetAttributeService`（按 asset_id 查询，而非 device_id）。

```sql
CREATE TABLE IF NOT EXISTS t_asset_attribute (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id    INT             NOT NULL,
    attr_key    VARCHAR(100)    NOT NULL,
    attr_value  VARCHAR(500)    NULL,
    attr_type   VARCHAR(10)     NULL    COMMENT 'STRING/NUMBER/DATE',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (asset_id, attr_key)
);
CREATE INDEX i_aa_asset ON t_asset_attribute (asset_id);
```

### 7.2 t_asset_type_config（类型定义配置）

```sql
CREATE TABLE IF NOT EXISTS t_asset_type_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    kind            VARCHAR(20)     NOT NULL,
    type_name       VARCHAR(100)    NOT NULL,
    caption         VARCHAR(200)    NULL,
    driver_class    VARCHAR(255)    NULL        COMMENT '驱动实现类全限定名（Service/Probe/Control 有效）',
    properties      TEXT            NULL        COMMENT 'JSON: [{name,dataType,defaultValue,description}]',
    version         INT             NOT NULL DEFAULT 1,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (kind, type_name)
);
```

`properties` JSON 示例：
```json
[
  {"name": "host", "dataType": "STRING", "defaultValue": "127.0.0.1", "description": "设备IP地址"},
  {"name": "port", "dataType": "NUMBER", "defaultValue": "502", "description": "端口号"},
  {"name": "unitId", "dataType": "NUMBER", "defaultValue": "1", "description": "从站ID"}
```

### 7.3 现有表变更：新增 `type_name` 列

资产的 `type_name` 列引用 `t_asset_type_config` 的类型名，决定驱动实例化。原 `t_probe`、`t_control`、`t_device`、`t_space`、`t_service` 均缺少此列。

**新增列**（所有 5 张子表）：

```sql
ALTER TABLE t_probe    ADD COLUMN type_name VARCHAR(100) NULL COMMENT '引用 t_asset_type_config.type_name';
ALTER TABLE t_control  ADD COLUMN type_name VARCHAR(100) NULL;
ALTER TABLE t_device   ADD COLUMN type_name VARCHAR(100) NULL;
ALTER TABLE t_space    ADD COLUMN type_name VARCHAR(100) NULL;
ALTER TABLE t_service  ADD COLUMN type_name VARCHAR(100) NULL;
```

**Entity 变更**：`ProbeEntity`、`ControlEntity`、`DeviceEntity`、`SpaceEntity`、`MonitorServiceEntity` 各新增 `private String typeName;` 字段。

**`DatabaseAssetLoader` 变更**：加载时从 `typeName` 查找 `AssetTypeManager` 中注册的类型，替代当前的匿名类型创建。

---

## 8. 前端变更

### 8.1 资产管理页 (`frontend/src/views/asset/index.vue`)

**新增功能**：
- 树面板顶部：`+ 新增` 按钮（下拉选择类型）
- 详情区右上角：`编辑` / `删除` 按钮
- 右键菜单（需扩展 TreePanel 组件，增加 `@node-contextmenu` 事件支持）：新增子项 / 编辑 / 删除 / 启停操作
- 新增/编辑弹窗：固定字段 + 根据 AssetType 动态渲染扩展属性
- 删除确认弹窗：展示约束检查结果
- 启用/禁用确认弹窗：展示级联影响范围
- 批量导入：工具栏"导入"按钮 → 上传 CSV/Excel → 预览校验 → 确认导入

### 8.2 实时监控页 (`frontend/src/views/monitor/index.vue`)

- Probe 行增加操作列：`启动` / `停止` / `停用`

### 8.3 控制执行页 (`frontend/src/views/control/index.vue`)

- Control 详情区增加：`启动` / `停止` / `停用`

### 8.4 前端 API (`frontend/src/api/iot/asset.js`)

新增：createAsset, updateAsset, deleteAsset, startAsset, stopAsset, disableAsset, enableAsset, importAssets, getAssetTypes

### 8.5 新建组件

- `frontend/src/components/AssetForm/` — 动态资产表单（根据类型渲染字段）

---

## 9. 涉及的关键文件

| 文件 | 变更 |
|------|------|
| `extensions/systar-server/.../controller/AssetController.java` | 修改：新增 CRUD + 运行时端点 |
| `extensions/systar-server/.../loader/DatabaseAssetLoader.java` | 修改：复用 Converter |
| `core/systar-monitor-core/.../server/MonitorServer.java` | 修改：新增 updateAsset/startMonitor/stopMonitor |
| `core/systar-data/.../service/AssetService.java` | 修改：扩展业务方法 |
| `core/systar-data/.../service/impl/AssetServiceImpl.java` | 修改：约束校验 + 级联 |
| 新建 `AssetManagementService.java` | 编排 DB + Event |
| 新建 `AssetChangedEvent.java` | Spring Event |
| 新建 `AssetCrudListener.java` | 事件监听 + 内存同步 |
| 新建 `AssetEntityConverter.java` | Entity→内存对象映射 |
| 新建 `AssetAttributeEntity.java` | KV 属性表 Entity |
| 新建 `AssetAttributeMapper.java` | KV 属性 Mapper |
| 新建 `AssetAttributeService.java` | KV 属性 UPSERT |
| 新建 `AssetTypeConfigEntity.java` | 类型配置 Entity |
| 新建 `AssetTypeConfigMapper.java` | 类型配置 Mapper |
| `schema/schema-mysql.sql` | 新增 t_asset_attribute、t_asset_type_config |
| `schema/schema-h2.sql` | 同步 H2 版本 |
| `data-mysql.sql` / `data-h2.sql` | 类型配置种子数据 |
| `frontend/src/api/iot/asset.js` | 新增 CRUD API |
| `frontend/src/views/asset/index.vue` | 新增/编辑/删除/导入 UI |
| `frontend/src/views/monitor/index.vue` | 增加启停按钮 |
| `frontend/src/views/control/index.vue` | 增加启停按钮 |
| 新建 `frontend/src/components/AssetForm/` | 动态资产表单组件 |

---

## 10. 验证方案

1. `./mvnw test -o` — 全量后端单元测试通过
2. CRUD 端点 Controller 单元测试（每种类型的增删改）
3. 级联逻辑测试（禁用 Space → 子节点全停；启用 → 只恢复 Stopped）
4. 约束校验测试（有子资产不能删、调度中不能删、关联告警不能删）
5. 三态转换测试（Started→Stopped→Started, Started→Disabled→Started）
6. 前端 `npm run dev` → 资产管理页 CRUD 全流程
7. 新增 Probe → 自动采集 → 停止 → 暂停 → 启动 → 恢复采集
8. 批量导入 CSV → 预览 → 导入 → 树刷新
9. 扩展属性动态表单渲染测试
10. 回归测试：monitor/control/alarm/linkage 页面功能不受影响

---

## 11. 批量导入（点表导入）

### 11.1 设计来源

批量导入的业务规则源自长期资产导入工具实践沉淀，按 Systar 架构重新实现（REST API 上传 + Event 实时内存同步）。

### 11.2 导入业务规则

| 规则 | Systar 实现 |
|------|------------|
| 导入顺序 | Space → Device → Service → Probe/Control |
| 类型标识 | 不依赖文件名，Excel 内用 `kind` 列标识 |
| ID 生成 | 使用 `AssetIdGenerator`（高16位站点编码+序号） |
| 父级解析 | 通过 parentId 或 parentName 列匹配 |
| Monitor vs Control 判定 | 不硬编码 IO 映射，由驱动/类型配置决定（见下方说明） |
| 扩展属性 | 列标题含 `{propName}` → `t_asset_attribute` |
| 去重检查 | 同文件内 name 不能重复 + 不与现有 DB 资产冲突 |
| 事务 | 整个导入在事务中，失败全回滚 |
| Probe 字段 | 保留全部字段（name, caption, parent, catalog, io, source, timeInterval, saveInterval, transform, alarm, min, max, type），适配新列名。`io` 字段存入 metadata（Entity 无此列） |

### 11.3 Excel 格式规范

每个 Sheet 对应一种资产类型，Sheet 名称标识类型。或使用单 Sheet 格式，首列 `kind` 标识类型。

**单 Sheet 格式（推荐，支持混合类型导入）**：

首行为表头，`kind` 列标识资产类型。列名对应 Entity 字段名。

**Probe/Control 通用列**：

| 列名 | 必填 | 说明 |
|------|------|------|
| kind | 是 | PROBE 或 CONTROL（由驱动/类型配置决定，不硬编码 IO 映射） |
| io | 否 | IO 类型标识（AI/DI/AO/DO 等），具体含义由驱动解释，存入 asset metadata |
| name | 是 | 编号 |
| caption | 否 | 显示名称 |
| parent | 是 | 父级 name（通过 name 匹配已导入数据） |
| catalog | 否 | 分类代码 |
| source | 否 | 关联服务 name |
| timeInterval | 否 | 采集间隔（如 "10s", "5m"） |
| saveInterval | 否 | 保存间隔 |
| transform | 否 | 值转换表达式 |
| alarm | 否 | 告警条件表达式 |
| min | 否 | 最小值 |
| max | 否 | 最大值 |
| type | 否 | 数据类型 |
| `{扩展属性名}` | 否 | 列标题含 `{}` 的写入 KV 表 |

**Space 列**：kind, name, caption, parent
**Device 列**：kind, name, caption, parent, catalog, vendor, model, serialNumber

### 11.4 导入流程

1. **上传文件** → 后端解析（支持 .xlsx/.xls/.csv）
2. **预校验** → 返回校验结果（成功 N 条、失败 M 条、每行失败原因）
   - kind 合法性
   - name 非空且无重复（文件内 + DB 已有）
   - parent 通过 name 可匹配到已导入或已有资产
   - io 与 kind 一致性（如 io=AI 但 kind=CONTROL → 警告，不阻止，因为映射由驱动决定）
   - 必填字段完整性
3. **前端展示预览** → 表格展示每行数据 + 校验状态（通过/失败/警告）
4. **用户确认** → 按顺序导入（Space → Device → Service → Probe/Control）
5. **批量写入** → 每条记录：DB 写入 + 发布 Event 同步内存
6. **返回结果** → 成功/跳过/失败数量

### 11.5 导入功能特性

| 特性 | Systar 实现 |
|------|--------|
| 运行方式 | REST API，前端上传 |
| ID 策略 | AssetIdGenerator |
| 类型判定 | Excel 内 kind 列 |
| 父级引用 | name 匹配（兼容）+ parentId（精确） |
| 内存同步 | 实时同步（Event → AssetStore + Scheduler）|
| 预览校验 | 前端预览 + 后端校验 |
