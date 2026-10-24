# 协议驱动二次开发指南

面向需要为 Systar 新增或定制协议驱动的开发者。内容基于当前代码（`core/systar-monitor-core` 资产框架 + `drivers/` 驱动插件模块），所有机制均可在引用的源文件中核对。

相关文档：

- `docs/design/architecture.md` §7 — 驱动架构总览与现有驱动清单（14 个驱动插件模块；BACnet/IEC 104 两个 GPL 插件目录不在公开仓库，见 `docs/design/mit-relicensing.md`）
- `docs/design/xml-asset-type-config-design.md` — 类型 XML 的完整规格（扫描来源、元素命名、DB 同步）
- `docs/tech_overview.md` — 系统整体技术概览

## 1. 驱动与插件目录的构成

### 1.1 驱动的四类构件

新增一个协议驱动 = 四类构件，全部放在该驱动 Maven 模块（`drivers/systar-driver-<proto>`）的同一个协议包下：

| 构件 | 形态 | 数量 | 作用 |
|------|------|------|------|
| Service 驱动类 | Java（继承 `ActiveService` 或 `PassiveService`） | ≥1 | 管理连接池/监听器，承载服务级配置（地址、端口、超时…） |
| Connection 类 | Java（实现 `MonitorConnection`，仅主动驱动） | 0..1 | 一条通信通道的生命周期与协议读写 |
| Probe / Control 驱动类 | Java（继承 `Probe` / `Control`） | ≥1 | 单个数据点的读取（detect）/ 写入（execute）逻辑 |
| 类型定义 XML | `<proto>-services.xml`、`<proto>-probes.xml`、`<proto>-controls.xml` | ≥2 | 声明类型名、JavaClass、属性清单——**放入即自注册**，无全局索引 |

目录布局（以 `myproto` 为例）：

```
drivers/systar-driver-myproto/src/main/
  java/com/systar/monitor/drivers/myproto/
    MyProtoService.java
    MyProtoConnection.java          ← 主动驱动才有
    MyProtoProbe.java
    MyProtoControl.java             ← 有写入需求才有
  resources/com/systar/monitor/drivers/myproto/
    myproto-services.xml
    myproto-probes.xml
    myproto-controls.xml            ← 可选
```

> **术语**：监测器 = Probe（只读数据点），操控器 = Control（可读可写），监控器 = Monitor（两者基类统称），Service = 承载一组监控器的协议服务实例。

### 1.2 运行时插件目录

构建产物与部署单元是**自包含插件目录**——一个插件 = 一个子目录，目录名即插件身份（本仓库构建以 artifactId 为目录名，如 `systar-driver-modbus`；私有插件可自取短名）：

```
systar-server.jar
drivers/                            ← 与服务器 jar 同级；systar.drivers.path 默认 ./drivers（按工作目录解析）
  systar-driver-modbus/             ← 插件目录（目录名 = 插件身份）
    driver.jar                      ← 驱动类 + 类型 XML（XML 在 jar 内类同包树下）
    lib/*.jar                       ← 私有协议库（snmp4j 等），不 shade、不 relocate
    seed/01-service.csv             ← 可选：演示种子（见 §10）
  my-site-types/                    ← XML-only 插件：无 jar，类型 XML 直接放目录根
    site-hvac.xml
```

- **拷贝即生效**：把插件目录放进 `drivers/` 下、重启服务器即加载；不需要改服务器代码、不需要重新打包服务器。加载顺序 = 目录名排序。
- **XML 放 jar 内而非目录里**：类型 XML 与驱动类同包是固定约定；插件类加载器只枚举**本插件自有** jar 条目、目录根 XML 与包树 `com/systar/monitor/drivers/**` 下的 XML，不会把服务器 classpath 上的他插件 XML 错记到本插件名下。目录根下的 loose XML 是 XML-only 插件的惯例写法，放包树下（与 jar 内同构）同样被识别；`lib/` 下等其他位置的 `*.xml` 会被 WARN 提示忽略。
- **XML-only 插件（零代码扩展）**：目录里没有 `driver.jar`、只有 XML——`JavaClass`/`Source` 可以指向**其他插件**已注册的类/类型（例如基于 modbus 插件的 `ModbusProbe` 定义站点专属类型并预设缺省属性）。这是软依赖：目标插件缺失时该类型按 WARN 跳过注册，不阻断启动。
- **类型名跨插件唯一**：两个插件声明同类别同名类型 = 启动失败，错误消息点名两个插件目录。
- **无 manifest 文件**：插件身份 = 目录名，类型清单 = XML 自身，排错靠启动日志（§14）。

## 2. 核心类型（systar-monitor-core）

| 类型 | 契约 | 源码 |
|------|------|------|
| `MonitorService` | 抽象基类；持有 `MonitorMode`（构造时固定）与监控器列表；声明 `start()`/`stop()` | `asset/MonitorService.java` |
| `ActiveService` | 主动（轮询）服务；内置连接池；抽象工厂 `createConnection()` | `asset/ActiveService.java` |
| `PassiveService` | 被动（推送）服务；内置 `registerKey → Monitor` 路由表；持有 `ResultDispatcher` | `asset/PassiveService.java` |
| `Monitor<T>` | 监控器基类；抽象 `detect(IMonitorResult)`；节流（detectInterval/savingInterval）、表达式（transform/warn）、超时 | `asset/Monitor.java` |
| `Probe` | 只读监控器；`detect()` 默认 no-op，驱动覆盖 | `asset/Probe.java` |
| `Control` | 可写监控器；抽象 `execute(String command)` | `asset/Control.java` |
| `MonitorConnection` | `open()` / `isConnected()` / `close()` | `asset/MonitorConnection.java` |
| `IPassiveMonitor` | `makeRegisterKey()` — 被动路由键 | `asset/IPassiveMonitor.java` |

**类型 vs 实例**：XML 定义的是 *类型*（如 `ModbusTcpMaster`，含属性清单与缺省值），运行时/数据库中的是 *实例*（如 `modbus_svc_hvac`，`t_service` + `t_asset_attribute` + `t_probe`/`t_control` 行）。实例由框架按类型的 `JavaClass` 经 `DriverClassRegistry` 反射创建（`AssetRepository`），驱动作者只写类和类型定义，不写实例化代码。

## 3. 选择通信模型

| | 主动 ACTIVE | 被动 PASSIVE |
|---|---|---|
| 数据流向 | 框架按 detectInterval 轮询驱动 | 外部推送，驱动解析后分发 |
| Service 基类 | `ActiveService` | `PassiveService` |
| 需要实现 | `createConnection()` + `start()`/`stop()` | `start()`/`stop()`（订阅/监听）+ 消息路由 |
| Probe 的 detect | 真正读设备 | 通常只返回缓存值（如 `MqttProbe`） |
| 典型驱动 | Modbus、OPC UA、BACnet、SNMP、S7、IEC 104 | MQTT、Environmental、WebSocket、Input |

**模式由驱动类决定**：`ActiveService` 构造即 ACTIVE、`PassiveService` 构造即 PASSIVE，运行时 `setSource()` 会把 service 的 mode 同步给监控器。UI 创建服务时 `AssetOrchestrator` 依据类型 `relatedClass`（回退请求 `driverClass`）推导默认 mode——驱动类是唯一事实来源，XML 与数据库中的 mode 字段只是推导结果。

## 4. 编写 Service

### 4.1 主动服务骨架（参照 `ModbusService`）

```java
public class MyProtoService extends ActiveService {

    private static final Logger LOG = LoggerFactory.getLogger(MyProtoService.class);

    // 内置兜底缺省：metadata 与 XML Default 都没有时生效
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int    DEFAULT_PORT = 502;

    private String host = DEFAULT_HOST;
    private int    port = DEFAULT_PORT;

    // 跟踪本服务创建过的全部连接，stop 时统一关闭（参照 ModbusService.allConnections）
    private final List<MonitorConnection> allConnections = new ArrayList<>();

    /** 由连接池统一创建；只负责构造，不开连接（池取出时会自动 open/重连）。 */
    @Override
    public MonitorConnection createConnection() {
        MyProtoConnection conn = new MyProtoConnection(host, port);
        synchronized (allConnections) {
            allConnections.add(conn);
        }
        return conn;
    }

    @Override
    public void start() throws Exception {
        // 预开一条连接做配置校验，失败即启动失败（fail-fast）
        MonitorConnection test = createConnection();
        try {
            test.open();
        } finally {
            test.close();
            synchronized (allConnections) {
                allConnections.remove(test);
            }
        }
        LOG.info("MyProto service started: {}:{}", host, port);
    }

    @Override
    public void stop() {
        synchronized (allConnections) {
            for (MonitorConnection conn : allConnections) {
                try {
                    conn.close();
                } catch (Exception e) {
                    LOG.warn("Error closing MyProto connection", e);
                }
            }
            allConnections.clear();
        }
        LOG.info("MyProto service stopped");
    }

    // ==== 配置字段：getter/setter 缺一不可，bindProperties 依赖 setter ====
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
}
```

要点：

- **配置只存字段**。绑定发生在加载/创建/更新时（`bindProperties()`），`start()` 直接读字段，不做任何配置再解析。
- **setter 是绑定的入口**：每个可配属性提供与 XML `Property Name` 对应的 setter（名称大小写不敏感匹配，见 §8）。`ActiveService.setMaxConnections(int)` 已内建，`MaxConnections` 属性无需自写。
- 连接池语义（框架提供，勿重复造）：`getConnection()` 在池未满时调 `createConnection()` 新建，池满时**阻塞等待**；取出时自动检查 `isConnected()`、失效则 `open()` 重连；用完必须 `releaseConnection(conn)`。多个服务共享网关级池时可用 `setConnectionLock()` 对齐锁对象。
- `ModbusService` 额外演示了 `allConnections` 跟踪清单（stop 时统一关闭）与 `getModbusConnection()` 强转捷径——按需参考。

### 4.2 被动服务骨架（参照 `MqttService`）

```java
public class MyProtoService extends PassiveService {

    private String listenAddress;   // setter 略，同上

    @Override
    public void start() throws Exception {
        // 建立订阅/监听；未配置则 WARN 后返回（不抛异常阻断其他服务）
    }

    @Override
    public void stop() {
        // 断开并释放资源；异常 catch 后 WARN，不向外抛
    }

    /** 消息到达：解析 → 找到归属监控器 → 经 ResultDispatcher 分发 */
    private void onMessage(String key, String payload) {
        var dispatcher = getResultDispatcher();          // MonitorServer 启动时注入
        if (dispatcher == null) {
            LOG.warn("No ResultDispatcher; message dropped");
            return;
        }
        Monitor<?> m = getMonitor(key);                  // 基类路由表
        // ... 解析 payload 得 value ...
        dispatcher.dispatch(new MonitorResult(m, value));
    }
}
```

路由有两种做法：

1. **基类路由表**：`MonitorServer` 启动被动服务时，自动把该服务下所有 Probe/Control 以 `String.valueOf(monitorId)` 为 key 注册进 `registerMonitor(key, monitor)`（`MonitorServer.registerPassiveMonitors`）。适合"一个 key 对一个监控器"的场景；也可在驱动内用自定义 key 补充注册。
2. **驱动自有路由**：`MqttService` 维护 `topic → List<MqttProbe>` 自有映射（`MqttProbe.init()` 时向 service 注册 topic），消息按 topic 组播到多个 Probe。适合一条消息多个消费者、或 key 不是监控器 id 的场景。

无论哪种，最终都必须经 `getResultDispatcher().dispatch(new MonitorResult(monitor, value)))` 走统一结果管道（入库、告警、联动都挂在后面）。

## 5. 编写 Connection（仅主动驱动）

实现 `MonitorConnection` 三个方法即可（参照 `ModbusConnection`）：

```java
public class MyProtoConnection implements MonitorConnection {

    @Override
    public void open() throws Exception { /* 建链 + 设置读超时 */ }

    @Override
    public boolean isConnected() { /* 连接池取出时据此决定是否重连 */ }

    @Override
    public void close() { /* 幂等关闭，不抛异常 */ }

    // 协议读写方法（如 readHoldingRegisters / writeCoil）供 Probe/Control 调用
}
```

- 构造参数来自 Service 的已绑定字段（如 `new ModbusConnection(host, port, unitId, timeout)`），超时等参数在构造时固化。
- `close()` 必须幂等且吞掉 IO 异常（或仅记日志）——它会被池和 stop 路径反复调用。
- 协议细节全部收敛在此类与伴生工具类（如 `ModbusValueReader`、`ModbusAddressParser`），Probe 保持薄。

## 6. 编写 Probe / Control

### 6.1 主动 Probe 的 detect 模板（参照 `ModbusProbe`）

```java
public class MyProtoProbe extends Probe {

    @Override
    public void detect(IMonitorResult result) throws Exception {
        MyProtoService service = getService();       // getSource() instanceof 检查
        MyProtoConnection conn = null;
        try {
            conn = service.getMyProtoConnection();   // 从池中取
            Object value = readValue(conn);          // 地址等从 getMetadata(...) 懒解析
            result.setValue(value);
            result.setSampleTime(System.currentTimeMillis());
        } catch (Exception e) {
            result.setError("MyProto read failed: " + e.getMessage());
            if (conn != null) {
                conn.close();                        // 坏连接直接关闭，不还池
            }
            conn = null;
        } finally {
            if (conn != null) {
                service.releaseConnection(conn);     // 好连接归还连接池
            }
        }
    }
}
```

必须遵守的约定：

- **成功路径**：`setValue` + `setSampleTime`；**失败路径**：`setError(消息)`（不要抛出到框架之外再由上层猜）。
- **finally 归还连接**；异常时 `close()` 弃用坏连接而不是归还——否则下一个 detect 会拿到脏连接。
- 实例级地址（寄存器号、节点 ID 等）放 metadata（`t_asset_attribute`），detect 时经 `getMetadata("RegisterAddr")` 懒解析并缓存描述符；类型级 `Source` 是 **Service 类型名**（如 `ModbusMaster`），不是协议地址。
- 框架负责节流（`shouldDetect`，间隔来自实例 `time_interval`，缺省 10 分钟，下限 1 秒）、超时（默认 30 s，`detectTimeoutMs`）、transform/warn 表达式——驱动只返回原始值，不要在 detect 里做告警判断或单位换算（交给类型/实例上的表达式配置）。

### 6.2 Control（参照 `ModbusControl`）

- `detect(result)`：读当前状态（回显值），写法与 Probe 相同（Modbus 用 `InRegisterAddr` 属性）。
- `execute(String command)`：下发命令。命令字符串格式由驱动自定义并写进类型 `Description` 文档化，例如 Modbus 的 `register:40001:value:100`、`coil:1:bool:true`。同样遵循"取连接 → 执行 → 异常关连接并抛出 → finally 归还"。

### 6.3 被动 Probe（参照 `MqttProbe`）

`detect()` 只返回缓存值；真正的取数在 service 回调里（`onMessage(payload)` 解析、更新 `cachedValue`、dispatch）。类型 `Source` 可复用为驱动级寻址描述——MQTT 约定 `topic` 或 `topic:$.jsonPath` 两种格式。

## 7. 类型 XML 三件套

完整规格见 `docs/design/xml-asset-type-config-design.md`，这里只列驱动作者每天用到的核心：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Services>
    <Service Name="MyProtoMaster" Caption="MyProto Master" Abstract="true">
        <JavaClass>com.systar.monitor.drivers.myproto.MyProtoService</JavaClass>
        <PropertyList>
            <Property Name="UnitId" Caption="从站地址" Default="1" Required="true">
                <DataType Min="0" Max="247">INT</DataType>
            </Property>
            <Property Name="Timeout" Caption="超时时间 (ms)" Default="5000" Required="false">
                <DataType Min="100" Max="60000">INT</DataType>
            </Property>
        </PropertyList>
    </Service>

    <Service Name="MyProtoTcpMaster" Caption="MyProto TCP Master" Super="MyProtoMaster">
        <JavaClass>com.systar.monitor.drivers.myproto.MyProtoService</JavaClass>
        <PropertyList>
            <Property Name="Host" Caption="地址" Default="127.0.0.1" Required="true">
                <DataType MaxLength="255">STRING</DataType>
            </Property>
            <Property Name="Port" Caption="端口号" Default="502" Required="true">
                <DataType Min="1" Max="65535">INT</DataType>
            </Property>
        </PropertyList>
    </Service>
</Services>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ProbeList>
    <Probe Name="MyProtoFloat" Caption="MyProto浮点输入">
        <JavaClass>com.systar.monitor.drivers.myproto.MyProtoProbe</JavaClass>
        <DataType>FLOAT</DataType>
        <PropertyList>
            <Property Name="RegisterAddr" Caption="寄存器地址（偏移量）" Required="true">
                <DataType Min="0">INT</DataType>
            </Property>
        </PropertyList>
        <Source>MyProtoMaster</Source>
    </Probe>
</ProbeList>
```

规则速记：

- 根元素定类别（`<Services>`/`<ProbeList>`/`<ControlList>`），子元素名必须是标准名 `<Service>`/`<Probe>`/`<Control>`（写协议前缀名会被静默跳过并 WARN）。
- 文件放入 `com/systar/monitor/drivers/<proto>/` 即自注册；**该包路径下不要放任何非类型 XML**（会被扫描器当类型解析，根元素不合法即启动失败）。
- 类型名同类别内跨所有来源（含跨插件）唯一；`Super` 父类型须同文件先声明（属性复制继承，子类同名属性覆盖）；`Source` 引用的 Service 类型由加载顺序（Service 先于 Probe/Control）保证可解析。`Source` 指向未注册类型（如目标插件缺失、类型名拼错）时按类型 WARN 跳过、不注册该类型，不再启动失败。
- 属性 `Name` 即绑定键——`t_asset_attribute.attr_key` 与 setter 名都以它为准。
- **惯例**：可调超时的服务都应暴露 `Timeout` Property（INT，ms，Default 5000 或协议惯例值，配 Min/Max 边界）；主动服务可暴露 `MaxConnections`（INT，Default 10，Min 1）。启动时类型镜像同步进 `t_asset_type_config`（内容变化才 version+1）。

## 8. 属性绑定机制（bindProperties）

框架在加载、创建、更新资产时调用 `Asset.bindProperties()`（`core/systar-monitor-core/.../asset/Asset.java`），把配置反射进驱动 setter。取值优先级：

```
实例 metadata（t_asset_attribute 运行时覆写）
  > 类型 XML Property Default（设计期缺省）
    > Java 字段初始值（内置兜底，驱动代码里的 = DEFAULT_xxx）
```

匹配与转换规则：

- setter 按 `set + PropertyName` **大小写不敏感**查找；参数类型与 DataType 匹配的 setter 优先。
- 值统一经 `convertValue` 转成 setter 参数类型（`"55502"` → int 55502）。
- 绑定失败（无 setter / 转换失败）记 WARN 不抛异常——不阻断其他属性与其他资产加载。
- **例外**：`t_service.max_connections` 列的优先级高于 XML Default（种子服务 5 覆盖类型缺省 10 即此语义）。

对驱动作者的含义：**字段初始值写协议惯例兜底，XML Default 写产品缺省，实例属性做部署微调**；三层都不写就维持字段初值。测试锚点可参照 `ModbusServiceTest`（三层优先级各有用例）与 `extensions/systar-server` 的 `AssetAttributeBindingTest`（create/update 全链路绑定）。

## 9. 类加载与依赖 scope

每个插件目录得到一个独立的 `URLClassLoader`（URLs = `driver.jar` + `lib/*.jar`），**parent-first**，parent 是加载 `com.systar.monitor.*` 的服务器类加载器。由此得出三条依赖规则：

| 依赖 | scope | 原因 |
|------|-------|------|
| `systar-monitor-core`（框架基类） | **provided**（编译可见、不打进 driver.jar） | 框架类永远来自服务器 parent，`(Probe)`/`(ActiveService)` 跨插件强转才安全；jar 内重复打包会 `ClassCastException` |
| 服务器已有共享库（jackson、slf4j 等） | **provided** | parent-first 下 parent 命中即生效，捆绑进 `lib/` 只是死重；slf4j 由 parent 统一提供，插件不会带出第二个日志后端 |
| 插件私有协议库（snmp4j、paho、s7connector、sdk-client、Java-WebSocket、bacnet4j、j60870…） | compile（进 `lib/`） | 只在各自加载器内可见——**两个插件捆绑同一库的不同版本互不干扰** |

- 类解析顺序：`DriverClassRegistry` 按目录名序在各插件加载器查找，未命中回退服务器 classpath，仍未命中抛 `AssetException` 点名"类 X 被类型 Y 引用，但不在任何已加载插件中"。两个插件含同全限定名类时按目录序取先者。
- **不支持 child-first 覆盖服务器库**（罕见需求，有意省略）。
- **已知限制**：driver.jar 与服务器之间没有版本兼容信号——core API 变更后驱动须重新编译，否则旧驱动配新服务器表现为运行期 `NoSuchMethodError`/`NoClassDefFoundError`（日志点名类与方法）。项目当前不做向下兼容承诺。

## 10. 种子文件（seed CSV）规范

插件可选携带演示种子（服务模板、演示资产、类型字典项），放在插件目录 `seed/` 下，用通用 CSV 而非 SQL（不绑定方言、非专业开发者可编辑）。解析用 commons-csv（Apache-2.0）。

### 10.1 命名与白名单

文件名 `NN-<逻辑名>.csv`：数字前缀 = 执行顺序（`02-asset.csv` 可引用 `01-service.csv` 插入的行），逻辑名映射到表 `t_<逻辑名>`。可播种表白名单：

| 逻辑名 | 目标表 | 用途 |
|--------|--------|------|
| `service` | `t_service` | 服务模板/演示服务实例 |
| `asset` | `t_asset` | 统一资产视图行（树节点） |
| `asset_attribute` | `t_asset_attribute` | 实例属性（metadata） |
| `probe` | `t_probe` | 监测点实例 |
| `control` | `t_control` | 操控器实例 |
| `dictionary` | `t_code_dict` | 类型下拉字典项 |

白名单外的逻辑名启动即报错并列出合法名（防拼写，如 `01-servcie.csv`）。白名单同时限制插件不能往 `t_sys_user`、`t_system_setting` 等核心表塞数据；告警/联动/定时任务/分组树等是运维方的运行数据、设备行不携带驱动实现，都不属于驱动插件的分发内容，故不可播种。

### 10.2 格式与写入语义

- 首行列头 = 数据库列名（英文，与 DDL 一致），**必须含 `id`**；其余列按需取子集。列名不在表上 → 启动失败点名插件/文件/列名。
- 空字段 = NULL（空串无法表达，罕见场景如需空串请改用属性默认值）；编码 UTF-8（容忍 BOM，Excel"UTF-8 CSV"导出可直接用）；解码失败报错点名插件/文件并提示另存为 UTF-8。
- 按主键 **insert-if-absent**：行已存在则跳过（天然幂等，重启重跑安全），按文件汇总一条 WARN 点名冲突 id，逐行明细在 DEBUG。
- 引用其他种子行的行（如 asset 引用 service）必须放在更晚的 NN 文件；外键违例报错点名插件/文件/行号。
- **执行条件**：dev（`spring.sql.init.mode=always`）在主种子之后无条件执行（与 dev 主种子"每次重启重播"语义一致）；生产仅 `systar.database.init-data=true` 时在 `DatabaseInitializer` 之后执行——生产默认不播种子，但类型照常注册。

### 10.3 id 段约定

| 段 | 归属 |
|----|------|
| < 50000 | 主种子（`sql/{h2,mysql}/data/`） |
| ≥ 50000 | 插件种子；每插件占一个千位子段（50000–50999、51000–51999…），私有插件自选空闲子段 |
| 历史豁免 | 803 / 103 / 104 / 44 / 45 —— bacnet/iec104 插件种子沿用 GPL SQL 时代的旧 id，使已按旧脚本播种的存量库按主键命中、不重复插入 |

高段约定同时消解两类冲突：与主种子/他插件撞 id，以及 H2 `IDENTITY` 与 MySQL `AUTO_INCREMENT` 对显式 id 插入的行为差异（H2 不推进序列、MySQL 会——高段给自动增量留足空间）。

### 10.4 列头模板与完整示例

各表白名单列全集（列头可取子集，`id` 必含）：

```
dictionary        id,catalog_id,name,caption,parent
service           id,name,caption,mode,driver_class,max_connections,type_name
asset             id,name,caption,kind,type_id,parent_id,state,enabled,sort,device_id,service_id,probe_id,control_id
asset_attribute   id,asset_id,attr_key,attr_value,attr_type
probe             id,name,caption,parent,source,unit,time_interval,saving_interval,warn_cond,transform,catalog,monitor_kind,min_value,max_value,type_name,is_virtual,expression,depends_on
control           id,name,caption,parent,source,unit,time_interval,saving_interval,catalog,transform,warn_cond,refresh_delay,min_value,max_value,type_name
```

要点：`kind` 1/2/3/4 = 设备/服务/监测器/操控器；`mode` 0=主动、1=被动；service 行可直接携带 `driver_class`（`type_name` 留空）或经 `type_name` 引用已注册类型；`t_asset` 行的 `service_id`/`probe_id`/`control_id`/`device_id` 是桥接子表运行时行的外键。

端到端示例即两个 GPL 插件的真实种子（字典项 → 服务 → 资产，NN 序即外键序）：

```
drivers/systar-driver-bacnet/seed/
  01-dictionary.csv   id,catalog_id,name,caption,parent
                      803,8,BACNET,BACnet,
  02-service.csv      id,name,caption,mode,driver_class,max_connections,type_name
                      104,bacnet_svc,BACnet服务,0,com.systar.monitor.drivers.bacnet.BacnetService,5,
  03-asset.csv        id,name,caption,kind,type_id,parent_id,state,enabled,sort,device_id,service_id,probe_id,control_id
                      45,bacnet_svc,BACnet服务,2,103,0,0,1,5,,104,,
```

## 11. 运行时生命周期

启动（`ServerLifeCycle` 编排，详见 `docs/design/architecture.md` §6.2）：

1. `DriverPluginLoader` 扫描 `systar.drivers.path`：每插件目录建类加载器、收集类型 XML（自有来源）、登记 `seed/*.csv`
2. `XmlAssetTypeLoader` 注册类型（内置 classpath 扫描 + 各插件贡献的 XML）→ 同步 `t_asset_type_config`
3. `PluginSeedExecutor` 执行各插件种子 CSV（条件见 §10.2）
4. `DatabaseAssetLoader` 按序加载资产实例（Device → Service → Probe → Control），每实例 `loadAttributes` + `bindProperties()`；`MonitorServer` 启动：被动服务 `setResultDispatcher` → `start()` → 注册被动路由；随后调度器轮询主动监控器

**解析失败语义**（删插件不炸启动的关键）：启动期每个资产行的实例化依赖三类目标——① 类型注册；② 实现类（类型的 `JavaClass`，或类型不带类时的行 `driver_class` 列）；③ 源 service 行。任一目标解析不出 → **WARN 跳过该行**（点名缺失目标与应检查的插件目录），被跳过行所引用的行沿引用链**级联跳过**至传递闭包，均不阻断启动。运行期用户显式启动/实例化此类资产 → 抛 `AssetException` 点名缺失目标。

**用户可见后果**：被跳过的资产从前端资产树整体消失（DB 行残留但界面无痕迹）；重装插件重启即恢复（类型重新注册、资产重新加载、种子幂等不重复）。两个例外需手动清理：`t_code_dict` 字典项（如 BACNET 下拉项）由 `ServerLifeCycle.loadCodeDict` 从 DB 加载，**不随插件消失**，残留至手动 DELETE；引用被跳过资产的告警/联动/定时规则照常加载、运行期静默失效。

运行中：

- 调度器对每个监控器按 `shouldDetect(now)` 节流后执行 detect（框架超时 `detectTimeoutMs` 默认 30 s；手动触发经 `trySetDetecting()` 防重入）。
- transform（如 `#value * 0.1`）与 warn 条件（如 `#value > 30`）由 `Monitor` 基类经受限 SpEL 求值器编译执行（变量 `#value`，禁 `T()`/`new`/方法调用），驱动无需关心。
- UI 创建/更新资产（`AssetOrchestrator`）即时入库并同步运行时 store，属性变更触发重绑定，无需重启。

关闭时反向：停止调度 → 排空告警/联动队列 → 各 service `stop()` 释放连接。

## 12. 构建、部署与开发循环

### 12.1 仓库构建

`drivers/` 是 Maven 聚合（与 core/、extensions/ 平级），每协议一个模块（14 个：12 MIT + 2 GPL）。每模块 package 阶段经 assembly 产出 `target/plugin/{driver.jar, lib/, seed/}`，再由 antrun 汇总到 `drivers/dist/<artifactId>/`（gitignored 构建产物）。fat jar（`systar-server.jar`）不含任何驱动类与协议库——发布形态 = `server.jar` + `drivers/` 插件目录。

### 12.2 开发循环

```bash
# 改驱动代码后：
./mvnw -pl drivers/systar-driver-modbus package
# 把 target/plugin/ 的内容拷到服务器插件目录（assembly 产物目录名是 plugin，落位须改名为插件身份）
# dev profile 的 systar.drivers.path=./drivers/dist，聚合构建已自动落位，重启即生效

# core/或上游共享模块有改动时，先安装再运行（-pl 会从本地仓库解析旧 jar）：
./mvnw install -DskipTests
./mvnw spring-boot:run -pl extensions/systar-server -Dspring-boot.run.profiles=dev
```

私有插件（不进仓库）：`./mvnw -pl <你的模块> package` 后把 `target/plugin/` 拷成服务器 `drivers/<你的插件名>/` 即可；XML-only 插件连 Maven 都不需要——建一个 `drivers/<name>/` 目录、放类型 XML、重启。

### 12.3 配置与部署形态

- `systar.drivers.path`：插件根目录，默认 `./drivers`，**按工作目录解析**（`bin/` 脚本与 `spring-boot:run` 的工作目录均为仓库根；生产配绝对路径亦可）。目录不存在时服务器照常启动（无插件），日志 WARN 点名解析后的绝对路径。
- 生产部署：`systar-server.jar` 与 `drivers/` 同级放置，从该目录 `java -jar`。

### 12.4 旧配方 → 新配方映射

| 旧机制（≤1.1.0） | 新机制（1.1.1+） |
|------------------|------------------|
| 私有驱动 JAR 经 Spring Boot `loader.path` 挂载 | 插件目录：`drivers/<name>/driver.jar` + `lib/*.jar` |
| `systar.asset-type.scan-paths=file:./config/drivers/*.xml` 外置 XML | XML-only 插件目录：XML 移到 `drivers/<name>/` 根下（属性已退役） |
| GPL 驱动经 `gpl-drivers` Maven profile 启用 | bacnet/iec104 就是普通插件目录（profile 与 settings.xml 激活已退役，残留激活无害但应删除） |

### 12.5 验证与可发现性

- 启动日志每插件一行 INFO：`Plugin 'systar-driver-modbus': 3 type XML(s), 12 class(es), 0 seed file(s).`，结束汇总 `Driver plugins: 14 plugin(s) loaded from '<绝对路径>'.`
- 已注册类型及其 `JavaClass` 可查 `t_asset_type_config` 表或 `GET /api/monitor/asset-types`（前端资产类型下拉同源）。

## 13. 测试

三层测试范式（均在现有代码中有样板）：

| 层 | 样板 | 做法 |
|----|------|------|
| 驱动单元测试（`drivers/systar-driver-*` 模块） | `ModbusServiceTest` | 手工构造 `ServiceType`+`AssetTypeProperty` 镜像 XML 展开结果，`init` 后 `setMetadata`/`bindProperties` 断言三层优先级；真 socket 用 `new ServerSocket(0)` 随机端口 |
| Probe/Control 单元测试 | `ModbusControlTest`、`MqttProbeTest` | 直接构造 Probe，伪造 metadata/连接，断言 detect 结果与命令解析 |
| 服务级集成测试（server 模块） | `AssetAttributeBindingTest` | `@SpringBootTest` + dev profile + `@Transactional @Rollback`；经 `AssetOrchestrator.createAsset` 全链路验证绑定；运行时 store 残留用 `monitorServer.removeAsset(id)` 在 `@AfterEach` 清理 |

注意事项：

- 测试类超时统一设短（≤3 min），驱动模块惯例 1 min。
- 嵌套测试类（`@Nested`）用 `-Dtest='XxxTest*'` 通配运行（surefire 3.2.5 只匹配外部类名）。
- 全量回归必须 `./mvnw clean test -o`（非 clean 时 Spring 上下文跨模块污染会间歇性失败）。
- 插件机制自身的集成夹具（temp 插件目录、XML-only 插件、种子 CSV）见 `extensions/systar-server` 的 `DriverPluginLoaderTest`、`PluginSeedExecutorTest` 与 dev 上下文测试。

## 14. 检查清单与常见陷阱

新驱动提交前自查：

- [ ] Service 继承正确（Active/Passive），`start()` fail-fast、`stop()` 幂等且不抛
- [ ] 每个可配属性有 setter；字段初始值为内置兜底；magic number 定义为常量
- [ ] 可调超时暴露为 `Timeout` Property；主动服务考虑 `MaxConnections`
- [ ] detect 成功 setValue+setSampleTime / 失败 setError；连接 finally 归还、坏连接 close
- [ ] XML：标准子元素名、类型名同类别唯一、`Source` 指向 Service 类型名、非类型资源不在 drivers 包路径下
- [ ] 依赖 scope 正确：`systar-monitor-core` 与共享库 provided、私有协议库 compile（§9）
- [ ] 种子 CSV：白名单逻辑名、列头含 `id`、id 用 ≥50000 空闲子段、外键序与 NN 序一致（§10）
- [ ] 单测覆盖三层绑定优先级；集成测试清理运行时 store 残留
- [ ] `./mvnw clean test -o` 全量绿

常见陷阱（加载器均以 fail-fast 异常或 WARN 日志给出明确原因，对表排查）：

| 症状 | 根因 |
|------|------|
| 启动日志 `Driver plugin directory '...' does not exist` | 工作目录不对（`systar.drivers.path` 按工作目录解析）或路径配错；服务器无插件照常启动 |
| 插件目录被 WARN `directory has no driver.jar, lib/*.jar, type XML or seed; skipped` | 目录既无 jar 也无 XML——残留/半成品目录，或 jar 没构建出来 |
| 启动失败 `driver jar ... is corrupt or unreadable` | driver.jar 损坏或拷贝不完整 |
| XML 放了没生效，WARN `XML file '...' is not in an accepted type XML location and is ignored` | loose XML 只认插件目录根与包树 `com/systar/monitor/drivers/**` 两处；jar 内 XML 必须在包树下；其他子目录（如 `lib/`）里的 XML 被忽略 |
| 类型没加载，日志 WARN "0 `<Service>` elements" | 子元素名写了协议前缀（`<SnmpService>`），必须 `<Service>` |
| 启动失败：duplicate type name | 两个插件声明了同类别同名类型 |
| 类型注册被 WARN 跳过（`Source` 相关） | `Source` 指向未注册的 Service 类型——目标插件缺失或类型名拼错 |
| 资产行消失于资产树，启动 WARN 点名类名 | 类型的 `JavaClass`（或行 `driver_class` 列）所在插件被删——重装插件重启即恢复 |
| 删插件后 BACNET 等下拉项仍在 | `t_code_dict` 字典项不随插件消失，需手动 DELETE |
| 运行期 `NoSuchMethodError`/`NoClassDefFoundError` | core API 变更后驱动未重编译（无版本兼容信号，§9） |
| 种子启动失败 `unknown logical name` | 文件名逻辑名拼错或表不在白名单（错误消息列出合法名） |
| 种子启动失败 `header lacks 'id'` / `has illegal column` | 列头缺主键列 / 列名不在表上 |
| 种子 WARN `row(s) already present (kept)` | 主键已存在（与主种子或他插件冲突），insert-if-absent 跳过——检查是否用了别人的 id 段 |
| 属性没生效，日志 WARN setter/convert | Property Name 与 setter 名对不上，或值超界转换失败 |
| detect 报 not attached to XxxService | Probe 的 `Source` 填了协议地址而非 Service 类型名 |
