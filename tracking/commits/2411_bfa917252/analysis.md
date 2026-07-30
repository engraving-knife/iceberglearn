# 提交 2411：Flink: Backport: Adds support for SpeculativeExecution for IcebergSink (#13663)

## 提交信息

- **序号**：2411 / 4088
- **哈希**：bfa9172525fa48acfc46a2fab18055e2fb99e90b
- **短哈希**：bfa917252
- **日期**：2025-07-24 22:05:10 +0200
- **作者**：Rodrigo
- **提交说明**：Flink: Backport: Adds support for SpeculativeExecution for IcebergSink (#13663)
- **PR/Issue**：#13663（backport of #13642）

## 总体目的

本提交是 PR #13642 向 1.4.x 分支的回溯。它为 Flink IcebergSink（V2 Sink）添加了对推测执行（Speculative Execution）的支持。

推测执行是 Flink 的一种优化机制：当一个任务执行缓慢时，Flink 可以启动该任务的并发执行副本（speculative attempt），取先完成的那个结果。这对于处理慢节点（straggler）问题非常有用。然而，并非所有算子都支持并发执行尝试——只有声明支持此特性的算子才能被安全地推测执行。

在此之前，Flink Iceberg Source 已经支持了推测执行，但 IcebergSink（V2）尚未声明支持。本提交让 IcebergSink 实现 `SupportsConcurrentExecutionAttempts` 接口，使其也能受益于推测执行机制。

## 如何达成设计目的

设计非常简洁：

1. 让 `IcebergSink` 类实现 Flink 的 `SupportsConcurrentExecutionAttempts` 标记接口。这是一个空接口（marker interface），实现它即声明该 Sink 可以安全地被并发执行。
2. IcebergSink 能够安全支持并发执行的前提是其 commit 阶段是幂等的——通过 Flink 的 committer 机制，写入的数据文件只有 commit 成功后才会被提交到 Iceberg 表，多个并发的 writer attempt 产生的数据文件中，只有最终成功的那个会被 commit，其余的会被清理。

关键设计点：
- 仅需添加接口声明，不需要实现任何额外方法，因为 IcebergSink 的设计本身就保证了 writer 的并发安全性
- 测试方面，将已有的推测执行测试参数化，使其同时覆盖 V1 Sink 和 V2 Sink 两种模式

## 修改详情

### `flink/v1.19/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+4/-1 lines)

**修改目的**：让 IcebergSink 声明支持并发执行尝试。

**工作逻辑**：
- 新增 `import org.apache.flink.api.common.SupportsConcurrentExecutionAttempts;` 导入
- 在 `IcebergSink` 类的 implements 列表中添加 `SupportsConcurrentExecutionAttempts` 接口。`IcebergSink` 已经实现了多个 Flink Sink V2 接口（`SupportsPreWriteTopology`、`SupportsCommitter`、`SupportsPreCommitTopology`、`SupportsPostCommitTopology`），新增此接口后即声明该 Sink 可以被 Flink 推测执行。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSpeculativeExecutionSupport.java` (+18/-2 lines)

**修改目的**：将推测执行测试扩展为参数化测试，同时覆盖 V1 Sink 和 V2 Sink。

**工作逻辑**：
- 使用 Iceberg 自定义的 `ParameterizedTestExtension` 将测试类改为参数化测试
- 新增 `useV2Sink` 布尔参数，参数集为 `{true}, {false}`
- 将 `@Test` 注解改为 `@TestTemplate`，以支持参数化运行
- 在 `testSpeculativeExecution` 方法中通过 `tEnv.getConfig().set("table.exec.iceberg.use-v2-sink", ...)` 设置使用 V1 还是 V2 Sink
- 这样同一个测试逻辑会分别在 V1 Sink 和 V2 Sink 模式下各运行一次

### Flink v1.20 版本的对应文件

同样的修改被应用到 `flink/v1.20` 版本的 `IcebergSink.java` 和 `TestIcebergSpeculativeExecutionSupport.java`，改动内容完全一致。

## 总结

本提交通过简单地为 IcebergSink 添加 `SupportsConcurrentExecutionAttempts` 标记接口，使其支持 Flink 的推测执行机制。这意味着当 IcebergSink 的 writer 任务执行缓慢时，Flink 可以启动并发副本来加速处理。由于 IcebergSink 的 commit 机制保证了幂等性，并发执行是安全的。测试扩展为参数化模式以确保 V1 和 V2 Sink 都得到覆盖验证。
