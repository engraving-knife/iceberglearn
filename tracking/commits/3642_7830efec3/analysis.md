# 提交 3642：Spark: Add unknown type support to Spark 3.4 and 3.5 (#16066)

## 提交信息

- **序号**：3642 / 4088
- **哈希**：7830efec3635893e39255823590b2422fde9171c
- **短哈希**：7830efec3
- **日期**：2026-05-05 15:04:34 +0200
- **作者**：Kurtis Wright
- **提交说明**：Spark: Add unknown type support to Spark 3.4 and 3.5 (#16066)
- **PR/Issue**：#16066

## 总体目的

这个提交为 Spark 3.4 和 3.5 添加对 Iceberg `UnknownType` 的支持，将其与 Spark 的 `NullType` 进行双向映射。

Iceberg v3 规范引入了 `UnknownType`，用于表示元数据中存在但类型未知的列（例如未来版本的数据类型）。此前 Spark 4.x 已支持该类型的映射，但 Spark 3.4 和 3.5 在遇到 `UnknownType` 时会抛出 `UnsupportedOperationException`，导致无法读取含未知类型列的 v3 表。本提交使 Spark 3.x 与 Spark 4.x 行为对齐，允许读取含未知类型列的表而不报错。由于 Spark 自 2.x 起就支持 `NullType`，该映射在 Spark 3.4/3.5 中是可行的。

## 如何达成设计目的

1. 在类型转换器中建立双向映射：`TypeToSparkType` 将 `UNKNOWN` 映射为 `NullType`，`SparkTypeToType` 将 `NullType` 映射为 `UnknownType`。
2. 在 `PruneColumnsWithoutReordering` 中将 `UNKNOWN` 类型与 `NullType` 关联，允许列裁剪时匹配。
3. 在 Parquet 和 ORC 的写入/读取 visitor 中处理 `NullType` 列：写入时跳过该列（不写入物理列），读取时为该列返回 null。
4. 新增多个测试覆盖 schema 转换、Parquet/ORC 读写场景。

## 修改详情

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/TypeToSparkType.java` (+4/-1 lines)

**修改目的**：Iceberg `UnknownType` -> Spark `NullType` 映射。

**工作逻辑**：
```java
case UNKNOWN:
  return NullType$.MODULE$;
```
同时将默认分支的错误消息从 "Cannot convert unknown type to Spark" 改为 "Cannot convert unsupported type to Spark"，避免与 `UNKNOWN` 类型名混淆。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/SparkTypeToType.java` (+3 lines)

**修改目的**：Spark `NullType` -> Iceberg `UnknownType` 映射。

**工作逻辑**：
```java
} else if (atomic instanceof NullType) {
  return Types.UnknownType.get();
}
```

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithoutReordering.java` (+2 lines)

**修改目的**：列裁剪时将 `UNKNOWN` 类型与 `NullType` 关联。

**工作逻辑**：在类型到 Spark 类型的映射表中新增 `.put(TypeID.UNKNOWN, ImmutableSet.of(NullType$.class))`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/ParquetWithSparkSchemaVisitor.java` (+18/-13 lines)

**修改目的**：Parquet schema visitor 跳过 `NullType` 列。

**工作逻辑**：重写 `visitFields` 方法，遍历 Spark struct 字段时跳过 `DataTypes.NullType` 类型的字段（不与 Parquet group 字段对应），最后校验所有 Parquet group 字段都被访问。这样含未知类型的列不会在 Parquet 文件中产生物理列。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+33/-5 lines)

**修改目的**：Parquet 写入器处理 `NullType` 列。

**工作逻辑**：
1. `InternalRowWriter` 新增 `writerToFieldIndex` 方法，构建 writer 索引到记录字段索引的映射，跳过 `NullType` 列。这样 writer 只处理非 null 列，但能正确定位到 InternalRow 中的字段位置。
2. `struct` 方法调整，从 Spark `StructField` 数组构建类型数组传入 writer。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/SparkOrcWriter.java` (+17/-4 lines)

**修改目的**：ORC 写入器处理 `NullType` 列。

**工作逻辑**：
1. `InternalRowWriter` 构造改为接收 `Types.StructType iStruct`，通过 field id 映射 ORC 类型，对未知类型列（field id 对应的 ORC 类型为 null）返回 null getter。
2. `createFieldGetter` 方法新增 null 检查：若 `fieldType == null`（即未知类型），返回 `(row, ordinal) -> null`。

### `spark/v3.4/spark/src/main/java/org/apache/iceberg/spark/data/vectorized/VectorizedSparkOrcReaders.java` (+2 lines)

**修改目的**：向量化 ORC 读取器处理未知类型。

### 测试文件（Spark 3.4 和 3.5 各一套）

- `TestSparkSchemaUtil.java` (+16 lines)：验证 UnknownType/NullType 双向转换。
- `AvroDataTestBase.java` (+56 lines)：验证 Avro 数据中未知类型列的处理。
- `TestSparkOrcReader.java` (+17 lines)：验证 ORC 读取含未知类型列。
- `TestSparkParquetReader.java` (+16 lines)：验证 Parquet 读取含未知类型列。
- `TestSparkRecordOrcReaderWriter.java` (+17 lines)：验证 ORC 读写往返。
- `ScanTestBase.java`：调整扫描测试适配未知类型。
- `TestORCDataFrameWrite.java` (+24 lines)：验证 ORC DataFrame 写入。
- `TestParquetDataFrameWrite.java` (+24 lines)：验证 Parquet DataFrame 写入。
- `TestParquetScan.java` (+18 lines)：验证 Parquet 扫描含未知类型列。

## 总结

这个提交为 Spark 3.4 和 3.5 补齐了对 Iceberg v3 `UnknownType` 的支持，通过将其映射为 Spark `NullType` 实现双向转换，并在 Parquet/ORC 的读写路径中跳过未知类型列（写入时不产生物理列，读取时返回 null）。这使得 Spark 3.x 用户能够读取含未知类型列的 v3 表而不报错，与 Spark 4.x 的行为保持一致。改动覆盖类型转换、列裁剪、Parquet/ORC 读写多个环节，并附带了全面的测试。
