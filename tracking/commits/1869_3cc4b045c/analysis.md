# 提交 1869：Avro: Add variant readers and writers (#12457)

## 提交信息

- **序号**：1869 / 4088
- **哈希**：3cc4b045c6df776a9167ceb31338e9170d02df4e
- **短哈希**：3cc4b045c
- **日期**：2025-03-17 16:33:01 -0700
- **作者**：Ryan Blue
- **提交说明**：Avro: Add variant readers and writers (#12457)
- **PR/Issue**：#12457

## 总体目的

本提交为 Iceberg 的 Avro 读写模块添加对 Variant（变体）类型的支持。Variant 是一种半结构化数据类型（类似 JSON），由两部分二进制数据组成：metadata（字典化的字段名元数据）和 value（实际值）。这是 Iceberg 为支持 Variant 类型在各文件格式（Parquet、ORC、Avro）上的完整读写能力的一部分。

在此提交之前，Avro 模块尚未实现 Variant 的读写。当 Avro schema 中出现带有 `VariantLogicalType` 的 record 时，访问器（visitor）会将其作为普通 record 处理，导致读写无法正确解析 metadata/value 的二进制结构。同时，对于像 Kafka 这样使用 Avro 作为序列化格式的场景，需要 Variant 类型能正确往返（round-trip）。

本提交通过扩展 `AvroWithPartnerVisitor`、`ValueReaders`、`ValueWriters`，以及为各 reader/writer 实现（GenericAvroReader、InternalReader、DataWriter、PlannedDataReader、BaseWriteBuilder）添加 `variant` 方法重写，使整个 Avro 读写链路都能识别并正确处理 Variant 类型。同时新增 `VariantConversion` 类，将 Avro 的 GenericRecord 与 `Variant` 对象相互转换，并注册到默认 Avro 数据模型中。

## 如何达成设计目的

整体设计思路：

1. **Visitor 扩展**：在 `AvroWithPartnerVisitor` 中新增 `variant` 访问方法和 `visitVariant` 路由逻辑。当 schema 的 logical type 是 `VariantLogicalType` 时，将 record 视为 variant，分别访问其 `metadata` 和 `value` 字段，并传入 `visitor.variant(partner, metadataResult, valueResult)`。

2. **读写器实现**：在 `ValueReaders` 中新增 `VariantReader`（读取 metadata 和 value 两个 ByteBuffer 并组装为 `Variant`）；在 `ValueWriters` 中新增 `VariantBinaryWriter` 抽象基类（复用 ByteBuffer 缓冲区）以及 `VariantMetadataWriter`、`VariantValueWriter`、`VariantWriter`，将 Variant 的 metadata 和 value 序列化为小端字节序的 ByteBuffer 写出。

3. **集成到各 reader/writer**：在 `GenericAvroReader`、`InternalReader`、`PlannedDataReader`、`DataWriter`、`BaseWriteBuilder` 中重写 `variant` 方法，统一返回 `ValueReaders.variants()` 或 `ValueWriters.variants()`。

4. **Avro LogicalType 转换**：新增 `VariantConversion` 类继承 Avro 的 `Conversion<Variant>`，实现 `fromRecord`/`toRecord`，使 Avro GenericData 能将 variant record 与 `Variant` 对象互转，并注册到 `Avro` 的 DEFAULT_MODEL。

5. **测试支持**：新增 `RandomVariants` 测试工具类生成随机 Variant 数据，并在 `RandomInternalData`、`RandomAvroData`、`RandomGenericData` 中接入，以覆盖 Variant 的随机往返测试。

## 修改详情

### `core/src/main/java/org/apache/iceberg/avro/AvroWithPartnerVisitor.java` (修改, +31/-1 lines)

**修改目的**：扩展 Avro 的 partner visitor，使其能识别并访问 Variant 类型。

**工作逻辑**：
- 新增 `variant(P partner, R metadataResult, R valueResult)` 方法，默认抛出 `UnsupportedOperationException`，子类按需重写。
- 在 `visit` 路由方法中，当 schema 类型为 `RECORD` 时，先检查其 logical type 是否为 `VariantLogicalType`，是则调用 `visitVariant`，否则按原逻辑访问 record。
- `visitVariant` 方法使用 `recordLevels` 栈防止递归 record 重复访问，分别访问 `metadata` 和 `value` 子字段 schema，最终回调 `visitor.variant(...)`。

### `core/src/main/java/org/apache/iceberg/avro/ValueReaders.java` (修改, +35/-0 lines)

**修改目的**：提供 Variant 类型的读取器。

**工作逻辑**：
- 新增 `variants()` 工厂方法返回单例 `VariantReader`。
- `VariantReader` 内部使用 `ByteBufferReader.INSTANCE` 分别读取 metadata 和 value 两个 ByteBuffer，转为小端字节序后通过 `VariantMetadata.from` 和 `VariantValue.from` 构造对象，最终返回 `Variant.of(metadata, value)`。
- `skip` 方法依次跳过 metadata 和 value。

### `core/src/main/java/org/apache/iceberg/avro/ValueWriters.java` (修改, +85/-0 lines)

**修改目的**：提供 Variant 类型的写入器。

**工作逻辑**：
- 新增抽象类 `VariantBinaryWriter<T>`，维护一个复用的小端 ByteBuffer（初始 2048 字节）。若值已是 `Serialized` 则直接写其 buffer；否则调用子类的 `sizeInBytes`/`writeTo` 序列化。`ensureCapacity` 使用 `IOUtil.capacityFor` 扩容。
- `VariantMetadataWriter` 和 `VariantValueWriter` 分别处理 `VariantMetadata` 和 `VariantValue`，委托给对象自身的 `writeTo`。
- `VariantWriter` 组合上述两者，依次写出 metadata 和 value。
- 新增 `variants()` 工厂方法返回单例 `VariantWriter`。

### `core/src/main/java/org/apache/iceberg/avro/VariantConversion.java` (新增, +68/-0 lines)

**修改目的**：实现 Avro LogicalType Conversion，使 GenericData 支持 Variant 与 Avro record 互转。

**工作逻辑**：
- 继承 `Conversion<Variant>`，logical type 名称为 `VariantLogicalType.NAME`。
- `fromRecord`：从 record 取出 metadata 和 value 两个 ByteBuffer，构造 `Variant`。
- `toRecord`：将 variant 的 metadata 和 value 各自分配小端 ByteBuffer 并 `writeTo`，填入 GenericRecord。

### `core/src/main/java/org/apache/iceberg/avro/Avro.java` (修改, +1/-0 lines)

**修改目的**：将 `VariantConversion` 注册到默认 Avro 数据模型。

**工作逻辑**：在静态初始化块中调用 `DEFAULT_MODEL.addLogicalTypeConversion(new VariantConversion())`。

### `core/src/main/java/org/apache/iceberg/avro/BaseWriteBuilder.java` (修改, +6/-0 lines)

**修改目的**：使 Avro 写构建器在遇到 variant schema 时返回 `ValueWriters.variants()`。

### `core/src/main/java/org/apache/iceberg/avro/GenericAvroReader.java` (修改, +6/-0 lines)

**修改目的**：GenericAvro reader 重写 variant 方法返回 `ValueReaders.variants()`。

### `core/src/main/java/org/apache/iceberg/avro/InternalReader.java` (修改, +6/-0 lines)

**修改目的**：Internal reader 重写 variant 方法返回 `ValueReaders.variants()`。

### `core/src/main/java/org/apache/iceberg/data/avro/DataWriter.java` (修改, +6/-0 lines)

**修改目的**：Data writer 重写 variant 方法返回 `ValueWriters.variants()`。

### `core/src/main/java/org/apache/iceberg/data/avro/PlannedDataReader.java` (修改, +6/-0 lines)

**修改目的**：PlannedData reader 重写 variant 方法返回 `ValueReaders.variants()`。

### `core/src/main/java/org/apache/iceberg/io/IOUtil.java` (修改, +15/-0 lines)

**修改目的**：新增 `capacityFor(int size)` 工具方法，供 VariantBinaryWriter 计算扩容容量。

**工作逻辑**：返回大于等于 size 的下一个 2 的幂的两倍容量（上限接近 Integer.MAX_VALUE），用于复用缓冲区扩容。

### `core/src/main/java/org/apache/iceberg/variants/Serialized.java` (修改, +1/-1 lines)

**修改目的**：小幅调整（使 `buffer()` 方法可被 VariantBinaryWriter 使用，可见性/签名微调）。

### `core/src/main/java/org/apache/iceberg/variants/VariantMetadata.java` (修改, +4/-0 lines)

**修改目的**：补充 VariantMetadata 相关方法（如构造/工厂）以支持 Avro 读写。

### `core/src/test/java/org/apache/iceberg/RandomVariants.java` (新增, +127/-0 lines)

**修改目的**：测试工具类，生成随机 Variant 数据用于往返测试。

**工作逻辑**：`randomVariant` 随机选择 `PhysicalType`，对 OBJECT/ARRAY 类型生成随机字段名 metadata（5-30 个），其余用空 metadata。`randomVariant` 重载方法按 PhysicalType 分支生成对应类型的 `VariantValue`（含 null、boolean、各整数、double、decimal、date、timestamp、float、binary、string、object）。ARRAY 暂时按 object 处理。

### `core/src/test/java/org/apache/iceberg/RandomInternalData.java` (修改, +5/-0 lines)

**修改目的**：在随机内部数据生成器中重写 `variant` 方法，委托 `RandomVariants.randomVariant`。

### 其他测试辅助文件 (修改)

- `core/src/test/java/org/apache/iceberg/avro/AvroTestHelpers.java` (+10)
- `core/src/test/java/org/apache/iceberg/avro/RandomAvroData.java` (+6)
- `core/src/test/java/org/apache/iceberg/avro/TestGenericAvro.java` (+5)
- `core/src/test/java/org/apache/iceberg/avro/TestInternalAvro.java` (+5)
- `core/src/test/java/org/apache/iceberg/data/DataTestHelpers.java` (+8)
- `core/src/test/java/org/apache/iceberg/data/RandomGenericData.java` (+6)
- `core/src/test/java/org/apache/iceberg/data/avro/TestGenericData.java` (+5)

**修改目的**：在测试辅助类中接入 Variant 生成与断言逻辑，使现有的 Avro 往返测试自动覆盖 Variant 类型。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantWriters.java` (修改, +12/-0 lines)

**修改目的**：小幅调整 Parquet variant writer 以配合本提交（如调整方法签名或可见性），保持与核心 variant API 一致。

## 总结

本提交为 Iceberg Avro 模块完整接入 Variant 类型支持：扩展 visitor 路由、新增 reader/writer、实现 Avro LogicalType Conversion、集成到所有内置 reader/writer 实现，并补充随机测试数据生成器。Variant 以 metadata+value 两个小端 ByteBuffer 存储，写入器复用缓冲区以提升性能。这是 Iceberg 多文件格式 Variant 支持系列工作的一部分。
