# 提交 2377：API, Core: Use short string in Variant when possible (#13284)

## 提交信息

- **序号**：2377 / 4088
- **哈希**：4c7726429603c15be68bc00aa751fa67f8975dad
- **短哈希**：4c7726429
- **日期**：2025-07-21 12:58:09 -0500
- **作者**：Manikandan R
- **提交说明**：API, Core: Use short string in Variant when possible (#13284)
- **PR/Issue**：#13284

## 总体目的

本提交为 Iceberg 的 Variant 类型（变体类型，用于存储半结构化数据）实现了短字符串（short string）优化。在 Variant 的二进制编码规范中，字符串类型有两种编码方式：常规字符串（需要 5 字节头部：1 字节类型标识 + 4 字节长度）和短字符串（仅需 1 字节头部，长度编码在头部中，最大支持 63 字节）。

此前代码在序列化字符串类型的 Variant 值时，总是使用常规字符串编码，代码中甚至留有 `// TODO: use short string when possible` 的注释。本次修改实现了该 TODO，当字符串的 UTF-8 字节长度不超过 63 字节时，使用短字符串编码，从而节省 4 字节的存储空间。这对于包含大量短字符串字段的 Variant 数据可以带来显著的存储和内存节省。

## 如何达成设计目的

设计思路是在序列化字符串时检查长度，如果不超过 63 字节则使用短字符串编码格式。关键设计点如下：

1. **新增头部生成方法**：在 VariantUtil 中新增 `shortStringHeader(int length)` 方法，按照规范生成短字符串的头部字节（将长度左移 2 位并与 BASIC_TYPE_SHORT_STRING 常量进行或运算）。
2. **修改序列化逻辑**：在 PrimitiveWrapper 的 `sizeInBytes` 和 `writeToBuffer` 方法中，对 STRING 类型增加长度判断，当不超过 63 字节时使用短字符串格式（1 字节头部），否则使用常规字符串格式（5 字节头部）。
3. **测试工具支持**：在 VariantTestUtil 中新增 `createShortString` 方法和 `assertVariantString` 断言方法，支持测试中创建和验证短字符串。

## 修改详情

### `api/src/main/java/org/apache/iceberg/variants/VariantUtil.java` (+4/-0 lines)

**修改目的**：新增短字符串头部生成方法。

**工作逻辑**：新增 `shortStringHeader(int length)` 静态方法，通过 `(length << 2) | BASIC_TYPE_SHORT_STRING` 计算短字符串的头部字节。短字符串的编码规范中，头部字节的低 2 位为基本类型标识（BASIC_TYPE_SHORT_STRING），高 6 位存储字符串长度（最大 63）。

### `core/src/main/java/org/apache/iceberg/variants/PrimitiveWrapper.java` (+13/-8 lines)

**修改目的**：在字符串序列化中使用短字符串编码。

**工作逻辑**：修改了两处：
- `sizeInBytes` 方法：对 STRING 类型，如果 buffer 剩余字节数不超过 `MAX_SHORT_STRING_LENGTH`（63），返回 `1 + buffer.remaining()`（1 字节头部 + 值长度），否则返回 `5 + buffer.remaining()`（1 字节头部 + 4 字节长度 + 值长度）。
- `writeToBuffer` 方法：对 STRING 类型，如果不超过 63 字节，写入 `shortStringHeader` 作为头部（1 字节），然后直接写入字符串数据；否则使用原来的 STRING_HEADER + 4 字节长度 + 数据的格式。同时删除了原有的 `// TODO: use short string when possible` 注释。

### `api/src/test/java/org/apache/iceberg/variants/VariantTestUtil.java` (+28/-0 lines)

**修改目的**：添加短字符串的测试工具方法。

**工作逻辑**：新增两个方法：
- `createShortString(String string)`：创建短字符串的 SerializedShortString 实例，验证长度不超过 63，生成头部字节并写入 UTF-8 编码的字符串数据。
- `assertVariantString(VariantValue actual, String expected)`：断言 Variant 值的字符串类型和值正确，并根据字符串长度验证使用了正确的编码类型（短字符串或常规字符串）和正确的字节数。

### 其他测试文件（TestSerializedArray、TestSerializedObject、TestSerializedPrimitives、TestPrimitiveWrapper、TestShreddedObject、TestValueArray）

**修改目的**：更新测试用例以使用短字符串编码。

**工作逻辑**：将测试中创建字符串 Variant 值的方式从常规字符串改为使用 `createShortString` 方法，使测试覆盖短字符串编码路径。同时使用 `assertVariantString` 替代原有的断言方式，验证编码正确性。这些修改确保测试与新的短字符串优化逻辑一致。

## 总结

本提交实现了 Variant 类型的短字符串编码优化，当字符串 UTF-8 长度不超过 63 字节时使用 1 字节头部代替 5 字节头部，节省存储空间。修改涉及核心序列化逻辑（VariantUtil 和 PrimitiveWrapper）和大量测试文件的更新。这是一个性能和存储优化改进，遵循了 Variant 二进制编码规范，对于包含大量短字符串的半结构化数据具有实际意义。
