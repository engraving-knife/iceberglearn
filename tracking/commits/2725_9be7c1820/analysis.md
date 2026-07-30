# 提交 2725：Parquet: Handle NPE for VariantLogicalType in TypeWithSchemaVisitor

## 提交信息

- **序号**：2725 / 4088
- **哈希**：9be7c1820c5e27cc029590af1dfca470dbbeb8b7
- **短哈希**：9be7c1820
- **日期**：2025-10-08 23:10:28 -0600
- **作者**：Aihua Xu
- **提交说明**：Parquet: Handle NPE for VariantLogicalType in TypeWithSchemaVisitor
- **PR/Issue**：#14261

## 总体目的

Iceberg 近期引入了 Variant 类型（变体类型），用于存储半结构化数据。Variant 类型在 Parquet 文件中通过 `VariantLogicalTypeAnnotation` 来标识。在 `TypeWithSchemaVisitor` 中，当检测到 Parquet 的 Variant 逻辑类型注解时，会调用 `visitVariant` 方法来处理。

问题出现在当 Parquet 文件中存在 Variant 逻辑类型注解，但 Iceberg 的 Schema（`iType`）为 null 时。在原始代码中，直接调用 `iType.asVariantType()` 会因为 `iType` 为 null 而抛出 `NullPointerException`。这种情况可能发生在列裁剪（column pruning）场景中，当 Variant 列不在投影模式中但 Parquet 文件仍包含其类型信息时。

此提交修复了这个 NPE，确保当 Iceberg Schema 中没有对应的 Variant 类型时，传入 null 给 `visitVariant` 方法，而不是直接抛出异常。

## 如何达成设计目的

通过在调用 `iType.asVariantType()` 之前添加 null 检查，当 `iType` 为 null 时传入 null 而非调用方法。具体修改是将 `iType.asVariantType()` 改为 `iType != null ? iType.asVariantType() : null`。同时添加了测试用例来验证 Variant 类型的列裁剪行为。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeWithSchemaVisitor.java` (+1/-1 lines)

**修改目的**：修复当 Iceberg Schema 类型为 null 时的 NPE。

**工作逻辑**：在处理 Variant 逻辑类型的分支中，原始代码为 `return visitVariant(iType.asVariantType(), group, visitor);`，修改为 `return visitVariant(iType != null ? iType.asVariantType() : null, group, visitor);`。这样当 `iType` 为 null 时（即 Iceberg Schema 中没有该列的定义，通常出现在列裁剪场景），不会抛出 NPE，而是将 null 传递给 `visitVariant` 方法处理。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestPruneColumns.java` (+45/-0 lines)

**修改目的**：添加 Variant 类型列裁剪的测试用例。

**工作逻辑**：
1. **`testVariant` 测试方法**：
   - 构建一个包含 INT32 列和两个 Variant 列的 Parquet 文件 schema
   - 构建一个只投影 INT32 列和第一个 Variant 列的 Iceberg Schema
   - 调用 `ParquetSchemaUtil.pruneColumns` 进行列裁剪
   - 验证裁剪后的 schema 只包含投影的列，第二个 Variant 列被正确裁剪掉

2. **`buildVariantType` 辅助方法**：构建 Parquet 的 Variant 类型，包含 metadata 和 value 两个 BINARY 字段，并使用 `VariantLogicalTypeAnnotation` 标注。

测试覆盖了以下场景：当 Parquet 文件包含 Variant 列，但 Iceberg Schema 只投影了部分 Variant 列时，列裁剪操作不会抛出 NPE，且能正确保留投影的列。

## 总结

此提交修复了 `TypeWithSchemaVisitor` 中处理 Variant 逻辑类型时的 NPE 问题。当 Iceberg Schema 中没有对应的 Variant 类型定义（如列裁剪场景）时，不再抛出异常，而是传入 null。这是一个重要的 bug 修复，确保了 Variant 类型在列裁剪等场景下的正确处理。测试用例验证了 Variant 列的裁剪行为。
