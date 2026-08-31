# 参与 Systar 贡献

[English](CONTRIBUTING.md) | 简体中文

感谢你有意为 Systar 做贡献！本文说明如何搭建开发环境，以及提交变更时需要注意的事项。

## 开发环境

```bash
# 后端——H2 开发 profile（无需外部数据库）
./mvnw spring-boot:run -pl extensions/systar-server -Dspring-boot.run.profiles=dev

# 前端
cd frontend
npm install
npm run dev

# 测试
./mvnw clean test -o      # 后端（离线模式；必须 clean 构建，见下）
cd frontend && npm test   # 前端
```

- 需要 JDK 17（项目基于 Java 17）。
- Maven Wrapper（`./mvnw`）已固定支持的 Maven 版本——无需本地安装 Maven。离线构建
  （`-o`）通过 `lib/maven-repo/` 中的 BACnet4J vendored 产物完成。
- 全量回归必须用 `./mvnw clean test -o`。不加 `clean` 时测试 JVM 跨模块复用，Spring
  应用上下文可能跨模块互相污染，引发间歇性失败。

## 代码规范

- 遵循 **SOLID** 设计原则；在满足需求的前提下，保持实现尽可能简洁、易维护。
- **测试驱动开发**——先写失败的测试。单元测试覆盖率应保持在 90% 以上。
- Java 17，遵循 Java 官方风格指南。标识符与字段命名使用英文。
- magic number 定义为命名常量，不要内联散落。
- 不静默吞掉运行时错误——记录日志或向上抛出。
- 防御式编程聚焦于输入数据缺漏处理（在边界处校验外部输入）。
- 每次修改都从架构全局审视：与周边设计矛盾的局部修复不算完成。

## 数据库变更

Systar 通过方言适配器同时支持 MySQL 和 H2。任何 schema 或种子数据变更必须**同时**
提供两套脚本并保持同步：

- `sql/mysql/ddl/`、`sql/mysql/data/`
- `sql/h2/ddl/`、`sql/h2/data/`

H2 脚本须避免 MySQL 专有语法（`ENGINE=InnoDB`、`COLLATE`、列级 `COMMENT`），并用
`MERGE INTO` 替代 `ON DUPLICATE KEY UPDATE`。

数据库异常必须传播——不允许 catch 后忽略；schema 就绪由初始化器保证，而非业务代码中
的防御性 try/catch。

## 提交变更

1. Fork 仓库并为你的变更创建主题分支。
2. 按上述规范实现，并新增或更新测试。
3. 跑全量测试（`./mvnw clean test -o` 与 `npm test`）并确保通过。单个测试运行时请设置
   较短的超时（挂起的测试就是缺陷）。
4. 提交 Pull Request，说明改了**什么**、**为什么**。缺陷修复请附可复现的场景（输入、
   预期与实际行为）。

提交信息遵循 Conventional Commits 风格（`feat:`、`fix:`、`docs:`、`refactor:`、
`test:`、`chore:` ...），每次提交一个逻辑完整的变更。

## 报告问题

报告缺陷时请包含：涉及的模块、复现的具体步骤或输入、预期行为、实际行为（日志/错误
信息），以及你的环境（JDK 版本、数据库类型、操作系统）。

## 许可证

向 Systar 贡献即表示你同意你的贡献以 GNU General Public License v3.0 only 许可——与
项目本身一致（见 [LICENSE](LICENSE) 与
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)）。
