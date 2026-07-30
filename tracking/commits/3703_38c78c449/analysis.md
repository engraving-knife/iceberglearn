# 提交 3703：Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor for v2.0 (#16322)

## 提交信息

- **序号**：3703 / 4088
- **哈希**：38c78c44973805ec15a792dafbe337452df607ce
- **短哈希**：38c78c449
- **日期**：2026-05-13 12:19:31 -0700
- **作者**：Talat UYARER
- **提交说明**：Flink: Nanosecond gaps in SortKeySerializer and ColumnStatsWatermarkExtractor for v2.0 (#16322)
- **PR/Issue**：#16322

## 总体目的

这个提交是 PR #16268（提交 3702）向 Flink 2.0 模块的回移植。它修复了 Flink 2.0 模块中 `SortKeySerializer` 和 `ColumnStatsWatermarkExtractor` 对纳秒时间戳（`TIMESTAMP_NANO`）类型支持缺失的问题，与 Flink 2.1 的修复保持一致。

## 如何达成设计目的

通过与提交 3702 完全相同的修改，将 `TIMESTAMP_NANO` 类型支持添加到 Flink 2.0 模块。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/shuffle/SortKeySerializer.java` (+2 lines)

**修改目的**：支持 TIMESTAMP_NANO 类型的序列化和反序列化。

**工作逻辑**：在序列化和反序列化的 switch 语句中添加 `case TIMESTAMP_NANO:`，与 `TIMESTAMP` 使用相同的 long 读写逻辑。

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/reader/ColumnStatsWatermarkExtractor.java` (+15/-4 lines)

**修改目的**：支持从 TIMESTAMP_NANO 类型的列统计中提取 watermark。

### 测试文件 (+54/-2 lines)

**修改目的**：添加测试验证 TIMESTAMP_NANO 支持。

## 总结

这是提交 3702 向 Flink 2.0 的回移植，内容完全一致。确保 Flink 2.0 和 2.1 模块对 `TIMESTAMP_NANO` 类型的支持保持一致。
