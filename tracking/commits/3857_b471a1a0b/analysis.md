# 提交 3857：Flink: Validate missing watermark column in column stats watermark extractor (#16774)

## 提交信息

- **序号**：3857 / 4088
- **哈希**：b471a1a0b18fd9d9e7007d0effb111b4bf0f2754
- **短哈希**：b471a1a0b
- **日期**：2026-06-11 16:12:20 +0200
- **作者**：jackylee
- **提交说明**：Flink: Validate missing watermark column in column stats watermark extractor (#16774)
- **PR/Issue**：#16774

## 总体目的

本提交修复了 Flink 的 `ColumnStatsWatermarkExtractor` 在水印列不存在时抛出 `NullPointerException` 而非有意义的错误信息的问题。

`ColumnStatsWatermarkExtractor` 用于从 Iceberg 数据文件的列统计信息中提取水位（watermark），需要指定一个事件时间字段名。当用户指定的字段名在 schema 中不存在时，`schema.findField(eventTimeFieldName)` 返回 `null`，随后代码直接调用 `field.type().typeId()` 导致 NPE。

这种 NPE 对用户来说难以诊断，因为它不包含字段名信息。本提交添加了显式的非空校验，当字段不存在时抛出包含字段名的 `IllegalArgumentException`，提供清晰的错误信息。

## 如何达成设计目的

在 `ColumnStatsWatermarkExtractor` 构造器中，调用 `schema.findField()` 后立即使用 `Preconditions.checkArgument()` 校验非 null。修复同时应用于 Flink 1.20、2.0 和 2.1 三个版本。

## 修改详情

### `flink/v1.20/flink/src/main/java/.../ColumnStatsWatermarkExtractor.java` (+2/-0 lines)

**修改目的**：添加水印列非空校验。

**工作逻辑**：
```java
Types.NestedField field = schema.findField(eventTimeFieldName);
Preconditions.checkArgument(
    field != null, "Cannot find watermark column: %s", eventTimeFieldName);
TypeID typeID = field.type().typeId();
```

### `flink/v1.20/flink/src/test/java/.../TestColumnStatsWatermarkExtractor.java` (+8/-0 lines)

**修改目的**：测试缺失列场景。

**工作逻辑**：
新增 `testMissingColumn` 测试，验证传入不存在的列名时抛出 `IllegalArgumentException` 且消息为 `"Cannot find watermark column: missing_column"`。

### `flink/v2.0/flink/src/main/java/.../ColumnStatsWatermarkExtractor.java` (+2/-0 lines)

**修改目的**：同 1.20，为 Flink 2.0 添加校验。

### `flink/v2.0/flink/src/test/java/.../TestColumnStatsWatermarkExtractor.java` (+8/-0 lines)

**修改目的**：同 1.20，为 Flink 2.0 添加测试。

### `flink/v2.1/flink/src/main/java/.../ColumnStatsWatermarkExtractor.java` (+2/-0 lines)

**修改目的**：同 1.20，为 Flink 2.1 添加校验。

### `flink/v2.1/flink/src/test/java/.../TestColumnStatsWatermarkExtractor.java` (+8/-0 lines)

**修改目的**：同 1.20，为 Flink 2.1 添加测试。

## 总结

这是一个用户体验改进修复，将水印列不存在时的 `NullPointerException` 替换为包含字段名的 `IllegalArgumentException`。修改简单直接，风险极低，但显著提升了错误诊断体验。修复同时应用于 Flink 1.20、2.0 和 2.1 三个版本。
