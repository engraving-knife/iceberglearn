# 提交 2572：Spark 4: Read and write UnknownType (#13445)

## 提交信息

- **序号**：2572 / 4088
- **哈希**：07982104caff495cf5de20f7c0de3cd215a4ff29
- **短哈希**：07982104c
- **日期**：2025-08-28 17:01:20 -0700
- **作者**：Fokko Driesprong
- **提交说明**：Spark 4: Read and write UnknownType (#13445)
- **PR/Issue**：#13445

## 总体目的

此次提交为 Spark 4.0 模块添加对 Iceberg `UnknownType`（未知类型）的读写支持。Iceberg 引入 `UnknownType` 用于表示 schema 演进过程中尚未识别或已被删除的字段类型——当一个表删除了某列后，旧版本读取器在解析旧数据文件时可能遇到无法识别的字段类型，此时用 `UnknownType` 占位，避免直接报错。

Spark 4 引入了与 Spark 3 不同的类型系统（使用 Scala object 类型如 `NullType$.MODULE$`），并且需要将 Iceberg 的 `UnknownType` 与 Spark 的 `NullType` 互相映射。此前 Spark 4 模块在遇到 `UnknownType` 时会抛出 `UnsupportedOperationException`，导致包含未知类型字段的表无法在 Spark 4 中读写。

本次改动使 Spark 4 能够：将 Iceberg `UnknownType` 映射为 Spark `NullType`（反之亦然），在 Parquet/ORC 读写中跳过未知类型字段（不实际写入数据列，读取时返回 null 常量列），并在列裁剪、schema 转换等环节正确处理该类型。

## 如何达成设计目的

- **类型映射**：在 `TypeToSparkType` 中将 `UNKNOWN` 分支映射为 `NullType$`；在 `SparkTypeToType` 中将 Spark `NullType` 映射为 `Types.UnknownType.get()`。
- **列裁剪兼容**：在 `PruneColumnsWithoutReordering` 的类型兼容映射表中新增 `UNKNOWN -> NullType$`。
- **Parquet 写入**：在 `SparkParquetWriters.InternalRowWriter` 中建立 writer 索引到字段索引的映射，跳过 `NullType` 字段，使未知类型列不产生实际写入器。
- **Parquet schema 访问**：在 `ParquetWithSparkSchemaVisitor.visitFields` 中跳过 `NullType` 字段，使 struct 字段遍历与 Parquet group 字段对齐（未知字段不对应 Parquet 列）。
- **ORC 写入**：在 `SparkOrcWriter.InternalRowWriter` 中按 fieldId 匹配 ORC 类型，未知类型字段找不到对应 ORC 类型时返回 null getter；`createFieldGetter` 对 null 类型返回常量 null。
- **ORC schema 转换**：在 `ORCSchemaUtil.convert` 中对 LIST/MAP 增加 `UnknownType` 校验（元素/值不能为未知类型），struct 字段转换时跳过返回 null 的子类型。
- **向量化 ORC 读取**：在 `VectorizedSparkOrcReaders` 中对 `UnknownType` 字段返回 `ConstantColumnVector(type, batchSize, null)` 常量列。
- **测试**：在多个读写测试类中新增 `NullType`/`UnknownType` 场景的测试用例。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/ORCSchemaUtil.java` (+18/-1)

**修改目的**：ORC schema 转换时正确处理 UnknownType。

**工作逻辑**：
- struct 分支：子字段转换结果若为 null 则跳过 `addField`。
- LIST 分支：新增校验，元素类型不能为 `UNKNOWN`，否则抛出 `IllegalArgumentException`。
- MAP 分支：新增校验，值类型不能为 `UNKNOWN`（因为 key 必须是 required 且 UnknownType 必须 optional，所以只有 value 可能是 unknown，这里直接禁止 map value 为 unknown）。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/SparkTypeToType.java` (+3)

**修改目的**：Spark `NullType` 转 Iceberg `UnknownType`。

**工作逻辑**：在 atomic 类型转换链中新增 `else if (atomic instanceof NullType) return Types.UnknownType.get()`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/TypeToSparkType.java` (+5/-1)

**修改目的**：Iceberg `UnknownType` 转 Spark `NullType`。

**工作逻辑**：在 primitive 类型 switch 中新增 `case UNKNOWN: return NullType$.MODULE$`；将 default 分支错误信息从 "Cannot convert unknown type" 改为 "Cannot convert unsupported type" 以避免与 UnknownType 概念混淆。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithoutReordering.java` (+2)

**修改目的**：列裁剪时允许 UnknownType 与 NullType 匹配。

**工作逻辑**：在类型兼容性映射表中新增 `.put(TypeID.UNKNOWN, ImmutableSet.of(NullType$.class))`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/ParquetWithSparkSchemaVisitor.java` (+31/-8)

**修改目的**：Parquet schema 遍历时跳过 NullType 字段。

**工作逻辑**：`visitFields` 方法重写为遍历 Spark struct 字段时跳过 `DataTypes.NullType` 的字段，仅对非 NullType 字段匹配 Parquet group 字段并访问；遍历结束后断言所有 group 字段都已访问（`fieldIndex == group.getFieldCount()`），保证 schema 对齐。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcWriter.java` (+21/-2)

**修改目的**：ORC 写入时按 fieldId 匹配类型，处理未知类型字段。

**工作逻辑**：
- `InternalRowWriter` 构造函数改为接收 `Types.StructType iStruct`，将 ORC 类型按 fieldId 建立映射，然后按 Iceberg struct 字段顺序创建 fieldGetter；未知类型字段在映射中找不到对应 ORC 类型，`createFieldGetter` 对 null 返回 `(row, ordinal) -> null` 常量 null getter。
- `struct` 工厂方法传入 `iStruct` 给 `InternalRowWriter`。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+39/-3)

**修改目的**：Parquet 写入时跳过 NullType 字段，建立 writer 到字段索引映射。

**工作逻辑**：
- `struct` 方法中改为从 Spark struct 字段构建 `DataType[]` 数组传给 `InternalRowWriter`。
- `InternalRowWriter` 构造函数改为接收 `DataType[]`，并调用 `writerToFieldIndex(types, writers.size())` 生成 writer 索引到 record 字段索引的映射数组传给父类 `StructWriter`。
- `writerToFieldIndex` 方法遍历 types 数组，跳过 `NullType` 字段，为每个非 NullType 字段建立 writer 索引到字段位置的映射。

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkOrcReaders.java` (+2)

**修改目的**：向量化 ORC 读取时对 UnknownType 返回 null 常量列。

**工作逻辑**：在字段向量构建逻辑中新增分支，若字段类型为 `Types.UnknownType.get()`，则添加 `ConstantColumnVector(field.type(), batchSize, null)`。

### `spark/v3.5/spark/.../VectorizedSparkOrcReaders.java` (+2)

**修改目的**：在 Spark v3.5 版本同步添加 UnknownType 常量列处理。

### 测试文件（多个，共约 +124/-21）

**修改目的**：新增 NullType/UnknownType 读写测试用例。

**工作逻辑**：在 `AvroDataTestBase`、`TestSparkOrcReader`、`TestSparkParquetReader`、`TestSparkRecordOrcReaderWriter`、`TestORCDataFrameWrite`、`TestParquetDataFrameWrite`、`TestParquetScan` 等测试类中添加包含 NullType 字段的 schema 与数据测试，验证读写正确性和 null 常量列返回。

## 总结

此次提交为 Spark 4.0 模块完整实现了 Iceberg `UnknownType` 的读写支持。核心设计是将 `UnknownType` 与 Spark `NullType` 双向映射，在 Parquet/ORC 写入时跳过未知类型列（不产生实际数据列），读取时返回 null 常量列，并在 schema 转换、列裁剪、向量读取等各环节统一处理。这使得 schema 演进中存在已删除/未知类型字段的表能在 Spark 4 中正常读写，避免因类型不支持而报错。
