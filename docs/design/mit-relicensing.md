# MIT 重新授权设计（许可边界）

> **日期**: 2026-10-23
> **状态**: 已实施
> **定位**: 记录 GPL-3.0 → MIT 重新授权的可行性依据与许可边界设计

---

## 背景

Systar 原以 GPL-3.0-only 发布。为降低使用与组合门槛，项目转为 MIT 许可。本文档记录
转换为何可行、许可边界现在划在哪里，以及对构建者、部署者与驱动作者分别意味着什么。

## 为什么可以重新授权

1. **版权单一**。仓库全部历史提交均出自项目所有者本人（代码移植自其私有老项目），
   变更自有代码的许可无需任何第三方同意。变更前已发布的版本仍按 GPL-3.0 提供；
   版权人只是将新版本（及当前代码树）以 MIT 授权。
2. **源码干净**。源文件无第三方版权头；前端从未引用 GPL 驱动。
3. **GPL 依赖已隔离**。编译期 GPL-3.0-only 依赖只有两个——BACnet4J（BACnet）与
   j60870（IEC 60870-5-104）——且都局限在一个 Maven 模块内的两个驱动包中。
4. **其余依赖全部宽松**。剩下的 Java 与前端依赖均为 Apache-2.0 / MIT / EPL / MPL /
   BSD / MulanPSL，可与 MIT 自由组合。

两个值得记录的细节：

- **MySQL Connector/J 8.x** 为 `GPL-2.0 WITH Universal-FOSS-Exception-1.0`。该 FOSS
  例外明确允许驱动与 OSI 认可许可（MIT 属之）的软件组合分发，条件是组合作品随完整
  源码分发——本仓库满足。精确条款见 `THIRD-PARTY-NOTICES.md`。
- **org.json** 在重新授权前已替换为 Jackson：JSON License 的 "Good, not Evil" 条款
  非 OSI 认可，随 MIT 发布物捆绑会污染许可面。

## 许可边界

> 1.1.1 起边界机制升级：GPL 驱动从"独立 Maven 模块 + profile"改为**独立插件目录**，
> 许可边界 = 目录边界（驱动插件目录架构见 `docs/driver-development-guide.md` §1 与 §12）。

```
MIT（本仓库）                         GPL-3.0（本地独占，导出时剔除）
┌──────────────────────────────┐     ┌─────────────────────────────────────┐
│ core/、extensions/、          │     │ drivers/systar-driver-bacnet/       │
│ simulator/、frontend/、sql/   │     │  └── BACnet 驱动（BACnet4J）        │
│ drivers/ 下 12 个 MIT 驱动    │     │ drivers/systar-driver-iec104/       │
│ 插件模块                      │     │  └── IEC 104 驱动（j60870）         │
└──────────────────────────────┘     └─────────────────────────────────────┘
```

### 拆分的机械原理

- 两个 GPL 驱动各是一个普通驱动插件模块（`drivers/systar-driver-bacnet`、
  `drivers/systar-driver-iec104`），与 12 个 MIT 驱动插件同构；GPL 依赖（BACnet4J、
  j60870）只进各自插件的 `lib/`，不进 fat jar。
- **导出剔除即公开仓库**：导出 systar-oss 时删除 `drivers/systar-driver-bacnet/`、
  `drivers/systar-driver-iec104/` 两个目录，从 `drivers/pom.xml` 删除对应两行
  `<module>`，并删除 vendored GPL 构件 `lib/maven-repo/com/serotonin/`（BACnet4J）
  与 `lib/maven-repo/lohbihler/`（sero-warp、sero-scheduler，BACnet4J 的传递依赖）；
  其余构建者得到纯 MIT 构建，不会出现缺模块错误。根 pom `dependencyManagement` 中
  两条 GPL 驱动版本声明无人消费，对 MIT 构建惰性，无需处理。早期的 `gpl-drivers`
  Maven profile 与 `settings.xml` 激活机制已随插件目录架构退役。
- 插件保留原包名（`com.systar.monitor.drivers.bacnet`、`...drivers.iec104`），类型
  XML 在 jar 内类同包树下，由 `DriverPluginLoader` 按插件自有来源发现——无需改动
  加载器或任何配置。
- GPL 服务的演示种子行（字典项 `BACNET`、服务 103/104、资产节点 44/45）从共享种子
  脚本（`sql/{h2,mysql}/data/01-init.sql`）迁入插件自带种子 CSV
  （`seed/01-dictionary.csv`、`02-service.csv`、`03-asset.csv`），由 `PluginSeedExecutor`
  按主键 insert-if-absent 执行（条件与主种子机制一致）。id 沿用旧值（803/103/104/44/45）
  而非插件种子惯例的 ≥50000 高段，使已按旧脚本播种的存量库按主键命中、不重复插入。
  共享种子保持与驱动无关：没有 GPL 插件的仓库不会出现没有驱动实现的驱动类型。

### 为什么是模块拆分而不是重写

成熟的宽松许可 Java BACnet 协议栈不存在，宽松许可的 IEC 60870-5-104 栈完全没有，
因此保留这两个驱动就无法去除 GPL。直接删驱动会损失功能；拆为可选插件目录既为私有
部署保留了功能，又让公开仓库保持 MIT。

## 对使用者的意义

- **构建本仓库**：得到纯 MIT 形态——`drivers/` 下 12 个 MIT 驱动插件模块，无 GPL
  代码、无 vendored GPL jar，`./mvnw clean test -o` 仅凭 Maven Central 即可完成。
- **从拆分前的版本升级**：既有数据库仍带有 GPL 演示行（服务 103/104、资产 44/45）。
  改用不含 GPL 插件的构建后，这些行在启动加载时因驱动类缺失被 **WARN 跳过**（行残留
  于库中、资产树不再显示），不再导致启动失败；重装 GPL 插件目录重启即恢复。
- **需要 BACnet 或 IEC 104**：直接使用本地仓库的 GPL 插件目录（拷贝进 `drivers/`
  即生效），或按驱动开发指南自行实现（见 `docs/driver-development-guide.md`）；
  底层库 BACnet4J 与 j60870 均为 GPL-3.0，基于它们构建的驱动会使组合分发物整体变为
  GPL-3.0——这是分发层面的决策，不是本仓库能够也不应该回避的。
- **带 GPL 插件部署**：GPL 库只存在于插件目录的 `lib/` 中，fat jar 本身不含 GPL
  代码；对外分发包含 GPL 插件目录的分发物（server.jar + `drivers/`）是 GPL-3.0
  组合作品，触发 GPL 义务（随附完整对应源码）；仅内部运行不分发则不触发。
