# 提交 2246：Flink: Backports #13260 to Flink 1.19 and 1.20

## 提交信息

- **序号**：2246 / 4088
- **哈希**：7dc4a66b423d0b8d05e4c9d5f8669a92480ee11d
- **短哈希**：7dc4a66b4
- **日期**：2025-06-16 14:53:47 -0700
- **作者**：Rodrigo
- **提交说明**：Flink: Backports #13260 to Flink 1.19 and 1.20
- **PR/Issue**：#13326（backports #13260）

## 总体目的

本提交是将 PR #13260（提交 2244）的修改反向移植到 Flink 1.19 和 1.20 版本。原始 PR 修复了 IcebergSink 在未指定 writeParallelism 时默认使用输入源并行度的行为，确保算子链（operator chaining）能被正确合并。由于 Iceberg 为不同的 Flink 版本（1.19、1.20、2.0）分别维护代码，同一修复需要在所有版本中同步应用。本提交完成了 v1.19 和 v1.20 两个版本的同步。

## 如何达成设计目的

- 在 Flink 1.19 和 1.20 的 `IcebergSink.java` 中应用与 v2.0 相同的修改：新增 `resolveWriterParallelism` 方法，统一并行度解析逻辑。
- 在两个版本的 `TestIcebergSink.java` 中新增 `testDefaultWriteParallelism` 和 `testWriteParallelism` 测试。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +9/-7 lines)

**修改目的**：统一 writeParallelism 解析逻辑，确保未指定时使用输入源并行度。

**工作逻辑**：与提交 2244 相同的修改——新增 `resolveWriterParallelism` 方法，在 `append()` 和 `distributeDataStreamByRangeDistributionMode` 中统一使用该方法解析并行度。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (修改, +45/-0 lines)

**修改目的**：验证默认并行度和显式指定并行度的行为。

**工作逻辑**：新增 `testDefaultWriteParallelism` 和 `testWriteParallelism` 两个测试用例。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (修改, +9/-7 lines)

**修改目的**：同 v1.19 的修改。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (修改, +45/-0 lines)

**修改目的**：同 v1.19 的测试新增。

## 总结

本提交是 PR #13260 的 backport，将 IcebergSink writeParallelism 默认值修复同步到 Flink 1.19 和 1.20 版本，确保所有 Flink 版本的行为一致。
