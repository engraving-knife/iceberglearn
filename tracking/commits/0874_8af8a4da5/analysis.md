# 提交 0874：Spark: Backport support for Aggregate push down for incremental scan to Spark 3.4 (#10561)

## 提交信息

- **序号**：0874 / 4088
- **哈希**：8af8a4da5efcdaecdafb59766a9325fe3f3f5043
- **短哈希**：8af8a4da5
- **日期**：2024-06-25 10:21:23 -0600
- **作者**：Huaxin Gao
- **提交说明**：Spark: Backport support for Aggregate push down for incremental scan to Spark 3.4 (#10561)
- **PR/Issue**：#10561

## 总体目的

Iceberg 的 Spark 集成支持 aggregate push down（聚合下推）：当查询只涉及 `min`/`max`/`count` 这类可以利用文件统计信息（column stats）直接计算的聚合时，可以把聚合操作下推到 Iceberg 读取层，避免把所有数据行读入 Spark 再聚合，从而显著提升查询性能。

但此前的实现存在一个限制：当用户使用增量扫描（incremental scan，通过 `start-snapshot-id` / `end-snapshot-id` 指定快照区间）时，聚合下推被显式跳过——`SparkScanBuilder` 中有 `if (readConf.startSnapshotId() != null) { LOG.info("Skipping aggregate pushdown: incremental scan is not supported"); return false; }`。这意味着增量扫描场景下的 `min/max/count` 查询不得不读取全部数据行，性能浪费明显。

本提交将 main 分支上已经为 Spark 3.5 实现的"增量扫描支持聚合下推"能力回迁（backport）到 Spark 3.4 模块，使两个版本行为一致。核心做法是重构 `SparkScanBuilder` 中 scan 构建逻辑的代码结构，让普通 batch scan 与增量 scan 共享同一套 scan 构建入口，从而都能选择性地附带 column stats 用于聚合下推。

## 如何达成设计目的

整体设计思路：将原本"散落在 aggregate push down 判定逻辑内、独立构建一份带 column stats 的 TableScan"的代码，重构为"复用统一的 `buildIcebergBatchScan(withStats, expectedSchema)` 入口"，让聚合下推判定与最终 scan 构建走同一条路径。这样无论最终走 batch scan 还是 incremental append scan，只要 `withStats=true`，都会带上 column stats，从而支持聚合下推。

具体步骤：
1. 抽取一个新方法 `buildIcebergBatchScan(boolean withStats, Schema expectedSchema)`，统一负责根据 readConf 中的 snapshotId / asOfTimestamp / branch / tag / startSnapshotId / endSnapshotId 等参数，决定走 `buildBatchScan` 还是 `buildIncrementalAppendScan`，并把 `withStats` 与 `expectedSchema` 透传下去。
2. `buildBatchScan` 与 `buildIncrementalAppendScan` 增加 `withStats` 和 `expectedSchema` 参数；当 `withStats=true` 时调用 `scan.includeColumnStats()`。
3. 把这两个方法返回值从 `SparkBatchQueryScan`（已包装）改为底层 `org.apache.iceberg.Scan`（未包装），把"包装成 `SparkBatchQueryScan`"的职责上移到 `buildBatchScan()`（无参版本，用于真正构造 Spark 扫描）和 aggregate push down 判定路径中，避免重复包装。
4. aggregate push down 判定方法中，删除原本独立构建 `TableScan` 的代码，改为调用 `buildIcebergBatchScan(true, schemaWithMetadataColumns())` 复用统一入口；同时移除"增量扫描不支持聚合下推"的早返回逻辑。
5. 删除不再使用的 `readSnapshot()` 私有方法和 `TableScan` 导入。

测试侧：在 `TestAggregatePushDown` 中新增 `testAggregatePushDownForIncrementalScan`，验证带 `start-snapshot-id` / `end-snapshot-id` 的增量扫描查询 `min/max/count` 时确实走了 `LocalTableScan`（即下推成功）；同时在 `TestDataSourceOptions` 中扩展既有增量扫描测试，补充对 min/max 聚合的断言。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：重构 scan 构建逻辑，让增量扫描也能携带 column stats，从而支持聚合下推。

**工作逻辑**：

- **移除导入**：删除 `import org.apache.iceberg.TableScan;`，因为不再单独构造 `TableScan`。
- **aggregate push down 判定方法**（约 232 行附近）：原本独立创建 `TableScan scan = table.newScan().includeColumnStats();`，并手动 `useSnapshot`、`configureSplitPlanning`、`filter`。重构后改为调用 `buildIcebergBatchScan(true /* include Column Stats */, schemaWithMetadataColumns())`，复用统一入口。这样判定逻辑与实际 scan 构建一致，避免两份代码漂移。
- **移除增量扫描早返回**：删除 `if (readConf.startSnapshotId() != null) { LOG.info("Skipping aggregate pushdown: incremental scan is not supported"); return false; }`，从而允许增量扫描走聚合下推路径。
- **移除 `readSnapshot()` 私有方法**：该方法此前用于 aggregate push down 判定中获取 snapshot，重构后不再需要。
- **新增 `buildIcebergBatchScan(boolean withStats, Schema expectedSchema)`**：这是新的统一入口。它读取 readConf 中的各种 snapshot 相关参数，判断 `startSnapshotId != null` 时调用 `buildIncrementalAppendScan(startSnapshotId, endSnapshotId, withStats, expectedSchema)`，否则调用 `buildBatchScan(snapshotId, asOfTimestamp, branch, tag, withStats, expectedSchema)`。返回类型为 `org.apache.iceberg.Scan`（未包装）。
- **重写无参 `buildBatchScan()`**：现在它先调用 `schemaWithMetadataColumns()` 计算期望 schema，再调用 `buildIcebergBatchScan(false, expectedSchema)`，最后把返回的底层 `Scan` 包装成 `SparkBatchQueryScan` 返回。
- **修改 `buildBatchScan(Long, Long, String, String)` → `buildBatchScan(Long, Long, String, String, boolean, Schema)`**：增加 `withStats` 与 `expectedSchema` 参数；当 `withStats=true` 时调用 `scan.includeColumnStats()`；返回值改为底层 `Scan`（调用 `configureSplitPlanning(scan)` 直接返回），不再在此处包装 `SparkBatchQueryScan`。
- **修改 `buildIncrementalAppendScan(long, Long)` → `buildIncrementalAppendScan(long, Long, boolean, Schema)`**：增加 `withStats` 与 `expectedSchema` 参数；当 `withStats=true` 时调用 `scan.includeColumnStats()`；返回值改为底层 `Scan`，不再包装 `SparkBatchQueryScan`。

整体效果：包装 `SparkBatchQueryScan` 的职责统一到无参 `buildBatchScan()` 一处，aggregate push down 判定与最终 scan 构建共享同一份 scan 构建代码，增量扫描自然获得 column stats 能力。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/source/TestDataSourceOptions.java`

**修改目的**：扩展既有增量扫描测试，补充对 min/max 聚合的断言，验证增量扫描下聚合查询的正确性。

**工作逻辑**：
- 新增 `import org.apache.spark.sql.functions;`。
- 把原本一次性 `collectAsList` 的测试改为先保存 `Dataset<Row> unboundedIncrementalResult`，再分别做 `collectAsList`、`count`、`agg(min("id"), max("id"))` 三组断言。
- 把 `Assert.assertEquals` 替换为 `assertThat(...).as(...).isEqualTo(...)` 风格（AssertJ）。
- 对有界增量扫描（start + end snapshot）也补充 min/max 聚合断言。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java`

**修改目的**：新增 `testAggregatePushDownForIncrementalScan` 测试，验证增量扫描场景下聚合下推确实生效。

**工作逻辑**：
- 新增导入 `SparkReadOptions`、`Dataset`、`Row`、`ExplainMode`、`functions`。
- 新测试方法 `testAggregatePushDownForIncrementalScan`：
  - 创建表并分 4 次插入数据，记录每次插入后的 snapshotId。
  - 构造一个有界增量扫描（`start-snapshot-id=snapshotId2`, `end-snapshot-id=snapshotId3`）的 `min/max/count` 聚合查询，通过 `queryExecution().explainString(...)` 断言执行计划中包含 `LocalTableScan`、`min(data)`、`max(data)`、`count(data)`，证明聚合被下推到 Iceberg 层（LocalTableScan 表示数据已作为本地常量返回，无需扫描文件）。同时断言聚合结果为 `{-7777, 8888, 2L}`。
  - 构造一个无界增量扫描（仅 `start-snapshot-id=snapshotId1`）的聚合查询，同样断言执行计划走 `LocalTableScan`，结果为 `{-7777, 9999, 6L}`。

## 小结

- **成效**：Spark 3.4 模块下的增量扫描（包括有界和无界）现在支持 `min/max/count` 聚合下推，与 Spark 3.5 行为对齐；通过 `LocalTableScan` 执行计划验证下推确实生效，避免读取底层文件数据。
- **影响范围**：仅涉及 `spark/v3.4` 模块，包含 1 个主代码文件（`SparkScanBuilder.java`）和 2 个测试文件。
- **回迁到 1.4.x 的注意事项**：本提交本身就是一次"回迁"（从 Spark 3.5 回迁到 Spark 3.4）。若 1.4.x 分支包含 Spark 3.4 模块且该能力尚未存在，可直接 cherry-pick 此提交。需要注意：1.4.x 分支的 Spark 3.4 代码基线与本提交所基于的 main 分支可能存在差异，cherry-pick 后需确认 `SparkScanBuilder` 中相关方法签名与调用点是否一致；测试文件中使用的 AssertJ `assertThat` 风格需确认 1.4.x 已引入相应依赖。本改动属于功能增强而非 Bug 修复，回迁优先级中等，需结合 1.4.x 版本策略判断。
