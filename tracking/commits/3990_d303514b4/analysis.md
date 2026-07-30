# 提交 3990：ORC: Fix garbled exception message for invalid timestamp unit attribute (#17098)

## 提交信息

- **序号**：3990 / 4088
- **哈希**：d303514b4a9e90e62ffe136dcea3eed624eab288
- **短哈希**：d303514b4
- **日期**：2026-07-06 20:21:12 +0200
- **作者**：Eunbin Son
- **提交说明**：ORC: Fix garbled exception message for invalid timestamp unit attribute (#17098)
- **PR/Issue**：#17098

## 总体目的

本提交修复了 `OrcToIcebergVisitor.primitive()` 中异常消息格式化的 bug。当 ORC 文件的 `iceberg.timestamp-unit` 属性值无法识别时，代码构建异常消息时使用了字符串拼接（`+`）而非 `String.format`，导致格式占位符 `%s` 未被替换，异常消息中出现了字面量 `%s` 而非实际的 unit 值。

例如，当 unit 为 "SECONDS" 时，旧代码抛出的消息是 `"Invalid Timestamp type unit: %sSECONDS"`（`%s` 未被替换），而正确应该是 `"Invalid Timestamp type unit: SECONDS"`。TIMESTAMP 和 TIMESTAMP_INSTANT 两个分支都有同样的 bug。

## 如何达成设计目的

将两处字符串拼接改为 `String.format()` 调用，与 `GenericOrcReader` 中类似 "invalid type" 异常的处理方式保持一致。

## 修改详情

### `orc/src/main/java/org/apache/iceberg/orc/OrcToIcebergVisitor.java` (+2/-2 lines)

**修改目的**：修复异常消息格式化。

**工作逻辑**：
```java
// 旧：throw new IllegalStateException("Invalid Timestamp type unit: %s" + unit);
// 新：
throw new IllegalStateException(String.format("Invalid Timestamp type unit: %s", unit));
```
TIMESTAMP 和 TIMESTAMP_INSTANT 两个分支都做了同样修改。

### `orc/src/test/java/org/apache/iceberg/orc/TestORCSchemaUtil.java` (+26/-0 lines)

**修改目的**：验证修复后的异常消息。

**工作逻辑**：新增两个测试 `testInvalidTimestampUnit` 和 `testInvalidTimestampInstantUnit`，构造带有无效 "SECONDS" unit 属性的 ORC schema，验证转换时抛出 `IllegalStateException`，消息为 `"Invalid Timestamp type unit: SECONDS"`（不含字面量 `%s`）。

## 总结

本提交修复了一个异常消息格式化 bug，将字符串拼接改为 `String.format`，使错误消息正确显示无效的 timestamp unit 值。这是一个小但影响诊断体验的修复。
