# 提交 2685：Parquet: Expose variantShreddingFunc() in Parquet.DataWriteBuilder (#14153)

## 提交信息

- **序号**：2685 / 4088
- **哈希**：df01d88c9360cda398c40493eb4135181a8976c5
- **短哈希**：df01d88c9
- **日期**：2025-09-25 13:44:45 -0600
- **作者**：Denys Kuzmenko
- **提交说明**：Parquet: Expose variantShreddingFunc() in Parquet.DataWriteBuilder (#14153)
- **PR/Issue**：#14153

## 总体目的

本提交在 Parquet 的高层写入 API `Parquet.DataWriteBuilder` 中暴露 `variantShreddingFunc()` 方法，使通过 `Parquet.writeData()` 写数据文件时也能配置 Variant 类型的 shredding（分片/拆解）功能。

背景：Variant 是 Iceberg 支持的半结构化数据类型（类似 JSON 的灵活数据类型）。Variant shredding 是一种优化技术，将 Variant 值中可静态推断类型的字段"拆解"（shred）到 Parquet 的独立 typed 列中（如 `typed_value`），而非将整个 Variant 存为单一二进制 blob。这可以显著提升查询性能，因为查询引擎可以直接读取 typed 列而无需解析整个 Variant。

此前，`VariantShreddingFunction` 已在底层的 `WriteBuilder`（appender builder）中实现，可以通过 `Parquet.write()` 的 `createWriterFunc` 路径间接使用。但高层的 `DataWriteBuilder`（`Parquet.writeData()` 的返回类型）缺少对应的配置入口，导致使用 `Parquet.writeData()` API 的用户（如 Iceberg 的数据写入器）无法启用 variant shredding。本提交补齐这一缺口。

## 如何达成设计目的

在 `Parquet.DataWriteBuilder` 中新增 `variantShreddingFunc(VariantShreddingFunction)` 方法，委托给内部的 `appenderBuilder.variantShreddingFunc(func)`，将 shredding 函数传递到底层的 WriteBuilder。同时增强测试以验证该功能端到端工作。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (+5/-0 lines)

**修改目的**：在 DataWriteBuilder 中暴露 variant shredding 配置入口。

**工作逻辑**：新增 `public DataWriteBuilder variantShreddingFunc(VariantShreddingFunction func)` 方法，方法体为 `appenderBuilder.variantShreddingFunc(func); return this;`，将 shredding 函数委托给底层的 `WriteBuilder`（该 builder 已在 schema 转换时使用此函数生成带 `typed_value` 字段的 Parquet schema）。返回 `this` 支持链式调用。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetDataWriter.java` (+81/-8 lines)

**修改目的**：验证通过 DataWriteBuilder 配置 variant shredding 的正确性。

**工作逻辑**：
- 将原 `testDataWriter()` 重构为委托给私有方法 `testDataWriter(Schema schema, VariantShreddingFunction variantShreddingFunc)`。原测试以 `(id, name) -> null`（不 shredding）调用，保持原有行为。
- 私有方法在构建 `DataWriter` 时调用 `.variantShreddingFunc(variantShreddingFunc)`，并将 try-finally 改为 try-with-resources。
- 写入后的验证逻辑增强：逐条用 `InternalTestHelpers.assertEquals()` 比较记录（而非整体 equals，因为 Variant 比较更复杂）；当 schema 包含 Variant 字段且提供了 shredding 函数时，用 `ParquetFileReader` 打开文件检查 Parquet 物理 schema，断言 Variant 字段的 GroupType 包含 `typed_value` 子字段，证明 shredding 生效。
- 新增 `testDataWriterWithVariantShredding()` 测试：构造包含 Variant 字段（field ID 4）的 schema，使用 `VariantTestUtil` 创建包含字段 "a"（整数）和 "b"（字符串）的 Variant 元数据与值，写入两条记录，调用 `testDataWriter` 时传入 shredding 函数 `(id, name) -> ParquetVariantUtil.toParquetSchema(variant.value())`。该函数根据 Variant 值生成对应的 Parquet schema，使 typed 字段被 shred 到物理列中。

## 总结

本提交在 Parquet 的高层数据写入 API `DataWriteBuilder` 中暴露了 `variantShreddingFunc()` 方法，使通过 `Parquet.writeData()` 写文件时能启用 Variant shredding 优化。改动简洁（核心仅 5 行委托代码），但补齐了 API 层面的功能缺口，配合完善的端到端测试验证了 shredding 后 Parquet 物理 schema 中确实包含 `typed_value` 字段。这对于 Iceberg Variant 类型在数据写入路径上的性能优化具有重要意义。
