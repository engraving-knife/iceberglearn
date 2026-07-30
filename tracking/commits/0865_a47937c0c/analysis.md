# 提交 0865：Spark 3.5: Support Aggregate push down for incremental scan (#10538)

## 提交信息

- **序号**：0865 / 4088
- **哈希**：a47937c0c1fcafe57d7dc83551d8c9a3ce0ab1b9
- **短哈希**：a47937c0c
- **日期**：2024-06-21（Fri Jun 21 10:34:13 2024 -0700）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark 3.5: Support Aggregate push down for incremental scan (#10538)
- **PR/Issue**：#10538

## 总体目的

Iceberg Spark 3.5 已经支持聚合下推（aggregate push down），即把 `min/max/count` 等聚合操作下推到 Iceberg 的扫描层，利用 manifest 中的列统计（column stats）直接计算结果，从而避免读取数据文件本体。但该能力此前对增量扫描（incremental scan，即 `start-snapshot-id`/`end-snapshot-id`）显式不支持——`SparkScanBuilder` 中 `doCheckBoundAggregate` 一旦发现 `readConf.startSnapshotId() != null`，就直接 `LOG.info("Skipping aggregate pushdown: incremental scan is not supported")` 返回 `false`，强制所有增量扫描查询走全文件读取再聚合的路径。

对于在增量场景下做 `SELECT min(x), max(x), count(x) FROM ...` 这类查询（例如增量数据校验、监控统计），下推缺失意味着大量本可省下的 I/O 仍然要发生，性能损失明显。本提交的目的是把聚合下推能力扩展到 incremental append scan：让 Iceberg 在增量扫描时也能基于 manifest 的列统计计算 min/max/count，并在物理计划上呈现为 `LocalTableScan`（直接返回统计结果，不读取数据文件）。

此外，本提交还顺手对 `SparkScanBuilder` 的 scan 构建做了重构——抽出统一的 `buildIcebergBatchScan(boolean withStats, Schema expectedSchema)` 方法，把原本散落在 `buildBatchScan(...)` 与 `buildIncrementalAppendScan(...)` 中的重复 `SparkBatchQueryScan` 包装逻辑收敛，让 `withStats`（是否带 column stats）成为正交开关，既能为聚合下推路径提供 stats，也能为普通读取路径省略 stats。

## 如何达成设计目的

整体设计包含两块：

1. **解除 incremental scan 的聚合下推限制**：移除 `doCheckBoundAggregate` 中对 `readConf.startSnapshotId() != null` 的早返回，让增量扫描也能通过聚合下推检查。

2. **重构 scan 构建路径以支持 stats 注入**：原先 `buildBatchScan()` 既创建 scan 又包装为 `SparkBatchQueryScan`，并且与 `buildBatchScan(...)`、`buildIncrementalAppendScan(...)` 之间有重复代码。重构后：
   - 新增 `buildIcebergBatchScan(boolean withStats, Schema expectedSchema)` 作为底层入口，根据 `withStats` 决定是否调用 `scan.includeColumnStats()`，按 `startSnapshotId` 是否非空选择 `buildBatchScan` 或 `buildIncrementalAppendScan`，返回原始的 `org.apache.iceberg.Scan`（不包装 `SparkBatchQueryScan`）。
   - `buildBatchScan()`（无参顶层方法）改为调用 `buildIcebergBatchScan(false, schemaWithMetadataColumns())` 再包装成 `SparkBatchQueryScan`。
   - 聚合下推检查路径 `doCheckBoundAggregate` 中，原来手写构造 `TableScan + includeColumnStats + useSnapshot + filter` 改为调用 `buildIcebergBatchScan(true, schemaWithMetadataColumns())`，从而让"是否带 stats"对增量扫描和普通扫描都生效。
   - `readSnapshot()` 私有方法由于不再被引用，被一并删除；`TableScan` 的 import 也被移除。

这样聚合下推路径与普通扫描路径共用底层 scan 构建，stats 开关统一通过参数控制，行为一致。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java`

**修改目的**：(1) 解除 incremental scan 的聚合下推限制；(2) 重构 scan 构建以共用 stats 注入逻辑。

**工作逻辑**：

1. 在 `doCheckBoundAggregate` 中，移除原先 4 行 `LOG.info("Skipping aggregate pushdown: incremental scan is not supported"); return false;`，删除 `if (readConf.startSnapshotId() != null)` 判断块。

2. 在聚合下推 plan 阶段，原先手动构造 scan：

```java
TableScan scan = table.newScan().includeColumnStats();
Snapshot snapshot = readSnapshot();
if (snapshot == null) {
  LOG.info("Skipping aggregate pushdown: table snapshot is null");
  return false;
}
scan = scan.useSnapshot(snapshot.snapshotId());
scan = configureSplitPlanning(scan);
scan = scan.filter(filterExpression());
```

改为调用统一方法：

```java
org.apache.iceberg.Scan scan =
    buildIcebergBatchScan(true /* include Column Stats */, schemaWithMetadataColumns());
```

注意：原来聚合下推 plan 还会显式校验 `snapshot == null`，重构后这个判断被下沉到 `buildIcebergBatchScan` 内部由 scan API 自身处理（或由后续 `planFiles()` 抛错），代码因此更紧凑。

3. 删除 `readSnapshot()` 方法（已不再被引用）。

4. 新增 `buildIcebergBatchScan(boolean withStats, Schema expectedSchema)` 方法，承载原 `buildBatchScan()` 中"构造底层 Iceberg scan（不带 `SparkBatchQueryScan` 包装）"的逻辑：

```java
private org.apache.iceberg.Scan buildIcebergBatchScan(boolean withStats, Schema expectedSchema) {
  Long snapshotId = readConf.snapshotId();
  Long asOfTimestamp = readConf.asOfTimestamp();
  String branch = readConf.branch();
  ...
  if (startSnapshotId != null) {
    return buildIncrementalAppendScan(startSnapshotId, endSnapshotId, withStats, expectedSchema);
  } else {
    return buildBatchScan(snapshotId, asOfTimestamp, branch, tag, withStats, expectedSchema);
  }
}
```

5. 原 `buildBatchScan()`（无参）改为：

```java
private Scan buildBatchScan() {
  Schema expectedSchema = schemaWithMetadataColumns();
  return new SparkBatchQueryScan(
      spark,
      table,
      buildIcebergBatchScan(false /* not include Column Stats */, expectedSchema),
      readConf,
      expectedSchema,
      filterExpressions,
      metricsReporter::scanReport);
}
```

6. 原 `buildBatchScan(Long, Long, String, String)` 改签名加 `withStats` 和 `expectedSchema` 参数，去掉内部 `SparkBatchQueryScan` 包装，改为返回原始 `org.apache.iceberg.Scan`：

```java
private org.apache.iceberg.Scan buildBatchScan(
    Long snapshotId, Long asOfTimestamp, String branch, String tag,
    boolean withStats, Schema expectedSchema) {
  BatchScan scan = newBatchScan()...;
  if (withStats) {
    scan = scan.includeColumnStats();
  }
  ...
  return configureSplitPlanning(scan);
}
```

7. 原 `buildIncrementalAppendScan(long, Long)` 改签名加 `withStats` 和 `expectedSchema` 参数，同样去掉 `SparkBatchQueryScan` 包装、返回原始 scan，并按 `withStats` 决定是否 `includeColumnStats()`。

8. 移除 `import org.apache.iceberg.TableScan;`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/source/TestDataSourceOptions.java`

**修改目的**：在增量扫描测试中补充聚合下推验证，确认 `min/max/count` 在 incremental scan 上能正确返回。

**工作逻辑**：

1. 新增 `import org.apache.spark.sql.functions;`。
2. 在原增量扫描测试用例中：
   - 把第一段（unbounded 增量 `(snapshot3, current]`）从直接 `collectAsList` 改为先保存 `Dataset<Row> unboundedIncrementalResult`，再分别做 `collectAsList`、`count`、`agg(min, max)` 三组断言；新增断言 `count == 3`、`min(id) == 2`、`max(id) == 4`。
   - 把第二段（bounded 增量 `(snapshot2, snapshot1]`）变量重命名为 `incrementalResult`、`result2`，并新增 `agg(min, max)` 断言：`min(id) == 3`、`max(id) == 3`。
3. 这些断言验证增量扫描能正确返回数据并支持聚合（虽然该用例不是直接验证 pushdown 物理计划，但确认了行为正确性）。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/sql/TestAggregatePushDown.java`

**修改目的**：新增专门的测试用例，验证聚合下推在增量扫描下确实生效（物理计划为 `LocalTableScan`，且结果正确）。

**工作逻辑**：新增 `testAggregatePushDownForIncrementalScan` 方法：

1. 建表 `id LONG, data INT`，连续插入 4 批数据，记录三个 snapshot id（`snapshotId1/2/3`）。
2. **bounded incremental + agg push down**：以 `START_SNAPSHOT_ID=snapshotId2, END_SNAPSHOT_ID=snapshotId3` 读表并做 `agg(min, max, count)`。通过 `queryExecution().explainString(...)` 断言物理计划包含 `LocalTableScan`、`min(data)`、`max(data)`、`count(data)`，证明聚合被下推到 Iceberg 层未读取数据文件；并断言结果为 `[-7777, 8888, 2L]`。
3. **unbounded incremental + agg push down**：以 `START_SNAPSHOT_ID=snapshotId1` 读表并做同样 `agg`。断言物理计划含 `LocalTableScan`，结果为 `[-7777, 9999, 6L]`。

引入的 import：`SparkReadOptions`、`Dataset`、`Row`、`ExplainMode`、`functions`。

## 小结

- **成效**：Spark 3.5 的增量扫描（bounded 与 unbounded）现在都支持 min/max/count 聚合下推，物理计划为 `LocalTableScan`，无需读取数据文件，显著降低增量场景下统计类查询的 I/O 成本；同时 `SparkScanBuilder` 的 scan 构建被重构为统一入口，stats 注入通过参数控制，代码更简洁一致。
- **影响范围**：仅 Spark 3.5 模块。主代码 1 个文件（`SparkScanBuilder.java`，89 行改动，包含重构净减少约 18 行），测试 2 个文件（`TestDataSourceOptions.java`、`TestAggregatePushDown.java`）。共 109 行新增、62 行删除。
- **回迁到 1.4.x 的注意事项**：本提交是 Spark 3.5 专属功能增强，**回迁价值取决于 1.4.x 是否需要支持增量扫描聚合下推**。如果 1.4.x 分支基于的 Spark 3.5 路径与 main 一致，回迁是可行的且相对低风险——改动集中在 `SparkScanBuilder` 单文件且无 API 变化。注意事项：(1) 需确认 1.4.x 上 `SparkScanBuilder` 此处代码结构与 main 一致，否则需手动适配重构；(2) 测试中新增的 `testAggregatePushDownForIncrementalScan` 依赖 `validationCatalog.loadTable`、`ExplainMode` 等，1.4.x 测试基类需具备相应能力；(3) 该改动只影响 Spark 3.5 模块，不影响 Spark 3.3/3.4 或 core 模块；(4) 与 #10373（提交 0861/0863）的清理逻辑修复无冲突，可独立回迁。
