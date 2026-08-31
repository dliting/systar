# 统计报表前端交互测试文档

> 版本：v1.0 | 更新：2026-05-24
> 测试环境：Vite dev server (5173) + Systar (8081) + H2

## 测试结果摘要

| 编号 | 测试项 | 结果 |
|------|--------|------|
| TC-STATS-01 | 页面导航与标题 | ✅ PASS |
| TC-STATS-02 | 日期范围选择器默认值 | ✅ PASS |
| TC-STATS-03 | 日期范围变更触发刷新 | ✅ PASS |
| TC-STATS-04 | 粒度选择器（4 选项） | ✅ PASS |
| TC-STATS-05 | 粒度切换触发刷新 | ✅ PASS |
| TC-STATS-06 | Tab 列表（5 个） | ✅ PASS |
| TC-STATS-07 | Tab 切换自动加载数据 | ✅ PASS |
| TC-STATS-08 | 告警统计卡片渲染 | ✅ PASS |
| TC-STATS-09 | 告警统计 ECharts 图表 | ✅ PASS |
| TC-STATS-10 | 工单统计卡片渲染 | ✅ PASS |
| TC-STATS-11 | 工单老化分布 | ✅ PASS |
| TC-STATS-12 | 工单统计 ECharts 图表 | ✅ PASS |
| TC-STATS-13 | 巡检统计卡片渲染 | ✅ PASS |
| TC-STATS-14 | 巡检统计 ECharts 图表 | ✅ PASS |
| TC-STATS-15 | 设备运行表格与数据 | ✅ PASS |
| TC-STATS-16 | 维护统计卡片渲染 | ✅ PASS |
| TC-STATS-17 | 维护统计 ECharts 图表 | ✅ PASS |
| TC-STATS-18 | 空数据状态 | ✅ PASS |
| TC-STATS-19 | 无 NaN/undefined 值 | ✅ PASS |
| TC-STATS-20 | 窗口 resize 图表自适应 | ✅ PASS |
| TC-STATS-21 | 日期超出 365 天限制 | ✅ PASS |
| TC-STATS-22 | 全 Tab 零控制台错误 | ✅ PASS |
| TC-STATS-23 | API 路径正确（/api/ops/statistics/） | ✅ PASS |
| TC-STATS-24 | API 参数完整（日期+粒度） | ✅ PASS |
| TC-STATS-25 | Dashboard 缓存验证 | ✅ PASS |

---

## 1. 导航与页面结构

### TC-STATS-01 页面导航与标题

**步骤**：
1. 登录后点击顶部导航"统计报表"
2. 观察页面 URL 和标题

**预期结果**：
- [x] URL 变为 `/statistics`
- [x] 页面标题显示 "/// 统计报表 STATISTICS_V1.0"
- [x] 顶部导航中"统计报表"高亮

---

## 2. 筛选器

### TC-STATS-02 日期范围选择器默认值

**步骤**：
1. 首次进入统计报表页面
2. 观察日期选择器

**预期结果**：
- [x] 开始日期默认为 7 天前
- [x] 结束日期默认为今天
- [x] 选择器类型为 `daterange`

### TC-STATS-03 日期范围变更触发刷新

**步骤**：
1. 修改开始日期为更早的日期（如 2026-05-10）
2. 按 Enter 或失焦确认

**预期结果**：
- [x] 触发新的 API 请求（含新日期参数）
- [x] 当前 Tab 数据刷新
- [x] 卡片数值更新

### TC-STATS-04 粒度选择器（4 选项）

**步骤**：
1. 点击粒度下拉框

**预期结果**：
- [x] 显示 4 个选项：按天、按小时、按周、按月
- [x] 默认选中"按天"

### TC-STATS-05 粒度切换触发刷新

**步骤**：
1. 选择"按周"
2. 观察数据变化

**预期结果**：
- [x] 触发新的 API 请求（`granularity=WEEK`）
- [x] 当前 Tab 数据按周重新聚合
- [x] 趋势图按周显示

---

## 3. Tab 面板

### TC-STATS-06 Tab 列表（5 个）

**步骤**：
1. 观察 Tab 栏

**预期结果**：
- [x] 显示 5 个 Tab：告警统计、工单统计、巡检统计、设备运行、维护统计
- [x] 首次加载默认激活"告警统计"

### TC-STATS-07 Tab 切换自动加载数据

**步骤**：
1. 点击"工单统计"Tab（首次访问）
2. 点击"巡检统计"Tab（首次访问）
3. 点击"设备运行"Tab（首次访问）
4. 点击"维护统计"Tab（首次访问）

**预期结果**：
- [x] 每个 Tab 首次访问时自动发起对应 API 请求
- [x] 数据加载完成后渲染卡片和图表
- [x] 再次点击已加载 Tab 时仅重新渲染图表，不重复请求

---

## 4. 告警统计 Tab

### TC-STATS-08 告警统计卡片渲染

**步骤**：
1. 激活"告警统计"Tab
2. 等待数据加载

**预期结果**：
- [x] 显示 4 个统计卡片：
  - 告警总数
  - 待处理
  - 处理率（百分比）
  - 趋势点数
- [x] 所有数值不含 NaN 或 undefined
- [x] 无数据时显示 0（不报错）

### TC-STATS-09 告警统计 ECharts 图表

**步骤**：
1. 观察图表区域

**预期结果**：
- [x] 柱状图渲染成功（`echarts.init` + `setOption`）
- [x] X 轴显示日期（period）
- [x] Y 轴显示数量（value）
- [x] 图表使用暗色主题（dark）
- [x] Tooltip 样式为暗色半透明背景
- [x] 图表高度 350px

---

## 5. 工单统计 Tab

### TC-STATS-10 工单统计卡片渲染

**步骤**：
1. 激活"工单统计"Tab
2. 等待数据加载

**预期结果**：
- [x] 显示 4 个统计卡片：
  - 进行中（CREATED + ASSIGNED + PROCESSING 之和）
  - MTTR（平均修复时间，单位小时）
  - SLA达标率（百分比）
  - 本期工单（当前时段内创建的工单数）
- [x] MTTR 显示格式为 "Xh"
- [x] SLA 显示格式为 "X%"

### TC-STATS-11 工单老化分布

**步骤**：
1. 观察卡片下方老化分布区域

**预期结果**：
- [x] 显示标题"工单老化分布"
- [x] 显示 4 个区间：
  - < 24h（创建后 24 小时内）
  - 24-72h（1-3 天）
  - 72h-7d（3-7 天）
  - > 7d（超过 7 天）
- [x] 每个区间显示对应的开放工单数
- [x] 无数据时各区间显示 0

### TC-STATS-12 工单统计 ECharts 图表

**步骤**：
1. 观察图表区域

**预期结果**：
- [x] 柱状图渲染成功
- [x] 显示工单创建趋势
- [x] 图表在 Tab 切换时正确销毁和重建

---

## 6. 巡检统计 Tab

### TC-STATS-13 巡检统计卡片渲染

**步骤**：
1. 激活"巡检统计"Tab

**预期结果**：
- [x] 显示 4 个统计卡片：
  - 任务总数
  - 完成率（百分比）
  - 异常数
  - 异常率（百分比）
- [x] 完成率 = COMPLETED / TOTAL
- [x] 异常率 = ABNORMAL / TOTAL

### TC-STATS-14 巡检统计 ECharts 图表

**步骤**：
1. 观察图表区域

**预期结果**：
- [x] 柱状图渲染成功
- [x] 显示巡检完成趋势

---

## 7. 设备运行 Tab

### TC-STATS-15 设备运行表格与数据

**步骤**：
1. 激活"设备运行"Tab
2. 观察统计卡片和设备列表

**预期结果**：
- [x] 显示 3 个统计卡片：设备总数、在线设备、可用率
- [x] 下方显示设备在线率明细表格
- [x] 表格列：设备名称、在线天数、统计天数、在线率
- [x] 所有设备的在线率无 NaN 或 undefined
- [x] 仅统计 lifecycle_status = 'IN_SERVICE' 的设备
- [x] 设备数为 7（种子数据中的设备数）
- [x] 无在线采样数据时在线天数显示 0

---

## 8. 维护统计 Tab

### TC-STATS-16 维护统计卡片渲染

**步骤**：
1. 激活"维护统计"Tab

**预期结果**：
- [x] 显示 3 个统计卡片：
  - 维护次数
  - 总费用（元，保留 2 位小数）
  - 维护类型数
- [x] 无数据时总费用显示 "0.00"

### TC-STATS-17 维护统计 ECharts 图表

**步骤**：
1. 观察图表区域

**预期结果**：
- [x] 柱状图渲染成功
- [x] 显示维护频率趋势

---

## 9. 边界与错误处理

### TC-STATS-18 空数据状态

**步骤**：
1. 在无数据的日期范围内观察各 Tab

**预期结果**：
- [x] 卡片数值显示 0（不崩溃）
- [x] 趋势图显示空（无柱状条）
- [x] 无异常或错误提示

### TC-STATS-19 无 NaN/undefined 值

**步骤**：
1. 遍历所有 5 个 Tab
2. 检查所有渲染的数值

**预期结果**：
- [x] 处理率、完成率、异常率、可用率、SLA达标率均显示 "0.0%" 而非 "NaN%"
- [x] MTTR 显示 "0.0h" 而非 "undefinedh"
- [x] 设备在线率显示 "0.0%" 而非 "NaN%"

### TC-STATS-20 窗口 resize 图表自适应

**步骤**：
1. 在任意有图表的 Tab 下
2. 调整浏览器窗口大小

**预期结果**：
- [x] ECharts 图表自动 resize
- [x] 无 JavaScript 异常
- [x] window resize 事件监听器在离开页面时正确清理

### TC-STATS-21 日期超出 365 天限制

**步骤**：
1. 尝试选择跨度 > 365 天的日期范围
2. 观察 API 响应

**预期结果**：
- [x] 前端 `disabledDate` 阻止选择超出 365 天范围的日期
- [x] 若绕过前端限制（如手动修改 URL），后端返回 400 错误

---

## 10. 控制台与网络

### TC-STATS-22 全 Tab 零控制台错误

**步骤**：
1. 从登录页开始，导航到统计报表
2. 切换所有 5 个 Tab
3. 变更日期和粒度各一次
4. 调整窗口大小一次
5. 检查浏览器控制台

**预期结果**：
- [x] console.error 级别日志：0 条
- [x] 无 JavaScript 运行时异常
- [x] 无 404 资源加载失败（除 favicon.ico）
- [x] 无 Reactivity 警告

### TC-STATS-23 API 路径正确

**步骤**：
1. 打开 Network 面板
2. 观察所有统计相关请求

**预期结果**：
- [x] 所有请求基础路径为 `/api/ops/statistics/`
- [x] 请求包含 `Authorization: Bearer <token>` 头
- [x] 所有请求返回 HTTP 200

### TC-STATS-24 API 参数完整

**步骤**：
1. 检查请求 URL 参数

**预期结果**：
- [x] 每个请求包含 `startDate`（yyyy-MM-dd 格式）
- [x] 每个请求包含 `endDate`（yyyy-MM-dd 格式）
- [x] 每个请求包含 `granularity`（DAY/HOUR/WEEK/MONTH）
- [x] 参数值正确编码

---

## 11. 缓存验证

### TC-STATS-25 Dashboard 缓存验证

**步骤**：
1. 连续两次调用 `GET /api/ops/statistics/dashboard`
2. 观察后端日志或响应时间

**预期结果**：
- [x] 第一次调用执行完整查询（H2 环境约 70ms）
- [x] 第二次调用命中缓存（5 分钟内不重新查询数据库）
- [x] Dashboard 数据结构正确（alarms + workOrders + inspections + devices + topAlarmDevices）

---

## 附录：API 端点清单

| 端点 | 方法 | 参数 | 缓存 |
|------|------|------|------|
| `/api/ops/statistics/alarm` | GET | startDate, endDate, deviceId?, spaceId?, granularity? | 无 |
| `/api/ops/statistics/work-order` | GET | 同上 | 无 |
| `/api/ops/statistics/inspection` | GET | 同上 | 无 |
| `/api/ops/statistics/device-runtime` | GET | 同上 | 无 |
| `/api/ops/statistics/maintenance` | GET | 同上 | 无 |
| `/api/ops/statistics/dashboard` | GET | 无参数 | 5 分钟 (Caffeine) |

## 附录：前端文件清单

| 文件 | 说明 |
|------|------|
| `src/api/iot/statistics.js` | API 请求模块（6 个函数） |
| `src/views/statistics/index.vue` | 统计报表页面组件 |
| `src/router/index.js` | 添加 `/statistics` 路由 |
| `src/components/layout/index.vue` | 添加"统计报表"导航项 |
