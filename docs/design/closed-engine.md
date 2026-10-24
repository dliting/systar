# 闭源监测引擎设计（许可边界）

> **日期**: 2026-10-24
> **状态**: 已实施（1.2.0 起）
> **定位**: 记录监测引擎闭源的许可模型、模块边界与获取全功能的方式

---

## 背景

Systar 以 MIT 开源维持采纳面，但核心监测运行时的持续开发需要商业化支撑。自 1.2.0
起，监测引擎（资产调度、告警判定、联动执行、定时控制）从开源框架中切出为**闭源模
块**：二进制免费、功能完整，源码与技术支持走商业渠道。

法律基础与 MIT 重新授权相同（见 `docs/design/mit-relicensing.md`）：全部提交出自唯
一版权人，可对自有代码新设商业许可。已发布的 MIT 部分不可撤回；闭源以新的模块边界
切出，不改变任何既有版本的许可。

## 许可模型

| 层 | 许可 | 源码 | 说明 |
|----|------|------|------|
| 框架（`core/`、`drivers/`、`extensions/`、`simulator/`） | MIT | 公开 | 本仓库全部内容 |
| 引擎二进制（`systar-monitor-engine`） | Systar Engine Binary License（freeware EULA，`commercial/systar-monitor-engine/LICENSE-ENGINE.txt`） | 不公开 | 免费使用与再分发，所有功能完整可用；禁反编译再发布源码 |
| 引擎源码 + 支持 | 商业许可 | 商业渠道 | 源码访问、技术支持、持续开发 |

要点：

- **无功能门控、无运行时许可校验**——没有 license key，没有到期检查。免费二进制与
  商业版功能完全一致，商业权益是源码与支持，不是功能。
- 引擎缺席时 server 照常启动：运行时操作降级为空操作并告警一次，公开构建可运行，
  仅无监测调度。

## 模块边界

边界按"契约在 MIT 侧、算法与编排在闭源侧"划分：

| 归属 | 内容 | 规则 |
|------|------|------|
| `core/systar-monitor-api`（MIT） | 资产模型与 `AssetStore`、`MonitorEngine` 端口（运行时操作契约）、告警契约（`AlarmRule`、`AlarmStrategy`、`AlarmRepository`、`ErrorMessageLog`）、联动契约（`LinkageRuleBean` 等、`LinkageRepository`、`LinkageTriggerStrategy`）、定时任务契约（`ScheduledTask`、`ScheduledTaskLog`、两个 Repository 接口） | systar-data 实现这些端口/引用这些枚举；MIT 数据层不得依赖闭源 |
| `commercial/systar-monitor-engine`（闭源） | `monitor/schedule` 全部、`MonitorServer`（实现 `MonitorEngine`）、告警算法（`AlarmHandler`、`AlarmCorrelator`、`AlarmSuppressionChecker` 及策略实现）、联动算法、`TimeControlService`、`ResultPersistHandler`、引擎 bootstrap 与 Spring 自动配置 | 算法与编排即 IP |

装配机制：引擎类不带 `@Component`，唯一注册点是 `EngineAutoConfiguration`（经
`AutoConfiguration.imports`）。server 主源码只 import api；引擎经
`ObjectProvider<MonitorEngine>` 可选注入——引擎 jar 在 classpath 即全功能，不在则
缺席降级。因此公开仓库剔除 `commercial/` 后构建、测试、运行全部照常。

## 如何获得全功能

- **完整体验（推荐）**：下载 Releases 中的 `systar-server-<版本>-full.jar`——商业形
  态构建，引擎已内嵌，开箱即得全部监测调度、告警与联动。
- **公开构建**：从本仓库 `./mvnw package` 得到不含引擎的 jar；框架全部 API、UI 与
  数据链路可用（资产管理完整可用：server 自行装载资产存储并同步 CRUD 事件），监测
  运行时为空档（启动与接口以告警/409 明确提示引擎缺席）。
- **引擎源码**：商业许可渠道获取，见 `commercial/systar-monitor-engine/README.md`。

## 与 GPL 驱动插件的关系

这是另一条正交的许可边界：BACnet/IP 与 IEC 60870-5-104 驱动插件因链接 GPL-3.0-only
库（BACnet4J、j60870）而整体 GPL-3.0，本地独占、导出时剔除（见
`docs/design/mit-relicensing.md` 与 `docs/driver-development-guide.md`）。闭源引擎
与 GPL 驱动互不影响：引擎不依赖任何驱动，驱动不依赖引擎；三者在 MIT 框架上各自组
合。
