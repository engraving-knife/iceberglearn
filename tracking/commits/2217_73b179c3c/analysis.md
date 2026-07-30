# 提交 2217：Spark 3.5, Arrow: Support for Row lineage when using the Parquet Vectorized reader (#12928)

## 提交信息

- **序号**：2217 / 4088
- **哈希**：73b179c3c130e54499d45a9203f63b58cc38e552
- **短哈希**：73b179c3c
- **日期**：2025-06-05 16:00:36 -0700
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark 3.5, Arrow: Support for Row lineage when using the Parquet Vectorized reader (#12928)
- **PR/Issue**：#12928

## 总体目的

这个提交为 Iceberg 的 Parquet 向量化读取器（vectorized reader）添加了行级血统（Row Lineage）支持。行级血统是 Iceberg 表格式 v3 的重要特性，通过为每行数据分配唯一的 `_row_id` 和 `_last_updated_sequence_number` 来追踪数据的来源和修改历史。此前，行级血统功能在非向量化读取器中已支持，但使用 Parquet 向量化读取器（基于 Arrow）时并不支持，导致在 Spark 3.5 中使用向量化读取时行级血统列无法正确读取（测试中通过 `assumeThat(vectorized).isFalse()` 跳过）。本提交在 Arrow 向量化读取层实现了 `RowIdVectorReader` 和 `LastUpdatedSeqVectorReader`，使向量化读取也能正确处理行级血统元数据列。这统一了不同读取路径的行为，消除了功能缺口，使行级血统在 Spark 的向量化读取场景下也可用。

## 如何达成设计目的

- 在 `VectorizedArrowReader` 中新增 `replaceWithMetadataReader` 静态方法，统一处理元数据列（ROW_ID、LAST_UPDATED_SEQUENCE_NUMBER、ROW_POSITION、IS_DELETED）和常量列的 reader 替换逻辑。
- 新增 `RowIdVectorReader`：读取行 ID 列。如果数据文件中已物化了行 ID（通过 idReader），则使用物化值；否则基于 baseRowId（快照的 firstRowId）和行位置计算行 ID。
- 新增 `LastUpdatedSeqVectorReader`：读取最后更新序列号列。如果数据文件中已物化了序列号，则使用物化值；否则使用文件级别的 lastUpdatedSeq 常量。
- 重构 `VectorizedReaderBuilder`：将元数据列处理逻辑提取到 `replaceWithMetadataReader`，简化为调用该方法后再用 `defaultReader` 处理默认值/null 填充。
- 将 `VectorHolder` 的私有构造函数提升为包级可见，并新增 `vectorHolder` 静态工厂方法，供新 reader 使用。
- 在测试中移除 `assumeThat(vectorized).isFalse()` 限制，使行级血统测试覆盖向量化读取路径。
- 新增大数据量 MERGE 测试 `testMergeWithManyRecords`（25000条记录），验证行级血统在数据承袭和更新场景下的正确性。
- 重构测试辅助类 `GenericsHelpers.assertEqualsBatch`，支持传入 idToConstant 和 batchFirstRowPos，正确验证元数据列。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java` (修改, +211 lines)

**修改目的**：实现行级血统列的向量化读取逻辑。

**工作逻辑**：
- 新增 `replaceWithMetadataReader` 方法：根据字段 ID 判断列类型，返回对应的 reader。ROW_ID → `rowIds(baseRowId, idReader)`，LAST_UPDATED_SEQUENCE_NUMBER → `lastUpdated(baseRowId, fileSeqNumber, seqReader)`，常量列 → `ConstantVectorReader`，ROW_POSITION → positions reader，IS_DELETED → `DeletedVectorReader`。
- 新增 `RowIdVectorReader` 内部类：读取行 ID。组合 idReader（读取物化的行 ID）和 posReader（读取行位置）。`read` 方法中：对每行，如果物化行 ID 存在且非 null 则使用物化值，否则使用 `firstRowId + position` 计算行 ID。输出为 BigIntVector。
- 新增 `LastUpdatedSeqVectorReader` 内部类：读取最后更新序列号。组合 seqReader（读取物化的序列号）。`read` 方法中：如果物化序列号存在且非 null 则使用物化值，否则使用文件级别的 `lastUpdatedSeq` 常量。输出为 BigIntVector。
- 新增辅助方法 `isNull`、`allocateBigIntVector`、`newNullabilityHolder`。
- 将原 `NullVectorReader` 中的 `newNullabilityHolder` 提取为类的静态方法，供新 reader 复用。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedReaderBuilder.java` (修改, +15/-26 lines)

**修改目的**：重构元数据列处理逻辑，集成行级血统支持。

**工作逻辑**：
- 将 `struct` 方法中原本内联的元数据列处理（idToConstant、ROW_POSITION、IS_DELETED、reader、initialDefault、optional）替换为先调用 `VectorizedArrowReader.replaceWithMetadataReader` 获取 reader，再调用 `defaultReader` 处理 null/默认值。
- 新增 `defaultReader` 方法：处理 reader 为 null 时的 fallback 逻辑（initialDefault → constantReader，optional → nulls，required → 抛异常）。
- 移除了对 `MetadataColumns` 和 `DeletedVectorReader` 的直接导入（逻辑已移入 VectorizedArrowReader）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorHolder.java` (修改, +6/-1 lines)

**修改目的**：暴露构造函数供新 reader 使用。

**工作逻辑**：将私有构造函数 `VectorHolder(FieldVector, Types.NestedField, NullabilityHolder)` 改为包级可见，并新增静态工厂方法 `vectorHolder(FieldVector, Types.NestedField, NullabilityHolder)`。

### `spark/v3.5/spark-extensions/src/test/java/org/apache/iceberg/spark/extensions/TestRowLevelOperationsWithLineage.java` (修改, +79 lines)

**修改目的**：启用向量化读取的行级血统测试并新增大数据量测试。

**工作逻辑**：
- 移除 `assumeThat(vectorized).isFalse()`，使行级血统测试覆盖向量化读取路径。
- 新增 `testMergeWithManyRecords` 测试：创建 25000 条记录的表，执行 MERGE INTO（更新一条 + 插入一条），验证承袭行（carry over）和更新行的行级血统（row_id 和 last_updated_sequence_number）正确性。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java` (修改, +9/-3 lines)

**修改目的**：支持元数据列的批量验证。

**工作逻辑**：`assertEqualsBatch` 方法新增 `idToConstant` 和 `batchFirstRowPos` 参数，调用 `assertEqualsUnsafe` 时传入这些参数，以正确验证 ROW_POSITION、ROW_ID 等元数据列的值。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java` (修改, -27 lines)

**修改目的**：移除旧的 `assertEqualsBatch` 方法，统一使用 GenericsHelpers 版本。

**工作逻辑**：删除 TestHelpers 中的 `assertEqualsBatch` 方法（27行），该方法的验证逻辑由 GenericsHelpers 的增强版本替代。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetVectorizedReads.java` (修改, +70/-9 lines)

**修改目的**：在向量化读取测试中支持行级血统验证。

**工作逻辑**：
- 新增 `supportsRowLineage()` 方法返回 true，启用行级血统测试。
- 重写 `writeAndValidate` 方法，传入 `ID_TO_CONSTANT` 常量映射。
- 新增多个 `writeAndValidate` 和 `assertRecordsMatch` 重载方法，支持 `idToConstant` 参数。
- 在 `assertRecordsMatch` 中使用 `VectorizedSparkParquetReaders.buildReader` 时传入 `idToConstant`。
- 验证时使用 `GenericsHelpers.assertEqualsBatch` 传入 `idToConstant` 和 `numRowsRead`。

## 总结

该提交为 Iceberg Parquet 向量化读取器补齐了行级血统支持，通过新增 `RowIdVectorReader` 和 `LastUpdatedSeqVectorReader` 实现行 ID 和最后更新序列号的向量化读取，支持物化值和计算值两种模式。重构了 `VectorizedReaderBuilder` 的元数据列处理逻辑，统一到 `replaceWithMetadataReader` 方法。测试方面移除了向量化读取的限制，新增了大数据量 MERGE 场景测试，并增强了测试辅助工具以支持元数据列验证。该提交消除了行级血统功能在不同读取路径间的行为差异。
