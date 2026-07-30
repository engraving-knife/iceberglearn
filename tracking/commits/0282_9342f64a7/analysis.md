# 提交 0282：Flink: Fix TestIcebergSourceWithWatermarkExtractor flakiness (#9309)

## 提交信息

- **序号**：0282 / 4088
- **哈希**：9342f64a713ed4a1c540046ae5db91b3ac86ac02
- **短哈希**：9342f64a7
- **日期**：2023-12-18 09:13:21 +0100
- **作者**：pvary
- **提交说明**：Flink: Fix TestIcebergSourceWithWatermarkExtractor flakiness (#9309)
- **PR/Issue**：#9309

## 总体目的

`TestIcebergSourceWithWatermarkExtractor` 是 Flink Iceberg 集成中验证水位线提取器（watermark extractor）和限流（throttling）行为的关键测试。该测试存在严重的 flakiness（不稳定性）问题，即在不修改代码的情况下，测试有时通过、有时失败，严重影响了 CI 的可靠性和开发效率。

该 flakiness 的根因是多方面的。首先，测试中的 `InMemoryReporter` 被声明为 `static final`，这意味着它在所有测试方法之间共享状态，导致指标数据在测试间泄漏，使基于指标的断言不可靠。其次，测试数据插入的时序存在问题：原始代码在启动 Flink 作业之前就先把数据写入了 Iceberg 表，这意味着作业启动时数据已经存在，但作业内部的 source reader 可能尚未就绪，导致数据消费的时序不确定。第三，Flink 的 classloader 检查和 source split 对齐配置在不同 Flink 版本中的行为不一致，也加剧了不稳定性。此外，`org.slf4j` 依赖未在 build.gradle 中排除，可能引发 SLF4J 绑定冲突。

本提交通过多管齐下的方式消除这些不稳定性来源，使测试在各种时序条件下都能确定性通过。

## 如何达成设计目的

修复策略包含四个方面：将 `InMemoryReporter` 从静态字段改为实例字段以隔离测试状态；在 build.gradle 中排除 `org.slf4j` 依赖以避免日志绑定冲突；重构 `testThrottling` 测试方法，将所有测试数据预先创建好但延迟到作业所有 task 都运行后再插入第一批数据，确保数据消费时序可控；并针对不同 Flink 版本（v1.17/v1.18 启用 `ALLOW_UNALIGNED_SOURCE_SPLITS`，v1.16 移除不再需要的 `CHECK_LEAKED_CLASSLOADER` 设置）调整配置。这些修改覆盖了 Flink v1.16、v1.17、v1.18 三个版本。

## 修改详情

### `flink/v1.16/build.gradle`、`flink/v1.17/build.gradle`、`flink/v1.18/build.gradle`

**修改目的**：排除 `org.slf4j` 依赖，避免 SLF4J 绑定冲突。

**工作逻辑**：
在三个 Flink 版本的 build.gradle 中，针对 `iceberg-flink`（主模块）、`iceberg-flink-runtime` 以及相关依赖配置块，分别增加了 `exclude group: 'org.slf4j'`。这防止了测试类路径上出现多个 SLF4J 绑定，避免了因日志框架冲突导致的运行时异常和测试不稳定。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`（及 v1.17、v1.18 对应文件）

**修改目的**：消除测试 flakiness 的多个根因。

**工作逻辑**：

1. **`InMemoryReporter` 改为实例字段**：
   原来是 `private static final InMemoryReporter reporter = ...`，改为 `private final InMemoryReporter reporter = ...`。这确保每个测试实例拥有独立的 reporter，避免指标状态在测试方法间泄漏。

2. **配置调整**：
   - v1.16：移除了 `CoreOptions.CHECK_LEAKED_CLASSLOADER` 设置（原注释为 "disable classloader check as Avro may cache class in the serializers"），简化为 `reporter.addToConfiguration(new Configuration())`。
   - v1.17：新增 `PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS = true` 配置。
   - v1.18：将原来的 `CoreOptions.CHECK_LEAKED_CLASSLOADER = false` 替换为 `PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS = true`。
   `ALLOW_UNALIGNED_SOURCE_SPLITS` 允许 source 在上游算子未完全对齐时分配 split，有助于测试中的限流场景更稳定地工作。

3. **`testThrottling` 方法重构——数据生成与插入时序调整**：
   这是核心修改。原始代码在作业启动前就插入了两批数据（batch1 = file_1 较新记录，batch = file_2 较旧记录），然后在作业执行过程中再插入被限流的旧数据（file_3、file_4）和解除限流的新数据（file_5）。

   重构后：
   - 所有五批数据（batch1~batch5）在方法开头预先创建完成，但不立即写入表。
   - 作业通过 `env.executeAsync()` 启动后，调用 `CommonTestUtils.waitForAllTaskRunning(miniCluster, jobID, false)` 等待所有 task 都进入运行状态。
   - 然后才通过 `dataAppender.appendToTable(dataAppender.writeFile(batch1), dataAppender.writeFile(batch2))` 写入第一批数据。
   - 后续 batch3、batch4（被限流的旧数据）和 batch5（解除限流的新数据）在测试流程中的相应节点写入。

   这一调整确保了数据写入发生在 source reader 完全就绪之后，消除了因作业启动阶段数据已存在而导致的时序竞争。

4. **引入 `CommonTestUtils`**：
   新增 `import org.apache.flink.runtime.testutils.CommonTestUtils`，用于 `waitForAllTaskRunning` 等待机制。

## 小结

本提交通过隔离测试状态（reporter 实例化）、修复依赖冲突（排除 slf4j）、调整测试配置（允许 unaligned splits）以及重构数据写入时序（等待所有 task 运行后再插入数据）等多重手段，彻底消除了 `TestIcebergSourceWithWatermarkExtractor` 的 flakiness。修复覆盖 Flink v1.16/v1.17/v1.18 三个版本，显著提升了 CI 的可靠性。
