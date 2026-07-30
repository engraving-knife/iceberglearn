# 提交 2965：Spark: ORC vectorized reader to use the delete filter (#14746)

## 提交信息

- **序号**：2965 / 4088
- **哈希**：4c908314ec3a81a04544784d8a1b69f76fc395e5
- **短哈希**：4c908314e
- **日期**：2025-12-05
- **作者**：pvary
- **提交说明**：Spark: ORC vectorized reader to use the delete filter (#14746)
- **PR/Issue**：#14746

## 总体目的

Iceberg 表支持通过 position delete 文件标记已删除的行。当用户查询时选择 `IS_DELETED` 元数据列，应当能看到哪些行被删除标记（`true` 表示已删除）。Spark 的 Parquet 向量化 reader 已正确实现了这一功能——它使用 `DeletedColumnVector` 并通过 `DeleteFilter` 填充每行的删除状态。然而 ORC 向量化 reader 中存在缺陷：`VectorizedSparkOrcReaders` 在处理 `IS_DELETED` 字段时，直接使用 `ConstantColumnVector(field.type(), batchSize, false)` 返回一个所有行均为 `false` 的常量向量，完全忽略了 delete filter 中的位置删除信息。

这意味着在有 delete 文件的 ORC 表上查询 `IS_DELETED` 列时，所有行都会显示为未删除，与实际数据状态不符。本提交修复这一缺陷，使 ORC 向量化 reader 与 Parquet reader 行为对齐：使用可变的 `DeletedColumnVector` 替代常量向量，并在批处理读取后通过 `BatchReaderUtil.applyDeleteFilter()` 根据 `PositionDeleteIndex` 填充实际的删除状态。

## 如何达成设计目的

核心改动在 `VectorizedSparkOrcReaders` 中将 `IS_DELETED` 字段的向量从 `ConstantColumnVector(false)` 改为 `DeletedColumnVector`（初始为空布尔数组），使后续的 delete filter 应用能够填充每行的删除状态。测试方面，将 Parquet 测试中已有的 `CustomizedDeleteFilter` 和 `CustomizedPositionDeleteIndex` 测试辅助类提取到共享的 `TestHelpers` 中，供 ORC 和 Parquet 测试复用；在 ORC 元数据列读取测试中新增 `testReadRowNumbersWithDelete` 测试验证删除行被正确标记，并修改 `readAndValidate` 方法接受 `DeleteFilter` 参数、在向量化路径上调用 `applyDeleteFilter`。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkOrcReaders.java` (+3/-1 lines)

**修改目的**：修复 ORC 向量化 reader 中 `IS_DELETED` 列始终返回 `false` 的问题。

**工作逻辑**：
在 `buildReader` 方法中处理 `MetadataColumns.IS_DELETED` 字段的分支，将原来的 `new ConstantColumnVector(field.type(), batchSize, false)` 替换为：
```java
DeletedColumnVector deletedVector = new DeletedColumnVector(field.type());
deletedVector.setValue(new boolean[batchSize]);
fieldVectors.add(deletedVector);
```
`DeletedColumnVector` 是一个可变列向量，初始时 `boolean` 数组默认全为 `false`（未删除）。当后续 `BatchReaderUtil.applyDeleteFilter()` 处理该批次时，会根据 `PositionDeleteIndex`（位置删除索引）将对应位置的元素设为 `true`（已删除）。这样 `IS_DELETED` 列就能正确反映每行的删除状态。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestHelpers.java` (+71/-0 lines)

**修改目的**：将测试用 delete filter 辅助类从 Parquet 测试提取到共享 TestHelpers，供 ORC 和 Parquet 测试复用。

**工作逻辑**：
新增两个 public static 内部类：
- **`CustomizedDeleteFilter`**：继承 `DeleteFilter<InternalRow>`，构造器接受 `hasDeletes`、`tableSchema`、`projectedSchema` 三个参数（相比原 Parquet 测试中的版本，schema 参数改为外部传入以适配不同测试的 schema）。`hasPosDeletes()` 返回 `hasDeletes` 标志；`deletedRowPositions()` 在 `hasDeletes` 为 true 时返回一个标记了位置 98-102（不含 103）为删除的 `CustomizedPositionDeleteIndex`。`asStructLike` 和 `getInputFile` 返回 null（测试不需要）。
- **`CustomizedPositionDeleteIndex`**：实现 `PositionDeleteIndex` 接口，内部用 `Set<Long>` 存储已删除位置。支持 `delete(pos)`、`delete(start, end)` 范围删除、`isDeleted(pos)` 查询和 `isEmpty()` 判空。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkOrcReadMetadataColumns.java` (+44/-7 lines)

**修改目的**：新增 ORC 向量化 reader 删除标记测试，并改造 `readAndValidate` 支持 delete filter。

**工作逻辑**：
1. **新增常量**：`NO_DELETES_FILTER`（`CustomizedDeleteFilter(false, ...)`，无删除）和 `RECORDS_PER_BATCH = 10`（每批行数）。
2. **`readAndValidate` 签名变更**：新增 `DeleteFilter<InternalRow> deleteFilter` 参数。向量化路径中增加 `.recordsPerBatch(RECORDS_PER_BATCH)` 设置批大小，并将读取结果通过 `BatchReaderUtil.applyDeleteFilter(builder.build(), deleteFilter)` 应用删除过滤后再转换为行。非向量化路径不受影响。
3. **`testReadRowNumbersWithDelete` 新测试**：使用 `assumeThat(vectorized).isTrue()` 跳过非向量化模式。构造 `CustomizedDeleteFilter(true, ...)` 标记位置 98-102 为删除（跨两个 row group [0,100) 和 [100,200)），在期望结果中将这 5 行的 `IS_DELETED` 字段（索引 3）设为 `true`，验证 ORC 向量化 reader 能正确标记删除行。
4. **现有测试适配**：`testReadRowNumbers`、`testReadRowNumbersWithFilter`、`testReadRowNumbersWithSplits` 均传入 `NO_DELETES_FILTER`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReadMetadataColumns.java` (+4/-74 lines)

**修改目的**：复用从 TestHelpers 提取的共享 delete filter 辅助类，删除本地重复实现。

**工作逻辑**：
删除原有的 `TestDeleteFilter` 和 `CustomizedPositionDeleteIndex` 两个内部类（共约 70 行），改为使用 `TestHelpers.CustomizedDeleteFilter`。两处使用点——`testReadRowNumbersWithDelete` 中的 `new TestDeleteFilter(true)` 改为 `new TestHelpers.CustomizedDeleteFilter(true, DATA_SCHEMA, PROJECTION_SCHEMA)`，`validate` 方法中的 `new TestDeleteFilter(false)` 改为 `new TestHelpers.CustomizedDeleteFilter(false, DATA_SCHEMA, PROJECTION_SCHEMA)`。同时清理不再需要的 import（`StructLike`、`DeleteCounter`、`PositionDeleteIndex`、`InputFile`、`Sets`、`Set`）。

## 总结

本提交修复了 Spark ORC 向量化 reader 中 `IS_DELETED` 元数据列始终返回 `false` 的缺陷，使其与 Parquet reader 行为对齐。核心改动是将常量向量替换为可变的 `DeletedColumnVector`，由 delete filter 在批处理级别填充实际删除状态。同时通过将测试辅助类提取到共享 `TestHelpers` 实现了 ORC 与 Parquet 测试的代码复用，新增的跨 row group 删除标记测试验证了修复的正确性。该修复对有 position delete 的 ORC 表的 `IS_DELETED` 查询具有实际正确性价值。
