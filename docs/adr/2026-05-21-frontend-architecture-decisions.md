# 架构决策记录 (ADR)

> 记录 Systar 前端架构设计中的关键决策及其依据，供后续开发者理解设计意图。

## ADR-001：不引入微前端框架

**日期**：2026-05-21
**状态**：已采纳

### 决策

Systar 监控前端与 RuoYi 管理前端作为**两个独立 SPA**，通过 Nginx 路由统一入口（`/` 和 `/admin/`），不引入 qiankun、wujie、Module Federation 等微前端框架。

### 背景

需要在一个统一系统中同时提供监控运维功能（Systar 前端）和系统管理/业务表单功能（RuoYi 前端）。

### 选项

| 方案 | 构建耦合度 | 外包门槛 | 页面互通 | 复杂度 |
|------|-----------|----------|----------|--------|
| 微前端（qiankun/wujie） | 主应用+子应用，构建独立但运行时耦合 | 需理解生命周期和沙箱 | 无缝嵌入 | 高 |
| Module Federation | Webpack/Vite 构建耦合 | 高 | 组件级共享 | 很高 |
| **独立 SPA + Nginx 路由** | **完全独立** | **低** | **跨页面跳转** | **低** |

### 理由

1. **交互密度低** — 监控运维（dashboard、实时数据）和系统管理（用户、菜单、字典）是两个独立工作流，不需要在同一页面内频繁切换或联动。跨应用跳转已足够
2. **降低外包门槛** — 第三方外协只需懂 RuoYi（MyBatis-Plus + 若依规范），不需要学习微前端框架
3. **独立演进** — Systar 前端和 RuoYi 可以独立发版、独立构建、独立部署，互不干扰
4. **复杂度收益不对等** — 微前端框架的构建配置、沙箱隔离、样式污染、公共依赖抽取等问题，对于两个独立工作流的场景是过度设计
5. **少数内嵌场景用 iframe** — RuoYi 管理页面在 Systar 中只需少量内嵌时用 iframe 承载，简单可靠

### 后果

- 需要跨 SPA 跳转（而非 SPA 内路由切换），但实际使用中两种工作流切换频率低，影响可接受
- iframe 嵌入时需注意 postMessage 通信（如菜单高亮同步）
- 两个 SPA 的公共依赖（Vue 3、Element Plus、ECharts）各自打包，产物体积略大，但通过 CDN 或 gzip 可缓解

---

## ADR-002：JWT 增强替代共享密钥

**日期**：2026-05-21
**状态**：已采纳

### 决策

Systar 后端(8081)从**静态共享密钥认证**（`X-Systar-Token` header）升级为**JWT Bearer Token 认证**，并在 RuoYi 签发 JWT 时注入 `user_id`、`permissions`、`exp` 到 claims。

### 背景

当前 Iot 前端通过 iot-proxy（RuoYi 进程内模块）中转所有 Systar API 请求，Systar 后端只接受一个静态共享密钥。若 Systar 前端直调 Systar 后端，需要 Systar 能独立识别用户身份。

### 理由

1. **消除中转依赖** — Systar 前端直调 Systar 后端，减少 iot-proxy 这一跳，降低延迟和故障点
2. **用户身份穿透** — Systar 后端可从 JWT 获取 userId，用于操作审计日志、设备级权限过滤（第二阶段的 `device_permission` 查询）
3. **权限自包含** — JWT claims 携带权限列表，Systar 无需查 Redis 即可判断用户是否有某项权限
4. **安全升级** — 静态密钥 shared secret 一旦泄露则全部失陷；JWT 有过期时间 (`exp`)，即使泄露影响窗口有限
5. **向前兼容** — 旧 `X-Systar-Token` 作为 fallback 保留，iot-proxy 改传 `Authorization` 头即可

### 后果

- RuoYi `TokenService.createToken()` 需要修改（增加 claims），影响范围小（1 个方法）
- Systar 需要引入 jjwt 依赖（约 40KB）
- JWT 体积增加约 200-500 字节（permissions 列表），在可接受范围内

---

## ADR-003：权限分两阶段迁移

**日期**：2026-05-21
**状态**：已采纳

### 决策

设备级数据权限（`device_permission` 表）的过滤逻辑分两个阶段迁移：

- **第一阶段**：敏感端点仍经 iot-proxy 中转（保留 `AssetPermissionService`），非敏感端点直调 Systar
- **第二阶段**：`device_permission` 表迁移到 Systar 数据库，Systar 侧自行过滤，废弃 iot-proxy 代理层

### 背景

当前 `AssetPermissionService` 运行在 RuoYi 进程中，查询 RuoYi 的 `device_permission` 表实现设备级数据过滤。如果第一阶段就让所有请求直调 Systar，设备过滤链路断裂。

### 中期架构

```
Phase 1:
  浏览器 → Systar frontend
    ├─ /api/dashboard/*             → Systar(8081)  直调（无需设备过滤）
    ├─ /api/monitor/tree            → RuoYi(8080)   iot-proxy 中转（有设备过滤）
    ├─ /api/monitor/probe-values    → RuoYi(8080)   iot-proxy 中转（有设备过滤）
    └─ /ws                          → Systar(8081)  直调（推送已过滤的订阅数据）

Phase 2:
  全部 /api/* → Systar(8081) 直调，Systar 自行过滤
```

### 理由

1. **降低第一阶段风险** — 不改动数据归属和数据库表，仅解耦前端
2. **验证可行后再深入** — Phase 1 验证了 JWT 认证链、前端直调、WebSocket 认证，Phase 2 再动数据库
3. **`device_permission` 归属评估** — 设备权限本质上是监控域的 concept，长期看应属于 Systar 数据库而非 RuoYi 数据库

### 后果

- iot-proxy 在 Phase 1 后仍然存在，但职责从"全部代理"缩减为"权限敏感端点代理"
- Phase 2 需要处理数据迁移（`device_permission` 从 `ruoyi_db` 到 `systar_db`）
