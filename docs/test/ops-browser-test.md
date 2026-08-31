# Systar 运维模块 浏览器交互测试文档

> 可重用前端交互测试，覆盖工单管理、设备台账、巡检管理 3 个模块的完整用户操作流程。

## 环境准备

```bash
# 1. 启动后端 (port 8081, H2 内存库)
./mvnw spring-boot:run -pl extensions/systar-server -Dspring-boot.run.profiles=dev -o

# 2. 启动前端 (port 5173, proxy /api → 8081)
cd frontend && npm run dev
```

## 认证

前端使用 JWT Bearer + X-Systar-Token 双重认证。测试时在浏览器 Console 执行：

```js
// 生成 JWT (密钥: changeme-default-secret)
async function genJwt() {
  const secret = new TextEncoder().encode('changeme-default-secret');
  const key = await crypto.subtle.importKey('raw', secret, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const header = { alg: 'HS256' };
  const payload = { sub: 'admin', user_id: 1, permissions: '*', iat: ~~(Date.now()/1000), exp: ~~(Date.now()/1000)+86400 };
  const b64 = s => btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
  const hb = b64(JSON.stringify(header)), pb = b64(JSON.stringify(payload));
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(hb+'.'+pb));
  const sb = btoa(String.fromCharCode(...new Uint8Array(sig))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=/g,'');
  return hb+'.'+pb+'.'+sb;
}
genJwt().then(jwt => { localStorage.setItem('Admin-Token', jwt); location.href = '/workorder'; });
```

---

## 测试用例

### 1. 工单管理 (`/workorder`)

| # | 操作 | 预期结果 |
|---|------|---------|
| 1.1 | 导航到 `/workorder` | 页面标题 "/// 工单管理 WORK_ORDER_V1.0"，4 张统计卡片显示数值 |
| 1.2 | 查看工单列表 | 表格显示 ID/工单号/标题/类型/设备/优先级/状态/负责人/截止时间 |
| 1.3 | 状态筛选 | 选择"已关闭"，表格只显示 CLOSED 工单 |
| 1.4 | 类型筛选 | 选择"维修"，表格只显示 REPAIR 工单 |
| 1.5 | 点击"新建工单" | 弹窗含标题/类型/设备ID/优先级/描述字段 |
| 1.6 | 填写必填字段提交 | 弹窗关闭，列表刷新显示新工单，status=CREATED |
| 1.7 | 点击"分配"按钮 | 分配弹窗，输入 assigneeId，提交后 status→ASSIGNED |
| 1.8 | 点击"处理"按钮 | status→PROCESSING |
| 1.9 | 点击"关闭"按钮 | 关闭弹窗，输入 resolution 必填，status→CLOSED |
| 1.10 | 点击"取消"按钮 | 取消弹窗，输入 comment 必填，status→CANCELLED |
| 1.11 | 切换到统计 Tab | 4 张统计卡片 + ECharts 柱状图 |

**测试结果**: ✅ 全部通过

### 2. 设备台账 (`/ledger`)

| # | 操作 | 预期结果 |
|---|------|---------|
| 2.1 | 导航到 `/ledger` | 页面标题 "/// 设备台账 DEVICE_LEDGER_V1.0"，统计卡片显示 |
| 2.2 | 查看设备列表 | 表格显示 ID/名称/型号/序列号/状态/部门/责任人/上次维护 |
| 2.3 | 生命周期筛选 | 选择"服务中"，只显示 IN_SERVICE 设备 |
| 2.4 | 展开行 | 显示扩展属性表 + 最近维护时间线 |
| 2.5 | 点击"属性" | 弹窗显示 key-value 编辑，可增删行 |
| 2.6 | 保存属性 | 弹窗关闭，展开行刷新显示新属性 |
| 2.7 | 点击"维护" | 弹窗创建维护记录(type/title/description/performerId) |
| 2.8 | 创建维护记录 | 弹窗关闭，维护记录已创建 |
| 2.9 | 切换到统计 Tab | 显示 4 张统计卡片(总数/服务中/维修中/已退役) |

**测试结果**: ✅ 全部通过

### 3. 巡检管理 (`/inspection`)

| # | 操作 | 预期结果 |
|---|------|---------|
| 3.1 | 导航到 `/inspection` | 页面标题 "/// 巡检管理 INSPECTION_V1.0" |
| 3.2 | 查看计划列表 | 表格显示 ID/名称/Cron/状态/默认负责人/自动建工单 |
| 3.3 | 点击"新建计划" | 弹窗含名称/Cron/启用/负责人/自动建工单/检查项列表/设备列表 |
| 3.4 | 添加检查项 | 动态增删行(itemName/itemType/expectedValue) |
| 3.5 | 添加设备 | 输入 deviceId 添加设备 tag |
| 3.6 | 保存计划 | 弹窗关闭，列表刷新 |
| 3.7 | 点击"详情/编辑" | 弹窗加载计划完整数据(含设备+模板) |
| 3.8 | 点击"删除" | 确认弹窗，删除后列表刷新 |
| 3.9 | 切换到任务 Tab | 任务列表显示 ID/任务编号/计划ID/状态/负责人/时间 |
| 3.10 | 任务状态筛选 | 选择"待执行"，仅显示 PENDING 任务 |
| 3.11 | 点击"开始" | status→IN_PROGRESS |
| 3.12 | 点击"提交结果" | 结果弹窗，每行选 NORMAL/ABNORMAL/SKIPPED+填实际值 |
| 3.13 | 提交结果 | 弹窗关闭 |
| 3.14 | 点击"完成" | status→COMPLETED |
| 3.15 | 点击"取消" | 输入原因，status→CANCELLED |

**测试结果**: ✅ 全部通过

---

## 页面截图

- 工单管理: `workorder-final.png`
- 所有页面使用科技风暗色主题 (#0a0e17 背景 + #4fc3f7 浅蓝色强调)
- 统计卡片带 glow 动画和数字递增
- 处理中状态有 pulse 动画
- 等宽字体 (Courier New/Source Code Pro)

## 更新记录

- 2026-05-23: 初始版本，覆盖 3 模块全部功能点
