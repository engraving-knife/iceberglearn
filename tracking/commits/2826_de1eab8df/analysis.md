# 提交 2826：Test: Verify variant logical type annotation of Parquet writer (#14306)

## 提交信息

- **序号**：2826 / 4088
- **哈希**：de1eab8df77c493275c5f66846a1d5b7317184e3
- **短哈希**：de1eab8df
- **日期**：2025-11-03 17:43:05 -0800
- **作者**：Yuya Ebihara
- **提交说明**：Test: Verify variant logical type annotation of Parquet writer (#14306)
- **PR/Issue**：#14306

## 总体目的

本提交为 Iceberg 的 Parquet variant 类型写入器新增测试，验证写入的 Parquet 文件中 variant 列的正确逻辑类型注解（logical type annotation）。

Variant 类型是 Iceberg 支持的一种半结构化数据类型，用于存储灵活的 JSON-like 数据。在 Parquet 文件格式中，variant 列需要被标注为特定的逻辑类型注解（`LogicalTypeAnnotation.variantType`），以便读取方能正确识别该列的 variant 语义。这个逻辑类型注解是 Iceberg variant 在 Parquet 中的元数据约定的一部分（使用 `Variant.VARIANT_SPEC_VERSION` 指定的规范版本）。

此前可能缺少对写入器是否正确写入该逻辑类型注解的验证，本提交补充了这个测试覆盖，确保 variant 列在 Parquet 文件中被正确标注。

## 如何达成设计目的

在现有的 `TestVariantWriters` 测试类中，于参数化测试的验证逻辑中新增一段代码：写入 Parquet 文件后，使用 Parquet 的 `ParquetFileReader` 读取文件元数据，遍历 schema 中的所有列，对于 variant 类型的列，验证其逻辑类型注解等于 `LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION)`。

## 修改详情

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantWriters.java` (+15/-0 lines)

**修改目的**：验证 Parquet 写入的 variant 列逻辑类型注解正确性。

**工作逻辑**：在测试写入 variant 数据并验证读取结果之后，新增一段验证逻辑：
1. 使用 `ParquetFileReader.open()` 打开刚写入的 Parquet 文件
2. 获取文件的 `MessageType`（Parquet schema）
3. 遍历 Iceberg `SCHEMA` 中的所有列
4. 对于类型为 `Types.VariantType.get()` 的列：
   - 通过 `schema.getFieldIndex(column.name())` 获取该列在 Parquet schema 中的字段索引
   - 断言该字段的 `getLogicalTypeAnnotation()` 等于 `LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION)`

新增了三个导入：`ParquetFileReader`、`LogicalTypeAnnotation`、`MessageType`（均来自 org.apache.parquet 包）。该验证嵌入在 try-with-resources 块中确保 reader 被正确关闭。

## 总结

本提交为 Parquet variant 写入器补充了逻辑类型注解的验证测试。通过读取写入的 Parquet 文件元数据，确认 variant 列被正确标注为 `LogicalTypeAnnotation.variantType(Variant.VARIANT_SPEC_VERSION)`。这确保了 variant 类型在 Parquet 中的元数据正确性，对于 variant 数据的互操作性和正确读取至关重要。
