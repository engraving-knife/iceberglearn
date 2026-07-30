# 提交 1892：Core, Parquet, ORC: Fix missing data when writing unknown (#12581)

## 提交信息

- **序号**：1892 / 4088
- **哈希**：721741764193bc4767bc2ae70a423f020fdb83c1
- **短哈希**：721741764
- **日期**：2025-03-20 14:42:11 -0700
- **作者**：Ryan Blue
- **提交说明**：Core, Parquet, ORC: Fix missing data when writing unknown (#12581)
- **PR/Issue**：#12581

## 总体目的

这个提交修复了 Iceberg 在写入包含 `Unknown` 类型字段的表时数据丢失的问题。`Unknown` 类型是 Iceberg 引入的一种特殊类型，用于表示 schema 演进中尚未确定的字段类型——例如在 format-version v3 中新增的 Variant 类型，在旧版本 reader 中会被表示为 Unknown。

问题的根源在于：当 Iceberg schema 中包含 `Unknown` 类型的字段时，文件格式写入器（Parquet/ORC）在构建 writer 时会跳过这些字段（因为它们不需要被写入文件），但 `StructWriter` 在写入数据时仍然按照 writer 的索引顺序来获取 `StructLike` 中的字段值。由于 Unknown 字段被跳过了，writer 索引和 schema 字段索引之间产生了偏移，导致后续字段的数据被错误地对应——即字段 N 的数据可能被写入到字段 N-1 的位置，或者数据完全错位。

例如，如果 schema 有 5 个字段，其中第 2 个是 Unknown 类型，那么 writer 只会创建 4 个子 writer（跳过 Unknown），但写入时 `get(value, 0)`、`get(value, 1)`、`get(value, 2)`、`get(value, 3)` 会从原始 5 字段的 StructLike 中取值，导致从第 2 个 writer 开始取到的都是错误字段的值。

## 如何达成设计目的

整体设计思路是在 `StructWriter` 中引入一个字段索引映射表 `fieldIndexes[]`，将 writer 索引正确映射到 schema 字段索引，跳过 Unknown 类型的字段。这样即使 schema 中有 Unknown 字段被跳过，writer 也能从正确的位置获取数据。

具体实现：

1. **Parquet 侧**：`ParquetValueWriters.StructWriter` 新增 `fieldIndexes` 字段和 `writerToFieldIndex` 方法，写入时使用 `get(value, fieldIndexes[i])` 而非 `get(value, i)`。
2. **ORC 侧**：`GenericOrcWriters.StructWriter` 做同样的修改。
3. **Flink 侧**：`FlinkParquetWriters` 中跳过 `LogicalTypeRoot.NULL` 类型字段（Flink 中 Unknown 对应 NULL 类型），并维护字段索引映射。
4. **writer 构建链路**：将 `createWriterFunc` 从 `Function<MessageType, Writer>` 改为 `BiFunction<Schema, MessageType, Writer>`，使 writer 构建时能获取到 Iceberg Schema 信息（而不仅仅是 Parquet MessageType），从而能判断哪些字段是 Unknown 类型。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetValueWriters.java` (修改, +55/-2 lines)

**修改目的**：在 Parquet StructWriter 中引入字段索引映射，跳过 Unknown 字段。

**工作逻辑**：

1. `recordWriter` 方法新增重载，接受 `Types.StructType struct` 参数。旧方法标记为 `@Deprecated`，委托给新方法并传 `null`。

2. `StructWriter` 新增 `fieldIndexes` 字段（int 数组），构造时通过 `writerToFieldIndex(struct, writers.size())` 计算映射。写入时 `get(value, fieldIndexes[i])` 替代 `get(value, i)`。

3. 新增 `writerToFieldIndex(Types.StructType struct, int numWriters)` 方法：遍历 schema 字段，跳过 `TypeID.UNKNOWN` 类型的字段，返回一个从 writer 索引到 schema 字段索引的映射数组。当 struct 为 null 时（向后兼容），返回 `0..numWriters` 的顺序映射。

### `parquet/src/main/java/org/apache/iceberg/parquet/Parquet.java` (修改, +28/-4 lines)

**修改目的**：将 `createWriterFunc` 从 `Function` 改为 `BiFunction<Schema, MessageType, Writer>`，使 writer 构建能获取 Iceberg Schema。

**工作逻辑**：

1. `WriteBuilder` 新增 `createWriterFunc(BiFunction<Schema, MessageType, ?>)` 重载方法。
2. `DataWriteBuilder` 同样新增 BiFunction 重载。
3. `DeleteWriteBuilder` 中将 `createWriterFunc` 字段类型从 `Function` 改为 `BiFunction`，旧方法适配为 `(ignored, fileSchema) -> newCreateWriterFunc.apply(fileSchema)`。
4. 在构建 position delete writer 时，lambda 改为接收 `(schema, parquetSchema)` 两个参数，调用 `createWriterFunc.apply(schema, parquetSchema)`，并将 `GenericParquetWriter.buildWriter(parquetSchema)` 改为 `GenericParquetWriter.create(schema, parquetSchema)`。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java` (修改, +4/-2 lines)

**修改目的**：将 Iceberg Schema 传递到 struct writer 创建方法。

**工作逻辑**：`createStructWriter` 方法签名从 `(List<ParquetValueWriter<?>> writers)` 改为 `(Types.StructType struct, List<ParquetValueWriter<?>> writers)`，并在构建 struct writer 时传入 `iceberg` schema 对象。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/GenericParquetWriter.java` (修改, +22/-3 lines)

**修改目的**：适配新的 struct writer 创建签名，提供 schema 信息。

**工作逻辑**：旧 `createStructWriter(List)` 标记 `@Deprecated` 并委托传 `null`。新 `createStructWriter(Types.StructType, List)` 调用 `ParquetValueWriters.recordWriter(struct, writers)`，将 schema 信息传递下去以正确跳过 Unknown 字段。`buildWriter` 标记 `@Deprecated`。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/InternalWriter.java` (修改, +24/-5 lines)

**修改目的**：与 GenericParquetWriter 相同的适配，为 InternalWriter 提供 schema 信息。

**工作逻辑**：`create` 方法重命名为 `createWriter`（旧名标记 `@Deprecated`）。`createStructWriter` 新增带 `Types.StructType` 的重载，调用 `ParquetValueWriters.recordWriter(struct, writers)`。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcWriters.java` (修改, +32/-1 lines)

**修改目的**：在 ORC StructWriter 中引入字段索引映射，跳过 Unknown 字段。

**工作逻辑**：与 Parquet 侧对称的修改。`StructWriter` 新增 `fieldIndexes` 字段，构造时调用 `writerToFieldIndex(struct, writers.size())`。`write` 方法中 `get(value, c)` 改为 `get(value, fieldIndexes[c])`。新增 `writerToFieldIndex` 方法，逻辑与 Parquet 版本相同。

### `orc/src/main/java/org/apache/iceberg/data/orc/GenericOrcWriter.java` (修改, +6/-2 lines)

**修改目的**：将 schema 信息传递到 ORC struct writer。

**工作逻辑**：`RecordWriter` 构造函数新增 `Types.StructType struct` 参数，传递给父类 `StructWriter` 的构造函数。`createStructWriter` 中传入 `iStruct`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (修改, +22/-3 lines)

**修改目的**：在 Flink Parquet writer 中跳过 NULL 类型字段（Flink 中 Unknown 的对应类型）。

**工作逻辑**：在 `struct` 方法中，遍历 Flink 的 `RowType` 字段而非 Parquet schema 字段。当字段类型为 `LogicalTypeRoot.NULL` 时跳过，不为 NULL 的字段才创建 writer 并记录字段索引。`RowDataWriter` 新增 `fieldIndexes` 参数，在创建 `FieldGetter` 时使用正确的字段索引而非顺序索引。

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (修改, +22/-3 lines)

**修改目的**：与 v1.20 相同的修复，应用到 Flink 1.18 版本。

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (修改, +22/-3 lines)

**修改目的**：与 v1.20 相同的修复，应用到 Flink 1.19 版本。

### 其他文件

多个测试文件中的 reader 引用从 `GenericParquetWriter.buildWriter` 改为 `create` 或 `createWriter`，以及少量适配性修改（如 `SparkRowLevelOperationsTestBase`、`RewriteTablePathSparkAction` 等）。

## 总结

本提交通过在 Parquet、ORC 和 Flink 的 `StructWriter` 中引入字段索引映射机制，修复了当 schema 包含 `Unknown` 类型字段时数据错位/丢失的问题。核心改动是 `writerToFieldIndex` 方法——它在 writer 索引和 schema 字段索引之间建立正确映射，跳过 Unknown 字段。同时将 `createWriterFunc` 接口从 `Function` 改为 `BiFunction`，使 writer 构建时能获取 Iceberg Schema 信息来判断哪些字段需要跳过。这是一个影响面较广但修复逻辑清晰的 bug fix。
