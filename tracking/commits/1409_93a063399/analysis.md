# 提交 1409：Parquet: Use native getRowIndexOffset support instead of calculating it (#11520)

## 提交信息

- **序号**：1409 / 4088
- **哈希**：93a06339992a002fa907f515e68f4c20a3af2253
- **短哈希**：93a063399
- **日期**：2024-11-20（Wed Nov 20 17:46:57 2024 -0800）
- **作者**：Wing Yew Poon <wypoon@cloudera.com>
- **提交说明**：Parquet: Use native getRowIndexOffset support instead of calculating it (#11520)
- **PR/Issue**：#11520

## 总体目的

Iceberg 的 Parquet 读取栈需要在读取数据时维护「当前 row group 在文件内的起始行号」（row index offset），用于：

- `PositionReader`（读取 `_pos` 元数据列 / `ROW_POSITION`）需要知道 row group 的起始行号才能输出每行的全局行位置；
- 向量化读取的 `ColumnarBatchReader` / `VectorizedArrowReader.PositionVectorReader` 需要同样的起始行号给 position 向量赋值；
- 删除向量（`PositionDelete`）匹配也需要行位置对齐。

此前 Iceberg 在 `ReadConf` 中自己计算这套偏移：`generateOffsetToStartPos(Schema)` 在 `ReadConf` 构造时**额外打开一次 `ParquetFileReader`**，遍历所有 row group 累加 `rowCount`，构建 `Map<offset, startRowPos>`，再把每个被选中 row group 的起始行号写入 `long[] startRowPositions`。`ParquetReader.FileIterator` / `VectorizedParquetReader.FileIterator` 在每次推进 row group 时把这个位置传给 `model.setPageSource(pages, rowPosition)` 或 `model.setRowGroupInfo(pages, metadata, rowPosition)`。

新版的 Parquet 库已经在 `PageReadStore#getRowIndexOffset()` 上原生提供 row index offset（返回 `OptionalLong`），无需应用层再算一遍。

本提交的目的是：

1. 删除 `ReadConf` 中自算 row positions 的逻辑（`generateOffsetToStartPos`、`startRowPositions` 字段与 getter），同时省去那次额外的 `ParquetFileReader` 打开/关闭 IO；
2. 在 `ParquetValueReader` 与 `VectorizedReader` 接口上新增无 `rowPosition` 重载的 `setPageSource(PageReadStore)` / `setRowGroupInfo(PageReadStore, Map)`，让实现直接从 `PageReadStore#getRowIndexOffset()` 取行偏移；
3. 把旧的带 `rowPosition` 的方法标记为 `@Deprecated`（1.8.0 起，1.9.0 移除），其实现改为委托给新方法，保持二进制兼容；
4. 让 reader 实现链路（`ParquetValueReaders` 各 reader、`BaseBatchReader`、`VectorizedArrowReader`、Spark `ColumnarBatchReader`）切换到新 API。

## 如何达成设计目的

整体策略是「新增无 rowPosition 重载 → 各实现在新重载里通过 `pageStore.getRowIndexOffset().orElseThrow(...)` 自取 → 旧重载标记 deprecated 并委托新重载 → 上层迭代器改调新方法 → 移除 ReadConf 中自算逻辑」。

具体步骤：

1. **接口层**：
   - `ParquetValueReader` 新增 `default void setPageSource(PageReadStore)`（默认抛 `UnsupportedOperationException`），旧 `setPageSource(PageReadStore, long)` 加 `@Deprecated`。
   - `VectorizedReader` 新增 `default void setRowGroupInfo(PageReadStore, Map<ColumnPath, ColumnChunkMetaData>)`（默认抛 `UnsupportedOperationException`），旧三参重载加 `@Deprecated`。

2. **实现层（无位置需求的 reader）**：
   - `ParquetValueReaders.NullReader`、`ConstantReader`、`ColumnReader`、`StructReader`、`OptionReader`、`MapReader`、`ListReader` 等只新增空实现或委托子 reader。
   - `VectorizedArrowReader.NullReader`、`ConstantReader`、`DeletedVectorReader` 新增空实现。

3. **位置相关 reader**：
   - `ParquetValueReaders.PositionReader#setPageSource(PageReadStore)`：从 `pageStore.getRowIndexOffset().orElseThrow(...)` 取行偏移并赋给 `rowGroupStart`，`rowOffset = -1`；旧重载调新方法。
   - `VectorizedArrowReader.PositionVectorReader#setRowGroupInfo(PageReadStore, Map)`：同样从 `pageStore.getRowIndexOffset()` 取并赋给 `rowStart`。
   - Spark 3.3/3.4/3.5 的 `ColumnarBatchReader#setRowGroupInfo(PageReadStore, Map)`：调 `super.setRowGroupInfo(...)` 后，从 `pageStore.getRowIndexOffset()` 取并赋给 `rowStartPosInBatch`。

4. **上层迭代器**：
   - `ParquetReader.FileIterator` 移除 `long[] rowGroupsStartRowPos` 字段，调用从 `model.setPageSource(pages, rowPosition)` 改为 `model.setPageSource(pages)`。
   - `VectorizedParquetReader.FileIterator` 同样移除字段，调用从 `model.setRowGroupInfo(pages, metadata, rowPosition)` 改为 `model.setRowGroupInfo(pages, metadata)`。

5. **ReadConf 清理**：
   - 删除 `startRowPositions` 字段、`startRowPositions()` getter、`generateOffsetToStartPos(Schema)` 私有方法，以及构造函数中相关初始化；
   - 删除拷贝构造中 `this.startRowPositions = toCopy.startRowPositions`；
   - 移除多余 import：`MetadataColumns`、`FileDecryptionProperties`、`Maps`、`UncheckedIOException`。

由于现在依赖 `PageReadStore#getRowIndexOffset()` 返回存在值，若底层 Parquet 实现未提供 row index offset，会抛 `IllegalArgumentException("PageReadStore does not contain row index offset")`，提示升级 Parquet 库版本。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ReadConf.java`

**修改目的**：移除自算 row positions 的逻辑与字段。

**工作逻辑**：

- 移除 import：`java.io.UncheckedIOException`、`org.apache.iceberg.MetadataColumns`、`org.apache.iceberg.relocated.com.google.common.collect.Maps`、`org.apache.parquet.crypto.FileDecryptionProperties`；
- 删除字段 `private final long[] startRowPositions;`；
- 构造函数中删除 `this.startRowPositions = new long[rowGroups.size()];` 与 `Map<Long, Long> offsetToStartPos = generateOffsetToStartPos(expectedSchema);`；
- 循环中删除 `startRowPositions[i] = offsetToStartPos == null ? 0 : offsetToStartPos.get(rowGroup.getStartingPos());`；
- 拷贝构造中删除 `this.startRowPositions = toCopy.startRowPositions;`；
- 删除私有方法 `generateOffsetToStartPos(Schema)`（额外打开 `ParquetFileReader` 遍历 row group 累加 rowCount，构建 offset→startRowPos 映射）；
- 删除 getter `long[] startRowPositions()`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReader.java`

**修改目的**：在接口上新增无 rowPosition 的 `setPageSource`，弃用旧重载。

**工作逻辑**：

```java
@Deprecated
void setPageSource(PageReadStore pageStore, long rowPosition);

default void setPageSource(PageReadStore pageStore) {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " doesn't implement setPageSource(PageReadStore)");
}
```

旧方法 javadoc 标注 `@deprecated since 1.8.0, will be removed in 1.9.0; use setPageSource(PageReadStore) instead`。default 实现抛异常，强制实现类显式覆写。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java`

**修改目的**：让所有内部 reader 实现新 API，位置相关 reader 通过 `getRowIndexOffset` 取行偏移。

**工作逻辑**：

- `NullReader` / `ConstantReader`：新增空 `setPageSource(PageReadStore)`。
- `PositionReader`：
  ```java
  @Override
  public void setPageSource(PageReadStore pageStore, long rowPosition) {
    setPageSource(pageStore);
  }

  @Override
  public void setPageSource(PageReadStore pageStore) {
    this.rowGroupStart =
        pageStore.getRowIndexOffset()
            .orElseThrow(() -> new IllegalArgumentException(
                "PageReadStore does not contain row index offset"));
    this.rowOffset = -1;
  }
  ```
- `ColumnReader`：旧 `setPageSource(pageStore, rowPosition)` 体改为 `setPageSource(pageStore)`，新方法只做 `column.setPageSource(pageStore.getPageReader(desc))`。
- `StructReader` / `OptionReader` / `MapReader` / `ListReader`：旧重载调 `setPageSource(pageStore)`，新重载递归调子 reader 的 `setPageSource(pageStore)`（去掉 `rowPosition` 参数透传）。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetReader.java`

**修改目的**：`FileIterator` 不再持有 row positions 数组，改调新 API。

**工作逻辑**：

- 删除 `private final long[] rowGroupsStartRowPos;` 字段及构造中 `this.rowGroupsStartRowPos = conf.startRowPositions();`；
- `nextRowGroup()` 中删除 `long rowPosition = rowGroupsStartRowPos[nextRowGroup];`，调用改为 `model.setPageSource(pages);`。

### `parquet/src/main/java/org/apache/iceberg/parquet/VectorizedParquetReader.java`

**修改目的**：`FileIterator` 同样切到新 API。

**工作逻辑**：

- 删除 `private final long[] rowGroupsStartRowPos;` 字段及构造中赋值；
- `nextRowGroup()` 中删除 `long rowPosition = rowGroupsStartRowPos[nextRowGroup];`，调用改为 `model.setRowGroupInfo(pages, columnChunkMetadata.get(nextRowGroup));`。

### `parquet/src/main/java/org/apache/iceberg/parquet/VectorizedReader.java`

**修改目的**：在接口上新增无 rowPosition 的 `setRowGroupInfo`，弃用旧三参重载。

**工作逻辑**：

```java
@Deprecated
void setRowGroupInfo(PageReadStore pages, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition);

default void setRowGroupInfo(PageReadStore pages, Map<ColumnPath, ColumnChunkMetaData> metadata) {
  throw new UnsupportedOperationException(
      this.getClass().getName() + " doesn't implement setRowGroupInfo(PageReadStore, Map<ColumnPath, ColumnChunkMetaData>)");
}
```

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/BaseBatchReader.java`

**修改目的**：让抽象基类把新 API 委托到子 readers。

**工作逻辑**：旧三参重载改为调 `setRowGroupInfo(pageStore, metaData)`；新两参重载循环调用每个 `VectorizedArrowReader.setRowGroupInfo(pageStore, metaData)`（不再传 rowPosition）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/VectorizedArrowReader.java`

**修改目的**：让 Arrow 向量化 reader 体系支持新 API，位置向量 reader 从 `getRowIndexOffset` 取值。

**工作逻辑**：

- 主类 `setRowGroupInfo(PageReadStore, Map)` 新增：与旧三参重载共享逻辑（取 `ColumnChunkMetaData`、设置 dictionary），不再依赖 `rowPosition`；旧三参重载调新方法。
- `NullReader` / `ConstantReader` / `DeletedVectorReader` 内部类：新增空 `setRowGroupInfo(PageReadStore, Map)`。
- `PositionVectorReader` 内部类：
  ```java
  @Override
  public void setRowGroupInfo(PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata, long rowPosition) {
    setRowGroupInfo(source, metadata);
  }

  @Override
  public void setRowGroupInfo(PageReadStore source, Map<ColumnPath, ColumnChunkMetaData> metadata) {
    this.rowStart =
        source.getRowIndexOffset()
            .orElseThrow(() -> new IllegalArgumentException(
                "PageReadStore does not contain row index offset"));
  }
  ```

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`
### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`
### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/ColumnarBatchReader.java`

三个文件改动相同。

**修改目的**：让 Spark 各版本的 `ColumnarBatchReader` 从 `PageReadStore` 直接取 row group 起始行号。

**工作逻辑**：

```java
@Override
public void setRowGroupInfo(PageReadStore pageStore, Map<ColumnPath, ColumnChunkMetaData> metaData, long rowPosition) {
  setRowGroupInfo(pageStore, metaData);
}

@Override
public void setRowGroupInfo(PageReadStore pageStore, Map<ColumnPath, ColumnChunkMetaData> metaData) {
  super.setRowGroupInfo(pageStore, metaData);
  this.rowStartPosInBatch =
      pageStore.getRowIndexOffset()
          .orElseThrow(() -> new IllegalArgumentException(
              "PageReadStore does not contain row index offset"));
}
```

旧实现把 `rowPosition` 直接赋给 `rowStartPosInBatch`；新实现改由 `pageStore.getRowIndexOffset()` 获取，与上游 `super` 保持一致。

## 小结

- **成效**：
  1. 移除了 `ReadConf` 中为计算 row positions 而额外打开 `ParquetFileReader` 的 IO 开销（每次 `ReadConf` 构造节省一次文件打开/关闭与元数据遍历）；
  2. 统一由 Parquet 库原生 `PageReadStore#getRowIndexOffset()` 提供行偏移，避免应用层与库内不一致；
  3. 简化了 reader 调用链：上层不再需要把 `rowPosition` 透传给每一层 reader，reader 自己按需取；
  4. 通过 deprecated + default 抛异常的双轨方式提供平滑迁移路径（1.8.0 弃用，1.9.0 移除）。
- **影响范围**：跨 parquet、arrow、spark 3.3/3.4/3.5 共 11 个文件，+154/-66 行；属于读取栈的接口级重构。无新增测试（依赖现有读取/位置相关测试覆盖）。
- **回迁到 1.4.x 的注意事项**：1.4.x 上的 Parquet 依赖版本可能未提供 `PageReadStore#getRowIndexOffset()`（该方法在较新 parquet-common 中才有）。**不建议直接回迁**，原因：
  1. 若 1.4.x 的 parquet 库版本较旧，`getRowIndexOffset` 不存在或返回空 `OptionalLong`，新代码会抛 `IllegalArgumentException("PageReadStore does not contain row index offset")`，导致带 row position 列的读取直接失败；
  2. 该重构是性能优化与代码简化，不修复 bug；1.4.x 维持现有 `ReadConf.generateOffsetToStartPos` 自算逻辑即可正常工作；
  3. 若确实要回迁，必须同时升级 1.4.x 的 parquet 依赖到提供 `getRowIndexOffset` 的版本，并验证所有 reader 路径（向量化 + 非向量化 + Spark 各版本 ColumnarBatchReader + 删除向量）在该版本下 `getRowIndexOffset` 都返回非空；
  4. 该 PR 引入的 `@Deprecated` 标注面向 1.8.0/1.9.0 的演进路线，与 1.4.x 的 API 演进阶段不匹配，回迁反而会让 1.4.x 的 API 兼容性语义混乱。
