# 提交 2027：API: Don't check underlying error msg on AIOOBE (#12867)

## 提交信息

- **序号**：2027 / 4088
- **哈希**：7dbafb438ee1e68d0047bebcb587265d7d87d8a1
- **短哈希**：7dbafb438
- **日期**：2025-04-22 15:14:07 +0200
- **作者**：Eduard Tudenhoefner
- **提交说明**：API: Don't check underlying error msg on AIOOBE (#12867) / The error msg might be missing when the tests are executed with different JDK versions, so it's better to skip checking the underlying error msg to avoid flakiness in test executions
- **PR/Issue**：#12867

## 总体目的

本提交移除 `TestSerializedMetadata` 中对 `ArrayIndexOutOfBoundsException` 错误消息内容的断言，仅检查异常类型，以消除不同 JDK 版本下测试的不稳定（flakiness）。

`SerializedMetadata.get(index)` 在索引越界时抛出 `ArrayIndexOutOfBoundsException`，此前的测试不仅断言异常类型，还断言消息包含 "out of bounds"。但 `ArrayIndexOutOfBoundsException` 的消息内容是 JDK 实现细节：不同 JDK 版本（或不同厂商 JDK）的消息格式可能不同，某些版本可能不包含 "out of bounds" 字样，甚至消息为空。这导致测试在不同环境下时好时坏（flaky）。本提交移除消息内容检查，仅保留类型断言。

## 如何达成设计目的

将 4 处 `assertThatThrownBy(...).isInstanceOf(ArrayIndexOutOfBoundsException.class).hasMessageContaining("out of bounds")` 简化为 `assertThatThrownBy(...).isInstanceOf(ArrayIndexOutOfBoundsException.class)`，并添加 `@SuppressWarnings("checkstyle:AssertThatThrownByWithMessageCheck")` 注解（因为项目 checkstyle 要求 `assertThatThrownBy` 必须包含消息检查，此处是合理的例外）。

## 修改详情

### `api/src/test/java/org/apache/iceberg/variants/TestSerializedMetadata.java` (修改, +12/-12 lines)

**修改目的**：移除对 AIOOBE 错误消息内容的断言。

**工作逻辑**：在 4 个测试方法（`testEmptyVariantMetadata`、`testReadString`、`testMultibyteString`、`testTwoByteOffsets`）中：
- 新增 `@SuppressWarnings("checkstyle:AssertThatThrownByWithMessageCheck")` 注解，抑制 checkstyle 要求异常断言必须含消息检查的规则。
- 将 `assertThatThrownBy(() -> metadata.get(N)).isInstanceOf(ArrayIndexOutOfBoundsException.class).hasMessageContaining("out of bounds")` 改为 `assertThatThrownBy(() -> metadata.get(N)).isInstanceOf(ArrayIndexOutOfBoundsException.class)`，并添加注释说明"不检查底层错误消息，因为不同 JDK 版本可能缺失"。

## 总结

本提交移除 `TestSerializedMetadata` 中 4 处对 `ArrayIndexOutOfBoundsException` 消息内容（"out of bounds"）的断言，仅保留异常类型检查，消除不同 JDK 版本下的测试 flakiness。共 1 个文件、+12/-12 行，纯测试稳定性改进。
