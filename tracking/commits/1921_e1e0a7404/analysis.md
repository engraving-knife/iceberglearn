# 提交 1921：API, Core: Add geometry and geography types support (#12346)

## 提交信息

- **序号**：1921 / 4088
- **哈希**：e1e0a7404740b2bf9e6638afb5f0ff19f2536713
- **短哈希**：e1e0a7404
- **日期**：2025-03-25 16:09:02 -0700
- **作者**：Kristin Cowalcijk
- **提交说明**：API, Core: Add geometry and geography types support (#12346)
- **PR/Issue**：#12346

## 总体目的

Iceberg v3 规范引入了对地理空间（geospatial）类型的支持。本提交在 API 与 Core 模块中新增两种原始类型：

1. **`geometry`**：几何类型，表示平面坐标系下的几何对象（点/线/面等），底层以 WKB（Well-Known Binary）字节存储。可带一个 CRS（坐标参考系统）参数，如 `geometry(srid:3857)`，默认 `OGC:CRS84`。
2. **`geography`**：地理类型，表示椭球面上的地理对象（经纬度），底层同样以字节存储。除 CRS 外还可带一个边插值算法参数 `EdgeAlgorithm`（如 `geography(srid:4269, karney)`），用于定义在椭球面上如何插值边（测地线）。

这两种类型属于 v3 格式特性，需要 `format-version=3`。本提交实现类型系统层面的支持：新增 `Type.TypeID`、`Types.GeometryType`/`Types.GeographyType` 类、CRS 与算法的解析/序列化、类型字符串解析、字段大小估算、以及在分区/排序/bucket 等 transform 中明确把它们标记为不支持（与 variant 类似，因为无法直接比较/哈希）。

## 如何达成设计目的

1. **类型 ID**：在 `Type.TypeID` 枚举中加入 `GEOMETRY(ByteBuffer.class)` 与 `GEOGRAPHY(ByteBuffer.class)`，二者都映射到 `ByteBuffer` 作为 Java 表示。
2. **类型类**：在 `Types` 中新增 `GeometryType` 与 `GeographyType` 两个 `PrimitiveType` 子类：
   - `GeometryType`：持有可选 `crs`（为 `OGC:CRS84` 时归一化为 null），`toString()` 为 `geometry` 或 `geometry(crs)`。
   - `GeographyType`：持有可选 `crs` 与可选 `EdgeAlgorithm`，`toString()` 为 `geography` / `geography(crs)` / `geography(crs, algorithm)`。
   - 两者都实现 `equals/hashCode` 基于 crs（与 algorithm），并提供 `crs84()` 默认实例与 `of(...)` 工厂。
3. **边算法枚举**：新增 `EdgeAlgorithm` 枚举（`SPHERICAL`、`VINCENTY`、`THOMAS`、`ANDOYER`、`KARNEY`），带 `fromName(String)` 解析（大小写不敏感）与 `toString()`（小写）。
4. **类型字符串解析**：在 `Types.fromPrimitiveString`/`fromTypeName` 中加入两个正则 `GEOMETRY_PARAMETERS` 与 `GEOGRAPHY_PARAMETERS`，解析 `geometry(crs)` 与 `geography(crs, algorithm)` 形式，并把默认实例注册到 `TYPES` map。
5. **Schema 格式版本**：在 `Schema.LOWER_CASE_TYPE_IDS_WITH_3`（要求 format-version=3 的类型集合）中加入 `GEOMETRY` 与 `GEOGRAPHY`。
6. **Transform 限制**：在 `Identity` transform 中把 `GEOMETRY`/`GEOGRAPHY` 加入 `UNSUPPORTED_TYPES`（与 `VARIANT` 一致），`canTransform` 返回 false，`get(type)` 校验拒绝。bucket transform 通过 `canTransform` / `bind` 同样拒绝（由既有 `Primitive` 校验路径覆盖，并补充测试）。
7. **字段大小估算**：`TypeUtil` 中给 `GEOMETRY`/`GEOGRAPHY` 估算 80 字节（约 4-5 个坐标的多边形/线串），用于统计等。
8. **SchemaParser 清理**：删除冗余的 `toJson(Type.PrimitiveType, JsonGenerator)` 重载，统一由 `toJson(Type, JsonGenerator)` 通过 `toString()` 处理原始类型（新类型也是原始类型，走同一路径）。
9. **测试**：新增 `TestGeospatialTable`（用 v3 format 创建含 geometry/geography 列的表并校验类型与参数持久化）、扩展 `TestTypes`（类型字符串解析与 toString）、`TestBucketing`（不支持 bucket）、`TestIdentity`、`TestPartitionSpecValidation`（参数化不支持分区）、`TestSortOrder`（不支持 identity 排序）、`DataTest`（加 `supportsGeospatial()` 开关，默认 false 跳过几何类型的读写测试）、`TestSchemaParser`（开启 `supportsGeospatial()`）。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/EdgeAlgorithm.java` (新增, +61 lines)

**修改目的**：定义 geography 类型的边插值算法枚举。

**工作逻辑**：枚举值 `SPHERICAL`（球面测地线）、`VINCENTY`、`THOMAS`、`ANDOYER`、`KARNEY`（GeographicLib 算法）。`fromName(String)` 大小写不敏感解析，非法值抛 `IllegalArgumentException`；`toString()` 返回小写名。

### `api/src/main/java/org/apache/iceberg/types/Type.java` (修改, +2 lines)

**修改目的**：新增两个类型 ID。

**工作逻辑**：`TypeID` 枚举加入 `GEOMETRY(ByteBuffer.class)` 与 `GEOGRAPHY(ByteBuffer.class)`，Java 类型均为 `ByteBuffer`。

### `api/src/main/java/org/apache/iceberg/types/Types.java` (修改, +152 lines)

**修改目的**：实现两个地理空间原始类型与字符串解析。

**工作逻辑**：

- `TYPES` map 注册 `GeometryType.crs84()` 与 `GeographyType.crs84()` 默认实例。
- 新增两个正则常量 `GEOMETRY_PARAMETERS`、`GEOGRAPHY_PARAMETERS`（大小写不敏感，允许灵活空格）。
- `fromPrimitiveString` 中先尝试匹配 geometry/geography，提取 crs 与 algorithm，构造对应类型；crs 含逗号则报错（避免与 algorithm 分隔混淆）。
- `GeometryType`：`DEFAULT_CRS = "OGC:CRS84"`；`crs84()` 返回无参实例；`of(crs)` 校验非空，`OGC:CRS84` 归一化为 null；`typeId()` 返回 `GEOMETRY`；`toString()` 为 `geometry` 或 `geometry(crs)`。
- `GeographyType`：类似，额外持有 `EdgeAlgorithm algorithm`；`of(crs)`、`of(crs, algorithm)` 工厂；`toString()` 根据 crs/algorithm 是否存在输出 `geography` / `geography(crs)` / `geography(crs, algorithm)`，algorithm 存在但 crs 为 null 时输出 `OGC:CRS84`。

### `api/src/main/java/org/apache/iceberg/Schema.java` (修改, +2 lines)

**修改目的**：将两种地理类型标记为需要 format-version=3。

**工作逻辑**：`LOWER_CASE_TYPE_IDS_WITH_3` map 加入 `Type.TypeID.GEOMETRY, 3` 与 `Type.TypeID.GEOGRAPHY, 3`，使 schema 校验时遇到这些类型要求表格式版本为 3。

### `api/src/main/java/org/apache/iceberg/transforms/Identity.java` (修改, +8 lines)

**修改目的**：禁止 geometry/geography 用作 identity 分区/排序 transform。

**工作逻辑**：新增 `UNSUPPORTED_TYPES = {VARIANT, GEOMETRY, GEOGRAPHY}`；`Identity.get(type)` 校验改为 `!UNSUPPORTED_TYPES.contains(type.typeId())`；`canTransform` 对这些类型返回 false。原因：地理空间对象无法直接比较/排序。

### `api/src/main/java/org/apache/iceberg/types/TypeUtil.java` (修改, +6 lines)

**修改目的**：估算地理类型字段大小。

**工作逻辑**：在 `estimateSize`（或同类方法）的 switch 中，`GEOMETRY`/`GEOGRAPHY` 返回 80 字节（约 4-5 个坐标的多边形/线串的近似大小），用于统计与布局估算。

### `core/src/main/java/org/apache/iceberg/SchemaParser.java` (修改, +0/-4 lines)

**修改目的**：清理冗余的原始类型序列化重载。

**工作逻辑**：删除 `static void toJson(Type.PrimitiveType primitive, JsonGenerator generator)`，统一由 `toJson(Type, JsonGenerator)` 处理——该路径对原始类型（含新加的 geometry/geography）通过 `type.toString()` 写为字符串。

### `core/src/test/java/org/apache/iceberg/TestGeospatialTable.java` (新增, +77 lines)

**修改目的**：端到端验证 v3 表创建与地理类型持久化。

**工作逻辑**：用 `InMemoryCatalog` 创建 `format-version=3` 的表，schema 含 `GeometryType.of("srid:3857")` 与 `GeographyType.of("srid:4269", EdgeAlgorithm.KARNEY)`，`catalog.createTable` 后 `loadTable` 校验字段 typeId、crs、algorithm 与设入一致。

### `api/src/test/java/org/apache/iceberg/types/TestTypes.java` (修改, +90 lines)

**修改目的**：验证类型字符串解析与 toString。

**工作逻辑**：扩展 `fromTypeName`/`fromPrimitiveString` 测试覆盖 `geometry`/`geometry(srid:3857)`/`geography`/`geography(srid:4269, karney)` 及各种空格变体；校验非法 CRS（空串、含逗号）与非法算法名抛 `IllegalArgumentException`；新增 `testGeospatialTypeToString` 校验 `toString()` 格式。

### `api/src/test/java/org/apache/iceberg/transforms/TestBucketing.java` (修改, +28 lines)

**修改目的**：验证 bucket transform 不支持地理类型。

**工作逻辑**：`testGeometryUnsupported`/`testGeographyUnsupported` 断言 `Transforms.bucket(type, 3)`、`bucket.bind(type)` 抛 `IllegalArgumentException` 且 `canTransform` 返回 false。

### `api/src/test/java/org/apache/iceberg/TestPartitionSpecValidation.java` (修改, +18/-13 lines)

**修改目的**：参数化验证分区 transform 不支持 variant/geometry/geography/unknown。

**工作逻辑**：把 `testVariantUnsupported`/`testUnknownUnsupported` 合并为参数化 `testUnsupported(fieldId, partitionName, expectedErrorMessage)`，数据源覆盖 variant（非原始类型）、geometry/geography（原始但 transform 不支持）、unknown。

### `core/src/test/java/org/apache/iceberg/TestSortOrder.java` (修改, +16 lines)

**修改目的**：验证 identity 排序不支持地理类型。

**工作逻辑**：`testGeospatialUnsupported` 断言对 geom/geog 列建 `SortOrder` 抛 `IllegalArgumentException` 含 "Unsupported type for identity"。

### `core/src/test/java/org/apache/iceberg/data/DataTest.java` (修改, +18 lines)

**修改目的**：为读写测试增加地理类型支持开关。

**工作逻辑**：`SIMPLE_TYPES` 加入 5 个地理类型实例；新增 `protected boolean supportsGeospatial()` 默认 false；`testTypeSchema` 中若 `supportsGeospatial()` 为 false，则用 `Assumptions.assumeThat` 跳过含 geometry/geography 的用例（标记 "not yet implemented"），子类（如 `TestSchemaParser`）覆写为 true 以启用。

### 其他测试文件 (修改)

`TestSchemaParser`（覆写 `supportsGeospatial()` 为 true）、`TestSchemaUnionByFieldName`、`TestSchemaUpdate`、`TestSerializableTypes`、`TestReadabilityChecks`、`TestTypeUtil`、`TestIdentity` 等同步加入地理类型用例或调整既有断言以兼容新类型。

## 总结

本提交为 Iceberg 在 API/Core 层引入 v3 规范的 `geometry` 与 `geography` 两种地理空间原始类型：新增 `EdgeAlgorithm` 枚举、`GeometryType`/`GeographyType` 类型类（带 CRS 与边算法参数）、类型字符串解析、format-version=3 约束、字段大小估算，并在 identity/bucket/分区/排序等 transform 中明确不支持这些类型（与 variant 一致）。同时清理 `SchemaParser` 冗余重载，并补充覆盖类型解析、表创建、分区/排序不支持、读写测试开关等的多项测试。
