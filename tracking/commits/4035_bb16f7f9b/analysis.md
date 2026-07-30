# 提交 4035：Flink: Fix watermark extractor error message showing field id instead of file (#17124)

## 提交信息

- **序号**：4035 / 4088
- **哈希**：bb16f7f9ba83b6f8b0442e0e16813b4cb1a5b96f
- **短哈希**：bb16f7f9b
- **日期**：2026-07-15 07:41:37 +0200
- **作者**：Anas Khan
- **提交说明**：Flink: Fix watermark extractor error message showing field id instead of file (#17124)
- **PR/Issue**：#17124

## 总体目的

本提交修复 Flink `ColumnStatsWatermarkExtractor` 中错误消息的参数错位 bug。原错误消息模板是 `"Missing statistics for column name = %s in file = %s"`，有两个 `%s` 占位符，但传入了三个参数（`eventTimeFieldName`、`eventTimeFieldId`、`scanTask.file()`）。

由于 `Preconditions.checkArgument` 的 varargs 参数匹配，第二个 `%s`（in file =）被填充为 `eventTimeFieldId`（field id），而 `scanTask.file()` 被忽略。结果是错误消息显示"file = <fieldId>"而非"file = <实际文件>"，误导排查——用户看到的是 field id 而非出问题的文件。

本提交修正消息模板，显式包含 fieldId 占位符，使三个参数都正确显示：`"Missing statistics for column name = %s with fieldId = %s in file = %s"`。

## 如何达成设计目的

1. 修改 `ColumnStatsWatermarkExtractor` 中 `Preconditions.checkArgument` 的消息模板，从两个 `%s` 改为三个 `%s`，与传入的三个参数对应。
2. 更新测试断言，验证完整错误消息包含 column name、fieldId 和 file。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/source/reader/ColumnStatsWatermarkExtractor.java` (+1/-1 lines)

**修改目的**：修复错误消息参数错位。

**工作逻辑**：
```java
// 修改前
Preconditions.checkArgument(
    scanTask.file().lowerBounds() != null
        && scanTask.file().lowerBounds().get(eventTimeFieldId) != null,
    "Missing statistics for column name = %s in file = %s",
    eventTimeFieldName,
    eventTimeFieldId,
    scanTask.file());

// 修改后
Preconditions.checkArgument(
    scanTask.file().lowerBounds() != null
        && scanTask.file().lowerBounds().get(eventTimeFieldId) != null,
    "Missing statistics for column name = %s with fieldId = %s in file = %s",
    eventTimeFieldName,
    eventTimeFieldId,
    scanTask.file());
```
现在三个参数（name、fieldId、file）分别填充三个占位符，消息完整准确。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestColumnStatsWatermarkExtractor.java` (+5/-2 lines)

**修改目的**：更新断言验证完整错误消息。

**工作逻辑**：
```java
IcebergSourceSplit split = split(0);
ColumnStatsWatermarkExtractor extractor = new ColumnStatsWatermarkExtractor(10, "missing_field");
assertThatThrownBy(() -> extractor.extractWatermark(split))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessage(
        "Missing statistics for column name = missing_field with fieldId = 10 in file = "
            + split.task().files().iterator().next().file());
```
精确断言完整消息，包含 column name、fieldId（10）和 file 对象。

### `flink/v1.20` 和 `flink/v2.0` 同名文件（各 +5/-2 和 +1/-1 lines）

**修改目的**：同步修复到 Flink 1.20 和 2.0。

**工作逻辑**：与 v2.1 完全相同的改动。

## 总结

本提交修复了 Flink watermark 提取器错误消息中参数错位的 bug，原消息因 `%s` 占位符数量与参数不匹配，导致"in file ="后显示 field id 而非实际文件。修正后消息完整显示 column name、fieldId 和 file，便于排查缺失统计信息的问题。改动同时应用到三个 Flink 版本（1.20、2.0、2.1），并配套更新测试做精确断言。这是一个错误消息可观测性修复，与 4011 类似都关注错误信息的准确性。
