# Changelog

English | [简体中文](CHANGELOG.zh-CN.md)

All notable changes to this project are documented in this file.

Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.2.0] - 2026-10-25

Closed-source monitor engine — the scheduling / alarm / linkage / time-control
runtime moves to a free-binary closed module; the framework stays MIT.

### Added

**Closed-source monitor engine**
- New `commercial/systar-monitor-engine` module (closed source, free binary): asset scheduling, alarm evaluation, linkage execution, time control. Every feature stays enabled — no feature gating, no runtime license check; commercial licensing covers engine source access and support (`commercial/systar-monitor-engine/LICENSE-ENGINE.txt`; design: `docs/design/closed-engine.md`)
- `systar-server-<version>-full.jar` release artifact embeds the engine for download-and-run full functionality; plain builds that exclude the engine (the public MIT repository) run fine — the framework starts, asset management stays fully usable, and only runtime monitoring operations WARN-skip once
- The engine auto-registers via `EngineAutoConfiguration` (`AutoConfiguration.imports`); server code depends only on the MIT API and injects the engine optionally via `ObjectProvider`
- With the engine absent the server owns the runtime store itself: `EngineAbsentAssetBootstrap` loads the type registries and seeded assets at startup, and asset CRUD events are applied directly to the `AssetStore` — asset management is fully functional in the open-source form

### Changed

- `systar-monitor-core` renamed to `systar-monitor-api` — MIT contracts only (asset model, `AssetStore`, the `MonitorEngine` port, alarm / linkage / scheduled-task contracts); engine internals moved to the closed module
- Server controllers, listeners and lifecycle reference only `systar-monitor-api`; with the engine absent, detection/control endpoints return a conflict result ("Monitor engine not present; … skipped.") instead of failing

---

## [1.1.1] - 2026-10-24

Driver plugin directory architecture — drivers split out of the core build into
self-contained, copy-in plugin directories — plus the MIT relicensing.

### Added

**Driver plugin directory architecture**
- Each protocol driver is now a self-contained plugin directory — `driver.jar` + `lib/` + optional `seed/` CSV — discovered at startup under `systar.drivers.path` (default `./drivers`, resolved against the working directory). Copy a plugin directory in and restart: no server code change, no repackaging. Removing one never breaks startup — affected database rows are WARN-skipped by name, cascading to rows that depend on them
- Per-plugin class loaders isolate private protocol libraries; core API classes are shared parent-first
- XML-only plugin directories (loose type XML at the directory root) extend the type catalog without writing Java, referencing classes provided by other plugins
- Plugin seed CSVs: whitelisted tables (`dictionary` / `service` / `asset` / `asset_attribute` / `probe` / `control`), header = DB column names, insert-if-absent by primary key, executed under the same conditions as the main SQL seeds
- Malformed or duplicate type definitions fail fast at startup with the offending plugin(s) named

**Protocol drivers**
- New protocol driver development guide (`docs/driver-development-guide.md`): plugin directory layout, type-XML self-registration, seed CSV specification, and private-driver deployment recipes

### Changed

**Driver plugin directory architecture (drivers/)**
- `core/systar-monitor-drivers` split into 14 `drivers/systar-driver-*` plugin modules (one per protocol); each module builds its plugin directory under `target/plugin/`, aggregated into `drivers/dist/` for the dev runtime
- Server fat jar slimmed: driver classes and private protocol libraries are no longer bundled — they load at runtime from the driver search path, and `systar-server` has no compile-time dependency on the drivers
- The `gpl-drivers` Maven profile and its `settings.xml` activation retired — GPL drivers are ordinary plugin modules; exporting the public repository deletes their two directories and their two `<module>` lines in `drivers/pom.xml`
- GPL demo seed rows (code dict 803, services 103/104, assets 44/45) migrated from the shared SQL seed scripts to the GPL plugins' own `seed/` CSVs, keeping the original ids so already-seeded databases stay idempotent
- `systar.asset-type.scan-paths` (added earlier in this cycle) retired before release — external type XML now ships as XML-only plugin directories

**Licensing**
- License changed from GPL-3.0-only to **MIT**. BACnet/IP and IEC 60870-5-104 drivers ship as GPL-3.0 driver plugin directories (`drivers/systar-driver-bacnet`, `drivers/systar-driver-iec104`, **not part of this repository**) because their underlying libraries (BACnet4J, j60870) are GPL-3.0-only; distributions that include those plugin directories follow GPL-3.0 for the combined work. Databases seeded before the split still carry the GPL demo rows (services 103/104, assets 44/45): an MIT-only build WARN-skips them at startup (the rows remain in the database, hidden from the asset tree) instead of failing; reinstalling the GPL plugin directories restores them. Fresh deployments wanting those two protocols just copy the GPL plugin directories into the driver search path — their demo seed rows ship inside the plugins as seed CSVs
- org.json replaced with Jackson (Apache-2.0): the JSON License ("Good, not Evil")
  is not OSI-approved. Two org.json quirks were intentionally dropped — a JSON null
  at an MQTT jsonPath now yields `null` (was the `JSONObject.NULL` sentinel), and a
  path continuing past a scalar node yields `null` (was the scalar)

**Protocol drivers (drivers/ / systar-server)**
- Driver modularization: protocol type-definition XMLs moved from server `config/assets/` into the driver plugin modules (co-located with the driver classes); the loader now self-registers types via directory scanning and the global index `Assets.xml` was deleted — adding or removing a driver no longer requires index maintenance
- ModbusService's hand-written config resolution chain (metadata > type default > built-in default) unified into the framework property binding (`bindProperties`)
- Type-config version numbers now increment only when content actually changes, fixing the version inflation caused by an unconditional version+1 on every startup
- Timeout/MaxConnections are now configurable per instance (with type-level defaults)

**Asset model (systar-monitor-core / systar-server)**
- AssetKind decoupled from ordinal: `t_asset.kind` now stores an explicit code (1/2/3/4 = Device/Service/Probe/Control), so enum reordering can no longer corrupt stored data
- Space asset kind removed: devices and services are now top-level assets; AssetStore mounts top-level assets on a neutral tree anchor that never enters the flat asset index, so the anchor cannot appear in `/assets` or statistics; the `/tree` endpoint temporarily returns the first top-level subtree (to be replaced by the grouped forest API `/asset-tree`)
- Create requests for Device/Service carrying a parentId are now rejected explicitly (must be top-level)

### Removed

- Dead spaceId plumbing across ops: the spaceId parameter of the 5 statistics endpoints, the device-ledger filter, the work-order backfill and its `space_id` entity field, `DeviceInfoProvider.resolveSpaceId`, and the dead `DeviceDto.parentId` component (verified reader-less end to end)
- Database schema cleanup (MySQL + H2 scripts): the `t_space` table and its index, the `t_asset.space_id`, `t_device.parent`, `t_service.parent` and `t_work_order.space_id` columns dropped (along with the work-order `i_wo_space` index); seeds rewritten — kind=0 rows deleted, devices/services seeded at top level (parent_id=0), dead space-type code-dictionary entries removed

### Fixed

**Assets & statistics**
- Fixed UI asset creation being blocked: the type-dropdown name filter was lost and the SERVICE mode default was derived incorrectly
- Statistics now log a warn (including the code and the row id) when reading an unknown or corrupt `t_asset.kind` code, instead of skipping it silently

**Statistics frontend (frontend)**
- Fixed all 6 statistics report pages never rendering charts: page templates previously used undeclared string refs (e.g. `ref="pieRef"`), so `useChart()`'s internal `chartRef` never bound to the DOM and `initChart()` silently returned null, leaving chart areas permanently blank (titles/legends only). `useChart` now exposes a `bindChart(el)` entry point (multi-chart pages uniformly use `:ref="xxx.bindChart"`), and `initChart()` reports an explicit error instead of failing silently when unbound
- Demo seeds (03-simulator.sql, both dialects) now include 15 historical alarms from the previous period (days 8–13 ago) so the period-over-period comparison panel shows both bars
- Statistics pages no longer swallow fetch failures: load errors on the 6 overview pages and the trend page now log `console.error` before falling back to the empty state (previously a backend 500 or offline state only showed "no data" with no console clue)

**Asset view (systar-server)**
- Fixed statistics undercounting UI-created assets: `t_asset.parent_id` previously stored the parent's runtime id (e.g. `t_device.id`); it now uniformly stores the parent asset-row id, aligned with seed data and the statistics SQL parent chain; see section 5 of `docs/design/ops-statistics-design.md` for the repair SQL of existing databases

## [1.1.0] - 2026-06-07

Operations business extensions, tech-debt cleanup, data retention policy, frontend UX improvements.

### Added

**Operations (systar-ops)**
- Work orders: alarm-driven auto creation, dispatch, processing, close/cancel lifecycle
- Device ledger: full lifecycle management (procurement → installation → maintenance → decommission)
- Inspection management: plans, tasks, results, scheduling, anomaly-to-work-order linkage
- Statistics: 5-dimension aggregation (alarm/work-order/inspection/device-runtime/maintenance) + dashboard caching + ECharts visualization
- Anomaly detection: Z-Score detection + moving-average trend prediction + weighted health scoring

**System administration (systar-system)**
- 6 controllers (user/role/menu/department/notification/log) + RequirePermission AOP
- Frontend admin pages (users/roles/menus/departments/logs/notifications)

**Virtual probe engine (P1.5)**
- VirtualProbeType / ProbeRef: SpEL expression types + `#probe[id].value` reference resolution
- VirtualProbe class: extends Probe; detect() computes derived values via SpEL expressions
- VirtualProbeEngine: dependency indexing + @EventListener on MonitorResultEvent to trigger recomputation
- Cycle detection: computing Set prevents infinite recursion
- SpEL sandbox: RestrictedSpelContext + property whitelist
- Frontend UI: probe multi-select dropdown + expression extraction button + isVirtual toggle

**Data retention policy (Phase 8)**
- DataRetentionService: monthly batch deletion of expired data by configured retention days
- Scheduled execution + REST API (GET/PUT) + frontend config page
- Safety checks: minimum retention-day limits

**Alarm correlation**
- AlarmCorrelationService: time-window aggregation + alarm suppression
- AlarmPusher: WS alarm broadcast; monitor result gains a type field

**Frontend UX improvements (Phase 1)**
- Grouped navigation menu: submenu groups + alarm tab routing
- NotificationBell component: unread count + alarm list popover
- Breadcrumb component: global breadcrumb navigation
- useKeyboard composable: Esc/Enter shortcut support

**Frontend components**
- EnhancedTable component: sorting/filtering/export (alarm page migrated)
- CronWizard component: visual cron expression editor
- DurationInput component: time-interval editing
- Dashboard tech-style large screen: ECharts ring charts, device online rate, KPI bars, alarm trends, work-order distribution

**MQTT protocol driver**
- Eclipse Paho 1.2.5, MqttClient + topic routing + JSON path extraction

**Protocol driver completion**
- BACnet: APDU encoding completed, upgraded from partial to full implementation
- IEC 104: ASDU encoding completed, upgraded from partial to full implementation

### Changed

- Frontend rebuilt as a standalone Vue3 project (`frontend/`)
- Removed the third-party host directory and proxy modules; the frontend now calls backend APIs directly
- Removed the integrations directory
- Frontend expanded to 11 pages (added dashboard/inspection/ledger/login/operations/system/workorder)
- Linkage engine extensions: CauseType(ALARM/MONITOR) + LinkageRuleBean + CRUD API + dual-tree UI
- Scheduled control: CRUD API (8 endpoints) + start/stop persistence + frontend admin page + CronWizard
- Alarm deduplication: alarms with identical monitorId+level are no longer inserted twice
- WebSocket push extensions: monitor value changes and alarm messages are both pushed

### Tests

- systar-data: coverage 20%→90% (109 test cases)
- systar-ops: coverage 18%→80%+ (17 test files)
- systar-server: coverage 39%→80%+
- systar-system: key service tests
- Frontend: 28 test files, including Vitest unit tests and page-level tests

---

## [1.0.0] - 2026-05-13

Directory restructure + frontend integration completion + alarm filtering.

### Changed

**Directory restructure**
- Core modules moved into `core/` (systar-common, systar-monitor-core, systar-monitor-drivers, systar-data)
- Extension modules moved into `extensions/` (systar-server, systar-websocket)
- Versions unified to 1.0.0 (fixing the root POM / submodule mismatch)

**Frontend & integration completion**
- IoT frontend 7 pages passed functional interaction tests (asset CRUD, monitoring data, alarm filtering, control execution)
- Alarm message filtering (state and recovered parameters threaded through frontend → API → QueryWrapper)
- Startup script health check, stop-before-build, `--force`/`--skip-build` flags

---

## [0.1.0] - 2026-05-10

Initial development release. Basic monitoring engine, protocol driver skeletons, and MySQL/H2 dual-database adaptation completed; the end-to-end pipeline (acquisition → storage → alarming → linkage) works end to end. The core engine and database schema were still in flux.

> **Note**: this version is a development intermediate state; interfaces and configuration may change as porting progresses.

### Added

**Core monitoring engine (systar-monitor-core)**
- Asset model hierarchy: Asset/CompoundAsset/Monitor abstraction layers with Space/Device/Service/Probe/Control concrete implementations
- AssetStore in-memory asset repository supporting tree add/remove/query and path computation
- Two-phase result dispatch: ResultDispatcher synchronous preprocessing + MonitorResultEvent asynchronous dispatch
- Collection scheduler: MonitorScheduler + DetectTask + TaskDispatcher, with per-service-type concurrency limits
- Alarm engine: 3 strategies (ONLY_ONCE/CONTINUOUS/SELECTIVE), multi-level alarms, automatic recovery detection
- Linkage engine: cause-rule matching; monitor value changes trigger control commands
- Scheduled control: cron-expression-driven scheduled task executor
- MonitorServer facade: integrates all subsystems behind a single entry point

**Common utilities (systar-common)**
- AssetIdGenerator: ID generator with a 16-bit station code in the high bits
- CodeDictManager/CodeCatalog/CodeItem: code dictionary management
- SystemConfigManager/SystemConfigItem: system configuration key-value store
- TimeSpan: immutable time-interval utility supporting "10s"/"5m"/"2h"/"1d" formats

**Data access layer (systar-data)**
- 16 MyBatis-Plus entity classes and mappers
- 7 service interfaces and implementations
- SampleRepository/AlarmRepository/LinkageRepository persistence repositories
- MySQL/H2 dual-database adaptation (DatabaseDialect adapter pattern)

**Protocol drivers (systar-monitor-drivers)**
- Modbus TCP: function codes 01-06, register read/write (implemented)
- OPC UA: Eclipse Milo client, NodeId reads (implemented)
- BACnet: BACnet4J, object/property resolvers (partial; APDU encoding missing)
- SNMP: SNMP4J, CommunityTarget GET/GETNEXT (implemented)
- Siemens S7: S7Connector, DB/mark reads + byte type conversion (implemented)
- IEC 104: j60870, TCP connection + YC/YX parsing (partial; ASDU encoding missing)
- Weather: HTTP API, 5-minute cache + JSON parsing (implemented)
- UPS: SNMP, RFC 1628 UPS-MIB standard OIDs (implemented)
- Environmental: TCP server, 25-byte frame decoder (implemented, passive mode)
- WebSocket: Java-WebSocket client + JSON routing (implemented, passive mode)
- TCP/IP: raw socket + connectivity check (implemented)
- Simulate: random/sine/fixed/increment simulation data
- Input: manual data entry (passive mode)

**Server bootstrap (systar-server)**
- Spring Boot application entry + startup lifecycle orchestration
- REST APIs: asset tree, live/historical data, control commands, alarms and linkage rules
- DatabaseAssetLoader: loads the asset tree from the database
- DatabaseDialect + MySQLDialect + H2Dialect adapters
- DatabaseInitializer: DDL and seed data initialization
- Unified response envelope (Result<T>)

**WebSocket (systar-websocket)**
- MonitorWebSocketHandler: per-session subscription + change-detection push
- MonitorResultPusher: bridges MonitorResultEvent to WebSocket

**Tests**
- systar-common: 140 unit tests
- systar-monitor-core: 237 unit tests
- systar-data: 14 H2 integration tests
- systar-monitor-drivers/websocket/server: 104 tests
- JaCoCo coverage plugin integration

**Infrastructure**
- Maven multi-module project structure
- Database initialization scripts (sql/mysql/init.sh, sql/mysql/init.bat)
- Separate MySQL/H2 schema and seed data files

### Fixed

- Asset.metadata thread safety: switched to ConcurrentHashMap
- Asset.setState atomicity: volatile + synchronized added
- Monitor.mode visibility: volatile added
- WebSocket CORS: tightened to localhost
- REST API input validation: MAX_IDS_LENGTH limit added
- Modbus module logging: unified on SLF4J
- LinkageRuleCauseEntity: removed mapping of the non-existent ruleId column
- PassiveService.resultDispatcher type: replaced with ResultDispatcher
- Enum database mapping: MonitorMode.getCode/fromCode + EnumOrdinalTypeHandler
- Seed data TimeSpan format unified to the short format
- Seed data driver_class path fixed
- Non-numeric effectCommand stored as Integer now logs a warning
- Linkage log gained the effectCommand field
- Alarm and linkage queues drained on graceful shutdown

---

## Project origin — 2026-05-09

Project goal established: an independent, general-purpose industrial IoT monitoring and operations core framework.
