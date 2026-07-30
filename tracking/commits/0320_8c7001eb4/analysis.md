# 提交 0320：Flink: Backport #9308 to v1.17 and the relevant parts to v1.16 (#9403)

## 提交信息

- **序号**：0320 / 4088
- **哈希**：8c7001eb462a69641b3f0546b7efeddb63e501be
- **短哈希**：8c7001eb4
- **日期**：2024-01-03 17:53:34 +0100
- **作者**：pvary
- **提交说明**：Flink: Backport #9308 to v1.17 and the relevant parts to v1.16 (#9403)
- **PR/Issue**：#9308（被 backport 的原始 PR），#9403（本次 backport PR）

## 总体目的

这个提交把主分支上 PR #9308 对 Flink `IcebergSourceSplitReader` 的修复回移到 Iceberg 维护中的 Flink v1.17 分支，并把“相关部分”回移到更老的 Flink v1.16 分支。背景是 Iceberg 同时维护 `flink/v1.16`、`flink/v1.17`（以及 v1.18/v1.19 等）多套 Flink 版本分支，主分支上的修复需要按 Flink API 差异分别适配回移。

PR #9308 解决的是 Iceberg Flink source 在 watermark alignment（水位对齐）场景下的行为问题。Flink 的 `SplitReader` 接口提供了 `pauseOrResumeSplits(Collection, Collection)` 方法，用于在 split 级 watermark alignment 时暂停/恢复某些 split 的消费。Iceberg 的 `IcebergSourceSplitReader` 是顺序读 split 的：当 SourceOperator 因为 watermark alignment 而停止处理并回收已 fetch 的 batch 时，底层的 `ArrayPoolDataIteratorBatcher` 池会被耗尽，于是 `currentReader.next()` 调用自然阻塞——也就是说，即便不实现真正的 split 级暂停/恢复，对齐行为也能“自然”工作。基于这一事实，#9308 给 `IcebergSourceSplitReader` 显式实现一个空的 `pauseOrResumeSplits`，并在注释里说明为何留空，从而满足 Flink 接口契约、避免在 v1.17 这类要求实现该方法的 Flink 版本上出现抽象方法未实现的编译/运行问题。

同时回移的还有若干配套调整：把内部 split 队列从 `java.util.ArrayDeque` 换成 Iceberg relocated Guava 的 `Queues.newArrayDeque()`、为 `fetch()` 补充说明性 JavaDoc、以及测试侧把 `monitorInterval` 从 2ms 放宽到 10ms 以减少 flaky 测试。

## 如何达成设计目的

backport 在两个 Flink 版本上的内容有所不同，反映了两套 Flink API 的差异：
- **v1.17**：完整回移 #9308，包括 `pauseOrResumeSplits` 的空实现、队列替换、JavaDoc 与测试侧的 `monitorInterval` 调整 + 移除 `PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS` 配置。
- **v1.16**：只回移“相关部分”——即不包含 `pauseOrResumeSplits`（v1.16 对应的 Flink API 还未要求该方法），只把队列替换、JavaDoc 与 `monitorInterval` 调整同步过来。

队列从 `java.util.ArrayDeque` 改为 `Queues.newArrayDeque()`，主要是为了与 Iceberg relocated Guava 的使用习惯保持一致（Iceberg 把 Guava 重定位后内部统一通过 relocated 包引用，避免与用户类路径上的 Guava 冲突）。`fetch()` 的 JavaDoc 解释了该方法的契约：当前 split 读完后会发一个 `ArrayBatchRecords#finishedSplit(String)` 信号，下一轮 fetch 再继续下一个 split。`monitorInterval` 从 2ms 提升到 10ms 是为了降低测试因轮询过密导致的 flakiness。

## 修改详情

### `flink/v1.17/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceSplitReader.java`

**修改目的**：把 #9308 的核心修复同步到 v1.17。

**工作逻辑**：
- import：移除 `java.util.ArrayDeque`，新增 `java.util.Collection`、`org.apache.iceberg.relocated.com.google.common.collect.Queues`。
- 构造器中 `this.splits = new ArrayDeque<>()` 改为 `this.splits = Queues.newArrayDeque()`。
- 为 `fetch()` 增加 JavaDoc，说明返回的 records、当前 split 读完时发 `finishedSplit` 信号、下一轮继续下一个 split。
- 新增 `@Override public void pauseOrResumeSplits(Collection<IcebergSourceSplit> splitsToPause, Collection<IcebergSourceSplit> splitsToResume)`，方法体为空，注释解释：`IcebergSourceSplitReader` 顺序读 split，当 SourceOperator 为 watermark alignment 停止处理并回收 batch 时，`ArrayPoolDataIteratorBatcher#pool` 被耗尽、`currentReader.next()` 自然阻塞，因此无需真正的 split 级暂停/恢复，`pauseOrResumeSplits` 与 `wakeUp` 都可留空。

### `flink/v1.16/flink/src/main/java/org/apache/iceberg/flink/source/reader/IcebergSourceSplitReader.java`

**修改目的**：回移 #9308 中适用于 v1.16 的“相关部分”。

**工作逻辑**：与 v1.17 相比，本文件**不包含** `pauseOrResumeSplits` 的空实现（因 v1.16 对应的 Flink `SplitReader` 接口尚未要求该方法）。其余改动一致：`ArrayDeque` -> `Queues.newArrayDeque()`，并为 `fetch()` 添加同样的 JavaDoc。

### `flink/v1.17/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：同步 v1.17 测试侧的调整。

**工作逻辑**：
- 移除 `import org.apache.flink.configuration.PipelineOptions`。
- 在 `miniClusterResources` 构建中，把 `new Configuration().set(PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS, true)` 简化为 `new Configuration()`，即不再显式开启 unaligned source splits——这与 #9308 后 source 行为调整一致。
- `monitorInterval` 从 `Duration.ofMillis(2)` 放宽到 `Duration.ofMillis(10)`，降低测试 flakiness。

### `flink/v1.16/flink/src/test/java/org/apache/iceberg/flink/source/TestIcebergSourceWithWatermarkExtractor.java`

**修改目的**：同步 v1.16 测试侧的调整。

**工作逻辑**：仅把 `monitorInterval` 从 `Duration.ofMillis(2)` 改为 `Duration.ofMillis(10)`。注意 v1.16 这里没有移除 `PipelineOptions.ALLOW_UNALIGNED_SOURCE_SPLITS` 的相关配置——这与“只回移相关部分”的策略一致：v1.17 上 #9308 改变了 unaligned splits 的处理，而 v1.16 上这部分行为未变，因此 v1.16 测试保留原配置，只做 monitor interval 的稳定性调整。

## 小结

这是一个分版本差异化的 backport：把主分支 #9308 对 `IcebergSourceSplitReader` 在 watermark alignment 场景下的修复同步到 Flink v1.17（完整回移，含空的 `pauseOrResumeSplits` 实现、队列替换、测试配置调整）和 Flink v1.16（仅回移队列替换、JavaDoc 与 monitor interval 调整）。改动虽然不大，但体现了 Iceberg 多 Flink 版本并行维护下根据各版本 API 差异精准裁剪 backport 内容的做法，并附带了减少 flaky 测试的稳定性改进。
