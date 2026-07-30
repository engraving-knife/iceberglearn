# 提交 3956：API, Parquet: Map geometry and geography to Parquet logical types (#16765)

## 提交信息

- **序号**：3956 / 4088
- **哈希**：8e46f49123f81fc5d8c87dbbdaa6eb5ea103cb86
- **短哈希**：8e46f4912
- **日期**：2026-06-26 16:43:56 -0700
- **作者**：Xin Huang
- **提交说明**：API, Parquet: Map geometry and geography to Parquet logical types (#16765)
- **PR/Issue**：#16765

## 总体目的

本提交为 Iceberg 的几何（geometry）和地理（geography）类型建立与 Parquet 逻辑类型的映射关系。这是 Iceberg 对地理空间数据类型支持的延续性工作，使得 Iceberg 表中的 geometry/geography 字段能够正确地序列化为 Parquet 文件中的逻辑类型注解，并能在读取时还原回 Iceberg 类型。

在之前的实现中，`GeometryType` 和 `GeographyType` 在内部使用 `null` 表示"使用默认值"，导致类型字符串表示不统一（有时是 `geometry`，有时是 `geometry(OGC:CRS84)`）、equals/hashCode 行为不一致、以及默认值与显式默认值之间比较结果不直观等问题。本提交同时重构了这些类型，使默认值规范化（canonicalize）为显式的默认 CRS 和算法，简化了后续的映射工作。

此外，本提交还完善了类型的相等性语义：CRS 比较改为大小写不敏感（`OGC:CRS84` 与 `ogc:crs84` 视为相等），但 `toString()` 保留原始大小写。这使得用户在不同大小写约定下使用 CRS 时不会产生意外的类型不匹配。

## 如何达成设计目的

设计上分为三个层面：

1. **类型规范化**：在 `Types.java` 中，将 `GeometryType` 和 `GeographyType` 的构造逻辑改为：省略参数时直接赋值为默认值（而非 `null`），从而消除 `null` 与默认值的二义性。同时引入 `NAME` 常量统一类型名。

2. **Parquet 映射**：在 `TypeToMessageType`（Iceberg → Parquet）中新增 `GEOMETRY` 和 `GEOGRAPHY` 分支，将它们映射为带逻辑类型注解的 `BINARY` 类型；在 `MessageTypeToType`（Parquet → Iceberg）中新增对应的 visitor 方法，从 Parquet 注解还原 Iceberg 类型。

3. **写入保护**：在 `BaseParquetWriter` 中对 geometry/geography 抛出 `UnsupportedOperationException`，因为实际的地理空间值写入路径尚未实现（注释中明确说明这是后续工作）。这样可以防止这些类型静默地走通用 binary writer，避免写入错误数据。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Types.java` (+45/-19 lines)

**修改目的**：规范化 geometry/geography 类型的默认值处理，并统一大小写不敏感的 CRS 比较。

**工作逻辑**：
- `TYPES` 静态映射表中的 key 从 `GeometryType.crs84().toString()` 改为 `GeometryType.NAME`，避免依赖 toString 输出。
- `GeometryType` 构造函数中，`crs == null` 时设为 `DEFAULT_CRS`（"OGC:CRS84"），而非保留 `null`。`crs()` 方法直接返回字段值。
- `equals()` 改为 `crs.equalsIgnoreCase(that.crs)`，`hashCode()` 使用 `crs.toUpperCase(Locale.ROOT)` 保持与 equals 一致。
- `toString()` 统一为 `String.format("%s(%s)", NAME, crs())`，始终带参数。
- `GeographyType` 同理处理 crs 和 algorithm 的规范化。
- 移除了 `toString()` 中针对 null 的多个条件分支。

### `api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java` (+6/-2 lines)

**修改目的**：更新分区验证测试中的期望错误消息，以匹配新的 toString 输出格式。

**工作逻辑**：将 `"Invalid source type geometry for transform: bucket[5]"` 更新为 `"Invalid source type geometry(OGC:CRS84) for transform: bucket[5]"`，geography 同理带上算法参数。

### `api/src/test/java/org/apache/iceberg/types/TestTypes.java` (+45/-1 lines)

**修改目的**：验证类型规范化和大小写不敏感比较的新行为。

**工作逻辑**：
- 更新 `testGeospatialTypeToString` 测试，断言 `crs84()` 的 toString 现在为 `"geometry(OGC:CRS84)"` 和 `"geography(OGC:CRS84, spherical)"`。
- 新增 `testGeospatialTypeDefaultNormalization` 测试，验证：省略默认值与显式默认值相等；CRS 大小写不敏感比较；hashCode 一致性；algorithm 默认值规范化。

### `parquet/src/main/java/org/apache/iceberg/data/parquet/BaseParquetWriter.java` (+16/-0 lines)

**修改目的**：阻止 geometry/geography 值被写入 Parquet，因为值写入路径尚未实现。

**工作逻辑**：
```java
@Override
public Optional<ParquetValueWriter<?>> visit(
    LogicalTypeAnnotation.GeometryLogicalTypeAnnotation geometryType) {
  throw new UnsupportedOperationException("Cannot write geometry value to Parquet");
}
```
对 geography 同样处理。注释说明这是为了防止静默走通用 binary writer。

### `parquet/src/main/java/org/apache/iceberg/parquet/MessageTypeToType.java` (+19/-0 lines)

**修改目的**：将 Parquet 的 geometry/geography 逻辑类型注解转换为 Iceberg 类型。

**工作逻辑**：
```java
@Override
public Optional<Type> visit(LogicalTypeAnnotation.GeometryLogicalTypeAnnotation geometryType) {
  return Optional.of(Types.GeometryType.of(geometryType.getCrs()));
}
```
对 geography，额外将 Parquet 的 `EdgeInterpolationAlgorithm` 转换为 Iceberg 的 `EdgeAlgorithm`（通过 name 映射）。null crs/algorithm 由 `GeometryType.of` / `GeographyType.of` 处理为默认值。

### `parquet/src/main/java/org/apache/iceberg/parquet/TypeToMessageType.java` (+26/-0 lines)

**修改目的**：将 Iceberg 的 geometry/geography 类型转换为 Parquet 的 BINARY + 逻辑类型注解。

**工作逻辑**：
```java
case GEOMETRY:
  GeometryType geometry = (GeometryType) primitive;
  return Types.primitive(BINARY, repetition)
      .as(LogicalTypeAnnotation.geometryType(geometry.crs()))
      .id(id)
      .named(name);
```
geography 类似，并通过私有方法 `toParquet(EdgeAlgorithm)` 将 Iceberg 算法枚举转换为 Parquet 算法枚举（两者名称相同，通过 `valueOf(name)` 映射）。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetDataWriter.java` (+34/-0 lines)

**修改目的**：验证写入 geometry/geography 数据时抛出异常。

**工作逻辑**：新增 `testGeospatialWriteIsRejected` 测试，分别构造包含 geometry 和 geography 字段的 schema，断言 `Parquet.writeData(...).build()` 抛出 `UnsupportedOperationException`，消息为 "Cannot write geometry/geography value to Parquet"。

### `parquet/src/test/java/org/apache/iceberg/parquet/TestParquetSchemaUtil.java` (+88/-0 lines)

**修改目的**：验证 schema 在 Iceberg 与 Parquet 之间双向转换的正确性。

**工作逻辑**：
- `testGeospatialTypeRoundTrip`：构造包含默认和自定义 CRS 的 geometry/geography 字段（覆盖所有 EdgeAlgorithm），通过 `ParquetSchemaUtil.convert` 双向转换，断言 schema 一致。
- `testGeospatialAnnotationsWithOmittedParameters`：构造省略 CRS/algorithm 参数的 Parquet MessageType，验证转换后得到 Iceberg 默认值；同时验证显式默认值被保留，并检查 toString 输出。

## 总结

本提交为 Iceberg 地理空间类型支持奠定了 Parquet 层面的基础：实现了 schema 层面的双向映射，使得 geometry/geography 类型能够在 Parquet 文件中被正确标注和还原。同时通过类型规范化重构，解决了默认值处理的二义性和大小写敏感性问题，提升了类型的健壮性。

值得注意的是，本提交仅完成了 schema 映射，值（value）的读写路径尚未实现——写入会显式抛出异常以防止错误数据产生。这表明地理空间值读写是后续提交（如 #16982）的工作内容。
