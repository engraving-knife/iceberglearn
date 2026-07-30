# 提交 1825：Parquet: Implement Variant writers (#12323)

## 提交信息

- **序号**：1825 / 4088
- **哈希**：58b283e3533afce8b9884ac847a46eb40688353b
- **短哈希**：58b283e35
- **日期**：2025-03-05 13:53:33 -0800
- **作者**：Ryan Blue
- **提交说明**：Parquet: Implement Variant writers (#12323)
- **PR/Issue**：#12323

## 总体目的

该提交为 Iceberg 的 Parquet 模块实现了 Variant 类型的写入器（Writer），包括完整的不透明（opaque）写入和部分 shredding（分片）写入支持。这是 Iceberg 引入 Variant 类型（半结构化数据类型，类似 JSON）系列工作的 Parquet 写入部分。

Variant 类型在 Parquet 中有两种存储方式：
1. **不透明存储**：将整个 Variant 的 metadata 和 value 分别作为两个二进制列存储，不做任何拆解。
2. **Shredding（分片）存储**：将 Variant 对象中已知的、有明确 schema 的字段"剥离"出来存为独立的 Parquet 列，剩余部分仍以不透明方式存储。这可以显著提升查询性能（已知字段可直接列式读取，无需解析 Variant 二进制）。

此前 Parquet 模块已有 Variant 的读取器（ParquetVariantReaders），但缺少写入器，无法将 Variant 数据写入 Parquet 文件。本提交补全了这一能力。

## 如何达成设计目的

整体设计通过以下组件实现：

1. **VariantVisitor**：访问者模式遍历 Variant 树结构（object/array/primitive），为 shredding 写入提供遍历基础。
2. **ParquetVariantWriters**：一组 Writer 类，负责将 Variant 的 metadata、value、shredded 字段写入 Parquet 列。包括 `VariantWriter`（不透明写入）、`ShreddedVariantWriter`（分片写入）、`PrimitiveWriter`（基本类型写入）等。
3. **VariantWriterBuilder**：基于 Parquet schema 构建 Writer 树，使用 `ParquetVariantVisitor` 遍历 Parquet 的 MessageType，为每个 shredding 字段创建对应的 Writer。
4. **VariantShreddingFunction**：接口，定义如何从 Variant 值中提取 shredded 字段。
5. **VariantData**：`Variant` 接口的简单实现，用于测试。
6. 在 `Parquet.java`、`BaseParquetWriter.java`、`ParquetWriter.java`、`TypeToMessageType.java` 中集成 Variant 写入支持。

## 修改详情

### `core/src/main/java/org/apache/iceberg/variants/VariantVisitor.java` (新增, 85 lines)

**修改目的**：提供 Variant 树结构的访问者模式遍历。

**工作逻辑**：泛型 `VariantVisitor<R>` 定义 `object()`、`array()`、`primitive()` 三个回调方法。静态 `visit()` 方法递归遍历 VariantValue：ARRAY 类型遍历所有元素并调用 `array()`；OBJECT 类型遍历所有字段并调用 `object()`（传递字段名列表和字段结果列表）；其他类型调用 `primitive()`。提供 `beforeObjectField`/`afterObjectField`/`beforeArrayElement`/`afterArrayElement` 钩子供子类使用。

### `core/src/main/java/org/apache/iceberg/variants/VariantData.java` (新增, 43 lines)

**修改目的**：提供 `Variant` 接口的简单实现，用于测试。

### `core/src/main/java/org/apache/iceberg/variants/Variant.java` (修改, 12 lines)

增加 `metadata()` 和 `value()` 方法声明，供 Writer 访问。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantWriters.java` (新增, 386 lines)

**修改目的**：Variant 的 Parquet 写入器核心实现。

**工作逻辑**：
- `VariantWriter`：将 Variant 拆为 metadata 和 value 两部分，分别写入两个二进制列。
- `VariantMetadataWriter`/`VariantValueWriter`：将 metadata/value 序列化为 ByteBuffer 写入。
- `ShreddedVariantWriter`：处理 shredding 写入，根据 definition level 决定写入 shredded 值、typed 值还是 fallback 到不透明写入。
- `PrimitiveWriter`：将 Variant 基本类型按指定 PhysicalType 写入 Parquet 列。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantWriterBuilder.java` (新增, 286 lines)

**修改目的**：根据 Parquet schema 构建 Variant Writer 树。

**工作逻辑**：继承 `ParquetVariantVisitor`，遍历 Parquet 的 MessageType。对于 shredded Variant schema（含 `metadata`、`value`、和已剥离字段），为每个字段创建对应的 Writer。使用 `LogicalTypeAnnotationVisitor` 将 Parquet 逻辑类型映射到 Variant PhysicalType。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantShreddingFunction.java` (新增, 37 lines)

**修改目的**：定义 shredding 提取函数接口。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (修改, 24 lines)

集成 Variant 写入：在 `write` 构建器中增加 Variant 类型支持。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java` (修改, 57 lines)

将 Iceberg VariantType 转换为 Parquet 的 MessageType（含 metadata 和 value 两个 binary 列）。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetSchemaUtil.java` (修改, 25 lines)

新增 Variant schema 工具方法。

### `core/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java` (修改, 44 lines)

在通用数据写入器中集成 Variant 写入。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantWriters.java` (新增, 342 lines)

全面测试 Variant 写入器：不透明写入、shredding 写入、各种基本类型。

## 小结

- **成效**：为 Parquet 模块补全了 Variant 类型写入能力，支持不透明存储和 shredding 存储两种模式。Shredding 模式可将已知字段剥离为独立列，提升查询性能。
- **影响范围**：parquet 模块（+1348/-64 行，18 个文件），core 模块的 variants 包。属于新功能，影响所有需要写入 Variant 数据到 Parquet 的场景。
- **回迁到 1.4.x 的注意事项**：1.4.x 分支需已包含 Variant 类型基础设施（`Types.VariantType`、`Variant` 接口等）。该提交改动量大（1348 行新增），建议整体回迁。Shredding 功能依赖 Parquet schema 的特殊结构，需确保 1.4.x 的 `TypeToMessageType` 已支持 VariantType。建议与 1768（API: Move variant to API）等 Variant 系列提交协同回迁。
