# 提交 3556：Flink 2.1: Fix forward-writer chaining regression in DynamicIcebergSink (#16026)

## 提交信息

- **序号**：3556 / 4088
- **哈希**：f66305aecb789e7583f26ccbdb922ba1c8175f1c
- **短哈希**：f66305aec
- **日期**：2026-04-18 22:58:23 -0700
- **作者**：drexler-sky
- **提交说明**：Flink 2.1: Fix forward-writer chaining regression in DynamicIcebergSink (#16026)
- **PR/Issue**：#16026

## 总体目的

该提交旨在修复 Flink 2.1 版本中 `DynamicIcebergSink` 的 forward-writer 链路（chaining）回归问题。在动态 Iceberg Sink 的实现中，forward writer 是一个用于将写入结果转发到下游的算子（operator）。之前的代码在将 forward writer 算子添加到 Flink 拓扑时，未显式设置并行度，导致算子的并行度可能与其上游/下游算子不一致，从而破坏了算子链（operator chaining）的正确性。

当并行度不一致时，Flink 在构建算子链时无法将 forward writer 与相邻算子正确链接在一起，导致数据需要经过不必要的网络洗牌（network shuffle），降低了作业性能并可能改变语义。该修复确保 forward writer 算子使用与 sink 配置一致的并行度，从而恢复正确的算子链行为。

## 如何达成设计目的

修复方式非常简洁：在构建 forward writer 算子时，通过调用 `.setParallelism(converted.getParallelism())` 显式设置其并行度，使其与转换后的 sink 配置保持一致。这样 Flink 在算子链优化阶段可以正确地将 forward writer 与相邻算子链接，避免不必要的网络传输。

## 修改详情

### `flink/v2.1/flink/src/main/java/org/apache/iceberg/flink/sink/dynamic/DynamicIcebergSink.java` (+1/-0 lines)

**修改目的**：为 forward writer 算子显式设置并行度，修复算子链回归问题。

**工作逻辑**：
在 `addWriterTopology`（或类似拓扑构建方法）中，构建 Forward-Writer 算子时新增了 `.setParallelism(converted.getParallelism())` 调用：

```java
operatorName("Forward-Writer"),
writeResultTypeInfo,
new SinkWriterOperatorFactory<>(forwardWriterSink))
.setParallelism(converted.getParallelism())
.uid(prefixIfNotNull(uidPrefix, "-forward-writer"));
```

`converted.getParallelism()` 取自 sink 转换后的并行度配置。通过将 forward writer 算子的并行度显式设置为该值，确保整个 sink 拓扑中的算子并行度一致，从而让 Flink 的算子链优化能够正确工作，避免因并行度不匹配而导致的算子链断裂。

## 总结

该提交是一个针对 Flink 2.1 动态 Iceberg Sink 的回归修复，通过一行代码的修改恢复了 forward writer 算子的正确并行度设置。这类回归通常在 Flink 版本升级或算子链逻辑变更后出现，修复后可以保证 sink 拓扑的性能和正确性。
