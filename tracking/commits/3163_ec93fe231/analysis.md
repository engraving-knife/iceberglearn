# 提交 3163：Flink: Fix test assumption which can produce flakiness (#15147)

## 提交信息

- **序号**：3163 / 4088
- **哈希**：ec93fe2313c116276acc1deff2abb47cc6e365e7
- **短哈希**：ec93fe231
- **日期**：2026-01-26 17:52:41 -0800
- **作者**：Maximilian Michels
- **提交说明**：Flink: Fix test assumption which can produce flakiness
- **PR/Issue**：#15147（关闭 #15139）

## 总体目的

`TestDynamicIcebergSink` 中的 `testCommitsOncePerTableBranchAndCheckpoint` 测试用于验证 Flink 动态 Iceberg Sink 的一个关键不变量：每个 checkpoint 只提交一次（commit once per checkpoint）。该测试在断言最后一个快照的 summary 时，原先严格要求 `total-equality-deletes=1` 且 `total-position-deletes=1`——即假定最后一个快照中恰好包含 1 个等值删除文件和 1 个位置删除文件。

问题（issue #15139）在于这一假定在 CI 高负载环境下不稳定（flaky）：当 CI 资源受限且测试并行执行时，Flink 流处理管线可能在所有记录被处理完之前就触发了多次 checkpoint。这会导致删除文件被划分到不同的快照中，最后一个快照里的等值/位置删除文件数量不再是各 1 个，但删除文件总数仍为 2，且语义上并无错误。原断言因此偶发失败，造成 CI 误报。而测试真正要验证的核心不变量——"每个 checkpoint 只提交一次"——是在更下方通过对 `flink.max-committed-checkpoint-id` 分组计数并断言每组恰好 1 次来检查的，与删除文件的具体分布无关。

本提交放宽对该快照 summary 的断言：用 `total-delete-files=2` 替代 `total-equality-deletes=1` + `total-position-deletes=1`，只校验删除文件总数为 2，不再约束其按删除类型的分布，从而消除因 checkpoint 切分时机不同导致的 flakiness，同时保留对核心不变量的验证。改动同步移植（port）到 Iceberg 支持的三个 Flink 版本（v1.20、v2.0、v2.1）。

## 如何达成设计目的

将三个 Flink 版本目录下 `TestDynamicIcebergSink.java` 中相同的断言块统一修改：移除对 `total-equality-deletes` 与 `total-position-deletes` 的逐项断言，改为断言 `total-delete-files` 为 2，并保留 `total-records` 为 6 的断言。这样既校验了删除文件总数与记录总数符合预期，又不依赖于删除文件在 checkpoint 间的具体分布。

## 修改详情

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+1/-2 lines)

**修改目的**：放宽最后快照 summary 中删除文件的断言以消除 flakiness。

**工作逻辑**：在 `testCommitsOncePerTableBranchAndCheckpoint` 中，原断言 `ImmutableMap.<String, String>builder().put("total-equality-deletes", "1").put("total-position-deletes", "1").put("total-records", "6").build()` 改为 `.put("total-delete-files", "2").put("total-records", "6").build()`。`total-delete-files` 是快照中删除文件的总数（不分等值/位置），值为 2 表示测试输入的两个 upsert 操作各产生一个删除文件，无论它们落在哪个 checkpoint 的快照里，总数恒为 2；而 `total-equality-deletes`/`total-position-deletes` 的具体取值会随 checkpoint 切分时机变化。测试核心不变量"每 checkpoint 提交一次"仍由紧随其后的 `commitsPerCheckpoint` 断言（`allMatch(count -> count == 1)`）保证。

### `flink/v2.0/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+1/-2 lines)

**修改目的**：将相同修复移植到 Flink 2.0 版本模块。

**工作逻辑**：与 v1.20 完全相同的断言修改，保持三版本测试行为一致。

### `flink/v2.1/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+1/-2 lines)

**修改目的**：将相同修复移植到 Flink 2.1 版本模块。

**工作逻辑**：与 v1.20 完全相同的断言修改，保持三版本测试行为一致。

## 总结

本提交通过将 `testCommitsOncePerTableBranchAndCheckpoint` 中对最后快照删除文件的断言从"等值删除 1 + 位置删除 1"放宽为"删除文件总数 2"，消除了 CI 高负载下因 checkpoint 切分时机不同导致的测试 flakiness（#15139），同时保留了对"每 checkpoint 提交一次"这一核心不变量的验证。修复同步移植到 Flink v1.20/v2.0/v2.1 三个版本模块，提升了 Flink 动态 Sink 测试的稳定性与 CI 可靠性。
