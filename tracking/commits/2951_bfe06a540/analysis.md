# 提交 2951：Spark: Move DeleteFiltering out from the vectorized reader (#14652)

## 提交信息

- **序号**：2951 / 4088
- **哈希**：bfe06a540d9d31d89b5161b20dcdcf6b1b6e2ef4
- **短哈希**：bfe06a540
- **日期**：2025-12-03
- **作者**：pvary
- **提交说明**：Spark: Move DeleteFiltering out from the vectorized reader (#14652)
- **PR/Issue**：#14652

## 总体目的

Iceberg 的 Spark 向量化读取路径里，"应用删除"（delete filtering）这一职责原本嵌在向量化 reader 内部。具体来说，`ColumnarBatchReader` 和 `CometColumnarBatchReader` 在 `read()` 一个 batch 时，会先读出全部列向量，再当场做：根据 `DeleteFilter` 构造 `isDeleted` 数组或 `rowIdMapping`，把 `DeletedColumnVector` 填值、或用 `ColumnVectorWithFilter` 包裹列向量，最后 `removeExtraColumns` 去掉等值删除引入的额外列，并 `setNumRows(numLiveRows)`。这意味着 reader 既要负责"读 Parquet 列"，又要负责"应用 Iceberg 删除语义"，两件事耦合在 `read()` 里，难以独立测试、也阻碍后续优化（例如想在不同时机应用删除、或在 reader 外部做更多 batch 级变换）。

本提交把这层"删除过滤"逻辑从向量化 reader 内部抽出来，移到 `BaseBatchReader` 中新增的 `BatchDeleteFilter` 静态内部类，并通过 `CloseableIterable.transform(iterable, filter::filterBatch)` 在 reader 产出的 batch 流上做后置转换。reader 只负责读出"原始" batch（行数=`batchSize`，含 `requiredSchema` 需要的元数据列），删除过滤作为外部 pipeline 一环独立运行。为支持这种解耦，新增 `UpdatableDeletedColumnVector` 接口让 `BatchDeleteFilter` 可以反向给 `DeletedColumnVector`/`CometDeletedColumnVector` 填值；并新增 `CometDeletedColumnVector` 类承载 Comet 路径下的删除列。这是为后续优化（如把删除过滤推迟到更晚、或与 Spark 的列处理解耦）铺路的纯重构，行为应保持不变。

## 如何达成设计目的

设计核心是"职责分离 + 数据流后置转换"。
1) reader 不再持有 `DeleteFilter`：`ColumnarBatchReader`/`CometColumnarBatchReader` 移除 `deletes`/`hasIsDeletedColumn`/`rowStartPosInBatch` 字段及 `setDeleteFilter` 方法，`read()` 直接返回未过滤的 batch（`setNumRows(batchSize)`）。
2) `BaseBatchReader.newBatchIterable` 不再把 `SparkDeleteFilter` 传给 reader 构造，而是先建出原始 batch iterable，再用 `CloseableIterable.transform(iterable, new BatchDeleteFilter(deleteFilter)::filterBatch)` 在外层应用删除。
3) 新增 `BatchDeleteFilter`：封装原先散在 reader 里的 `buildIsDeleted`/`buildRowIdMapping`/`removeExtraColumns` 逻辑，并按 schema 找到 `ROW_POSITION` 列索引（用于从 batch 内读取行起始位置，替代原来 reader 维护的 `rowStartPosInBatch`）与判断是否含 `IS_DELETED` 列。
4) 新增 `UpdatableDeletedColumnVector` 接口与 `CometDeletedColumnVector` 类，让删除列可以被外部填值；`DeletedColumnVector` 实现该接口，`CometDeleteColumnReader` 内部改用 `CometDeletedColumnVector` 而非每次 `new CometDeleteColumnReader(isDeleted)`。
5) `BatchDataReader` 改为始终构造 `SparkDeleteFilter`（不再 `task.deletes().isEmpty() ? null`），并用 `deleteFilter.requiredSchema()` 决定 `constantsMap`，确保 `BatchDeleteFilter` 能拿到 `ROW_POSITION` 等列。
6) 测试侧新增 `BatchReaderUtil` 工具类，让 metadata 列读取测试可以显式套用 `BatchDeleteFilter`；原先用 Mockito mock `DeleteFilter` 的测试改为用真实的 `TestDeleteFilter` 子类，因为删除逻辑已经移出 reader、必须在测试里手动接上 filter pipeline。

## 修改详情

### `.baseline/checkstyle/checkstyle-suppressions.xml` (+2/-0 lines)

**修改目的**：为新类 `CometDeletedColumnVector` 抑制 `IllegalImport` 检查。

**工作逻辑**：
新增一行 `<suppress files="org.apache.iceberg.spark.data.vectorized.CometDeletedColumnVector" checks="IllegalImport"/>`，与既有 `CometColumnReader` 的抑制规则并列。原因是 `CometDeletedColumnVector` 需要 `import org.apache.comet.shaded.arrow.vector.ValueVector` 等 Comet 阴影化包，checkstyle 默认禁止这类 import，需显式放行，与同包其他 Comet 类的处理一致。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java` (+3/-54 lines)

**修改目的**：从 `ColumnarBatchReader` 移除所有删除过滤逻辑，让它只负责读列。

**工作逻辑**：
- 删除字段 `hasIsDeletedColumn`、`deletes`、`rowStartPosInBatch`，以及构造器里检测 `DeletedVectorReader` 的逻辑。
- 删除 `setDeleteFilter(DeleteFilter)` 方法。
- `setRowGroupInfo` 不再从 `PageReadStore.getRowIndexOffset()` 取 `rowStartPosInBatch`（这段逻辑移到 `BatchDeleteFilter` 里，从 batch 内的 `ROW_POSITION` 列读取）。
- `read()` 简化为 `return new ColumnBatchLoader(numRowsToRead).loadDataToColumnBatch()`，不再累加 `rowStartPosInBatch`。
- `ColumnBatchLoader.loadDataToColumnBatch()` 删除 `hasIsDeletedColumn`/`buildIsDeleted`/`buildRowIdMapping`/`ColumnVectorWithFilter`/`removeExtraColumns`/`numLiveRows` 全部分支，直接 `new ColumnarBatch(vectors).setNumRows(batchSize)` 返回未过滤 batch。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometColumnarBatchReader.java` (+3/-61 lines)

**修改目的**：对 Comet 向量化 reader 做与 `ColumnarBatchReader` 对称的瘦身。

**工作逻辑**：
- 删除 `hasIsDeletedColumn`、`deletes`、`rowStartPosInBatch` 字段与 `setDeleteFilter` 方法。
- `setRowGroupInfo` 不再取 `rowStartPosInBatch`。
- `read()` 直接返回 `new ColumnBatchLoader(numRowsToRead).loadDataToColumnBatch()`。
- `ColumnBatchLoader` 删除 `buildIsDeleted`/`buildRowIdMapping`/`readDeletedColumn`/`ColumnVectorWithFilter`/`removeExtraColumns` 分支，`setNumRows(batchSize)` 返回原始 batch。这样 Comet 路径下删除也由外部 `BatchDeleteFilter` 统一处理。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeleteColumnReader.java` (+27/-21 lines, 净 +6)

**修改目的**：让 Comet 删除列 reader 持有可更新的 `CometDeletedColumnVector`，而非每次按 `isDeleted` 数组新建 reader。

**工作逻辑**：
- 导入从 `org.apache.iceberg.MetadataColumns` 改为 `org.apache.comet.vector.CometVector`。
- 删除原来按 `isDeleted` 数组构造的 `CometDeleteColumnReader(boolean[] isDeleted)` 公开构造器（不再需要，因为删除列不再由 reader 内部即时生成）。
- `DeleteColumnReader` 内部从持有 `boolean[] isDeleted` 改为持有 `final CometDeletedColumnVector deletedVector`，构造时 `new CometDeletedColumnVector(isDeleted)`。
- `readBatch(int total)` 改为 `Native.setIsDeleted(nativeHandle, deletedVector.isDeleted())`，把数组喂给 native。
- 新增 `@Override public CometVector currentBatch() { return deletedVector; }`——返回 `CometDeletedColumnVector` 而非 native batch，这样外部 `BatchDeleteFilter` 在需要时可以通过 `UpdatableDeletedColumnVector` 接口更新它的 `isDeleted` 数组，并且 batch 里的删除列就是这个可更新的 vector。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometDeletedColumnVector.java` (+155/-0 lines, 新文件)

**修改目的**：为 Comet 路径提供一个实现 `UpdatableDeletedColumnVector` 的 `CometVector`，承载 `_deleted` 列。

**工作逻辑**：
`CometDeletedColumnVector extends CometVector implements UpdatableDeletedColumnVector`，持有一个 `boolean[] isDeleted`。构造时 `super(SparkSchemaUtil.convert(Types.BooleanType.get()), false)`。`setValue(boolean[])` 让外部 `BatchDeleteFilter` 在每个 batch 上更新删除标记；`isDeleted()` 供 `CometDeleteColumnReader.readBatch` 取数组传给 native。`getBoolean(int rowId)` 返回 `isDeleted[rowId]`，其余类型访问（`getByte`/`getShort`/.../`getArray`/`getMap`/`getDecimal`/`getUTF8String`/`getBinary`/`getChild`）以及 `getValueVector`/`slice`/`setNumNulls`/`setNumValues`/`numValues` 都抛 `UnsupportedOperationException`——因为这是布尔删除列，只支持布尔读取。`hasNull()`/`numNulls()`/`isNullAt` 返回 false/0。`close()` 空实现。这个类是把删除列从"reader 内部即时生成"改为"可在外部更新的常驻 vector"的关键，使 `BatchDeleteFilter` 能在 batch 流上反复填值。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/CometVectorizedReaderBuilder.java` (+2/-11 lines)

**修改目的**：移除 reader builder 里的 `deleteFilter` 参数，因为 reader 不再需要它。

**工作逻辑**：
- 删除字段 `deleteFilter` 与构造参数，构造器签名少一个参数。
- `vectorizedReader(List)` 从"`readerFactory.apply` 后调 `setDeleteFilter`"简化为直接 `return readerFactory.apply(reorderedFields)`。这让 Comet reader 构建链路与 `deleteFilter` 彻底解耦。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/DeletedColumnVector.java` (+2/-1 lines)

**修改目的**：让 Arrow 路径的 `DeletedColumnVector` 实现 `UpdatableDeletedColumnVector` 接口。

**工作逻辑**：
类声明从 `extends ColumnVector` 改为 `extends ColumnVector implements UpdatableDeletedColumnVector`，并给已有的 `setValue(boolean[])` 加 `@Override`。这样 `BatchDeleteFilter` 可以用统一接口 `instanceof UpdatableDeletedColumnVector` 来发现并填值，无需区分 Arrow 与 Comet 路径。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/UpdatableDeletedColumnVector.java` (+23/-0 lines, 新文件)

**修改目的**：定义"可在外部更新删除标记"的列向量公共接口。

**工作逻辑**：
仅一个方法 `void setValue(boolean[] isDeleted)`。这是连接 `BatchDeleteFilter`（生产 `isDeleted` 数组）与具体列向量实现（`DeletedColumnVector`/`CometDeletedColumnVector`）的桥梁，让过滤逻辑不需要知道具体向量类型。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkParquetReaders.java` (+4/-22 lines)

**修改目的**：从 reader 工厂方法签名移除 `DeleteFilter<InternalRow>` 参数。

**工作逻辑**：
- `buildReader(...)` 与 `buildCometReader(...)` 都删除 `DeleteFilter<InternalRow> deleteFilter` 参数，内部调用对应的 `ReaderBuilder`/`CometVectorizedReaderBuilder` 时也不再传 `deleteFilter`。
- `ReaderBuilder` 内部删除 `deleteFilter` 字段与 `vectorizedReader` 的覆写（该覆写原本就是为 `setDeleteFilter` 而存在）。
- `buildReader` 的便捷重载从 `(schema, fileSchema, idToConstant, deleteFilter)` 改为 `(schema, fileSchema, idToConstant)`。这是 reader 与删除解耦在工厂层的体现：reader 构建时不再需要知道 `DeleteFilter`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java` (+101/-9 lines)

**修改目的**：在 `BaseBatchReader` 引入 `BatchDeleteFilter`，把删除过滤作为 batch 流的后置转换。

**工作逻辑**：
- `newBatchIterable` 改造：原本按格式分支直接返回带 `deleteFilter` 的 iterable；现在分支只产出"原始 batch iterable"（Parquet 调 `newParquetIterable(... deleteFilter.requiredSchema())`，ORC 不变），最后统一 `return CloseableIterable.transform(iterable, new BatchDeleteFilter(deleteFilter)::filterBatch)`。`deleteFilter` 参数标注 `@Nonnull`。
- `newParquetIterable` 改为接收 `Schema requiredSchema`（由调用方提前算好），不再内部判断 `deleteFilter != null`；构建 reader 时调用去掉 `deleteFilter` 参数的 `buildReader`/`buildCometReader`。
- 新增 `@VisibleForTesting static class BatchDeleteFilter`：
  - 构造时扫描 `deletes.requiredSchema()` 找 `ROW_POSITION` 列索引（`rowPositionColumnIndex`）与是否含 `IS_DELETED`（`hasIsDeletedColumn`）。
  - `filterBatch(ColumnarBatch)`：若 `!needDeletes()` 直接返回原 batch；否则拷出列向量，从 `ROW_POSITION` 列读 `rowStartPosInBatch = vectors[rowPositionColumnIndex].getLong(0)`（替代 reader 里维护的累加器），然后走两条分支——`hasIsDeletedColumn` 时用 `ColumnarBatchUtil.buildIsDeleted` 算出 `isDeleted` 并通过 `UpdatableDeletedColumnVector` 接口回填到删除列；否则用 `buildRowIdMapping` 算 `rowIdMapping`，用 `ColumnVectorWithFilter` 包裹所有列。最后 `removeExtraColumns` 处理等值删除的额外列，`setNumRows(numLiveRows)`。
  - `needDeletes()`：`hasIsDeletedColumn || (deletes != null && (hasEqDeletes || hasPosDeletes))`，避免无删除时做无谓变换。
- 新增若干 import：`DeleteFilter`、`ColumnVectorWithFilter`、`ColumnarBatchUtil`、`UpdatableDeletedColumnVector`、`Pair`、`InternalRow`、`ColumnVector`、`VisibleForTesting`、`Nonnull`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/BatchDataReader.java` (+4/-4 lines)

**修改目的**：让 `BatchDataReader` 始终构造 `SparkDeleteFilter`，并把 `requiredSchema` 传给 reader。

**工作逻辑**：
- 原本 `task.deletes().isEmpty() ? null : new SparkDeleteFilter(...)`，现在改为 `new SparkDeleteFilter(filePath, task.deletes(), counter(), true)`（始终非空）。`BaseBatchReader` 已要求 `@Nonnull SparkDeleteFilter`，且即使无删除 `SparkDeleteFilter` 也能正确表达"无删除"语义（`needDeletes()` 返回 false）。
- `constantsMap` 的 schema 参数从 `expectedSchema()` 改为 `deleteFilter.requiredSchema()`，因为 reader 现在投影的是 `requiredSchema`（可能多出 `ROW_POSITION`/`IS_DELETED` 元数据列），常量映射必须基于同一 schema 才能正确匹配列 id。
- 调整语句顺序：先建 `deleteFilter`，再算 `idToConstant`，再 `newBatchIterable`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/BatchReaderUtil.java` (+34/-0 lines, 新文件)

**修改目的**：提供测试用的工具方法，把 `BatchDeleteFilter` 套到 batch 流上。

**工作逻辑**：
`public static CloseableIterable<ColumnarBatch> applyDeleteFilter(CloseableIterable<ColumnarBatch> batches, DeleteFilter<InternalRow> filter)` 直接 `return CloseableIterable.transform(batches, new BaseBatchReader.BatchDeleteFilter(filter)::filterBatch)`。这让 metadata 列读取测试在不走完整 `BatchDataReader` 的情况下，也能对 `VectorizedSparkParquetReaders.buildReader` 产出的 batch 应用删除过滤，模拟生产路径。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReadMetadataColumns.java` (+49/-22 lines)

**修改目的**：把原本 mock `DeleteFilter` 的测试改为使用真实 `TestDeleteFilter` 子类，并在 vectorized 路径上手动套用 `BatchDeleteFilter`。

**工作逻辑**：
- 删除 `mock`/`when` 的静态导入，新增 `StructLike`/`DeleteCounter`/`InputFile`/`BatchReaderUtil` 导入。
- 新增内嵌 `TestDeleteFilter extends DeleteFilter<InternalRow>`：构造 `super("", List.of(), DATA_SCHEMA, PROJECTION_SCHEMA, new DeleteCounter(), true)`，覆写 `hasPosDeletes()` 返回可配置布尔，`deletedRowPositions()` 返回删除 98–103 的 `CustomizedPositionDeleteIndex`（无删除时返回空索引）；`asStructLike`/`getInputFile` 返回 null（测试不需要）。
- `buildReader` 调用从 4 参数（含 `deleteFilter`）改为 3 参数。
- `validate` 新增 `DeleteFilter<InternalRow> filter` 参数，并抽出 `reader(builder, filter)` 方法：vectorized 时 `batchesToRows(BatchReaderUtil.applyDeleteFilter(builder.build(), filter))`，非 vectorized 时直接 `builder.build()`。这样删除过滤在测试里被显式接到 vectorized 路径上，与生产代码的处理方式一致。
- 原 `CustomizedPositionDeleteIndex` 改为 `private static`。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetDictionaryEncodedVectorizedReads.java` (+1/-1 lines)

**修改目的**：适配 `buildReader` 新签名。

**工作逻辑**：`VectorizedSparkParquetReaders.buildReader(schema, type, ImmutableMap.of(), null, allocator)` 改为 `buildReader(schema, type, ImmutableMap.of(), allocator)`——去掉已删除的 `deleteFilter` 参数（原本传 `null`）。

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/data/vectorized/parquet/TestParquetVectorizedReads.java` (+4/-2 lines, 净 +2)

**修改目的**：适配 `buildReader` 新签名。

**工作逻辑**：
- 主调用点 `buildReader(schema, type, idToConstant, null, allocator)` 改为 `buildReader(schema, type, idToConstant, allocator)`。
- 另一处断言 struct 字段不支持向量化读取的测试，`buildReader(... Maps.newHashMap(), null)` 改为 `buildReader(... Maps.newHashMap())`。两处都是去掉 `deleteFilter` 参数。

## 总结

本提交把 Iceberg Spark 向量化读取中的"应用删除"职责从 `ColumnarBatchReader`/`CometColumnarBatchReader` 内部抽离到 `BaseBatchReader.BatchDeleteFilter`，reader 只产出原始 batch、删除过滤作为 `CloseableIterable.transform` 的后置环节独立运行。配套引入 `UpdatableDeletedColumnVector` 接口与 `CometDeletedColumnVector` 类，让删除列可在 batch 间被反复填值。这是一次行为不变的重构，显著降低了 reader 的复杂度、使删除逻辑可独立测试，并为后续把删除过滤推迟到更晚阶段或与列处理解耦奠定基础。
