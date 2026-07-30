# 提交 4010：Core: Test geometry and geography metrics keep counts without bounds (#17147)

## 提交信息

- **序号**：4010 / 4088
- **哈希**：d99b0eefab6a9459e65a77448de914c61a89fadd
- **短哈希**：d99b0eefa
- **日期**：2026-07-10 23:53:21 -0700
- **作者**：Xin Huang
- **提交说明**：Core: Test geometry and geography metrics keep counts without bounds (#17147)
- **PR/Issue**：#17147

## 总体目的

本提交是提交 4002（已被 4004 revert）的重新引入，以适配 3996 重构后的新 metrics API。验证 geometry（几何）和 geography（地理）类型在生成文件统计信息时，保留 value count 和 null value count，但不生成 lower/upper bounds。

与 4002 的唯一区别在于：本版本不再使用已被移除的 `MetricsWithStats`/`MetricsUtil.fromMetrics`，而是直接对 `Metrics` 对象调用 `assertCounts`/`assertBounds`，与 3996 重构后的测试 API 一致。

## 如何达成设计目的

在 `TestMetrics` 中新增 `testMetricsForGeospatialTypes` 测试方法和 `wkbPoint` 辅助方法，逻辑与 4002 完全相同，仅断言调用方式适配新 API：
- 4002：`assertCounts(2, 2L, 1L, metricsWithStats)`（使用 `MetricsWithStats` 包装）
- 4010：`assertCounts(2, 2L, 1L, metrics)`（直接传 `Metrics`）

## 修改详情

### `core/src/test/java/org/apache/iceberg/TestMetrics.java` (+44/-0 lines)

**修改目的**：新增地理空间类型 metrics 测试（适配新 API）。

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
  assertThat(metrics.recordCount()).isEqualTo(2L);
  assertCounts(2, 2L, 1L, metrics);
  assertBounds(2, Types.GeometryType.crs84(), null, null, metrics);
  assertCounts(3, 2L, 1L, metrics);
  assertBounds(3, Types.GeographyType.crs84(), null, null, metrics);
  ```
  注释说明 geo 类型保留 counts 但无 bounds，因为 WKB 字典序 min/max 无空间语义，空间 bounds 是后续工作。

- `wkbPoint(double xCoord, double yCoord)`：生成 21 字节小端 WKB Point：
  ```java
  ByteBuffer.allocate(21).order(ByteOrder.LITTLE_ENDIAN)
      .put((byte) 1).putInt(1).putDouble(xCoord).putDouble(yCoord).array()
  ```

## 总结

本提交重新引入了 4002 被 revert 的地理空间类型 metrics 测试，适配 3996 重构后的新 API（直接对 `Metrics` 断言而非 `MetricsWithStats`）。测试逻辑不变，验证 geometry/geography 保留 value/null counts 但不计算 bounds 的设计意图。这是 4002/4004/4010 三步曲折过程中的最终落地版本。
