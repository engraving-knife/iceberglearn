# 提交 2259：Spark 3.4: Backport UPDATE/MERGE logic for row lineage (#13344)

## 提交信息

- **序号**：2259 / 4088
- **哈希**：56d81e72ff138d026fc6c79575d280a6a9b0bf48
- **短哈希**：56d81e72f
- **日期**：2025-06-19 18:51:38 -0700
- **作者**：Drew Gallardo
- **提交说明**：Spark 3.4: Backport UPDATE/MERGE logic for row lineage
- **PR/Issue**：#13344

## 总体目的

本提交将行级血缘（row lineage）的 UPDATE/MERGE 逻辑回移植到 Spark 3.4。行级血缘允许 Iceberg 追踪每一行数据的变更历史，通过 `_row_id`（行唯一标识）和 `_last_updated_sequence_number`（最后更新的序列号）元数据列实现。当表启用了行级血缘后，UPDATE 和 MERGE 操作需要在写入时正确处理这些元数据列——UPDATE 操作需要保留被更新行的 `_row_id` 并更新 `_last_updated_sequence_number`，MERGE 操作也需要类似处理。

在 Spark 3.4 中，原有的 UPDATE 和 MERGE 重写规则不感知行级血缘列。本提交新增了专门的行级血缘重写规则（`RewriteUpdateTableForRowLineage` 和 `RewriteMergeIntoTableForRowLineage`），在表支持行级血缘时将 `_row_id` 和 `_last_updated_sequence_number` 添加到操作输出中，使得这些列在写入数据文件时被正确处理。同时新增了优化器规则 `RemoveRowLineageOutputFromOriginalTable`，在最终计划中从原始表的输出中移除这些临时添加的行级血缘列。

## 如何达成设计目的

- 新增 `RewriteOperationForRowLineage` trait 作为行级血缘重写规则的基类，提供判断表是否支持行级血缘、查找行级血缘属性等公共逻辑。
- 新增 `RewriteUpdateTableForRowLineage` 和 `RewriteMergeIntoTableForRowLineage` 规则，在 Spark 分析阶段注入行级血缘列。
- 新增 `RemoveRowLineageOutputFromOriginalTable` 优化器规则，清理原始表中临时的行级血缘输出。
- 修改 `SparkWriteBuilder`，在 overwrite 操作中包含行级血缘列到写入 schema。
- 修改 `SparkTable`，暴露 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 元数据列。
- 修改 `SparkCopyOnWriteOperation`、`SparkPositionDeltaOperation` 等写入操作以支持行级血缘列的处理。

## 修改详情

### `spark/v3.4/spark-extensions/.../IcebergSparkSessionExtensions.scala` (修改, +6/0 lines)

**修改目的**：注册行级血缘相关的分析和优化规则。

**工作逻辑**：注入两条新的分析规则 `RewriteUpdateTableForRowLineage` 和 `RewriteMergeIntoTableForRowLineage`（在原有 RewriteUpdateTable/RewriteMergeIntoTable 之前），以及一条优化器规则 `RemoveRowLineageOutputFromOriginalTable`。规则注册顺序确保行级血缘规则先于标准规则执行。

### `spark/v3.4/spark-extensions/.../RewriteOperationForRowLineage.scala` (新增, +87/0 lines)

**修改目的**：提供行级血缘重写规则的公共基类逻辑。

**工作逻辑**：定义 trait 包含两个核心方法：`shouldUpdatePlan()` 判断表是否支持行级血缘且输出中尚未包含行级血缘列；`findRowLineageAttributes()` 从表达式序列中查找 `_row_id` 和 `_last_updated_sequence_number` 属性引用，并通过 `removeMetadataColumnAttribute` 移除其元数据列标记（绕过 ExposesMetadataColumns 逻辑中阻止同时暴露多个元数据列的限制）。

### `spark/v3.4/spark-extensions/.../RewriteUpdateTableForRowLineage.scala` (新增, +53/0 lines)

**修改目的**：在 UPDATE 操作中注入行级血缘列。

### `spark/v3.4/spark-extensions/.../RewriteMergeIntoTableForRowLineage.scala` (新增, +71/0 lines)

**修改目的**：在 MERGE 操作中注入行级血缘列。

### `spark/v3.4/spark-extensions/.../RemoveRowLineageOutputFromOriginalTable.scala` (新增, +54/0 lines)

**修改目的**：优化器规则，从原始表输出中移除临时的行级血缘列。

**工作逻辑**：在优化阶段遍历逻辑计划，找到 DataSourceV2Relation 中的原始表引用，移除其输出中仅为行级血缘重写而临时添加的 `_row_id` 和 `_last_updated_sequence_number` 属性，避免这些列出现在最终查询结果中。

### `spark/v3.4/spark/.../SparkWriteBuilder.java` (修改, +24/-XX lines)

**修改目的**：在 overwrite 操作中包含行级血缘列到写入 schema。

**工作逻辑**：新增 `writeIncludesRowLineage` 标志，仅在表支持行级血缘且为 overwrite 操作时为 true。在 `validateOrMergeWriteSchema()` 方法中，当 `writeIncludesRowLineage` 为 true 时，通过 `MetadataColumns.schemaWithRowLineage(table.schema())` 将行级血缘列加入到写入 schema 中。

### `spark/v3.4/spark/.../SparkTable.java` (修改, +5/-1 lines)

**修改目的**：暴露 ROW_ID 和 LAST_UPDATED_SEQUENCE_NUMBER 元数据列。

**工作逻辑**：在 `metadataColumns()` 方法中新增两个 `SparkMetadataColumn`：ROW_ID（LongType, nullable=true）和 LAST_UPDATED_SEQUENCE_NUMBER（LongType, nullable=true）。

### `spark/v3.4/spark/.../SparkCopyOnWriteOperation.java` (修改, +20/-XX lines)

**修改目的**：支持 Copy-On-Write 模式下行级血缘列的写入。

### `spark/v3.4/spark/.../SparkCopyOnWriteScan.java` (新增, +40/0 lines)

**修改目的**：支持 Copy-On-Write 扫描中读取行级血缘列。

### `spark/v3.4/spark/.../SparkPositionDeltaOperation.java` (修改, +17/-XX lines)

**修改目的**：支持 Position Delta 操作中行级血缘列的处理。

### `spark/v3.4/spark/.../SparkPositionDeltaWriteBuilder.java` (修改, +13/-XX lines)

**修改目的**：适配 Position Delta 写入构建器以支持行级血缘。

### 测试文件 (新增/修改)

- `TestRowLevelOperationsWithLineage.java` (+510 lines)：行级血缘场景下的行级操作完整测试
- `TestCopyOnWriteWithLineage.java` (+35 lines)、`TestMergeOnReadWithLineage.java` (+35 lines)：COW/MOR 模式行级血缘测试
- `SparkRowLevelOperationsTestBase.java`：测试基类适配

## 总结

本提交是行级血缘 UPDATE/MERGE 逻辑回移植到 Spark 3.4 的核心提交，涉及近 1000 行新增代码。它通过在 Spark 分析阶段注入行级血缘列、在写入时正确处理这些列、在优化阶段清理临时列，实现了 UPDATE/MERGE 操作对行级血缘的完整支持。这使得 Spark 3.4 用户能够在启用行级血缘的表上执行 UPDATE 和 MERGE 操作，同时保持行标识和变更序列号的正确追踪。
