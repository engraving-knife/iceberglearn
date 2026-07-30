# 提交 2244：Flink: If IcebergSink writeParallelism is not specified, defaults to the input source parallelism

## 提交信息

- **序号**：2244 / 4088
- **哈希**：3ed8184387e92af3b0bfdfc92f439dba9fe37b9d
- **短哈希**：3ed818438
- **日期**：2025-06-16 13:41:03 -0700
- **作者**：Rodrigo
- **提交说明**：Flink: If IcebergSink writeParallelism is not specified, defaults to the input source parallelism
- **PR/Issue**：#13260

## 总体目的

本提交修复了 Flink IcebergSink 在未指定 writeParallelism 时的默认并行度行为。之前的实现存在不一致：在 `distributeDataStreamByRangeDistributionMode` 方法中，当 writeParallelism 为 null 时默认使用输入源的并行度，但在主 `append()` 方法的最终 sink 设置中，只有 writeParallelism 非 null 时才设置并行度，否则不设置（由 Flink 框架默认决定）。这导致未指定 writeParallelism 时，sink 的并行度可能不等于输入源的并行度，从而阻止了 Flink 算子链（operator chaining）的优化。修复后，统一使用 `resolveWriterParallelism` 方法，当 writeParallelism 为 null 时默认使用输入源并行度，鼓励算子链合并，提升性能。

## 如何达成设计目的

- 新增 `resolveWriterParallelism` 方法，统一并行度解析逻辑：优先使用用户指定的 writeParallelism，未指定时回退到输入 DataStream 的并行度。
- 在 `append()` 方法的 sink 设置处调用该方法，替换原来仅在 writeParallelism 非 null 时设置的条件逻辑。
- 在 `distributeDataStreamByRangeDistributionMode` 方法中也使用该方法，消除重复逻辑。
- 新增两个测试验证默认并行度和显式指定并行度的行为。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +9/-7 lines)

**修改目的**：统一 writeParallelism 的解析逻辑，确保未指定时使用输入源并行度。

**工作逻辑**：
- 新增 `resolveWriterParallelism(DataStream<RowData> input)` 方法，使用 `Optional.ofNullable(flinkWriteConf.writeParallelism()).orElseGet(input::getParallelism)` 实现优先级逻辑。
- 在 `append()` 中，将原来的 `if (sink.flinkWriteConf.writeParallelism() != null) { setParallelism(...) }` 替换为无条件调用 `setParallelism(sink.resolveWriterParallelism(rowDataInput))`，确保即使未指定 writeParallelism 也会显式设置并行度为输入源并行度。
- 在 `distributeDataStreamByRangeDistributionMode` 中，将原来的三元表达式替换为 `resolveWriterParallelism(input)` 调用，消除代码重复。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (修改, +45/-0 lines)

**修改目的**：验证 writeParallelism 的默认和显式指定行为。

**工作逻辑**：
- `testDefaultWriteParallelism`：不指定 writeParallelism，验证 sink transformation 的并行度等于输入 DataStream 的并行度。
- `testWriteParallelism`：指定 writeParallelism 为测试参数 `parallelism`，验证 sink transformation 的并行度等于指定值。

## 总结

本提交统一了 IcebergSink 的 writeParallelism 解析逻辑，确保未指定 writeParallelism 时默认使用输入源并行度，而非交由 Flink 框架决定。这一改动鼓励算子链合并（operator chaining），减少了不必要的数据序列化/反序列化开销，提升了写入性能。
