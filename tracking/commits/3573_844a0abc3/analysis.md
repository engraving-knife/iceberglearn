# 提交 3573：Kafka Connect: Support VARIANT when record convert (#15283)

## 提交信息

- **序号**：3573 / 4088
- **哈希**：844a0abc3b6f7ec3164c4d60b26973eb686a27db
- **短哈希**：844a0abc3
- **日期**：2026-04-22 11:45:50 -0700
- **作者**：seokyun.ha
- **提交说明**：Kafka Connect: Support VARIANT when record convert (#15283)
- **PR/Issue**：#15283

## 总体目的

该提交为 Kafka Connect Iceberg Sink 的 `RecordConverter` 添加了 VARIANT 类型的转换支持。VARIANT 是 Iceberg 中的一种半结构化数据类型，可以存储任意 JSON-like 的嵌套数据（对象、数组、原始值）。之前，Kafka Connect 在将 SinkRecord 转换为 Iceberg 记录时，不支持 VARIANT 类型字段，导致包含 VARIANT 列的表无法通过 Kafka Connect 写入数据。

该提交实现了从 Kafka Connect 的 `Struct`、`Map`、`Collection` 以及各种原始类型（String、Number、Boolean、时间类型等）到 Iceberg `Variant` 的完整转换逻辑。转换过程首先递归收集所有嵌套字段名构建共享的 `VariantMetadata`，然后递归将 Java 对象转换为 `VariantValue`，最终组合成 `Variant`。

## 如何达成设计目的

整体设计分为三个层次：

1. **字段名收集**：`collectFieldNames` 方法递归遍历 Collection、Map、Struct，收集所有字段名并去重排序，用于构建 `VariantMetadata`。Variant 的元数据需要所有可能字段名的排序列表，以便字段名可以通过字典索引引用，减少存储开销。
2. **递归转换**：`objectToVariantValue` 方法递归将 Java 对象转换为 `VariantValue`：原始类型通过 `primitiveToVariantValue` 处理，Collection 转为 `ValueArray`，Map 和 Struct 转为 `ShreddedObject`。
3. **原始类型转换**：`primitiveToVariantValue` 处理 Boolean、Number、String、ByteBuffer、byte[]、UUID 等；`temporalObjectToVariantValue` 处理各种 java.time 类型和 Kafka Connect 的 java.util.Date 逻辑类型；`numberToVariantValue` 处理各种 Number 子类型。

## 修改详情

### `kafka-connect/kafka-connect/src/main/java/org/apache/iceberg/connect/data/RecordConverter.java` (+244/-0 lines)

**修改目的**：实现 VARIANT 类型的转换逻辑。

**工作逻辑**：
- 在 `convertValue` 方法的 switch 语句中新增 `VARIANT` case，调用 `convertVariantValue`。
- `convertVariantValue`：如果值已经是 `Variant` 则直接返回（pass-through）；否则收集字段名、构建 metadata、调用 `objectToVariantValue` 转换。
- `collectFieldNames`：递归遍历 Collection/Map/Struct 收集字段名，Map 的 key 必须为 String 否则抛出 `IllegalArgumentException`。
- `objectToVariantValue`：递归转换，null 返回 `Variants.ofNull()`，原始类型委托给 `primitiveToVariantValue`，Collection 转为 `ValueArray`，Map 通过 `mapToVariantValue` 转换，Struct 转为 `ShreddedObject`。
- `mapToVariantValue`：将 Map 转为 `ShreddedObject`，要求 key 为 String。
- `primitiveToVariantValue`：处理 Boolean、时间类型（委托给 `temporalObjectToVariantValue`）、Number（委托给 `numberToVariantValue`）、String、ByteBuffer、byte[]、UUID。
- `temporalObjectToVariantValue`：将 Instant/OffsetDateTime/ZonedDateTime 转为 timestamptz，LocalDateTime 转为 timestampntz，LocalDate 转为 date，LocalTime 转为 time。对于 java.util.Date，根据 Connect schema 的逻辑类型名（Timestamp/Time/Date）进行区分转换。
- `numberToVariantValue`：处理 BigDecimal、BigInteger、Integer、Long、Float、Double、Byte、Short，不支持的 Number 子类型抛出异常。

### `kafka-connect/kafka-connect/src/test/java/org/apache/iceberg/connect/data/TestRecordConverter.java` (+367/-0 lines)

**修改目的**：全面测试 VARIANT 转换逻辑。

**工作逻辑**：
新增大量测试用例覆盖：
- null 值转换（返回 `PhysicalType.NULL`）
- Variant pass-through（已经是 Variant 的直接返回）
- 原始类型：String、Number、Boolean、Instant、各种时间类型
- UUID、ByteBuffer、byte[]
- Map 转换（包括嵌套 Map）
- Struct 转换
- Collection/Array 转换
- 嵌套结构（对象中的数组、数组中的对象等）
- 错误场景（非 String key 的 Map、未知 Number 类型等）
- 使用 `VARIANT_SCHEMA`（包含 `VariantType.get()` 字段）创建测试用 converter。

## 总结

该提交为 Kafka Connect Iceberg Sink 添加了完整的 VARIANT 类型支持，实现了从 Kafka Connect 数据模型（Struct/Map/Collection/原始类型）到 Iceberg Variant 的递归转换。VARIANT 类型在处理半结构化数据（如 JSON）时非常有用，该实现支持所有主要的 Java 数据类型，包括时间类型和 Kafka Connect 逻辑类型。测试覆盖全面，确保转换的正确性。
