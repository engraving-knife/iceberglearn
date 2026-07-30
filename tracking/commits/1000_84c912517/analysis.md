# 提交 1000：Flink: Backport #10548 to v1.18 and v1.17 (#10776)

## 提交信息

- **序号**：1000 / 4088
- **哈希**：84c91251738cb86f741952bd1b23daa45c80d2aa
- **短哈希**：84c912517
- **日期**：2024-08-01（Thu Aug 1 00:22:51 2024 -0700）
- **作者**：Venkata krishnan Sowrirajan <vsowrirajan@linkedin.com>
- **提交说明**：Flink: Backport #10548 to v1.18 and v1.17 (#10776)
- **PR/Issue**：#10776（回迁自 #10548）

## 总体目的

Flink 支持批作业的推测执行（speculative execution）：当某个 task 执行过慢时，调度器会为同一 subtask 启动额外的"推测"尝试（attempt），先完成的胜出。Flink 的 source API 为支持推测执行提供了 `SupportsHandleExecutionAttemptSourceEvent` 接口，其 `handleSourceEvent(int subTaskId, int attemptNumber, SourceEvent sourceEvent)` 方法让 enumerator 能区分来自不同 attempt 的 source 事件。

此前 Iceberg 的 `AbstractIcebergEnumerator`（FLIP-27 source 的枚举器基类）只实现了 `SplitEnumerator` 的 `handleSourceEvent(int subTaskId, SourceEvent sourceEvent)`，未实现带 `attemptNumber` 的新方法。这意味着在开启推测执行时，Flink 1.18+ 调度器需要 enumerator 能感知 attempt 编号，而 Iceberg enumerator 无法提供，可能导致推测执行场景下的 source 事件处理不正确或集成测试无法验证该能力。

PR #10548 已在 Flink 1.19 模块实现该支持（让 `AbstractIcebergEnumerator` 实现 `SupportsHandleExecutionAttemptSourceEvent`，并把带 attempt 的事件委托到旧方法）。本提交把同一改动回迁到 Flink 1.18 与 1.17 模块，并补充对应模块的推测执行集成测试 `TestIcebergSpeculativeExecutionSupport`，同时微调 1.19 已有测试中一处注释以使三版本一致。

## 如何达成设计目的

1. 让 `AbstractIcebergEnumerator` 在 v1.17/v1.18 中实现 `SupportsHandleExecutionAttemptSourceEvent` 接口，提供 `handleSourceEvent(int subTaskId, int attemptNumber, SourceEvent sourceEvent)` 的实现——直接委托给 `handleSourceEvent(subTaskId, sourceEvent)`。注释说明：Flink 的 `SourceCoordinator` 已维护 subtask 到 split 的映射并负责把 split 重新分配给推测 attempt，因此 enumerator 无需对 attempt 做特殊处理。
2. 把 1.19 已有的 `TestIcebergSpeculativeExecutionSupport` 测试复制到 v1.17/v1.18，验证推测执行下 source 能正常工作。
3. 调整 1.19 该测试中一处注释（`even subtask indices` → `subtasks`），使三版本测试描述一致。

## 修改详情

### `flink/v1.18/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/AbstractIcebergEnumerator.java`（v1.17 同）

**修改目的**：让 enumerator 支持带 attempt 编号的 source 事件，适配推测执行。

**工作逻辑**：
- 新增 import `org.apache.flink.api.connector.source.SupportsHandleExecutionAttemptSourceEvent`；
- 类声明由 `implements SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState>` 改为同时 implements `SupportsHandleExecutionAttemptSourceEvent`；
- 新增方法：

```java
// Flink's SourceCoordinator already keeps track of subTask to splits mapping.
// It already takes care of re-assigning splits to speculated attempts as well.
@Override
public void handleSourceEvent(int subTaskId, int attemptNumber, SourceEvent sourceEvent) {
  handleSourceEvent(subTaskId, sourceEvent);
}
```

即把带 attempt 的事件直接转交给旧的 `handleSourceEvent(subTaskId, sourceEvent)` 处理，因为 split 分配与推测 attempt 重分配由 Flink 的 `SourceCoordinator` 统一管理，enumerator 无需关心 attempt 编号。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSpeculativeExecutionSupport.java`（新增，v1.17 同）

**修改目的**：为 v1.17/v1.18 补充推测执行集成测试，此前仅 1.19 有。

**工作逻辑**：测试类继承 `TestBase`，配置一个 MiniCluster（1 个 task manager、3 个 slot），开启批执行模式与推测执行相关配置（`SlowTaskDetectorOptions`、`JobManagerOptions`、`BatchExecutionOptions` 等）。写入测试数据后用 `RichMapFunction` 让首次 attempt（`attemptNumber <= 0`）的 subtask 长时间 `Thread.sleep(Integer.MAX_VALUE)` 以触发推测执行，验证推测 attempt 能成功完成作业并产出正确结果。该测试与 1.19 已有版本内容基本一致。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSpeculativeExecutionSupport.java`

**修改目的**：统一三版本测试中的注释措辞。

**工作逻辑**：将 `TestingMap.map` 中注释 `// Put the even subtask indices with the first attempt to sleep to trigger speculative execution` 改为 `// Put the subtasks with the first attempt to sleep to trigger speculative execution`（去掉 `even` 一词，因为实际上所有首次 attempt 都会 sleep，而非仅偶数 subtask）。仅注释改动，无逻辑变化。

## 小结

- **成效**：Flink 1.17 与 1.18 模块的 `AbstractIcebergEnumerator` 现也支持推测执行场景下的 source 事件处理，三个 Flink 版本行为一致；并补齐了这两个模块的推测执行集成测试。
- **影响范围**：Flink 1.17、1.18 模块各 2 个文件（1 主代码 + 1 新增测试），1.19 模块 1 个测试文件注释微调，共 5 个文件，389 insertions / 3 deletions。主代码改动极小（约 11 行/模块）。
- **回迁到 1.4.x 的注意事项**：本提交本身即是向旧 Flink 模块的回迁。1.4.x 分支若维护 Flink 1.17/1.18 模块且尚不支持推测执行事件处理，可直接回迁。需注意：`SupportsHandleExecutionAttemptSourceEvent` 接口在 Flink 1.17/1.18 中存在，回迁无 API 缺失风险。推测执行集成测试依赖较重的 MiniCluster 配置与 `SlowTaskDetectorOptions`，回迁后需确认 1.4.x 对应 Flink 版本这些配置项可用。整体风险低，是 bug fix / 兼容性增强性质。
