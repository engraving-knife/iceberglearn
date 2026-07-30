# 提交 2534：API, Spark 3.5: Adding new rewrite manifest spark action to accept custom partition order (#12840)

## 提交信息

- **序号**：2534 / 4088
- **哈希**：07c088fce9c54369864dcb6da16006e78206048b
- **短哈希**：07c088fce
- **日期**：2025-08-20 10:00:22 -0500
- **作者**：Zach Dischner
- **提交说明**：API, Spark 3.5: Adding new rewrite manifest spark action to accept custom partition order (#12840)
- **PR/Issue**：#12840

## 总体目的

`RewriteManifests` action 之前总是按表的分区 spec 中分区字段的原始顺序对 manifest 进行排序。但实际查询模式往往集中在某几个分区字段上，按原始顺序排序产出的 manifest 不能让查询规划阶段最大化跳过无关 manifest，导致 planning 时间偏长。

本提交为 `RewriteManifests` API 增加一个 `sortBy(List<String> partitionFields)` 方法，允许用户在重写 manifest 时显式指定一个排序字段序列（用 partition 字段的"转换后名称"，例如 `c3_bucket` 而非 `c3`）。这样重写后生成的 manifest 在指定字段上聚集，对应字段范围更窄、更易被查询规划器裁剪，从而降低 planning 时间。Spark 3.5 的 `RewriteManifestsSparkAction` 实现了该方法，并通过新的 `sortColumn()` 在 repartition+sort 阶段构造合适的排序列。

这是一个针对查询规划性能优化的特性增强，原始 API 用 default 抛 `UnsupportedOperationException` 兼容其他实现（Spark 3.4/4.0 在后续 PR #13893 中跟进）。

## 如何达成设计目的

- API 层：`RewriteManifests` 接口新增 `default sortBy(List<String> partitionFields)`，默认抛 `UnsupportedOperationException`，保证向后兼容。
- 实现层：
  - `sortBy` 先校验传入的字段名必须全部存在于当前 `PartitionSpec` 的 `PartitionField.name()` 集合中（即转换后的隐藏分区名，例如 `c3_bucket`），否则抛 `IllegalArgumentException`。
  - 校验通过后将字段名列表保存到 `partitionFieldClustering`。
  - 在写入 manifest 时，通过新的 `sortColumn()` 决定排序列：若设置了自定义聚类字段，则把 `data_file.partition.<field>` 这些子列打包成 `struct(...)` 作为单一排序列；否则保持原行为（用整个 `data_file.partition` 列）。
  - `repartitionAndSort` 仍使用 `repartitionByRange + sortWithinPartitions`，但作用列改为 `sortColumn()` 返回的列。
- 测试层：
  - 新增"无效排序字段"测试：验证传入不存在的字段（包括误用原始列名 `c3` 而非 `c3_bucket`）会抛出带提示的 `IllegalArgumentException`。
  - 新增"自定义排序"测试：构造 c1/c2_trunc/c3_bucket 三字段分区表，写入 1000 条随机数据，强制 manifest 分裂，按 `c3_bucket, c2_trunc, c1` 排序重写，然后从 `#manifests` 元数据表读取每个 manifest 的 partition_summaries 中 c3_bucket 的 lower/upper bound，断言这些边界整体有序，证明 manifest 在 c3_bucket 维度上被有效聚集。

## 修改详情

### `api/src/main/java/org/apache/iceberg/actions/RewriteManifests.java` (+26)

**修改目的**：在接口层暴露自定义排序 API。

**工作逻辑**：新增 `default RewriteManifests sortBy(List<String> partitionFields)`，附详细 Javadoc 说明用途、示例与字段命名约定（用转换后的分区名而非原始列名）。默认抛 `UnsupportedOperationException`。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java` (+56/-2)

**修改目的**：在 Spark 3.5 实现自定义排序。

**工作逻辑**：
- 新增字段 `partitionFieldClustering`。
- `sortBy` 实现：从 `spec.fields()` 收集可用 partition 名，过滤出请求列表中不在其中的字段，若非空则抛 `IllegalArgumentException`，否则保存请求列表。
- 新增 `sortColumn()`：若设置了自定义列表，则对每个字段构造 `col("data_file.partition." + field)`，组合成 `functions.struct(...)` 作为单一排序列；否则返回原来的 `col("data_file.partition")`。
- `writeManifests` 流程中把原来直接传 `df.col("data_file.partition")` 改为传 `sortColumn()`，让 repartition+sort 按自定义顺序执行。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java` (+111)

**修改目的**：覆盖排序校验与排序效果。

**工作逻辑**：
- `testRewriteManifestsPartitionedTableWithInvalidSortingColumns`：分别用 `[c1, c2]`（c2 不在 spec）和 `[c1, c3]`（c3 是原始列名，应使用 `c3_bucket`）验证抛 `IllegalArgumentException`，且消息包含 spec 详情。
- `testRewriteManifestsPartitionedTableWithCustomSorting`：构造 1000 条随机数据、强制 manifest 分裂、按 `c3_bucket, c2_trunc, c1` 重写，读取 manifests 元数据表中 `partition_summaries` 数组第 3 项（c3_bucket）的 lower/upper bound，断言整体有序且 manifest 数 ≥ 2。

## 总结

通过在 `RewriteManifests` API 增加 `sortBy(List<String>)` 方法并在 Spark 3.5 实现自定义分区字段排序，让用户能根据查询模式优化 manifest 的聚集顺序，从而降低查询规划时间。配套校验逻辑保证字段名合法（必须用转换后的隐藏分区名），并通过详尽测试覆盖异常路径与实际排序效果。其他 Spark 版本在后续 PR 中跟进。
