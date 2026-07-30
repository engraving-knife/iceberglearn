# 提交 3583：Flink: Fix watermark value which should be min timestamp minus one (#15884)

## 提交信息

- **序号**：3583 / 4088
- **哈希**：8c217a35b0eb78f04b4127e38eee0683bb6f1931
- **短哈希**：8c217a35b
- **日期**：2026-04-24 22:28:41 +0200
- **作者**：Chase Zhang
- **提交说明**：Flink: Fix watermark value which should be min timestamp minus one (#15884)
- **PR/Issue**：#15884

## 总体目的

该提交修复了 Flink Iceberg Source 中 `WatermarkExtractorRecordEmitter` 的 watermark 值计算错误。在 Flink 中，watermark W 表示所有事件时间 <= W 的记录已经到达。`WatermarkExtractorRecordEmitter` 从 split 中提取最小时间戳（`extractWatermark`），但之前直接将该最小时间戳作为 watermark 发射。

这是不正确的：因为 split 中的记录的事件时间可能等于该最小时间戳，如果将 watermark 设为该最小时间戳，则这些记录会被认为是"已过期"（late），可能在窗口计算中被丢弃。正确的做法是将 watermark 设为最小时间戳减一（`extracted - 1`），这样事件时间等于最小时间戳的记录仍然被视为 on-time（严格大于 watermark）。

此外，`watermark` 字段的初始值从默认值 0 改为 `Long.MIN_VALUE`，避免在第一个 split 处理前 watermark 为 0 导致的潜在问题。同时处理了 `Long.MIN_VALUE` 溢出的边界情况（`extracted > Long.MIN_VALUE ? extracted - 1 : Long.MIN_VALUE`）。

## 如何达成设计目的

在 `emitRecord` 方法中，当遇到新的 split 时，将提取的水印值减一再作为新 watermark。修改 `watermark` 字段初始值为 `Long.MIN_VALUE`。该修复覆盖 Flink 1.20、2.0、2.1 三个版本。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/source/reader/WatermarkExtractorRecordEmitter.java` (+7/-2 lines)

**修改目的**：修复 watermark 值计算。

**工作逻辑**：
```java
private long watermark = Long.MIN_VALUE; // 从默认 0 改为 Long.MIN_VALUE

public void emitRecord(...) {
  if (!split.splitId().equals(lastSplitId)) {
    long extracted = timeExtractor.extractWatermark(split);
    // watermark W 表示所有 eventTime <= W 的记录已到达
    // split 中记录的 eventTime == extracted，所以 watermark 必须是 extracted - 1
    long newWatermark = extracted > Long.MIN_VALUE ? extracted - 1 : Long.MIN_VALUE;
    // ... 后续比较逻辑不变
  }
}
```

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java` (+62/-0 lines)

**修改目的**：集成测试验证最小时间戳记录的窗口包含。

**工作逻辑**：
`testWindowingWithRecordsAtSplitMinTimestamp`：
- 写入 3 条事件时间为 t=0 的记录，split 的列统计下界为 0，提取的 watermark 为 0ms，发射的 watermark 为 -1ms。
- 设置 5 分钟滚动窗口 [0, 5min)，验证 t=0 的记录被正确包含在该窗口中。
- 追加一条更晚时间戳的记录以推进 watermark 超过窗口边界，触发窗口计算。
- 验证 [0, 5min) 窗口中有 3 条记录。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/source/reader/TestWatermarkExtractorRecordEmitter.java` (+164/-0 lines, new file)

**修改目的**：单元测试 watermark emitter 行为。

**工作逻辑**：
测试 `WatermarkExtractorRecordEmitter` 的核心逻辑，包括：
- 验证 watermark 为 extracted - 1。
- 验证 split 切换时 watermark 的更新和比较逻辑。
- 验证 Long.MIN_VALUE 边界处理。

### `flink/v2.0/...` 和 `flink/v2.1/...` 对应文件

**修改目的**：将相同修复应用到 Flink 2.0 和 2.1 版本。

**工作逻辑**：与 v1.20 版本的改动完全一致。

## 总结

该提交修复了 Flink Iceberg Source 中 watermark 计算的 off-by-one 错误。之前直接将 split 最小时间戳作为 watermark，导致事件时间等于最小时间戳的记录在窗口计算中被错误地视为 late。修复后 watermark 为最小时间戳减一，确保这些记录被视为 on-time。同时修复了 watermark 初始值和 Long.MIN_VALUE 边界处理。测试覆盖单元测试和集成测试两个层面。
