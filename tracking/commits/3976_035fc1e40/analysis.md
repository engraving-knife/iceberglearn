# 提交 3976：Spark 4.1: Map geo Spark types (#16851)

## 提交信息

- **序号**：3976 / 4088
- **哈希**：035fc1e405d8902b272f4c5af42a24919d2ea625
- **短哈希**：035fc1e40
- **日期**：2026-07-02 17:37:38 -0700
- **作者**：Xin Huang
- **提交说明**：Spark 4.1: Map geo Spark types (#16851)
- **PR/Issue**：#16851

## 总体目的

本提交在 Spark 4.1 模块中实现了 Iceberg 的 geometry/geography 类型与 Spark 4.1 原生地理空间类型之间的双向映射。Spark 4.1 引入了 `GeometryType` 和 `GeographyType` 作为原生类型，本提交使 Iceberg 表中的地理空间字段能在 Spark 中以原生类型使用，反之亦然。

映射涉及 CRS（坐标参考系统）和边缘插值算法（edge interpolation algorithm）的转换。由于 Spark 和 Iceberg 对算法枚举的定义不同，需要专门的转换逻辑。同时，Spark 的某些限制（如只支持 spherical 算法、mixed SRID 不支持）需要在转换时进行检查和拒绝。

## 如何达成设计目的

1. **TypeToSparkType（Iceberg → Spark）**：新增 GEOMETRY/GEOGRAPHY 分支，调用 Spark 的 `GeometryType$.apply(crs)` 和 `GeographyType$.apply(crs, algorithm)` 构造 Spark 类型。通过 `convertAlgorithm()` 将 Iceberg 的 `EdgeAlgorithm` 转换为 Spark 的 `EdgeInterpolationAlgorithm`（仅支持 SPHERICAL）。
2. **SparkTypeToType（Spark → Iceberg）**：新增对 Spark `GeometryType`/`GeographyType` 的识别，转换为 Iceberg 类型。检查 `isMixedSrid()` 并拒绝。通过 `convertAlgorithm()` 反向转换算法。
3. **PruneColumnsWithoutReordering**：在列裁剪时验证请求的 geo 类型与表类型兼容（CRS 和算法一致），并将 geo 类型注册到类型兼容性映射中。
4. **测试**：覆盖类型往返转换、不支持的 CRS/算法拒绝、mixed SRID 拒绝、列裁剪兼容性检查。

## 修改详情

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/PruneColumnsWithoutReordering.java` (+32/-0 lines)

**修改目的**：在列裁剪时验证 geo 类型兼容性。

**工作逻辑**：
- GEOMETRY 分支：检查请求的 CRS 与表 CRS 大小写不敏感相等。
- GEOGRAPHY 分支：检查 CRS 和算法兼容。算法比较需要先将 Iceberg 的 `EdgeAlgorithm` 转换为 Spark 的 `EdgeInterpolationAlgorithm`（因为两者枚举不同）。
- 在类型兼容性映射中注册：`TypeID.GEOMETRY → GeometryType.class`，`TypeID.GEOGRAPHY → GeographyType.class`。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/SparkTypeToType.java` (+33/-0 lines)

**修改目的**：Spark geo 类型 → Iceberg geo 类型。

**工作逻辑**：
```java
} else if (atomic instanceof GeometryType) {
  GeometryType geometry = (GeometryType) atomic;
  if (geometry.isMixedSrid()) {
    throw new UnsupportedOperationException("Cannot convert Spark geometry with mixed SRID to Iceberg");
  }
  return Types.GeometryType.of(geometry.crs());
} else if (atomic instanceof GeographyType) {
  // 类似，含算法转换
  return Types.GeographyType.of(geography.crs(), convertAlgorithm(geography.algorithm()));
}
```
`convertAlgorithm` 通过名称大写匹配，仅支持 SPHERICAL，其他抛出异常。

### `spark/v4.1/spark/src/main/java/org/apache/iceberg/spark/TypeToSparkType.java` (+38/-0 lines)

**修改目的**：Iceberg geo 类型 → Spark geo 类型。

**工作逻辑**：
```java
case GEOMETRY:
  return geometryType((Types.GeometryType) primitive);  // GeometryType$.apply(crs)
case GEOGRAPHY:
  return geographyType((Types.GeographyType) primitive);  // GeographyType$.apply(crs, algorithm)
```
`convertAlgorithm(EdgeAlgorithm)` 仅支持 SPHERICAL → `SPHERICAL$.MODULE$`，其他抛出异常。该方法为包级可见，被 `PruneColumnsWithoutReordering` 复用。

### `spark/v4.1/spark/src/test/java/org/apache/iceberg/spark/TestSparkSchemaUtil.java` (+123/-0 lines)

**修改目的**：验证 geo 类型双向转换和边界情况。

**工作逻辑**：新增多个测试：
- `testGeospatialTypeConversion`：默认 CRS 和自定义 CRS 的往返转换；不支持算法（VINCENTY）的拒绝。
- `testGeospatialCrsUnsupportedBySparkIsRejected`：Spark 不认识的 CRS（EPSG:4269）被拒绝。
- `testGeospatialMixedSridIsRejected`：mixed SRID（"ANY"）被拒绝。
- `testPruneGeospatialTypes`：geo 类型的列裁剪保持类型不变。
- `testPruneGeospatialTypeWithIncompatibleRequestedType`：请求非 geo 类型（BinaryType）被拒绝。
- `testPruneGeospatialTypeWithIncompatibleCrs`：请求的 CRS 与表 CRS 不匹配被拒绝。

## 总结

本提交实现了 Iceberg 地理空间类型与 Spark 4.1 原生地理空间类型的完整双向映射，包括 CRS 和边缘算法的转换。设计上谨慎处理了 Spark 的限制（仅支持 spherical 算法、不支持 mixed SRID），对不兼容的情况抛出清晰异常而非静默降级。这使得 Iceberg 表的 geometry/geography 列能在 Spark 4.1 中以原生类型使用。
