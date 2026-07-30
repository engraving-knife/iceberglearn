# 提交 0797：Flink 1.19: Fix flaky TestIcebergSourceFailover > testBoundedWithSavepoint (#10393)

## 提交信息

- **序号**：0797 / 4088
- **哈希**：46732b876a50409915f7ccf79471bb26c09f7ef8
- **短哈希**：46732b876
- **日期**：2024-05-30 17:41:40 +0200
- **作者**：pvary
- **提交说明**：Flink 1.19: Fix flaky TestIcebergSourceFailover > testBoundedWithSavepoint (#10393)
- **PR/Issue**：#10393

## 总体目的

修复 Flink Iceberg Source 故障转移测试 `TestIcebergSourceFailover` 中的 flaky（不稳定）用例，主要针对 `testBoundedWithSavepoint` 测试。该测试在 Flink 1.17、1.18、1.19 三个版本的测试模块中存在相同问题，本提交同步修复三个版本。

测试的不稳定性来源于两方面：
1. **同步机制不可靠**：测试使用 `CompletableFuture` 作为信号来协调"等待故障点"与"继续处理"的时序，但在多并行度场景下只能保证一个子任务到达故障点，无法确保所有并行子任务都就绪，导致 savepoint / failover 触发时机不确定。
2. **测试数据量与配置不当**：原测试基于 `expectedRecords.size() / 2` 计算故障触发点，并设置了较大的 fetch batch（128），在数据量或并行度变化时容易导致故障点无法被正确触发或测试耗时过长。

## 如何达成设计目的

提交通过对三个 Flink 版本（1.17/1.18/1.19）的测试代码做相同修改来达成目的，核心改动包含四方面：

### 1. 用 CountDownLatch 替换 CompletableFuture 作为故障点同步信号

将内部类 `RecordCounterToFail` 重命名为 `RecordCounterToWait`，并将其中的 `CompletableFuture<Void> fail` 字段替换为 `CountDownLatch countDownLatch`。

- **原逻辑**：`fail = new CompletableFuture<>()`，当任一并行子任务的记录数超过阈值时调用 `fail.complete(null)`，主线程通过 `fail.get()` 等待。问题在于 `CompletableFuture.complete()` 只能完成一次，第一个到达阈值的子任务就会触发信号，其他并行子任务可能尚未处理任何记录，此时触发 savepoint/failover 会导致这些子任务状态不完整。
- **新逻辑**：`countDownLatch = new CountDownLatch(stream.getParallelism())`，latch 的初始计数设为流的并行度。每个并行子任务到达阈值时调用 `countDownLatch.countDown()`，主线程通过 `countDownLatch.await()` 等待所有并行子任务都到达故障点后才继续。这确保了 savepoint / failover 在所有子任务都处理了足够记录后才触发，消除了因并行子任务进度不同步导致的 flaky 行为。

判断条件也相应调整：`notFailedYet` 从 `!fail.isDone()` 改为 `countDownLatch.getCount() != 0`，确保每个子任务只 countDown 一次。

### 2. 添加全局超时保护

新增 `@Rule public Timeout globalTimeout = Timeout.seconds(120);`，为所有测试方法设置 120 秒的全局超时。这防止因死锁或信号等待失败导致测试无限挂起，使 flaky 测试以明确失败（超时）而非无限阻塞的形式暴露。

### 3. 固定故障触发点为常量 2

在 `testBoundedWithSavepoint` 相关的 `createBoundedStreams` 调用中，将故障触发阈值从 `expectedRecords.size() / 2` 改为固定值 `2`。原来依赖数据量的一半作为阈值，当数据量较小时阈值过小容易在 checkpoint 完成前就触发；改为固定值 2 使测试行为可预测且与数据量解耦，降低 flaky 概率。

### 4. 移除过大的 fetch batch 配置并防止 split 合并

- 在 `TestIcebergSourceFailover.sourceBuilder()` 中移除了 `config.setInteger(FlinkConfigOptions.SOURCE_READER_FETCH_BATCH_RECORD_COUNT, 128)`。原配置使 reader 一次拉取 128 条记录，在数据量小的测试中会导致所有数据一次性被读完，无法在读取过程中触发 savepoint/failover。
- 在 `TestIcebergSourceFailoverWithWatermarkExtractor.sourceBuilder()` 中新增配置 `.set(FlinkReadOptions.SPLIT_FILE_OPEN_COST, Long.toString(TableProperties.SPLIT_SIZE_DEFAULT))` 来阻止 split 被合并。同时将 builder 构造方式从 `IcebergSource.<RowData>builder()` 改为 `IcebergSource.forRowData()` 并显式传入 `Configuration`。这确保每个 split 保持独立，使多并行度场景下每个子任务都能分到数据，配合 CountDownLatch 机制确保所有子任务都能到达故障点。

## 修改详情

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：修复 Flink 1.17 版本的 flaky 故障转移测试。

**工作逻辑**：
- 导入 `CountDownLatch` 和 `Timeout`，移除 `ExecutionException` 导入。
- 新增 `@Rule public Timeout globalTimeout = Timeout.seconds(120)` 全局超时规则。
- `sourceBuilder()` 中移除 `SOURCE_READER_FETCH_BATCH_RECORD_COUNT` 设为 128 的配置。
- `testBoundedWithSavepoint` 中 `createBoundedStreams(env, expectedRecords.size() / 2)` 改为 `createBoundedStreams(env, 2)`，同步信号相关调用从 `RecordCounterToFail.waitToFail()` / `continueProcessing()` 改为 `RecordCounterToWait.waitForCondition()` / `continueProcessing()`。
- `testBoundedIcebergSourceFailover` 中同样替换 `RecordCounterToFail` 为 `RecordCounterToWait`，`createBoundedStreams` 阈值改为 2。
- 内部类 `RecordCounterToFail` 重命名为 `RecordCounterToWait`：
  - `CompletableFuture<Void> fail` → `CountDownLatch countDownLatch`，初始化为 `new CountDownLatch(stream.getParallelism())`。
  - `wrapWithFailureAfter(stream, failAfter)` 参数名改为 `condition`。
  - map 函数中 `fail.complete(null)` → `countDownLatch.countDown()`，`notFailedYet` 判断改为 `countDownLatch.getCount() != 0`。
  - `waitToFail()`（`fail.get()`）→ `waitForCondition()`（`countDownLatch.await()`），不再抛 `ExecutionException`。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java`

**修改目的**：修复带 watermark extractor 的故障转移测试，防止 split 合并导致并行子任务无数据可处理。

**工作逻辑**：`sourceBuilder()` 重写为：
- 新建 `Configuration config`。
- builder 由 `IcebergSource.<RowData>builder()` 改为 `IcebergSource.forRowData()`。
- 新增 `.set(FlinkReadOptions.SPLIT_FILE_OPEN_COST, Long.toString(TableProperties.SPLIT_SIZE_DEFAULT))`：将 split 的文件打开成本阈值设为默认 split 大小（`SPLIT_SIZE_DEFAULT`，通常为 128MB），这使得每个数据文件都不会因为小于 open cost 而被合并到其他 split，从而保证每个并行子任务都能分到独立的 split。
- 新增 `.flinkConfig(config)` 传入配置对象。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：对 Flink 1.18 版本做与 1.17 完全相同的修复。

**工作逻辑**：同上 1.17 版本的 `TestIcebergSourceFailover.java`。

### `flink/v1.18/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java`

**修改目的**：对 Flink 1.18 版本做与 1.17 完全相同的修复。

**工作逻辑**：同上 1.17 版本的 `TestIcebergSourceFailoverWithWatermarkExtractor.java`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailover.java`

**修改目的**：对 Flink 1.19 版本做与 1.17 完全相同的修复（提交标题特指 1.19）。

**工作逻辑**：同上 1.17 版本的 `TestIcebergSourceFailover.java`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceFailoverWithWatermarkExtractor.java`

**修改目的**：对 Flink 1.19 版本做与 1.17 完全相同的修复。

**工作逻辑**：同上 1.17 版本的 `TestIcebergSourceFailoverWithWatermarkExtractor.java`。

## 小结

- **成效**：通过引入 `CountDownLatch`（计数等于并行度）确保所有并行子任务都到达故障点后再触发 savepoint/failover，从根本上消除了原 `CompletableFuture` 单次完成导致的并行子任务进度不同步问题；配合全局超时、固定阈值和 split 不合并策略，显著提升了 `testBoundedWithSavepoint` 等故障转移测试的稳定性。
- **影响范围**：仅影响 Flink 1.17/1.18/1.19 三个版本的测试代码（`TestIcebergSourceFailover` 和 `TestIcebergSourceFailoverWithWatermarkExtractor`），不涉及任何生产代码或运行时行为。
- **回迁注意事项**：回迁到 1.4.x 分支时需确认目标分支是否同时维护 Flink 1.17/1.18/1.19 三个版本。若 1.4.x 分支的 Flink 版本范围不同（如缺少 1.19），仅需回迁对应版本目录下的测试文件。三个版本的修改内容完全一致，可批量处理。需注意 `CountDownLatch` 的计数依赖 `stream.getParallelism()`，回迁后应确认测试环境的默认并行度配置与修改预期一致。`FlinkReadOptions.SPLIT_FILE_OPEN_COST` 和 `TableProperties.SPLIT_SIZE_DEFAULT` 这两个符号在 1.4.x 分支中应已存在，回迁无兼容性问题。
