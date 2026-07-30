# 提交 2404：Flink: Add support for SpeculativeExecution for IcebergSink (#13642)

## 提交信息

- **序号**：2404 / 4088
- **哈希**：8047c6b8444b2f940133c9d6a77ffabc32f561be
- **短哈希**：8047c6b84
- **日期**：2025-07-24 11:20:11 +0200
- **作者**：Rodrigo
- **提交说明**：Flink: Add support for SpeculativeExecution for IcebergSink (#13642)
- **PR/Issue**：#13642

## 总体目的

此提交为 Flink IcebergSink 添加了推测执行（Speculative Execution）支持。推测执行是 Flink 的一项优化机制，当某个任务执行缓慢时，调度器会启动该任务的并发副本（speculative attempt），哪个先完成就采用哪个结果，从而减少慢节点对整体作业的影响。

要让 Sink 支持推测执行，Sink 实现必须标记自己能安全地处理并发执行尝试（concurrent execution attempts）。对于 IcebergSink 来说，这是安全的，因为 Iceberg 的提交机制基于快照和乐观并发控制——多个并发的写入尝试会产生各自的数据文件，但只有第一个成功提交的尝试会被采纳，后续重复提交会被检测并处理（通过 commit 重试机制）。

此提交让 `IcebergSink` 实现 `SupportsConcurrentExecutionAttempts` 标记接口，并扩展测试以参数化方式覆盖 V1 和 V2 Sink 两种模式。

## 如何达成设计目的

关键设计点：

1. **实现 SupportsConcurrentExecutionAttempts 接口**：`IcebergSink` 类添加 `SupportsConcurrentExecutionAttempts` 标记接口，告知 Flink 框架该 Sink 可以安全处理并发执行尝试。这是一个标记接口（marker interface），不需要实现任何方法。
2. **测试参数化**：将 `TestIcebergSpeculativeExecutionSupport` 从单一 `@Test` 改为参数化的 `@TestTemplate`，通过 `useV2Sink` 参数（true/false）同时覆盖 V1 Sink 和 V2 Sink（IcebergSink）两种模式。
3. **测试配置**：在测试中通过 `table.exec.iceberg.use-v2-sink` 配置切换 V1/V2 Sink。

## 修改详情

### `flink/v2.0/flink/src/main/java/org/apache/iceberg/flink/sink/IcebergSink.java` (+4/-1 lines)

**修改目的**：让 IcebergSink 支持推测执行。

**工作逻辑**：在 `IcebergSink` 类的 implements 列表中添加 `SupportsConcurrentExecutionAttempts` 接口。这是一个标记接口，不需要实现任何额外方法。Flink 框架在检测到 Sink 实现了此接口后，会允许对该 Sink 的任务启动推测执行尝试。Iceberg 的两阶段提交机制（写入数据文件 + commit 快照）天然支持并发安全——多个尝试各自写入独立的数据文件，commit 时通过乐观锁机制处理冲突。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSpeculativeExecutionSupport.java` (+18/-2 lines)

**修改目的**：扩展推测执行测试以覆盖 V2 Sink。

**工作逻辑**：
- 添加 `@ExtendWith(ParameterizedTestExtension.class)` 注解使测试类支持参数化。
- 新增 `@Parameter(index = 0) private boolean useV2Sink` 字段和 `@Parameters` 方法，参数为 `{true}` 和 `{false}`。
- 将 `testSpeculativeExecution` 从 `@Test` 改为 `@TestTemplate`，使其在参数化下运行。
- 在测试方法中通过 `tEnv.getConfig().set("table.exec.iceberg.use-v2-sink", String.valueOf(useV2Sink))` 切换 V1/V2 Sink，验证两种 Sink 模式都支持推测执行。

## 总结

此提交通过让 `IcebergSink` 实现 `SupportsConcurrentExecutionAttempts` 标记接口，为 Flink V2 IcebergSink 添加了推测执行支持。Iceberg 的乐观并发提交机制天然保证了并发尝试的安全性。测试扩展为参数化模式，同时覆盖 V1 和 V2 Sink，确保两者都能正确处理推测执行。
