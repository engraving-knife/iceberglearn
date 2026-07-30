# 提交 2139：Flink: Fix watermark no pass in TaskResultAggregator

## 提交信息

- **序号**：2139 / 4088
- **哈希**：b050314a035f7916667e8441de6f100eafcc9ddb
- **短哈希**：b050314a0
- **日期**：2025-05-17 03:01:00 +0800
- **作者**：GuoYu
- **提交说明**：Flink: Fix watermark no pass in TaskResultAggregator (#13069)
- **PR/Issue**：#13069

## 总体目的

这个提交修复了 Flink 的 TaskResultAggregator 中 watermark 不传递的 bug。TaskResultAggregator 是 Flink 维护任务中的一个流操作算子，负责聚合任务执行结果。在 Flink 的流处理中，watermark 是用于事件时间处理的关键机制，它需要在算子链中正确传递以驱动下游算子的时间推进。然而 TaskResultAggregator 的 `processWatermark` 方法在处理 watermark 时只输出了 TaskResult 记录，却没有调用 `super.processWatermark(mark)` 将 watermark 继续向下游传递。这导致下游算子无法收到 watermark，可能造成事件时间处理停滞。这个提交通过在方法末尾添加对父类 `processWatermark` 的调用来修复此问题。

## 如何达成设计目的

1. 修改 TaskResultAggregator 的 `processWatermark` 方法签名，添加 `throws Exception` 声明（因为父类方法声明了异常）。
2. 在 `processWatermark` 方法末尾添加 `super.processWatermark(mark)` 调用，确保 watermark 被正确传递给下游。
3. 新增 TestTaskResultAggregator 测试类，验证 watermark 被正确传递。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/TaskResultAggregator.java` (修改, +3/-1 lines)

**修改目的**：修复 watermark 不传递的问题。

**工作逻辑**：
- 将 `processWatermark(Watermark mark)` 方法签名改为 `processWatermark(Watermark mark) throws Exception`，以匹配父类 AbstractStreamOperator 的方法签名。
- 在方法末尾（清理 exceptions 和 startTime 之后）添加 `super.processWatermark(mark);` 调用。原来该方法只输出 TaskResult 记录和清理状态，但没有将 watermark 传递给下游，导致下游算子的 watermark 推进中断。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestTaskResultAggregator.java` (新增, +45 lines)

**修改目的**：验证 watermark 在 TaskResultAggregator 中被正确传递。

**工作逻辑**：测试类继承 OperatorTestBase，使用 TwoInputStreamOperatorTestHarness 测试框架。`testPassWatermark` 测试方法创建 TaskResultAggregator 实例，通过 testHarness 分别在两个输入通道上发送 watermark，然后验证输出队列中包含且仅包含一次该 watermark，确认 watermark 被正确传递。

## 总结

这个提交修复了 TaskResultAggregator 中 watermark 不传递的重要 bug。由于 `processWatermark` 方法没有调用父类的实现，watermark 在经过该算子时被"吞掉"，导致下游算子无法推进事件时间。修复方法简单直接，仅需在方法末尾添加 `super.processWatermark(mark)` 调用。该修复后续被 backport 到 Flink 1.19 和 1.20 版本。
