# 提交 2620：Flink: Backport fix flaky tests for Iceberg sink (#14050)

## 提交信息

- **序号**：2620 / 4088
- **哈希**：d1771207c9040f1c8b6886665b56d1a972fe402a
- **短哈希**：d1771207c
- **日期**：2025-09-11 14:10:57 +0200
- **作者**：Maximilian Michels
- **提交说明**：Flink: Backport fix flaky tests for Iceberg sink
- **PR/Issue**：#14050（backport 原始 PR #14044）

## 总体目的

这是将 PR #14044（即提交 2618，修复 Flink Iceberg sink 测试的 flaky 问题）backport 到 Flink 1.19 和 1.20 两个版本目录的提交。提交 2618 原始落地在 `flink/v2.0`，而 Iceberg 项目同时维护 Flink 1.19、1.20、2.0 三个版本的源码目录，测试修复需在各版本分别应用以保证一致性。

flaky 测试的根因与修复方式与 2618 完全一致：Iceberg sink 依赖 checkpoint 提交数据，`fromCollection` 数据源与 checkpoint 协作不稳定，替换为自定义 `createBoundedSource`；同时为无快照断言增加诊断描述。

## 如何达成设计目的

将 2618 的改动原样应用到 `flink/v1.19` 和 `flink/v1.20` 两个目录下的对应文件：
- `SimpleDataUtil.java`：为无快照断言增加 `.as(...)` 描述。
- `TestFlinkIcebergSinkExtended.java`：left/right 数据流从 `env.fromCollection` 改为 `env.addSource(createBoundedSource(...))`。
- `TestIcebergSink.java`：同上改动。

## 修改详情

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java` (+6/-1 lines)

**修改目的**：改进无快照断言诊断信息。

**工作逻辑**：与 2618 相同——在 `latestSnapshot` 返回 null 时，为 `assertThat(expected).isEmpty()` 增加 `.as(...)` 描述，提示无快照通常意味着 Flink job 未 checkpoint。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkExtended.java` (+2/-2 lines)

**修改目的**：用 createBoundedSource 替换 fromCollection。

**工作逻辑**：left/right 数据流从 `env.fromCollection(rows, ROW_TYPE_INFO)` 改为 `env.addSource(createBoundedSource(rows), ROW_TYPE_INFO)`。

### `flink/v1.19/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (+2/-2 lines)

**修改目的**：同上。

**工作逻辑**：同上改动。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/SimpleDataUtil.java` (+6/-1 lines)

**修改目的**：同 1.19，改进断言诊断。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestFlinkIcebergSinkExtended.java` (+2/-2 lines)

**修改目的**：同 1.19，替换数据源。

### `flink/v1.20/flink/src/test/java/org/apache/iceberg/flink/sink/TestIcebergSink.java` (+2/-2 lines)

**修改目的**：同 1.19，替换数据源。

## 总结

本提交是 PR #14044（提交 2618）向 Flink 1.19 和 1.20 的 backport，将 flaky 测试修复（替换 fromCollection 为 createBoundedSource、改进断言描述）应用到这两个版本目录。至此三个支持的 Flink 版本（1.19、1.20、2.0）的 sink 测试稳定性修复齐备，保证跨版本测试质量一致。
