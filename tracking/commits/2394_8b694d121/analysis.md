# 提交 2394：Spark 4: Port vectorized reader tests for row lineage from #12928 (#13649)

## 提交信息

- **序号**：2394 / 4088
- **哈希**：8b694d121599150c7444e2eaccf993ef23fd58be
- **短哈希**：8b694d121
- **日期**：2025-07-23 15:21:40 -0600
- **作者**：Kevin Liu
- **提交说明**：Spark 4: Port vectorized reader tests for row lineage from #12928 (#13649)
- **PR/Issue**：#13649（port from #12928）

## 总体目的

此提交将 PR #12928 中的向量化读取器（vectorized reader）行级血统测试移植到 Spark 4 分支。Iceberg V3 表的行级血统特性引入了 `_row_id` 和 `_last_updated_sequence_number` 元数据列，需要确保向量化 Parquet 读取器能正确读取这些列。

本提交包含两方面工作：一是新增了大规模数据 MERGE 操作的行级血统测试（`testMergeWithManyRecords`，25000 条记录），验证在大数据量下行级血统的正确性；二是重构了向量化读取测试基础设施，使其支持行级血统列的验证，包括通过 `idToConstant` 映射和 `batchFirstRowPos` 来正确比对向量化批量读取的结果。

## 如何达成设计目的

关键设计点：

1. **新增大规模 MERGE 测试**：`testMergeWithManyRecords` 使用 25000 条记录验证 MERGE 操作的行级血统行为，区分了 carry-over 行（行级血统从旧快照继承）和新插入行（行级血统从新快照开始）。
2. **GenericsHelpers.assertEqualsBatch 增强**：原先的 `assertEqualsBatch` 方法仅接受 struct、expectedRecords 和 batch 参数，新增了 `idToConstant`（字段 ID 到常量值的映射，用于元数据列）和 `batchFirstRowPos`（批处理在文件中的起始位置，用于行位置验证）参数。
3. **TestHelpers 清理**：移除了 TestHelpers 中的 `assertEqualsBatch` 方法，统一使用 GenericsHelpers 中的增强版本。
4. **TestParquetVectorizedReads 扩展**：重写 `supportsRowLineage()` 返回 true，添加 `writeAndValidate` 重载方法支持行级血统常量映射，将 `idToConstant` 传播到 `VectorizedSparkParquetReaders.buildReader`。

## 修改详情

### `spark/v4.0/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (+78/-0 lines)

**修改目的**：新增大规模 MERGE 操作的行级血统测试。

**工作逻辑**：`testMergeWithManyRecords` 创建 25000 条记录的表，执行 MERGE 操作（更新 id=101 的记录，插入 id=26000 的新记录）。验证更新快照前的 carry-over 行保持原有行级血统（`_row_id` 和 `_last_updated_sequence_number`），新插入行的行级血统从更新快照的 `firstRowId` 开始。`recordToExpectedRow` 辅助方法将 Record 转换为期望的行数组格式。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java` (+10/-2 lines)

**修改目的**：增强 assertEqualsBatch 以支持行级血统列验证。

**工作逻辑**：`assertEqualsBatch` 新增 `idToConstant` 和 `batchFirstRowPos` 参数。`batchFirstRowPos + rowPos` 计算每行在文件中的绝对位置，传递给 `assertEqualsUnsafe` 用于行位置相关的元数据列验证。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java` (+0/-27 lines)

**修改目的**：移除重复的 assertEqualsBatch 方法。

**工作逻辑**：删除了 TestHelpers 中的 `assertEqualsBatch` 方法，该方法的功能已被 GenericsHelpers 中的增强版本取代，避免代码重复。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+73/-6 lines)

**修改目的**：扩展向量化 Parquet 读取测试以支持行级血统。

**工作逻辑**：重写 `supportsRowLineage()` 返回 true，重写 `writeAndValidate` 方法传入 `ID_TO_CONSTANT`。新增多个 `writeAndValidate` 和 `assertRecordsMatch` 重载方法，将 `idToConstant` 参数传播到 `VectorizedSparkParquetReaders.buildReader`。在批量读取验证时，使用增强的 `GenericsHelpers.assertEqualsBatch` 传入 `idToConstant` 和 `numRowsRead`（作为 batchFirstRowPos）。

## 总结

此提交为 Spark 4 的向量化 Parquet 读取器添加了行级血统测试覆盖，包括大规模 MERGE 场景的端到端验证和底层向量化读取的基础设施增强。通过 `idToConstant` 和 `batchFirstRowPos` 机制，测试能正确验证行级血统元数据列在向量化批量读取中的准确性。
