# P1: VirtualProbe — 计算/派生字段引擎

## 概述

VirtualProbe 是一种特殊的 Probe，其值由 SpEL 表达式计算得出，而非从物理数据源采集。运维工程师通过配置表达式（如 `#probe[101].value / #probe[102].value * 100`）即可定义 KPI 派生值，无需开发人员编写硬编码逻辑。

## 架构

```
Probe (物理) ──detect──▶ MonitorResult ──event──▶ VirtualProbeEngine
                                                    │
                                                    ▼
                                              VirtualProbe.recalculate()
                                                    │
                                                    ▼
                                              SpEL evaluate(#probe[id].value)
                                                    │
                                                    ▼
                                              ResultDispatcher.dispatch()
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `VirtualProbe` | 继承 Probe，通过 SpEL 表达式计算派生值 |
| `VirtualProbeType` | 继承 ProbeType，持有 expression 和 dependsOn 配置 |
| `VirtualProbeEngine` | @Component，监听 MonitorResultEvent，触发依赖 VirtualProbe 重计算 |
| `ProbeRef` | record，SpEL 表达式中 `#probe[id].value` 的访问载体 |
| `RestrictedSpelContext` | 安全沙箱，禁止类型引用、构造器调用、任意方法调用 |
| `RestrictedMethodResolver` | 仅允许 ProbeRef.value() 访问的白名单方法解析器 |

### 安全模型

SpEL 表达式在受限上下文中执行：
- **禁止** `T(java.lang.Runtime)` — 类型引用被阻止
- **禁止** `new java.lang.StringBuilder()` — 构造器调用被阻止
- **禁止** 任意方法调用 — 仅允许 ProbeRef 的访问器方法
- **禁止** `@bean` — Bean 引用被阻止
- **允许** 算术运算、比较运算、变量引用 (`#probe`, `#value`)

### 循环依赖防护

VirtualProbeEngine 使用 `computing` 集合（ConcurrentHashMap.newKeySet）检测循环：
1. 依赖 Probe 更新 → 触发 VirtualProbe A 重计算
2. A 的 ID 加入 computing 集合
3. 若 A 的结果又触发 B，B 的 ID 也加入 computing
4. 若 B 依赖 A，而 A 已在 computing 中 → 跳过，防止无限递归

## 数据库

### t_probe 表新增列

| 列 | 类型 | 说明 |
|----|------|------|
| is_virtual | TINYINT DEFAULT 0 | 是否虚拟探头 |
| expression | VARCHAR(500) | SpEL 表达式 |
| depends_on | VARCHAR(255) | 逗号分隔的依赖 Probe ID |

MySQL 和 H2 DDL 已同步更新。

## REST API

VirtualProbe 通过现有 Probe CRUD API 创建/更新，无需新增端点：

- **创建**: `POST /api/monitor/assets`，properties 中包含 `isVirtual=1`, `expression`, `dependsOn`
- **更新**: `PUT /api/monitor/assets/{id}`，同上
- **查询**: `GET /api/monitor/assets/{id}`，MonitorAssetVO 中包含 `isVirtual`, `expression`, `dependsOn` 字段

### 验证规则

- 当 `isVirtual=1` 时，`expression` 不能为空
- 当 `isVirtual=1` 时，`dependsOn` 不能为空

## 前端

在运维管理页面（operations/index.vue）的 PROBE 表单中添加：
- **虚拟探头** 开关 (el-switch)
- **表达式** 文本域 (el-input textarea)，如 `#probe[101].value / #probe[102].value * 100`
- **依赖探头ID** 输入框，如 `101,102`

详情卡片显示虚拟探头标签、表达式和依赖信息。

## 表达式语法

| 语法 | 说明 | 示例 |
|------|------|------|
| `#probe[id].value` | 引用其他 Probe 的当前值 | `#probe[101].value` |
| 算术运算 | 加减乘除 | `#probe[101].value * 0.1` |
| 比较运算 | 返回 Boolean | `#probe[101].value > 30` |
| 字符串比较 | 等值判断 | `#value == 'on'` |

## 测试覆盖

| 测试类 | 用例数 | 覆盖内容 |
|--------|--------|----------|
| VirtualProbeTest | 21 | detect(), compileExpression(), parseDependsOn(), getKind(), visitor, accessors |
| VirtualProbeEngineTest | 8 | register, unregister, onMonitorResult, circular dependency |
| SpelExpressionEvaluatorTest | 15 | compile, evaluate, probe reference, security restrictions |
