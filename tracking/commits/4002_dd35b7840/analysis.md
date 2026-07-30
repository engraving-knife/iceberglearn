# 提交 4002：Core: Test geometry and geography metrics keep counts without bounds (#17131)

## 提交信息

- **序号**：4002 / 4088
- **哈希**：dd35b7840edefdcbcb5a1fce14702cdafe76967b
- **短哈希**：dd35b7840
- **日期**：2026-07-08 17:38:05 -0700
- **作者**：Xin Huang
- **提交说明**：Core: Test geometry and geography metrics keep counts without bounds (#17131)
- **PR/Issue**：#17131

## 总体目的

本提交为 Iceberg 核心添加针对 geometry（几何）和 geography（地理）类型的 metrics 测试，验证这两种地理空间类型在生成文件统计信息时，会保留 value count 和 null value count，但不会生成 lower/upper bounds。

原因是 geometry/geography 在 Iceberg 中以 WKB（Well-Known Binary）字节序列存储，而 WKB 的字典序最小/最大值在空间语义上没有意义（不同几何对象的 WKB 字节序比较不反映空间包含/距离关系），所以当前实现故意跳过 bounds 计算，空间边界统计留作后续 follow-up。

## 如何达成设计目的

在 `TestMetrics`（参数化测试基类，覆盖多种文件格式）中新增 `testMetricsForGeospatialTypes` 测试方法。由于目前只有 Parquet 支持写入 geo 类型，使用 `assumeThat(fileFormat()).isEqualTo(FileFormat.PARQUET)` 跳过其他格式。测试构造包含 `geom`（GeometryType.crs84）和 `geog`（GeographyType.crs84）两列的 schema，写入两条记录（第二条 geo 列为 null），然后断言：
- recordCount 为 2。
- geo 列的 value count 为 2、null count 为 1。
- geo 列的 bounds 为 null（无上下界）。

同时新增辅助方法 `wkbPoint(x, y)` 生成小端序 WKB 编码的点几何。

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+46/-0 lines)

**修改目的**：新增地理空间类型 metrics 测试和 WKB 辅助方法。

**工作逻辑**：
- `testMetricsForGeospatialTypes`：
  ```java
  assumeThat(fileFormat()).isEqualTo(FileFormat.PARQUET);
  Schema schema = new Schema(
      required(1, "id", LongType.get()),
      optional(2, "geom", Types.GeometryType.crs84()),
      optional(3, "geog", Types.GeographyType.crs84()));
  // first: geom=wkbPoint(30,10), geog=wkbPoint(-5,40); second: geo 列 null
  Metrics metrics = getMetrics(schema, first, second);
  assertCounts(2, 2L, 1L, metricsWithStats);
  assertBounds(2, Types.GeometryType.crs84(), null, null, metricsWithStats);
  assertCounts(3, 2L, 1L, metricsWithStats);
  assertBounds(3, Types.GeographyType.crs84(), null, null, metricsWithStats);
  ```
  注释明确说明：geo 类型保留 counts 但无 bounds，因为 WKB 的字典序 min/max 无意义，空间 bounds 是单独的后续工作。

- `wkbPoint(double xCoord, double yCoord)`：构造 21 字节的小端 WKB Point：
  ```java
  ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN)
      .put((byte) 1)   // little endian
      .putInt(1)       // WKB type: Point
      .putDouble(xCoord)
      .putDouble(yCoord)
      .array()
  ```

## 总结

本提交补齐了 geometry/geography 类型在 metrics 层面的测试覆盖，明确了当前设计：geo 类型保留 value/null counts 但不计算 bounds（因 WKB 字典序无空间语义），为后续空间边界统计功能留出接口。注意该测试使用 `MetricsWithStats`/`MetricsUtil.fromMetrics`，而这些在提交 3996 中已被移除，因此本提交随后在 4004 被 revert，并在 4010 以适配新 API 的形式重新引入。
