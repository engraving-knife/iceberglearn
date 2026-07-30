# 提交 2542：Porting custom spark manifest rewrite order to spark 3.4 and 4.0 (#13893)

## 提交信息

- **序号**：2542 / 4088
- **哈希**：75ef4940dfb8a05dea55d067f2b038f656fd468c
- **短哈希**：75ef4940d
- **日期**：2025-08-21 15:27:43 -0700
- **作者**：Zach Dischner
- **提交说明**：Porting custom spark manifest rewrite order to spark 3.4 and 4.0 (#13893)
- **PR/Issue**：#13893（backport of #12840）

## 总体目的

#12840（提交 2534）为 Spark 3.5 的 `RewriteManifestsSparkAction` 实现了 `sortBy(List<String> partitionFields)` 自定义分区字段排序能力，让用户根据查询模式优化 manifest 聚集顺序以降低 planning 时间。但 Spark 3.4 与 4.0 模块尚未同步该能力，跨版本行为不一致。

本提交把 #12840 的实现 backport 到 Spark 3.4 与 4.0：
- 在两个版本的 `RewriteManifestsSparkAction` 中添加 `sortBy` 实现、`partitionFieldClustering` 字段、`sortColumn()` 方法，逻辑与 3.5 完全一致。
- 把对应的两个测试用例（`testRewriteManifestsPartitionedTableWithInvalidSortingColumns`、`testRewriteManifestsPartitionedTableWithCustomSorting`）backport 到 3.4 与 4.0 的测试类。
- 顺手清理 3.5 实现中遗留的未使用常量 `CUSTOM_CLUSTERING_COLUMN_NAME`（`__clustering_column__`），因为实际实现用 `functions.struct(...)` 直接构造排序列，没有用到这个临时列名。

## 如何达成设计目的

- Spark 3.4 与 4.0 的 `RewriteManifestsSparkAction`：
  - 新增 `partitionFieldClustering` 字段与 `DATA_FILE_PARTITION_COLUMN_NAME` 常量。
  - 实现 `sortBy(List<String>)`：从 `spec.fields()` 收集可用 partition 名，过滤出请求中不存在的字段，若非空抛 `IllegalArgumentException`，否则保存请求列表。
  - 新增 `sortColumn()`：若设置了自定义列表，对每个字段构造 `col("data_file.partition." + field)` 组合成 `functions.struct(...)`；否则返回 `col("data_file.partition")`。
  - `writeManifests` 中把 `df.col("data_file.partition")` 替换为 `sortColumn()`。
- Spark 3.5：删除未使用的 `CUSTOM_CLUSTERING_COLUMN_NAME` 常量。
- 测试：3.4 与 4.0 各新增两个测试，覆盖无效字段校验与自定义排序效果，逻辑与 3.5 完全一致。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java` (+55/-1)

**修改目的**：3.4 实现 `sortBy`。

**工作逻辑**：见上文。新增 `col`、`Set`、`PartitionField`、`functions` import。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java` (+111)

**修改目的**：3.4 测试覆盖。

**工作逻辑**：backport 3.5 的两个测试用例，验证无效字段（含误用原始列名 `c3`）抛 `IllegalArgumentException`，以及按 `c3_bucket, c2_trunc, c1` 排序后 manifest 边界整体有序。

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java` (+0/-1)

**修改目的**：清理 3.5 遗留常量。

**工作逻辑**：删除 `private static final String CUSTOM_CLUSTERING_COLUMN_NAME = "__clustering_column__";`，该常量在 #12840 实现中未被使用。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/actions/RewriteManifestsSparkAction.java` (+55/-1)

**修改目的**：4.0 实现 `sortBy`。

**工作逻辑**：与 3.4 完全相同。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/actions/TestRewriteManifestsAction.java` (+111)

**修改目的**：4.0 测试覆盖。

**工作逻辑**：与 3.4 测试完全相同。

## 总结

将 #12840 的自定义 manifest 排序能力 backport 到 Spark 3.4 与 4.0，复制 `sortBy` 实现、`sortColumn()` 方法与配套测试，使三个 Spark 版本行为一致；同时清理 3.5 中遗留的未使用常量 `CUSTOM_CLUSTERING_COLUMN_NAME`。
