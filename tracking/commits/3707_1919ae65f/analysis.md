# 提交 3707：Flink: Support UUID type in Avro and Parquet readers and writers (#16097)

## 提交信息

- **序号**：3707 / 4088
- **哈希**：1919ae65fb6e836df3530562af693d1fb8f14b5b
- **短哈希**：1919ae65f
- **日期**：2026-05-14 14:11:01 +0200
- **作者**：Joy Haldar
- **提交说明**：Flink: Support UUID type in Avro and Parquet readers and writers (#16097)
- **PR/Issue**：#16097

## 总体目的

这个提交为 Flink 2.1 模块添加了 UUID 数据类型的完整读写支持。Iceberg 支持 UUID 类型（128 位唯一标识符），但 Flink 集成模块在 Avro 和 Parquet 文件格式的读写器中缺乏对 UUID 类型的正确处理。

具体问题包括：
1. **Avro 写入器**：`FlinkAvroWriter` 对 UUID 逻辑类型使用了通用的 `ValueWriters.uuids()` 而非 Flink 专用的 `FlinkValueWriters.uuids()`，导致写入逻辑不一致
2. **Parquet 读取器**：`FlinkParquetReaders` 没有处理 Parquet 的 `UUIDLogicalTypeAnnotation`，导致无法读取含 UUID 类型的 Parquet 文件
3. **Parquet 写入器**：`FlinkParquetWriters` 同样没有处理 `UUIDLogicalTypeAnnotation`，导致无法写入 UUID 类型到 Parquet 文件
4. **FlinkValueWriters**：缺少专用的 UUID 写入器实现
5. **FlinkSink**：schema 转换逻辑使用 `TypeUtil.reassignIds` 可能导致 UUID 类型信息丢失

## 如何达成设计目的

通过以下修改实现 UUID 类型支持：
1. 在 `FlinkValueWriters` 中新增 `UUIDWriter` 内部类和 `uuids()` 工厂方法
2. 在 `FlinkAvroWriter` 中将 `ValueWriters.uuids()` 替换为 `FlinkValueWriters.uuids()`
3. 在 `FlinkParquetReaders` 和 `FlinkParquetWriters` 中添加 `UUIDLogicalTypeAnnotation` 的 visit 方法
4. 在 `FlinkSink` 中改进 schema 转换逻辑，使用 `FlinkSchemaUtil.convert(schema, requestedSchema)` 替代 `TypeUtil.reassignIds`

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkValueWriters.java` (+13 lines)

**修改目的**：新增 UUID 写入器实现。

**工作逻辑**：

```java
static ValueWriter<byte[]> uuids() {
  return UUIDWriter.INSTANCE;
}

private static class UUIDWriter implements ValueWriter<byte[]> {
  private static final UUIDWriter INSTANCE = new UUIDWriter();

  @Override
  public void write(byte[] bytes, Encoder encoder) throws IOException {
    encoder.writeFixed(bytes);
  }
}
```

UUID 以 byte 数组形式存储，写入时使用 Avro 的 `writeFixed` 方法。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkAvroWriter.java` (+1/-1 lines)

**修改目的**：使用 Flink 专用的 UUID 写入器。

**工作逻辑**：
```java
-case "uuid":
-  return ValueWriters.uuids();
+case "uuid":
+  return FlinkValueWriters.uuids();
```

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetReaders.java` (+6 lines)

**修改目的**：支持读取 Parquet UUID 类型。

**工作逻辑**：
```java
@Override
public Optional<ParquetValueReader<?>> visit(
    LogicalTypeAnnotation.UUIDLogicalTypeAnnotation uuidLogicalType) {
  return Optional.of(new ParquetValueReaders.ByteArrayReader(desc));
}
```

UUID 在 Parquet 中以 16 字节 fixed-length binary 形式存储，读取时使用 `ByteArrayReader`。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/data/FlinkParquetWriters.java` (+6 lines)

**修改目的**：支持写入 Parquet UUID 类型。

**工作逻辑**：
```java
@Override
public Optional<ParquetValueWriter<?>> visit(UUIDLogicalTypeAnnotation uuid) {
  return Optional.of(byteArrays(desc));
}
```

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/FlinkSink.java` (+4/-6 lines)

**修改目的**：改进 schema 转换逻辑。

**工作逻辑**：

```java
-      Schema writeSchema = TypeUtil.reassignIds(FlinkSchemaUtil.convert(requestedSchema), schema);
+      Schema writeSchema = FlinkSchemaUtil.convert(schema, requestedSchema);
```

使用 `FlinkSchemaUtil.convert(schema, requestedSchema)` 以表 schema 为参考转换 Flink schema，替代之前的 `TypeUtil.reassignIds` 方式。这种方式能更好地保留类型信息（包括 UUID）。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/TestFlinkUuidType.java` (new file, +192 lines)

**修改目的**：添加 UUID 类型端到端测试。

## 总结

这是一个功能补全提交，为 Flink 2.1 模块添加了 UUID 数据类型的完整读写支持，覆盖 Avro 和 Parquet 两种文件格式。修改涉及读取器、写入器、schema 转换等多个层面，同时改进了 FlinkSink 的 schema 转换逻辑以更好地保留类型信息。这使得 Flink 用户可以在 Iceberg 表中使用 UUID 类型进行数据读写。
