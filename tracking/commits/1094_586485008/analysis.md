# 提交 1094：Spark 3.5: Add utility to load table state reliably (#10984)

## 提交信息

- **序号**：1094 / 4088
- **哈希**：586485008355e2a11ea4c78890964dc88613c54f
- **短哈希**：586485008
- **日期**：2024-08-23 17:31:20 -0700
- **作者**：Anton Okolnychyi
- **提交说明**：Spark 3.5: Add utility to load table state reliably (#10984)
- **PR/Issue**：#10984

## 总体目的

本提交为 Spark 3.5 集成模块新增一个工具方法 `SparkTableUtil.loadTable(spark, table, snapshotId)`，用于可靠地加载指定快照下的表数据为 `Dataset<Row>`，并重构 `NDVSketchUtil` 使其使用该新方法替代原先通过 Spark DataFrameReader 读取的方式。

原先 `NDVSketchUtil.computeNDVSketches` 在计算 NDV（Number of Distinct Values，基数估计）草图时，通过 `spark.read().format("iceberg").option(SNAPSHOT_ID, ...).load(table.name())` 来加载指定快照数据。这种基于 `table.name()`（表名）的加载路径会重新解析表，可能在并发或表状态变化的场景下加载到非预期的快照状态，不够可靠。而通过直接构造 `SparkTable`（绑定具体 `Table` 实例与 `snapshotId`）并经 `DataSourceV2Relation` 加载，可以确保读取的就是传入的那个 `Table` 对象在指定快照下的数据，避免表名解析带来的歧义。

同时本提交将 `loadMetadataTable` 中重复的 `DataSourceV2Relation.create` 逻辑抽取为私有方法 `createRelation`，减少代码重复，使 `loadTable` 与 `loadMetadataTable` 共用同一构造路径。新增测试 `testLoadingTableDirectly` 验证直接加载表并执行 `computeTableStats` 的正确性。

## 如何达成设计目的

设计思路：

1. **新增 `loadTable` 方法**：在 `SparkTableUtil` 中新增 `public static Dataset<Row> loadTable(SparkSession spark, Table table, long snapshotId)`，内部用 `new SparkTable(table, snapshotId, false)` 创建绑定特定快照的 Spark 表，再通过 `createRelation` 构造 `DataSourceV2Relation`，最后 `Dataset.ofRows(spark, relation)` 得到 DataFrame。

2. **抽取公共 `createRelation` 方法**：将原 `loadMetadataTable` 中构造 `CaseInsensitiveStringMap` 与 `DataSourceV2Relation.create` 的逻辑提取为 `private static DataSourceV2Relation createRelation(SparkTable sparkTable, Map<String, String> extraOptions)`，供 `loadTable` 与 `loadMetadataTable` 共用。注意同时把 `Some.empty()` 改为 `Option.empty()`（Scala 互操作更规范的写法）。

3. **重构 `NDVSketchUtil`**：`computeNDVSketches` 不再使用 `spark.read().format("iceberg")...`，改为调用 `SparkTableUtil.loadTable(spark, table, snapshot.snapshotId())`，确保加载的就是传入 `Table` 对象在目标快照下的数据。

4. **新增测试**：`TestComputeTableStatsAction` 中新增 `testLoadingTableDirectly`，建表、插入数据、直接 `computeTableStats`，断言统计文件大小非 0 且 blob 数为 2（对应 NDV sketch）。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`

**修改目的**：新增 `loadTable` 方法并抽取公共 `createRelation`。

**工作逻辑**：
- 新增 `loadTable(SparkSession spark, Table table, long snapshotId)`：构造 `new SparkTable(table, snapshotId, false)`（第三个参数 `refreshEagerly=false`），调用 `createRelation` 得到 `DataSourceV2Relation`，返回 `Dataset.ofRows(spark, relation)`。
- 重构 `loadMetadataTable(...)`：先 `MetadataTableUtils.createMetadataTableInstance(table, type)` 得到元数据表，再 `new SparkTable(metadataTable, false)`，通过 `createRelation` 构造 relation 并返回 Dataset。
- 抽取 `private static DataSourceV2Relation createRelation(SparkTable sparkTable, Map<String, String> extraOptions)`：封装 `CaseInsensitiveStringMap` 构造与 `DataSourceV2Relation.create(sparkTable, Option.empty(), Option.empty(), options)`（注意从 `Some.empty()` 改为 `Option.empty()`）。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/NDVSketchUtil.java`

**修改目的**：改用 `SparkTableUtil.loadTable` 加载指定快照数据，替代基于表名的 DataFrameReader 加载。

**工作逻辑**：
- 移除对 `SparkReadOptions` 的 import，改为 import `SparkTableUtil` 与 `Dataset`。
- `computeNDVSketches` 内部由原来的 `spark.read().format("iceberg").option(SparkReadOptions.SNAPSHOT_ID, snapshot.snapshotId()).load(table.name()).select(...).first()` 改为 `SparkTableUtil.loadTable(spark, table, snapshot.snapshotId()).select(toAggColumns(colNames)).first()`。这样直接使用传入的 `Table` 实例绑定快照，避免重新通过表名解析，加载更可靠。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestComputeTableStatsAction.java`

**修改目的**：新增测试验证直接加载表路径下 `computeTableStats` 的正确性。

**工作逻辑**：新增 `testLoadingTableDirectly` 测试：建表（id int, data string）、插入一条数据、`validationCatalog.loadTable(tableIdent)` 加载表、`SparkActions.get().computeTableStats(table).execute()` 计算统计，断言 `statisticsFile.fileSizeInBytes()` 不为 0，且 `blobMetadata().size()` 为 2（NDV sketch 包含 2 个 blob）。

## 小结

- **成效**：为 Spark 3.5 提供了可靠的表快照加载工具方法 `SparkTableUtil.loadTable`，使 NDV 草图计算等场景能够基于传入的 `Table` 实例加载指定快照数据，避免表名解析的歧义；同时抽取公共 `createRelation` 减少重复代码。
- **影响范围**：仅影响 `spark/v3.5/` 模块的 3 个文件（`SparkTableUtil`、`NDVSketchUtil`、对应测试），不影响其他 Spark 版本目录或核心 API。
- **回迁到 1.4.x 的注意事项**：此改动属于内部工具方法增强与可靠性修复，API 新增公共方法 `loadTable`（向后兼容），**适合回迁到 1.4.x**（若 1.4.x 包含 spark/v3.5 模块）。回迁风险低：`NDVSketchUtil` 改动是内部实现替换，`createRelation` 抽取为纯重构。需注意 `Option.empty()` 与 `Some.empty()` 的差异在 1.4.x 的 Spark 版本下是否一致（Spark 3.5 两者均可用）。若 1.4.x 已有 NDV 统计功能的 bug 与快照加载可靠性相关，回迁此提交可直接修复。
