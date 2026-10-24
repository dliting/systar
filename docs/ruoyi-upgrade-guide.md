# RuoYi 版本升级指南

## 前置条件

| 组件 | 版本要求 | 检查命令 |
|------|---------|---------|
| JDK | 17+ | `java -version` |
| Maven | 3.6.3+ (RuoYi 编译需要) | `mvn -version`（IntelliJ 内置 Maven 3.9.9 可用） |
| Node.js | 18+ | `node -v` |
| MySQL | 8.x | `mysqladmin ping -h localhost` |
| Redis | 任意版本 | `redis-cli ping` |

## 升级前备份

升级前需保存以下 Systar 专属文件（它们不在 RuoYi 官方仓库中）：

| 备份项 | 位置 | 说明 |
|--------|------|------|
| IoT 前端源码 | `extensions/iot-frontend/src/` | 软链接目标，不受 RuoYi 替换影响（**无需备份**） |
| IoT 代理模块 | `extensions/iot-proxy/` | 不受 RuoYi 替换影响（**无需备份**） |
| IoT 菜单 SQL | `integrations/sql/iot_menu.sql` | 需重新导入到 `ry-vue` 数据库 |
| 权限表 SQL | `integrations/sql/iot_device_permission.sql` | 需重新导入到 `ry-vue` 数据库 |
| Systar 配置 | `integrations/config/application-systar.yml` | 需放入 RuoYi classpath |
| Systar 启动脚本 | `bin/start-systar.bat/.sh` | 不受影响 |
| 统一启动脚本 | `bin/start-all.bat/.sh` | 不受影响 |

> **核心原则**：Systar 专属文件全部位于 `extensions/`、`integrations/`、`bin/`、`docs/` 目录下，`ThirdParty/Ruoyi/` 目录中只有软链接（前端）和 Maven module 引用（后端）。替换 RuoYi 时不会丢失任何 Systar 代码。

---

## 步骤 1：替换 RuoYi 后端

### 1.1 停止现有服务
```bash
# Windows
bin\stop-all.bat

# Linux
bin/stop-all.sh
```

### 1.2 备份并替换
```bash
# 删除旧 RuoYi 后端（iot-proxy 源码在 extensions/ 中，安全）
rm -rf ThirdParty/Ruoyi/backend

# 解压/克隆新版 RuoYi 后端
git clone https://gitee.com/y_project/RuoYi-Vue.git ThirdParty/Ruoyi/backend-tmp
# 或解压官方发行包
```

### 1.3 重新关联 iot-proxy 模块

编辑 `ThirdParty/Ruoyi/backend/pom.xml`，在 `<modules>` 中添加：
```xml
<module>../../../extensions/iot-proxy</module>
```

编辑 `ThirdParty/Ruoyi/backend/ruoyi-admin/pom.xml`，确认依赖存在：
```xml
<dependency>
    <groupId>com.ruoyi</groupId>
    <artifactId>ruoyi-iot</artifactId>
    <version>${ruoyi.version}</version>
</dependency>
```

### 1.4 重新应用 Systar 配置

复制 `integrations/config/application-systar.yml` 到 `ThirdParty/Ruoyi/backend/ruoyi-admin/src/main/resources/`

编辑 `ThirdParty/Ruoyi/backend/ruoyi-admin/src/main/resources/application.yml`，添加 profile：
```yaml
spring:
  profiles:
    include: druid, systar
```

### 1.5 验证后端编译
```bash
cd ThirdParty/Ruoyi/backend
mvn clean compile -pl ruoyi-admin -am
```

---

## 步骤 2：替换 RuoYi 前端

### 2.1 备份并替换
```bash
# 删除旧 RuoYi 前端
rm -rf ThirdParty/Ruoyi/frontend

# 解压/克隆新版 RuoYi 前端
# （从官方仓库获取）
```

### 2.2 创建 IoT 前端软链接

**Windows**（不需要管理员权限，使用目录联结）：
```batch
cd extensions\iot-frontend\bin
link-ruoyi.bat
```

**Linux**：
```bash
cd extensions/iot-frontend/bin
bash link-ruoyi.sh
```

> **原理**：`ThirdParty/Ruoyi/frontend/src/api/iot/` 和 `src/views/iot/` 是软链接/联结，指向 `extensions/iot-frontend/src/` 中的真实文件。Vite 构建时自动跟随软链接发现 IoT 页面，无需修改 RuoYi 任何代码。

### 2.3 验证前端编译
```bash
cd ThirdParty/Ruoyi/frontend
npm install
npm run dev
```
浏览器访问 `http://localhost:5173/iot/asset`，确认页面正常加载。

---

## 步骤 3：重新导入数据库

### 3.1 IoT 菜单和权限
```bash
# MySQL 命令行导入
mysql -u root -p ry-vue < integrations/sql/iot_menu.sql
mysql -u root -p ry-vue < integrations/sql/iot_device_permission.sql
```

### 3.2 验证
```sql
-- 确认菜单已注册
SELECT menu_id, menu_name FROM sys_menu WHERE menu_id >= 2000;
-- 应返回 2000~2007 + 按钮权限条目
```

---

## 步骤 4：完整验证

对照 `docs/test/iot-frontend-ui-test.md` 执行 13 个交互测试用例：

1. 登录 (`admin` / `admin123`)
2. 资产管理 — 32 条资产记录加载
3. 实时监控 — 数据查询 + 历史趋势
4. 告警管理 — 规则展示 + 筛选功能
5. 联动规则 — 展开行显示
6. 控制面板 — 命令执行
7. 点表导入/工单管理 — 占位页面
8. Systar 不可达错误提示
9. 权限控制

---

## 常见问题

### Q: 软链接看起来无效？
**Windows**: 确认使用 `mklink /J`（目录联结），不是 `mklink /D`（需要管理员）。运行 `dir` 应显示 `<JUNCTION>`。
**Linux**: 运行 `ls -la` 确认软链接目标路径正确。

### Q: Vite 找不到 IoT 页面？
确认软链接创建正确。Vite 的 `import.meta.glob` 默认跟随软链接。如果仍失败，尝试删除 `node_modules/.vite` 缓存后重启。

### Q: 后端编译找不到 iot-proxy？
确认 `ThirdParty/Ruoyi/backend/pom.xml` 中 `<module>` 的**相对路径**正确（`../../../extensions/iot-proxy`，从 `backend/` 目录上升三级到项目根）。

### Q: 菜单显示但页面空白？
检查 `sys_menu` 表的 `component` 字段是否正确指向 `iot/xxx/index`（与软链接后的路径匹配）。检查 `is_frame=1`（否则路由 path 会缺少 `/` 前缀）。
