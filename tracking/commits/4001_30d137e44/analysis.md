# 提交 4001：Spark 4.1: Read and write geometry and geography values in Parquet (#17073)

## 提交信息

- **序号**：4001 / 4088
- **哈希**：30d137e441e08f475d2cf82b5909489cb362de89
- **短哈希**：30d137e44
- **日期**：2026-07-08 16:57:07 -0700
- **作者**：Xin Huang
- **提交说明**：Spark 4.1: Read and write geometry and geography values in Parquet (#17073)
- **PR/Issue**：#17073

## 总体目的

本提交为 Spark 4.1 集成添加对 Iceberg 中 geometry（几何）和 geography（地理）类型的 Parquet 读写支持。Iceberg 在底层将这两种地理空间类型存储为 WKB（Well-Known Binary）字节序列，而 Spark 4.1 引入了原生的 `GeometryVal`/`GeographyVal` 类型，其内部表示为 `[SRID | WKB]`（带 SRID 头的 WKB）。因此需要在读写时做格式转换：写入时剥离 Spark 的 SRID 头只存纯 WKB，读取时根据列的 CRS（坐标参考系）信息重建 Spark 的 geometry/geography 值。

这是 Iceberg 支持地理空间类型生态的关键一环，使 Spark 4.1 用户能够直接以原生 geometry/geography 类型读写 Iceberg 表的 Parquet 文件。

## 如何达成设计目的

设计思路：
1. **读取端**（`SparkParquetReaders`）：在 `primitive(...)` 方法中先检查 Parquet 列的 `LogicalTypeAnnotation`，若为 `GeometryLogicalTypeAnnotation` 则用 `GeometryReader`，若为 `GeographyLogicalTypeAnnotation` 则用 `GeographyReader`。两个 reader 都从 Parquet 读取 BINARY（WKB），通过 Spark 的 `STUtils.stGeomFromWKB`/`stGeogFromWKB` 转为 Spark 类型，其中 geometry 需要从列的 CRS 解析 SRID。
2. **写入端**（`SparkParquetWriters`）：在 logical type visitor 中新增对 `GeometryLogicalTypeAnnotation`/`GeographyLogicalTypeAnnotation` 的处理，分别返回 `GeometryWriter`/`GeographyWriter`。两个 writer 通过 `STUtils.stAsBinary(value)` 把 Spark 值转为纯 WKB 写入 BINARY 列。
3. **测试基础设施**：在 `GenericsHelpers`、`RandomData`、`TestSparkParquetReader` 中添加对 GEOMETRY/GEOGRAPHY 类型的支持，包括随机数据生成、断言比较，以及声明 `supportsGeospatial() = true`。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetReaders.java` (+49/-0 lines)

**修改目的**：实现 geometry/geography 的 Parquet 读取。

**工作逻辑**：
- `primitive(...)` 方法开头新增对 `LogicalTypeAnnotation` 的判断：
  ```java
  if (logicalType instanceof GeometryLogicalTypeAnnotation) {
    String crs = ((GeometryLogicalTypeAnnotation) logicalType).getCrs();
    return new GeometryReader(desc, sridFromCrs(crs));
  } else if (logicalType instanceof GeographyLogicalTypeAnnotation) {
    return new GeographyReader(desc);
  }
  ```
- `sridFromCrs(crs)`：crs 为 null 时返回 `GeometryType$.GEOMETRY_DEFAULT_SRID()`（OGC:CRS84），否则通过 `GeometryType$.apply(crs).srid()` 解析。
- `GeometryReader`：持有 srid，`read` 时调用 `STUtils.stGeomFromWKB(column.nextBinary().getBytes(), srid)` 把 WKB + SRID 组装成 `GeometryVal`。
- `GeographyReader`：geography 仅支持 OGC:CRS84，`read` 时调用 `STUtils.stGeogFromWKB(column.nextBinary().getBytes())`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/data/SparkParquetWriters.java` (+41/-0 lines)

**修改目的**：实现 geometry/geography 的 Parquet 写入。

**工作逻辑**：
- 在 logical type visitor 中新增两个 `visit` 方法分别处理 `GeometryLogicalTypeAnnotation` 和 `GeographyLogicalTypeAnnotation`，返回 `new GeometryWriter(desc)` / `new GeographyWriter(desc)`。
- `GeometryWriter.write`：`column.writeBinary(repetitionLevel, Binary.fromReusedByteArray(STUtils.stAsBinary(value)))`，把 `GeometryVal` 转为纯 WKB 写入。
- `GeographyWriter.write`：同样调用 `STUtils.stAsBinary(value)` 写入 WKB。
- 注释说明 Spark 存 `[SRID | WKB]`，Iceberg 存纯 WKB，所以写入时剥离 SRID 头。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/GenericsHelpers.java` (+27/-0 lines)

**修改目的**：在测试断言中支持 GEOMETRY/GEOGRAPHY 类型比较。

**工作逻辑**：
在两个 `assertEquals` 重载的 switch 中新增 `case GEOMETRY` 和 `case GEOGRAPHY`：
- 期望值是 `ByteBuffer`（Iceberg 的 WKB 表示），实际值是 `GeometryVal`/`GeographyVal`。
- 通过 `STUtils.stAsBinary((GeometryVal) actual)` 把 Spark 值转回 WKB，再与 `((ByteBuffer) expected).array()` 比较。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/RandomData.java` (+6/-0 lines)

**修改目的**：随机数据生成支持 geometry/geography。

**工作逻辑**：
在 `convertValue` 的 switch 中新增：
```java
case GEOMETRY:
  return STUtils.stGeomFromWKB((byte[]) obj);
case GEOGRAPHY:
  return STUtils.stGeogFromWKB((byte[]) obj);
```
将生成的 WKB 字节数组转换为 Spark 的 `GeometryVal`/`GeographyVal`。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetReader.java` (+5/-0 lines)

**修改目的**：声明该 reader 支持地理空间类型。

**工作逻辑**：重写 `supportsGeospatial()` 返回 `true`，使基类测试包含地理空间类型的用例。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/data/TestSparkParquetWriter.java` (+51/-0 lines)

**修改目的**：为 writer 添加地理空间类型测试。

**工作逻辑**：新增针对 geometry/geography 的写入测试用例（具体内容见文件）。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/sql/TestSparkGeospatial.java` (+100/-0 lines)

**修改目的**：端到端 Spark SQL 地理空间类型测试。

**工作逻辑**：新增测试类验证通过 Spark SQL 读写 Iceberg 表中 geometry/geography 列的完整流程。

## 总结

本提交为 Spark 4.1 集成补齐了 geometry/geography 类型的 Parquet 读写能力，核心是在 Iceberg 的纯 WKB 存储与 Spark 的 `[SRID | WKB]` 内部表示之间做转换。读取端根据列的 CRS 解析 SRID 重建 Spark 值，写入端剥离 SRID 头只存 WKB。配套完善了测试基础设施，使地理空间类型能够端到端地在 Spark 4.1 + Iceberg + Parquet 栈中流通。
