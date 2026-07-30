# 提交 1748：Parquet: Implement Variant readers (#12139)

## 提交信息

- **序号**：1748 / 4088
- **哈希**：3c8f369637abfa6674aa4b1f8419716a38b46361
- **短哈希**：3c8f36963
- **日期**：2025-02-18 15:31:48 -0800
- **作者**：Ryan Blue
- **提交说明**：Parquet: Implement Variant readers (#12139)
- **PR/Issue**：#12139

## 总体目的

Iceberg 正在引入 Variant 类型（变体类型）来存储半结构化数据。Variant 类型在 Iceberg 规范中定义为一种特殊的类型，其底层由两部分组成：metadata（元数据，包含字段名字典）和 value（值，使用 Variant 二进制编码格式）。

在 Parquet 文件格式中，Variant 可以以两种方式存储：
1. **序列化存储（Serialized）**：metadata 和 value 分别存储为两个 binary 列，value 以 Variant 二进制格式编码。
2. **分片存储（Shredded）**：除了序列化的 value 列外，还将部分或全部值"分片"到 typed_value 列中，以 Parquet 原生类型存储（如 INT32、UTF8 字符串等），以提高查询性能。分片可以递归应用于对象字段和数组元素。

此前，Iceberg 的 Parquet 读取器不支持读取 Variant 列。本提交的目标是实现完整的 Parquet Variant 读取器，支持序列化和分片两种存储格式，包括对象和数组的递归分片读取。

## 如何达成设计目的

提交通过以下架构层次实现 Variant 读取器：

1. **ParquetVariantVisitor**：定义遍历 Parquet Variant schema 结构的访问者模式。Variant 在 Parquet 中的 schema 结构为：variant group 包含 metadata（required binary）、value（optional binary）和 typed_value（optional，可为原始类型或嵌套 group）。访问者提供 `variant`、`metadata`、`serialized`、`primitive`、`value`、`object`、`array` 等回调方法。

2. **VariantReaderBuilder**：继承 `ParquetVariantVisitor`，根据 Parquet schema 结构构建对应的读取器。为 metadata 创建 `VariantMetadataReader`，为序列化 value 创建 `SerializedVariantReader`，为分片原始值创建 `asVariant` 包装读取器，处理各种逻辑类型注解（String、Date、Decimal、Timestamp、Int）。

3. **ParquetVariantReaders**：包含所有 Variant 读取器实现类，包括读取 metadata、序列化 value、分片 value、分片对象、原始值转 Variant 等。

4. **TypeWithSchemaVisitor 扩展**：在现有的 Iceberg 类型与 Parquet schema 联合访问者中新增 Variant 类型支持。当检测到 Iceberg 类型为 VariantType 时，委托给 `ParquetVariantVisitor` 进行细粒度的 schema 遍历。

5. **其他基础设施**：在 `ParquetSchemaUtil` 中新增 `hasField`/`fieldType` 工具方法，在 `PruneColumns` 中添加 Variant 列裁剪支持，在 `TypeToMessageType` 中添加 Variant 类型到 Parquet group 的转换，在 `BaseParquetReaders` 中集成 Variant 读取器构建。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantReaders.java`（新增, +424 lines）

**修改目的**：实现所有 Variant 读取器类。

**工作逻辑**：
- `VariantValueReader` 接口：扩展 `ParquetValueReader<VariantValue>`，新增 `read(VariantMetadata metadata)` 方法，因为 Variant 值的读取需要 metadata 来解码字段名。
- `VariantReader`：组合 metadata 读取器和 value 读取器，先读 metadata 再读 value，组装成 `Variant` 对象。
- `VariantMetadataReader`：从 binary 列读取 metadata 字节，通过 `Variants.metadata()` 解析。
- `SerializedVariantReader`：从 binary 列读取序列化 value 字节，通过 `Variants.value()` 解析。
- `ShreddedVariantReader`：处理 value 和 typed_value 列对。根据 definition level 判断值存在于哪一列：如果 typed_value 存在则使用 typedReader，否则使用 valueReader。
- `ShreddedObjectReader`：处理分片对象，结合 value 列和各字段的 typed_value 读取器，递归构建 `ShreddedObject`。
- `ValueAsVariantReader`：将原始类型读取器（如 INT32）的输出包装为 `VariantValue`，通过 `Variants.of()` 转换。
- `readBinary` 工具方法：读取 binary 列数据为 Little-Endian ByteBuffer。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantVisitor.java`（新增, +285 lines）

**修改目的**：定义遍历 Parquet Variant schema 的访问者模式。

**工作逻辑**：
- 定义常量 `METADATA`、`VALUE`、`TYPED_VALUE` 对应 Parquet schema 中的列名。
- 提供回调方法：`variant`（处理根 variant group）、`metadata`（处理 metadata 列）、`serialized`（处理序列化 value 列）、`primitive`（处理分片原始类型列）、`value`（处理 value+typed_value 对）、`object`（处理分片对象）、`array`（处理分片数组）。
- 静态 `visit` 方法：根据 Parquet group 结构递归遍历，自动识别 metadata、value、typed_value 列并调用对应的回调方法。支持递归处理对象字段和数组元素。

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantReaderBuilder.java`（新增, +260 lines）

**修改目的**：基于 ParquetVariantVisitor 构建 Variant 读取器。

**工作逻辑**：
- 维护字段名路径栈（`fieldNames`），用于构建 `ColumnDescriptor`。
- `variant` 方法：组合 metadata 和 value 读取器为 `VariantReader`。
- `metadata` 方法：创建 `VariantMetadataReader`。
- `serialized` 方法：创建 `SerializedVariantReader`。
- `primitive` 方法：根据 Parquet 原始类型和逻辑类型注解创建对应的读取器。处理 BINARY、BOOLEAN、INT32、INT64、FLOAT、DOUBLE 等原始类型，以及 String、Date、Decimal、Timestamp、Int 等逻辑类型。
- `value` 方法：计算 value 和 typed_value 的 definition level，创建 `ShreddedVariantReader`。
- `object` 方法：创建 `ShreddedObjectReader`，传入字段名列表和各字段读取器。
- 内部类 `LogicalTypeToVariantReader`：将 Parquet 逻辑类型注解映射到 Variant 读取器。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeWithSchemaVisitor.java`（修改, +108/-98 lines）

**修改目的**：在 Iceberg 类型与 Parquet schema 联合访问者中添加 Variant 支持，并重构 List/Map 处理。

**工作逻辑**：
- 将原来内联在 `visit` 方法中的 List 和 Map 处理逻辑提取为独立的 `visitList` 和 `visitMap` 方法。
- 从使用已废弃的 `OriginalType` 迁移到 `LogicalTypeAnnotation`（使用 `instanceof` 检查 `ListLogicalTypeAnnotation` 和 `MapLogicalTypeAnnotation`）。
- 新增 `visitVariant` 方法：当 Iceberg 类型为 VariantType 时，获取访问者的 `variantVisitor()`，通过 `ParquetVariantVisitor.visit` 遍历 Parquet group 结构并构建读取器，然后调用 `visitor.variant()` 返回结果。
- 新增 `variant(Types.VariantType, T)` 和 `variantVisitor()` 方法到基类。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetSchemaUtil.java`（修改, +27 lines）

**修改目的**：新增 Parquet group 字段查询工具方法。

**工作逻辑**：
- `hasField(GroupType group, String name)`：检查 group 是否包含指定名称的字段。
- `fieldType(GroupType group, String name)`：返回指定名称字段的 Type，不存在时返回 null（捕获 `InvalidRecordException`）。

### `parquet/src/main/java/org/apache/iceberg/parquet/PruneColumns.java`（修改, +8/-2 lines）

**修改目的**：在列裁剪中支持 Variant 类型。

**工作逻辑**：
- 新增 `variant(VariantType expected, Type variant)` 方法，直接返回 Parquet variant 类型（不裁剪 variant 内部列）。
- 修改 `isStruct` 方法签名为 `isStruct(Type field, NestedField expected)`，增加对 Variant 类型的检查——Variant 类型不被视为 struct，避免错误地将 variant group 当作 struct 处理。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java`（修改, +14 lines）

**修改目的**：将 Iceberg Variant 类型转换为 Parquet group schema。

**工作逻辑**：
- 在 `convertField` 中添加 `isVariantType()` 检查，调用 `variant` 方法。
- 新增 `variant(Repetition, id, name)` 方法：构建包含 `metadata`（required binary）和 `value`（required binary）两个子字段的 Parquet group。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java`（修改, +13 lines）

**修改目的**：在基础 Parquet 读取器中集成 Variant 读取器。

**工作逻辑**：
- 覆盖 `variant(Types.VariantType, ParquetValueReader)` 方法，直接返回传入的 reader。
- 覆盖 `variantVisitor()` 方法，返回新的 `VariantReaderBuilder` 实例，传入 Parquet schema 和当前字段路径。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueReaders.java`（修改, +30 lines）

**修改目的**：新增 INT32 到 Byte/Short 的读取器，用于 Variant 分片值。

**工作逻辑**：
- 新增 `intsAsByte(ColumnDescriptor)` 和 `intsAsShort(ColumnDescriptor)` 工厂方法。
- `IntAsByteReader`：读取 INT32 并转换为 byte。
- `IntAsShortReader`：读取 INT32 并转换为 short。

### `core/src/main/java/org/apache/iceberg/variants/Variants.java`（修改, +18/-18 lines）

**修改目的**：将 Variant 工厂方法从包级可见改为 public，供 Parquet 读取器使用。

**工作逻辑**：
- 新增 `emptyMetadata()` 方法，返回空的 V1 metadata。
- 将所有 `of(...)` 工厂方法（boolean、byte、short、int、long、float、double、date、timestamp、decimal、binary、string 等）从 `static`（包级）改为 `public static`。

### `core/src/test/java/org/apache/iceberg/variants/VariantTestUtil.java`（修改, +45/-5 lines）

**修改目的**：新增 Variant 相等性断言方法，供测试使用。

**工作逻辑**：
- 新增 `assertEqual(VariantMetadata expected, VariantMetadata actual)`：比较字典大小和各字典条目。
- 新增 `assertEqual(VariantValue expected, VariantValue actual)`：递归比较 Variant 值（对象、数组、原始值）。
- 将 `createMetadata`、`createObject`、`emptyMetadata` 等方法改为 public。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestVariantReaders.java`（新增, +1107 lines）

**修改目的**：为 Variant 读取器提供全面的测试覆盖。

**工作逻辑**：测试覆盖了序列化 Variant 读取、分片原始值读取、分片对象读取、分片数组读取等多种场景，验证读取结果的正确性。

## 小结

- **成效**：成功实现了完整的 Parquet Variant 读取器，支持序列化存储和分片存储（包括对象和数组的递归分片）两种格式。新增了 3 个核心类（ParquetVariantReaders、ParquetVariantVisitor、VariantReaderBuilder）和 1 个测试类（TestVariantReaders），修改了 8 个现有类以集成 Variant 支持。这是 Iceberg Variant 类型支持的重大里程碑。
- **影响范围**：涉及 parquet 和 core 两个模块。核心影响在于 Parquet 读取路径——所有通过 `BaseParquetReaders` 读取 Parquet 文件的代码路径现在都能处理 Variant 列。同时 `TypeWithSchemaVisitor` 的重构（List/Map 提取为独立方法、迁移到 `LogicalTypeAnnotation`）可能影响所有 Parquet 读取器。
- **回迁到 1.4.x 的注意事项**：此提交是 Variant 类型支持的核心实现，依赖提交 1745（Add variant type support to utils and visitors）中的 `VariantType` 类型和 `isVariantType()`/`asVariantType()` 方法。回迁需要确认 1.4.x 分支已有 `Variants` 类和 variant 编解码基础设施。`TypeWithSchemaVisitor` 的重构（从 `OriginalType` 迁移到 `LogicalTypeAnnotation`）需要确认 1.4.x 的 Parquet 依赖版本支持 `LogicalTypeAnnotation`。建议与 1743、1745、1746 一起作为 Variant 类型支持的一组回迁。此提交较大且复杂，回迁时需仔细测试。
