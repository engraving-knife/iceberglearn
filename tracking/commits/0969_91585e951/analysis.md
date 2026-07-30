# 提交 0969：Support for Flink's SpeculativeExecution in batch execution mode (#10548)

## 提交信息

- **序号**：0969 / 4088
- **哈希**：91585e9515ea8e46b981e4a5966bbc9a31966443
- **短哈希**：91585e951
- **日期**：2024-07-24 07:18:50 +0200
- **作者**：Venkata krishnan Sowrirajan
- **提交说明**：Support for Flink's SpeculativeExecution in batch execution mode (#10548)
- **PR/Issue**：#10548

## 总体目的

Flink 在批执行模式（`RuntimeExecutionMode.BATCH`）下支持"投机执行（Speculative Execution）"——当某个 task 的执行明显慢于同阶段其他 task 时，JobManager 会启动该 task 的额外 attempt（副本），哪个先完成就用哪个结果，从而避免少数慢节点拖累整个作业。这是 Flink 1.17 起新引入的能力，对长尾延迟敏感的批处理作业很有价值。

要让一个 Source 支持投机执行，Source 必须实现 Flink 的 `SupportsHandleExecutionAttemptSourceEvent` 接口（位于 `org.apache.flink.api.connector.source`），否则 `SourceCoordinator` 在向 source 发送携带 `attemptNumber` 的 `SourceEvent` 时会因为接口未实现而抛错或无法路由。Iceberg 的 `AbstractIcebergEnumerator` 此前只实现 `SplitEnumerator` 接口，其 `handleSourceEvent(int subTaskId, SourceEvent sourceEvent)` 是双参数版本，没有针对 `attemptNumber` 的重载，因此当用户在批模式下对 Iceberg source 启用投机执行时，会因缺少该接口实现而无法工作。

本提交的目标是让 Iceberg 的 FLIP-27 source（即新的 `IcebergSource` + `AbstractIcebergEnumerator`）在 Flink 批执行模式下支持投机执行，使长尾读取场景可以通过投机副本来加速。

## 如何达成设计目的

设计思路非常简洁，依赖一个关键事实：Flink 的 `SourceCoordinator` 内部已经维护了"subtask 到 splits 的映射"，并负责把 splits 重新分配给投机 attempt，因此 Iceberg enumerator 不需要为投机 attempt 做任何特殊的 split 分配逻辑——只要 enumerator 能够接收携带 `attemptNumber` 的 `SourceEvent` 即可。

具体做法是：

1. 让 `AbstractIcebergEnumerator` 在已实现 `SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState>` 的基础上**额外实现 `SupportsHandleExecutionAttemptSourceEvent` 接口**。这个接口是 Flink 1.17 引入的标记接口，要求实现 `handleSourceEvent(int subTaskId, int attemptNumber, SourceEvent sourceEvent)`。
2. 在 `AbstractIcebergEnumerator` 中实现这个三参数方法，方法体直接转发到既有的两参数 `handleSourceEvent(subTaskId, sourceEvent)`——也就是说 Iceberg enumerator 当前并不区分 attemptNumber，所有 attempt（包括投机副本）发送的 source event 都按相同的逻辑处理；split 的分配由 Flink 的 SourceCoordinator 在更上层完成。
3. 新增端到端测试 `TestIcebergSpeculativeExecutionSupport`，构建一个最小可复现的批作业：source 读取一张含 3 行的 Iceberg 表，经过一个 `TestingMap` 让第一次 attempt 永远 sleep（模拟慢任务），从而触发 Flink 的慢任务检测器启动投机 attempt，投机 attempt 快速完成；测试断言输出表中至少有一条记录的 `attemptNumber > 0`，并且所有原始行都被正确写入（保证投机执行下结果不丢不重）。

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/source/enumerator/AbstractIcebergEnumerator.java`

**修改目的**：让 Iceberg enumerator 支持接收携带 attempt 信息的 source event，从而兼容 Flink 的投机执行。

**工作逻辑**：
- 新增 import `org.apache.flink.api.connector.source.SupportsHandleExecutionAttemptSourceEvent`；
- 类签名在 `implements SplitEnumerator<IcebergSourceSplit, IcebergEnumeratorState>` 之后追加 `SupportsHandleExecutionAttemptSourceEvent`；
- 新增方法 `@Override public void handleSourceEvent(int subTaskId, int attemptNumber, SourceEvent sourceEvent)`，方法体仅调用 `handleSourceEvent(subTaskId, sourceEvent)`，并在注释中说明"Flink 的 SourceCoordinator 已经维护了 subtask 到 splits 的映射，并负责把 splits 重新分配给投机 attempt"。也就是说 Iceberg enumerator 不需要为投机执行做额外的状态管理，靠 Flink 框架本身处理。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSpeculativeExecutionSupport.java`（新增）

**修改目的**：通过端到端集成测试验证 Iceberg source 在 Flink 批执行模式下能正确支持投机执行。

**工作逻辑**：
- 类继承 `TestBase`，使用 `@RegisterExtension static MiniClusterExtension` 启动 1 个 TaskManager、3 个 slot 的 mini cluster，配置走 `configure()` 方法。
- `configure()` 中：禁用 classloader leak 检查；`RestOptions.BIND_PORT = "0"` 随机端口；`JobManagerOptions.SLOT_REQUEST_TIMEOUT = 5000`；启用 `FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE = true`（即用新的 FLIP-27 source）；启用 `BatchExecutionOptions.SPECULATIVE_ENABLED = true`；调低慢任务检测阈值（`EXECUTION_TIME_BASELINE_MULTIPLIER = 1.0`、`EXECUTION_TIME_BASELINE_RATIO = 0.2`、`EXECUTION_TIME_BASELINE_LOWER_BOUND = Duration.ofMillis(0)`、`BLOCK_SLOW_NODE_DURATION = Duration.ofMillis(0)`）以便快速触发投机执行。
- `getTableEnv()` 在 `BATCH` 模式下创建 `StreamTableEnvironment`。
- `before()` 在临时 warehouse 下建 catalog/database，建输入表 `test_table (i INT, j INT)` 并插入 3 行 `(1,-1),(2,-1),(3,-1)`，建输出表 `sink_table (i INT, j INT, subTask INT, attempt INT)`。
- `after()` 顺序 drop 输入表、输出表、database、catalog。
- `testSpeculativeExecution()` 测试方法：从输入表 SELECT 出 `Table`，转成 `DataStream<Row>`，经过自定义 `TestingMap`，并行度设为 `NUM_TASK_SLOTS = 3`，输出 schema 为 `(i, j, subTask, attempt)`；写入 sink 表后读取所有结果；断言 `attempt` 字段构成的集合中包含 `1`（即至少有一个 task 触发了 attempt 1，证明投机执行确实发生）；调用 `assertSameElements` 验证输出的 `(i, j)` 与输入的 3 行完全一致（保证投机执行下结果正确不丢）。
- 内部静态类 `TestingMap extends RichMapFunction<Row, Row>`：在 `map()` 中检查 `getRuntimeContext().getTaskInfo().getAttemptNumber() <= 0`（第一次 attempt）时 `Thread.sleep(Integer.MAX_VALUE)`（永远 sleep 模拟慢任务），其他 attempt 正常返回带 `subTask` 与 `attempt` 字段的 Row。这样 Flink 的慢任务检测器会很快给被卡住的 task 启动投机副本，副本正常完成后整个作业结束。

## 小结

- **成效**：Iceberg 的 FLIP-27 source 在 Flink 批执行模式下可以正确支持投机执行——通过让 `AbstractIcebergEnumerator` 实现 `SupportsHandleExecutionAttemptSourceEvent` 接口，把三参数 `handleSourceEvent` 委托给既有两参数版本，依赖 Flink `SourceCoordinator` 完成 splits 到投机 attempt 的重分配；新增的端到端测试用故意 sleep 的 map 触发投机副本，验证了投机 attempt 确实被启动且结果正确不丢不重。
- **影响范围**：仅 Flink 1.19 目录下 2 个文件：`AbstractIcebergEnumerator.java`（主代码，新增 import、接口实现、1 个委托方法）与新增测试 `TestIcebergSpeculativeExecutionSupport.java`。改动只对 Flink 批执行 + 投机执行启用的场景生效，不影响流式或非投机批作业的行为。
- **回迁到 1.4.x 的注意事项**：本提交依赖 Flink 1.17+ 才有的 `SupportsHandleExecutionAttemptSourceEvent` 接口，**只适合回迁到 1.4.x 上对应的 Flink 版本目录**（如 `flink/v1.17`、`flink/v1.18`、`flink/v1.19`）。回迁时需注意：(1) 1.4.x 上是否已经引入了 FLIP-27 source（`FlinkConfigOptions.TABLE_EXEC_ICEBERG_USE_FLIP27_SOURCE`）与 `IcebergSource`/`AbstractIcebergEnumerator`，如果只有老的 source 实现，则本提交不适用；(2) 测试用到的 `MiniClusterExtension`、`SlowTaskDetectorOptions`、`BatchExecutionOptions.SPECULATIVE_ENABLED` 等需要 Flink 1.17+ 的依赖，1.4.x 上若 Flink 版本低于 1.17 则无法运行测试；(3) 主代码改动量很小且无破坏性，回迁风险低，但应作为整体 FLIP-27 source 能力的一部分一起评估。如果 1.4.x 上确认有对应 Flink 版本与 FLIP-27 source，建议回迁以获得投机执行支持能力。
