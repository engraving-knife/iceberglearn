# 提交 3905：Flink: Add equality delete conversion DV resolution and writing (#16858)

## 提交信息

- **序号**：3905 / 4088
- **哈希**：a3a755897fc43029adeea92f1bdb645903852ac1
- **短哈希**：a3a755897
- **日期**：2026-06-18 19:52:43 -0700
- **作者**：Maximilian Michels
- **提交说明**：Flink: Add equality delete conversion DV resolution and writing (#16858)
- **PR/Issue**：#16858

## 总体目的

为 Flink equality delete 转换任务添加 DV（Deletion Vector）写入器算子 `EqualityConvertDVWriter`。这是从大型 PR #15996 拆分出的第三个提交（前两个是数据模型 #16831 和算子 #16844），实现了 DV 的解析和写入逻辑。

`EqualityConvertDVWriter` 是一个双输入算子，按数据文件路径 keyed，消费 `EqualityConvertPKIndex` 发出的 `DVPosition`，为 Committer 生成 `DVWriteResult`。按数据文件路径 keying 确保每个数据文件的所有删除位置汇聚到同一个 task，使算子能为每个文件写入恰好一个 DV。

V3 格式允许每个数据文件只有一个 DV，因此写入器需要将已有的 DV（包括 staging 数据文件的新 DV）合并到重写中。删除 manifest 首先通过分区摘要裁剪，将读取范围限制在本周期受影响的分区而非表的全部 DV 历史。

## 如何达成设计目的

实现双输入 Flink 算子，利用 watermark 机制控制 DV 写入时机：位置被缓冲直到 watermark 到达周期计划时间戳（通过第二输入广播），此时解析并写入 DV。包含失败快速检测和 abort 信号传播机制。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/EqualityConvertDVWriter.java` (+354 lines, new file)

**修改目的**：实现 DV 写入算子。

**工作逻辑**：
- 双输入算子，按数据文件路径 keyed
- 消费 DVPosition，按文件缓冲删除位置
- 当 watermark 到达周期计划时间戳时（通过第二输入广播的 EqualityConvertPlan），解析并写入 DV
- 将已有 DV（包括 staging DV）合并到重写中（V3 每文件仅一个 DV）
- 删除 manifest 先按分区摘要裁剪，限制读取范围
- 如果 main 分支快照自规划后已变更，快速失败
- 收到上游 abort 信号或写入失败时，发出 `DVWriteResult.ABORT`

### `flink/v2.1/build.gradle` (+1 line)

**修改目的**：添加 DV 写入算子所需的构建依赖。

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/maintenance/operator/OperatorTestBase.java` (+12 lines)

**修改目的**：为 DV 写入器测试添加公共基础设施。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/maintenance/operator/TestEqualityConvertDVWriter.java` (+472 lines, new file)

**修改目的**：测试 DV 写入器的各种场景。

**工作逻辑**：
覆盖测试场景包括：
- 正常 DV 写入
- 已有 DV 的合并
- staging DV 的处理
- main 快照变更导致的 abort
- 上游 abort 信号传播
- 写入失败处理
- 分区裁剪

## 总结

为 equality delete 转换任务实现了 DV 写入器算子，负责将 PK Index 解析出的删除位置写入 deletion vector 文件。算子利用 Flink 的双输入和 watermark 机制控制写入时机，处理 V3 单 DV 约束下的合并逻辑，并通过分区裁剪优化读取范围。配有近 500 行测试覆盖正常和异常场景。该算子与之前的 Reader、PK Index 共同构成了 equality delete 转换的核心处理链路，Committer 将在后续 PR 中添加。
