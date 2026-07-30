# 提交 2146：Flink: Backport fix watermark no pass in TaskResultAggregator to Flink 1.19 and 1.20

## 提交信息

- **序号**：2146 / 4088
- **哈希**：4ca3f10832907540eee798236fa401eb07c7e7df
- **短哈希**：4ca3f1083
- **日期**：2025-05-19 19:25:31 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Backport fix watermark no pass in TaskResultAggregator to Flink 1.19 and 1.20 (#13085)
- **PR/Issue**：#13085（backport #13069）

## 总体目的

这个提交是将 #13069（提交 2139）中修复的 watermark 不传递 bug backport 到 Flink 1.19 和 1.20 版本。原始修复只在 Flink 2.0 中实现，但 Flink 1.19 和 1.20 作为仍然广泛使用的版本，同样存在 TaskResultAggregator 中 watermark 不传递的问题，会导致下游算子的事件时间推进中断。这个 backport 确保了旧版本 Flink 用户也能获得此修复。

## 如何达成设计目的

1. 将 Flink 2.0 中修复后的 TaskResultAggregator.java 和新增的 TestTaskResultAggregator.java 复制到 Flink 1.19 和 1.20 对应的目录中。
2. 代码内容与原始修复完全一致，在 processWatermark 方法末尾添加 super.processWatermark(mark) 调用。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (修改, +3/-1 lines)

**修改目的**：修复 Flink 1.19 中 watermark 不传递的 bug。

**工作逻辑**：与提交 2139 相同，在 processWatermark 方法签名添加 throws Exception，并在方法末尾添加 super.processWatermark(mark) 调用。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTaskResultAggregator.java` (新增, +45 lines)

**修改目的**：为 Flink 1.19 的修复提供测试验证。

**工作逻辑**：与 Flink 2.0 版本的测试类相同，验证 watermark 被正确传递。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (修改, +3/-1 lines)

**修改目的**：修复 Flink 1.20 中 watermark 不传递的 bug。

**工作逻辑**：与 Flink 1.19 版本完全相同。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTaskResultAggregator.java` (新增, +45 lines)

**修改目的**：为 Flink 1.20 的修复提供测试验证。

**工作逻辑**：与 Flink 1.19 版本的测试类相同。

## 总结

这个提交是 #13069 的 backport，将 watermark 不传递的修复扩展到 Flink 1.19 和 1.20 版本。代码与原始修复完全一致，确保了所有受支持的 Flink 版本都能正确处理 watermark 传递。
