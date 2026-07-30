# 提交 2949：Spark 4.0, Core: Add Limit pushdown to Scan (#14615)

## 提交信息

- **序号**：2949 / 4088
- **哈希**：74a11607435ec332e557323b78c51726a7ef2fdf
- **短哈希**：74a116074
- **日期**：2025-12-03
- **作者**：Eduard Tudenhoefner
- **提交说明**：Spark 4.0, Core: Add Limit pushdown to Scan (#14615)
- **PR/Issue**：#14615

## 总体目的

当 Spark 执行 `SELECT * FROM t LIMIT N` 时，如果数据源 scan 不支持 limit 下推，Spark 只能在读完后做全局 limit，意味着 Iceberg 仍会扫描全部匹配文件、读取所有行，再由 Spark 丢弃多余行——这对大表上的小 limit 查询是明显的浪费。Spark DataSource V2 提供了 `SupportsPushDownLimit` 接口，允许 scan builder 接收 limit 并把它下推到数据源，由数据源在文件规划阶段就少读一些数据。

本提交为 Iceberg 的 scan 体系新增一个"最小行数提示"语义的 `minRowsRequested(long)` API，并让 Spark 4.0 的 `SparkScanBuilder` 实现 `SupportsPushDownLimit`：当 Spark 调用 `pushLimit(n)` 时，Iceberg 把 limit 记下来，在最终构建 scan 时调用 `scan.minRowsRequested(limit)`，把它存入 `TableScanContext`，作为后续文件规划的可选优化提示。命名为 "minRowsRequested" 而非 "limit" 是有意为之——它是一个 hint，数据源可以返回略多或略少的行（取决于能否精确满足），文档明确说明"entirely optional"，避免给实现方过强的约束。这是 Spark 4.0 + Core 的版本；后续 #14741 会把同样的 Spark 侧改动 backport 到 3.4/3.5。

## 如何达成设计目的

设计分两层。API/Core 层：在 `Scan` 接口加 `default minRowsRequested`（默认抛 `UnsupportedOperationException`，保持向后兼容），在 `BaseScan` 实现它（通过 `context.minRowsRequested` 复制出新的 refined scan），在 `BatchScanAdapter` 委托给被包装的 scan，在 `TableScanContext` 新增 `minRowsRequested` 字段及对应的 builder 方法。Spark 4.0 层：`SparkScanBuilder` 实现 `SupportsPushDownLimit`，新增 `limit` 字段、`pushLimit` 方法（返回 true 表示接受下推），并在 `buildBatchScan` 路径里把 limit 透传到 scan。测试覆盖 scan 上下文是否带上 `minRowsRequested`、以及端到端 `SELECT ... LIMIT n` 行为正确。

## 修改详情

### `api/src/main/java/org/apache/iceberg/Scan.java` (+13/-0 lines)

**修改目的**：在 `Scan` 接口新增 `minRowsRequested` 方法，定义 limit 下推的公共 API。

**工作逻辑**：
新增 `default ThisT minRowsRequested(long numRows)`，默认实现抛 `UnsupportedOperationException(this.getClass().getName() + " doesn't implement minRowsRequested")`，与同文件中 `metricsReporter` 的默认实现风格一致。Javadoc 说明：返回"至少"给定行数的文件，是一个 hint、完全可选，实际可能返回更少（scan 不够多）或更多。default 抛异常保证旧实现不受影响、编译兼容。

### `api/src/main/java/org/apache/iceberg/BatchScanAdapter.java` (+5/-0 lines)

**修改目的**：让 `BatchScanAdapter`（把 `BatchScan` 适配成 `Scan`）委托 `minRowsRequested` 给被包装的 scan。

**工作逻辑**：
实现 `minRowsRequested` 为 `return new BatchScanAdapter(scan.minRowsRequested(numRows))`，保持适配器的"转发即不变量"模式——和它对 `metricsReporter` 的实现完全对称，确保任何实现了 `BatchScan` 的底层 scan 的 limit 下推能力都能透过适配器暴露。

### `core/src/main/java/org/apache/iceberg/BaseScan.java` (+5/-0 lines)

**修改目的**：为所有基于 `TableScanContext` 的 scan 实现提供 `minRowsRequested` 的通用实现。

**工作逻辑**：
`@Override public ThisT minRowsRequested(long numRows) { return newRefinedScan(table, schema, context.minRowsRequested(numRows)); }`——`newRefinedScan` 是 `BaseScan` 已有的工厂方法，用来基于新的 context 构造一个同类型的 refined scan。这样所有继承 `BaseScan` 的 scan（`DataTableScan`、各种 metadata table scan 等）都自动获得 limit 下推支持，无需各自重写。

### `core/src/main/java/org/apache/iceberg/TableScanContext.java` (+7/-0 lines)

**修改目的**：在 scan 上下文中持久化 `minRowsRequested` 值。

**工作逻辑**：
新增 `@Nullable public abstract Long minRowsRequested()` 字段（由 Immutable 自动生成 builder），以及 `TableScanContext minRowsRequested(long numRows)` 方法，该方法用 `ImmutableTableScanContext.builder().from(this).minRowsRequested(numRows).build()` 复制出带新值的 context——这是 `TableScanContext` 所有其他字段（`snapshotId`、`branch` 等）的标准写法，保证 context 不可变。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+15/-1 lines)

**修改目的**：让 Spark 4.0 的 `SparkScanBuilder` 实现 `SupportsPushDownLimit`，把 Spark 的 limit 下推到 Iceberg scan。

**工作逻辑**：
- 类签名追加 `implements ..., SupportsPushDownLimit`，并导入 `SupportsPushDownLimit`。
- 新增字段 `private Integer limit = null`（用 `Integer` 区分"未设置"与"0"）。
- 在 `buildBatchScan`（构造最终 scan 的方法）里，所有 split/选项配置完成后追加 `if (null != limit) { configuredScan = configuredScan.minRowsRequested(limit.longValue()); }`，把 limit 应用到 scan 上。
- 实现 `@Override public boolean pushLimit(int pushedLimit) { this.limit = pushedLimit; return true; }`——返回 `true` 表示 Iceberg 接受这次下推。注意此处并未重写 `isPartiallyPushed()`，因此 Spark 仍会在 Iceberg 返回的行上再做一次 limit，保证结果精确（测试注释里也强调"verify that LIMIT is properly applied in case SupportsPushDownLimit.isPartiallyPushed() is ever overridden"，防御未来行为变化）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestFilteredScan.java` (+94/-0 lines)

**修改目的**：验证 `pushLimit` 后各类 scan 的 context 都带上 `minRowsRequested`。

**工作逻辑**：
新增两个测试。`limitPushedDownToSparkScan`：用 `assumeThat(fileFormat).isEqualTo(PARQUET)` 跳过非 Parquet 格式以减少测试矩阵，构造 `SparkScanBuilder` 调 `pushLimit(23)`，断言 builder 的 `limit` 字段为 23；然后分别 `build()`（batch scan）、`buildChangelogScan()`、`buildCopyOnWriteScan()`、`buildMergeOnReadScan()`，用 AssertJ 的 `extracting("scan").extracting("context").extracting("minRowsRequested")` 链式断言 context 中 `minRowsRequested` 等于 23。对 LOCAL planning mode 还多 `extracting("scan")` 一层以适配包装结构，体现对不同 planning mode 的兼容性。`limitPushedDownToSparkScanForMetadataTable`：对 `#snapshots` 元数据表做同样的断言，验证元数据表 scan 也支持 limit 下推。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/sql/TestSelect.java` (+13/-0 lines)

**修改目的**：端到端验证 `SELECT ... LIMIT n` 结果正确。

**工作逻辑**：
`selectWithLimit` 向表插入三行 `(1,'a',1.0)`、`(2,'b',2.0)`、`(3,'c',NaN)`，分别执行 `LIMIT 1/2/3`，断言返回前 N 行。注释说明这是为了"verify that LIMIT is properly applied in case `SupportsPushDownLimit.isPartiallyPushed()` is ever overridden"——即不论 Iceberg 是否返回精确行数，Spark 侧都会保证最终 limit 正确，防止下推后结果偏差。

## 总结

本提交通过在 `Scan` 接口与 `TableScanContext` 引入可选的 `minRowsRequested` 提示，并在 Spark 4.0 的 `SparkScanBuilder` 实现 `SupportsPushDownLimit`，使 `LIMIT N` 查询能下推到 Iceberg scan 规划阶段，减少不必要的数据读取。API 设计为 hint 语义、默认抛异常，保证向后兼容；测试同时覆盖 scan 上下文与端到端 SQL 行为，为后续 backport 到 Spark 3.4/3.5 打下基础。
