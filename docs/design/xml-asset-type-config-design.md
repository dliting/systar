# XML 资产类型配置设计说明

## 概述

资产类型定义通过 XML 配置文件加载，由 `XmlAssetTypeLoader` 在应用启动时**扫描**资源路径并解析注册到 `AssetStore`。驱动自带类型定义 XML，放入驱动模块的 resources 即自注册——没有中央索引文件，新增/删除驱动不需要改动任何全局配置。

## 文件位置与扫描路径

类型 XML 按归属放置：

```
core/systar-monitor-drivers/src/main/resources/com/systar/monitor/drivers/
  ├── modbus/modbus-services.xml      ← Modbus Service 类型定义
  ├── modbus/modbus-probes.xml        ← Modbus Probe 类型定义
  ├── modbus/modbus-controls.xml      ← Modbus Control 类型定义
  ├── snmp/snmp-services.xml          ← SNMP Service 类型定义
  └── ...                             ← 其他协议（小写连字符命名 <proto>-{services,probes,controls}.xml）

extensions/systar-server/src/main/resources/config/assets/
  └── generic-devices.xml             ← Device 类型定义（非驱动关注点）
```

`XmlAssetTypeLoader` 扫描以下路径模式（`PathMatchingResourcePatternResolver` 语法）：

1. **内置默认（不可关闭）**：
   - `classpath*:com/systar/monitor/drivers/**/*.xml` — 驱动模块内的全部类型 XML（驱动自注册）
   - `classpath*:config/assets/generic-*.xml` — 服务端的通用 Device 类型
2. **额外路径（可选）**：配置项 `systar.asset-type.scan-paths`，逗号分隔的资源定位模式列表，
   典型用法是 `file:` 外置目录（目录不存在时静默解析为空集，不报错）：

```yaml
systar:
  asset-type:
    scan-paths: file:./config/drivers/*.xml   # 逗号分隔可配多个
```

## 根元素分类与加载顺序

每个 XML 文件按**根元素**确定资产类别，无需在文件名或索引中登记：

| 资产类别 | 根元素 | 子元素名 | Name 示例 |
|----------|--------|----------|-----------|
| Device | `<Devices>` | `<Device>` | `GeneralDevice` |
| Service | `<Services>` | `<Service>` | `ModbusService`, `SnmpService` |
| Probe | `<ProbeList>` | `<Probe>` | `ModbusProbe`, `SnmpProbe` |
| Control | `<ControlList>` | `<Control>` | `ModbusControl` |

**未知根元素直接抛 `AssetException`（fail-fast）**，错误信息含文件名与合法根元素清单——避免文件被静默跳过。

加载顺序固定为 Device → Service → Probe → Control：

1. Probe/Control 的 `Source` 引用的 Service 类型先于它们注册
2. 同一类别内，文件按路径排序加载（确定性），文件内保持文档顺序——`Super` 父类型与子类型写在同一个文件中、父先于子即可

**类型名同类别内唯一**：同一资产类别中，类型名跨所有扫描路径唯一——重名即抛异常（不做覆盖语义，顺序依赖是惊喜源）。不同类别之间同名（如某个 Device 类型与某个 Service 类型同名）不冲突：类型查找、`t_asset_type_config` 同步逻辑键（kind + typeName）、前端查询均按类别隔离。

### 扫描注意事项

- **drivers 资源树是类型 XML 的保留区**：内置模式 `com/systar/monitor/drivers/**/*.xml` 会把该包下的**任何** XML 当作类型配置解析，根元素不合法即启动失败。驱动需要的非类型 XML 资源（模板、映射表等）不要放在这个包路径下。
- **避免扫描路径与内置模式重叠**：同一物理文件若被两个模式以不同 URL 形式命中（如同时匹配 `classpath*:` 与 `file:`），会解析两次并按"类型重名"报错——配置 `scan-paths` 时指向独立目录即可。

## 元素命名约定（关键规则）

**子元素名必须使用标准名称**，通过 `Name` 属性区分具体类型。

**错误示例**（会导致类型被静默跳过并输出 WARN）：
```xml
<Services>
    <SnmpService Name="SnmpService">...</SnmpService>   <!-- 错误 -->
</Services>
```

**正确示例**：
```xml
<Services>
    <Service Name="SnmpService">...</Service>           <!-- 正确 -->
</Services>
```

`XmlAssetTypeLoader` 使用 `root.elements(itemElementName)` 匹配子元素，其中 `itemElementName` 是固定字符串（`"Service"`, `"Probe"` 等）。非标准元素名不会被匹配到。

## 类型定义属性

```xml
<Service Name="SnmpService" Caption="SNMP Service" Abstract="false"
         Super="ParentService">
    <JavaClass>com.systar.monitor.drivers.snmp.SnmpService</JavaClass>
    <DataType></DataType>
</Service>
```

| 属性/子元素 | 说明 |
|-------------|------|
| `Name` | 类型唯一标识，同类别内跨所有扫描路径唯一 |
| `Caption` | 显示名称 |
| `Abstract` | `true` 表示抽象类型，不可直接实例化 |
| `Super` | 父类型名称，实现类型继承（属性会从父类型复制；父类型须在同文件中先声明） |
| `JavaClass` | 驱动实现类的全限定名 |
| `DataType` | 数据类型（保留字段） |
| `Source` | Probe/Control 引用的 Service 类型名 |

## ViewType 与 DataType

每个 Probe/Control 类型及其属性（Property）都可以独立配置 **DataType**（数据类型）和 **ViewType**（展示方式）。

### DataType

定义值的存储格式：

| 值 | 说明 |
|---|---|
| `INT` / `INTEGER` | 整数 |
| `FLOAT` / `DOUBLE` / `NUMBER` | 浮点数 |
| `BOOL` / `BOOLEAN` | 布尔值 |
| `STRING` / `TEXT` | 字符串 |
| `TIMESPAN` / `DURATION` | 时间跨度 |

### ViewType

定义 UI 中的交互展示控件：

| 值 | 说明 |
|---|---|
| `TEXTFIELD` | 单行文本框 |
| `TEXTAREA` | 多行文本框 |
| `LIST` | 下拉列表 |
| `PASSWORD` | 密码输入框 |
| `YESNO` | 布尔开关（复选框） |
| `PERCENT` | 百分比滑块 |
| `SLIDER` | 数值滑块 |

### 自动推断

如果未显式声明 `ViewType`，系统通过 `ViewType.infer(DataType)` 自动推断默认展示方式：

| DataType | 默认 ViewType |
|----------|---------------|
| `BOOLEAN` | `YESNO` |
| `INT` | `TEXTFIELD` |
| `FLOAT` | `TEXTFIELD` |
| `STRING` | `TEXTFIELD` |
| `TIMESPAN` | `TEXTFIELD` |

### 配置位置

**类型级别**（Probe/Control）：
```xml
<Probe Name="ModbusProbe" Caption="Modbus Probe">
    <Source>ModbusService</Source>
    <DataType>BOOLEAN</DataType>
    <ViewType>YESNO</ViewType>     <!-- 可省略，会从 DataType 自动推断 -->
</Probe>
```

**属性级别**（Property 内部）：
```xml
<PropertyList>
    <Property Name="address" Caption="寄存器地址" Default="0" Required="true">
        <DataType Max="65535" Min="0">INT</DataType>
        <ViewType>SLIDER</ViewType>  <!-- 覆盖 INT 默认的 TEXTFIELD -->
    </Property>
    <Property Name="enabled" Caption="启用">
        <DataType>BOOLEAN</DataType>
        <!-- 无 ViewType → 自动推断为 YESNO -->
    </Property>
</PropertyList>
```

## 数据库同步（t_asset_type_config）

扫描结果镜像到 `t_asset_type_config` 表：

- XML 中有、数据库没有 → INSERT（version = 1）
- 两边都有且**内容确实变化**（caption / driverClass / 属性序列化结果任一不同）→ UPDATE，version + 1；内容未变则跳过（版本号只反映真实变更，不随每次启动膨胀）
- 数据库有、XML 中没有 → DELETE（ obsolete 清理）

## 诊断日志

`XmlAssetTypeLoader` 的两类反馈：

1. **fail-fast 异常**（启动失败）：未知根元素（含文件名与合法根元素清单）、类型重名、`Super`/`Source` 引用不存在、XML 解析失败
2. **WARN 日志**：文件内 0 个匹配标准元素名的子元素，通常是因为元素名被错误修改（如 `<SnmpService>` 而非 `<Service>`）

## 添加新协议驱动的类型配置

1. 在驱动模块的包 resources 下创建 `<proto>-services.xml`、`<proto>-probes.xml`（如有 Control 则再加 `<proto>-controls.xml`），放入 `com/systar/monitor/drivers/<proto>/` 目录——**放入即自注册，无需任何登记**
2. 在文件中使用标准子元素名（`<Service>`, `<Probe>`, `<Control>`）
3. 通过 `Name` 属性指定具体类型名（如 `Name="XxxService"`，同类别内跨扫描路径唯一）
4. 确保 `JavaClass` 指向的驱动类存在

## 私有驱动部署（不公开进仓库）

部分用户扩展驱动后不愿公开私有驱动，可通过额外扫描路径实现代码与配置均不落入仓库：

- **XML-only 扩展**（零代码）：在外置目录放类型 XML，`<JavaClass>` 指向**内置**驱动类。例如基于 ModbusProbe 定义站点专属类型并预设属性默认值。启动时配置：
  `systar.asset-type.scan-paths=file:./config/drivers/*.xml`
- **Java + XML 扩展**：私有驱动打成 JAR，经 Spring Boot `PropertiesLauncher` 的 `loader.path` 挂载，其 resources 内的 XML 经内置 `classpath*:` 模式自动发现；或 XML 放外置 `file:` 目录。私有代码与配置均不进仓库。
