# XML 资产类型配置设计说明

## 概述

资产类型定义通过 XML 配置文件加载，由 `XmlAssetTypeLoader` 在应用启动时解析并注册到 `AssetStore`。

## 文件结构

```
config/assets/
  ├── Assets.xml            ← 主索引，引用所有配置文件
  ├── GenericSpaces.xml     ← Space 类型定义
  ├── GenericDevices.xml    ← Device 类型定义
  ├── ModbusServices.xml    ← Modbus Service 类型定义
  ├── ModbusProbes.xml      ← Modbus Probe 类型定义
  ├── ModbusControls.xml    ← Modbus Control 类型定义
  ├── SnmpServices.xml      ← SNMP Service 类型定义
  ├── SnmpProbes.xml        ← SNMP Probe 类型定义
  └── ...                   ← 其他协议的 Service/Probe/Control 配置
```

## 元素命名约定（关键规则）

**子元素名必须使用标准名称**，通过 `Name` 属性区分具体类型：

| 资产类别 | 根元素 | 子元素名 | Name 示例 |
|----------|--------|----------|-----------|
| Space | `<Spaces>` | `<Space>` | `Building`, `Floor`, `Room` |
| Device | `<Devices>` | `<Device>` | `GeneralDevice` |
| Service | `<Services>` | `<Service>` | `ModbusService`, `SnmpService` |
| Probe | `<ProbeList>` | `<Probe>` | `ModbusProbe`, `SnmpProbe` |
| Control | `<ControlList>` | `<Control>` | `ModbusControl` |

**错误示例**（会导致类型被静默跳过）：
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

## 主索引格式 (Assets.xml)

```xml
<Assets xmlns="http://www.systar.com/AssetConfig">
    <SpaceConfig>GenericSpaces</SpaceConfig>
    <DeviceConfig>GenericDevices</DeviceConfig>
    <ServiceConfig>ModbusServices</ServiceConfig>
    <ServiceConfig>SnmpServices</ServiceConfig>
    <ProbeConfig>ModbusProbes</ProbeConfig>
    <ProbeConfig>SnmpProbes</ProbeConfig>
    <ControlConfig>ModbusControls</ControlConfig>
</Assets>
```

- 根元素为 `<Assets>`，含命名空间 `xmlns="http://www.systar.com/AssetConfig"`（仅主索引文件使用命名空间，子配置文件不使用）

- `<XxxConfig>` 标签名决定加载的资产类别（SpaceConfig → SPACE, ServiceConfig → SERVICE 等）
- 标签内容是不含 `.xml` 后缀的文件名
- 同一类别的多个文件按出现顺序加载（类型继承要求父类型先加载）

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
| `Name` | 类型唯一标识，全局唯一 |
| `Caption` | 显示名称 |
| `Abstract` | `true` 表示抽象类型，不可直接实例化 |
| `Super` | 父类型名称，实现类型继承（属性会从父类型复制） |
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

Space → Device → Service → Probe → Control

这个顺序保证：
1. Probe/Control 的 `Source` 引用的 Service 类型已注册
2. 类型继承链中父类型先于子类型加载（同类别内由主索引的文件顺序保证）

## 诊断日志

`XmlAssetTypeLoader` 会在以下情况输出 WARN 日志：

1. **根元素名不匹配**：配置文件的根元素名与预期不符（如期望 `<Services>` 但实际是其他名称）
2. **零匹配子元素**：配置文件中没有匹配标准元素名的子元素，通常是因为元素名被错误修改（如 `<SnmpService>` 而非 `<Service>`）

## 添加新协议驱动的类型配置

1. 创建 `XxxServices.xml` 和 `XxxProbes.xml`（如有 Control 则再加 `XxxControls.xml`）
2. 在文件中使用标准子元素名（`<Service>`, `<Probe>`, `<Control>`）
3. 通过 `Name` 属性指定具体类型名（如 `Name="XxxService"`）
4. 在 `Assets.xml` 中添加对应的 `<XxxConfig>` 引用
5. 确保 `JavaClass` 指向的驱动类存在
