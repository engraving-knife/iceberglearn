# 提交 2037：Core, Parquet: Add timestamp(9), time, and UUID types for Variant

## 提交信息

- **序号**：2037 / 4088
- **哈希**：137965d72d090b4268827607786ccd3c2925bbc5
- **短哈希**：137965d72
- **日期**：2025-04-24 15:06:35 -0700
- **作者**：Aihua Xu
- **提交说明**：Core, Parquet: Add timestamp(9), time, and UUID types for Variant (#12682)
- **PR/Issue**：#12682

## 总体目的

Iceberg 正在开发 Variant 类型支持（Variant 是一种灵活的半结构化数据类型，类似 JSON）。Variant 类型体系由 `PhysicalType` 枚举定义底层物理类型，并通过 `Primitives` 定义序列化时的类型标识。此前 Variant 已支持基本的类型（如 INT、LONG、FLOAT、DOUBLE、TIMESTAMPTZ、TIMESTAMPNTZ、STRING、BINARY 等），但还缺少三种重要类型：

1. **Time（时间类型）**：表示一天中的时间（无日期部分），以微秒为单位存储为 INT64。
2. **Timestamp with nanosecond precision（纳秒精度时间戳）**：包括 TIMESTAMPTZ_NANOS（带时区）和 TIMESTAMPNTZ_NANOS（不带时区），以纳秒为单位存储为 INT64。此前的 TIMESTAMPTZ/TIMESTAMPNTZ 只支持微秒精度。
3. **UUID 类型**：128 位唯一标识符，以 16 字节固定长度二进制存储。

这些类型在 Parquet shredding（将 Variant 字段拆解为 Parquet 的原生列存储以提升查询性能）场景中也需要支持，即需要能将 Parquet 的 `TimeLogicalTypeAnnotation`、`TimestampLogicalTypeAnnotation(NANOS)`、`UUIDLogicalTypeAnnotation` 正确映射到 Variant 的 PhysicalType，并能在读写器中处理这些类型。

## 如何达成设计目的

整体设计分为三层：

**1. API 层（类型定义）**：
- 在 `Primitives` 中新增 4 个类型常量（TYPE_TIME=17, TYPE_TIMESTAMPTZ_NANOS=18, TYPE_TIMESTAMPNTZ_NANOS=19, TYPE_UUID=20）
- 在 `LogicalType` 枚举中新增 TIME 和 UUID（TIMESTAMPTZ_NANOS 和 TIMESTAMPNTZ_NANOS 复用已有的 TIMESTAMPTZ/TIMESTAMPNTZ 逻辑类型）
- 在 `PhysicalType` 枚举中新增 4 个物理类型，并建立从 Primitives 类型 ID 到 PhysicalType 的映射

**2. Core 层（序列化与反序列化）**：
- `SerializedPrimitive`：支持从序列化字节中读取 TIME（INT64）、TIMESTAMPTZ_NANOS（INT64）、TIMESTAMPNTZ_NANOS（INT64）和 UUID（16 字节 ByteBuffer 转 UUID）
- `PrimitiveWrapper`：支持将这四种类型写入 Variant 二进制格式，计算 sizeInBytes 并正确序列化
- `Variants`：新增工厂方法 `ofTime`、`ofTimestamptzNanos`、`ofTimestampntzNanos`、`ofUUID`，以及从 ISO 字符串转换的便捷方法
- `ShreddedObject`：将字段名集合从 `HashSet` 改为 `TreeSet`，保证字段遍历顺序确定性

**3. Parquet 层（Shredding 适配）**：
- `ParquetVariantUtil`：新增 `convert(TimestampLogicalTypeAnnotation)` 方法处理纳秒时间戳转换；在 PhysicalType 到 Parquet Type 的映射中新增 TIME、TIMESTAMPTZ_NANOS、TIMESTAMPNTZ_NANOS、UUID 的对应关系；在 Parquet value 到 Java value 的转换中新增 UUID 处理
- `VariantReaderBuilder`：新增 `visit(TimeLogicalTypeAnnotation)` 和 `visit(UUIDLogicalTypeAnnotation)` 方法，修复 `visit(TimestampLogicalTypeAnnotation)` 以支持纳秒精度
- `VariantWriterBuilder`：启用此前被注释掉的 Time 和 UUID 写入支持，并将纳秒时间戳写入逻辑统一委托给 `ParquetVariantUtil.convert()`

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/Primitives.java` (修改, +4 lines)

**修改目的**：定义新的 Variant 原始类型 ID 常量。

**工作逻辑**：
新增 4 个常量：`TYPE_TIME = 17`、`TYPE_TIMESTAMPTZ_NANOS = 18`、`TYPE_TIMESTAMPNTZ_NANOS = 19`、`TYPE_UUID = 20`，延续已有的类型 ID 编号序列。

### `api/src/main/java/org/apache/iceberg/variants/LogicalType.java` (修改, +2 lines)

**修改目的**：在逻辑类型枚举中添加 TIME 和 UUID。

**工作逻辑**：
在 `LogicalType` 枚举中 STRING 之后新增 `TIME` 和 `UUID`。TIMESTAMPTZ_NANOS 和 TIMESTAMPNTZ_NANOS 复用已有的 TIMESTAMPTZ 和 TIMESTAMPNTZ 逻辑类型。

### `api/src/main/java/org/apache/iceberg/variants/PhysicalType.java` (修改, +12 lines)

**修改目的**：在物理类型枚举中新增 4 种类型并建立映射。

**工作逻辑**：
新增 4 个枚举值：
- `TIME(LogicalType.TIME, Long.class)` - 以 Long 存储微秒
- `TIMESTAMPTZ_NANOS(LogicalType.TIMESTAMPTZ, Long.class)` - 以 Long 存储纳秒，带时区
- `TIMESTAMPNTZ_NANOS(LogicalType.TIMESTAMPNTZ, Long.class)` - 以 Long 存储纳秒，不带时区
- `UUID(LogicalType.UUID, String.class)` - 以 String 表示 UUID

在 `fromPrimitiveType()` 方法中新增对应的 case 映射。

### `api/src/main/java/org/apache/iceberg/variants/SerializedPrimitive.java` (修改, +7 lines)

**修改目的**：支持从序列化字节中反序列化新类型。

**工作逻辑**：
在 `value()` 方法的 switch 中新增：
- TIME/TIMESTAMPTZ_NANOS/TIMESTAMPNTZ_NANOS：使用 `readLittleEndianInt64` 读取 8 字节 Long 值
- UUID：使用 `UUIDUtil.convert()` 将 16 字节 ByteBuffer 转换为 UUID 对象

### `core/src/main/java/org/apache/iceberg/variants/PrimitiveWrapper.java` (修改, +30 lines)

**修改目的**：支持将新类型序列化为 Variant 二进制格式。

**工作逻辑**：
- 新增 4 个 header 常量（TIME_HEADER、TIMESTAMPTZ_NANOS_HEADER、TIMESTAMPNTZ_NANOS_HEADER、UUID_HEADER）
- `sizeInBytes()`：TIME/TIMESTAMPTZ_NANOS/TIMESTAMPNTZ_NANOS 返回 9（1 字节 header + 8 字节值）；UUID 返回 17（1 字节 header + 16 字节值）
- `writeTo()`：TIME/TIMESTAMPTZ_NANOS/TIMESTAMPNTZ_NANOS 写入 header + Long 值；UUID 写入 header + `UUIDUtil.convertToByteBuffer()` 转换的 16 字节

### `core/src/main/java/org/apache/iceberg/variants/Variants.java` (修改, +33 lines)

**修改目的**：提供创建新类型 Variant 值的工厂方法。

**工作逻辑**：
新增 8 个工厂方法：
- `ofTime(long value)` 和 `ofIsoTime(String value)` - 从微秒值或 ISO 字符串创建 Time
- `ofTimestamptzNanos(long value)` 和 `ofIsoTimestamptzNanos(String value)` - 从纳秒值或 ISO 字符串创建带时区纳秒时间戳
- `ofTimestampntzNanos(long value)` 和 `ofIsoTimestampntzNanos(String value)` - 从纳秒值或 ISO 字符串创建不带时区纳秒时间戳
- `ofUUID(UUID uuid)` 和 `ofUUID(String uuid)` - 从 UUID 对象或字符串创建 UUID Variant

### `core/src/main/java/org/apache/iceberg/variants/ShreddedObject.java` (修改, +1/-1 lines)

**修改目的**：保证字段名遍历顺序的确定性。

**工作逻辑**：
`nameSet()` 方法从 `Sets.newHashSet()` 改为 `Sets.newTreeSet()`，使字段名按字典序排列，避免因 HashSet 遍历顺序不确定导致的序列化结果不一致问题。

### `parquet/src/main/java/org/apache/iceberg/parquet/ParquetVariantUtil.java` (修改, +59 lines)

**修改目的**：支持新类型的 Parquet shredding 映射。

**工作逻辑**：
- 新增 `convert(TimestampLogicalTypeAnnotation)` 方法：MICROS 映射到 TIMESTAMPTZ/TIMESTAMPNTZ，NANOS 映射到 TIMESTAMPTZ_NANOS/TIMESTAMPNTZ_NANOS
- `ParquetValueToJavaValue`：新增 TIME（直接返回）、TIMESTAMPTZ_NANOS/TIMESTAMPNTZ_NANOS（直接返回）、UUID（通过 `UUIDUtil.convert()` 转换）
- `ParquetTypeToVariantType`：`visit(TimeLogicalTypeAnnotation)` 从返回 `empty()` 改为返回 `PhysicalType.TIME`；`visit(TimestampLogicalTypeAnnotation)` 的 NANOS 分支从 fallthrough 改为返回对应的 NANO 物理类型；`visit(UUIDLogicalTypeAnnotation)` 从返回 `empty()` 改为返回 `PhysicalType.UUID`
- `VariantTypeToParquetType`：新增 TIME（INT64 + timeType MICROS）、TIMESTAMPTZ_NANOS（INT64 + timestampType NANOS UTC）、TIMESTAMPNTZ_NANOS（INT64 + timestampType NANOS non-UTC）、UUID（FIXED_LEN_BYTE_ARRAY(16) + uuidType）的映射，并新增支持 length 参数的 `shreddedPrimitive` 重载方法

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantReaderBuilder.java` (修改, +26 lines)

**修改目的**：在 Variant 读取器构建中支持新类型。

**工作逻辑**：
- `visit(TimestampLogicalTypeAnnotation)`：改为使用 `ParquetVariantUtil.convert(logical)` 统一转换，支持 MICROS 和 NANOS
- 新增 `visit(TimeLogicalTypeAnnotation)`：校验为 MICROS 且不带时区，使用 `ParquetValueReaders.times(desc)` 读取
- 新增 `visit(UUIDLogicalTypeAnnotation)`：使用 `ParquetValueReaders.uuids(desc)` 读取

### `parquet/src/main/java/org/apache/iceberg/parquet/VariantWriterBuilder.java` (修改, +13/-13 lines)

**修改目的**：启用 Time 和 UUID 写入支持，统一时间戳写入逻辑。

**工作逻辑**：
- `visit(TimeLogicalTypeAnnotation)`：从返回 `Optional.empty()`（注释掉的代码）改为实际实现，使用 `ParquetVariantWriters.primitive(ParquetValueWriters.longs(desc), PhysicalType.TIME)`
- `visit(TimestampLogicalTypeAnnotation)`：简化为使用 `ParquetVariantUtil.convert(timestamp)` 统一处理 MICROS 和 NANOS，不再手动判断
- `visit(UUIDLogicalTypeAnnotation)`：从返回 `Optional.empty()`（注释掉的代码）改为实际实现，使用 `ParquetVariantWriters.primitive(ParquetValueWriters.uuids(desc), PhysicalType.UUID)`

### 测试文件 (修改)

**修改目的**：覆盖新类型的序列化、读取和写入测试。

包括 `TestSerializedPrimitives.java`（+160 行，新增新类型的序列化测试）、`RandomVariants.java`（+12 行，随机生成新类型测试数据）、`TestVariantMetrics.java`（+15 行）、`TestVariantReaders.java`（+18 行）、`TestVariantWriters.java`（+10 行）。

## 总结

本提交为 Iceberg 的 Variant 类型系统新增了三种重要数据类型：Time（微秒精度时间）、纳秒精度时间戳（TIMESTAMPTZ_NANOS 和 TIMESTAMPNTZ_NANOS）以及 UUID。修改覆盖了从 API 类型定义、Core 层序列化/反序列化、到 Parquet 层 shredding 读写器的完整链路，使这些类型能够在 Variant 二进制格式中存储和在 Parquet 文件中被 shred 为原生列。同时修复了 `ShreddedObject` 中字段名遍历顺序不确定的问题。
