# 提交 3844：Core: Align content stats fields with latest Spec changes (#16439)

## 提交信息

- **序号**：3844 / 4088
- **哈希**：d2de290441b0dbaab7cb00e14cd446be6ef47120
- **短哈希**：d2de29044
- **日期**：2026-06-08 14:12:53 -0700
- **作者**：Eduard Tudenhoefner
- **提交说明**：Core: Align content stats fields with latest Spec changes (#16439)
- **PR/Issue**：#16439

## 总体目的

本提交将 Iceberg 核心模块中的内容统计（content stats）字段结构与最新的 Iceberg 规范变更对齐。这是一个重要的规范对齐工作，涉及字段重命名、字段顺序调整、新增地理和 Variant 类型支持等多个方面。

主要变更包括：
1. 将 `exact_bounds` 重命名为 `tight_bounds`，语义更准确（表示边界是否是精确的最小/最大值）。
2. 移除 `max_value_size` 字段（规范不再要求），将 `avg_value_size` 重命名为 `avg_value_size_in_bytes`，语义更清晰。
3. 调整字段顺序：将 `lower_bound`、`upper_bound`、`tight_bounds` 移到前面，计数类字段移到后面。
4. 为 Geometry/Geography 类型新增基于 XYZM 边界框（bounding box）的统计支持，使用专门的 `geo_lower` / `geo_upper` struct 类型存储。
5. 为 Variant 类型支持统计，边界存储为未分片（unshredded）的 Variant 值。
6. 修复 `StatsUtil` 中统计字段名使用列名（如 `"i"`、`"list.element"`）而非字段 ID 字符串（如 `"0"`、`"3"`），确保字段名唯一且可读。

这些变更使 Iceberg 实现与规范的字段 ID 和命名约定保持一致，为后续多引擎互操作奠定基础。

## 如何达成设计目的

整体设计分四层：

1. **`FieldStatistic` 枚举**：重新定义字段偏移量顺序（LOWER_BOUND=1, UPPER_BOUND=2, TIGHT_BOUNDS=3, VALUE_COUNT=4, NULL_VALUE_COUNT=5, NAN_VALUE_COUNT=6, AVG_VALUE_SIZE_IN_BYTES=7），移除 MAX_VALUE_SIZE 和 EXACT_BOUNDS，新增 TIGHT_BOUNDS 和 AVG_VALUE_SIZE_IN_BYTES。新增地理边界 struct 的偏移量常量（GEO_LOWER_X_OFFSET=10 等）。

2. **`FieldStatistic.fieldStatsFor()`**：根据字段类型生成不同的统计 struct。对 geo 类型生成 XYZM 边界框 struct；对 variant 类型使用 VariantType 作为边界类型；其他类型使用字段自身类型。`tight_bounds` 字段仅在非 geo、非 variant 类型时添加。

3. **`BaseFieldStats` / `FieldStats` / `BaseContentStats`**：更新字段定义、构造器、Builder、`internalGet`、`toString`、`equals`、`hashCode` 等方法以匹配新字段结构。

4. **`StatsUtil`**：`ContentStatsSchemaVisitor` 新增 `tableSchema` 引用，使用 `tableSchema.findColumnName(field.fieldId())` 获取列全名（如 `"simple_struct.int"`）替换原来的 `Integer.toString(field.fieldId())`，确保统计 struct 字段名可读且唯一。

## 修改详情

### `core/src/main/java/org/apache/iceberg/BaseContentStats.java` (+42/-42 lines)

**修改目的**：适配新的字段顺序和命名。

**工作逻辑**：
从记录构建 `BaseFieldStats.Builder` 时，调整字段设置顺序：先设置 `lowerBound`/`upperBound`/`tightBounds`，再设置 `valueCount`/`nullValueCount`/`nanValueCount`/`avgValueSizeInBytes`。将 `EXACT_BOUNDS` 改为 `TIGHT_BOUNDS`，将 `AVG_VALUE_SIZE` 改为 `AVG_VALUE_SIZE_IN_BYTES`，移除 `MAX_VALUE_SIZE` 的处理。

### `core/src/main/java/org/apache/iceberg/BaseFieldStats.java` (+122/-122 lines)

**修改目的**：重构字段定义以匹配新规范。

**工作逻辑**：

1. 字段顺序调整：`lowerBound`、`upperBound`、`tightBounds` 移到前面，`valueCount`、`nullValueCount`、`nanValueCount`、`avgValueSizeInBytes` 移到后面。移除 `maxValueSize` 和 `hasExactBounds`，新增 `tightBounds` 和 `avgValueSizeInBytes`。

2. `internalGet` 方法中 `FieldStatistic.fromPosition(pos)` 的 switch 顺序调整匹配新偏移量：
```java
return switch (FieldStatistic.fromPosition(pos)) {
  case LOWER_BOUND -> javaClass.cast(lowerBound());
  case UPPER_BOUND -> javaClass.cast(upperBound());
  case TIGHT_BOUNDS -> javaClass.cast(tightBounds);
  case VALUE_COUNT -> javaClass.cast(valueCount);
  // ...
  case AVG_VALUE_SIZE_IN_BYTES -> javaClass.cast(avgValueSizeInBytes);
};
```

3. Builder 方法重命名：`avgValueSize` → `avgValueSizeInBytes`，`hasExactBounds` → `tightBounds`，移除 `maxValueSize`。

### `core/src/main/java/org/apache/iceberg/FieldStatistic.java` (+150/-50 lines)

**修改目的**：重新定义字段枚举和统计 struct 生成逻辑。

**工作逻辑**：

1. 枚举重定义：
```java
LOWER_BOUND(1, "lower_bound"),
UPPER_BOUND(2, "upper_bound"),
TIGHT_BOUNDS(3, "tight_bounds"),
VALUE_COUNT(4, "value_count"),
NULL_VALUE_COUNT(5, "null_value_count"),
NAN_VALUE_COUNT(6, "nan_value_count"),
AVG_VALUE_SIZE_IN_BYTES(7, "avg_value_size_in_bytes");
```

2. 新增地理边界偏移量常量（GEO_LOWER_X/Y/Z/M_OFFSET = 10-13，GEO_UPPER_X/Y/Z/M_OFFSET = 14-17）。

3. `fieldStatsFor()` 方法重写：根据 `isGeo` 和 `isVariant` 标志生成不同的边界类型和条件字段。geo 类型使用 `geoLowerBoundStruct()` / `geoUpperBoundStruct()` 生成 XYZM struct，且不包含 `tight_bounds`。variant 类型使用 VariantType 作为边界类型，且不包含 `tight_bounds` 但包含 `avg_value_size_in_bytes`。

4. 新增 `geoLowerBoundStruct(int baseFieldId)` 和 `geoUpperBoundStruct(int baseFieldId)` 方法，生成包含 x（必填，经度）、y（必填，纬度）、z（可选，高度）、m（可选，测量值）四个 Double 字段的 struct。

### `core/src/main/java/org/apache/iceberg/FieldStats.java` (+39/-39 lines)

**修改目的**：更新接口方法签名。

**工作逻辑**：
将 `avgValueSize()` 和 `maxValueSize()` 替换为 `avgValueSizeInBytes()`，将 `hasExactBounds()` 替换为 `tightBounds()`。调整方法顺序，新增 `lowerBound()`、`upperBound()`、`tightBounds()` 的 Javadoc。

### `core/src/main/java/org/apache/iceberg/StatsUtil.java` (+12/-12 lines)

**修改目的**：使用列全名而非字段 ID 字符串作为统计字段名。

**工作逻辑**：
`ContentStatsSchemaVisitor` 新增 `tableSchema` 字段和构造器参数。在 `field()` 方法中：
```java
String fullName = tableSchema.findColumnName(field.fieldId());
return optional(fieldId, fullName, structType);
```
同时移除了对 variant 类型的特殊跳过逻辑（variant 现在可以生成统计）。

### `core/src/test/java/org/apache/iceberg/TestContentStats.java` (+157/-2 lines)

**修改目的**：更新测试并新增 geo/variant 类型测试。

**工作逻辑**：
更新现有测试中的字段名和 Builder 方法调用。新增三个测试：
- `setByPositionOptionalGeometry`：测试 Geometry 类型的 XYZM 边界框统计。
- `setByPositionOptionalGeography`：测试 Geography 类型的 XYZM 边界框统计。
- `setByPositionRequiredVariant`：测试 Variant 类型的边界统计和 `avg_value_size_in_bytes`。

### `core/src/test/java/org/apache/iceberg/TestFieldStats.java` (+57/-57 lines)

**修改目的**：更新测试以匹配新字段结构。

**工作逻辑**：
将所有 `avgValueSize()` → `avgValueSizeInBytes()`，`maxValueSize()` → 移除，`hasExactBounds()` → `tightBounds()`。调整 `get()` 调用的 position 顺序。数组长度从 8 改为 7。

### `core/src/test/java/org/apache/iceberg/TestStatsUtil.java` (+275/-5 lines)

**修改目的**：更新统计 schema 测试并新增 geo/variant 条件包含测试。

**工作逻辑**：
1. 更新 `contentStatsForOptionalAndNestedFields` 测试：统计字段名从 ID 字符串（`"0"`、`"3"`）改为列全名（`"i"`、`"list.element"`、`"simple_struct.int"` 等）。新增 variant 字段的统计验证。

2. 新增 `contentStatsChildNamesAreUnique` 测试：验证嵌套结构中同名字段（如两个 struct 都有 `x` 字段）的统计名唯一（`"a.x"`、`"b.x"`）。

3. 新增 `conditionalFieldInclusionForGeometry` / `conditionalFieldInclusionForGeography` / `conditionalFieldInclusionForVariant` 测试：验证 geo 类型生成 XYZM 边界框 struct 且不含 `tight_bounds`；variant 类型边界使用 VariantType 且含 `avg_value_size_in_bytes`。

4. 新增 `assertGeoBoundStructs` 和 `assertVariantBoundTypes` 辅助方法验证 struct 结构。

### `core/src/test/java/org/apache/iceberg/TestTrackedFileStruct.java` (+20/-20 lines)

**修改目的**：更新期望的统计 struct 字段顺序和 ID。

**工作逻辑**：
将期望的 struct 字段顺序从 `value_count(10001), null_value_count(10002), nan_value_count(10003), lower_bound(10006), upper_bound(10007)` 调整为 `lower_bound(10001), upper_bound(10002), value_count(10004), null_value_count(10005), nan_value_count(10006)`。

## 总结

本提交是一个重要的规范对齐工作，涉及字段重命名、顺序调整、geo 和 variant 类型支持等多个方面。`exact_bounds` → `tight_bounds` 的重命名更准确地表达了语义，移除 `max_value_size` 简化了结构，新增的 geo XYZM 边界框和 variant 边界支持扩展了统计能力。使用列全名替换字段 ID 字符串提升了可读性和唯一性。这是一个破坏性变更（字段 ID 和顺序变化），但因为是与规范对齐的必要工作，对 Iceberg 的长期互操作性至关重要。
