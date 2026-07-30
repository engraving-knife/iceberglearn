# 提交 1444：Spark: remove ROW_POSITION from project schema (#11610)

## 提交信息

- **序号**：1444
- **哈希**：8e0031c01fa5da75555938dd527c690a0727d50e
- **短哈希**：8e0031c01
- **日期**：2024-11-28（Thu Nov 28 11:34:23 2024 -0800）
- **作者**：Huaxin Gao <huaxin.gao11@gmail.com>
- **提交说明**：Spark: remove ROW_POSITION from project schema (#11610)
- **PR/Issue**：#11610

## 总体目的

Iceberg 的 Spark 向量化批量读取器 `BaseBatchReader` 在 `newParquetIterable` 中读取数据时，会先根据是否存在删除（`deleteFilter`）确定 `requiredSchema`。在历史实现中，当 `deleteFilter.hasPosDeletes()` 为真（即存在位置删除 position delete）时，会临时把 `MetadataColumns.ROW_POSITION` 这个隐藏元数据列追加到投影 schema 中再交给 `Parquet.read(...).project(...)`，目的是让旧的 `ReadConf.generateOffsetToStartPos(Schema schema)` 方法能从投影 schema 中拿到 `ROW_POSITION` 列以生成行偏移映射。

代码注释中已明确标注："This is not needed any more after #10107 is merged."——即 PR #10107 合入后，`generateOffsetToStartPos` 这条调用链已不再依赖投影 schema 中是否包含 `ROW_POSITION`，但当时并未同步清理 `BaseBatchReader` 中的这段 workaround 代码。这段"幽灵代码"不仅多余，还会让 Parquet 读取器实际去读取一个用不到的列，造成不必要的 IO 与解码开销，并使投影 schema 与实际需要的数据列不一致。

本提交彻底删除这段 workaround，让 `BaseBatchReader.newParquetIterable` 直接使用 `requiredSchema` 作为 `Parquet.read(...).project(...)` 的入参，并清理因此变成未使用的 import（`java.util.List`、`Lists`、`Types`）。修改同步应用到 Spark v3.3、v3.4、v3.5 三个版本的镜像文件。

## 如何达成设计目的

1. **直接删除 workaround 分支**：把原先根据 `hasPositionDelete` 临时构造 `projectedSchema`（必要时追加 `MetadataColumns.ROW_POSITION` 并 `new Schema(columns)`）的逻辑全部移除，只保留 `Schema requiredSchema = deleteFilter != null ? deleteFilter.requiredSchema() : expectedSchema();` 一行。
2. **改用 requiredSchema 投影**：`Parquet.read(inputFile).project(requiredSchema)...`，与 ORC 路径和其他读取器保持一致。
3. **清理未用 import**：删除 `java.util.List`、`org.apache.iceberg.relocated.com.google.common.collect.Lists`、`org.apache.iceberg.types.Types` 三个不再被引用的 import。
4. **三版本同步**：spark/v3.3、spark/v3.4、spark/v3.5 三个模块下的同名文件 `BaseBatchReader.java` 做完全相同的修改。

## 修改详情

### `spark/v3.3/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java`
### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java`
### `spark/v3.5/spark/src/main/java/org/apache/iceberg/spark/source/BaseBatchReader.java`

三个文件改动完全相同。

**修改目的**：移除向量化 Parquet 读取时为位置删除场景额外追加 `ROW_POSITION` 列的 workaround。

**工作逻辑**：

修改前（核心片段）：

```java
Schema requiredSchema = deleteFilter != null ? deleteFilter.requiredSchema() : expectedSchema();
boolean hasPositionDelete = deleteFilter != null ? deleteFilter.hasPosDeletes() : false;
Schema projectedSchema = requiredSchema;
if (hasPositionDelete) {
  // We need to add MetadataColumns.ROW_POSITION in the schema for
  // ReadConf.generateOffsetToStartPos(Schema schema). This is not needed any
  // more after #10107 is merged.
  List<Types.NestedField> columns = Lists.newArrayList(requiredSchema.columns());
  if (!columns.contains(MetadataColumns.ROW_POSITION)) {
    columns.add(MetadataColumns.ROW_POSITION);
    projectedSchema = new Schema(columns);
  }
}

return Parquet.read(inputFile)
    .project(projectedSchema)
    ...
```

修改后：

```java
Schema requiredSchema = deleteFilter != null ? deleteFilter.requiredSchema() : expectedSchema();

return Parquet.read(inputFile)
    .project(requiredSchema)
    ...
```

同时移除三个不再使用的 import：

- `import java.util.List;`
- `import org.apache.iceberg.relocated.com.google.common.collect.Lists;`
- `import org.apache.iceberg.types.Types;`

## 小结

- **成效**：清理了 `BaseBatchReader` 中自 #10107 合入后即失效的 workaround 代码，避免在位置删除场景下向 Parquet 读取器投影一个用不到的 `ROW_POSITION` 列，减少了不必要的列读取开销，并保持投影 schema 与实际需求一致；同时清理了未使用的 import，使代码更整洁。
- **影响范围**：仅修改 Spark v3.3/v3.4/v3.5 三个模块的 `BaseBatchReader.java`，每个文件减少约 14 行（含 import 与 workaround 代码），共 3 个文件、3 行新增、48 行删除。无 API 变更、无测试改动。
- **回迁到 1.4.x 的注意事项**：这是一个性能/整洁性优化，不修复功能性 bug，**优先级较低**。回迁前提是 1.4.x 上已合入 #10107（即 `ReadConf.generateOffsetToStartPos` 已经不再依赖投影 schema 中的 `ROW_POSITION`）。如果 1.4.x 上 `generateOffsetToStartPos` 仍依赖 `ROW_POSITION`，则贸然删除 workaround 会导致位置删除场景下偏移映射生成失败。回迁前需先在 1.4.x 上搜索 `generateOffsetToStartPos` 的调用与实现，确认其已不读取投影 schema 中的 `ROW_POSITION` 列。另外，1.4.x 上若 Spark 版本范围与 main 不同（例如仍维护 v3.2），需要同步处理所有相关 Spark 版本目录下的 `BaseBatchReader.java`。如果 1.4.x 上该 workaround 仍然必要，则不应回迁本提交。
