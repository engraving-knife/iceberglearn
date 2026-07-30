# 提交 2762：API, Core: Fix byte buffer conversion for Variant in bounds (#14362)

## 提交信息

- **序号**：2762 / 4088
- **哈希**：e23cf65e9ee0c4f8c5d5677a7ab95eaff2f295c5
- **短哈希**：e23cf65e9
- **日期**：2025-10-18 12:04:36 -0600
- **作者**：Amogh Jahagirdar
- **提交说明**：API, Core: Fix byte buffer conversion for Variant in bounds (#14362)
- **PR/Issue**：#14362

## 总体目的

本提交为 `Conversions` 工具类补全了 Variant 类型的 `ByteBuffer` 序列化与反序列化实现，使得 Variant 值能够正确地在边界统计信息（bounds）中存储和读取。

背景在于：Iceberg v3 引入了 Variant 类型。`Conversions` 类负责在 Java 对象与 `ByteBuffer` 之间进行转换，用于序列化列统计信息（如 lowerBounds/upperBounds）、表达式常量等。此前 `Conversions.toByteBuffer` 和 `Conversions.fromByteBuffer` 缺少对 `Type.TypeID.VARIANT` 的处理分支，调用时会抛出 `UnsupportedOperationException`。

这意味着任何涉及 Variant 列统计信息的场景都会失败，例如：写入 Variant 列时收集 lower/upper bounds、在过滤表达式中使用 Variant 常量等。本提交与 2666（Variant 在 Parquet 过滤中的处理）和 2759（Variant 在 eq/in 中按嵌套处理）属于同一 Variant 类型支持系列，本提交聚焦于底层的字节缓冲转换。

## 如何达成设计目的

在 `Conversions` 的 `toByteBuffer` 和 `fromByteBuffer` 两个方法中分别新增 `VARIANT` 分支：

1. **序列化（toByteBuffer）**：Variant 由 `VariantMetadata`（元数据）和 `VariantValue`（值）两部分组成。将两者按 metadata 在前、value 在后的顺序拼接为一个 `ByteBuffer`（小端序 Little Endian）。分配大小为 `metadata.sizeInBytes() + value.sizeInBytes()`，先用 `metadata.writeTo(buffer, 0)` 写入元数据，再用 `value.writeTo(buffer, metadata.sizeInBytes())` 写入值。

2. **反序列化（fromByteBuffer）**：通过 `Variant.from(tmp)` 从 ByteBuffer 重建 Variant 对象，由 Variant 的工厂方法负责解析 metadata 和 value 两部分。

同时在三个 Variant 测试类（`TestPrimitiveWrapper`、`TestShreddedObject`、`TestValueArray`）中新增 `testByteBufferConversion` 测试，验证 toByteBuffer → fromByteBuffer 的往返一致性，覆盖了基本类型、对象（shredded object）、数组三种 Variant 形态。

## 修改详情

### `api/src/main/java/org/apache/iceberg/types/Conversions.java` (+16/-0 lines)

**修改目的**：为 Variant 类型补全 ByteBuffer 序列化与反序列化。

**工作逻辑**：
- 新增 import `Variant`、`VariantMetadata`、`VariantValue`。
- 在 `toByteBuffer(TypeID, Object)` 的 switch 中新增 `case VARIANT`：将 value 转为 `Variant`，分别获取 `variant.metadata()` 和 `variant.value()`，分配小端序 ByteBuffer，依次写入 metadata（偏移 0）和 value（偏移 metadata.sizeInBytes()），返回该 buffer。
- 在 `fromByteBuffer(Type, ByteBuffer)` 的 switch 中新增 `case VARIANT`：通过 `Variant.from(tmp)` 从 ByteBuffer 重建 Variant。

### `core/src/test/java/org/apache/iceberg/variants/TestPrimitiveWrapper.java` (+14/-0 lines)

**修改目的**：验证基本类型 Variant 的 ByteBuffer 往返转换。

**工作逻辑**：新增参数化测试 `testByteBufferConversion(VariantPrimitive)`：对每个基本类型 primitive，构造 `Variant.of(primitiveMetadata, primitive)`，调用 `Conversions.toByteBuffer(VariantType, variant)` 序列化，再 `Conversions.fromByteBuffer(VariantType, buffer)` 反序列化，用 `VariantTestUtil.assertEqual` 分别断言 metadata 和 value 一致。

### `core/src/test/java/org/apache/iceberg/variants/TestShreddedObject.java` (+22/-0 lines)

**修改目的**：验证对象类型 Variant 的 ByteBuffer 往返转换。

**工作逻辑**：新增测试 `testByteBufferConversion`：构造含三个字段（a=34, b="iceberg", c=BigDecimal 12.21）的 ShreddedObject，组合成 Variant，执行 toByteBuffer → fromByteBuffer 往返，断言 metadata 和 value 一致。

### `core/src/test/java/org/apache/iceberg/variants/TestValueArray.java` (+14/-0 lines)

**修改目的**：验证数组类型 Variant 的 ByteBuffer 往返转换。

**工作逻辑**：新增测试 `testByteBufferConversion`：构造 ValueArray，组合成 Variant，执行 toByteBuffer → fromByteBuffer 往返，断言 metadata 和 value 一致。

## 总结

本提交为 `Conversions` 类补全了 Variant 类型的 ByteBuffer 序列化（metadata + value 拼接，小端序）与反序列化（`Variant.from`）实现，使 Variant 列能够正确地参与边界统计信息存储和表达式常量处理。核心改动在 `Conversions.java`（16 行），配套在三个 Variant 测试类中新增往返测试，覆盖基本类型、对象、数组三种形态。这是 Iceberg v3 Variant 类型支持（2666 系列）在底层序列化方面的完善，与 2759（Parquet 行组过滤中 Variant 按 eq/in 处理）共同保障 Variant 类型的正确读写与过滤。
