# 提交 2441：Arrow: Test FIXED type (#13700)

## 提交信息

- **序号**：2441 / 4088
- **哈希**：19b9eaebb9500d86315600791109ba3a9a6be080
- **短哈希**：19b9eaebb
- **日期**：2025-08-04 10:21:18 +0200
- **作者**：Nándor Kollár
- **提交说明**：Arrow: Test FIXED type (#13700)
- **PR/Issue**：#13700

## 总体目的

本提交为 Iceberg 的 Arrow 向量化读取器（ArrowReader）新增了对 `FixedType`（固定长度二进制类型）的测试覆盖，并修正了相关文档。此前 `ArrowReader` 实际上已经支持读取 `FixedType`（映射到 Arrow 的 `FixedSizeBinary`），但该类型被错误地列在"不支持的数据类型"文档说明中，且缺乏专门的测试用例。

具体问题包括：
1. `ArrowReader` 类的 JavaDoc 将 `Types.FixedType` 列在不支持的数据类型清单中，但实际上 `SUPPORTED_TYPES` 集合和读取逻辑已支持该类型（这可能是历史遗留，FIXED 类型支持是在某次提交中加入但文档未同步更新）。
2. `ColumnVector` 的 JavaDoc 未列出 `Types.FixedType`。
3. 测试中没有针对 FIXED 类型的列读取验证。

本提交将 `TypeID.FIXED` 加入 `SUPPORTED_TYPES`（确认其已支持）、更新文档、并在 `TestArrowReader` 中新增 `fixed` 和 `fixed_nullable` 两列的完整测试。

## 如何达成设计目的

1. 在 `ArrowReader.SUPPORTED_TYPES` 中显式加入 `TypeID.FIXED`。
2. 更新 `ArrowReader` 和 `ColumnVector` 的 JavaDoc，将 FixedType 从"不支持"移到"支持"列表。
3. 在测试 schema 中新增 `fixed`（required）和 `fixed_nullable`（optional）两列，类型为 `FixedType.ofLength(7)`。
4. 在测试数据生成、Arrow schema 构建、列值验证、向量类型断言中均补充对这两列的处理。

## 修改详情

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ArrowReader.java` (+9/-6 lines)

**修改目的**：确认 FIXED 类型支持并更新文档。

**工作逻辑**：
- 在类 JavaDoc 的支持类型列表中新增 `Iceberg: Types.FixedType, Arrow: MinorType#FIXEDSIZEBINARY`。
- 从不支持类型列表中移除 `Types.FixedType`。
- 在 `SUPPORTED_TYPES` 集合中新增 `TypeID.FIXED`（使用 `Set.of(...)` 的可变参数列表追加）。

### `arrow/src/main/java/org/apache/iceberg/arrow/vectorized/ColumnVector.java` (+1/-0 lines)

**修改目的**：更新 ColumnVector 文档。

**工作逻辑**：在支持的类型列表中新增 `Types.FixedType`。

### `arrow/src/test/java/org/apache/iceberg/arrow/vectorized/TestArrowReader.java` (+57/-6 lines)

**修改目的**：新增 FIXED 类型的完整测试覆盖。

**工作逻辑**：
- 将 `fixed` 和 `fixed_nullable` 加入 `COLUMN_SET`（参与测试的列名集合）。
- **Schema 新增**：在 Iceberg schema 中新增 `Types.NestedField.required(28, "fixed", Types.FixedType.ofLength(7))` 和 `Types.NestedField.optional(29, "fixed_nullable", Types.FixedType.ofLength(7))`。
- **Arrow schema 新增**：新增对应的 `ArrowType.FixedSizeBinary(7)` 字段（required 和 nullable 各一）。
- **测试数据生成**：`createExpectedRecords` 和 `createSingleRecord` 中为 fixed 列设置 `("abcdef" + i % 7).getBytes(StandardCharsets.UTF_8)`（7 字节）。
- **列值验证**：在 `checkColumnarArrayValues` 和 `checkVectorValues` 中新增对 `fixed` 和 `fixed_nullable` 列的值验证，读取方式为 `array.getBinary(i)` 和 `((FixedSizeBinaryVector) vector).getObject(i)`。
- **向量类型断言**：新增 `assertEqualsForField(root, columnSet, "fixed", FixedSizeBinaryVector.class)` 和 `fixed_nullable` 的断言，验证 Arrow 向量类型正确。

## 总结

本提交完善了 Arrow 读取器对 FIXED 类型的支持声明和测试覆盖。虽然底层读取逻辑已支持 FIXED 类型，但此前文档错误地将其列为不支持，且缺乏测试。本提交修正了文档、在 `SUPPORTED_TYPES` 中显式声明、并新增了从 schema 定义到数据生成、值验证、向量类型断言的完整测试链路。这是一个测试与文档完整性增强。
