# 提交 2416：API: Improve test coverage for 1-5 byte header string primitive in Variant (#13629)

## 提交信息

- **序号**：2416 / 4088
- **哈希**：02978169527aa6ee6686a3f19d5ec96fde3eb5e8
- **短哈希**：029781695
- **日期**：2025-07-25 13:33:30 -0500
- **作者**：Manikandan R
- **提交说明**：API: Improve test coverage for 1-5 byte header string primitive in Variant (#13629)
- **PR/Issue**：#13629

## 总体目的

本提交改进了 Iceberg Variant 类型中字符串原语（string primitive）的多字节偏移量测试覆盖率。

Iceberg Variant 类型是 Iceberg 支持的一种半结构化数据类型。在 Variant 的序列化格式中，字符串值的大小决定了其偏移量（offset）使用多少字节来存储：
- 1 字节偏移量：字符串 ≤ 255 字节
- 2 字节偏移量：字符串 > 255 字节（需 2 字节表示偏移）
- 3 字节偏移量：字符串 > 65535 字节
- 4 字节偏移量：字符串 > 16777216 字节（约 16MB）

此前的测试 `testMultiByteOffsets` 只覆盖了 2 字节（300 字节字符串）和 3 字节（70000 字节字符串）偏移量的场景，没有覆盖 4 字节偏移量的场景。本提交新增了 4 字节偏移量的测试用例（16800000 字节字符串），并增加了对偏移量大小的显式验证。

## 如何达成设计目的

1. 将参数化测试从 `@ValueSource`（仅传递 int 参数）改为 `@MethodSource`，允许同时传递字符串大小和期望的偏移量字节数
2. 新增 4 字节偏移量的测试参数（16800000 字节字符串，期望偏移量大小为 4）
3. 在 `VariantTestUtil` 中新增带 `offsetSize` 参数的 `assertVariantString` 重载方法，验证偏移量大小

## 修改详情

### `api/src/test/java/org/apache/iceberg/variants/TestSerializedObject.java` (+27/-8 lines)

**修改目的**：扩展多字节偏移量测试，覆盖 4 字节偏移量场景并验证偏移量大小。

**工作逻辑**：
- 新增 `provideInputsForTestMultiByteOffsets()` 静态方法作为 `@MethodSource` 数据源，返回三组参数：
  - `(300, 2)`：300 字节字符串，期望 2 字节偏移量
  - `(70000, 3)`：70000 字节字符串，期望 3 字节偏移量
  - `(16800000, 4)`：16800000 字节字符串，期望 4 字节偏移量
- 将 `@ParameterizedTest` + `@ValueSource(ints = {300, 70000})` 改为 `@ParameterizedTest` + `@MethodSource("provideInputsForTestMultiByteOffsets")`
- 测试方法签名从 `testMultiByteOffsets(int multiByteOffset)` 改为 `testMultiByteOffsets(int multiByteOffset, int offsetSize)`
- 断言调用从 `VariantTestUtil.assertVariantString(object.get("big"), randomString)` 改为 `VariantTestUtil.assertVariantString(object.get("big"), randomString, offsetSize)`，增加了对偏移量大小的验证

### `api/src/test/java/org/apache/iceberg/variants/VariantTestUtil.java` (+5/-0 lines)

**修改目的**：新增带偏移量大小验证的断言方法。

**工作逻辑**：新增 `assertVariantString(VariantValue actual, String expected, int offsetSize)` 重载方法。该方法先调用原有的 `assertVariantString(actual, expected)` 验证字符串值正确性，然后通过 `VariantUtil.sizeOf(actual.asPrimitive().sizeInBytes())` 计算实际的偏移量大小，并断言其等于期望的 `offsetSize`。

## 总结

本提交是一个纯测试改进，将 Variant 字符串原语的多字节偏移量测试覆盖率从 2-3 字节扩展到 2-4 字节，并增加了对偏移量大小的显式验证。这确保了 Variant 序列化格式在处理不同大小的字符串时，能正确地选择和使用相应字节数的偏移量编码。
