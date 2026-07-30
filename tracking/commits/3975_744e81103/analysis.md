# 提交 3975：Parquet: Read and write geometry and geography WKB values (#16982)

## 提交信息

- **序号**：3975 / 4088
- **哈希**：744e811036c87727b41aa211fab9010bab48721f
- **短哈希**：744e81103
- **日期**：2026-07-02 17:36:18 -0700
- **作者**：Xin Huang
- **提交说明**：Parquet: Read and write geometry and geography WKB values (#16982)
- **PR/Issue**：#16982

## 总体目的

本提交实现了 Iceberg geometry/geography 类型在 Parquet 中的实际值读写能力。在提交 3956 中，schema 层面的 Parquet 逻辑类型映射已完成，但值（value）的读写路径被有意拒绝（抛出 `UnsupportedOperationException`），因为当时值路径尚未实现。本提交填补了这一空白。

geometry 和 geography 值以 WKB（Well-Known Binary）格式存储在 Parquet 的 BINARY 列中。本提交将之前抛出异常的 writer visitor 改为使用 `byteBuffers` writer 写入 WKB 字节，并新增对应的 reader visitor 读取 WKB 字节。同时扩展了测试基础设施以支持地理空间类型的随机数据生成和往返测试。

## 如何达成设计目的

1. **Writer**：将 `BaseParquetWriter` 中 geometry/geography 的 visitor 从抛出异常改为返回 `ParquetValueWriters.byteBuffers(desc)`，将 WKB 字节作为 ByteBuffer 写入 BINARY 列。
2. **Reader**：在 `BaseParquetReaders` 中新增 geometry/geography 的 visitor，使用 `ParquetValueReaders.byteBuffers(desc)` 读取 WKB 字节。
3. **测试基础设施**：在 `RandomUtil` 中新增 `wkbPoint()` 方法生成随机 WKB Point 值；在多个测试辅助类中添加 GEOMETRY/GEOGRAPHY 的类型处理分支；在 `DataTestBase` 子类中启用 `supportsGeospatial()` 并更新测试用例。

## 修改详情

### `api/src/test/java/org/apache/iceberg/util/RandomUtil.java` (+23/-0 lines)

**修改目的**：为地理空间类型生成随机 WKB 值。

**工作逻辑**：
- 在两个 randomValue 方法中新增 GEOMETRY/GEOGRAPHY 分支，生成随机坐标的 WKB Point。
- 新增 `wkbPoint(double x, double y)` 方法，编码小端 WKB Point（1 字节 byte order + 4 字节类型 + 2 个 double）：
```java
public static byte[] wkbPoint(double xCoord, double yCoord) {
  return ByteBuffer.allocate(21)
      .order(ByteOrder.LITTLE_ENDIAN)
      .put((byte) 1) // little endian
      .putInt(1) // WKB type: Point
      .putDouble(xCoord)
      .putDouble(yCoord)
      .array();
}
```

### `core/src/test/java/org/apache/iceberg/InternalTestHelpers.java` (+2/-0 lines)

**修改目的**：在值比较中支持 GEOMETRY/GEOGRAPHY。

### `core/src/test/java/org/apache/iceberg/RandomInternalData.java` (+2/-0 lines)

**修改目的**：将地理空间 byte[] 包装为 ByteBuffer。

### `core/src/test/java/org/apache/iceberg/data/DataTestHelpers.java` (+2/-0 lines)

**修改目的**：在数据测试比较中支持地理空间类型。

### `data/src/test/java/org/apache/iceberg/data/RandomGenericData.java` (+2/-0 lines)

**修改目的**：将地理空间 byte[] 包装为 ByteBuffer。

### `data/src/test/java/org/apache/iceberg/data/parquet/TestGenericData.java` (+5/-0 lines)

**修改目的**：启用 Parquet 的地理空间支持。

**工作逻辑**：重写 `supportsGeospatial()` 返回 true。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetReaders.java` (+14/-0 lines)

**修改目的**：新增 geometry/geography reader。

**工作逻辑**：
```java
@Override
public Optional<ParquetValueReader<?>> visit(
    LogicalTypeAnnotation.GeometryLogicalTypeAnnotation geometryLogicalType) {
  return Optional.of(ParquetValueReaders.byteBuffers(desc));
}
```
geography 同理。WKB 值作为 ByteBuffer 读取。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java` (+5/-5 lines)

**修改目的**：将 geometry/geography writer 从拒绝改为写入 ByteBuffer。

**工作逻辑**：
```java
// 旧：throw new UnsupportedOperationException("Cannot write geometry value to Parquet");
// 新：
return Optional.of(ParquetValueWriters.byteBuffers(desc));
```

### `parquet/src/test/java/org/apache/iceberg/parquet/TestInternalParquet.java` (+5/-0 lines)

**修改目的**：启用 Parquet 内部测试的地理空间支持。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetDataWriter.java` (+49/-35 lines)

**修改目的**：将拒绝测试改为往返测试。

**工作逻辑**：将 `testGeospatialWriteIsRejected` 替换为 `testGeospatialRoundTrip`，构造包含 geometry 和 geography 列的 schema，写入包含 null 和非 null WKB 值的记录，读取后验证 WKB 字节完全一致。

## 总结

本提交完成了 geometry/geography 类型在 Parquet 中的值读写实现，使地理空间数据能够以 WKB 格式正确存储和读取。这是提交 3956（schema 映射）的后续工作，两者结合实现了完整的 Parquet 地理空间支持。WKB 作为纯二进制格式存储在 BINARY 列中，读写均使用 ByteBuffer 处理，简洁高效。
