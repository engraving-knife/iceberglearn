# 提交 3704：Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor for 1.20 (#16323)

## 提交信息

- **序号**：3704 / 4088
- **哈希**：c72c2bdc48e308c8f118f55f9b5c9e053f886894
- **短哈希**：c72c2bdc4
- **日期**：2026-05-13 12:20:48 -0700
- **作者**：Talat UYARER
- **提交说明**：Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor for 1.20 (#16323)
- **PR/Issue**：#16323

## 总体目的

这个提交是 PR #16268（提交 3702）向 Flink 1.20 模块的回移植。它修复了 Flink 1.20 模块中 `SortKeySerializer` 和 `ColumnStatsWatermarkExtractor` 对纳秒时间戳（`TIMESTAMP_NANO`）类型支持缺失的问题，与 Flink 2.0 和 2.1 的修复保持一致。

## 如何达成设计目的

通过与提交 3702 完全相同的修改，将 `TIMESTAMP_NANO` 类型支持添加到 Flink 1.20 模块。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java` (+2 lines)

**修改目的**：支持 TIMESTAMP_NANO 类型的序列化和反序列化。

**工作逻辑**：在序列化和反序列化的 switch 语句中添加 `case TIMESTAMP_NANO:`，与 `TIMESTAMP` 使用相同的 long 读写逻辑。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/reader/ColumnStatsWatermarkExtractor.java` (+15/-4 lines)

**修改目的**：支持从 TIMESTAMP_NANO 类型的列统计中提取 watermark。

### 测试文件 (+54/-2 lines)

**修改目的**：添加测试验证 TIMESTAMP_NANO 支持。

## 总结

这是提交 3702 向 Flink 1.20 的回移植，内容完全一致。至此，所有三个 Flink 版本（1.20、2.0、2.1）都完成了 `TIMESTAMP_NANO` 类型支持的修复，确保跨版本一致性。
