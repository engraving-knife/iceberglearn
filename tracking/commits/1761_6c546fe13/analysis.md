# 提交 1761：Spark 3.5: Fix Incorrect Spec Used With AddFiles Procedure (#12319)

## 提交信息

- **序号**：1761 / 4088
- **哈希**：6c546fe1346e81ca0c3f477695340016da891204
- **短哈希**：6c546fe13
- **日期**：2025-02-19 16:19:56 -0600
- **作者**：Russell Spitzer
- **提交说明**：Spark 3.5: Fix Incorrect Spec Used With AddFiles Procedure (#12319)
- **PR/Issue**：#12319

## 总体目的

本提交旨在修复 Spark AddFiles 存储过程中使用错误分区规范（PartitionSpec）的问题。

AddFiles 过程用于将外部数据文件（如 Hive/Spark 表中的 Parquet 文件）导入到 Iceberg 表中。在导入时，需要确定使用哪个分区规范来组织这些文件。原实现使用 `SparkSchemaUtil.specForTable()` 方法，该方法会根据 Spark 源表的分区列和 Iceberg 目标表的当前规范（current spec）来构建一个新的 PartitionSpec。

问题在于：当 Iceberg 表经历过分区演化（partition evolution），即表有多个历史分区规范时，`specForTable()` 基于当前规范构建的 PartitionSpec 可能不是表中实际存在的任何一个规范。这会导致导入的文件使用的分区规范在 Iceberg 表中不存在，产生不一致——manifest 中的分区规范与表定义的任何规范都不匹配。

本提交将此逻辑改为 `findCompatibleSpec()`：遍历 Iceberg 表中所有已有的分区规范，找到一个与 Spark 源表分区列名称和顺序完全匹配的现有规范，确保导入文件使用的是表中已存在的规范。

## 如何达成设计目的

提交通过以下策略修复问题：

1. **新增 `findCompatibleSpec` 方法**：替代原来的 `SparkSchemaUtil.specForTable()` 调用。该方法获取 Spark 源表的分区列名列表，然后遍历 Iceberg 目标表的所有分区规范（`specs()`），对于每个只使用 identity transform 的规范，比较其分区字段名是否与 Spark 分区列名完全一致（顺序和名称），返回第一个匹配的规范。

2. **新增测试用例**：添加多个测试来验证修复的正确性，包括：
   - `addPartitionsFromHiveSnapshotInheritanceEnabled`：验证从 Hive 表导入文件时 manifest 的分区规范与表规范一致。
   - `testAddFilesToTableWithManySpecs`：验证在经过多次分区演化（有4个规范）的表上正确导入文件。
   - 重构原测试方法 `addPartitionToPartitionedSnapshotIdInheritanceEnabledInTwoRuns`，提取公共验证逻辑到 `manifestSpecMatchesTableSpec()` 和 `verifyUUIDInPath()` 方法。

3. **验证方法**：`manifestSpecMatchesTableSpec()` 方法读取表当前快照的所有 manifest 文件（强制从文件中读取规范信息而非使用表的默认规范），验证每个 manifest 的分区规范与表的当前规范一致。

## 修改详情

### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/SparkTableUtil.java`（修改, +44/-1 lines）

**修改目的**：修复 AddFiles 过程中使用错误分区规范的问题。

**工作逻辑**：

1. **修改调用点**：在 `importSparkTable` 方法中，将 `SparkSchemaUtil.specForTable(spark, sourceTableIdentWithDB.unquotedString())` 替换为 `findCompatibleSpec(targetTable, spark, sourceTableIdentWithDB.unquotedString())`。从基于源表创建新规范改为在目标表已有规范中查找匹配项。

2. **新增 `findCompatibleSpec` 方法**：
   - 解析 Spark 表名为数据库和表名（用 `.` 分割，limit 2）
   - 通过 `spark.catalog().listColumns()` 获取 Spark 源表的所有列，过滤出分区列（`isPartition()`），提取列名并转为小写
   - 遍历 Iceberg 目标表的所有分区规范（`icebergTable.specs().values()`）：
     - 首先检查规范是否全部使用 identity transform（`field.transform().isIdentity()`）
     - 如果是，提取分区字段名并转为小写
     - 比较 Iceberg 分区字段名列表与 Spark 分区列名列表是否完全一致（顺序和内容）
     - 返回第一个匹配的规范
   - 如果没有找到匹配的规范，抛出 `IllegalArgumentException`，包含表名、Spark 分区列名和源表名的详细错误信息

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestAddFilesProcedure.java`（修改, +85/-7 lines）

**修改目的**：为修复添加测试验证。

**工作逻辑**：

1. **重构原有测试**：将 `addPartitionToPartitionedSnapshotIdInheritanceEnabledInTwoRuns` 中的 manifest 路径 UUID 验证逻辑提取到 `verifyUUIDInPath()` 方法，并新增 `manifestSpecMatchesTableSpec()` 方法验证 manifest 的分区规范与表规范一致。

2. **`manifestSpecMatchesTableSpec()` 方法**：通过 `Spark3Util.loadIcebergTable()` 加载 Iceberg 表，获取其 FileIO，然后读取当前快照的所有 manifest 文件（传入 `null` 作为 spec 参数以强制从文件读取规范），验证每个 manifest 的 spec 与表的当前 spec 相等。

3. **新增 `addPartitionsFromHiveSnapshotInheritanceEnabled` 测试**：创建 Hive 分区表，创建 Iceberg 表并启用 snapshot-id-inheritance，调用 add_files 导入文件，验证数据正确性和 manifest 规范匹配。

4. **新增 `testAddFilesToTableWithManySpecs` 测试**：创建有4个分区规范的 Iceberg 表（通过多次 ADD PARTITION FIELD），验证 add_files 正确选择匹配的规范导入文件。

## 小结

- **成效**：修复了 AddFiles 过程在分区演化表上使用错误分区规范的问题，确保导入文件使用的分区规范是 Iceberg 表中实际存在的规范，避免 manifest 与表定义不一致。
- **影响范围**：修改 Spark 3.5 模块的 `SparkTableUtil`，影响所有使用 `add_files` 存储过程导入外部文件到 Iceberg 表的场景，特别是经历过分区演化的表。
- **回迁到 1.4.x 的注意事项**：此 bug 修复建议回迁到 1.4.x 分支。需注意 1.4.x 分支的 Spark 版本（3.3/3.4/3.5），此修复仅针对 Spark 3.5 模块。如果 1.4.x 分支也支持 Spark 3.3/3.4，可能需要在对应模块中做相同修复。同时需确认 1.4.x 分支的 `SparkTableUtil` 代码结构与 main 分支一致，特别是 `importSparkTable` 方法中的调用点。无特殊前置依赖。
