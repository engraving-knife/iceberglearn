# 提交 2428：Spark: Add Variant read support for Spark Iceberg tables (#13219)

## 提交信息

- **序号**：2428 / 4088
- **哈希**：571056929091f1e62412500045f71e5ba6ea00ad
- **短哈希**：571056929
- **日期**：2025-07-28 20:29:23 -0600
- **作者**：Aihua Xu
- **提交说明**：Spark: Add Variant read support for Spark Iceberg tables (#13219)
- **PR/Issue**：#13219

## 总体目的

本提交为 Spark Iceberg 表添加了 Variant 类型的读取支持。Variant 是一种半结构化数据类型，可以存储任意 JSON-like 数据（对象、数组、原始值等），类似于 Spark 的 `VARIANT` 类型。

Iceberg 已经在 Core/API 层定义了 `Types.VariantType`，并在 Parquet 层提供了 Variant 的读写能力。但 Spark 集成层尚未支持 Variant 类型，这意味着包含 Variant 列的 Iceberg 表无法在 Spark 中读取。

本提交打通了从 Iceberg Variant 类型到 Spark `VariantType` 的完整读取链路，包括：
1. 类型转换：Iceberg `VariantType` ↔ Spark `VariantType` 双向转换
2. Parquet 读取：从 Parquet 文件读取 Variant 数据并转换为 Spark 的 `VariantVal`
3. Avro 读取：从 Avro 文件读取 Variant 数据并转换为 Spark 的 `VariantVal`
4. 列裁剪：在 Spark 列裁剪时正确处理 Variant 类型

## 如何达成设计目的

整体设计遵循 Iceberg Spark 集成的现有架构模式，通过 Visitor 模式处理类型遍历：

1. **类型转换层**：在 `SparkTypeVisitor` 中新增 `VariantType` 的访问分支，在 `TypeToSparkType` 和 `SparkTypeToType` 中实现双向转换，在 `PruneColumnsWithoutReordering` 中支持 Variant 类型的列裁剪

2. **Parquet 读取层**：在 `SparkParquetReaders` 中新增 `VariantReader` 内部类，将 Iceberg 的 `Variant` 对象转换为 Spark 的 `VariantVal`（包含 metadata 和 value 两个字节数组，使用小端字节序）

3. **Avro 读取层**：在 `SparkValueReaders` 中新增 `VariantReader`，分别读取 metadata 和 value 字节数组并组装为 `VariantVal`；在 `SparkValueWriters` 中新增对应的 `VariantWriter`

4. **Core/Parquet 层支持**：在 `AvroWithPartnerByStructureVisitor` 中新增 Variant 类型的访问逻辑（检测 Avro 的 `VariantLogicalType`），在 `ParquetAvro` 中注册 `VariantConversion`，将 `ParquetVariantReaders.DelegatingValueReader` 从 private 改为 public 以供 Spark 层使用

5. **测试层**：新增 `TestSparkVariants` 测试类，覆盖各种 Variant 数据类型的读写验证

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTypeVisitor.java` (+8/-0 lines)

**修改目的**：在 Spark 类型访问器中添加 VariantType 的访问支持。

**工作逻辑**：在 `visit` 方法中新增 `type instanceof VariantType` 分支，调用 `visitor.variant((VariantType) type)`。新增 `variant(VariantType)` 方法默认抛出 `UnsupportedOperationException`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTypeToType.java` (+6/-0 lines)

**修改目的**：实现 Spark VariantType 到 Iceberg VariantType 的转换。

**工作逻辑**：重写 `variant(VariantType)` 方法，返回 `Types.VariantType.get()`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/TypeToSparkType.java` (+6/-0 lines)

**修改目的**：实现 Iceberg VariantType 到 Spark VariantType 的转换。

**工作逻辑**：重写 `variant(Types.VariantType)` 方法，返回 `VariantType$.MODULE$`（Spark 的 VariantType 单例对象）。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithoutReordering.java` (+5/-0 lines)

**修改目的**：支持 Variant 类型的列裁剪。

**工作逻辑**：重写 `variant(Types.VariantType)` 方法，直接返回 `Types.VariantType.get()`，表示 Variant 类型在列裁剪时保持不变。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java` (+38/-0 lines)

**修改目的**：实现从 Parquet 读取 Variant 数据并转换为 Spark VariantVal。

**工作逻辑**：
- 新增 `variantVisitor()` 方法返回 `VariantReaderBuilder`
- 新增 `variant()` 方法创建 `VariantReader` 实例
- 新增 `VariantReader` 内部类，继承 `DelegatingValueReader<Variant, VariantVal>`，将 Iceberg `Variant` 对象的 metadata 和 value 分别序列化为小端字节序的字节数组，组装为 Spark 的 `VariantVal`

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkValueReaders.java` (+30/-0 lines)

**修改目的**：实现从 Avro 读取 Variant 数据并转换为 Spark VariantVal。

**工作逻辑**：新增 `variants()` 工厂方法和 `VariantReader` 内部类。该 reader 分别读取 metadata 和 value 两个字节数组（通过 `ValueReaders.bytes()`），然后组装为 `VariantVal(value, metadata)`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkValueWriters.java` (+24/-0 lines)

**修改目的**：实现将 Spark VariantVal 写入 Avro 格式。

**工作逻辑**：新增 `variants()` 工厂方法和 `VariantWriter` 内部类。该 writer 将 `VariantVal` 的 metadata 和 value 字节数组依次写入 Avro encoder。

### `core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerByStructureVisitor.java` (+32/-1 lines)

**修改目的**：在 Avro 结构访问器中支持 Variant 类型的识别和访问。

**工作逻辑**：
- 在 `RECORD` case 中，先检查 schema 是否有 `VariantLogicalType` 或 partner 类型是否为 Variant，如果是则调用 `visitVariant` 而非 `visitRecord`
- 新增 `visitVariant` 方法：访问 Variant 的 metadata 和 value 子字段
- 新增 `isVariantType(P)` 方法（默认返回 false）和 `variant(P, T, T)` 方法（默认抛出异常）

### `core/src/main/java/org/apache/iceberg/avro/AvroWithTypeByStructureVisitor.java` (+5/-0 lines)

**修改目的**：实现 Iceberg Type 的 Variant 类型识别。

**工作逻辑**：重写 `isVariantType(Type)` 方法，返回 `type.isVariantType()`。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetAvro.java` (+8/-0 lines)

**修改目的**：在 Parquet-Avro 转换中注册 Variant 转换器。

**工作逻辑**：新增 `VariantConversion` 实例并注册到 Avro `GenericData`，新增 `variant()` 方法返回原 schema。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantReaders.java` (+2/-2 lines)

**修改目的**：将 `DelegatingValueReader` 从 private 改为 public 以供 Spark 层继承使用。

**工作逻辑**：将 `DelegatingValueReader` 类的访问修饰符从 `private` 改为 `public`，构造函数从 `private` 改为 `protected`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/ParquetWithSparkSchemaVisitor.java` (+13/-0 lines)

**修改目的**：在 Parquet-Spark schema 访问器中支持 Variant 类型。

**工作逻辑**：在结构体字段访问中新增 `sType instanceof VariantType` 分支，调用 `visitor.variant(variant, group)`。新增 `variant()` 方法默认抛出异常。包含 TODO 注释，表示将来 Parquet 原生支持 Variant 类型后应添加逻辑类型注解校验。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/AvroWithSparkSchemaVisitor.java` (+5/-0 lines)

**修改目的**：在 Avro-Spark schema 访问器中支持 Variant 类型。

**工作逻辑**：重写 `isVariantType(DataType)` 方法，返回 `type instanceof VariantType`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+57/-0 lines)

**修改目的**：实现将 Spark VariantVal 写入 Parquet 格式。

**工作逻辑**：新增 Variant writer 相关逻辑，将 `VariantVal` 转换为 Iceberg `Variant` 对象后写入 Parquet。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkAvroWriter.java` (+5/-0 lines)

**修改目的**：在 Spark Avro writer 中支持 Variant 类型。

**工作逻辑**：重写 `variant()` 方法，调用 `SparkValueWriters.variants()` 创建 Variant writer。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkPlannedAvroReader.java` (+5/-0 lines)

**修改目的**：在 Spark Avro reader 中支持 Variant 类型。

**工作逻辑**：重写 `variant()` 方法，调用 `SparkValueReaders.variants()` 创建 Variant reader。

### 测试文件

- **`TestSparkVariants.java`** (新文件，+344 lines)：全面测试 Variant 在 Spark 中的读写，覆盖各种原始类型（null、boolean、byte、short、int、long、float、double、decimal、string、binary、date、timestamp）、对象和数组
- **`AvroDataTestBase.java`** (+95 lines)：添加 Variant 类型到 Avro 数据测试
- **`RandomData.java`** (+23 lines)：支持生成随机 Variant 测试数据
- **`GenericsHelpers.java`** (+43 lines)：支持 Variant 值的比较
- **`TestHelpers.java`** (+7 lines)：添加 Variant 断言辅助方法
- **`TestSparkParquetReader.java`** (+14 lines)：添加 Variant Parquet 读取测试
- **`DataFrameWriteTestBase.java`** (+32 lines)：添加 Variant DataFrame 写入测试
- **`ScanTestBase.java`** (+19 lines)：添加 Variant 扫描测试
- 其他测试文件添加 Variant schema 支持

## 总结

本提交是一个重要的功能添加，为 Spark Iceberg 表提供了完整的 Variant 类型读写支持。Variant 类型是一种半结构化数据类型，适用于存储灵活的 JSON-like 数据。实现覆盖了 Parquet 和 Avro 两种文件格式，以及类型转换、列裁剪、读取和写入的完整链路。该功能仅在 Spark v4.0 中实现，因为 Spark 4.0 才原生支持 `VariantType`。测试覆盖了各种 Variant 数据类型和读写场景。
