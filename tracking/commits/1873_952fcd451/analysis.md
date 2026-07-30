# 提交 1873：Parquet, Core: Enable passing Variant tests (#12559)

## 提交信息

- **序号**：1873 / 4088
- **哈希**：952fcd451f079a82e00e14107377108c3fff9648
- **短哈希**：952fcd451
- **日期**：2025-03-18 13:24:22 -0700
- **作者**：Ryan Blue
- **提交说明**：Parquet, Core: Enable passing Variant tests (#12559)
- **PR/Issue**：#12559

## 总体目的

本提交启用 Parquet 和 Avro 模块中针对 Variant 类型的往返测试，并修复测试基础设施中阻止 Variant 写入器正确接收 Iceberg schema 的问题。

此前，Parquet 的 writer 工厂方法（如 `GenericParquetWriter.buildWriter` 和 `InternalWriter.create`）只接收 Parquet 的 `MessageType`，而不接收 Iceberg 的 `Schema`/`Types.StructType`。Variant 写入器需要从 Iceberg schema 获取 partner type 信息（因为 Parquet 的 schema 无法完整表达 Variant 的 logical type），因此当测试尝试写入 Variant 数据时会失败。

本提交通过为 `GenericParquetWriter` 和 `InternalWriter` 新增接受 Iceberg schema 的工厂方法，使测试能将 Iceberg schema 传递给 writer，从而让 Variant writer 能正确工作。同时在多个测试类中重写 `supportsVariant()` 返回 true，启用 Variant 测试用例。

## 如何达成设计目的

1. **扩展 writer 工厂方法**：
   - `GenericParquetWriter` 新增 `create(Schema, MessageType)` 和 `create(Types.StructType, MessageType)` 重载，委托给 `INSTANCE.createWriter(struct, type)`。
   - `InternalWriter` 新增 `create(Schema, MessageType)` 重载，委托给 `create(schema.asStruct(), type)`；原 `create(MessageType)` 改为显式传 null。

2. **更新测试调用**：
   - `TestParquetEncryptionWithWriteSupport` 将 `GenericParquetWriter::buildWriter` 改为 lambda `fileSchema -> GenericParquetWriter.create(schema, fileSchema)`，传入 Iceberg schema。
   - `TestInternalParquet` 将 `InternalWriter::create` 改为 `fileSchema -> InternalWriter.create(writeSchema, fileSchema)`。

3. **启用 Variant 测试**：在 `TestAvroEncoderUtil`、`TestParquetEncryptionWithWriteSupport`、`TestInternalParquet` 中重写 `supportsVariant()` 返回 true。同时在 `TestParquetEncryptionWithWriteSupport` 中重写 `supportsUnknown()` 和 `supportsTimestampNanos()` 返回 true。

## 修改详情

### `core/src/test/java/org/apache/iceberg/avro/TestAvroEncoderUtil.java` (修改, +5/-0 lines)

**修改目的**：启用 Avro encoder 的 Variant 测试。

**工作逻辑**：重写 `supportsVariant()` 返回 true，使基类 `DataTest` 的 Variant 相关测试用例在本测试中执行。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestParquetEncryptionWithWriteSupport.java` (修改, +16/-1 lines)

**修改目的**：启用加密场景下的 Variant、Unknown、TimestampNanos 测试，并修复 writer 创建方式。

**工作逻辑**：
- 重写 `supportsUnknown()`、`supportsTimestampNanos()`、`supportsVariant()` 均返回 true。
- 将 `createWriterFunc(GenericParquetWriter::buildWriter)` 改为 `createWriterFunc(fileSchema -> GenericParquetWriter.create(schema, fileSchema))`，使 writer 能拿到 Iceberg schema。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetWriter.java` (修改, +10/-0 lines)

**修改目的**：新增接受 Iceberg schema 的工厂方法。

**工作逻辑**：
- 新增 `public static ParquetValueWriter<Record> create(Schema schema, MessageType type)`，调用 `INSTANCE.createWriter(schema.asStruct(), type)`。
- 新增 `public static ParquetValueWriter<Record> create(Types.StructType struct, MessageType type)`，调用 `INSTANCE.createWriter(struct, type)`。
- 导入 `org.apache.iceberg.Schema` 和 `org.apache.iceberg.types.Types`。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalWriter.java` (修改, +7/-1 lines)

**修改目的**：新增接受 Iceberg schema 的工厂方法。

**工作逻辑**：
- 原 `create(MessageType type)` 改为显式调用 `create((Types.StructType) null, type)`（消除 null 歧义）。
- 新增 `create(Schema schema, MessageType type)` 重载，委托 `create(schema.asStruct(), type)`。
- 导入 `org.apache.iceberg.Schema`。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestInternalParquet.java` (修改, +6/-1 lines)

**修改目的**：启用内部 Parquet 的 Variant 测试并修复 writer 创建方式。

**工作逻辑**：
- 重写 `supportsVariant()` 返回 true。
- 将 `createWriterFunc(InternalWriter::create)` 改为 `createWriterFunc(fileSchema -> InternalWriter.create(writeSchema, fileSchema))`。

## 总结

本提交通过为 `GenericParquetWriter` 和 `InternalWriter` 新增接受 Iceberg schema 的工厂方法，解决了 Variant writer 无法获取 partner type 的问题，并在 Avro encoder、Parquet 加密、内部 Parquet 三个测试类中启用 Variant（及部分 Unknown/TimestampNanos）测试。这是 Variant 类型多格式支持系列工作的一部分。
