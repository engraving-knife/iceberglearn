# 提交 2954：Spark: Backport move DeleteFiltering out from the vectorized reader (#14745)

## 提交信息

- **序号**：2954 / 4088
- **哈希**：65280c0153ec53f5a1a9ff1b2a608a75018dc147
- **短哈希**：65280c015
- **日期**：2025-12-04
- **作者**：pvary
- **提交说明**：Spark: Backport move DeleteFiltering out from the vectorized reader (#14745)
- **PR/Issue**：#14745

## 总体目的

这是上一提交 #14652（序号 2951）的 backport。#14652 把 Spark 4.0 向量化读取路径中的"应用删除"逻辑从 `ColumnarBatchReader`/`CometColumnarBatchReader` 内部抽离到 `BaseBatchReader.BatchDeleteFilter`，让 reader 只产出原始 batch、删除过滤作为 batch 流的后置 `CloseableIterable.transform` 环节独立运行。由于 Spark 3.4 与 3.5 有独立的 `spark/v3.4`、`spark/v3.5` 源码树，向量化 reader、`BaseBatchReader`、`BatchDataReader` 与对应测试都是版本特定的类，必须分别 backport 才能让这两个 Spark 版本也获得同样的解耦与可测试性改进。

本提交把 #14652 的全部改动原样应用到 Spark 3.4 与 3.5：移除 reader 内的 `DeleteFilter` 持有与 `setDeleteFilter`，新增 `UpdatableDeletedColumnVector` 接口、`CometDeletedColumnVector` 类、`BatchDeleteFilter` 内部类与 `BatchReaderUtil` 测试工具，并把 reader 工厂签名去掉 `deleteFilter` 参数、`BatchDataReader` 改为始终构造 `SparkDeleteFilter`。行为应与 2951 一致——纯重构，删除过滤从 reader 内部移到外部 pipeline。

## 如何达成设计目的

与 2951 完全相同的设计，只是落在 `spark/v3.4` 与 `spark/v3.5` 两套源码树。每个版本的改动文件集合与 2951 中 Spark 4.0 的对应文件一一对应（reader 瘦身、`BatchDeleteFilter` 引入、接口/向量类新增、测试适配），无新设计。需注意两处版本差异：v3.4 的 `TestParquetVectorizedReads` 位于 `data/parquet/vectorized/` 包（与 v4.0/v3.5 的 `data/vectorized/parquet/` 不同）；v3.4 不含 `TestParquetDictionaryEncodedVectorizedReads`（该测试在 v3.5 与 v4.0 才存在，故 v3.4 少一个文件改动）。此外 v3.4/v3.5 的 `BatchReaderUtil` 落在 `spark/v{3.4,3.5}/spark/src/test/java/org/apache/iceberg/spark/source/` 下，与 v4.0 路径一致。

## 修改详情

下述改动在 `spark/v3.4` 与 `spark/v3.5` 两套源码树中各有一份（除特别说明外内容与 2951 的 Spark 4.0 版本一致）。为避免重复，按文件类型合并描述；每个文件在两个版本下都做了等价改动。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java` (各 +3/-54 lines)

**修改目的**：从 `ColumnarBatchReader` 移除删除过滤逻辑。

**工作逻辑**：删除 `hasIsDeletedColumn`/`deletes`/`rowStartPosInBatch` 字段、`setDeleteFilter` 方法、构造器里的 `DeletedVectorReader` 检测、`setRowGroupInfo` 中的 row index offset 取值；`read()` 与 `ColumnBatchLoader.loadDataToColumnBatch()` 简化为直接返回未过滤 batch（`setNumRows(batchSize)`）。与 2951 的 v4.0 改动逐字相同。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnarBatchReader.java` (各 +3/-61 lines)

**修改目的**：对 Comet 向量化 reader 做对称瘦身。

**工作逻辑**：删除 `hasIsDeletedColumn`/`deletes`/`rowStartPosInBatch` 与 `setDeleteFilter`，`read()` 直接返回原始 batch，`ColumnBatchLoader` 删除 `buildIsDeleted`/`buildRowIdMapping`/`readDeletedColumn`/`removeExtraColumns` 分支。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeleteColumnReader.java` (各 +6/-15 lines 左右)

**修改目的**：让 Comet 删除列 reader 持有可更新的 `CometDeletedColumnVector`。

**工作逻辑**：删除按 `isDeleted` 数组构造的公开构造器；`DeleteColumnReader` 内部从 `boolean[] isDeleted` 改为 `CometDeletedColumnVector deletedVector`，`readBatch` 用 `deletedVector.isDeleted()` 喂 native，新增 `currentBatch()` 返回 `deletedVector`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeletedColumnVector.java` (各 +155/-0 lines, 新文件)

**修改目的**：为 Comet 路径提供实现 `UpdatableDeletedColumnVector` 的 `CometVector`。

**工作逻辑**：`extends CometVector implements UpdatableDeletedColumnVector`，持有 `boolean[] isDeleted`，`setValue`/`isDeleted`/`getBoolean` 可用，其余类型访问抛 `UnsupportedOperationException`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometVectorizedReaderBuilder.java` (各 +2/-11 lines)

**修改目的**：从 reader builder 移除 `deleteFilter`。

**工作逻辑**：删除 `deleteFilter` 字段与构造参数，`vectorizedReader` 直接 `return readerFactory.apply(reorderedFields)`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/DeletedColumnVector.java` (各 +2/-1 lines)

**修改目的**：让 `DeletedColumnVector` 实现 `UpdatableDeletedColumnVector`。

**工作逻辑**：类声明追加 `implements UpdatableDeletedColumnVector`，`setValue` 加 `@Override`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/UpdatableDeletedColumnVector.java` (各 +23/-0 lines, 新文件)

**修改目的**：定义可更新删除标记的列向量公共接口。

**工作逻辑**：单方法接口 `void setValue(boolean[] isDeleted)`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java` (各 +4/-22 lines)

**修改目的**：从 reader 工厂方法签名移除 `DeleteFilter` 参数。

**工作逻辑**：`buildReader`/`buildCometReader` 与内部 `ReaderBuilder` 都去掉 `deleteFilter` 参数及字段，`vectorizedReader` 覆写删除。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (各 +101/-9 lines)

**修改目的**：引入 `BatchDeleteFilter`，把删除过滤作为 batch 流后置转换。

**工作逻辑**：`newBatchIterable` 改为先产出原始 batch iterable，再 `CloseableIterable.transform(iterable, new BatchDeleteFilter(deleteFilter)::filterBatch)`；`newParquetIterable` 接收 `requiredSchema`；新增 `BatchDeleteFilter` 静态内部类（按 schema 找 `ROW_POSITION`/`IS_DELETED`，`filterBatch` 复用 `ColumnarBatchUtil.buildIsDeleted`/`buildRowIdMapping`/`removeExtraColumns`，`needDeletes` 短路）。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java` (各 +4/-4 lines)

**修改目的**：始终构造 `SparkDeleteFilter` 并用 `requiredSchema` 算 `constantsMap`。

**工作逻辑**：从 `task.deletes().isEmpty() ? null : new SparkDeleteFilter(...)` 改为始终 `new SparkDeleteFilter(filePath, task.deletes(), counter(), true)`；`constantsMap` schema 从 `expectedSchema()` 改为 `deleteFilter.requiredSchema()`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/test/java/org/apache/iceberg/spark/source/BatchReaderUtil.java` (各 +34/-0 lines, 新文件)

**修改目的**：测试工具，把 `BatchDeleteFilter` 套到 batch 流上。

**工作逻辑**：`applyDeleteFilter(batches, filter)` 直接 `CloseableIterable.transform(batches, new BaseBatchReader.BatchDeleteFilter(filter)::filterBatch)`。与 2951 一致。

### `spark/v{3.4,3.5}/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReadMetadataColumns.java` (各 +49/-22 lines)

**修改目的**：把 mock `DeleteFilter` 测试改为真实 `TestDeleteFilter` 子类，并在 vectorized 路径手动套用 `BatchDeleteFilter`。

**工作逻辑**：新增 `TestDeleteFilter extends DeleteFilter<InternalRow>`（`hasPosDeletes` 可配，`deletedRowPositions` 删除 98–103），`buildReader` 改 3 参数，`validate` 新增 `filter` 参数并经 `BatchReaderUtil.applyDeleteFilter` 接到 vectorized 路径。与 2951 一致。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetVectorizedReads.java` (+4/-2 lines)

**修改目的**：适配 `buildReader` 新签名（v3.4 专用路径）。

**工作逻辑**：两处 `buildReader(... null, allocator)`/`buildReader(... null)` 改为去掉 `deleteFilter` 参数。注意 v3.4 该测试位于 `data/parquet/vectorized/` 包，与 v3.5/v4.0 的 `data/vectorized/parquet/` 不同。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+4/-2 lines)

**修改目的**：v3.5 的对应测试适配新签名。

**工作逻辑**：与 v3.4 同样去掉 `null` 的 `deleteFilter` 参数，但路径在 `data/vectorized/parquet/`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetDictionaryEncodedVectorizedReads.java` (+1/-1 lines)

**修改目的**：v3.5 字典编码向量化测试适配新签名。

**工作逻辑**：`buildReader(schema, type, ImmutableMap.of(), null, allocator)` 改为 `buildReader(schema, type, ImmutableMap.of(), allocator)`。注意此测试 v3.4 不存在，故 v3.4 无对应改动。

## 总结

本提交把 #14652 的"删除过滤从向量化 reader 移到 `BaseBatchReader.BatchDeleteFilter`"重构 backport 到 Spark 3.4 与 3.5，使三个 Spark 版本的向量化读取路径在结构与可测试性上对齐。改动与 2951 设计完全相同，仅因版本差异在测试包路径与是否存在字典编码测试上有细微不同，是一次直接的跨版本同步 backport，行为不变。
