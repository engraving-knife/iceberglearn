# 提交 3662：Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration to Flink 2.0 - missing changes (#16239)

## 提交信息

- **序号**：3662 / 4088
- **哈希**：153237b568d6c1ddb6025a58c551e8e459164c71
- **短哈希**：153237b56
- **日期**：2026-05-07 13:30:23 +0200
- **作者**：pvary
- **提交说明**：Flink: Backport add Nanosecond Precision Support for Flink-Iceberg Integration to Flink 2.0 - missing changes (#16239)
- **PR/Issue**：#16239（backport #15475 的补充）

## 总体目的

这个提交是第 3661 号提交（PR #16183，backport #15475 到 Flink 2.0）的补充修复。在第 3661 号提交中，`StructRowData.java` 的纳秒精度支持改动存在一个代码风格问题：使用了传统的 `instanceof` 加显式强制类型转换，而非 Java 16+ 的 pattern matching for instanceof 语法。

本提交将 `StructRowData.java` 中的 `instanceof` 检查改为 pattern matching 形式（`if (value instanceof LocalDateTime localDateTime)`），这与项目中其他地方使用的风格一致，也是该 backport 原本应包含但遗漏的改动。

## 如何达成设计目的

将 `StructRowData.java` 中四处 `instanceof` 检查从传统形式改为 pattern matching 形式，消除显式类型转换。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/data/StructRowData.java` (+10/-10 lines)

**修改目的**：使用 pattern matching for instanceof 替代显式类型转换。

**工作逻辑**：
```java
// 旧
if (value instanceof LocalDateTime) {
  nanoLong = DateTimeUtil.nanosFromTimestamp((LocalDateTime) value);
} else if (value instanceof OffsetDateTime) {
  nanoLong = DateTimeUtil.nanosFromTimestamptz((OffsetDateTime) value);
}
// 新
if (value instanceof LocalDateTime localDateTime) {
  nanoLong = DateTimeUtil.nanosFromTimestamp(localDateTime);
} else if (value instanceof OffsetDateTime offsetDateTime) {
  nanoLong = DateTimeUtil.nanosFromTimestamptz(offsetDateTime);
}
```
涉及 `LocalDate`、`LocalDateTime`、`OffsetDateTime` 三种类型的四处 instanceof 检查。

## 总结

这是第 3661 号提交的补充修复，将 `StructRowData.java` 中的 `instanceof` 检查改为 Java 16+ 的 pattern matching 语法，消除显式类型转换。功能无变化，纯属代码风格统一，使 Flink 2.0 的纳秒精度 backport 与原 PR 保持一致。
