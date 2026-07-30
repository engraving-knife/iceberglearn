# 提交 3934：Parquet: Skip geo footer bounds (#16850)

## 提交信息

- **序号**：3934 / 4088
- **哈希**：f07b404b060d2bdf06799161de6c12aa43235e11
- **短哈希**：f07b404b0
- **日期**：2026-06-23 17:01:21 -0700
- **作者**：Xin Huang
- **提交说明**：Parquet: Skip geo footer bounds (#16850)
- **PR/Issue**：#16850

## 总体目的

这次提交修复了 Parquet metrics 计算中对地理空间（geospatial）类型列的 bounds 处理问题。Iceberg 引入了 `GEOMETRY` 和 `GEOGRAPHY` 两种地理空间类型，其底层存储为 WKB（Well-Known Binary）格式的二进制数据。Parquet 文件 footer 中存储的列统计（min/max）是基于二进制字节数组的词典序（lexicographic）比较结果。

然而，WKB 字节数组的词典序比较对于空间数据是没有意义的——两个 WKB 字节序列的字典序大小关系并不反映它们所表示的几何对象的空间包含或距离关系。如果将这些无意义的 min/max bounds 写入 Iceberg metrics，查询引擎可能会基于错误的 bounds 进行文件裁剪，导致错误地跳过包含目标数据的文件，或无法有效裁剪不相关文件，反而降低查询性能。

修复方式是在计算 Parquet metrics 时，对 GEOMETRY 和 GEOGRAPHY 类型的列跳过 bounds 计算，只保留 value count 和 null count 统计。

## 如何达成设计目的

在 `ParquetMetrics` 的 bounds 计算分支中，增加对地理空间类型的判断：当列的 Iceberg 类型为 `GEOMETRY` 或 `GEOGRAPHY` 时，调用 `counts(fieldId)` 只计算计数统计而不计算 bounds，与 `truncateLength <= 0`（禁用 bounds）的处理方式一致。新增 `isGeospatial(TypeID typeId)` 私有方法判断类型。

## 修改详情

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetMetrics.java` (+9/-1 lines)

**修改目的**：跳过地理空间类型列的 bounds 计算。

**工作逻辑**：
1. 新增 import `org.apache.iceberg.types.Type.TypeID`。
2. 修改 bounds 计算的条件判断：
```java
} else if (truncateLength <= 0
    || (icebergType != null && isGeospatial(icebergType.typeId()))) {
  // Parquet lexicographic min/max is not meaningful for spatial WKB.
  return counts(fieldId);
}
```
当 truncateLength <= 0 或列为地理空间类型时，仅计算计数统计。
3. 新增 `isGeospatial` 方法：
```java
private static boolean isGeospatial(TypeID typeId) {
  return typeId == TypeID.GEOMETRY || typeId == TypeID.GEOGRAPHY;
}
```

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquet.java` (+46/-0 lines)

**修改目的**：验证地理空间类型列不包含 bounds 但保留计数统计。

**工作逻辑**：
新增 `testGeospatialFooterMetricsSkipParquetBounds` 测试方法，使用二进制 schema 写入两个 WKB 字节记录，然后分别用 `Types.GeometryType.crs4()` 和 `Types.GeographyType.crs4()` schema 读取 metrics，断言：
- `valueCounts` 包含条目且值为 2（两条记录）。
- `nullValueCounts` 包含条目且值为 0（无 null）。
- `lowerBounds` 不包含该列的 key（即未计算 bounds）。
- `upperBounds` 不包含该列的 key。

## 总结

这次提交修复了 Parquet metrics 中对地理空间类型的处理，避免将无意义的 WKB 字典序 bounds 写入 Iceberg metrics。通过跳过 GEOMETRY/GEOGRAPHY 列的 bounds 计算，只保留计数统计，确保查询引擎不会基于错误的 bounds 做出错误的文件裁剪决策。这是 Iceberg 地理空间类型支持的重要补充。
