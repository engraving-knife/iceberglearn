# 提交 3279：Spark 4.1: Add BaseSparkScanBuilder (#15360)

## 提交信息

- **序号**：3279 / 4088
- **哈希**：d35a1f995d3efb687a6a451a864cbe1ff46a5a33
- **短哈希**：d35a1f995
- **日期**：2026-02-18
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 4.1: Add BaseSparkScanBuilder (#15360)
- **PR/Issue**：#15360

## 总体目的

本提交在 Spark 4.1 模块中抽取一个新的抽象基类 `BaseSparkScanBuilder`，用于承载 Spark 扫描构建器（scan builder）的通用功能，消除 `SparkScanBuilder` 与 `SparkStagedScanBuilder` 之间大量重复代码。在此之前，这两个扫描构建器各自维护着几乎相同的逻辑：投影裁剪（`pruneColumns`）、谓词下推（`pushPredicates`/`pushedPredicates`）、limit 下推（`pushLimit`）、元数据列处理（`projectionWithMetadataColumns`/`calculateMetadataSchema`）、分片规划配置（`configureSplitPlanning`），以及一组相同的字段（`spark`、`table`、`readConf`、`caseSensitive`、`projection`、`filters`、`metricsReporter`、`limit` 等）。两份实现细节上还存在细微差异（例如 `SparkStagedScanBuilder.pruneColumns` 不带 filter 和大小写参数），既增加维护成本，也容易导致行为不一致。

引入 `BaseSparkScanBuilder` 后，通用字段与方法上移到基类，两个子类只需保留各自特有的扫描构建逻辑（`SparkScanBuilder` 仍负责批量扫描、增量扫描、changelog 扫描、merge-on-read/copy-on-write 扫描及聚合下推；`SparkStagedScanBuilder` 仍负责 staged scan）。基类的设计有意不实现任何可选的 Spark mix-in 接口（如 `SupportsPushDownV2Filters` 等），而是把必要逻辑以普通方法提供，让每个具体子类自行声明它支持的接口组合，从而保证灵活性。这是 Spark 4.1 扫描层重构的第一步，为后续把更多扫描能力（如冲突检测、统计上报等）统一到基类奠定基础。

## 如何达成设计目的

新增抽象类 `BaseSparkScanBuilder implements ScanBuilder`，集中通用字段与逻辑，并通过 `protected` 访问器（`spark()`、`table()`、`schema()`、`projection()`、`readConf()`、`caseSensitive()`、`filters()`、`filter()`、`metricsReporter()`）暴露给子类。`SparkScanBuilder` 与 `SparkStagedScanBuilder` 改为继承该基类，删除重复字段与方法，将原先直接字段访问改为调用基类访问器。同时移除 `SparkScanBuilder` 中已不再需要的 `caseSensitive(boolean)` setter，相应地调整测试代码。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/BaseSparkScanBuilder.java` (+270/-0 lines)

**修改目的**：新增承载扫描构建器通用功能的抽象基类。

**工作逻辑**：
类声明为 `abstract class BaseSparkScanBuilder implements ScanBuilder`，类注释明确指出有意不实现可选 mix-in 接口，让子类自行选择适用功能。字段包括 `spark`、`table`、`schema`、`readConf`、`caseSensitive`、`metaFieldNames`（LinkedHashSet）、`metricsReporter`（`InMemoryMetricsReporter`）、`projection`、`filters`（`List<Expression>`）、`pushedPredicates`（`Predicate[]`）、`limit`。提供两个 `protected` 构造方法（带/不带 branch）。

通用方法包括：
- `pruneColumns(StructType)`：将请求字段分为元数据列与数据列，数据列组成 `requestedDataType` 后调用 `SparkSchemaUtil.prune(projection, requestedDataType, filter(), caseSensitive)` 更新 projection。相比原 `SparkStagedScanBuilder` 版本多了 filter 与大小写参数，但因 staged scan 不下推 filter，`filter()` 返回 `alwaysTrue`，行为等价。
- `pushPredicates(Predicate[])`：把 Spark V2 谓词转换为 Iceberg `Expression`，绑定到 projection，按是否能完全由 Iceberg 求值（`ExpressionUtil.selectsPartitions`）分为可下推与 post-scan 两组，返回 post-scan 谓词。
- `pushedPredicates()`、`pushLimit(int)`：标准下推接口实现。
- `filter()`：将 `filters` 列表归约为合取表达式，替代原 `SparkScanBuilder.filterExpression()`。
- `projectionWithMetadataColumns()`：合并 projection 与元数据 schema。
- `calculateMetadataSchema()`：处理分区列与数据列字段 ID 冲突（`TypeUtil.reassignConflictingIds`），逻辑从 `SparkScanBuilder` 原样上移。
- `configureSplitPlanning(T scan)`：按 `readConf` 设置 `SPLIT_SIZE`/`SPLIT_LOOKBACK`/`SPLIT_OPEN_FILE_COST` 及 `minRowsRequested(limit)`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkScanBuilder.java` (+35/-321 lines)

**修改目的**：改为继承 `BaseSparkScanBuilder`，删除重复字段与方法。

**工作逻辑**：
类声明由 `implements ScanBuilder, SupportsPushDownAggregates, SupportsPushDownV2Filters, SupportsPushDownRequiredColumns, SupportsReportStatistics, SupportsPushDownLimit` 改为 `extends BaseSparkScanBuilder implements SupportsPushDownV2Filters, SupportsPushDownRequiredColumns, SupportsReportStatistics, SupportsPushDownLimit, SupportsPushDownAggregates`（接口顺序调整，`ScanBuilder` 由基类实现）。删除字段 `spark`、`table`、`readConf`、`metaFieldNames`、`metricsReporter`、`projection`、`caseSensitive`、`filterExpressions`、`pushedPredicates`、`limit`，构造方法改为 `super(spark, table, schema, branch, options)`。删除方法 `filterExpression()`、`caseSensitive(boolean)`、`pushPredicates`、`pushedPredicates`、`pruneColumns`、`prune`、`projectionWithMetadataColumns`、`calculateMetadataSchema`、`metaFields`、`findPartitionField`、`allUsedFieldIds`、`configureSplitPlanning`、`pushLimit`。方法体内原先直接引用字段的处全部改为调用 `spark()`、`table()`、`readConf()`、`caseSensitive()`、`projection()`、`filters()`、`filter()`、`metricsReporter()`。例如 `buildIcebergBatchScan` 中 `readConf.snapshotId()` 改为 `readConf().snapshotId()`，`filterExpression()` 改为 `filter()`，`filterExpressions` 改为 `filters()`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/source/SparkStagedScanBuilder.java` (+5/-56 lines)

**修改目的**：改为继承 `BaseSparkScanBuilder`，复用通用投影与元数据列逻辑。

**工作逻辑**：
类声明改为 `extends BaseSparkScanBuilder implements SupportsPushDownRequiredColumns`。删除自身字段 `spark`、`table`、`readConf`、`metaColumns`、`schema`，构造方法改为 `super(spark, table, table.schema(), options)`。删除 `pruneColumns`、`removeMetaColumns`、`schemaWithMetadataColumns` 方法。`build()` 改为 `return new SparkStagedScan(spark(), table(), projectionWithMetadataColumns(), taskSetId, readConf())`，复用基类的 `projectionWithMetadataColumns()`。原先 `SparkStagedScanBuilder` 自己维护 `metaColumns` 列表与 `schemaWithMetadataColumns()` 的逻辑被基类统一的 `metaFieldNames` 集合与 `projectionWithMetadataColumns()` 取代，行为等价但实现统一。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/source/TestFilteredScan.java` (+1/-2 lines)

**修改目的**：适配 `caseSensitive(boolean)` setter 的移除。

**工作逻辑**：
测试中构造 `SparkScanBuilder` 时删除了链式 `.caseSensitive(false)` 调用。大小写敏感性现在通过 `SparkReadConf`（由 options 决定）在基类构造方法中设置，测试不再需要单独调用 setter。

## 总结

本提交通过抽取 `BaseSparkScanBuilder` 基类，将 `SparkScanBuilder` 与 `SparkStagedScanBuilder` 中重复的投影、谓词下推、limit 下推、元数据列处理、分片规划等通用逻辑统一到一处，显著减少了代码重复并消除两份实现间的细微差异。基类有意不绑定可选 Spark 接口，保留了子类按需声明能力的灵活性，为后续扫描层能力的统一扩展打好基础。
