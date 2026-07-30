# 提交 2881：API: Add geospatial bounding box types and implement intersects checking (#12667)

## 提交信息

- **序号**：2881 / 4088
- **哈希**：8de302bf48a57ea7220c613d0526e01a66b3aab7
- **短哈希**：8de302bf4
- **日期**：2025-11-17 10:31:33 -0800
- **作者**：Kristin Cowalcijk
- **提交说明**：API: Add geospatial bounding box types and implement intersects checking (#12667)
- **PR/Issue**：#12667

## 总体目的

Iceberg 规范已经引入了地理空间类型 `geometry` 和 `geography`（用于存储 WKB 格式的几何/地理数据）。为了支持基于地理空间列的数据跳过（data skipping），需要能够对地理空间列的边界值（bounds）进行比较和过滤。此提交是地理空间功能的基础设施层，引入了三个核心类：

1. **`GeospatialBound`**：表示地理空间的一个边界点（最小或最大），支持 X、Y、Z、M 坐标维度，按照 Iceberg 规范的 bound 序列化格式进行序列化/反序列化。

2. **`BoundingBox`**：表示地理空间边界框（Minimum Bounding Rectangle, MBR），由最小和最大两个 `GeospatialBound` 组成，提供序列化/反序列化支持。

3. **`GeospatialPredicateEvaluators`**：提供边界框相交（intersects）判断的评估器，区分 `Geometry`（笛卡尔坐标，无环绕）和 `Geography`（球面坐标，支持反子午线环绕）两种类型。

这些类型是地理空间数据跳过的核心基础，使得引擎可以利用清单文件中的边界统计信息快速过滤不相关的数据文件。同时，此提交还修复了 `Types.java` 中 `GeometryType` 和 `GeographyType` 的 `crs()` 和 `algorithm()` 方法在值为 null 时不返回默认值的问题，并移除了 CRS 中不允许逗号的限制。

## 如何达成设计目的

整体设计分为三个层次：

1. **边界点表示（GeospatialBound）**：按照 Iceberg 规范的 bound 序列化规则，地理空间边界不是 WKB 格式，而是简单的坐标值拼接。根据是否存在 Z 和 M 坐标，序列化格式有四种：`x:y`（2 doubles）、`x:y:z`（3 doubles）、`x:y:NaN:m`（4 doubles，z 为 NaN 占位）、`x:y:z:m`（4 doubles）。通过工厂方法 `createXY`、`createXYZ`、`createXYM`、`createXYZM` 创建不同维度的边界点。

2. **边界框表示（BoundingBox）**：由 min 和 max 两个 `GeospatialBound` 组成。序列化格式为先写入 min 的长度和内容，再写入 max 的长度和内容，全部使用小端字节序。

3. **相交判断（GeospatialPredicateEvaluators）**：
   - `GeometryEvaluator`：适用于笛卡尔坐标系（geometry 类型），X 维度不涉及环绕，直接使用区间相交判断。
   - `GeographyEvaluator`：适用于球面坐标系（geography 类型），X 维度（经度）支持反子午线环绕（antimeridian crossing），当 `min > max` 时表示边界框跨越 ±180° 经线。Y 维度（纬度）限制在 [-90°, 90°]，X 维度限制在 [-180°, 180°]。
   - 两者共享 Y、Z、M 维度的相交判断逻辑（`intersectsYZM`），这些维度不涉及环绕。

## 修改详情

### `api/src/main/java/org/apache/iceberg/geospatial/GeospatialBound.java` (+324/-0 lines, 新文件)

**修改目的**：表示地理空间边界点，支持序列化/反序列化。

**工作逻辑**：
- 内部存储 x、y、z、m 四个 double 值，z/m 为 NaN 表示未设置。
- `fromByteBuffer()` 根据缓冲区大小（16/24/32 字节）判断坐标维度格式并解析。
- `toByteBuffer()` 根据 hasZ()/hasM() 决定序列化格式：无 Z 无 M 写 x:y；有 Z 无 M 写 x:y:z；有 M（无论 Z 是否为 NaN）写 x:y:z:m（z 可能为 NaN 占位）。
- 提供工厂方法 `createXY`、`createXYZ`、`createXYM`、`createXYZM`。
- `hasZ()`/`hasM()` 通过 `!Double.isNaN()` 判断坐标是否存在。

### `api/src/main/java/org/apache/iceberg/geospatial/BoundingBox.java` (+148/-0 lines, 新文件)

**修改目的**：表示地理空间边界框（MBR）。

**工作逻辑**：
- 由 `GeospatialBound min` 和 `GeospatialBound max` 组成。
- `fromByteBuffer()` 依次读取 min 长度、min 数据、max 长度、max 数据，校验长度必须为 16/24/32 字节。
- `toByteBuffer()` 将 min 和 max 的序列化结果按 `[minLen][minData][maxLen][maxData]` 格式拼接，全部小端序。
- 提供 `fromByteBuffers(min, max)` 直接从两个独立缓冲区构造。

### `api/src/main/java/org/apache/iceberg/geospatial/GeospatialPredicateEvaluators.java` (+241/-0 lines, 新文件)

**修改目的**：提供边界框相交判断评估器。

**工作逻辑**：
- `create(Type type)` 工厂方法根据类型 ID 创建 `GeometryEvaluator` 或 `GeographyEvaluator`。
- `GeometryEvaluator`：
  - 校验 `xmin <= xmax`、`ymin <= ymax`（无环绕）。
  - X 维度使用 `rangeIntersects(min1, max1, min2, max2)`：`min1 <= max2 && max1 >= min2`。
- `GeographyEvaluator`：
  - 校验纬度 [-90°, 90°]、经度 [-180°, 180°]，`ymin <= ymax`（纬度无环绕）。
  - X 维度使用 `rangeIntersectsWithWrapAround()`，处理四种情况：两者都不环绕（常规相交）、两者都环绕（必相交）、仅一方环绕（`min1 <= max2 || max1 >= min2`）。
- 共享 `intersectsYZM()`：检查 Y、Z、M 维度的常规区间相交，仅当双方都拥有对应维度坐标时才检查。

### `api/src/main/java/org/apache/iceberg/types/Types.java` (+4/-4 lines)

**修改目的**：修复 GeometryType 和 GeographyType 的默认值返回问题。

**工作逻辑**：
- `GeometryType.crs()`：从 `return crs` 改为 `return crs != null ? crs : DEFAULT_CRS`，当 crs 为 null 时返回默认 CRS。
- `GeographyType.crs()`：同上修复。
- `GeographyType.algorithm()`：从 `return algorithm` 改为 `return algorithm != null ? algorithm : DEFAULT_ALGORITHM`。
- 新增 `GeographyType.DEFAULT_ALGORITHM = EdgeAlgorithm.SPHERICAL` 常量。
- 移除 `GeometryType` 解析中对 CRS 含逗号的校验（`Preconditions.checkArgument(!crs.contains(","), ...)`），允许 CRS 字符串中包含逗号。

### 测试文件

- `TestBoundingBox.java`（+138 lines）：测试 BoundingBox 的序列化/反序列化往返。
- `TestGeospatialBound.java`（+248 lines）：测试 GeospatialBound 各种坐标维度格式的序列化/反序列化。
- `TestGeospatialPredicateEvaluators.java`（+545 lines）：测试 Geometry 和 Geography 评估器的相交判断，包括环绕场景。
- `TestTypes.java`（+5/-2 lines）：测试 Types 修改后的行为。

## 总结

该提交是 Iceberg 地理空间功能的重要基础设施，引入了 `GeospatialBound`、`BoundingBox` 和 `GeospatialPredicateEvaluators` 三个核心类，为地理空间列的数据跳过提供了类型支持和相交判断能力。关键设计包括：按 Iceberg 规范的坐标序列化格式（区分 XY/XYZ/XYM/XYZM）、Geometry 和 Geography 两种评估器（后者支持反子午线环绕）、以及 Types 中默认值返回的修复。这些类型位于 `api` 模块，为后续在核心模块和引擎集成中实现地理空间数据跳过奠定了基础。这是一个 1654 行新增的大型功能提交。
