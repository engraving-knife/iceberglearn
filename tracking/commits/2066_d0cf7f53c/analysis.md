# 提交 2066：Spark: Update Spark Parquet vectorized read tests to uses Iceberg Record instead of Avro GenericRecord (#12925)

## 提交信息

- **序号**：2066 / 4088
- **哈希**：d0cf7f53c675c4296e9818da08e3a7270d6c8c77
- **短哈希**：d0cf7f53c
- **日期**：2025-04-30 11:09:03 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：Spark: Update Spark Parquet vectorized read tests to uses Iceberg Record instead of Avro GenericRecord (#12925)
- **PR/Issue**：#12925

## 总体目的

Iceberg Spark Parquet 向量化读取测试此前使用 Avro 的 `GenericData.Record` 作为期望数据模型与写入对象。这与 Iceberg 自身的数据模型不一致——Iceberg 提供 `org.apache.iceberg.data.Record` 接口与 `GenericParquetWriter`，应当作为测试的首选数据模型，避免测试代码对 Avro 的不必要耦合，并使测试更贴近 Iceberg 在生产中的真实使用方式（Iceberg 自己的写入路径用 `Record` 而非 Avro `GenericRecord`）。

本提交把 Spark 3.4/3.5 的 Parquet 向量化读取测试从 Avro `GenericData.Record` 迁移到 Iceberg `Record`：测试数据用 `RandomGenericData.generate` 生成、用 `GenericParquetWriter::create` 写入、读回后用新增的 `GenericsHelpers.assertEqualsBatch` 与 `Record` 比较。同时为 `RandomGenericData` / `RandomData` 增加 `nullPercentage` 参数，使测试可以控制 null 比例（原先固定 5%），增强测试覆盖。

## 如何达成设计目的

1. **数据生成**：测试期望数据从 `RandomData.generate(schema, numRecords, seed, nullPercentage)`（生成 Avro `GenericData.Record`）改为 `RandomGenericData.generate(schema, numRecords, seed)`（生成 Iceberg `Record`）。为保留 null 比例控制，给 `RandomGenericData` 增加 `nullPercentage` 重载。
2. **写入器**：Parquet 写入器从 `Parquet.write(...).schema(schema).named("test").build()`（默认写 Avro）改为 `.createWriterFunc(GenericParquetWriter::create)` 指定 Iceberg `Record` 写入器。
3. **断言**：新增 `GenericsHelpers.assertEqualsBatch(struct, expectedRecords, batch)` 工具，按行从 `ColumnarBatch` 取 `InternalRow`、从期望 `Record` 迭代器取记录，调用 `assertEqualsUnsafe` 比较。同时扩展 `assertEqualsUnsafe` 处理 `LONG`/`DOUBLE` 类型在 Iceberg Record 与 Spark InternalRow 间的窄类型差异（如 Integer→Long、Float→Double 的转换）。
4. **null 比例可配**：`RandomDataGenerator` 与 `SparkRandomDataGenerator` 引入 `nullPercentage` 字段与 `isNull()` 方法（基于 `random.nextFloat() < nullPercentage`），替代原先 `random.nextInt(20) == 1` 的固定 5% 写法。

## 修改详情

### `data/src/test/java/org/apache/iceberg/data/RandomGenericData.java` (修改, +35/-6 lines)

**修改目的**：支持 null 比例参数与 dictionary-encodable 记录生成。

**工作逻辑**：
- 新增 `generateDictionaryEncodableRecords(schema, numRecords, seed, nullPercentage)`，使用带 nullPercentage 的 `DictionaryEncodedGenerator`。
- `RandomDataGenerator<T>` 抽象类增加 `nullPercentage` 字段、带 nullPercentage 的构造器（校验 0.0~1.0）与 `isNull()` 方法，`field`/`list`/`map` 中固定 5% 改为 `isNull()`。
- `RandomRecordGenerator` 与 `DictionaryEncodedGenerator` 增加带 nullPercentage 的构造器。

### `data/src/test/java/org/apache/iceberg/data/TestLocalScan.java` (修改, +9/-3 lines)

**修改目的**：适配 `RandomGenericData` 新增的 nullPercentage API。

**工作逻辑**：
更新测试中对 `RandomGenericData` 的调用以利用新的 nullPercentage 重载。

### `spark/v3.4/spark/src/test/java/org/apache/iceberg/spark/data/RandomData.java` (修改)

**修改目的**：Spark 3.4 的 `SparkRandomDataGenerator` 增加 nullPercentage 支持。

**工作逻辑**：
引入 `nullPercentage` 字段、带 nullPercentage 的构造器、`isNull()` 方法，`field`/`list`/`map` 中固定 5% 改为 `isNull()`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java` (修改, +31/-1 lines)

**修改目的**：新增基于 `Record` 与 `ColumnarBatch` 的批量断言工具，并扩展类型断言。

**工作逻辑**：
- 新增 `assertEqualsBatch(Types.StructType struct, Iterator<Record> expectedRecords, ColumnarBatch batch)`：遍历 batch 行，对每行取 `InternalRow` 与下一条期望 `Record`，调用 `assertEqualsUnsafe` 比较。
- `assertEqualsUnsafe` 的 switch 增加 `LONG` 与 `DOUBLE` 分支：当期望是 `Integer` 而实际是 `Long` 时做 `((Number) expected).longValue()` 比较；当期望是 `Float` 而实际是 `Double` 时用 `Double.doubleToLongBits` 比较，以适配 Iceberg Record 与 Spark 内部行间的窄类型差异。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/RandomData.java` (修改, +16/-5 lines)

**修改目的**：Spark 3.5 的 `SparkRandomDataGenerator` 增加 nullPercentage 支持。

**工作逻辑**：
同 Spark 3.4，引入 `nullPercentage` 字段、构造器、`isNull()` 方法，替换固定 5% 写法。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetDictionaryEncodedVectorizedReads.java` (修改, +16/-14 lines)

**修改目的**：迁移到 Iceberg `Record`。

**工作逻辑**：
import 从 Avro `GenericData` 改为 `RandomGenericData`/`Record`/`GenericParquetWriter`；数据生成、写入器、断言改用 `Record` 与 `GenericsHelpers.assertEqualsBatch`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetDictionaryFallbackToPlainEncodingVectorizedReads.java` (修改)

**修改目的**：迁移到 Iceberg `Record`。

**工作逻辑**：
同上，import、数据生成、写入器、断言改用 `Record`。

### `spark/v3.5/spark/src/test/java/org/apache/iceberg/spark/data/parquet/vectorized/TestParquetVectorizedReads.java` (修改, +34/-27 lines)

**修改目的**：迁移到 Iceberg `Record`，并支持 nullPercentage。

**工作逻辑**：
- `IDENTITY` 函数类型由 `Function<GenericData.Record, GenericData.Record>` 改为 `Function<Record, Record>`。
- `writeAndValidate`/`generateData`/`getParquetWriter`/`getParquetV2Writer`/`assertRecordsMatch` 等方法签名与实现从 `GenericData.Record` 改为 `Record`，写入器调用 `.createWriterFunc(GenericParquetWriter::create)`。
- `generateData` 改用 `RandomGenericData.generate(schema, numRecords, seed)`，并保留 transform 支持。
- 断言改用 `GenericsHelpers.assertEqualsBatch`。

## 总结

本提交把 Spark 3.4/3.5 的 Parquet 向量化读取测试从 Avro `GenericData.Record` 迁移到 Iceberg `Record`：测试数据用 `RandomGenericData` 生成、用 `GenericParquetWriter` 写入、用新增的 `GenericsHelpers.assertEqualsBatch` 比较，并扩展类型断言以处理窄类型差异。同时为随机数据生成器引入可配置的 `nullPercentage` 参数，替代原先固定 5% 的写法，增强测试灵活性。这是测试基础设施与 Iceberg 数据模型对齐的一部分。
