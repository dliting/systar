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

```
MIT（本仓库）                         GPL-3.0（独立模块，不在本仓库）
┌──────────────────────────────┐     ┌─────────────────────────────────────┐
│ core/、extensions/、          │     │ gpl/systar-drivers-gpl              │
│ simulator/、frontend/、sql/   │     │  ├── BACnet 驱动（BACnet4J）        │
│ 12 种内置协议驱动             │     │  └── IEC 104 驱动（j60870）         │
└──────────────────────────────┘     └─────────────────────────────────────┘
```

### 拆分的机械原理

- GPL 驱动位于单一 Maven 模块 `gpl/systar-drivers-gpl`，由 `gpl-drivers` Maven
  profile 激活。该 profile **默认不激活**：本仓库的构建者得到纯 MIT 构建，不会出现
  缺模块错误；私有构建通过 `settings.xml` 或 `-P gpl-drivers` 激活以纳入 GPL 驱动。
- 模块保留原包名（`com.systar.monitor.drivers.bacnet`、`...drivers.iec104`），即插
  即用类型加载器 `XmlAssetTypeLoader` 扫描 `classpath*:com/systar/monitor/drivers/**/*.xml`，
  jar 进入 classpath 即被自动发现——无需改动加载器或任何配置。
- GPL 服务的演示种子行（字典项 `BACNET`、服务 103/104、资产节点 44/45）从共享种子
  脚本（`sql/{h2,mysql}/data/01-init.sql`）迁入模块自带 classpath 脚本
  （`sql/{h2,mysql}/data/09-gpl-drivers.sql`），由 `GplDriverSeedInitializer` 在与主
  初始化器相同的条件（`systar.database.init-data=true`）下执行，并通过 `@DependsOn`
  保证在主种子之后。共享种子保持与驱动无关：没有 GPL 模块的仓库不会出现没有驱动
  实现的驱动类型。

### 为什么是模块拆分而不是重写

成熟的宽松许可 Java BACnet 协议栈不存在，宽松许可的 IEC 60870-5-104 栈完全没有，
因此保留这两个驱动就无法去除 GPL。直接删驱动会损失功能；拆为可选模块既为私有部署
保留了功能，又让公开仓库保持 MIT。

## 对使用者的意义

- **构建本仓库**：得到纯 MIT 形态——12 种内置协议驱动，无 GPL 代码、无 vendored
  GPL jar，`./mvnw clean test -o` 仅凭 Maven Central 即可完成。
- **需要 BACnet 或 IEC 104**：按描述符加载器自行实现驱动（见
  `docs/driver-development-guide.md`）；底层库 BACnet4J 与 j60870 均为 GPL-3.0，
  基于它们构建的驱动会使组合二进制整体变为 GPL-3.0——这是分发层面的决策，不是
  本仓库能够也不应该回避的。
- **带 GPL 模块部署**：将 GPL 驱动打进 fat jar 的私有构建产物是 GPL-3.0 组合作品，
  对外分发该二进制触发 GPL 义务（随附完整对应源码）；仅内部运行不分发则不触发。
