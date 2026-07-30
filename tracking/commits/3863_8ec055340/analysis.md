# 提交 3863：API: Single-value binary serialization for geometry and geography (#16607)

## 提交信息

- **序号**：3863 / 4088
- **哈希**：8ec055340e0c886f286927bc5467d049995bd840
- **短哈希**：8ec055340
- **日期**：2026-06-11 16:45:14 -0700
- **作者**：Xin Huang
- **提交说明**：API: Single-value binary serialization for geometry and geography (#16607)
- **PR/Issue**：#16607

## 总体目的

本提交为 Iceberg 的 Geometry（几何）和 Geography（地理）类型实现了单值的二进制序列化和反序列化，集成到 `Conversions` 工具类中。

Iceberg 的 `Conversions` 类负责将各种类型的值与 `ByteBuffer` 之间进行转换，用于清单文件中的统计边界（lower/upper bounds）存储。在此提交之前，`Conversions` 已支持大多数 Iceberg 类型（String、Integer、Long、Decimal、Variant 等），但尚未支持 Geometry 和 Geography 类型。

随着 Iceberg V4 规范引入 Geometry/Geography 类型及其基于 XYZM 边界框的统计支持（见提交 3844），需要一种方式将这些边界值序列化为 ByteBuffer 存储在清单中。本提交通过 `GeospatialBound` 类实现 XYZM 点的二进制编码：X、Y、Z、M 各为 8 字节小端序 IEEE 754 double，按 X:Y[:Z][:M] 顺序拼接，其中 Z 或 M 未设置时省略（XYM 模式下 Z 位置填充 NaN）。

## 如何达成设计目的

在 `Conversions` 的 `toByteBuffer` 和 `fromByteBuffer` 方法中新增 `GEOMETRY` 和 `GEOGRAPHY` case 分支，委托给 `GeospatialBound` 类的 `toByteBuffer()` 和 `fromByteBuffer()` 方法。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Conversions.java` (+10/-0 lines)

**修改目的**：支持 Geometry/Geography 的 ByteBuffer 转换。

**工作逻辑**：

1. `toByteBuffer` 新增 case：
```java
case GEOMETRY:
case GEOGRAPHY:
  // Geometry and geography lower/upper bounds are single points encoded as an
  // x:y:z:m concatenation of 8-byte little-endian IEEE 754 doubles.
  return ((GeospatialBound) value).toByteBuffer();
```

2. `fromByteBuffer` 新增 case：
```java
case GEOMETRY:
case GEOGRAPHY:
  return GeospatialBound.fromByteBuffer(tmp);
```

### `api/src/test/java/org/apache/iceberg/types/TestConversions.java` (+51/-0 lines)

**修改目的**：测试 Geometry/Geography 的序列化。

**工作逻辑**：

新增两个测试：

1. `testByteBufferConversionsForGeometry`：验证四种 XYZM 组合的二进制编码：
   - `createXY(10, 13)` → 16 字节（X+Y）
   - `createXYZ(10, 13, 15)` → 24 字节（X+Y+Z）
   - `createXYM(10, 13, 20)` → 32 字节（X+Y+NaN+M，Z 位置填充 NaN）
   - `createXYZM(10, 13, 15, 20)` → 32 字节（X+Y+Z+M）

   同时验证非默认 CRS（如 EPSG:3857）不影响二进制编码。测试同时对 `GeometryType.crs84()` 和 `GeographyType.crs84()` 执行。

2. `testNullByteBufferConversionsForGeometry`：验证 null 值的处理契约——`toByteBuffer` 和 `fromByteBuffer` 对 null 返回 null。

## 总结

本提交为 Geometry 和 Geography 类型补齐了 `Conversions` 中的二进制序列化支持，使这些类型的边界值能正确存储在清单文件的统计中。编码格式遵循 Iceberg 规范的 XYZM 边界框编码（8 字节小端序 double 拼接），与提交 3844 中引入的 geo 统计 struct 结构对应。这是 Iceberg 地理空间类型支持的重要一环。
