# 提交 3911：Flink: Add equality delete conversion committer (#16874)

## 提交信息

- **序号**：3911 / 4088
- **哈希**：8d1f973ba9717c51070a0b7c2cec76af7660576a
- **短哈希**：8d1f973ba
- **日期**：2026-06-19 22:45:17 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add equality delete conversion committer (#16874)
- **PR/Issue**：#16874

## 总体目的

为 Flink equality delete 转换任务添加提交器算子 `EqualityConvertCommitter`。这是从大型 PR #15996 拆分出的第四个提交（前三个是数据模型 #16831、算子 #16844、DV 写入器 #16858），完成了 equality delete 转换处理链路的最后一个组件。

`EqualityConvertCommitter` 是一个双输入算子，缓冲来自并行 `EqualityConvertDVWriter` 实例的 `DVWriteResult` 和来自 Planner 的 `EqualityConvertPlan`，当两者都到达后执行提交。提交器负责：
- 添加新的 staging 数据文件和 writer 合并后的 DV
- 移除被替代的 DV
- 保留剩余的 staging deletes
- 验证 main 快照未被外部修改
- 记录已处理的 staging snapshot id 以实现幂等性
- 在失败时清理已写入的 DV 文件

## 如何达成设计目的

实现双输入 Flink 算子，利用 watermark 同步机制确保 plan 和 DV 结果都就绪后才提交。提交逻辑包含文件添加/移除/保留的完整管理，以及失败时的资源清理。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertCommitter.java` (+363 lines, new file)

**修改目的**：实现 equality delete 转换的提交器算子。

**工作逻辑**：
- 双输入算子：第一输入接收 DVWriteResult，第二输入接收 EqualityConvertPlan
- 缓冲 DVWriteResults 直到 plan 和其 done-timestamp watermark 都到达
- 提交操作：
  - 添加新 staging 数据文件和合并后的 DV
  - 移除被替代的旧 DV
  - 保留剩余 staging deletes
- 验证：对比 planner 的 main 快照，外部修改导致提交失败
- 幂等性：提交摘要记录已处理的 staging snapshot id，planner 重启时读取以跳过已提交快照
- Trigger 发送：每个周期后（包括 no-op 和 error）向 TaskResultAggregator 发送 Trigger，确保任务总能完成
- 失败清理：上游 abort 或提交失败时删除本周期写入的 DV 文件，避免泄漏 Puffin 文件

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertCommitter.java` (+524 lines, new file)

**修改目的**：测试提交器算子的各种场景。

**工作逻辑**：
覆盖测试场景包括：
- 正常提交流程
- 共享分支 DV 合并回归测试
- main 快照变更检测
- 上游 abort 信号处理
- 提交失败后的 DV 清理
- no-op 周期处理
- 幂等性验证

## 总结

为 equality delete 转换任务实现了提交器算子，完成了从 Reader -> PK Index -> DV Writer -> Committer 的完整处理链路。提交器负责将转换结果原子性地提交到目标分支，包含文件管理、快照验证、幂等性保证和失败清理等完整逻辑。至此，equality delete 到 deletion vector 转换的 Flink 作业核心组件已全部就绪，配有超过 500 行详细测试覆盖各种正常和异常场景。
