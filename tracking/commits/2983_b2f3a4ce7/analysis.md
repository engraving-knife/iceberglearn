# 提交 2983：Spark: Backport ORC vectorized reader to use the delete filter (#14794)

## 提交信息

- **序号**：2983 / 4088
- **哈希**：b2f3a4ce7bcb21b966cd3928ae964cc98b9cbdf5
- **短哈希**：b2f3a4ce7
- **日期**：2025-12-09
- **作者**：pvary
- **提交说明**：Spark: Backport ORC vectorized reader to use the delete filter (#14794)
- **PR/Issue**：#14794（回移自 #14746）

## 总体目的

这是一次 backport 类提交，将 PR #14746（"Spark: ORC vectorized reader to use the delete filter"）的修复回移到仍在维护的 `spark/v3.4` 与 `spark/v3.5` 两个版本目录。原 PR 修复的是 Spark ORC 向量化读取器在读取 `IS_DELETED` 元数据列时的一个正确性缺陷。

具体问题：在 `VectorizedSparkOrcReaders.buildReader` 中，当投影 schema 包含 `MetadataColumns.IS_DELETED` 时，原代码对该列返回一个常量向量 `new ConstantColumnVector(field.type(), batchSize, false)`——即无论该行是否被位置删除（position delete）标记为已删除，`IS_DELETED` 永远显示 `false`。这意味着在使用 ORC 向量化读取器、且表中存在位置删除文件时，查询 `IS_DELETED` 列会得到错误结果（已删除的行不会被正确标记），导致依赖该列的下游逻辑（如删除数据审计、CDC 等）出错。相比之下，Parquet 向量化读取器已经正确地通过 `DeletedColumnVector` 与 `DeleteFilter` 协作来反映逐行的删除状态。

修复后的行为：ORC 向量化读取器对 `IS_DELETED` 列改为创建 `DeletedColumnVector` 并分配 `boolean[batchSize]` 数组（通过 `setValue`），使 `DeleteFilter` 能够在应用位置删除时把对应行标记为 `true`，与 Parquet 路径行为一致。回移的原因是 v3.4 与 v3.5 仍是受支持的 Spark 版本，需要让该正确性修复覆盖这些版本，避免版本间行为不一致。

## 如何达成设计目的

整体思路与原 PR 一致：修改 `VectorizedSparkOrcReaders` 中 `IS_DELETED` 分支，把常量向量替换为可被 `DeleteFilter` 填充的 `DeletedColumnVector`；同时在测试侧把 Parquet 测试中既有的 `DeleteFilter` 测试桩（`TestDeleteFilter` 与 `CustomizedPositionDeleteIndex`）提升到共享的 `TestHelpers` 中（重命名为 `CustomizedDeleteFilter`），让 ORC 元数据列测试也能复用，并新增 `testReadRowNumbersWithDelete` 测试验证 ORC 向量化读取在带位置删除时 `IS_DELETED` 列被正确标记。改动覆盖 `spark/v3.4` 与 `spark/v3.5` 两个版本目录，每个版本 4 个文件。

## 修改详情

下面按文件说明。由于 v3.4 与 v3.5 两个版本目录下同名文件的改动内容基本一致（仅 `VectorizedSparkOrcReaders` 在 v3.5 多一个 `UnknownType` 分支，与本次改动无关），这里对每个文件只描述一次。

### `spark/v3.4/spark/.../VectorizedSparkOrcReaders.java` 与 `spark/v3.5/spark/.../VectorizedSparkOrcReaders.java` (+3/-1 each)

**修改目的**：修复 ORC 向量化读取器中 `IS_DELETED` 列永远返回 `false` 的正确性缺陷。

**工作逻辑**：
在 `buildReader` 中处理元数据字段的分支里，`field.equals(MetadataColumns.IS_DELETED)` 分支原本是 `fieldVectors.add(new ConstantColumnVector(field.type(), batchSize, false))`。改为：
```java
DeletedColumnVector deletedVector = new DeletedColumnVector(field.type());
deletedVector.setValue(new boolean[batchSize]);
fieldVectors.add(deletedVector);
```
`DeletedColumnVector` 是一个可写的列向量，`setValue(boolean[])` 设置其底层数组。这样当上层 `DeleteFilter` 应用位置删除时，可以按行把对应位置置为 `true`，使 `IS_DELETED` 列正确反映删除状态，而非恒为 `false`。这与 Parquet 向量化读取器的处理方式对齐。

### `spark/v3.4/spark/.../TestHelpers.java` 与 `spark/v3.5/spark/.../TestHelpers.java` (+71/-0 each)

**修改目的**：把 Parquet 测试中既有的删除过滤测试桩提升为共享工具类，供 ORC 与 Parquet 测试复用。

**工作逻辑**：
新增两个 public static 内部类（从 `TestSparkParquetReadMetadataColumns` 中搬迁并重命名而来）：

- `CustomizedDeleteFilter extends DeleteFilter<InternalRow>`：构造时接收 `hasDeletes`、`tableSchema`、`projectedSchema`，调用父类构造器 `super("", List.of(), tableSchema, projectedSchema, new DeleteCounter(), true)`。`asStructLike`/`getInputFile` 返回 null（测试不需要）。`hasPosDeletes()` 返回 `hasDeletes`。`deletedRowPositions()` 返回一个 `CustomizedPositionDeleteIndex`，当 `hasDeletes` 为 true 时调用 `deletedRowPos.delete(98, 103)` 标记位置 98–102（左闭右开）为删除——注释说明这跨越两个 row group `[0,100)` 与 `[100,200)`，可验证跨 row group 的删除标记。

- `CustomizedPositionDeleteIndex implements PositionDeleteIndex`：基于 `Set<Long>` 实现，支持 `delete(long)`、`delete(long,long)`（左闭右开区间逐个 add）、`isDeleted(long)`、`isEmpty()`。

### `spark/v3.4/spark/.../TestSparkOrcReadMetadataColumns.java` 与 `spark/v3.5/spark/.../TestSparkOrcReadMetadataColumns.java` (+49/-5 each)

**修改目的**：为 ORC 向量化读取新增带位置删除的测试，并把 `readAndModel` 改为支持 `DeleteFilter`。

**工作逻辑**：
新增 import（`DeleteFilter`、`BatchReaderUtil`、`assumeThat`）。新增常量 `NO_DELETES_FILTER = new TestHelpers.CustomizedDeleteFilter(false, DATA_SCHEMA, PROJECTION_SCHEMA)` 与 `RECORDS_PER_BATCH = 10`。

- `readAndModel` 方法签名新增 `DeleteFilter<InternalRow> deleteFilter` 参数；vectorized 分支下 builder 增加 `.recordsPerBatch(RECORDS_PER_BATCH)`，并把读取结果从 `batchesToRows(builder.build())` 改为 `batchesToRows(BatchReaderUtil.applyDeleteFilter(builder.build(), deleteFilter))`——即构建出 batch reader 后，先用 `BatchReaderUtil.applyDeleteFilter` 把删除过滤器应用到 reader 上，再把 batch 转回行。既有测试调用补上 `NO_DELETES_FILTER`。

- 新增 `testReadRowNumbersWithDelete`：先用 `assumeThat(vectorized).isTrue()` 跳过非向量化模式（因为修复只针对向量化路径）；拷贝 `EXPECTED_ROWS`，把位置 98–102 的行的第 3 列（`IS_DELETED`）置为 `true`；构造 `CustomizedDeleteFilter(true, ...)` 并调用 `readAndModel`，验证这些行的 `IS_DELETED` 被正确标记为 true。这直接验证了本次修复的正确性——修复前这些行会错误地显示 `false`。

### `spark/v3.4/spark/.../TestSparkParquetReadMetadataColumns.java` 与 `spark/v3.5/spark/.../TestSparkParquetReadMetadataColumns.java` (+3/-75 each)

**修改目的**：把 Parquet 测试中本地的 `TestDeleteFilter`/`CustomizedPositionDeleteIndex` 删除，改为引用共享的 `TestHelpers.CustomizedDeleteFilter`，消除重复代码。

**工作逻辑**：
删除本地的 `TestDeleteFilter` 与 `CustomizedPositionDeleteIndex` 两个内部类（约 64 行），以及不再需要的 import（`Set`、`StructLike`、`DeleteCounter`、`PositionDeleteIndex`、`InputFile`、`Sets`）。原先 `new TestDeleteFilter(true)` 改为 `new TestHelpers.CustomizedDeleteFilter(true, DATA_SCHEMA, PROJECTION_SCHEMA)`，`new TestDeleteFilter(false)` 同样改为 `new TestHelpers.CustomizedDeleteFilter(false, DATA_SCHEMA, PROJECTION_SCHEMA)`。Parquet 测试的行为不变，只是测试桩来源变为共享的 `TestHelpers`。

## 总结

该提交是 PR #14746 的 backport，修复了 Spark ORC 向量化读取器中 `IS_DELETED` 元数据列永远返回 `false` 的正确性缺陷——改为使用可被 `DeleteFilter` 填充的 `DeletedColumnVector`，使带位置删除的 ORC 表在向量化读取时能正确标记已删除行，与 Parquet 路径行为对齐。同时把 Parquet 测试中的删除过滤测试桩提升到共享 `TestHelpers`，并为 ORC 新增 `testReadRowNumbersWithDelete` 测试覆盖跨 row group 的删除标记场景。改动覆盖 `spark/v3.4` 与 `spark/v3.5` 两个版本目录，核心价值在于消除 ORC 与 Parquet 在删除语义上的行为差异并保证多版本一致性。
