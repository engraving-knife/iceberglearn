# 提交 2848：support StructInternalRow.getVariant (#14379)

## 提交信息

- **序号**：2848 / 4088
- **哈希**：7e28be434c64afb9675c743335734bb8217c1df5
- **短哈希**：7e28be434
- **日期**：2025-11-08 20:41:33 -0800
- **作者**：Huaxin Gao
- **提交说明**：support StructInternalRow.getVariant (#14379)
- **PR/Issue**：#14379

## 总体目的

`StructInternalRow` 是 Iceberg Spark 模块中的一个内部行实现类，它将 Iceberg 的结构化记录（`Struct`）包装为 Spark 的 `InternalRow`，供 Spark 读取 Iceberg 表数据时使用。Spark 4.0 引入了 Variant 类型（一种半结构化数据类型，类似 JSON），Iceberg 也相应支持了 Variant 类型。

在此之前，`StructInternalRow` 的 `getVariant(int ordinal)` 方法直接抛出 `UnsupportedOperationException`，这意味着通过 `StructInternalRow` 读取 Variant 类型字段时无法正常工作。本提交的目的就是实现 `getVariant` 方法，使 Iceberg 内部的 `Variant` 对象能正确转换为 Spark 的 `VariantVal` 对象，从而支持在 Spark 中读取 Iceberg 表中的 Variant 类型数据，包括 Variant 出现在顶层字段、数组元素、Map 值以及嵌套结构中的场景。

## 如何达成设计目的

核心设计思路是添加一个 `toVariantVal` 静态转换方法，将 Iceberg 的 `Variant` 对象（由 metadata 和 value 两部分组成）序列化为 Spark `VariantVal` 所需的两个字节数组（value 字节和 metadata 字节），并使用小端序（LITTLE_ENDIAN）字节缓冲区进行写入。然后在 `getVariant`、`getVariantInternal` 以及数组填充逻辑中调用此转换方法。

同时，为了支持 Spark 通过 `InternalRow.get(ordinal, DataType)` 通用接口以 `VariantType` 读取 Variant 值，在 `get` 方法中增加了对 `VariantType` 的分支处理。在数组元素填充逻辑中也增加了 `VARIANT` 类型的 case 分支，使 Variant 数组能正确转换为 `VariantVal` 数组。

## 修改详情

### `spark/v4.0/spark/src/main/java/org/apache/iceberg/spark/source/StructInternalRow.java` (+40/-4 lines)

**修改目的**：实现 `getVariant` 方法及相关的 Variant 类型支持，包括顶层字段读取、数组元素填充和通用 `get` 方法。

**工作逻辑**：

1. **实现 `getVariant` 和 `getVariantInternal`**：将原来直接抛异常的 `getVariant` 改为在非 null 时委托给新的 `getVariantInternal` 方法，后者从底层 struct 获取 Object 值并调用 `toVariantVal` 转换：

```java
@Override
public VariantVal getVariant(int ordinal) {
  return isNullAt(ordinal) ? null : getVariantInternal(ordinal);
}

private VariantVal getVariantInternal(int ordinal) {
  Object value = struct.get(ordinal, Object.class);
  return toVariantVal(value);
}
```

2. **新增 `toVariantVal` 静态方法**：将 Iceberg `Variant` 转换为 Spark `VariantVal`。分别分配 metadata 和 value 的字节数组，使用小端序 ByteBuffer 调用 `variant.metadata().writeTo()` 和 `variant.value().writeTo()` 写入数据：

```java
private static VariantVal toVariantVal(Object value) {
  if (value instanceof Variant) {
    Variant variant = (Variant) value;
    byte[] metadataBytes = new byte[variant.metadata().sizeInBytes()];
    ByteBuffer metadataBuffer = ByteBuffer.wrap(metadataBytes).order(ByteOrder.LITTLE_ENDIAN);
    variant.metadata().writeTo(metadataBuffer, 0);

    byte[] valueBytes = new byte[variant.value().sizeInBytes()];
    ByteBuffer valueBuffer = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN);
    variant.value().writeTo(valueBuffer, 0);

    return new VariantVal(valueBytes, metadataBytes);
  }
  throw new UnsupportedOperationException(
      "Unsupported value for VARIANT in StructInternalRow: " + value.getClass());
}
```

3. **通用 `get` 方法支持 VariantType**：在 `get(int ordinal, DataType dataType)` 方法中增加 `VariantType` 分支，使 Spark 可通过通用接口读取 Variant：

```java
} else if (dataType instanceof VariantType) {
  return getVariantInternal(ordinal);
}
```

4. **数组元素填充支持 VARIANT**：在 `createArraySetter` 的 switch 语句中增加 `VARIANT` case，使 Variant 数组元素通过 `toVariantVal` 转换：

```java
case VARIANT:
  return fillArray(
      values,
      array -> (BiConsumer<Integer, Object>) (pos, v) -> array[pos] = toVariantVal(v));
```

### `spark/v4.0/spark/src/test/java/org/apache/iceberg/spark/source/TestStructInternalRowVariant.java` (+182/-0 lines, 新文件)

**修改目的**：全面测试 `StructInternalRow` 对 Variant 类型的支持。

**工作逻辑**：新增测试类包含以下测试用例：
- `testGetVariantReturnsVariantVal`：测试从顶层字段读取 Variant 并验证 metadata 和 value 内容正确。
- `testGetVariantNull`：测试 null Variant 字段返回 null。
- `testArrayOfVariant`：测试 Variant 数组元素的读取，包括 null 元素。
- `testMapWithVariant`：测试 Map 中 Variant 值的读取。
- `testNestedStructVariant`：测试嵌套结构中 Variant 字段的读取。
- `testGetWithVariantType`：测试通过通用 `get(ordinal, VariantType)` 接口读取 Variant。

测试使用 `Variants.metadata("k")` 和 `Variants.object(md)` 构建 `{"k":"v1"}` 的样例 Variant，并通过 `assertVariantValEqualsKV` 辅助方法验证转换后的 `VariantVal` 内容正确。

## 总结

本提交为 Spark v4.0 的 `StructInternalRow` 实现了完整的 Variant 类型读取支持，通过 `toVariantVal` 转换方法将 Iceberg 的 `Variant` 对象序列化为 Spark `VariantVal` 所需的小端序字节数组。修改覆盖了顶层字段、数组元素、Map 值和嵌套结构等多种场景，并配有全面的测试用例。这是 Iceberg 对 Spark Variant 类型支持的重要补充，使 Spark 能正确读取 Iceberg 表中的 Variant 数据。
