# 统计聚合管道设计文档

> 目标规模：10万监测器 × 1分钟周期。最后更新：2026-05-26。

## 1. 设计目标

将监测原始采样数据（`t_sample_float/int/bool`）按时序聚合为 HOUR → DAY → WEEK → MONTH 四级统计数据，存入 `t_monitor_stats` 表，支撑趋势图的高效查询。

### 规模约束
- 10 万监测器，平均 1 分钟采样周期
- 每小时 600 万条 raw samples → 10 万条 HOUR 聚合行
- 每天 1.44 亿条 raw samples → 10 万条 DAY 聚合行

## 2. 关键设计决策

### 2.1 全量 SQL，禁逐行循环

**决策**: 聚合和级联都使用单条 SQL 的 `GROUP BY monitor, bucket`，处理全量数据。不逐 monitor 循环。

**理由**: 10 万 monitor × 逐条 SQL = 30 万次 DB 往返（3 表），在 5 分钟窗口内不可完成。全量 SQL = 3 次往返。

**反模式** (不可用):
```java
for (int monitorId : activeMonitors) {
    aggregateOneMonitor(monitorId);  // 10 万次 SQL！
}
```

**正确模式**（HOUR 聚合实际为跨库，须分两步——详见第 4 节；单库内级联仍可一条 SQL）:
```sql
-- 单库内 GROUP BY 一次性聚合（DAY/WEEK/MONTH 级联模式）
INSERT INTO t_monitor_stats (...)
SELECT monitor, ..., AVG(value), MAX(value), MIN(value), COUNT(*)
FROM t_monitor_stats WHERE granularity = ? AND bucket_start BETWEEN ? AND ?
GROUP BY monitor, <bucketExpr>
```

### 2.2 统计库与主库分离

**决策**: `t_monitor_stats` 存入独立的 DataSource（`db_systar_stats` / `jdbc:h2:mem:db_systar_stats`）。

**理由**:
- 统计数据是追加型写入（写后不变），主库是 CRUD 型
- 统计查询是大范围扫描（`bucket_start BETWEEN`），主库查询是点查
- 共享 I/O 会相互影响
- 分离后统计库可以独立扩容、备份、迁移

**Bean 命名**:

`StatisticsDataConfig` 必须**同时显式声明**两个 JdbcTemplate Bean，缺一不可：

| Bean 名 | 注解 | 数据源 | 用途 |
|--------|------|--------|------|
| `mainJdbcTemplate` | `@Primary` | Spring Boot 自动配置的主 `DataSource` | 查询 raw samples、probe/control 元数据；写入主库 |
| `statsJdbcTemplate` | （无） | 本类内部构造的 `db_systar_stats` `HikariDataSource` | 读写 `t_monitor_stats` |

**为什么 `mainJdbcTemplate` 必须显式声明**：仅声明 `statsJdbcTemplate` 时，Spring Boot 的 `JdbcTemplateAutoConfiguration` 会因 `@ConditionalOnMissingBean(JdbcOperations)` 失败而**不生成主库 JdbcTemplate**。未加 `@Qualifier` 的注入会拿到 `statsJdbcTemplate`，查询主库表时报 "Table not found"。

**注入侧规则**：systar-ops 模块内所有 `JdbcTemplate` 注入位点**必须**用 `@Qualifier` 显式选定，禁止依赖 `@Primary` 隐式回退。`@Primary` 仅作为意外漏写时的安全网。

```java
// ✅ 正确
public TrendService(@Qualifier("mainJdbcTemplate") JdbcTemplate jdbc) { ... }
public MonitorStatsAggregator(
        @Qualifier("mainJdbcTemplate") JdbcTemplate mainJdbc,
        @Qualifier("statsJdbcTemplate") JdbcTemplate statsJdbc,
        DatabaseDialect dialect) { ... }

// ❌ 错误：隐式回退，难调试
public TrendService(JdbcTemplate jdbc) { ... }
```

### 2.3 单线程顺序级联

**决策**: 用单线程 `@Async` Executor（core=1, max=1）顺序执行 DAY→WEEK→MONTH 级联。

**理由**: 100K 行的 GROUP BY + UPSERT < 5 秒/级，3 级 < 15 秒。对比 300 秒调度间隔，单线程完全够用。多线程带来的并发控制复杂度无收益。

### 2.4 增量级联（非全表重算）

**决策**: 每次 HOUR 聚合后，只级联受影响的 DAY/WEEK/MONTH 桶。

**理由**: 全表重算 DAY = 扫描全部 HOUR 行（随时间增长无限膨胀）。增量 = 只算今天的 DAY、本周的 WEEK、本月的 MONTH，O(1)。

**实现**: 级联 SQL 的 WHERE 条件限定了时间范围：
```sql
WHERE granularity = 1 AND bucket_start BETWEEN ? AND ?
```

### 2.5 批量追赶（非逐小时循环）

**决策**: 重启后用单条 SQL 填补所有缺失小时，上限 7 天（168 小时）。

**理由**: 单条 SQL 的 GROUP BY 天然支持多小时范围（`moment BETWEEN start AND end`）。7 天 × 600 万/小时 = 4200 万行，在有索引的情况下可在 30-60 秒内完成。

### 2.6 加权平均聚合

**决策**: 从 HOUR 级联到 DAY 时，使用加权平均而非简单平均。

**公式**: `avg_val = SUM(avg_val × sample_count) / SUM(sample_count)`

**理由**: 不同 HOUR 桶可能有不同的样本数（例如某个小时采集器离线，只有 30 个样本）。简单平均会赋予每小时相同权重，导致偏差。

### 2.7 GROUP BY 而非 Window Functions

**决策**: 聚合器使用 `GROUP BY monitor, bucket` 而非 `AVG() OVER (PARTITION BY ...)` 窗口函数。

**理由**: 
- GROUP BY 一次得到去重结果，无需外层 `SELECT DISTINCT`
- 窗口函数在 600 万行上开窗内存开销大
- GROUP BY 的聚合语义更清晰

## 3. 架构

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  @Scheduled(fixedRate=300_000)    @Async("statsCascade") │
│                                                          │
│  aggregateHourly()                   cascadeAsync()      │
│    │                                     │               │
│    ├─ 3 条 SQL (float/int/bool)          │               │
│    │  GROUP BY monitor, HOUR bucket      │               │
│    │  → INSERT INTO stats.t_monitor_stats│               │
│    │                                     │               │
│    │  收集 affected day range            │               │
│    └─────────────────────────────────────┘               │
│                                          │               │
│                           cascadeDays(start, end)        │
│                             1 条 SQL: HOUR→DAY            │
│                           GROUP BY monitor, DATE(bucket)  │
│                                          │               │
│                           cascadeWeeks(start, end)        │
│                             1 条 SQL: DAY→WEEK            │
│                                          │               │
│                           cascadeMonths(start, end)       │
│                             1 条 SQL: DAY→MONTH           │
│                                                          │
│  @PostConstruct(+delay 5s)                               │
│  catchUpOnStartup() — 同 aggregateHourly 逻辑            │
│    追赶窗口: MAX_CATCH_UP_HOURS = 168                    │
│    AtomicBoolean catchUpInProgress 防并发                │
└──────────────────────────────────────────────────────────┘
```

## 4. 核心 SQL

### HOUR 聚合（按 sample_table 分别执行）

**跨库实现要点**（实施版）：HOUR 聚合是**跨库操作**——SELECT 走 `mainJdbcTemplate`（主库的 `t_sample_*`），INSERT 走 `statsJdbcTemplate`（统计库的 `t_monitor_stats`）。不可在一条 SQL 内做 JOIN，须分两步：

1. `mainJdbc.query(...)` 从主库读出 `(monitor, bucket, avg, max, min, count)` 行集
2. `statsJdbc.batchUpdate(...)` 批量 upsert 到统计库

```sql
-- 步骤 1（mainJdbcTemplate）：在主库内完成 GROUP BY
SELECT monitor,
       <hourlyBucketExpr> AS bucket,
       AVG(value), MAX(value), MIN(value), COUNT(*)
FROM t_sample_float
WHERE moment >= ? AND moment < ?
GROUP BY monitor, <hourlyBucketExpr>

-- 步骤 2（statsJdbcTemplate）：批量 upsert
INSERT INTO t_monitor_stats
  (monitor, bucket_start, bucket_end, granularity,
   avg_val, max_val, min_val, sample_count, updated_at)
VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
   avg_val = VALUES(avg_val), max_val = VALUES(max_val),
   min_val = VALUES(min_val), sample_count = VALUES(sample_count),
   updated_at = VALUES(updated_at)
```

### DAY 级联（HOUR→DAY，加权平均，单库内一条 SQL）
```sql
INSERT INTO t_monitor_stats
  (monitor, bucket_start, bucket_end, granularity,
   avg_val, max_val, min_val, sample_count, updated_at)
SELECT monitor,
       DATE(bucket_start) AS bucket_start,
       DATE(bucket_start) AS bucket_end,  -- Java 层补全为 bucket_start + 1d
       2 AS granularity,
       SUM(avg_val * sample_count) / NULLIF(SUM(sample_count), 0),
       MAX(max_val), MIN(min_val), SUM(sample_count), ?
FROM t_monitor_stats
WHERE granularity = 1 AND bucket_start BETWEEN ? AND ?
GROUP BY monitor, DATE(bucket_start)
ON DUPLICATE KEY UPDATE
   avg_val = VALUES(avg_val), max_val = VALUES(max_val),
   min_val = VALUES(min_val), sample_count = VALUES(sample_count),
   updated_at = VALUES(updated_at)
```

### WEEK/MONTH 级联（从 DAY 聚合，同理）

## 5. 异常处理策略

| 场景 | 处理 |
|------|------|
| 某 sample 表聚合失败 | catch + log.error，不影响其他表 |
| **某 sample 表不存在**（如 `t_sample_bool` 未在 schema 中创建） | catch + log.warn（首次），后续静默跳过；continue 处理下一张表 |
| 某级联失败 | catch + log.error，不影响后续级联 |
| 追赶失败 | catch + log.error，不影响定时聚合 |
| 追赶进行中，定时触发 | 检测 catchUpInProgress → skip + log.debug |
| 级联进行中，新触发 | 检测 cascadeInProgress → skip + log.debug |
| @Scheduled 线程异常 | Spring 自动重试下一个周期 |

## 6. 索引策略

```sql
-- 已有（趋势图阶段）
UNIQUE KEY (monitor, granularity, bucket_start)  -- upsert 去重
INDEX (granularity, bucket_start)                 -- 级联 WHERE 条件扫描

-- 建议（production）
INDEX (monitor, moment) ON t_sample_float/int/bool  -- HOUR 聚合扫描
```

## 7. 配置

```yaml
# application.yml
systar:
  statistics:
    week-start-day: MONDAY          # 周开始日配置
    max-catch-up-hours: 168         # 重启追赶上限
  stats-datasource:
    url: jdbc:h2:mem:db_systar_stats;DB_CLOSE_DELAY=-1;MODE=MySQL
    username: sa
    password:
    driver-class-name: org.h2.Driver
```

### 测试环境额外约束

`@SpringBootTest` 用的 `application-test.yml` 两个 H2 URL **必须**带 `DB_CLOSE_DELAY=-1`：

```yaml
spring:
  datasource:
    # DB_CLOSE_DELAY=-1: keep in-memory DB alive for the test JVM lifetime
    url: jdbc:h2:mem:testdb;MODE=MySQL;...;DB_CLOSE_DELAY=-1
systar:
  stats-datasource:
    url: jdbc:h2:mem:testdb_stats;MODE=MySQL;...;DB_CLOSE_DELAY=-1
```

理由：H2 在最后一个连接关闭时默认销毁内存库。Spring Boot 在 `spring.sql.init` 执行 schema 后到连接池稳定前存在短暂连接窗口，缺此参数会概率性"Table not found"。本约束是防御性的——真正的 Bean 绑定问题已由第 2.2 节的 `@Qualifier` 规则解决。

## 8. 主要实现类索引

| 类型 | 位置 |
|------|------|
| `StatisticsDataConfig` | `extensions/systar-ops/.../ops/config/` — 声明 `mainJdbcTemplate@Primary`、`statsJdbcTemplate`、`statsCascadeExecutor` |
| `MonitorStatsAggregator` | `extensions/systar-ops/.../ops/statistics/aggregator/` — `@Scheduled` 入口、跨库 HOUR 聚合、`@Async` 级联、追赶 |
| `TrendBucketExpr` | `extensions/systar-ops/.../ops/statistics/mapper/` — 按方言生成桶表达式 |
| `TrendService` | `extensions/systar-ops/.../ops/statistics/service/` — 查询统计结果给趋势图 |
| stats schema | `StatisticsDataConfig.CREATE_STATS_TABLE_SQL` 内联（统计库自建，不在主 schema-*.sql 中）|
