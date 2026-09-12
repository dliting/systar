# 协议驱动二次开发指南

面向需要为 Systar 新增或定制协议驱动的开发者。内容基于当前代码（`core/systar-monitor-core` 资产框架 + `core/systar-monitor-drivers` 驱动集合），所有机制均可在引用的源文件中核对。

相关文档：

- `docs/design/architecture.md` §7 — 驱动架构总览与 14 个现有驱动清单
- `docs/design/xml-asset-type-config-design.md` — 类型 XML 的完整规格（扫描路径、元素命名、DB 同步、私有部署）
- `docs/tech_overview.md` — 系统整体技术概览

## 1. 一个驱动由什么构成

新增一个协议驱动 = 四类构件，全部放在 `core/systar-monitor-drivers` 模块的同一个协议包下：

| 构件 | 形态 | 数量 | 作用 |
|------|------|------|------|
| Service 驱动类 | Java（继承 `ActiveService` 或 `PassiveService`） | ≥1 | 管理连接池/监听器，承载服务级配置（地址、端口、超时…） |
| Connection 类 | Java（实现 `MonitorConnection`，仅主动驱动） | 0..1 | 一条通信通道的生命周期与协议读写 |
| Probe / Control 驱动类 | Java（继承 `Probe` / `Control`） | ≥1 | 单个数据点的读取（detect）/ 写入（execute）逻辑 |
| 类型定义 XML | `<proto>-services.xml`、`<proto>-probes.xml`、`<proto>-controls.xml` | ≥2 | 声明类型名、JavaClass、属性清单——**放入即自注册**，无全局索引 |

目录布局（以 `myproto` 为例）：

```
core/systar-monitor-drivers/src/main/
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

**类型 vs 实例**：XML 定义的是 *类型*（如 `ModbusTcpMaster`，含属性清单与缺省值），运行时/数据库中的是 *实例*（如 `modbus_svc_hvac`，`t_service` + `t_asset_attribute` + `t_probe`/`t_control` 行）。实例由框架按类型的 `JavaClass` 反射创建（`AssetRepository`），驱动作者只写类和类型定义，不写实例化代码。

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

无论哪种，最终都必须经 `getResultDispatcher().dispatch(new MonitorResult(monitor, value))` 走统一结果管道（入库、告警、联动都挂在后面）。

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
- 类型名同类别内跨所有扫描路径唯一；`Super` 父类型须同文件先声明（属性复制继承，子类同名属性覆盖）；`Source` 引用的 Service 类型由加载顺序（Service 先于 Probe/Control）保证可解析。
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

## 9. 运行时生命周期

启动（`ServerLifeCycle` 编排，详见 `docs/design/architecture.md` §6.2）：

1. `XmlAssetTypeLoader` 扫描类型 XML → 注册进 `AssetStore` + 同步 `t_asset_type_config`
2. `DatabaseAssetLoader` 按序加载资产实例（Device → Service → Probe → Control），每实例 `loadAttributes` + `bindProperties()`
3. `MonitorServer` 启动：被动服务 `setResultDispatcher` → `start()` → 注册被动路由；随后调度器轮询主动监控器

运行中：

- 调度器对每个监控器按 `shouldDetect(now)` 节流后执行 detect（框架超时 `detectTimeoutMs` 默认 30 s；手动触发经 `trySetDetecting()` 防重入）。
- transform（如 `#value * 0.1`）与 warn 条件（如 `#value > 30`）由 `Monitor` 基类经受限 SpEL 求值器编译执行（变量 `#value`，禁 `T()`/`new`/方法调用），驱动无需关心。
- UI 创建/更新资产（`AssetOrchestrator`）即时入库并同步运行时 store，属性变更触发重绑定，无需重启。

关闭时反向：停止调度 → 排空告警/联动队列 → 各 service `stop()` 释放连接。

## 10. 测试

三层测试范式（均在现有代码中有样板）：

| 层 | 样板 | 做法 |
|----|------|------|
| 驱动单元测试（drivers 模块） | `ModbusServiceTest` | 手工构造 `ServiceType`+`AssetTypeProperty` 镜像 XML 展开结果，`init` 后 `setMetadata`/`bindProperties` 断言三层优先级；真 socket 用 `new ServerSocket(0)` 随机端口 |
| Probe/Control 单元测试 | `ModbusControlTest`、`MqttProbeTest` | 直接构造 Probe，伪造 metadata/连接，断言 detect 结果与命令解析 |
| 服务级集成测试（server 模块） | `AssetAttributeBindingTest` | `@SpringBootTest` + dev profile + `@Transactional @Rollback`；经 `AssetOrchestrator.createAsset` 全链路验证绑定；运行时 store 残留用 `monitorServer.removeAsset(id)` 在 `@AfterEach` 清理 |

注意事项：

- 测试类超时统一设短（≤3 min），drivers 模块惯例 1 min。
- 嵌套测试类（`@Nested`）用 `-Dtest='XxxTest*'` 通配运行（surefire 3.2.5 只匹配外部类名）。
- 全量回归必须 `./mvnw clean test -o`（非 clean 时 Spring 上下文跨模块污染会间歇性失败）。

## 11. 部署

**内置驱动**：改完代码 `./mvnw spring-boot:run -pl extensions/systar-server -Dspring-boot.run.profiles=dev` 即可验证（开发期用最新编译产物）；生产打包后 `java -jar`。

**私有驱动（不进公开仓库）**：两种配方，详见 `xml-asset-type-config-design.md` 末节——

1. **XML-only**：外置目录放类型 XML，`JavaClass` 指向内置驱动类（如基于 `ModbusProbe` 定义站点专属类型、预设缺省），配置 `systar.asset-type.scan-paths=file:./config/drivers/*.xml`。
2. **Java + XML**：私有驱动打 JAR 经 `loader.path`（PropertiesLauncher）挂载，JAR 内 XML 由内置 `classpath*:` 模式自动发现；或 XML 放外置 `file:` 目录。

验证方式：启动日志 `Asset types loaded: N types registered.` 数目符合预期；前端资产类型下拉（`/api/monitor/asset-types`）出现新类型。

## 12. 检查清单与常见陷阱

新驱动提交前自查：

- [ ] Service 继承正确（Active/Passive），`start()` fail-fast、`stop()` 幂等且不抛
- [ ] 每个可配属性有 setter；字段初始值为内置兜底；magic number 定义为常量
- [ ] 可调超时暴露为 `Timeout` Property；主动服务考虑 `MaxConnections`
- [ ] detect 成功 setValue+setSampleTime / 失败 setError；连接 finally 归还、坏连接 close
- [ ] XML：标准子元素名、类型名同类别唯一、`Source` 指向 Service 类型名、非类型资源不在 drivers 包路径下
- [ ] 单测覆盖三层绑定优先级；集成测试清理运行时 store 残留
- [ ] `./mvnw clean test -o` 全量绿

常见陷阱（加载器均以 fail-fast 异常或 WARN 日志给出明确原因，对表排查）：

| 症状 | 根因 |
|------|------|
| 启动失败：unrecognized root element | drivers 包路径下放了非类型 XML |
| 类型没加载，日志 WARN "0 \<Service\> elements" | 子元素名写了协议前缀（`<SnmpService>`），必须 `<Service>` |
| 启动失败：duplicate type name | 两个扫描路径出现同名类型（含 `file:` 与 `classpath*:` 重叠命中同一文件） |
| 属性没生效，日志 WARN setter/convert | Property Name 与 setter 名对不上，或值超界转换失败 |
| detect 报 not attached to XxxService | Probe 的 `Source` 填了协议地址而非 Service 类型名 |
