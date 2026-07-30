# 提交 3563：Flink: Backport add passthroughRecords option to DynamicIcebergSink (#16019)

## 提交信息

- **序号**：3563 / 4088
- **哈希**：f984c28b215c56846c632ccc5a368f4f4afe0b5d
- **短哈希**：f984c28b2
- **日期**：2026-04-20 16:07:47 +0200
- **作者**：Han You
- **提交说明**：Flink: Backport add passthroughRecords option to DynamicIcebergSink (#16019)
- **PR/Issue**：#16019（backport #15433 和 #16026）

## 总体目的

该提交将 `passthroughRecords`（前向直通写入）功能反向移植到 Flink 1.20 和 2.0 版本的 `DynamicIcebergSink` 中。在动态 Iceberg Sink 的原有实现中，所有记录无论是否需要分区分布（distribution mode），都会经过标准的 Sink2 流水线，包括 keyBy/shuffle 分布步骤。对于不需要特定分布模式（distributionMode 为 null）的记录来说，这种 shuffle 是不必要的开销。

该提交引入了一种"前向写入路径"（forward write path）：当记录的 `distributionMode` 为 null 时，记录通过 Flink 的 forward 边直接发送到一个链式（chained）的 writer 算子，完全避免数据 shuffle。而需要分布的记录仍然走标准的 shuffle 路径。两条路径的写入结果最终通过 union 合并到同一个 pre-commit 聚合器和 committer 中，保证原子提交。这同时包含了 #16026 的修复（显式设置 forward writer 的并行度），确保算子链正确性。

## 如何达成设计目的

整体设计将 sink 拓扑拆分为两条并行写入路径：

1. **前向路径（forward path）**：`distributionMode == null` 的记录通过 side output（`DYNAMIC_FORWARD_STREAM`）输出到一个使用 `SinkWriterOperatorFactory` 创建的 `ForwardWriterSink` 算子。该算子与 generator 通过 forward 边链式连接，无数据 shuffle。
2. **Shuffle 路径**：带有 distributionMode 的记录走原有的 keyBy/map/sinkTo 流水线。

两条路径产出的 `DynamicWriteResult` committable 通过 `union` 合并，统一进入 pre-commit 拓扑进行聚合和提交。`DynamicRecordProcessor` 被修改为：对于 forward 记录总是强制立即更新表元数据（immediate update），因为 forward 路径没有后续的 table update 算子。

## 修改详情

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+134/-19 lines)

**修改目的**：引入 ForwardWriterSink 和双路径拓扑。

**工作逻辑**：
- 新增 `forwardWriteResults` 字段（`DataStream<CommittableMessage<DynamicWriteResult>>`），由 Builder 在构建时注入。
- `addPreCommitTopology` 中将 `writeResults` 与 `forwardWriteResults` 进行 `union` 后再 keyBy 聚合。
- 新增内部类 `ForwardWriterSink`，实现 `Sink<DynamicRecordInternal>` 和 `SupportsCommitter<DynamicWriteResult>`。其 `createWriter` 返回一个 `DynamicWriter`，但 `createCommitter` 抛出 `UnsupportedOperationException`（提交由主 sink 处理）。
- Builder 的 `build` 方法改为接收 `converted` 和 `sideOutputType` 参数，构建 forward writer 算子并设置并行度 `converted.getParallelism()`（即 #16026 的修复），然后注入到主 sink。
- `append` 方法重构：创建 `sideOutputType`，构建 generator，通过 `build(converted, sideOutputType)` 创建 sink，shuffle 路径单独构建为 `shuffleInput` 后 `sinkTo(sink)`。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecord.java` (+26/-7 lines)

**修改目的**：支持 `distributionMode` 为 null 的 forward 记录。

**工作逻辑**：
- `distributionMode` 字段标注为 `@Nullable`。
- 新增一个不接收 `distributionMode` 参数的构造函数，表示 forward（无 shuffle）写入，内部调用 `this(..., null, -1)`。
- 原构造函数的 `distributionMode` 文档更新为说明 `null` 表示 forward 写入。

### `flink/v1.20/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicRecordProcessor.java` (+58/-15 lines)

**修改目的**：区分 forward 和 shuffle 记录，forward 记录走 side output。

**工作逻辑**：
- 新增 `DYNAMIC_FORWARD_STREAM` 输出标签和对应的 `forwardStream` 字段。
- 总是创建 `TableUpdater`（forward 记录需要强制立即更新），不再仅当 `immediateUpdate` 为 true 时创建。
- `collect` 方法中判断 `isForward = data.distributionMode() == null`。当需要表更新时，forward 记录或 `immediateUpdate` 为 true 时直接调用 `updater.update`，否则走 side output 更新流。
- `emit` 方法新增 `forward` 布尔参数：forward 记录通过 `context.output(forwardStream, record)` 输出到 forward 流，非 forward 记录通过 `collector.collect(record)` 输出到主流。forward 路径中 `writerKey` 设为 -1（不使用）。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/dynamic/TestDynamicIcebergSink.java` (+153/-12 lines)

**修改目的**：添加 forward 写入和混合写入的测试。

**工作逻辑**：
- 新增 `ForwardGenerator`（总是生成 distributionMode=null 的记录）和 `MixedGenerator`（交替生成 forward 和 shuffle 记录）。
- `testNoShuffleTopology`：验证 generator 和 Forward-Writer 在同一个 JobVertex 中（即算子链成功）。
- `testForwardWrite` 和 `testMixedForwardAndShuffleWrite`：分别测试纯 forward 和混合写入的数据正确性。
- UID 断言从 `contains` 改为 `containsOnly`，并新增 `test--forward-writer` UID。
- `CommitHookDynamicIcebergSink` 测试子类更新以匹配新的构造函数签名。

### `flink/v2.0/...` 和 `flink/v2.1/...` 对应文件 (+133/-19, +165/-12 等类似改动)

**修改目的**：将相同改动应用到 Flink 2.0 和 2.1 版本。

**工作逻辑**：与 v1.20 版本的改动基本一致，v2.1 额外包含了 #16026 的并行度修复（`.setParallelism(converted.getParallelism())`）。

## 总结

该提交是一个重要的功能反向移植，为 Flink 1.20/2.0/2.1 版本的 DynamicIcebergSink 添加了 forward（passthrough）写入路径。通过允许不需要分布模式的记录跳过 shuffle 直接链式写入，显著减少了不必要的网络开销，提升了写入性能。双路径设计保证了原子提交语义不受影响。同时包含了 #16026 的算子链并行度修复，确保 forward writer 能正确与 generator 链式连接。
